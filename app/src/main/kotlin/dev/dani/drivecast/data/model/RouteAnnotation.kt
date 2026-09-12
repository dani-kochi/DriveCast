package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteAnnotation(
    @SerialName("duration") val duration: List<Double> = emptyList()
)