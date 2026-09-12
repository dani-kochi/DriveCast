package dev.dani.drivecast.data.model

import dev.dani.drivecast.domain.model.UnitSystem

enum class WeatherUnit(
    val temperature: String,
    val windSpeed: String,
    val precipitation: String
) {
    IMPERIAL("fahrenheit", "mph", "inch"),
    METRIC("celsius", "kmh", "mm");

    companion object {
        fun from(unitSystem: UnitSystem): WeatherUnit = when (unitSystem) {
            UnitSystem.IMPERIAL -> IMPERIAL
            UnitSystem.METRIC -> METRIC
        }
    }
}