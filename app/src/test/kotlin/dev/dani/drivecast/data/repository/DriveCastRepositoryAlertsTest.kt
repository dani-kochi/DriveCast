package dev.dani.drivecast.data.repository

import dev.dani.drivecast.data.model.NwsAlertFeature
import dev.dani.drivecast.data.model.NwsAlertProperties
import dev.dani.drivecast.data.model.NwsAlertsResponse
import dev.dani.drivecast.data.remote.USNationalWeatherServiceAlertsApi
import dev.dani.drivecast.domain.model.AlertCertainty
import dev.dani.drivecast.domain.model.AlertSeverity
import dev.dani.drivecast.domain.model.WeatherAlertSource
import dev.dani.drivecast.domain.model.AlertUrgency
import dev.dani.drivecast.domain.model.LatLng
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale

/**
 * The alert half of the repository, in isolation.
 *
 * Every expectation here mirrors something observed in a live `api.weather.gov` response rather
 * than something the DTOs merely permit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveCastRepositoryAlertsTest {

    @Test
    fun `maps NWS features into domain alerts`() = runTest {
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = alertsResponse(
                    "Winter Storm Warning" to "Severe",
                    "Special Weather Statement" to "Moderate"
                )
            )
        )

        val alerts = repository.fetchAlerts(LatLng(39.47, -82.17))

        assertEquals(2, alerts.size)
        assertEquals("Winter Storm Warning", alerts[0].event)
        assertEquals(AlertSeverity.SEVERE, alerts[0].severity)
        assertTrue(alerts.all { it.source == WeatherAlertSource.NWS })
    }

    @Test
    fun `collapses the duplicate alerts adjacent zones produce for one point`() = runTest {
        // Verified live: point=39.47,-82.17 returned "Severe Thunderstorm Warning" twice.
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = alertsResponse(
                    "Severe Thunderstorm Warning" to "Severe",
                    "Severe Thunderstorm Warning" to "Severe",
                    "Special Weather Statement" to "Moderate",
                    "Special Weather Statement" to "Moderate"
                )
            )
        )

        val alerts = repository.fetchAlerts(LatLng(39.47, -82.17))

        assertEquals(
            listOf("Severe Thunderstorm Warning", "Special Weather Statement"),
            alerts.map { it.event }
        )
    }

    @Test
    fun `a collapsed duplicate keeps the worst severity of the pair`() = runTest {
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = alertsResponse(
                    "Flood Watch" to "Minor",
                    "Flood Watch" to "Severe"
                )
            )
        )

        val alerts = repository.fetchAlerts(LatLng(39.47, -82.17))

        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.SEVERE, alerts.single().severity)
    }

    @Test
    fun `sorts the most serious alert first so the UI can take the head`() = runTest {
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = alertsResponse(
                    "Frost Advisory" to "Minor",
                    "Tornado Warning" to "Extreme",
                    "Wind Advisory" to "Moderate"
                )
            )
        )

        val alerts = repository.fetchAlerts(LatLng(39.47, -82.17))

        assertEquals(
            listOf("Tornado Warning", "Wind Advisory", "Frost Advisory"),
            alerts.map { it.event }
        )
    }

    @Test
    fun `a point outside NWS coverage reads as no alerts rather than an error`() = runTest {
        // Verified with curl: 52.52,13.40 answers HTTP 400 'Parameter "point" is invalid: out of
        // bounds'. Treating that as a failure would break every route outside the United States.
        val repository = repositoryWith(FakeUSNationalWeatherServiceAlertsApi(failure = httpException(400)))

        assertEquals(emptyList<Any>(), repository.fetchAlerts(LatLng(52.52, 13.40)))
    }

    @Test(expected = HttpException::class)
    fun `a genuine server failure still propagates`() = runTest {
        val repository = repositoryWith(FakeUSNationalWeatherServiceAlertsApi(failure = httpException(503)))

        repository.fetchAlerts(LatLng(39.47, -82.17))
    }

    @Test
    fun `a feature with no event name is dropped rather than shown blank`() = runTest {
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = NwsAlertsResponse(
                    features = listOf(
                        NwsAlertFeature(NwsAlertProperties(event = "  ", severity = "Severe")),
                        NwsAlertFeature(properties = null),
                        NwsAlertFeature(
                            NwsAlertProperties(event = "Heat Advisory", severity = "Minor")
                        )
                    )
                )
            )
        )

        val alerts = repository.fetchAlerts(LatLng(39.47, -82.17))

        assertEquals(listOf("Heat Advisory"), alerts.map { it.event })
    }

    @Test
    fun `an empty response is not an error`() = runTest {
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(response = NwsAlertsResponse(features = null))
        )

        assertEquals(emptyList<Any>(), repository.fetchAlerts(LatLng(39.47, -82.17)))
    }

    @Test
    fun `formats the point with a dot separator whatever the device locale is`() = runTest {
        // A comma-decimal locale would send "39,4700,-82,1700" and the endpoint would reject it.
        val original = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
        try {
            val api = FakeUSNationalWeatherServiceAlertsApi(response = NwsAlertsResponse())
            repositoryWith(api).fetchAlerts(LatLng(39.47, -82.17))
            assertEquals("39.4700,-82.1700", api.lastPoint)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `carries the long-form text through to the domain`() = runTest {
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = NwsAlertsResponse(
                    features = listOf(
                        NwsAlertFeature(
                            NwsAlertProperties(
                                event = "Flood Advisory",
                                severity = "Minor",
                                headline = "Flood Advisory issued September 9 at 9:43PM MDT",
                                description = "* WHAT...Urban flooding is expected.",
                                instruction = "Do not drive on flooded roads.",
                                urgency = "Expected",
                                certainty = "Likely"
                            )
                        )
                    )
                )
            )
        )

        val alert = repository.fetchAlerts(LatLng(39.47, -82.17)).single()

        assertEquals("Flood Advisory issued September 9 at 9:43PM MDT", alert.headline)
        assertEquals("* WHAT...Urban flooding is expected.", alert.description)
        assertEquals("Do not drive on flooded roads.", alert.instruction)
        assertEquals(AlertUrgency.EXPECTED, alert.urgency)
        assertEquals(AlertCertainty.LIKELY, alert.certainty)
    }

    @Test
    fun `parses the CAP urgency and certainty the sheet spells out`() = runTest {
        // Every one of the 254 live alerts sampled carried both, so these are load-bearing
        // fields rather than optional decoration -- but an unrecognised or absent value has to
        // degrade to Unknown rather than to a crash, because CAP allows values NWS does not use.
        val repository = repositoryWith(
            FakeUSNationalWeatherServiceAlertsApi(
                response = NwsAlertsResponse(
                    features = listOf(
                        NwsAlertFeature(
                            NwsAlertProperties(
                                event = "Tornado Warning",
                                severity = "Extreme",
                                urgency = "immediate",
                                certainty = "OBSERVED"
                            )
                        ),
                        NwsAlertFeature(
                            NwsAlertProperties(
                                event = "Special Weather Statement",
                                severity = "Minor",
                                urgency = "Whenever",
                                certainty = null
                            )
                        )
                    )
                )
            )
        )

        val alerts = repository.fetchAlerts(LatLng(39.47, -82.17))

        val tornado = alerts.single { it.event == "Tornado Warning" }
        assertEquals(AlertUrgency.IMMEDIATE, tornado.urgency)
        assertEquals(AlertCertainty.OBSERVED, tornado.certainty)

        val statement = alerts.single { it.event == "Special Weather Statement" }
        assertEquals(AlertUrgency.UNKNOWN, statement.urgency)
        assertEquals(AlertCertainty.UNKNOWN, statement.certainty)
    }

    @Test
    fun `a missing instruction arrives as null rather than an empty string`() {
        // 58% of live alerts have no instruction, and blank text would make a row look expandable
        // when there is nothing behind it.
        runTest {
            val repository = repositoryWith(
                FakeUSNationalWeatherServiceAlertsApi(
                    response = NwsAlertsResponse(
                        features = listOf(
                            NwsAlertFeature(
                                NwsAlertProperties(
                                    event = "Heat Advisory",
                                    severity = "Minor",
                                    description = "It will be hot.",
                                    instruction = "   "
                                )
                            )
                        )
                    )
                )
            )

            val alert = repository.fetchAlerts(LatLng(39.47, -82.17)).single()

            assertNull(alert.instruction)
            assertNull(alert.headline)
        }
    }

    private fun repositoryWith(USNationalWeatherServiceAlertsApi: USNationalWeatherServiceAlertsApi) = DriveCastRepositoryImpl(
        geocodingApi = unusedApi(),
        routeApi = unusedApi(),
        weatherApi = unusedApi(),
        reverseGeocodingApi = unusedApi(),
        usNwsAlertsApi = USNationalWeatherServiceAlertsApi,
        ioDispatcher = UnconfinedTestDispatcher()
    )

    private fun alertsResponse(vararg events: Pair<String, String>) = NwsAlertsResponse(
        features = events.map { (event, severity) ->
            NwsAlertFeature(NwsAlertProperties(event = event, severity = severity))
        }
    )

    private fun httpException(code: Int) = HttpException(
        Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType()))
    )

    private class FakeUSNationalWeatherServiceAlertsApi(
        private val response: NwsAlertsResponse? = null,
        private val failure: Throwable? = null
    ) : USNationalWeatherServiceAlertsApi {

        var lastPoint: String? = null
            private set

        override suspend fun activeAlerts(point: String): NwsAlertsResponse {
            lastPoint = point
            failure?.let { throw it }
            return response!!
        }
    }
}

/** These tests only exercise the alert path; anything else being touched is itself a failure. */
private inline fun <reified T : Any> unusedApi(): T =
    java.lang.reflect.Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, method, _ -> error("unexpected call to ${method.name}") } as T
