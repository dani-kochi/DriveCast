package dev.dani.drivecast.domain.model

data class Route(
    val geometry: List<LatLng>,
    val durationSeconds: Double,
    val distanceMeters: Double,
    val segmentDurationsSeconds: List<Double> = emptyList()
)
