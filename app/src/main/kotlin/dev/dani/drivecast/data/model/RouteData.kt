package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteData(
    /** Encoded polyline. Precision depends on the requested `geometries` parameter. */
    @SerialName("geometry") val geometry: String,
    @SerialName("duration") val duration: Double,
    @SerialName("distance") val distance: Double,
    @SerialName("legs") val legs: List<RouteLeg> = emptyList()
)