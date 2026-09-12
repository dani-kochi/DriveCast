package dev.dani.drivecast.domain.model

data class ResolvedPlace(
    val locality: String? = null,
    val town: String? = null,
    val county: String? = null,
    val adminArea: String? = null,
    val country: String? = null,
    val countryCode: String? = null
)

