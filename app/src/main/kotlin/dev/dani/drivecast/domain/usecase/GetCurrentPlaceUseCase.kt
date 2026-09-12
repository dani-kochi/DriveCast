package dev.dani.drivecast.domain.usecase

import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.domain.location.LocationData
import dev.dani.drivecast.domain.location.LocationProvider
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.WaypointPlaceFormatter
import timber.log.Timber
import javax.inject.Inject

class GetCurrentPlaceUseCase @Inject constructor(
    private val locationProvider: LocationProvider,
    private val repository: DriveCastRepository
) {
    sealed interface Result {
        data class Resolved(val place: Place) : Result
        data class Unavailable(val reason: LocationData) : Result
    }

    suspend operator fun invoke(): Result {
        val locationData = locationProvider.currentLocation()
        val location = (locationData as? LocationData.Available)?.location
            ?: return Result.Unavailable(locationData)

        val resolved: ResolvedPlace? = try {
            repository.reverseGeocode(location)
        } catch (_: Throwable) {
            Timber.tag("GetCurrentPlaceUseCase").e("Failed to reverse geocode location")
            null
        }

        val label = WaypointPlaceFormatter.format(
            place = resolved,
            location = location,
            referenceCountryCode = resolved?.countryCode
        )

        return Result.Resolved(
            Place(
                name = label,
                admin = null,
                country = null,
                location = location
            )
        )
    }
}
