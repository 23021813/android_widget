package com.carlauncher.data.models

enum class ClockFormat(val pattern: String, val label: String) {
    TIME_ONLY("HH:mm", "Chỉ giờ"),
    DATE_TIME("dd/MM HH:mm", "Ngày & Giờ"),
    FULL("EEE, dd MMM HH:mm", "Đầy đủ")
}

enum class WeatherLocationMode(val label: String) {
    GPS("GPS tự động"),
    MANUAL("Thủ công")
}

enum class TemperatureUnit(val label: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F")
}

data class LauncherSettings(
    // App Frames
    val frame1App: String? = null,
    val frame2App: String? = null,
    val assistantApp: String? = null,
    val autoStartOnBoot: Boolean = false,
    
    // Widget Appearance
    val widgetScale: Float = 1.0f,
    val widgetOpacity: Float = 0.85f,

    // Clock & Overlay
    val clockFormat: ClockFormat = ClockFormat.TIME_ONLY,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showGps: Boolean = true,

    // Weather
    val showWeather: Boolean = true,
    val weatherLocationMode: WeatherLocationMode = WeatherLocationMode.GPS,
    val weatherCity: String = "Hanoi",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val weatherApiKey: String = ""
)
