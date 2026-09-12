package dev.dani.drivecast.data.remote

import dev.dani.drivecast.data.model.WeatherResponse
import dev.dani.drivecast.data.model.WeatherUnit
import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo forecast API: https://api.open-meteo.com
interface WeatherApi {

    @GET("v1/forecast")
    suspend fun hourlyForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String =
            "temperature_2m,apparent_temperature,precipitation,precipitation_probability,weather_code,wind_speed_10m",
        @Query("temperature_unit") temperatureUnit: String = WeatherUnit.IMPERIAL.temperature,
        @Query("wind_speed_unit") windSpeedUnit: String = WeatherUnit.IMPERIAL.windSpeed,
        @Query("precipitation_unit") precipitationUnit: String = WeatherUnit.IMPERIAL.precipitation,
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("timezone") timezone: String = "UTC",
        @Query("forecast_days") forecastDays: Int = 3
    ): WeatherResponse
}
