package dev.dani.drivecast.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FilterDrama
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbCloudy
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import dev.dani.drivecast.domain.model.WeatherCondition

/** Maps a WMO-derived condition onto a Material icon. */
fun WeatherCondition.icon(): ImageVector = when (this) {
    WeatherCondition.CLEAR -> Icons.Filled.WbSunny
    WeatherCondition.MAINLY_CLEAR -> Icons.Filled.WbSunny
    WeatherCondition.PARTLY_CLOUDY -> Icons.Filled.FilterDrama
    WeatherCondition.OVERCAST -> Icons.Filled.WbCloudy
    WeatherCondition.FOG -> Icons.Filled.Cloud
    WeatherCondition.DRIZZLE -> Icons.Filled.Grain
    WeatherCondition.FREEZING_DRIZZLE -> Icons.Filled.AcUnit
    WeatherCondition.RAIN -> Icons.Filled.WaterDrop
    WeatherCondition.FREEZING_RAIN -> Icons.Filled.AcUnit
    WeatherCondition.SNOW -> Icons.Filled.AcUnit
    WeatherCondition.SNOW_GRAINS -> Icons.Filled.AcUnit
    WeatherCondition.RAIN_SHOWERS -> Icons.Filled.WaterDrop
    WeatherCondition.SNOW_SHOWERS -> Icons.Filled.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Filled.Bolt
    WeatherCondition.THUNDERSTORM_HAIL -> Icons.Filled.Bolt
    WeatherCondition.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
}
