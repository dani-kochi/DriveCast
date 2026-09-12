package dev.dani.drivecast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.dani.drivecast.ui.route.RouteEvent
import dev.dani.drivecast.ui.route.RouteInputScreen
import dev.dani.drivecast.ui.route.RouteResultScreen
import dev.dani.drivecast.ui.route.RouteViewModel

object Routes {
    const val INPUT = "input"
    const val RESULTS = "results"
}

@Composable
fun DriveCastApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val viewModel: RouteViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    val formState by viewModel.inputState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val originSuggestions by viewModel.originSuggestions.collectAsStateWithLifecycle()
    val destinationSuggestions by viewModel.destinationSuggestions.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RouteEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                RouteEvent.NavigateToResults -> navController.navigate(Routes.RESULTS)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.INPUT
        ) {
            composable(Routes.INPUT) {
                RouteInputScreen(
                    formState = formState,
                    originSuggestions = originSuggestions,
                    destinationSuggestions = destinationSuggestions,
                    currentLocation = currentLocation,
                    hasLocationPermission = viewModel::hasLocationPermission,
                    onLocationPermissionResult = viewModel::onLocationPermissionResult,
                    onUseCurrentLocation = { viewModel.useCurrentLocationAsOrigin(force = true) },
                    onRequestCurrentLocation = { viewModel.useCurrentLocationAsOrigin() },
                    onOriginChanged = viewModel::onOriginChanged,
                    onDestinationChanged = viewModel::onDestinationChanged,
                    onOriginSuggestionSelected = viewModel::onOriginSuggestionSelected,
                    onDestinationSuggestionSelected = viewModel::onDestinationSuggestionSelected,
                    onDismissOriginSuggestions = viewModel::dismissOriginSuggestions,
                    onDismissDestinationSuggestions = viewModel::dismissDestinationSuggestions,
                    onDepartureChanged = viewModel::onDepartureChanged,
                    onSubmit = { viewModel.submit() }
                )
            }
            composable(Routes.RESULTS) {
                RouteResultScreen(
                    state = uiState,
                    onBack = { navController.popBackStack() },
                    onRetry = viewModel::refresh
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
