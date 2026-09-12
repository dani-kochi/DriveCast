package dev.dani.drivecast.domain.model

enum class AlertCertainty(val label: String) {
    OBSERVED("Observed"),
    LIKELY("Likely"),
    POSSIBLE("Possible"),
    UNLIKELY("Unlikely"),
    UNKNOWN("Unknown");

    companion object {
        fun fromCap(value: String?): AlertCertainty =
            entries.firstOrNull { it.label.equals(value?.trim(), ignoreCase = true) } ?: UNKNOWN
    }
}