package dev.dani.drivecast.ui.route

import dev.dani.drivecast.data.repository.DriveCastException
import dev.dani.drivecast.data.repository.DriveCastRepository
import dev.dani.drivecast.domain.location.LocationData
import dev.dani.drivecast.domain.location.LocationProvider
import dev.dani.drivecast.domain.model.LatLng
import dev.dani.drivecast.domain.model.Place
import dev.dani.drivecast.domain.model.ResolvedPlace
import dev.dani.drivecast.domain.model.Route
import dev.dani.drivecast.domain.model.UnitSystem
import dev.dani.drivecast.domain.model.Weather
import dev.dani.drivecast.domain.model.WeatherAlert
import dev.dani.drivecast.domain.usecase.GetCurrentPlaceUseCase
import dev.dani.drivecast.domain.usecase.GetRouteWeatherUseCase
import dev.dani.drivecast.domain.usecase.SearchPlacesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Covers the type-ahead pipeline: debounce, the minimum-length short circuit, and above all that
 * [kotlinx.coroutines.flow.flatMapLatest] discards results for superseded keystrokes.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RouteViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: FakeRepository,
        locationProvider: FakeLocationProvider = FakeLocationProvider()
    ) = RouteViewModel(
        getRouteWeather = GetRouteWeatherUseCase(repository),
        searchPlaces = SearchPlacesUseCase(repository),
        getCurrentPlace = GetCurrentPlaceUseCase(locationProvider, repository),
        locationProvider = locationProvider
    )

    /** Turbine-style: record every state the field emits so ordering can be asserted. */
    private fun TestScope.record(flow: StateFlow<SuggestionsUiState>) =
        CopyOnWriteArrayList<SuggestionsUiState>().also { log ->
            flow.onEach { log += it }.launchIn(backgroundScope)
        }

    /**
     * Drains pending work *and* the recorder coroutines. `advanceUntilIdle` on its own returns
     * before collectors running in [TestScope.backgroundScope] have observed the latest value, so
     * the yield is what makes the recorded sequence deterministic.
     */
    private suspend fun TestScope.settle() {
        advanceUntilIdle()
        yield()
        advanceUntilIdle()
    }

    @Test
    fun `a burst of keystrokes only ever yields the last query's results`() = runTest(dispatcher) {
        // "Ber" is slow, "Berlin" is fast: without flatMapLatest the stale response would land
        // last and win.
        val repository = FakeRepository(
            delaysByQuery = mapOf("Ber" to 5_000L, "Berlin" to 10L)
        )
        val vm = viewModel(repository)
        val states = record(vm.originSuggestions)

        vm.onOriginChanged("B")
        advanceTimeBy(50)
        vm.onOriginChanged("Be")
        advanceTimeBy(50)
        vm.onOriginChanged("Ber")
        advanceTimeBy(400) // long enough to let "Ber" past the debounce and start its lookup
        vm.onOriginChanged("Berlin")
        settle()

        val ready = states.filterIsInstance<SuggestionsUiState.Ready>()
        assertEquals(
            "only the final query may produce results, saw $ready",
            listOf(listOf("Berlin")),
            ready.map { r -> r.places.map { it.name } }
        )
        assertTrue("the superseded lookup must be cancelled", "Ber" in repository.cancelled)
        assertEquals(listOf("Berlin"), repository.completed)
    }

    @Test
    fun `debounce collapses a burst into a single lookup`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        record(vm.originSuggestions)

        "Hamburg".forEachIndexed { index, _ ->
            vm.onOriginChanged("Hamburg".take(index + 1))
            advanceTimeBy(50) // faster than the 300ms debounce window
        }
        settle()

        assertEquals(listOf("Hamburg"), repository.started)
    }

    @Test
    fun `blank and too-short queries never reach the network`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        val states = record(vm.originSuggestions)

        listOf("", " ", "B", "  x  ").forEach {
            vm.onOriginChanged(it)
            settle()
        }

        assertEquals(emptyList<String>(), repository.started)
        assertTrue(
            "expected no loading state, saw $states",
            states.none { it is SuggestionsUiState.Loading }
        )
        assertTrue(states.all { it is SuggestionsUiState.Idle })
    }

    @Test
    fun `a failing lookup surfaces an error without disturbing the sibling field`() = runTest(dispatcher) {
        val repository = FakeRepository(failingQueries = setOf("Boom"))
        val vm = viewModel(repository)
        val originStates = record(vm.originSuggestions)
        val destinationStates = record(vm.destinationSuggestions)

        vm.onOriginChanged("Boom")
        vm.onDestinationChanged("Hamburg")
        settle()

        val error = originStates.filterIsInstance<SuggestionsUiState.Error>().single()
        assertEquals("boom", error.message)

        // The sibling pipeline completed normally, and the view model is still alive.
        val ready = destinationStates.filterIsInstance<SuggestionsUiState.Ready>().single()
        assertEquals(listOf("Hamburg"), ready.places.map { it.name })
        assertTrue(vm.uiState.value is RouteWeatherUiState.Idle)
    }

    @Test
    fun `the two fields run independent pipelines`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        val originStates = record(vm.originSuggestions)

        vm.onOriginChanged("Berlin")
        settle()
        val afterOrigin = originStates.size

        // Typing in the destination must not cancel, restart or otherwise perturb origin.
        vm.onDestinationChanged("Hamburg")
        settle()

        assertEquals(afterOrigin, originStates.size)
        assertEquals(
            listOf("Berlin"),
            (vm.originSuggestions.value as SuggestionsUiState.Ready).places.map { it.name }
        )
        assertEquals(listOf("Berlin", "Hamburg"), repository.started)
    }

    @Test
    fun `choosing a suggestion fills the field without searching for the inserted text`() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val vm = viewModel(repository)
            record(vm.originSuggestions)

            vm.onOriginChanged("Berlin")
            settle()
            val place = (vm.originSuggestions.value as SuggestionsUiState.Ready).places.first()

            vm.onOriginSuggestionSelected(place)
            settle()

            assertEquals(place.displayName, vm.inputState.value.origin)
            assertSame(place, vm.inputState.value.originPlace)
            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
            // Crucially: no second lookup for the label the tap just wrote into the field.
            assertEquals(listOf("Berlin"), repository.started)
        }

    @Test
    fun `editing after a selection drops the pinned coordinates`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        record(vm.originSuggestions)

        vm.onOriginChanged("Berlin")
        settle()
        vm.onOriginSuggestionSelected(
            (vm.originSuggestions.value as SuggestionsUiState.Ready).places.first()
        )
        settle()

        vm.onOriginChanged("Berlin!")
        settle()

        assertNull(
            "hand edits invalidate the resolved place",
            vm.inputState.value.originPlace
        )
    }

    @Test
    fun `tapping a suggestion whose label already matches the field still dismisses it`() =
        runTest(dispatcher) {
            // displayName == the text the user typed, which is exactly what a repeat selection
            // leaves in the field.
            val target = Place("Berlin", "Berlin", "Germany", LatLng(52.52, 13.40))
            assertEquals("Berlin, Germany", target.displayName)
            val repository = FakeRepository(fixedPlace = target)
            val vm = viewModel(repository)
            record(vm.originSuggestions)

            vm.onOriginChanged("Berlin, Germany")
            settle()
            assertTrue(
                "precondition: the list is open",
                vm.originSuggestions.value is SuggestionsUiState.Ready
            )

            vm.onOriginSuggestionSelected(target)
            settle()

            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
            assertSame(target, vm.inputState.value.originPlace)
        }

    @Test
    fun `selecting the same suggestion twice dismisses and pins both times`() =
        runTest(dispatcher) {
            val target = Place("Berlin", "Berlin", "Germany", LatLng(52.52, 13.40))
            val repository = FakeRepository(fixedPlace = target)
            val vm = viewModel(repository)
            record(vm.originSuggestions)

            vm.onOriginChanged("Berl")
            settle()
            vm.onOriginSuggestionSelected(target)
            settle()
            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)

            // Back to a list, then the identical tap again.
            vm.onOriginChanged("Berl")
            settle()
            assertTrue(vm.originSuggestions.value is SuggestionsUiState.Ready)

            vm.onOriginSuggestionSelected(target)
            settle()
            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
            assertSame("the pin must survive a repeat tap", target, vm.inputState.value.originPlace)

            // And a second identical tap with no typing in between: the value pushed into the
            // query flow is equals-identical to the current one, which a plain StateFlow conflates.
            vm.onOriginSuggestionSelected(target)
            settle()
            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
            assertSame(target, vm.inputState.value.originPlace)
        }

    @Test
    fun `re-reporting the same text performs no lookup and keeps the pin`() =
        runTest(dispatcher) {
            // The caret contract. The field only forwards changes when the text actually differs,
            // but the view-model is the layer that must not be fooled if something ever does
            // report a caret move as a change: no second network call, and above all no loss of
            // the coordinates the user picked.
            val target = Place("Berlin", "Berlin", "Germany", LatLng(52.52, 13.40))
            val repository = FakeRepository(fixedPlace = target)
            val vm = viewModel(repository)
            record(vm.originSuggestions)

            vm.onOriginChanged("Berl")
            settle()
            vm.onOriginSuggestionSelected(target)
            settle()

            vm.onOriginChanged(vm.inputState.value.origin)
            settle()

            assertSame("a caret move must not unpin the place", target, vm.inputState.value.originPlace)
            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
            assertEquals("no lookup for text that did not change", listOf("Berl"), repository.started)
        }

    @Test
    fun `a caret move in one field cannot disturb the other`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        record(vm.destinationSuggestions)

        vm.onDestinationChanged("Hamburg")
        settle()
        assertTrue(vm.destinationSuggestions.value is SuggestionsUiState.Ready)

        vm.onOriginChanged("")
        vm.onDestinationChanged("Hamburg")
        settle()

        assertTrue(
            "the destination list survives a no-op report",
            vm.destinationSuggestions.value is SuggestionsUiState.Ready
        )
        assertEquals(listOf("Hamburg"), repository.started)
    }

    @Test
    fun `the prefilled origin carries the whole label, so the caret has an end to land on`() =
        runTest {
            val repository = FakeRepository(
                reverseGeocode = ResolvedPlace(
                    locality = "Dallas",
                    adminArea = "Texas",
                    country = "United States of America",
                    countryCode = "US"
                )
            )
            val vm = viewModel(
                repository,
                FakeLocationProvider(fix = LocationData.Available(LatLng(32.7767, -96.7970)))
            )

            vm.useCurrentLocationAsOrigin(force = true)
            settle()

            // The field mirrors this string and puts the caret at its length. A caret left at the
            // offset the user had reached while typing would sit inside the name, not after it.
            assertEquals("Dallas, TX", vm.inputState.value.origin)
            assertEquals(
                "the caret target is the end of the whole label",
                "Dallas, TX".length,
                vm.inputState.value.origin.length
            )
        }

    @Test
    fun `an explicit dismissal closes the list and cancels an in-flight lookup`() =
        runTest(dispatcher) {
            val repository = FakeRepository(delaysByQuery = mapOf("Berlin" to 5_000))
            val vm = viewModel(repository)
            record(vm.originSuggestions)

            vm.onOriginChanged("Berlin")
            advanceTimeBy(400) // past the 300ms debounce, so the lookup is genuinely in flight
            assertEquals(listOf("Berlin"), repository.started)

            vm.dismissOriginSuggestions()
            settle()

            assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
            assertEquals("the slow lookup is cancelled, not awaited", listOf("Berlin"), repository.cancelled)
            assertTrue(repository.completed.isEmpty())
        }

    @Test
    fun `dismissing is idempotent and leaves the text and the pin alone`() = runTest(dispatcher) {
        val target = Place("Berlin", "Berlin", "Germany", LatLng(52.52, 13.40))
        val repository = FakeRepository(fixedPlace = target)
        val vm = viewModel(repository)
        record(vm.originSuggestions)

        vm.onOriginChanged("Berl")
        settle()
        vm.onOriginSuggestionSelected(target)
        settle()

        repeat(3) {
            vm.dismissOriginSuggestions()
            settle()
        }

        assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
        assertEquals(target.displayName, vm.inputState.value.origin)
        assertSame(target, vm.inputState.value.originPlace)
        // No dismissal ever triggers a lookup, however many arrive.
        assertEquals(listOf("Berl"), repository.started)
    }

    @Test
    fun `dismissing one field leaves the other field's list open`() = runTest(dispatcher) {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        record(vm.originSuggestions)
        record(vm.destinationSuggestions)

        vm.onOriginChanged("Berlin")
        vm.onDestinationChanged("Munich")
        settle()

        vm.dismissOriginSuggestions()
        settle()

        assertEquals(SuggestionsUiState.Idle, vm.originSuggestions.value)
        assertTrue(
            "the sibling pipeline is untouched",
            vm.destinationSuggestions.value is SuggestionsUiState.Ready
        )
    }

    // ---------------------------------------------------------------------------------------
    // Current location
    // ---------------------------------------------------------------------------------------

    @Test
    fun `the origin is prefilled from the device location, pinned to its coordinates`() = runTest {
        val repository = FakeRepository(
            reverseGeocode = ResolvedPlace(
                locality = "Dallas",
                adminArea = "Texas",
                country = "United States of America",
                countryCode = "US"
            )
        )
        val location = LatLng(32.7767, -96.7970)
        val vm = viewModel(repository, FakeLocationProvider(fix = LocationData.Available(location)))

        vm.useCurrentLocationAsOrigin()
        settle()

        // The country is the user's own, so the "(if different)" rule correctly omits it.
        assertEquals("Dallas, TX", vm.inputState.value.origin)
        assertEquals(location, vm.inputState.value.originPlace?.location)
        assertEquals(CurrentLocationUiState.Filled, vm.currentLocation.value)
        // Writing the label into the field must not kick off a search for it.
        assertEquals(emptyList<String>(), repository.started)
    }

    @Test
    fun `a location fix arriving after the user starts typing is discarded`() = runTest {
        val repository = FakeRepository()
        val provider = FakeLocationProvider(
            fix = LocationData.Available(LatLng(1.0, 2.0)),
            delayMs = 5_000
        )
        val vm = viewModel(repository, provider)

        vm.useCurrentLocationAsOrigin()
        advanceTimeBy(100)
        vm.onOriginChanged("Berlin") // the user got there first
        settle()

        assertEquals("Berlin", vm.inputState.value.origin)
        assertEquals(CurrentLocationUiState.Idle, vm.currentLocation.value)
    }

    @Test
    fun `the automatic prefill backs off once an origin has been picked`() = runTest {
        val repository = FakeRepository()
        val provider = FakeLocationProvider(fix = LocationData.Available(LatLng(1.0, 2.0)))
        val vm = viewModel(repository, provider)

        vm.onOriginChanged("Berlin")
        settle()
        vm.onOriginSuggestionSelected(
            (vm.originSuggestions.value as SuggestionsUiState.Ready).places.first()
        )
        settle()

        vm.useCurrentLocationAsOrigin()
        settle()

        assertEquals(0, provider.calls)
        assertEquals("Berlin, Germany", vm.inputState.value.origin)
    }

    @Test
    fun `tapping the crosshair overwrites whatever the user typed`() = runTest {
        val repository = FakeRepository()
        val provider = FakeLocationProvider(fix = LocationData.Available(LatLng(48.85, 2.35)))
        val vm = viewModel(repository, provider)

        vm.onOriginChanged("somewhere else")
        settle()
        vm.useCurrentLocationAsOrigin(force = true)
        settle()

        assertEquals(1, provider.calls)
        assertEquals(LatLng(48.85, 2.35), vm.inputState.value.originPlace?.location)
        assertEquals(CurrentLocationUiState.Filled, vm.currentLocation.value)
    }

    @Test
    fun `a denied permission leaves the origin empty with a calm hint`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository, FakeLocationProvider(hasPermission = false))

        vm.onLocationPermissionResult(granted = false)
        settle()

        val state = vm.currentLocation.value as CurrentLocationUiState.Unavailable
        assertTrue(state.hint, state.hint.contains("Type an origin"))
        assertEquals("", vm.inputState.value.origin)
        assertNull(vm.inputState.value.originPlace)
        assertTrue(vm.uiState.value is RouteWeatherUiState.Idle)
    }

    @Test
    fun `each unavailable outcome gets its own hint and none of them is an error`() = runTest {
        listOf(
            LocationData.PermissionMissing,
            LocationData.LocationDisabled,
            LocationData.Unavailable
        ).forEach { fix ->
            val vm = viewModel(FakeRepository(), FakeLocationProvider(fix = fix))

            vm.useCurrentLocationAsOrigin()
            settle()

            val state = vm.currentLocation.value
            assertTrue("$fix -> $state", state is CurrentLocationUiState.Unavailable)
            assertTrue(
                "$fix produced a blank hint",
                (state as CurrentLocationUiState.Unavailable).hint.isNotBlank()
            )
            assertTrue("$fix disturbed the route", vm.uiState.value is RouteWeatherUiState.Idle)
        }
    }

    @Test
    fun `a location failure does not disturb the destination pipeline`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository, FakeLocationProvider(throws = true))
        val destinationStates = record(vm.destinationSuggestions)

        vm.useCurrentLocationAsOrigin()
        vm.onDestinationChanged("Hamburg")
        settle()

        assertTrue(vm.currentLocation.value is CurrentLocationUiState.Unavailable)
        val ready = destinationStates.filterIsInstance<SuggestionsUiState.Ready>().single()
        assertEquals(listOf("Hamburg"), ready.places.map { it.name })
    }

    @Test
    fun `a dead reverse geocoder still pins usable coordinates`() = runTest {
        val repository = FakeRepository(reverseGeocodeFails = true)
        val location = LatLng(32.7767, -96.7970)
        val vm = viewModel(repository, FakeLocationProvider(fix = LocationData.Available(location)))

        vm.useCurrentLocationAsOrigin()
        settle()

        assertEquals(location, vm.inputState.value.originPlace?.location)
        assertTrue(vm.inputState.value.origin, vm.inputState.value.origin.contains("°"))
        assertEquals(CurrentLocationUiState.Filled, vm.currentLocation.value)
    }

    @Test
    fun `focusing the destination does not open a dropdown for empty text`() = runTest {
        val repository = FakeRepository()
        val vm = viewModel(repository)
        val states = record(vm.destinationSuggestions)
        settle()

        assertEquals(listOf<SuggestionsUiState>(SuggestionsUiState.Idle), states)
        assertEquals(emptyList<String>(), repository.started)
    }

    private class FakeLocationProvider(
        private val fix: LocationData = LocationData.Unavailable,
        private val hasPermission: Boolean = true,
        private val delayMs: Long = 0,
        private val throws: Boolean = false
    ) : LocationProvider {
        var calls = 0
            private set

        override fun hasPermission(): Boolean = hasPermission

        override suspend fun currentLocation(): LocationData {
            calls++
            delay(delayMs)
            if (throws) throw IllegalStateException("location subsystem exploded")
            return fix
        }
    }

    private class FakeRepository(
        private val delaysByQuery: Map<String, Long> = emptyMap(),
        private val failingQueries: Set<String> = emptySet(),
        private val defaultDelayMs: Long = 10,
        private val reverseGeocode: ResolvedPlace = ResolvedPlace(),
        private val reverseGeocodeFails: Boolean = false,
        /** When set, every query resolves to this one place, so "the same suggestion" is literal. */
        private val fixedPlace: Place? = null
    ) : DriveCastRepository {

        val started = CopyOnWriteArrayList<String>()
        val completed = CopyOnWriteArrayList<String>()
        val cancelled = CopyOnWriteArrayList<String>()

        override suspend fun geocode(query: String): List<Place> {
            started += query
            try {
                delay(delaysByQuery[query] ?: defaultDelayMs)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                cancelled += query
                throw cancellation
            }
            if (query in failingQueries) throw DriveCastException("boom")
            completed += query
            fixedPlace?.let { return listOf(it) }
            return listOf(
                Place(
                    name = query,
                    admin = "Berlin",
                    country = "Germany",
                    location = LatLng(52.52, 13.40)
                )
            )
        }

        override suspend fun fetchRoute(origin: LatLng, destination: LatLng): Route =
            Route(
                geometry = listOf(LatLng(52.0, 13.0), LatLng(53.0, 10.0)),
                durationSeconds = 3600.0,
                distanceMeters = 100_000.0
            )

        override suspend fun fetchWeather(location: LatLng, atEpochSeconds: Long, unitSystem: UnitSystem): Weather =
            throw UnsupportedOperationException("not exercised by these tests")


        override suspend fun fetchAlerts(location: LatLng): List<WeatherAlert> = emptyList()


        override suspend fun reverseGeocode(location: LatLng): ResolvedPlace {
            if (reverseGeocodeFails) throw DriveCastException("reverse geocoder down")
            return reverseGeocode
        }
    }
}
