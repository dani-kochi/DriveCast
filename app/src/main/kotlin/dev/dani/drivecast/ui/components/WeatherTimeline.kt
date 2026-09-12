package dev.dani.drivecast.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.WaypointForecast
import dev.dani.drivecast.domain.model.WeatherAlert
import dev.dani.drivecast.domain.model.WeatherAlertSource
import dev.dani.drivecast.ui.AlertDetail
import dev.dani.drivecast.ui.TemperatureDelta
import dev.dani.drivecast.ui.TemperatureTrend
import dev.dani.drivecast.ui.Units
import dev.dani.drivecast.ui.formatClock
import dev.dani.drivecast.ui.theme.DriveCastTheme
import dev.dani.drivecast.ui.theme.TemperatureCardColors
import java.util.Locale


// Vertical timeline of per-waypoint forecasts
@Composable
fun WeatherTimelineItem(
    forecast: WaypointForecast,
    modifier: Modifier = Modifier,
    benchmarkTemperature: Double? = null,
    unitSystem: UnitSystem,
    isBenchmark: Boolean = false,
    showTimelineHeader: Boolean = false,
    onShowAlertDetail: (WaypointForecast.Success) -> Unit = {},
) {
    val success = forecast as? WaypointForecast.Success
    val alert = success?.topAlert
    val detailTarget = success?.takeIf { AlertDetail.isDetailed(it.alerts) }
    val delta = success?.let {
        TemperatureDelta.between(
            temperature = it.weather.temperature,
            benchmark = benchmarkTemperature.takeUnless { _ -> isBenchmark },
            unitSystem = unitSystem,
        )
    }
    val colors = DriveCastTheme.colors
    val card = when {
        forecast is WaypointForecast.Failure -> TemperatureCardColors(
            colors.unavailableForecastSurface, colors.forecastContent, colors.mutedText
        )

        alert != null -> TemperatureCardColors(
            colors.alertSurfaces[alert.severity.ordinal], colors.forecastContent, colors.mutedText
        )

        (forecast as WaypointForecast.Success).weather.condition.isHazardous ->
            TemperatureCardColors(
                colors.hazardousForecastSurface, colors.forecastContent, colors.mutedText
            )

        else -> colors.temperatureCards[delta?.trend?.ordinal ?: TemperatureTrend.STEADY.ordinal]
    }
    val edge = alert
        ?.takeIf { it.source == WeatherAlertSource.NWS }
        ?.let { colors.alertEdges[it.severity.ordinal] }
    Column(modifier = modifier.fillMaxWidth()) {
        if (showTimelineHeader) {
            TimelineHeader(distanceAlongRouteMeters = forecast.waypoint.distanceAlongRouteMeters, unitSystem = unitSystem)
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (detailTarget == null) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            onClickLabel = "Open full weather alert details",
                            role = Role.Button
                        ) { onShowAlertDetail(detailTarget) }
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = card.surface,
                contentColor = card.content
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (edge == null) {
                            Modifier
                        } else {
                            Modifier.drawBehind {
                                val width = 10.dp.toPx()
                                drawRect(
                                    color = edge,
                                    topLeft = Offset(
                                        if (layoutDirection == LayoutDirection.Ltr) {
                                            0f
                                        } else {
                                            size.width - width
                                        },
                                        0f
                                    ),
                                    size = Size(width, size.height)
                                )
                            }
                        }
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = forecast.placeLabel,
                        style = DriveCastTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatClock(forecast.waypoint.etaEpochSeconds),
                        style = DriveCastTheme.typography.bodySmall
                    )
                    when (forecast) {
                        is WaypointForecast.Success -> {
                            Text(
                                text = forecast.weather.condition.label,
                                style = DriveCastTheme.typography.bodyMedium
                            )
                            if (alert != null) {
                                AlertLine(alert, hasDetail = detailTarget != null)
                            }
                            Text(
                                text = buildString {
                                    append("Precip ")
                                    append(Units.formatPrecipitation(forecast.weather.precipitation, unitSystem))
                                    forecast.weather.precipitationProbability?.let { append(" · $it%") }
                                    forecast.weather.windSpeed?.let {
                                        append(" · Wind ${Units.formatWindSpeed(it, unitSystem)}")
                                    }
                                },
                                style = DriveCastTheme.typography.bodySmall
                            )
                        }

                        is WaypointForecast.Failure -> Text(
                            text = forecast.message,
                            style = DriveCastTheme.typography.bodySmall
                        )
                    }
                }

                ReadingColumn(
                    forecast = forecast,
                    delta = delta,
                    unitSystem = unitSystem,
                    isBenchmark = isBenchmark,
                    card = card
                )
            }
        }
    }
}

@Composable
private fun TimelineHeader(distanceAlongRouteMeters: Double, unitSystem: UnitSystem) {
    Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .background(DriveCastTheme.colors.timelineConnector)
        )
        Text(
            text = Units.formatDistance(distanceAlongRouteMeters, unitSystem),
            style = DriveCastTheme.textStyles.timelineDistance,
            color = DriveCastTheme.colors.mutedText,
            maxLines = 1
        )
    }
}

@Composable
private fun ReadingColumn(
    forecast: WaypointForecast,
    delta: TemperatureDelta?,
    unitSystem: UnitSystem,
    isBenchmark: Boolean,
    card: TemperatureCardColors
) {
    Column(
        modifier = Modifier.width(112.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when (forecast) {
            is WaypointForecast.Success -> {
                Icon(
                    imageVector = forecast.weather.condition.icon(),
                    contentDescription = forecast.weather.condition.label,
                    modifier = Modifier.size(32.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isBenchmark && delta != null) TrendArrow(delta, card.content)
                    Text(
                        text = Units.formatTemperature(forecast.weather.temperature, unitSystem),
                        style = DriveCastTheme.typography.headlineSmall,
                        color = card.content,
                        maxLines = 1
                    )
                }
                if (isBenchmark) {
                    Text(
                        text = "baseline",
                        style = DriveCastTheme.typography.bodySmall,
                        color = card.muted,
                        maxLines = 1
                    )
                }
                forecast.weather.apparentTemperature?.let { apparent ->
                    if (Units.apparentTemperatureIsNotable(
                            forecast.weather.temperature,
                            apparent
                        )
                    ) {
                        Text(
                            text = "feels ${Units.formatTemperature(apparent, unitSystem)}",
                            style = DriveCastTheme.typography.bodySmall,
                            color = card.muted,
                            maxLines = 1
                        )
                    }
                }
            }

            is WaypointForecast.Failure -> Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = "Forecast unavailable",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun TrendArrow(delta: TemperatureDelta, tint: Color) {
    val direction = delta.trend.changeDirection
    if (direction == 0) return
    Icon(
        imageVector = if (direction > 0) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
        contentDescription = delta.accessibilityLabel,
        tint = tint,
        modifier = Modifier.size(22.dp)
    )
}

@Composable
private fun AlertLine(alert: WeatherAlert, hasDetail: Boolean) {
    val accent = DriveCastTheme.colors.alertAccents[alert.severity.ordinal]
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = "Weather alert",
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = buildString {
                append(alert.severity.label.uppercase(Locale.US))
                append(" · ")
                append(alert.event)
                if (alert.source == WeatherAlertSource.DERIVED) append(" (forecast)")
            },
            style = DriveCastTheme.typography.bodySmall,
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (hasDetail) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
