package dev.dani.drivecast.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NwsAlertProperties(
    val event: String? = null,
    val severity: String? = null,
    val headline: String? = null,
    val description: String? = null,
    val instruction: String? = null,
    val urgency: String? = null,
    val certainty: String? = null,
    @SerialName("messageType") val messageType: String? = null
)