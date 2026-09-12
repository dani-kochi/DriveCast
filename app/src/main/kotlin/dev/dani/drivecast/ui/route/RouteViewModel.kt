package dev.dani.drivecast.ui.route

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.dani.drivecast.domain.location.LocationData
import dev.dani.drivecast.domain.location.LocationProvider
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.QueryInput
import dev.dani.drivecast.domain.usecase.GetCurrentPlaceUseCase
import dev.dani.drivecast.domain.usecase.GetRouteWeatherUseCase
import dev.dani.drivecast.domain.usecase.SearchPlacesUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class RouteViewModel @Inject constructor(
    private val getRouteWeather: GetRouteWeatherUseCase,
    private val searchPlaces: SearchPlacesUseCase,
    private val getCurrentPlace: GetCurrentPlaceUseCase,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        _uiState.value = RouteWeatherUiState.Error(throwable.readableMessage())
        _events.tryEmit(RouteEvent.ShowMessage(throwable.readableMessage()))
    }

    private val uiStateScope: CoroutineScope = viewModelScope + exceptionHandler

    private val _inputState = MutableStateFlow(RouteInputState(departureEpochSeconds = nextHourEpochSeconds()))
    val inputState: StateFlow<RouteInputState> = _inputState.asStateFlow()

    private val _uiState = MutableStateFlow<RouteWeatherUiState>(RouteWeatherUiState.Idle)
    val uiState: StateFlow<RouteWeatherUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RouteEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RouteEvent> = _events.asSharedFlow()

    private val requests = MutableSharedFlow<GetRouteWeatherUseCase.Params>(extraBufferCapacity = 1)

    private val originQuery = MutableStateFlow(QueryInput())
    private val destinationQuery = MutableStateFlow(QueryInput())

    val originSuggestions: StateFlow<SuggestionsUiState> = suggestionPipeline(originQuery)
    val destinationSuggestions: StateFlow<SuggestionsUiState> = suggestionPipeline(destinationQuery)

    private val _currentLocation = MutableStateFlow<CurrentLocationUiState>(CurrentLocationUiState.Idle)
    val currentLocation: StateFlow<CurrentLocationUiState> = _currentLocation.asStateFlow()
    private var locationJob: Job? = null
    private var queryToken = 0L

    private fun dismissal(text: String) = QueryInput(text = text, suppressSearch = true, token = ++queryToken)

    init {
        observeRequests()
    }

    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    // Uses the device's location as the origin
    fun useCurrentLocationAsOrigin(force: Boolean = false) {
        if (force.not() && _inputState.value.isOriginNotSelected.not()) return

        locationJob?.cancel()
        locationJob = uiStateScope.launch {
            _currentLocation.update { CurrentLocationUiState.Resolving }
            val result = runCatching { getCurrentPlace() }.getOrElse {
                GetCurrentPlaceUseCase.Result.Unavailable(LocationData.Unavailable)
            }

            when (result) {
                is GetCurrentPlaceUseCase.Result.Resolved ->
                    if (force.not() && _inputState.value.isOriginNotSelected.not()) {
                        _currentLocation.update { CurrentLocationUiState.Idle }
                    } else {
                        updateOrigin(result.place)
                        originQuery.update { dismissal(result.place.displayName) }
                        _currentLocation.update { CurrentLocationUiState.Filled }
                    }

                is GetCurrentPlaceUseCase.Result.Unavailable ->
                    _currentLocation.update { CurrentLocationUiState.Unavailable(result.reason.hint()) }
            }
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            useCurrentLocationAsOrigin()
        } else {
            _currentLocation.update { CurrentLocationUiState.Unavailable(LocationData.PermissionMissing.hint()) }
        }
    }

    private fun LocationData.hint(): String = when (this) {
        is LocationData.Available -> ""
        LocationData.PermissionMissing -> "Location access is off. Type an origin, or allow access."
        LocationData.LocationDisabled -> "Location services are off. Type an origin instead."
        LocationData.Unavailable -> "Couldn't get your location. Type an origin instead."
    }

    fun onOriginChanged(value: String) {
        if (value == _inputState.value.origin) return

        _inputState.update { it.copy(origin = value, originPlace = null) }
        originQuery.update { QueryInput(text = value, token = ++queryToken) }

        locationJob?.cancel()
        _currentLocation.update { CurrentLocationUiState.Idle }
    }

    fun onDestinationChanged(value: String) {
        if (value == _inputState.value.destination) return
        _inputState.update { it.copy(destination = value, destinationPlace = null) }
        destinationQuery.update { QueryInput(text = value, token = ++queryToken) }
    }

    fun onOriginSuggestionSelected(place: Place) {
        updateOrigin(place)
        originQuery.value = dismissal(place.displayName)
    }

    fun onDestinationSuggestionSelected(place: Place) {
        updateDestination(place)
        destinationQuery.value = dismissal(place.displayName)
    }

    fun dismissOriginSuggestions() {
        originQuery.value = dismissal(_inputState.value.origin)
    }

    fun dismissDestinationSuggestions() {
        destinationQuery.value = dismissal(_inputState.value.destination)
    }

    fun onDepartureChanged(epochSeconds: Long) {
        _inputState.update { it.copy(departureEpochSeconds = epochSeconds) }
        // Re-run automatically when a route has already been computed.
        if (_uiState.value is RouteWeatherUiState.Success) submit(navigate = false)
    }

    fun submit(navigate: Boolean = true) {
        val input = _inputState.value
        if (input.canSubmit.not()) {
            _events.tryEmit(RouteEvent.ShowMessage("Enter both an origin and a destination."))
            return
        }
        if (navigate) _events.tryEmit(RouteEvent.NavigateToResults)
        requests.tryEmit(
            GetRouteWeatherUseCase.Params(
                originQuery = input.origin,
                destinationQuery = input.destination,
                departureEpochSeconds = input.departureEpochSeconds,
                originPlace = input.originPlace,
                destinationPlace = input.destinationPlace,
                unitSystem = input.unitSystem,
            )
        )
    }

    fun refresh() {
        uiStateScope.launch { retry() }
    }

    private fun suggestionPipeline(
        queryFlow: MutableStateFlow<QueryInput>
    ): StateFlow<SuggestionsUiState> = queryFlow
        .map { it.copy(text = it.text.trim()) }
        .distinctUntilChangedBy { it.dedupKey }
        .debounce { input -> if (input.isSearchable) SUGGESTION_DEBOUNCE_MS else 0L }
        .flatMapLatest { input ->
            if (input.isSearchable.not()) {
                flowOf(SuggestionsUiState.Idle)
            } else {
                flow {
                    emit(SuggestionsUiState.Loading)
                    emit(SuggestionsUiState.Ready(searchPlaces(input.text)))
                }.catch { throwable ->
                    emit(SuggestionsUiState.Error(throwable.readableMessage()))
                }
            }
        }
        .stateIn(uiStateScope, SharingStarted.Eagerly, SuggestionsUiState.Idle)

    private fun observeRequests() {
        requests
            .debounce(REQUEST_DEBOUNCE_MS.milliseconds)
            .flatMapLatest { params ->
                flow {
                    emit(RouteWeatherUiState.Loading)
                    emit(RouteWeatherUiState.Success(getRouteWeather(params)))
                }.catch { throwable ->
                    emit(RouteWeatherUiState.Error(throwable.readableMessage()))
                }
            }
            .onEach { state -> _uiState.value = state }
            .launchIn(uiStateScope)
    }

    private fun retry() = submit(navigate = false)

    private fun updateOrigin(place: Place) {
        _inputState.update {
            it.copy(
                origin = place.displayName,
                originPlace = place
            )
        }
    }

    private fun updateDestination(place: Place) {
        _inputState.update {
            it.copy(
                destination = place.displayName,
                destinationPlace = place
            )
        }
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: (this::class.simpleName ?: "Unexpected error")

    companion object {
        private const val SUGGESTION_DEBOUNCE_MS = 300L
        private const val REQUEST_DEBOUNCE_MS = 250L

        fun nextHourEpochSeconds(): Long =
            Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS).epochSecond
    }
}
