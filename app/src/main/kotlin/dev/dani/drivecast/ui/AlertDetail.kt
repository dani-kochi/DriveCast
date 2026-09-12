package dev.dani.drivecast.ui

import dev.dani.drivecast.domain.model.WeatherAlert
import dev.dani.drivecast.domain.model.WeatherAlertSource

object AlertDetail {
    fun isDetailed(alert: WeatherAlert): Boolean = alert.source == WeatherAlertSource.NWS

    fun isDetailed(alerts: List<WeatherAlert>): Boolean = alerts.any(::isDetailed)

    fun ordered(alerts: List<WeatherAlert>): List<WeatherAlert> = alerts
        .filter(::isDetailed)
        .sortedWith(
            compareByDescending<WeatherAlert> { it.severity.ordinal }
                .thenBy { it.urgency.ordinal }
                .thenBy { it.event }
        )

    fun paragraphs(text: String?): List<String> {
        val body = text?.trim().orEmpty()
        if (body.isEmpty()) return emptyList()
        return body.split(PARAGRAPH_BREAK)
            .map { it.reflow() }
            .filter { it.isNotEmpty() }
    }

    private fun String.reflow(): String = trim()
        .replace(BULLET, " ")
        .replace(WHITESPACE, " ")
        .trim()

    private val PARAGRAPH_BREAK = Regex("""\n[ \t]*\n\s*""")
    private val WHITESPACE = Regex("""\s+""")
    private val BULLET = Regex("""(^|\n)\s*\*\s*""")
}
