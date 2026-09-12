package dev.dani.drivecast.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NwsAlertsResponse(
    val features: List<NwsAlertFeature>? = null
)