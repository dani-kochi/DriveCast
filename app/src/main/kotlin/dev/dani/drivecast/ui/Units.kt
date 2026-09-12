package dev.dani.drivecast.ui

import dev.dani.drivecast.domain.model.UnitSystem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * This is a utility object to handle conversion and formatting of units for display in the app.
 */
object Units {

    const val METERS_PER_MILE = 1609.344
    const val APPARENT_TEMPERATURE_DELTA = 4.0

    fun metersToMiles(meters: Double): Double = meters / METERS_PER_MILE

    fun formatDistance(meters: Double, unit: UnitSystem): String {
        val distance = when (unit) {
            UnitSystem.IMPERIAL -> metersToMiles(meters) // OSRM always return distance in meters.
            UnitSystem.METRIC -> meters
        }
        val distanceFormatted = String.format(Locale.US, "%.${if (distance < 10.0) "1" else "0"}f", distance)
        return "$distanceFormatted ${unit.distance}"
    }

    fun formatTemperature(value: Double, unit: UnitSystem): String =
        String.format(Locale.US, "%d${unit.temperature}", value.roundToInt())

    fun formatPrecipitation(value: Double, unit: UnitSystem): String = when {
        value <= 0.0 -> "0 ${unit.precipitation}"
        value < 0.005 -> "trace"
        else -> String.format(Locale.US, "%.2f ${unit.precipitation}", value)
    }

    fun formatWindSpeed(speed: Double, unit: UnitSystem): String =
        String.format(Locale.US, "%d ${unit.windSpeed}", speed.roundToInt())

    fun apparentTemperatureIsNotable(temperature: Double, apparent: Double?): Boolean =
        apparent != null && abs(apparent - temperature) >= APPARENT_TEMPERATURE_DELTA
}
