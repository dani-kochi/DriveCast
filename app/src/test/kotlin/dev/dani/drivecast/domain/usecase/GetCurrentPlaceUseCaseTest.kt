package dev.dani.drivecast.domain.usecase

import dev.dani.drivecast.data.repository.DriveCastException
import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.domain.location.LocationData
import dev.dani.drivecast.domain.location.LocationProvider
import dev.dani.drivecast.domain.model.LatLng
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.Route
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.Weather
import dev.dani.drivecast.domain.model.WeatherAlert
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCurrentPlaceUseCaseTest {

    private val dallas = LatLng(32.7767, -96.7970)

    @Test
    fun `the label omits the country because it is the user's own`() = runTest {
        val useCase = useCase(
            fix = LocationData.Available(dallas),
            resolved = ResolvedPlace(
                locality = "Dallas",
                adminArea = "Texas",
                country = "United States of America",
                countryCode = "US"
            )
        )

        val result = useCase() as GetCurrentPlaceUseCase.Result.Resolved

        assertEquals("Dallas, TX", result.place.name)
        // displayName is what lands in the text field, so it must not repeat the label.
        assertEquals("Dallas, TX", result.place.displayName)
        assertEquals(dallas, result.place.location)
    }

    @Test
    fun `the label follows the fallback chain when no locality resolves`() = runTest {
        val useCase = useCase(
            fix = LocationData.Available(dallas),
            resolved = ResolvedPlace(county = "Ellis County", countryCode = "US")
        )

        val result = useCase() as GetCurrentPlaceUseCase.Result.Resolved

        assertEquals("Ellis County", result.place.displayName)
    }

    @Test
    fun `a failed reverse geocode still pins the coordinates`() = runTest {
        val useCase = useCase(fix = LocationData.Available(dallas), reverseGeocodeFails = true)

        val result = useCase() as GetCurrentPlaceUseCase.Result.Resolved

        assertEquals(dallas, result.place.location)
        assertTrue(result.place.displayName, result.place.displayName.contains("°"))
    }

    @Test
    fun `an unavailable fix is reported verbatim and costs no network call`() = runTest {
        listOf(
            LocationData.PermissionMissing,
            LocationData.LocationDisabled,
            LocationData.Unavailable
        ).forEach { fix ->
            val repository = FakeRepository()
            val useCase = GetCurrentPlaceUseCase(FakeLocationProvider(fix), repository)

            val result = useCase()

            assertEquals(
                GetCurrentPlaceUseCase.Result.Unavailable(fix),
                result as GetCurrentPlaceUseCase.Result.Unavailable
            )
            assertEquals("$fix should not have been geocoded", 0, repository.reverseGeocodeCalls)
        }
    }

    private fun useCase(
        fix: LocationData,
        resolved: ResolvedPlace = ResolvedPlace(),
        reverseGeocodeFails: Boolean = false
    ) = GetCurrentPlaceUseCase(
        FakeLocationProvider(fix),
        FakeRepository(resolved, reverseGeocodeFails)
    )

    private class FakeLocationProvider(private val fix: LocationData) : LocationProvider {
        override fun hasPermission(): Boolean = fix != LocationData.PermissionMissing
        override suspend fun currentLocation(): LocationData = fix
    }

    private class FakeRepository(
        private val resolved: ResolvedPlace = ResolvedPlace(),
        private val fails: Boolean = false
    ) : DriveCastRepository {
        var reverseGeocodeCalls = 0
            private set

        override suspend fun fetchAlerts(location: LatLng): List<WeatherAlert> = emptyList()

        override suspend fun reverseGeocode(location: LatLng): ResolvedPlace {
            reverseGeocodeCalls++
            if (fails) throw DriveCastException("reverse geocoder down")
            return resolved
        }

        override suspend fun geocode(query: String): List<Place> = unsupported()
        override suspend fun fetchRoute(origin: LatLng, destination: LatLng): Route = unsupported()
        override suspend fun fetchWeather(location: LatLng, atEpochSeconds: Long, unitSystem: UnitSystem): Weather =
            unsupported()

        private fun unsupported(): Nothing =
            throw UnsupportedOperationException("not exercised by these tests")
    }
}
