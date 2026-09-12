package dev.dani.drivecast.domain.location

import dev.dani.drivecast.domain.model.LatLng

interface LocationProvider {

    fun hasPermission(): Boolean
    suspend fun currentLocation(): LocationData
}

sealed interface LocationData {

    data class Available(val location: LatLng) : LocationData

    // Either not granted or permanently denied
    data object PermissionMissing : LocationData

    // Location services switched off
    data object LocationDisabled : LocationData

    data object Unavailable : LocationData
}
