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

/** Special package names for virtual actions (used in App Picker) */
object VirtualActions {
    const val ACTION_HOME = "com.carlauncher.ACTION_HOME"
    const val ACTION_VOICE_COMMAND = "com.carlauncher.ACTION_VOICE_COMMAND"
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
    val autoSplitOnBoot: Boolean = true,

    // Schedule Automation
    val scheduleEnabled: Boolean = false,
    val scheduleDays: Set<Int> = setOf(2, 3, 4, 5, 6), // Calendar.MONDAY(2)..FRIDAY(6)
    val scheduleHour: Int = 7,
    val scheduleMinute: Int = 30,
    val scheduleAutoNavigate: Boolean = false,
    val scheduleNavigationAddress: String = "",
    val scheduleAutoMusic: Boolean = false,
    val scheduleMusicKeyword: String = ""
)
