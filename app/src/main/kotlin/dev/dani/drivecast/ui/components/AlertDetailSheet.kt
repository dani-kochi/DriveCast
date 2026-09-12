package dev.dani.drivecast.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.dani.drivecast.domain.model.WaypointForecast
import dev.dani.drivecast.domain.model.WeatherAlert
import dev.dani.drivecast.ui.AlertDetail
import dev.dani.drivecast.ui.formatClock
import dev.dani.drivecast.ui.theme.DriveCastTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailSheet(
    forecast: WaypointForecast.Success,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val alerts = AlertDetail.ordered(forecast.alerts)
    if (alerts.isEmpty()) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DriveCastTheme.colorScheme.background,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = forecast.placeLabel,
                    style = DriveCastTheme.typography.titleMedium
                )
                Text(
                    text = "Arriving ${formatClock(forecast.waypoint.etaEpochSeconds)}",
                    style = DriveCastTheme.typography.bodySmall,
                    color = DriveCastTheme.colors.mutedText
                )
            }

            alerts.forEach { alert -> AlertBlock(alert) }

            Text(
                text = "Issued by the US National Weather Service.",
                style = DriveCastTheme.typography.bodySmall,
                color = DriveCastTheme.colors.mutedText
            )
        }
    }
}

@Composable
private fun AlertBlock(alert: WeatherAlert) {
    val accent = DriveCastTheme.colors.alertAccents[alert.severity.ordinal]
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = DriveCastTheme.colors.alertSurfaces[alert.severity.ordinal]
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = alert.event,
                    style = DriveCastTheme.typography.titleMedium,
                    color = accent
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CapField("Severity", alert.severity.label, Modifier.weight(1f))
                CapField("Urgency", alert.urgency.label, Modifier.weight(1f))
                CapField("Certainty", alert.certainty.label, Modifier.weight(1f))
            }

            alert.headline?.let { headline ->
                AlertDetail.paragraphs(headline).forEach {
                    Text(
                        text = it,
                        style = DriveCastTheme.typography.bodyMedium,
                        color = accent
                    )
                }
            }

            AlertDetail.paragraphs(alert.description).forEach {
                Text(
                    text = it,
                    style = DriveCastTheme.typography.bodySmall,
                    color = DriveCastTheme.colorScheme.onSurface
                )
            }

            AlertDetail.paragraphs(alert.instruction).takeIf { it.isNotEmpty() }?.let { body ->
                Text(
                    text = "What to do",
                    style = DriveCastTheme.typography.labelMedium,
                    color = accent
                )
                body.forEach {
                    Text(
                        text = it,
                        style = DriveCastTheme.typography.bodySmall,
                        color = DriveCastTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CapField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = "$label: $value" },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(Locale.US),
            style = DriveCastTheme.typography.labelSmall,
            color = DriveCastTheme.colors.mutedText
        )
        Text(
            text = value,
            style = DriveCastTheme.typography.bodySmall,
            color = DriveCastTheme.colorScheme.onSurface
        )
    }
}
