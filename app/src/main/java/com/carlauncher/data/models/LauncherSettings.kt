package com.carlauncher.data.models

enum class ClockFormat(val pattern: String, val labelKey: String) {
    TIME_ONLY("HH:mm", "clock_time_only"),
    DATE_TIME("dd/MM HH:mm", "clock_date_time"),
    FULL("EEE, dd MMM HH:mm", "clock_full")
}

enum class WeatherLocationMode(val labelKey: String) {
    GPS("weather_gps"),
    MANUAL("weather_manual")
}

enum class TemperatureUnit(val label: String) {
    CELSIUS("°C"),
    FAHRENHEIT("°F")
}

enum class AppLanguage(val locale: String, val displayName: String) {
    SYSTEM("", "System Default"),
    ENGLISH("en", "English"),
    VIETNAMESE("vi", "Tiếng Việt"),
    CHINESE("zh", "中文"),
    JAPANESE("ja", "日本語"),
    KOREAN("ko", "한국어"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch")
}

enum class AssistantIcon(val displayName: String) {
    MIC("Microphone"),
    HEADSET("Headset"),
    ASSISTANT("AI Assistant"),
    RECORD("Record"),
    VOICE("Voice Over"),
    CHAT("Chat"),
    STAR("Star"),
    HOME("Home"),
    MUSIC("Music"),
    PHONE("Phone")
}

data class LauncherSettings(
    // App Frames
    val frame1App: String? = null,
    val frame2App: String? = null,
    val assistantApp: String? = null,
    val autoStartOnBoot: Boolean = false,

    // Widget Visibility
    val showStatusWidget: Boolean = true,
    val showAssistantWidget: Boolean = true,

    // Widget Appearance — independent sizing
    val statusWidgetScale: Float = 1.0f,
    val assistantButtonScale: Float = 1.0f,
    val widgetOpacity: Float = 0.85f,

    // Widget Position (Int.MIN_VALUE = not set, use default gravity)
    val statusWidgetX: Int = Int.MIN_VALUE,
    val statusWidgetY: Int = Int.MIN_VALUE,
    val assistantWidgetX: Int = Int.MIN_VALUE,
    val assistantWidgetY: Int = Int.MIN_VALUE,

    // System bar overlap
    val allowOverlapSystemBars: Boolean = false,

    // Clock & Overlay
    val clockFormat: ClockFormat = ClockFormat.TIME_ONLY,
    val showWifi: Boolean = true,
    val showBluetooth: Boolean = true,
    val showGps: Boolean = true,

    // Click-through
    val clockClickThrough: Boolean = false,

    // Weather
    val showWeather: Boolean = true,
    val weatherLocationMode: WeatherLocationMode = WeatherLocationMode.GPS,
    val weatherCity: String = "Hanoi",
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val weatherApiKey: String = "",

    // Language
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,

    // Voice button
    val assistantIcon: AssistantIcon = AssistantIcon.MIC,
    val assistantLongPressApp: String? = null,
    val assistantDoubleTapApp: String? = null,

    // Boot Split-View
    val autoSplitOnBoot: Boolean = true
)
