package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReverseGeocodeResponse(
    @SerialName("city") val city: String? = null,
    @SerialName("locality") val locality: String? = null,
    @SerialName("principalSubdivision") val principalSubdivision: String? = null,
    @SerialName("countryName") val countryName: String? = null,
    @SerialName("countryCode") val countryCode: String? = null,
    @SerialName("localityInfo") val localityInfo: LocalityInfo? = null
)