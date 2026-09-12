package dev.dani.drivecast.domain.model

sealed interface WaypointForecast {
    val waypoint: Waypoint
    val placeLabel: String

    data class Success(
        override val waypoint: Waypoint,
        override val placeLabel: String,
        val weather: Weather,
        val alerts: List<WeatherAlert> = emptyList()
    ) : WaypointForecast {
        val topAlert: WeatherAlert? get() = alerts.maxByOrNull { it.severity.ordinal }
    }

    data class Failure(
        override val waypoint: Waypoint,
        override val placeLabel: String,
        val message: String
    ) : WaypointForecast
}