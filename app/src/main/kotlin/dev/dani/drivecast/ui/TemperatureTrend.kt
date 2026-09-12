package dev.dani.drivecast.ui

// Temperature change compared to the origin
enum class TemperatureTrend {
    FAR_COLDER,
    MUCH_COLDER,
    COLDER,
    A_LITTLE_COLDER,
    STEADY,
    A_LITTLE_WARMER,
    WARMER,
    MUCH_WARMER,
    FAR_WARMER;

    val changeDirection: Int
        get() = when (this) {
            FAR_COLDER, MUCH_COLDER, COLDER, A_LITTLE_COLDER -> -1 // Down
            STEADY -> 0
            A_LITTLE_WARMER, WARMER, MUCH_WARMER, FAR_WARMER -> 1  // Up
        }

    companion object {

        const val STEADY_THRESHOLD = 3.0 // Change within 3° is considered steady
        const val NOTICEABLE_THRESHOLD = 8.0
        const val STRONG_THRESHOLD = 15.0
        const val EXTREME_THRESHOLD = 25.0

        // @param delta waypoint temperature minus the origin's
        fun of(delta: Double): TemperatureTrend = when {
            delta <= -EXTREME_THRESHOLD -> FAR_COLDER
            delta <= -STRONG_THRESHOLD -> MUCH_COLDER
            delta <= -NOTICEABLE_THRESHOLD -> COLDER
            delta <= -STEADY_THRESHOLD -> A_LITTLE_COLDER
            delta < STEADY_THRESHOLD -> STEADY
            delta < NOTICEABLE_THRESHOLD -> A_LITTLE_WARMER
            delta < STRONG_THRESHOLD -> WARMER
            delta < EXTREME_THRESHOLD -> MUCH_WARMER
            else -> FAR_WARMER
        }
    }
}