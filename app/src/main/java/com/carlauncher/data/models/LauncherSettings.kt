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
    VIETNAMESE("vi", "Tiếng Việt")
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
    const val ACTION_SPLIT_VIEW = "com.carlauncher.ACTION_SPLIT_VIEW"
}

data class ScheduleProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val startHour: Int = 7,
    val startMinute: Int = 0,
    val endHour: Int = 8,
    val endMinute: Int = 0,
    val lastTriggeredDayOfYear: Int = -1,
    val days: Set<Int> = emptySet(), // Calendar.MONDAY(2) .. MONDAY(6) etc.
    val autoNavigate: Boolean = false,
    val navAddress: String = "",
    val autoMusic: Boolean = false,
    val musicKeyword: String = ""
)

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

    // Assistant button
    val assistantIcon: AssistantIcon = AssistantIcon.MIC,
    val assistantLongPressApp: String? = null,
    val assistantDoubleTapApp: String? = null,

    // Boot Split-View
    val autoSplitOnBoot: Boolean = true,

    // Schedule Automation Profiles
    val scheduleProfiles: List<ScheduleProfile> = emptyList()
)
