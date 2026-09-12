package dev.dani.drivecast.data.remote

import dev.dani.drivecast.data.model.NwsAlertsResponse
import retrofit2.http.GET
import retrofit2.http.Query

// US National Weather Service active alerts: https://api.weather.gov
// Available only for United States
interface USNationalWeatherServiceAlertsApi {

    @GET("alerts/active")
    suspend fun activeAlerts(
        @Query("point") point: String // Format is `"LAT,LNG"`
    ): NwsAlertsResponse
}
