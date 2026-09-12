package dev.dani.drivecast.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NwsAlertFeature(
    val properties: NwsAlertProperties? = null
)