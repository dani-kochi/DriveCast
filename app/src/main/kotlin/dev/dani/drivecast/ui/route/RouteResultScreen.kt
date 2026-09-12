package dev.dani.drivecast.ui.route

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dani.drivecast.domain.model.RouteWeather
import dev.dani.drivecast.domain.model.WaypointForecast
import dev.dani.drivecast.ui.Units
import dev.dani.drivecast.ui.components.AlertDetailSheet
import dev.dani.drivecast.ui.components.DriveCastButton
import dev.dani.drivecast.ui.components.DriveCastIconButton
import dev.dani.drivecast.ui.components.WeatherTimelineItem
import dev.dani.drivecast.ui.formatDuration
import dev.dani.drivecast.ui.openRouteInGoogleMaps
import dev.dani.drivecast.ui.theme.DriveCastTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteResultScreen(
    state: RouteWeatherUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    titleContentColor = DriveCastTheme.colors.topBarContent,
                    navigationIconContentColor = DriveCastTheme.colors.topBarContent,
                    actionIconContentColor = DriveCastTheme.colors.topBarContent
                ),
                title = { Text(text = "Route Forecast", style = DriveCastTheme.typography.headlineSmall) },
                navigationIcon = {
                    DriveCastIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                },
                actions = {
                    DriveCastIconButton(
                        onClick = onRetry,
                        icon = Icons.Filled.Refresh,
                        contentDescription = "Refresh"
                    )
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state) {
                RouteWeatherUiState.Idle -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Enter a route to see the forecast.", textAlign = TextAlign.Center)
                }

                RouteWeatherUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            text = "Finding best route and weather forecast...",
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                is RouteWeatherUiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = state.message,
                            textAlign = TextAlign.Center,
                            style = DriveCastTheme.typography.bodyLarge
                        )
                        DriveCastButton(onClick = onRetry, text = "Retry")
                    }
                }

                is RouteWeatherUiState.Success -> SuccessContent(state.data)
            }
        }
    }
}

@Composable
private fun OpenInMapsItem(data: RouteWeather) {
    val context = LocalContext.current
    DriveCastButton(
        text = "Open in Google Maps",
        icon = Icons.Filled.Directions,
        onClick = {
            context.openRouteInGoogleMaps(
                buildList {
                    add(data.origin.location)
                    data.forecasts.forEach { add(it.waypoint.location) }
                    add(data.destination.location)
                }
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = DriveCastTheme.spacing.actionSeparation)
    )
}

@Composable
private fun SuccessContent(data: RouteWeather) {
    var alertDetailTarget by remember { mutableStateOf<WaypointForecast.Success?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = Units.formatDistance(data.route.distanceMeters, data.unitSystem),
                    style = DriveCastTheme.typography.titleMedium
                )
                Text(
                    text = formatDuration(data.route.durationSeconds.toLong()),
                    style = DriveCastTheme.typography.titleMedium
                )
            }
        }
        itemsIndexed(
            items = data.forecasts,
            key = { _, forecast -> forecast.waypoint.index }
        ) { index, forecast ->
            WeatherTimelineItem(
                forecast = forecast,
                benchmarkTemperature = data.benchmarkTemperature,
                unitSystem = data.unitSystem,
                isBenchmark = index == 0,
                showTimelineHeader = index > 0,
                onShowAlertDetail = { alertDetailTarget = it },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item { OpenInMapsItem(data) }
    }

    alertDetailTarget?.let { target ->
        AlertDetailSheet(
            forecast = target,
            onDismiss = { alertDetailTarget = null }
        )
    }
}