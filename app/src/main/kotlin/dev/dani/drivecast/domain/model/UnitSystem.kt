package dev.dani.drivecast.domain.model

enum class UnitSystem(
    val temperature: String,
    val windSpeed: String,
    val precipitation: String,
    val distance: String,
) {
    IMPERIAL("°F", "mph", "inch", "mi"),
    METRIC("°C", "kph", "mm", "m")
}