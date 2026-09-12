package dev.dani.drivecast.domain.model

data class WeatherAlert(
    val event: String,
    val severity: AlertSeverity,
    val source: WeatherAlertSource,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val urgency: AlertUrgency = AlertUrgency.UNKNOWN,
    val certainty: AlertCertainty = AlertCertainty.UNKNOWN
)
