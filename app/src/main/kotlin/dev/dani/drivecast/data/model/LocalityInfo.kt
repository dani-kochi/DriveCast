package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocalityInfo(
    @SerialName("administrative") val administrative: List<AdministrativeArea>? = null
)