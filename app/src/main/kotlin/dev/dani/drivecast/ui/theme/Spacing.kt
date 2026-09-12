package dev.dani.drivecast.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class DriveCastSpacing(
    val actionSeparation: Dp
)

val driveCastSpacing = DriveCastSpacing(
    actionSeparation = 60.dp
)
