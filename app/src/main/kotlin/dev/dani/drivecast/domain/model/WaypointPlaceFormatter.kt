package dev.dani.drivecast.domain.model

import java.util.Locale
import kotlin.collections.get
import kotlin.math.abs

/**
 * Converts a [ResolvedPlace] into the single line shown above a waypoint's ETA.
 *
 * - `"City, State"` If origin and destination is in same country
 * - `"City, State, Country"` If the country differs
 */
object WaypointPlaceFormatter {

    fun format(
        place: ResolvedPlace?,
        location: LatLng,
        referenceCountryCode: String?
    ): String {
        val primary = place?.let {
            firstNonBlank(it.locality, it.town, it.county, it.adminArea)
        } ?: return formatCoordinates(location)

        val leading = if (primary.equals(place.adminArea?.trim(), ignoreCase = true)) {
            UsStateAbbreviation.abbreviate(primary, place.countryCode) ?: primary
        } else {
            primary
        }

        val parts = mutableListOf(leading)

        place.adminArea.nonBlank()
            ?.takeUnless { it.equals(primary, ignoreCase = true) }
            ?.let { UsStateAbbreviation.abbreviate(it, place.countryCode) }
            ?.let { parts += it }

        if (countryDiffers(place.countryCode, referenceCountryCode)) {
            shortCountryName(place)
                ?.takeUnless { country -> parts.any { it.equals(country, ignoreCase = true) } }
                ?.let { parts += it }
        }

        return parts.joinToString(", ")
    }

    fun formatCoordinates(location: LatLng): String = String.format(
        Locale.US,
        "%.4f°%s, %.4f°%s",
        abs(location.latitude),
        if (location.latitude >= 0) "N" else "S",
        abs(location.longitude),
        if (location.longitude >= 0) "E" else "W"
    )

    private fun countryDiffers(countryCode: String?, referenceCountryCode: String?): Boolean =
        countryCode.nonBlank() != null && !countryCode.equals(referenceCountryCode, true)

    private fun shortCountryName(place: ResolvedPlace): String? {
        val country = place.country.nonBlank()
        val code = place.countryCode.nonBlank()?.uppercase(Locale.US)
        return SHORT_TO_LONG_COUNTRY_NAMES[code] ?: country ?: code
    }

    private fun firstNonBlank(vararg candidates: String?): String? =
        candidates.firstNotNullOfOrNull { it.nonBlank() }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private val SHORT_TO_LONG_COUNTRY_NAMES = mapOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "NL" to "Netherlands",
        "CZ" to "Czechia",
        "KR" to "South Korea",
        "KP" to "North Korea",
        "RU" to "Russia",
        "BO" to "Bolivia",
        "VE" to "Venezuela",
        "TZ" to "Tanzania",
        "IN" to "India",
        "IR" to "Iran",
        "SY" to "Syria",
        "LA" to "Laos",
        "VN" to "Vietnam",
        "MD" to "Moldova",
        "MK" to "North Macedonia",
        "AE" to "United Arab Emirates"
    )
}