package dev.dani.drivecast.domain.usecase

import dev.dani.drivecast.data.repository.DriveCastException
import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.domain.model.AlertSeverity
import dev.dani.drivecast.domain.model.LatLng
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.Route
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.WaypointForecast
import dev.dani.drivecast.domain.model.Weather
import dev.dani.drivecast.domain.model.WeatherAlert
import dev.dani.drivecast.domain.model.WeatherAlertSource
import dev.dani.drivecast.domain.model.WeatherCondition
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GetRouteWeatherUseCaseTest {

    private val departure = 1_700_000_000L

    @Test
    fun `fans out weather requests in parallel and aggregates every waypoint`() = runTest {
        val repository = FakeRepository(waypoints = 8)
        val useCase = GetRouteWeatherUseCase(repository)

        val result = useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )

        assertEquals(8, result.forecasts.size)
        assertEquals(8, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals("Berlin", result.origin.name)
        assertEquals("Hamburg", result.destination.name)
        assertTrue(
            "expected genuine parallelism, saw ${repository.maxConcurrent.get()}",
            repository.maxConcurrent.get() > 1
        )
    }

    @Test
    fun `semaphore caps the number of in-flight weather requests`() = runTest {
        val repository = FakeRepository(weatherDelayMs = 100, waypoints = 12)
        val useCase = GetRouteWeatherUseCase(repository)

        val start = currentTime
        useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )
        val elapsed = currentTime - start

        assertTrue(
            "concurrency exceeded the semaphore limit: ${repository.maxConcurrent.get()}",
            repository.maxConcurrent.get() <= GetRouteWeatherUseCase.MAX_CONCURRENT_REQUESTS
        )
        // 12 requests / 4 permits = 3 sequential batches of 100ms (plus the geocoding round).
        assertTrue("elapsed=$elapsed", elapsed >= 300)
    }

    @Test
    fun `origin and destination are geocoded concurrently`() = runTest {
        val repository = FakeRepository(geocodeDelayMs = 200, waypoints = 2)
        val useCase = GetRouteWeatherUseCase(repository)

        val start = currentTime
        useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )
        val elapsed = currentTime - start

        assertTrue("elapsed=$elapsed", elapsed < 400)
    }

    @Test
    fun `a single failing waypoint degrades to a Failure row without cancelling the rest`() =
        runTest {
            val repository = FakeRepository(failEveryNth = 3, waypoints = 9)
            val useCase = GetRouteWeatherUseCase(repository)

            val result = useCase(
                GetRouteWeatherUseCase.Params(
                    originQuery = "Berlin",
                    destinationQuery = "Hamburg",
                    departureEpochSeconds = departure
                )
            )

            assertEquals(9, result.forecasts.size)
            assertTrue(result.failureCount > 0)
            assertTrue(result.successCount > 0)
            assertEquals(9, result.successCount + result.failureCount)
            val failure = result.forecasts.first { it is WaypointForecast.Failure }
            assertTrue((failure as WaypointForecast.Failure).message.isNotBlank())
        }

    @Test
    fun `etas increase monotonically from departure to arrival`() = runTest {
        val repository = FakeRepository(waypoints = 6)
        val useCase = GetRouteWeatherUseCase(repository)

        val result = useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )

        val etas = result.forecasts.map { it.waypoint.etaEpochSeconds }
        assertEquals(departure, etas.first())
        assertEquals(result.arrivalEpochSeconds, etas.last())
        assertEquals(etas.sorted(), etas)
    }

    @Test(expected = DriveCastException::class)
    fun `an unresolvable place aborts the pipeline`() = runTest {
        val repository = FakeRepository(geocodeResults = emptyList(), waypoints = 4)
        val useCase = GetRouteWeatherUseCase(repository)

        useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Nowhere",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )
    }

    @Test
    fun `every waypoint is labelled with its reverse geocoded place name`() = runTest {
        val repository = FakeRepository(waypoints = 4)
        val useCase = GetRouteWeatherUseCase(repository)

        val result = useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )

        assertEquals(4, repository.reverseGeocodeCalls.get())
        assertEquals("Town 0, Brandenburg, Germany", result.forecasts.first().placeLabel)
        assertTrue(result.forecasts.all { it.placeLabel.endsWith(", Germany") })
    }

    @Test
    fun `a route that starts and ends in one country leaves that country unsaid`() = runTest {
        val repository = FakeRepository(
            waypoints = 4,
            reverseGeocodePlace = { index ->
                ResolvedPlace(
                    locality = "Town $index",
                    adminArea = "Brandenburg",
                    country = "Germany",
                    countryCode = if (index == 2) "PL" else "DE"
                )
            }
        )
        val useCase = GetRouteWeatherUseCase(repository)

        val result = useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Dresden",
                departureEpochSeconds = departure
            )
        )

        assertEquals("Town 0, Brandenburg", result.forecasts[0].placeLabel)
        assertEquals("Town 1, Brandenburg", result.forecasts[1].placeLabel)
        assertEquals("Town 2, Brandenburg, Germany", result.forecasts[2].placeLabel)
        assertEquals("Town 3, Brandenburg", result.forecasts[3].placeLabel)
    }

    @Test
    fun `a failed reverse geocode at the origin does not cost the whole country rule`() = runTest {
        val repository = FakeRepository(
            waypoints = 4,
            reverseGeocodePlace = { index ->
                ResolvedPlace(
                    locality = "Town $index",
                    adminArea = "Brandenburg",
                    country = "Germany",
                    countryCode = if (index == 0) null else "DE"
                )
            }
        )
        val useCase = GetRouteWeatherUseCase(repository)

        val result = useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Dresden",
                departureEpochSeconds = departure
            )
        )

        assertTrue(result.forecasts.none { it.placeLabel.endsWith(", Germany") })
    }

    @Test
    fun `a reverse geocode outage still yields weather, labelled with coordinates`() = runTest {
        val repository = FakeRepository(reverseGeocodeFails = true, waypoints = 5)
        val useCase = GetRouteWeatherUseCase(repository)

        val result = useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )

        assertEquals(5, result.successCount)
        assertEquals(0, result.failureCount)
        assertTrue(
            "expected a coordinate fallback, got ${result.forecasts.map { it.placeLabel }}",
            result.forecasts.all { it.placeLabel.contains("°") }
        )
    }

    @Test
    fun `reverse geocoding shares the semaphore with the weather fan-out`() = runTest {
        val repository =
            FakeRepository(weatherDelayMs = 0, reverseGeocodeDelayMs = 100, waypoints = 8)
        val useCase = GetRouteWeatherUseCase(repository)

        val start = currentTime
        useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )
        val elapsed = currentTime - start

        // 8 reverse geocodes through 4 permits = at least two 100ms rounds.
        assertTrue("elapsed=$elapsed", elapsed >= 200)
        // ...but they still overlap rather than running one at a time.
        assertTrue("elapsed=$elapsed", elapsed < 800)
    }

    @Test
    fun `place and weather for one waypoint are fetched concurrently`() = runTest {
        val repository =
            FakeRepository(weatherDelayMs = 200, reverseGeocodeDelayMs = 200, waypoints = 2)
        val useCase = GetRouteWeatherUseCase(repository)

        val start = currentTime
        useCase(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )
        val elapsed = currentTime - start

        // 4 calls across 4 permits: sequential would cost 800ms, overlapped costs ~200ms.
        assertTrue("elapsed=$elapsed", elapsed < 400)
    }

    @Test
    fun `attaches the NWS alerts fetched for each waypoint`() = runTest {
        val repository = FakeRepository(
            waypoints = 3,
            alerts = listOf(
                WeatherAlert("Winter Storm Warning", AlertSeverity.SEVERE, WeatherAlertSource.NWS)
            )
        )

        val result = useCase(repository)

        assertEquals(3, result.alertCount)
        val first = result.forecasts.first() as WaypointForecast.Success
        assertEquals("Winter Storm Warning", first.topAlert?.event)
        assertEquals(WeatherAlertSource.NWS, first.topAlert?.source)
    }

    @Test
    fun `a real NWS alert outranks the one derived from the forecast code`() = runTest {
        // The waypoint's condition is snow, which would derive a Moderate alert on its own. A
        // government warning is the better statement, so it must win rather than merely tie.
        val repository = FakeRepository(
            waypoints = 2,
            condition = WeatherCondition.SNOW,
            alerts = listOf(
                WeatherAlert("Blizzard Warning", AlertSeverity.EXTREME, WeatherAlertSource.NWS)
            )
        )

        val alert = (useCase(repository).forecasts.first() as WaypointForecast.Success).topAlert

        assertEquals("Blizzard Warning", alert?.event)
        assertEquals(WeatherAlertSource.NWS, alert?.source)
    }

    @Test
    fun `falls back to a derived alert where NWS has no coverage`() = runTest {
        // Outside the United States the endpoint answers 400 and the repository reports no
        // alerts, so the WMO code is all there is to go on.
        val repository = FakeRepository(
            waypoints = 2,
            condition = WeatherCondition.FREEZING_RAIN,
            alerts = emptyList()
        )

        val alert = (useCase(repository).forecasts.first() as WaypointForecast.Success).topAlert

        assertEquals(WeatherAlertSource.DERIVED, alert?.source)
        assertEquals(AlertSeverity.SEVERE, alert?.severity)
    }

    @Test
    fun `a benign forecast with no NWS alerts carries no alert at all`() = runTest {
        val result = useCase(FakeRepository(waypoints = 3))

        assertEquals(0, result.alertCount)
        assertTrue(result.forecasts.filterIsInstance<WaypointForecast.Success>().all { it.alerts.isEmpty() })
    }

    @Test
    fun `an alerts outage degrades to no alerts without disturbing the weather`() = runTest {
        val repository = FakeRepository(waypoints = 3, alertsFail = true)

        val result = useCase(repository)

        // The whole point of the per-call Result: one failing sibling must not take the row down.
        assertEquals(3, result.successCount)
        assertEquals(0, result.failureCount)
        assertEquals(0, result.alertCount)
    }

    @Test
    fun `alerts share the semaphore with the rest of the fan-out`() = runTest {
        val repository = FakeRepository(waypoints = 6, alertDelayMs = 20)

        useCase(repository)

        assertEquals(6, repository.alertCalls.get())
        assertTrue(
            "maxConcurrent=${repository.maxConcurrent.get()}",
            repository.maxConcurrent.get() <= GetRouteWeatherUseCase.MAX_CONCURRENT_REQUESTS
        )
    }

    @Test
    fun `the benchmark temperature is the origin's own`() = runTest {
        val result = useCase(FakeRepository(waypoints = 4))

        val origin = result.forecasts.first() as WaypointForecast.Success
        assertEquals(origin.weather.temperature, result.benchmarkTemperature)
    }

    private suspend fun useCase(repository: FakeRepository) =
        GetRouteWeatherUseCase(repository)(
            GetRouteWeatherUseCase.Params(
                originQuery = "Berlin",
                destinationQuery = "Hamburg",
                departureEpochSeconds = departure
            )
        )

    private class FakeRepository(
        private val weatherDelayMs: Long = 10,
        private val geocodeDelayMs: Long = 0,
        private val failEveryNth: Int = 0,
        private val geocodeResults: List<Place>? = null,
        private val reverseGeocodeDelayMs: Long = 0,
        private val reverseGeocodeFails: Boolean = false,
        private val alerts: List<WeatherAlert> = emptyList(),
        private val condition: WeatherCondition = WeatherCondition.PARTLY_CLOUDY,
        private val alertsFail: Boolean = false,
        private val alertDelayMs: Long = 0,
        private val waypoints: Int = 3,
        private val reverseGeocodePlace: (Int) -> ResolvedPlace = { index ->
            ResolvedPlace(
                locality = "Town $index",
                adminArea = "Brandenburg",
                country = "Germany",
                countryCode = if (index == 0) "DE" else "PL"
            )
        }
    ) : DriveCastRepository {

        val reverseGeocodeCalls = AtomicInteger(0)
        val alertCalls = AtomicInteger(0)

        val maxConcurrent = AtomicInteger(0)
        private val inFlight = AtomicInteger(0)
        private val weatherCalls = AtomicInteger(0)

        override suspend fun geocode(query: String): List<Place> {
            if (geocodeDelayMs > 0) delay(geocodeDelayMs)
            geocodeResults?.let { return it }
            return listOf(
                Place(
                    name = query,
                    admin = null,
                    country = "DE",
                    location = LatLng(52.0, 13.0)
                )
            )
        }

        override suspend fun fetchRoute(origin: LatLng, destination: LatLng): Route = Route(
            geometry = (0..40).map { LatLng(52.0 + it * 0.05, 13.0 - it * 0.03) },
            durationSeconds = (waypoints - 1) * 3_600.0,
            distanceMeters = 290_000.0
        )

        override suspend fun fetchWeather(location: LatLng, atEpochSeconds: Long, unitSystem: UnitSystem): Weather {
            val current = inFlight.incrementAndGet()
            maxConcurrent.updateAndGet { previous -> maxOf(previous, current) }
            try {
                delay(weatherDelayMs)
                val call = weatherCalls.incrementAndGet()
                if (failEveryNth > 0 && call % failEveryNth == 0) {
                    throw DriveCastException("simulated upstream failure #$call")
                }
                return Weather(
                    temperature = 54.5,
                    apparentTemperature = 51.8,
                    precipitation = 0.02,
                    precipitationProbability = 25,
                    windSpeed = 8.7,
                    condition = condition,
                    forecastEpochSeconds = atEpochSeconds
                )
            } finally {
                inFlight.decrementAndGet()
            }
        }

        override suspend fun fetchAlerts(location: LatLng): List<WeatherAlert> {
            alertCalls.incrementAndGet()
            val current = inFlight.incrementAndGet()
            maxConcurrent.updateAndGet { previous -> maxOf(previous, current) }
            try {
                if (alertDelayMs > 0) delay(alertDelayMs)
                if (alertsFail) throw DriveCastException("simulated alert outage")
                return alerts
            } finally {
                inFlight.decrementAndGet()
            }
        }


        override suspend fun reverseGeocode(location: LatLng): ResolvedPlace {
            if (reverseGeocodeDelayMs > 0) delay(reverseGeocodeDelayMs)
            val call = reverseGeocodeCalls.getAndIncrement()
            if (reverseGeocodeFails) throw DriveCastException("simulated reverse geocode outage")
            return reverseGeocodePlace(call)
        }
    }
}
