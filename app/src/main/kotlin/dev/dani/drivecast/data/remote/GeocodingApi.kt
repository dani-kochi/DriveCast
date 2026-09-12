package dev.dani.drivecast.data.remote

import dev.dani.drivecast.data.model.GeocodingResponse
import retrofit2.http.GET
import retrofit2.http.Query

// Open-Meteo geocoding API: https://geocoding-api.open-meteo.com
interface GeocodingApi {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}
