package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteStep(
    @SerialName("duration") val duration: Double,
    @SerialName("distance") val distance: Double,
    @SerialName("name") val name: String? = null
)