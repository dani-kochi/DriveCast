package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteLeg(
    @SerialName("duration") val duration: Double,
    @SerialName("distance") val distance: Double,
    @SerialName("steps") val steps: List<RouteStep> = emptyList(),
    @SerialName("annotation") val annotation: RouteAnnotation? = null
)