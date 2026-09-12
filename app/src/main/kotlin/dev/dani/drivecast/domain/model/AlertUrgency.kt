package dev.dani.drivecast.domain.model

enum class AlertUrgency(val label: String) {
    IMMEDIATE("Immediate"),
    EXPECTED("Expected"),
    FUTURE("Future"),
    PAST("Past"),
    UNKNOWN("Unknown");

    companion object {
        fun fromCap(value: String?): AlertUrgency =
            entries.firstOrNull { it.label.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}