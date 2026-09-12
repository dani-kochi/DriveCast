package dev.dani.drivecast.ui.route

import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.RouteWeather
import dev.dani.drivecast.domain.model.UnitSystem

data class RouteInputState(
    val origin: String = "",
    val destination: String = "",
    val departureEpochSeconds: Long = 0L,
    val originPlace: Place? = null,
    val destinationPlace: Place? = null,
    val unitSystem: UnitSystem = UnitSystem.IMPERIAL,
) {
    val canSubmit: Boolean
        get() = origin.isNotBlank() && destination.isNotBlank()
    val isOriginNotSelected: Boolean
        get() = origin.isBlank() && originPlace == null
}

sealed interface SuggestionsUiState {

    /** Nothing to show: query too short, field untouched, or a suggestion was just taken. */
    data object Idle : SuggestionsUiState

    /** A lookup is in flight for the current query. */
    data object Loading : SuggestionsUiState

    data class Ready(val places: List<Place>) : SuggestionsUiState

    /** The lookup failed. The route pipeline is unaffected. */
    data class Error(val message: String) : SuggestionsUiState
}

/** The consolidated result state emitted by the aggregator flow. */
sealed interface RouteWeatherUiState {
    data object Idle : RouteWeatherUiState
    data object Loading : RouteWeatherUiState
    data class Success(val data: RouteWeather) : RouteWeatherUiState
    data class Error(val message: String) : RouteWeatherUiState
}

/** One-shot side effects delivered over a SharedFlow. */
sealed interface RouteEvent {
    data class ShowMessage(val message: String) : RouteEvent
    data object NavigateToResults : RouteEvent
}

/**
 * The "use my current location" affordance on the origin field.
 *
 * Every non-success outcome is a *hint*, never a dialog or an error banner: the origin field stays
 * empty and editable, and the user can always just type. Losing a location fix should be a
 * non-event.
 */
sealed interface CurrentLocationUiState {

    /** Nothing attempted yet, or the user has taken the field over. */
    data object Idle : CurrentLocationUiState

    /** A fix is in flight. Shown as a spinner in the field's trailing slot. */
    data object Resolving : CurrentLocationUiState

    /** The origin was filled in from the device's position. */
    data object Filled : CurrentLocationUiState

    /** No fix. [hint] is a short, calm explanation shown under the field. */
    data class Unavailable(val hint: String) : CurrentLocationUiState
}
