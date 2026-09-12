package dev.dani.drivecast.ui

import dev.dani.drivecast.domain.model.LatLng
import java.util.Locale
import kotlin.math.roundToInt

// https://developers.google.com/maps/documentation/urls/get-started
// Builds Google Maps URL for a sampled route.
object GoogleMapsLink {

    private const val BASE = "https://www.google.com/maps/dir/?api=1"
    const val MAX_WAYPOINTS = 9 // Google Maps only supports 9 waypoints
    const val MAX_WAYPOINTS_MOBILE_BROWSER = 3 // Up to three waypoints for mobile browsers
    const val MAX_URL_LENGTH = 2048
    private const val COORDINATE_FORMAT = "%.5f"

    fun directionsUrl(stops: List<LatLng>, maxWaypoints: Int = MAX_WAYPOINTS): String {
        if (stops.isEmpty()) return BASE

        val origin = stops.first()
        val destination = stops.last()
        val intermediates = if (stops.size <= 2) {
            emptyList()
        } else {
            downSample(stops.subList(1, stops.size - 1), maxWaypoints)
        }

        return buildString {
            append(BASE)
            append("&origin=").append(encode(origin))
            if (stops.size > 1) append("&destination=").append(encode(destination))
            if (intermediates.isNotEmpty()) {
                append("&waypoints=")
                append(intermediates.joinToString("%7C") { encode(it) })
            }
            append("&travelmode=driving")
        }
    }

    fun downSample(points: List<LatLng>, max: Int): List<LatLng> {
        if (max <= 0) return emptyList()
        if (points.size <= max) return points
        if (max == 1) return listOf(points.first())

        val last = points.size - 1
        return (0 until max)
            .map { i -> (i.toDouble() * last / (max - 1)).roundToInt() }
            .distinct()
            .map { points[it] }
    }

    private fun encode(point: LatLng): String {
        val latitude = String.format(Locale.US, COORDINATE_FORMAT, point.latitude)
        val longitude = String.format(Locale.US, COORDINATE_FORMAT, point.longitude)
        // The comma separating a coordinate pair must be percent-encoded per the Maps URLs docs.
        return "$latitude%2C$longitude"
    }
}
