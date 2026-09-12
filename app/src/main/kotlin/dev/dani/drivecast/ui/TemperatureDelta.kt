package dev.dani.drivecast.ui

import dev.dani.drivecast.domain.model.UnitSystem
import kotlin.math.abs
import kotlin.math.roundToInt

// The temperature difference between a waypoint and the origin, ready to display.
data class TemperatureDelta(
    val degrees: Double,
    val trend: TemperatureTrend,
    val unitSystem: UnitSystem,
) {
    val accessibilityLabel: String?
        get() {
            val magnitude = abs(degrees).roundToInt()
            return when (trend.changeDirection) {
                1 -> "$magnitude${unitSystem.temperature} warmer than the origin"
                -1 -> "$magnitude${unitSystem.temperature} cooler than the origin"
                else -> null
            }
        }

    companion object {

        fun between(temperature: Double, benchmark: Double?, unitSystem: UnitSystem): TemperatureDelta {
            val delta = if (benchmark == null) {
                0.0
            } else {
                temperature - benchmark
            }
            return TemperatureDelta(delta, TemperatureTrend.of(delta), unitSystem)
        }
    }
}
