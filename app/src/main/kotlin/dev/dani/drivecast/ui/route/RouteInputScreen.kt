package dev.dani.drivecast.ui.route

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.dani.drivecast.R
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.ui.components.DriveCastButton
import dev.dani.drivecast.ui.components.DriveCastFilterChip
import dev.dani.drivecast.ui.components.DriveCastIconButton
import dev.dani.drivecast.ui.components.DriveCastTextButton
import dev.dani.drivecast.ui.formatClock
import dev.dani.drivecast.ui.theme.DriveCastTheme
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteInputScreen(
    formState: RouteInputState,
    originSuggestions: SuggestionsUiState,
    destinationSuggestions: SuggestionsUiState,
    currentLocation: CurrentLocationUiState,
    hasLocationPermission: () -> Boolean,
    onLocationPermissionResult: (Boolean) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onRequestCurrentLocation: () -> Unit,
    onOriginChanged: (String) -> Unit,
    onDestinationChanged: (String) -> Unit,
    onOriginSuggestionSelected: (Place) -> Unit,
    onDestinationSuggestionSelected: (Place) -> Unit,
    onDismissOriginSuggestions: () -> Unit,
    onDismissDestinationSuggestions: () -> Unit,
    onDepartureChanged: (Long) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val destinationFocus = remember { FocusRequester() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        onLocationPermissionResult(granted.values.any { it })
        runCatching { destinationFocus.requestFocus() }
    }

    LaunchedEffect(Unit) {
        runCatching { destinationFocus.requestFocus() }
        if (hasLocationPermission()) {
            onRequestCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AppMark()
                        Text(
                            text = "DriveCast",
                            style = DriveCastTheme.typography.headlineMedium,
                            color = Color.White,
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Weather along your route, timed to when you actually get there.",
                style = DriveCastTheme.typography.bodyMedium
            )

            PlaceField(
                text = formState.origin,
                onTextChanged = onOriginChanged,
                label = "Origin",
                trailingIcon = {
                    if (currentLocation is CurrentLocationUiState.Resolving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        DriveCastIconButton(
                            onClick = onUseCurrentLocation,
                            icon = Icons.Filled.MyLocation,
                            contentDescription = "Use current location"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    onDismissOriginSuggestions()
                    runCatching { destinationFocus.requestFocus() }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (!it.isFocused) onDismissOriginSuggestions() }
            )
            if (currentLocation is CurrentLocationUiState.Unavailable) {
                Text(
                    text = currentLocation.hint,
                    style = DriveCastTheme.typography.bodySmall,
                    color = DriveCastTheme.colors.mutedText
                )
            }
            SuggestionList(
                state = originSuggestions,
                onSelected = onOriginSuggestionSelected
            )

            PlaceField(
                text = formState.destination,
                onTextChanged = onDestinationChanged,
                label = "Destination",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDismissDestinationSuggestions() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(destinationFocus)
                    .onFocusChanged { if (!it.isFocused) onDismissDestinationSuggestions() }
            )
            SuggestionList(
                state = destinationSuggestions,
                onSelected = onDestinationSuggestionSelected
            )

            Text("Departure", style = DriveCastTheme.typography.titleMedium)
            Text(
                text = formatClock(formState.departureEpochSeconds),
                style = DriveCastTheme.typography.bodyLarge
            )
            DepartureChips(
                selectedEpochSeconds = formState.departureEpochSeconds,
                onDepartureChanged = onDepartureChanged
            )

            DriveCastButton(
                onClick = onSubmit,
                text = "Forecast my drive",
                icon = Icons.Filled.PlayArrow,
                enabled = formState.canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = DriveCastTheme.spacing.actionSeparation)
            )
        }
    }
}

@Composable
private fun AppMark() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            // Decorative: the title says "DriveCast" immediately to its right, and describing the
            // mark as well would only make a screen reader announce the name twice.
            contentDescription = null,
            modifier = Modifier
                .requiredSize(30.dp * ART_SCALE)
                .offset(x = 0.dp, y = 0.dp)
        )
    }
}

private const val ART_SCALE = 1.6364f

@Composable
private fun PlaceField(
    text: String,
    onTextChanged: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(text, TextRange(text.length)))
    }

    if (field.text != text) {
        field = TextFieldValue(text, TextRange(text.length))
    }

    OutlinedTextField(
        value = field,
        onValueChange = { updated ->
            val textChanged = updated.text != field.text
            field = updated
            if (textChanged) onTextChanged(updated.text)
        },
        label = { Text(label) },
        singleLine = true,
        trailingIcon = trailingIcon,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier
    )
}

@Composable
private fun SuggestionList(
    state: SuggestionsUiState,
    onSelected: (Place) -> Unit
) {
    when (state) {
        SuggestionsUiState.Idle -> Unit

        SuggestionsUiState.Loading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Searching\u2026", style = DriveCastTheme.typography.bodySmall)
        }

        is SuggestionsUiState.Error -> Text(
            text = state.message,
            style = DriveCastTheme.typography.bodySmall,
            color = DriveCastTheme.colors.errorText,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        is SuggestionsUiState.Ready -> {
            if (state.places.isEmpty()) {
                Text(
                    text = "No matches.",
                    style = DriveCastTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                return
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    state.places.take(5).forEach { place ->
                        DriveCastTextButton(
                            onClick = { onSelected(place) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = place.name,
                                    style = DriveCastTheme.typography.bodyLarge
                                )
                                // admin + country disambiguate the many Springfields.
                                val qualifier = listOfNotNull(place.admin, place.country)
                                    .joinToString(", ")
                                if (qualifier.isNotEmpty()) {
                                    Text(
                                        text = qualifier,
                                        style = DriveCastTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DepartureChips(
    selectedEpochSeconds: Long,
    onDepartureChanged: (Long) -> Unit
) {
    val options = listOf(
        "+1h" to 1L,
        "+3h" to 3L,
        "+6h" to 6L,
        "+12h" to 12L,
        "+24h" to 24L
    )
    val now = Instant.now().truncatedTo(ChronoUnit.MINUTES).epochSecond
    val scrollState = rememberScrollState()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
    ) {
        options.forEach { (label, hours) ->
            val epoch = now + hours * 3600
            DriveCastFilterChip(
                selected = kotlin.math.abs(selectedEpochSeconds - epoch) < 120,
                onClick = { onDepartureChanged(epoch) },
                label = label
            )
        }
    }
}
