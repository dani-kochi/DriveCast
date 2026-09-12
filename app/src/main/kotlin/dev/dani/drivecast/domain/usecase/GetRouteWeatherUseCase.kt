package dev.dani.drivecast.domain.usecase

import dev.dani.drivecast.data.model.WeatherUnit
import dev.dani.drivecast.data.repository.DriveCastException
import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.RouteWeather
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.Waypoint
import dev.dani.drivecast.domain.model.WaypointForecast
import dev.dani.drivecast.domain.model.WaypointPlaceFormatter
import dev.dani.drivecast.domain.model.Weather
import dev.dani.drivecast.domain.model.WeatherAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Get weather along route
 *
 * 1. Geocode origin and destination
 * 2. Get route from origin to destination with segments.
 * 3. Determine waypoint for every hour of driving time
 * 4. For each waypoint
 *    i. Get weather forecast
 *    ii. Reverse geocode to name the waypoint
 *    iii. Get weather alerts for the waypoint
 *
 *    This is done for 4 ([MAX_CONCURRENT_REQUESTS]) waypoints at a time
 * 5. Adjust place name - display country if waypoints are in different countries
 *
 */
class GetRouteWeatherUseCase @Inject constructor(
    private val repository: DriveCastRepository
) {

    data class Params(
        val originQuery: String,
        val destinationQuery: String,
        val departureEpochSeconds: Long,
        val originPlace: Place? = null,
        val destinationPlace: Place? = null,
        val unitSystem: UnitSystem = UnitSystem.IMPERIAL,
    )

    suspend operator fun invoke(params: Params): RouteWeather = coroutineScope {
        require(params.originQuery.isNotBlank()) { "Origin must not be blank." }
        require(params.destinationQuery.isNotBlank()) { "Destination must not be blank." }

        // Get origin and destination in parallel
        val originDeferred = async { params.originPlace ?: resolve(params.originQuery) }
        val destinationDeferred = async { params.destinationPlace ?: resolve(params.destinationQuery) }
        val (origin, destination) = awaitAll(originDeferred, destinationDeferred)

        // Get route from origin to destination
        val route = repository.fetchRoute(origin.location, destination.location)

        // Determine Waypoints
        val waypoints = WaypointSampler.sample(
            route = route,
            departureEpochSeconds = params.departureEpochSeconds
        )

        // Limit concurrent requests to avoid overwhelming the APIs.
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        // Resolve location, forecast and get alerts for each waypoint - limited to max (4) points at a time.
        val resolved = waypoints.mapAsync { waypoint ->
            // A nested scope so both calls for this waypoint overlap with each other as well as with the other waypoints.
            coroutineScope {
                val placeDeferred = async {
                    semaphore.withPermit {
                        result { repository.reverseGeocode(waypoint.location) }
                    }
                }
                val alertsDeferred = async {
                    semaphore.withPermit {
                        result { repository.fetchAlerts(waypoint.location) }
                    }
                }
                val weatherDeferred = async {
                    semaphore.withPermit {
                        result { repository.fetchWeather(location = waypoint.location, atEpochSeconds = waypoint.etaEpochSeconds, unitSystem = params.unitSystem) }
                    }
                }
                ResolvedWaypoint(
                    waypoint = waypoint,
                    place = placeDeferred.await().getOrNull(),
                    weather = weatherDeferred.await(),
                    alerts = alertsDeferred.await().getOrNull().orEmpty()
                )
            }
        }

        // Check if all waypoints are in the same country
        val referenceCountryCode = sharedCountryCode(resolved)
        val forecasts = resolved.map { it.toForecast(referenceCountryCode) }

        RouteWeather(
            origin = origin,
            destination = destination,
            route = route,
            departureEpochSeconds = params.departureEpochSeconds,
            forecasts = forecasts,
            unitSystem = params.unitSystem,
        )
    }

    private fun sharedCountryCode(resolved: List<ResolvedWaypoint>): String? {
        val start = resolved.firstNotNullOfOrNull { it.place?.countryCode?.takeIf(String::isNotBlank) }
        val end = resolved.asReversed()
            .firstNotNullOfOrNull { it.place?.countryCode?.takeIf(String::isNotBlank) }
        return start?.takeIf { it.equals(end, ignoreCase = true) }
    }

    private fun ResolvedWaypoint.toForecast(referenceCountryCode: String?): WaypointForecast {
        val label = WaypointPlaceFormatter.format(
            place = place,
            location = waypoint.location,
            referenceCountryCode = referenceCountryCode
        )
        return weather.fold(
            onSuccess = { forecast ->
                WaypointForecast.Success(
                    waypoint = waypoint,
                    placeLabel = label,
                    weather = forecast,
                    alerts = alerts.ifEmpty { listOfNotNull(forecast.condition.derivedAlert()) }
                )
            },
            onFailure = {
                WaypointForecast.Failure(
                    waypoint = waypoint,
                    placeLabel = label,
                    message = it.message ?: it::class.simpleName ?: "Unknown error"
                )
            }
        )
    }

    private data class ResolvedWaypoint(
        val waypoint: Waypoint,
        val place: ResolvedPlace?,
        val weather: Result<Weather>,
        val alerts: List<WeatherAlert>
    )

    // Wrap the response as a Result
    private inline fun <T> result(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun resolve(query: String): Place = repository.geocode(query).firstOrNull()
        ?: throw DriveCastException("Could not find a place matching '$query'")

    private suspend fun <T, R> Iterable<T>.mapAsync(transform: suspend (T) -> R): List<R> = coroutineScope {
        map { async { transform(it) } }.awaitAll()
    }

    companion object {
        // Max four requests at a time.
        const val MAX_CONCURRENT_REQUESTS = 4
    }
}
