package dev.dani.drivecast.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember

private val DriveCastSemanticColors = driveCastColors(DriveCastColorScheme)

@Composable
fun DriveCastTheme(
    content: @Composable () -> Unit
) {
    val colors = remember { DriveCastSemanticColors }

    CompositionLocalProvider(LocalDriveCastColors provides colors) {
        MaterialTheme(
            colorScheme = DriveCastColorScheme,
            typography = DriveCastTypography,
            content = content
        )
    }
}

object DriveCastTheme {
    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val textStyles: DriveCastTextStyles
        @Composable
        @ReadOnlyComposable
        get() = driveCastTextStyles

    val spacing: DriveCastSpacing
        @Composable
        @ReadOnlyComposable
        get() = driveCastSpacing

    val colors: DriveCastColors
        @Composable
        @ReadOnlyComposable
        get() = LocalDriveCastColors.current

    val colorScheme: ColorScheme
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme
}
