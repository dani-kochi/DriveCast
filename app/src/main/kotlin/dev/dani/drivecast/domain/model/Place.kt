package dev.dani.drivecast.domain.model

data class Place(
    val name: String,
    val admin: String?,
    val country: String?,
    val location: LatLng,
    val countryCode: String? = null // To distinguish US states from regions with the same name
) {
    val displayName: String
        get() = listOfNotNull(
            name,
            UsStateAbbreviation.abbreviate(admin, countryCode),
            country
        ).distinct().joinToString(", ")
}
