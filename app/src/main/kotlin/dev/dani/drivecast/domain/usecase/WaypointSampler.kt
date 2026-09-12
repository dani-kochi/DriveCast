package dev.dani.drivecast.domain.usecase

import dev.dani.drivecast.domain.model.LatLng
import dev.dani.drivecast.domain.model.Route
import dev.dani.drivecast.domain.model.Waypoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object WaypointSampler {

    private const val EARTH_RADIUS_METERS = 6_371_008.8
    const val INTERVAL_SECONDS = 3_600L // One waypoint for 1 hour of driving
    const val MAX_WAYPOINTS = 24
    const val THRESHOLD_SECONDS = 600L // 10 minutes threshold

    /**
     * @param route the decoded route, with duration for each segment.
     * @param departureEpochSeconds Departure time
     */
    fun sample(route: Route, departureEpochSeconds: Long): List<Waypoint> {
        val points = route.geometry
        if (points.isEmpty()) return emptyList()

        val cumulativeDistance = cumulativeDistances(points)
        val cumulativeDuration = cumulativeDurations(route, cumulativeDistance)
        val total = cumulativeDuration.last()

        if (points.size == 1 || total <= 0.0) {
            return points.endpoints().mapIndexed { index, point ->
                Waypoint(
                    index = index,
                    location = point,
                    offsetSeconds = 0L,
                    etaEpochSeconds = departureEpochSeconds,
                    distanceAlongRouteMeters = if (index == 0) 0.0 else cumulativeDistance.last(),
                    fractionOfRoute = if (index == 0) 0.0 else 1.0
                )
            }
        }

        return offsetsFor(total).mapIndexed { index, offset ->
            val position = locate(offset, points, cumulativeDistance, cumulativeDuration)
            Waypoint(
                index = index,
                location = position.location,
                offsetSeconds = offset.roundToLong(),
                etaEpochSeconds = departureEpochSeconds + offset.roundToLong(),
                distanceAlongRouteMeters = position.distanceMeters,
                fractionOfRoute = (offset / total).coerceIn(0.0, 1.0)
            )
        }
    }

    fun offsetsFor(totalDurationSeconds: Double): List<Double> {
        val interval = intervalSecondsFor(totalDurationSeconds).toDouble()
        return buildList {
            add(0.0)
            var offset = interval
            while (offset < totalDurationSeconds - THRESHOLD_SECONDS) {
                add(offset)
                offset += interval
            }
            add(totalDurationSeconds)
        }
    }

    fun intervalSecondsFor(totalDurationSeconds: Double): Long {
        if (totalDurationSeconds <= 0.0) return INTERVAL_SECONDS
        var multiplier = 1L
        while (
            waypointCountFor(totalDurationSeconds, INTERVAL_SECONDS * multiplier) > MAX_WAYPOINTS
        ) {
            multiplier++
        }
        return INTERVAL_SECONDS * multiplier
    }

    private fun waypointCountFor(totalDurationSeconds: Double, interval: Long): Int {
        var count = 2 // origin + destination
        var offset = interval.toDouble()
        while (offset < totalDurationSeconds - THRESHOLD_SECONDS) {
            count++
            offset += interval
        }
        return count
    }

    // Cumulative drive time from the origin to each geometry
    fun cumulativeDurations(route: Route, cumulativeDistance: DoubleArray): DoubleArray {
        val size = route.geometry.size
        val result = DoubleArray(size)
        if (size < 2) return result

        val segments = route.segmentDurationsSeconds
        val annotationTotal = segments.sum()
        if (segments.size == size - 1 && annotationTotal > 0.0) {
            val scale = if (route.durationSeconds > 0.0) route.durationSeconds / annotationTotal else 1.0
            for (i in 1 until size) {
                result[i] = result[i - 1] + segments[i - 1] * scale
            }
            if (route.durationSeconds > 0.0) result[size - 1] = route.durationSeconds
        } else {
            val totalDistance = cumulativeDistance.last()
            for (i in 1 until size) {
                result[i] = if (totalDistance > 0.0) {
                    route.durationSeconds * cumulativeDistance[i] / totalDistance
                } else {
                    route.durationSeconds * i / (size - 1)
                }
            }
        }
        return result
    }

    // Cumulative great-circle distance from the first point to each point
    fun cumulativeDistances(points: List<LatLng>): DoubleArray {
        val result = DoubleArray(points.size)
        for (i in 1 until points.size) {
            result[i] = result[i - 1] + halfVersedSineMeters(points[i - 1], points[i])
        }
        return result
    }

    private data class Position(val location: LatLng, val distanceMeters: Double)

    // Interpolate location and distance for drive time
    private fun locate(
        offsetSeconds: Double,
        points: List<LatLng>,
        cumulativeDistance: DoubleArray,
        cumulativeDuration: DoubleArray
    ): Position {
        if (offsetSeconds <= 0.0) return Position(points.first(), 0.0)
        if (offsetSeconds >= cumulativeDuration.last()) {
            return Position(points.last(), cumulativeDistance.last())
        }

        var high = cumulativeDuration.indexOfFirst { it >= offsetSeconds }
        if (high <= 0) high = 1
        val low = high - 1
        val span = cumulativeDuration[high] - cumulativeDuration[low]

        if (span <= 0.0) return Position(points[low], cumulativeDistance[low])

        val t = (offsetSeconds - cumulativeDuration[low]) / span
        val a = points[low]
        val b = points[high]
        return Position(
            location = LatLng(
                latitude = a.latitude + (b.latitude - a.latitude) * t,
                longitude = a.longitude + (b.longitude - a.longitude) * t
            ),
            distanceMeters = cumulativeDistance[low] +
                    (cumulativeDistance[high] - cumulativeDistance[low]) * t
        )
    }

    private fun List<LatLng>.endpoints(): List<LatLng> = if (size < 2) take(1) else listOf(first(), last())

    // Great-circle distance between two coordinates, in meters
    fun halfVersedSineMeters(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)

        return 2 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(h)))
    }
}
