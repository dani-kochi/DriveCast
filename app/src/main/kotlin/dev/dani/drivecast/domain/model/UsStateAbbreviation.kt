package dev.dani.drivecast.domain.model

import java.util.Locale

// Convert a US state's name into its two-letter abbreviation.
object UsStateAbbreviation {

    fun abbreviate(
        adminArea: String?, // the state or territory name
        countryCode: String?
    ): String? {
        val trimmed = adminArea?.trim()?.takeIf { it.isNotEmpty() } ?: return adminArea

        if (countryCode.equals(UNITED_STATES, ignoreCase = true).not()) return trimmed

        return NAME_TO_CODE[trimmed.lowercase(Locale.US)] ?: trimmed
    }

    private const val UNITED_STATES = "US"

    // All 50 states, DC, and territories
    private val NAME_TO_CODE: Map<String, String> = mapOf(
        "alabama" to "AL",
        "alaska" to "AK",
        "arizona" to "AZ",
        "arkansas" to "AR",
        "california" to "CA",
        "colorado" to "CO",
        "connecticut" to "CT",
        "delaware" to "DE",
        "florida" to "FL",
        "georgia" to "GA",
        "hawaii" to "HI",
        "idaho" to "ID",
        "illinois" to "IL",
        "indiana" to "IN",
        "iowa" to "IA",
        "kansas" to "KS",
        "kentucky" to "KY",
        "louisiana" to "LA",
        "maine" to "ME",
        "maryland" to "MD",
        "massachusetts" to "MA",
        "michigan" to "MI",
        "minnesota" to "MN",
        "mississippi" to "MS",
        "missouri" to "MO",
        "montana" to "MT",
        "nebraska" to "NE",
        "nevada" to "NV",
        "new hampshire" to "NH",
        "new jersey" to "NJ",
        "new mexico" to "NM",
        "new york" to "NY",
        "north carolina" to "NC",
        "north dakota" to "ND",
        "ohio" to "OH",
        "oklahoma" to "OK",
        "oregon" to "OR",
        "pennsylvania" to "PA",
        "rhode island" to "RI",
        "south carolina" to "SC",
        "south dakota" to "SD",
        "tennessee" to "TN",
        "texas" to "TX",
        "utah" to "UT",
        "vermont" to "VT",
        "virginia" to "VA",
        "washington" to "WA",
        "west virginia" to "WV",
        "wisconsin" to "WI",
        "wyoming" to "WY",
        "district of columbia" to "DC",
        "washington, d.c." to "DC",
        "puerto rico" to "PR",
        "guam" to "GU",
        "united states virgin islands" to "VI",
        "u.s. virgin islands" to "VI",
        "american samoa" to "AS",
        "northern mariana islands" to "MP"
    )
}
