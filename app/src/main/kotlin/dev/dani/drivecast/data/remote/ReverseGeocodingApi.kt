package dev.dani.drivecast.data.remote

import dev.dani.drivecast.data.model.ReverseGeocodeResponse
import retrofit2.http.GET
import retrofit2.http.Query

// BigDataCloud reverse geocoding: https://api-bdc.net
interface ReverseGeocodingApi {

    @GET("data/reverse-geocode-client")
    suspend fun reverseGeocode(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("localityLanguage") localityLanguage: String = "en"
    ): ReverseGeocodeResponse
}
