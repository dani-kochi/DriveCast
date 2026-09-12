package dev.dani.drivecast.data.repository

import dev.dani.drivecast.domain.model.LatLng
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.Route
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.Weather
import dev.dani.drivecast.domain.model.WeatherAlert

interface DriveCastRepository {

    // searches for places matching [query] and returns a list of results
    suspend fun geocode(query: String): List<Place>

    // Fetches a driving route between two coordinates
    suspend fun fetchRoute(origin: LatLng, destination: LatLng): Route

    // Get the hourly forecast for [location]
    suspend fun fetchWeather(location: LatLng, atEpochSeconds: Long, unitSystem: UnitSystem): Weather

    // Resolves a coordinate back into place name.
    suspend fun reverseGeocode(location: LatLng): ResolvedPlace

    // Get active weather alerts for a [location]
    suspend fun fetchAlerts(location: LatLng): List<WeatherAlert>
}
