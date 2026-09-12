package dev.dani.drivecast.domain.model

data class RouteWeather(
    val origin: Place,
    val destination: Place,
    val route: Route,
    val departureEpochSeconds: Long,
    val forecasts: List<WaypointForecast>,
    val unitSystem: UnitSystem,
) {
    val arrivalEpochSeconds: Long
        get() = departureEpochSeconds + route.durationSeconds.toLong()

    val successCount: Int get() = forecasts.count { it is WaypointForecast.Success }

    val failureCount: Int get() = forecasts.count { it is WaypointForecast.Failure }

    val hazardousCount: Int
        get() = forecasts.count { it is WaypointForecast.Success && it.weather.condition.isHazardous }

    val alertCount: Int
        get() = forecasts.count { it is WaypointForecast.Success && it.alerts.isNotEmpty() }

    // The temperature every other waypoint is compared against the one at the origin.
    val benchmarkTemperature: Double?
        get() = (forecasts.firstOrNull() as? WaypointForecast.Success)?.weather?.temperature
}