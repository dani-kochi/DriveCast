package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RouteResponse(
    @SerialName("code") val code: String,
    @SerialName("routes") val routes: List<RouteData> = emptyList(),
    @SerialName("message") val message: String? = null
)