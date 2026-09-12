package dev.dani.drivecast.data.repository

import dev.dani.drivecast.data.model.PolylineDecoder
import dev.dani.drivecast.data.model.WeatherUnit
import dev.dani.drivecast.data.remote.GeocodingApi
import dev.dani.drivecast.data.remote.ReverseGeocodingApi
import dev.dani.drivecast.data.remote.RouteApi
import dev.dani.drivecast.data.remote.USNationalWeatherServiceAlertsApi
import dev.dani.drivecast.data.remote.WeatherApi
import dev.dani.drivecast.di.IoDispatcher
import dev.dani.drivecast.domain.model.AlertCertainty
import dev.dani.drivecast.domain.model.AlertSeverity
import dev.dani.drivecast.domain.model.AlertUrgency
import dev.dani.drivecast.domain.model.LatLng
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.Route
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.Weather
import dev.dani.drivecast.domain.model.WeatherAlert
import dev.dani.drivecast.domain.model.WeatherAlertSource
import dev.dani.drivecast.domain.model.WeatherCondition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class DriveCastRepositoryImpl @Inject constructor(
    private val geocodingApi: GeocodingApi,
    private val routeApi: RouteApi,
    private val weatherApi: WeatherApi,
    private val reverseGeocodingApi: ReverseGeocodingApi,
    private val usNwsAlertsApi: USNationalWeatherServiceAlertsApi,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DriveCastRepository {

    override suspend fun geocode(
        query: String,
    ): List<Place> = withContext(ioDispatcher) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val response = geocodingApi.search(name = trimmed)
        response.results.orEmpty().map { result ->
            Place(
                name = result.name,
                admin = result.admin1,
                country = result.country,
                location = LatLng(result.latitude, result.longitude),
                countryCode = result.countryCode
            )
        }
    }

    override suspend fun fetchRoute(
        origin: LatLng,
        destination: LatLng,
    ): Route = withContext(ioDispatcher) {
        val response = routeApi.route(
            coordinates = "${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}",
            geometries = GEOMETRY_FORMAT,
        )
        if (response.code.equals(OSRM_SUCCESS_CODE, ignoreCase = true).not()) {
            throw DriveCastException(response.message ?: "OSRM could not compute a route (${response.code}).")
        }
        val route = response.routes.firstOrNull()
            ?: throw DriveCastException("OSRM returned no routes for this pair of places.")

        val geometry = PolylineDecoder.decode(route.geometry, GEOMETRY_PRECISION)
        if (geometry.isEmpty()) {
            throw DriveCastException("RouteData geometry could not be decoded.")
        }
        Route(
            geometry = geometry,
            durationSeconds = route.duration,
            distanceMeters = route.distance,
            segmentDurationsSeconds = route.legs
                .flatMap { it.annotation?.duration.orEmpty() }
                .takeIf { it.size == geometry.size - 1 }
                .orEmpty()
        )
    }

    override suspend fun fetchWeather(
        location: LatLng,
        atEpochSeconds: Long,
        unitSystem: UnitSystem,
    ): Weather = withContext(ioDispatcher) {
        val weatherUnit = WeatherUnit.from(unitSystem)
        val response = weatherApi.hourlyForecast(
            latitude = location.latitude,
            longitude = location.longitude,
            temperatureUnit = weatherUnit.temperature,
            windSpeedUnit = weatherUnit.windSpeed,
            precipitationUnit = weatherUnit.precipitation,
        )
        val hourly = response.hourly ?: throw DriveCastException("Open-Meteo returned no hourly series.")
        if (hourly.time.isEmpty()) {
            throw DriveCastException("Open-Meteo returned an empty hourly series.")
        }

        val index = closestIndex(hourly.time, atEpochSeconds)
        val temperature = hourly.temperature2m.getOrNull(index) ?: throw DriveCastException("No temperature available for the requested hour.")

        Weather(
            temperature = temperature,
            apparentTemperature = hourly.apparentTemperature.getOrNull(index),
            precipitation = hourly.precipitation.getOrNull(index) ?: 0.0,
            precipitationProbability = hourly.precipitationProbability.getOrNull(index),
            windSpeed = hourly.windSpeed10m.getOrNull(index),
            condition = WeatherCondition.fromWmoCode(hourly.weatherCode.getOrNull(index) ?: -1),
            forecastEpochSeconds = hourly.time[index]
        )
    }

    override suspend fun reverseGeocode(
        location: LatLng
    ): ResolvedPlace = withContext(ioDispatcher) {
        val response = reverseGeocodingApi.reverseGeocode(
            latitude = location.latitude,
            longitude = location.longitude
        )

        val byLevel = response.localityInfo?.administrative.orEmpty()
            .filter { it.name.isNullOrBlank().not() }
            .associateBy({ it.adminLevel }, { it.name })

        ResolvedPlace(
            locality = response.city.orNull() ?: response.locality.orNull(),
            town = byLevel[ADMIN_LEVEL_TOWN].orNull(),
            county = byLevel[ADMIN_LEVEL_COUNTY].orNull(),
            adminArea = response.principalSubdivision.orNull()
                ?: byLevel[ADMIN_LEVEL_STATE].orNull(),
            country = response.countryName.orNull() ?: byLevel[ADMIN_LEVEL_COUNTRY].orNull(),
            countryCode = response.countryCode.orNull()
        )
    }

    override suspend fun fetchAlerts(
        location: LatLng,
    ): List<WeatherAlert> = withContext(ioDispatcher) {
        val response = try {
            usNwsAlertsApi.activeAlerts(
                point = String.format(Locale.US, "%.4f,%.4f", location.latitude, location.longitude)
            )
        } catch (http: HttpException) {
            // 400 "out of bounds" when the location is out of United States.
            if (http.code() == HTTP_BAD_REQUEST) return@withContext emptyList()
            throw http
        }

        response.features.orEmpty()
            .asSequence()
            .mapNotNull { feature ->
                val props = feature.properties ?: return@mapNotNull null
                val event = props.event?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                WeatherAlert(
                    event = event,
                    severity = AlertSeverity.fromCap(props.severity),
                    source = WeatherAlertSource.NWS,
                    headline = props.headline.orNull(),
                    description = props.description.orNull(),
                    instruction = props.instruction.orNull(),
                    urgency = AlertUrgency.fromCap(props.urgency),
                    certainty = AlertCertainty.fromCap(props.certainty)
                )
            }
            // Group by event, show the most serious first.
            .groupBy { it.event }
            .map { (_, sameEvent) -> sameEvent.maxBy { it.severity.ordinal } }
            .sortedByDescending { it.severity.ordinal }
            .toList()
    }

    private fun closestIndex(times: List<Long>, target: Long): Int {
        var bestIndex = 0
        var bestDelta = Long.MAX_VALUE
        times.forEachIndexed { i, t ->
            val delta = abs(t - target)
            if (delta < bestDelta) {
                bestDelta = delta
                bestIndex = i
            }
        }
        return bestIndex
    }

    private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        const val GEOMETRY_FORMAT = "polyline6" // (precision 6) - Just `polyline` gives only precision 5
        const val GEOMETRY_PRECISION = PolylineDecoder.PRECISION_6

        const val ADMIN_LEVEL_COUNTRY = 2
        const val ADMIN_LEVEL_STATE = 4
        const val ADMIN_LEVEL_COUNTY = 6
        const val ADMIN_LEVEL_TOWN = 8

        const val OSRM_SUCCESS_CODE = "Ok"

        /** api.weather.gov answers an out-of-coverage point with 400, not an empty collection. */
        const val HTTP_BAD_REQUEST = 400
    }
}
