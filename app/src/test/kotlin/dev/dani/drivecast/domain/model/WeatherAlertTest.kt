package dev.dani.drivecast.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherAlertTest {

    @Test
    fun `parses every CAP severity the NWS actually emits`() {
        assertEquals(AlertSeverity.EXTREME, AlertSeverity.fromCap("Extreme"))
        assertEquals(AlertSeverity.SEVERE, AlertSeverity.fromCap("Severe"))
        assertEquals(AlertSeverity.MODERATE, AlertSeverity.fromCap("Moderate"))
        assertEquals(AlertSeverity.MINOR, AlertSeverity.fromCap("Minor"))
        assertEquals(AlertSeverity.UNKNOWN, AlertSeverity.fromCap("Unknown"))
    }

    @Test
    fun `severity parsing tolerates casing and whitespace`() {
        assertEquals(AlertSeverity.SEVERE, AlertSeverity.fromCap("severe"))
        assertEquals(AlertSeverity.SEVERE, AlertSeverity.fromCap("  SEVERE  "))
    }

    @Test
    fun `an absent or unrecognised severity degrades to Unknown rather than throwing`() {
        assertEquals(AlertSeverity.UNKNOWN, AlertSeverity.fromCap(null))
        assertEquals(AlertSeverity.UNKNOWN, AlertSeverity.fromCap(""))
        assertEquals(AlertSeverity.UNKNOWN, AlertSeverity.fromCap("Catastrophic"))
    }

    @Test
    fun `severity order ranks Unknown below Minor`() {
        // An alert that will not say how bad it is should not outrank one that has said
        // "not very" -- the ordinal drives both the sort and the colour lookup.
        val worst = listOf(AlertSeverity.MINOR, AlertSeverity.UNKNOWN).maxBy { it.ordinal }
        assertEquals(AlertSeverity.MINOR, worst)
    }

    @Test
    fun `benign conditions derive no alert at all`() {
        assertNull(WeatherCondition.CLEAR.derivedAlert())
        assertNull(WeatherCondition.PARTLY_CLOUDY.derivedAlert())
        assertNull(WeatherCondition.DRIZZLE.derivedAlert())
    }

    @Test
    fun `dangerous conditions derive a graded alert naming the condition`() {
        val hail = WeatherCondition.THUNDERSTORM_HAIL.derivedAlert()!!
        assertEquals(AlertSeverity.SEVERE, hail.severity)
        assertEquals(WeatherCondition.THUNDERSTORM_HAIL.label, hail.event)

        assertEquals(AlertSeverity.MODERATE, WeatherCondition.SNOW.derivedAlert()?.severity)
        assertEquals(AlertSeverity.MINOR, WeatherCondition.FOG.derivedAlert()?.severity)
    }

    @Test
    fun `no derived alert ever claims Extreme`() {
        // Extreme is a statement a meteorologist makes, not one a WMO code supports. Letting a
        // forecast reach it would make the strongest colour in the app mean nothing.
        val derived = WeatherCondition.entries.mapNotNull { it.derivedAlert() }
        assertEquals(emptyList<WeatherAlert>(), derived.filter { it.severity == AlertSeverity.EXTREME })
    }

    @Test
    fun `derived alerts are labelled as derived so the UI can qualify them`() {
        val derived = WeatherCondition.entries.mapNotNull { it.derivedAlert() }
        assertEquals(emptyList<WeatherAlert>(), derived.filter { it.source != WeatherAlertSource.DERIVED })
    }

    @Test
    fun `CAP urgency parses case-insensitively and degrades to Unknown`() {
        assertEquals(AlertUrgency.IMMEDIATE, AlertUrgency.fromCap("Immediate"))
        assertEquals(AlertUrgency.EXPECTED, AlertUrgency.fromCap(" expected "))
        assertEquals(AlertUrgency.UNKNOWN, AlertUrgency.fromCap(null))
        assertEquals(AlertUrgency.UNKNOWN, AlertUrgency.fromCap("Soonish"))
    }

    @Test
    fun `CAP certainty parses case-insensitively and degrades to Unknown`() {
        assertEquals(AlertCertainty.OBSERVED, AlertCertainty.fromCap("OBSERVED"))
        assertEquals(AlertCertainty.POSSIBLE, AlertCertainty.fromCap("Possible"))
        assertEquals(AlertCertainty.UNKNOWN, AlertCertainty.fromCap(""))
    }

    @Test
    fun `urgency and certainty are ordered by how much they should worry you`() {
        // The sheet sorts equally severe alerts by urgency, so the order is behaviour, not
        // decoration: Immediate must outrank Expected must outrank Future.
        assertTrue(AlertUrgency.IMMEDIATE.ordinal < AlertUrgency.EXPECTED.ordinal)
        assertTrue(AlertUrgency.EXPECTED.ordinal < AlertUrgency.FUTURE.ordinal)
        assertTrue(AlertCertainty.OBSERVED.ordinal < AlertCertainty.LIKELY.ordinal)
        assertTrue(AlertCertainty.LIKELY.ordinal < AlertCertainty.POSSIBLE.ordinal)
    }

    @Test
    fun `a derived alert claims no urgency or certainty it has not got`() {
        val derived = WeatherCondition.FOG.derivedAlert()!!

        assertEquals(AlertUrgency.UNKNOWN, derived.urgency)
        assertEquals(AlertCertainty.UNKNOWN, derived.certainty)
    }
}
