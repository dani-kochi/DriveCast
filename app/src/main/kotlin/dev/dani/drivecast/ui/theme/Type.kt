package dev.dani.drivecast.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.dani.drivecast.R

val LatoFontFamily = FontFamily(
    Font(R.font.lato_thin, FontWeight.Thin, FontStyle.Normal),
    Font(R.font.lato_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.lato_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.lato_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.lato_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.lato_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.lato_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.lato_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.lato_black, FontWeight.Black, FontStyle.Normal),
    Font(R.font.lato_black_italic, FontWeight.Black, FontStyle.Italic)
)

private fun lato(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0
) = TextStyle(
    fontFamily = LatoFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val DriveCastTypography = Typography(
    displayLarge = lato(FontWeight.Light, size = 71, lineHeight = 80, letterSpacing = -0.25),
    displayMedium = lato(FontWeight.Light, size = 56, lineHeight = 64),
    displaySmall = lato(FontWeight.Light, size = 45, lineHeight = 54),
    headlineLarge = lato(FontWeight.Bold, size = 40, lineHeight = 48),
    headlineMedium = lato(FontWeight.Bold, size = 35, lineHeight = 44),
    headlineSmall = lato(FontWeight.Bold, size = 30, lineHeight = 40),
    titleLarge = lato(FontWeight.Bold, size = 28, lineHeight = 36),
    titleMedium = lato(FontWeight.Bold, size = 20, lineHeight = 28, letterSpacing = 0.15),
    titleSmall = lato(FontWeight.Bold, size = 18, lineHeight = 24, letterSpacing = 0.1),
    bodyLarge = lato(FontWeight.Normal, size = 20, lineHeight = 28, letterSpacing = 0.5),
    bodyMedium = lato(FontWeight.Normal, size = 18, lineHeight = 24, letterSpacing = 0.25),
    bodySmall = lato(FontWeight.Normal, size = 15, lineHeight = 20, letterSpacing = 0.4),
    labelLarge = lato(FontWeight.Bold, size = 18, lineHeight = 24, letterSpacing = 0.1),
    labelMedium = lato(FontWeight.Bold, size = 15, lineHeight = 20, letterSpacing = 0.5),
    labelSmall = lato(FontWeight.Bold, size = 14, lineHeight = 18, letterSpacing = 0.5)
)

@Immutable
data class DriveCastTextStyles(
    val timelineDistance: TextStyle
)

val driveCastTextStyles = DriveCastTextStyles(
    timelineDistance = lato(FontWeight.Light, size = 14, lineHeight = 18, letterSpacing = 0.5)
)
