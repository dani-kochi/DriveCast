package dev.dani.drivecast.domain.model

// WMO weather interpretation codes used by Open-Meteo.
enum class WeatherCondition(val label: String) {
    CLEAR("Clear sky"),
    MAINLY_CLEAR("Mainly clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    OVERCAST("Overcast"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    FREEZING_DRIZZLE("Freezing drizzle"),
    RAIN("Rain"),
    FREEZING_RAIN("Freezing rain"),
    SNOW("Snow"),
    SNOW_GRAINS("Snow grains"),
    RAIN_SHOWERS("Rain showers"),
    SNOW_SHOWERS("Snow showers"),
    THUNDERSTORM("Thunderstorm"),
    THUNDERSTORM_HAIL("Thunderstorm with hail"),
    UNKNOWN("Unknown");

    val isHazardous: Boolean
        get() = this in HAZARDOUS

    fun derivedAlert(): WeatherAlert? {
        val severity = when (this) {
            THUNDERSTORM_HAIL,
            FREEZING_RAIN -> AlertSeverity.SEVERE

            THUNDERSTORM,
            FREEZING_DRIZZLE,
            SNOW,
            SNOW_SHOWERS -> AlertSeverity.MODERATE

            SNOW_GRAINS,
            FOG -> AlertSeverity.MINOR

            else -> return null
        }
        return WeatherAlert(event = label, severity = severity, source = WeatherAlertSource.DERIVED)
    }


    companion object {
        private val HAZARDOUS = setOf(
            FREEZING_DRIZZLE,
            FREEZING_RAIN,
            SNOW,
            SNOW_GRAINS,
            SNOW_SHOWERS,
            THUNDERSTORM,
            THUNDERSTORM_HAIL,
            FOG
        )

        fun fromWmoCode(code: Int): WeatherCondition = when (code) {
            0 -> CLEAR
            1 -> MAINLY_CLEAR
            2 -> PARTLY_CLOUDY
            3 -> OVERCAST
            45, 48 -> FOG
            51, 53, 55 -> DRIZZLE
            56, 57 -> FREEZING_DRIZZLE
            61, 63, 65 -> RAIN
            66, 67 -> FREEZING_RAIN
            71, 73, 75 -> SNOW
            77 -> SNOW_GRAINS
            80, 81, 82 -> RAIN_SHOWERS
            85, 86 -> SNOW_SHOWERS
            95 -> THUNDERSTORM
            96, 99 -> THUNDERSTORM_HAIL
            else -> UNKNOWN
        }
    }
}
