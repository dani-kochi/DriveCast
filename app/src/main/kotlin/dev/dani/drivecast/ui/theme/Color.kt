package dev.dani.drivecast.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val RichBlack = Color(0xFF001219)
val MidnightGreen = Color(0xFF005F73)
val Teal = Color(0xFF0A9396)
val Tiffany = Color(0xFF94D2BD)
val Vanilla = Color(0xFFE9D8A6)
val Gamboge = Color(0xFFEE9B00)
val AlloyOrange = Color(0xFFCA6702)
val Rust = Color(0xFFBB3E03)
val Rufous = Color(0xFFAE2012)
val Auburn = Color(0xFF9B2226)
val DeepPetrol = Color(0xFF002530)
val AbyssPetrol = Color(0xFF002029)
val DeepTeal = Color(0xFF003542)
val EmberDark = Color(0xFF392A13)
val OxbloodDark = Color(0xFF2B161D)
val GlacierBlue = Color(0xFF8ECAE6)
val MistBlue = Color(0xFF73BFDC)
val PowderBlue = Color(0xFF58B4D1)
val BlueGreen = Color(0xFF219EBC)
val DeepCerulean = Color(0xFF126782)
val PumpkinOrange = Color(0xFFFB8500)
val CarrotOrange = Color(0xFFFB9017)
val MarigoldOrange = Color(0xFFFD9E02)
val SelectiveYellow = Color(0xFFFFB703)

const val MUTED_ON_CARD_ALPHA = 0.80f

val RustDark = Color(0xFF341E13)
val RufousDark = Color(0xFF311617)
val ApricotLight = Color(0xFFEAC29A)
val ClayLight = Color(0xFFE4B29A)
val AshRose = Color(0xFFD7A7A8)
val SignalRose = Color(0xFFFF9E9E)
val SignalCoral = Color(0xFFFF7373)
val SignalScarlet = Color(0xFFFF4040)
val SignalRed = Color(0xFFFF0000)
val ErrorRose = Color(0xFFDFA6A0)
val TitleWhite = Color(0xFFFFFFFF)

val DriveCastColorScheme: ColorScheme = darkColorScheme(
    primary = Teal,
    onPrimary = RichBlack,
    primaryContainer = MidnightGreen,
    onPrimaryContainer = Vanilla,
    inversePrimary = MidnightGreen,

    secondary = Tiffany,
    onSecondary = RichBlack,
    secondaryContainer = DeepTeal,
    onSecondaryContainer = Tiffany,

    tertiary = Gamboge,
    onTertiary = RichBlack,
    tertiaryContainer = EmberDark,
    onTertiaryContainer = Vanilla,

    background = RichBlack,
    onBackground = Vanilla,
    surface = RichBlack,
    onSurface = Vanilla,
    surfaceVariant = DeepPetrol,
    onSurfaceVariant = Tiffany,
    surfaceTint = Teal,

    surfaceContainerLowest = RichBlack,
    surfaceContainerLow = AbyssPetrol,
    surfaceContainer = DeepPetrol,
    surfaceContainerHigh = DeepTeal,
    surfaceContainerHighest = DeepTeal,

    inverseSurface = Vanilla,
    inverseOnSurface = RichBlack,

    error = ErrorRose,
    onError = RichBlack,
    errorContainer = OxbloodDark,
    onErrorContainer = ErrorRose,

    outline = Teal,
    outlineVariant = MidnightGreen,
    scrim = RichBlack
)

@Immutable
data class TemperatureCardColors(
    val surface: Color,
    val content: Color,
    val muted: Color
)

private fun onDark(surface: Color) = TemperatureCardColors(
    surface = surface,
    content = RichBlack,
    muted = RichBlack.copy(alpha = MUTED_ON_CARD_ALPHA)
)

private fun onLight(surface: Color) = TemperatureCardColors(
    surface = surface,
    content = TitleWhite,
    muted = TitleWhite.copy(alpha = MUTED_ON_CARD_ALPHA)
)

@Immutable
data class DriveCastColors(
    val errorText: Color,
    val forecastSurface: Color,
    val forecastContent: Color,
    val hazardousForecastSurface: Color,
    val unavailableForecastSurface: Color,
    val mutedText: Color,
    val timelineConnector: Color,
    val temperatureCards: List<TemperatureCardColors>,
    val alertSurfaces: List<Color>,
    val alertAccents: List<Color>,
    val alertEdges: List<Color>,
    val topBarContent: Color
)

fun driveCastColors(scheme: ColorScheme): DriveCastColors = DriveCastColors(
    errorText = scheme.error,
    forecastSurface = scheme.surfaceVariant,
    forecastContent = scheme.onSurface,
    hazardousForecastSurface = scheme.tertiaryContainer,
    unavailableForecastSurface = scheme.errorContainer,
    mutedText = scheme.onSurfaceVariant,
    timelineConnector = scheme.outline,
    temperatureCards = listOf(
        onDark(GlacierBlue),
        onDark(PowderBlue),
        onDark(BlueGreen),
        onLight(DeepCerulean),
        TemperatureCardColors(scheme.surfaceVariant, scheme.onSurface, scheme.onSurfaceVariant),
        onDark(SelectiveYellow),
        onDark(MarigoldOrange),
        onDark(PumpkinOrange),
        onDark(CarrotOrange)
    ),
    alertSurfaces = listOf(EmberDark, EmberDark, RustDark, RufousDark, OxbloodDark),
    alertAccents = listOf(ApricotLight, ApricotLight, ClayLight, ErrorRose, AshRose),
    alertEdges = listOf(SignalRose, SignalRose, SignalCoral, SignalScarlet, SignalRed),
    topBarContent = TitleWhite
)

val LocalDriveCastColors = staticCompositionLocalOf<DriveCastColors> {
    error("No DriveCastColors provided. Wrap the content in DriveCastTheme { … }.")
}
