package dev.dani.drivecast.domain.model

data class Waypoint(
    val index: Int,
    val location: LatLng,
    val offsetSeconds: Long,
    val etaEpochSeconds: Long,
    val distanceAlongRouteMeters: Double,
    val fractionOfRoute: Double
)
