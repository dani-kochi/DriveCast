package dev.dani.drivecast.domain.model

enum class AlertSeverity(val label: String) {
    UNKNOWN("Unknown"),
    MINOR("Minor"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
    EXTREME("Extreme");

    companion object {
        fun fromCap(value: String?): AlertSeverity =
            entries.firstOrNull { it.label.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}