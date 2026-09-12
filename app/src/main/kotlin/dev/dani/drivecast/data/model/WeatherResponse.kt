package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    @SerialName("latitude") val latitude: Double = 0.0,
    @SerialName("longitude") val longitude: Double = 0.0,
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Long = 0L,
    @SerialName("hourly") val hourly: HourlyWeather? = null
)