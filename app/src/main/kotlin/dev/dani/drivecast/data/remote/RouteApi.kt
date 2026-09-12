package dev.dani.drivecast.data.remote

import dev.dani.drivecast.data.model.RouteResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

// OSRM Router: https://router.project-osrm.org
interface RouteApi {

    /**
     * @param coordinates `lon,lat;lon,lat`
     * @param geometries `polyline_` - `polyline`, `polyline6`, etc.
     * @param annotations `duration` To get hourly segments. Requires `overview=full`
     */
    @GET("route/v1/driving/{coordinates}")
    suspend fun route(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("geometries") geometries: String,
        @Query("steps") steps: Boolean = false,
        @Query("annotations") annotations: String = "duration",
        @Query("overview") overview: String = "full",
    ): RouteResponse
}
