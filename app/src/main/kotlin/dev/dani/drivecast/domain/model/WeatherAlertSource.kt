package dev.dani.drivecast.domain.model

enum class WeatherAlertSource {
    NWS, // warning from the US National Weather Service.
    DERIVED // Inferred from the WMO weather code in the forecast
}