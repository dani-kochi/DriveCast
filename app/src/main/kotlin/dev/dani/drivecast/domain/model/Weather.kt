package dev.dani.drivecast.domain.model

// Weather at a location
data class Weather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val precipitation: Double,
    val precipitationProbability: Int?,
    val windSpeed: Double?,
    val condition: WeatherCondition,
    val forecastEpochSeconds: Long
)

