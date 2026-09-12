package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Level 2 = country, 4 = state / principal subdivision, 6 = county, 8 = town or municipality.
@Serializable
data class AdministrativeArea(
    @SerialName("name") val name: String? = null,
    @SerialName("adminLevel") val adminLevel: Int? = null
)