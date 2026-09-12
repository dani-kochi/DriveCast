# DriveCast 🚗🌤️

> Real-Time Route Weather Forecasting powered by Asynchronous Structured Concurrency.

DriveCast calculates weather conditions along a driving route based on the estimated time of
arrival (ETA) at each leg of the journey. Instead of static destination weather, it samples
waypoints along the travel path, predicts when the user reaches each point, and fetches
hyper-local forecasts concurrently.

## Key Features & Coroutine Capabilities

- **Parallel Waypoint Fetching** — structured concurrency (`coroutineScope`, `async`/`awaitAll`)
  queries weather *and* the reverse-geocoded place name for every sampled waypoint simultaneously.
- **Controlled Concurrency & Rate Limiting** — a `kotlinx.coroutines.sync.Semaphore` throttles the
  fan-out to 4 in-flight requests so we stay polite to the free public APIs and never blow out the
  OkHttp socket pool.
- **Reactive Stream UI State** — `StateFlow` for form + result state and `SharedFlow` for one-shot
  events, combined with `flatMapLatest` and `debounce` so the forecast updates as the user edits
  the departure time or the text inputs.
- **Graceful Error Handling** — `CoroutineExceptionHandler` + `SupervisorJob` in the view model,
  and per-waypoint failures modelled as `WaypointForecast.Failure` so one bad network call degrades
  a single row instead of killing the whole route pipeline. The two calls behind a waypoint degrade
  independently: a failed reverse geocode still shows weather (labelled with coordinates), and a
  failed forecast still shows the resolved place name.

## Architecture & Tech Stack

- Kotlin, Kotlin Coroutines & Flow
- Clean Architecture + MVVM
- Networking: Retrofit + OkHttp + **kotlinx.serialization**
  - [OSRM](https://router.project-osrm.org) for route calculation & polyline geometry
  - [Open-Meteo](https://api.open-meteo.com) for hourly time-series weather, requested directly in
    US customary units
  - [Open-Meteo Geocoding](https://geocoding-api.open-meteo.com) to resolve place names to lat/lon
  - [BigDataCloud](https://api-bdc.net) for keyless *reverse* geocoding — naming each waypoint.
    Open-Meteo's geocoder is forward-only, and Nominatim's ~1 req/sec policy would serialise the
    fan-out, whereas this endpoint absorbs the concurrent burst.
- Location: Google Play Services **fused provider** for the current-position fix, with a pure-AOSP
  `LocationManagerCompat` fallback so the app still works without Play Services
- UI: Jetpack Compose (Material 3), single Activity, `androidx.navigation:navigation-compose`
- Typography: **Lato**, bundled as app resources across the full Material 3 type scale
- Dependency injection: **Hilt** (KSP)
- Build: AGP 9 with **built-in Kotlin** (no separate `kotlin-android` plugin), Gradle configuration cache on

No API keys are required — every endpoint used is free and public.
