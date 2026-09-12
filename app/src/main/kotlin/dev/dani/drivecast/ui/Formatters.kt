package dev.dani.drivecast.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 12-hour with a meridiem, to match the US units the rest of the app now speaks.
private val clockFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)

/** Formats an epoch-second instant in the device's local time zone. */
fun formatClock(epochSeconds: Long): String =
    clockFormatter.format(Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()))

/** Human readable duration, e.g. `3h 25m`. */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
