package com.carlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.carlauncher.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "car_launcher_settings")

internal object SettingsKeys {
    val FRAME1_APP = stringPreferencesKey("frame1_app")
    val FRAME2_APP = stringPreferencesKey("frame2_app")
    val ASSISTANT_APP = stringPreferencesKey("assistant_app")
    val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    val SHOW_STATUS_WIDGET = booleanPreferencesKey("show_status_widget")
    val SHOW_ASSISTANT_WIDGET = booleanPreferencesKey("show_assistant_widget")

    val STATUS_WIDGET_SCALE = floatPreferencesKey("status_widget_scale")
    val ASSISTANT_BUTTON_SCALE = floatPreferencesKey("assistant_button_scale")
    val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

    val STATUS_WIDGET_X = intPreferencesKey("status_widget_x")
    val STATUS_WIDGET_Y = intPreferencesKey("status_widget_y")
    val ASSISTANT_WIDGET_X = intPreferencesKey("assistant_widget_x")
    val ASSISTANT_WIDGET_Y = intPreferencesKey("assistant_widget_y")

    val ALLOW_OVERLAP_SYSTEM_BARS = booleanPreferencesKey("allow_overlap_system_bars")

    val CLOCK_FORMAT = stringPreferencesKey("clock_format")
    val SHOW_WIFI = booleanPreferencesKey("show_wifi")
    val SHOW_BLUETOOTH = booleanPreferencesKey("show_bluetooth")
    val SHOW_GPS = booleanPreferencesKey("show_gps")

    val CLOCK_CLICK_THROUGH = booleanPreferencesKey("clock_click_through")

    val SHOW_WEATHER = booleanPreferencesKey("show_weather")
    val WEATHER_LOCATION_MODE = stringPreferencesKey("weather_location_mode")
    val WEATHER_CITY = stringPreferencesKey("weather_city")
    val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
    val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
    val APP_LANGUAGE = stringPreferencesKey("app_language")

    val ASSISTANT_ICON = stringPreferencesKey("assistant_icon")
    val ASSISTANT_LONG_PRESS_APP = stringPreferencesKey("assistant_long_press_app")
    val ASSISTANT_DOUBLE_TAP_APP = stringPreferencesKey("assistant_double_tap_app")

    val AUTO_SPLIT_ON_BOOT = booleanPreferencesKey("auto_split_on_boot")
    val SCHEDULE_PROFILES = stringPreferencesKey("schedule_profiles")

    val BRIDGE_ENABLED = booleanPreferencesKey("bridge_enabled")
    val BRIDGE_LAST_DEVICE_NAME = stringPreferencesKey("bridge_last_device_name")
    val BRIDGE_LAST_DEVICE_ADDRESS = stringPreferencesKey("bridge_last_device_address")
    val BRIDGE_LIGHT_THEME = booleanPreferencesKey("bridge_light_theme")
    val BRIDGE_BRIGHTNESS = intPreferencesKey("bridge_brightness")
    val BRIDGE_SPEED_WARNING_LIMIT = intPreferencesKey("bridge_speed_warning_limit")
    val BRIDGE_SPEED_SIGN_CAPTURE_ENABLED = booleanPreferencesKey("bridge_speed_sign_capture_enabled")
    val BRIDGE_SPEED_SIGN_CAPTURE_INTERVAL_SEC = intPreferencesKey("bridge_speed_sign_capture_interval_sec")
    val BRIDGE_SPEED_SIGN_ROI_X = intPreferencesKey("bridge_speed_sign_roi_x")
    val BRIDGE_SPEED_SIGN_ROI_Y = intPreferencesKey("bridge_speed_sign_roi_y")
    val BRIDGE_SPEED_SIGN_ROI_WIDTH = intPreferencesKey("bridge_speed_sign_roi_width")
    val BRIDGE_SPEED_SIGN_ROI_HEIGHT = intPreferencesKey("bridge_speed_sign_roi_height")
}

internal object SettingsPreferencesMapper {
    fun read(prefs: Preferences): LauncherSettings {
        return LauncherSettings(
            frame1App = prefs[SettingsKeys.FRAME1_APP],
            frame2App = prefs[SettingsKeys.FRAME2_APP],
            assistantApp = prefs[SettingsKeys.ASSISTANT_APP],
            autoStartOnBoot = prefs[SettingsKeys.AUTO_START_ON_BOOT] ?: false,
            showStatusWidget = prefs[SettingsKeys.SHOW_STATUS_WIDGET] ?: true,
            showAssistantWidget = prefs[SettingsKeys.SHOW_ASSISTANT_WIDGET] ?: true,

            statusWidgetScale = prefs[SettingsKeys.STATUS_WIDGET_SCALE] ?: 1.0f,
            assistantButtonScale = prefs[SettingsKeys.ASSISTANT_BUTTON_SCALE] ?: 1.0f,
            widgetOpacity = prefs[SettingsKeys.WIDGET_OPACITY] ?: 0.85f,

            statusWidgetX = prefs[SettingsKeys.STATUS_WIDGET_X] ?: Int.MIN_VALUE,
            statusWidgetY = prefs[SettingsKeys.STATUS_WIDGET_Y] ?: Int.MIN_VALUE,
            assistantWidgetX = prefs[SettingsKeys.ASSISTANT_WIDGET_X] ?: Int.MIN_VALUE,
            assistantWidgetY = prefs[SettingsKeys.ASSISTANT_WIDGET_Y] ?: Int.MIN_VALUE,

            allowOverlapSystemBars = prefs[SettingsKeys.ALLOW_OVERLAP_SYSTEM_BARS] ?: false,

            clockFormat = prefs[SettingsKeys.CLOCK_FORMAT]?.let {
                try { ClockFormat.valueOf(it) } catch (_: Exception) { ClockFormat.TIME_ONLY }
            } ?: ClockFormat.TIME_ONLY,
            showWifi = prefs[SettingsKeys.SHOW_WIFI] ?: true,
            showBluetooth = prefs[SettingsKeys.SHOW_BLUETOOTH] ?: true,
            showGps = prefs[SettingsKeys.SHOW_GPS] ?: true,

            clockClickThrough = prefs[SettingsKeys.CLOCK_CLICK_THROUGH] ?: false,

            showWeather = prefs[SettingsKeys.SHOW_WEATHER] ?: true,
            weatherLocationMode = prefs[SettingsKeys.WEATHER_LOCATION_MODE]?.let {
                try { WeatherLocationMode.valueOf(it) } catch (_: Exception) { WeatherLocationMode.GPS }
            } ?: WeatherLocationMode.GPS,
            weatherCity = prefs[SettingsKeys.WEATHER_CITY] ?: "Hanoi",
            temperatureUnit = prefs[SettingsKeys.TEMPERATURE_UNIT]?.let {
                try { TemperatureUnit.valueOf(it) } catch (_: Exception) { TemperatureUnit.CELSIUS }
            } ?: TemperatureUnit.CELSIUS,
            weatherApiKey = prefs[SettingsKeys.WEATHER_API_KEY] ?: "",
            appLanguage = prefs[SettingsKeys.APP_LANGUAGE]?.let {
                try { AppLanguage.valueOf(it) } catch (_: Exception) { AppLanguage.SYSTEM }
            } ?: AppLanguage.SYSTEM,

            assistantIcon = prefs[SettingsKeys.ASSISTANT_ICON]?.let {
                try { AssistantIcon.valueOf(it) } catch (_: Exception) { AssistantIcon.MIC }
            } ?: AssistantIcon.MIC,
            assistantLongPressApp = prefs[SettingsKeys.ASSISTANT_LONG_PRESS_APP],
            assistantDoubleTapApp = prefs[SettingsKeys.ASSISTANT_DOUBLE_TAP_APP],

            autoSplitOnBoot = prefs[SettingsKeys.AUTO_SPLIT_ON_BOOT] ?: true,

            scheduleProfiles = prefs[SettingsKeys.SCHEDULE_PROFILES]?.let {
                parseScheduleProfiles(it)
            } ?: emptyList(),

            navigationBridge = NavigationBridgeSettings(
                enabled = prefs[SettingsKeys.BRIDGE_ENABLED] ?: false,
                lastDeviceName = prefs[SettingsKeys.BRIDGE_LAST_DEVICE_NAME],
                lastDeviceAddress = prefs[SettingsKeys.BRIDGE_LAST_DEVICE_ADDRESS],
                displayLightTheme = prefs[SettingsKeys.BRIDGE_LIGHT_THEME] ?: true,
                displayBrightness = prefs[SettingsKeys.BRIDGE_BRIGHTNESS] ?: 50,
                speedWarningLimit = prefs[SettingsKeys.BRIDGE_SPEED_WARNING_LIMIT] ?: 50,
                speedSignCapture = SpeedSignCaptureSettings(
                    enabled = prefs[SettingsKeys.BRIDGE_SPEED_SIGN_CAPTURE_ENABLED] ?: false,
                    intervalSeconds = (prefs[SettingsKeys.BRIDGE_SPEED_SIGN_CAPTURE_INTERVAL_SEC] ?: 5)
                        .coerceIn(2, 30),
                    roiX = prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_X] ?: 120,
                    roiY = prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_Y] ?: 120,
                    roiWidth = prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_WIDTH] ?: 420,
                    roiHeight = prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_HEIGHT] ?: 320
                )
            )
        )
    }

    fun write(prefs: MutablePreferences, settings: LauncherSettings) {
        if (settings.frame1App != null) prefs[SettingsKeys.FRAME1_APP] = settings.frame1App
        else prefs.remove(SettingsKeys.FRAME1_APP)
        if (settings.frame2App != null) prefs[SettingsKeys.FRAME2_APP] = settings.frame2App
        else prefs.remove(SettingsKeys.FRAME2_APP)
        if (settings.assistantApp != null) prefs[SettingsKeys.ASSISTANT_APP] = settings.assistantApp
        else prefs.remove(SettingsKeys.ASSISTANT_APP)
        if (settings.assistantLongPressApp != null) {
            prefs[SettingsKeys.ASSISTANT_LONG_PRESS_APP] = settings.assistantLongPressApp
        } else prefs.remove(SettingsKeys.ASSISTANT_LONG_PRESS_APP)
        if (settings.assistantDoubleTapApp != null) {
            prefs[SettingsKeys.ASSISTANT_DOUBLE_TAP_APP] = settings.assistantDoubleTapApp
        } else prefs.remove(SettingsKeys.ASSISTANT_DOUBLE_TAP_APP)

        prefs[SettingsKeys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
        prefs[SettingsKeys.SHOW_STATUS_WIDGET] = settings.showStatusWidget
        prefs[SettingsKeys.SHOW_ASSISTANT_WIDGET] = settings.showAssistantWidget

        prefs[SettingsKeys.STATUS_WIDGET_SCALE] = settings.statusWidgetScale
        prefs[SettingsKeys.ASSISTANT_BUTTON_SCALE] = settings.assistantButtonScale
        prefs[SettingsKeys.WIDGET_OPACITY] = settings.widgetOpacity

        prefs[SettingsKeys.STATUS_WIDGET_X] = settings.statusWidgetX
        prefs[SettingsKeys.STATUS_WIDGET_Y] = settings.statusWidgetY
        prefs[SettingsKeys.ASSISTANT_WIDGET_X] = settings.assistantWidgetX
        prefs[SettingsKeys.ASSISTANT_WIDGET_Y] = settings.assistantWidgetY

        prefs[SettingsKeys.ALLOW_OVERLAP_SYSTEM_BARS] = settings.allowOverlapSystemBars

        prefs[SettingsKeys.CLOCK_FORMAT] = settings.clockFormat.name
        prefs[SettingsKeys.SHOW_WIFI] = settings.showWifi
        prefs[SettingsKeys.SHOW_BLUETOOTH] = settings.showBluetooth
        prefs[SettingsKeys.SHOW_GPS] = settings.showGps

        prefs[SettingsKeys.CLOCK_CLICK_THROUGH] = settings.clockClickThrough

        prefs[SettingsKeys.SHOW_WEATHER] = settings.showWeather
        prefs[SettingsKeys.WEATHER_LOCATION_MODE] = settings.weatherLocationMode.name
        prefs[SettingsKeys.WEATHER_CITY] = settings.weatherCity
        prefs[SettingsKeys.TEMPERATURE_UNIT] = settings.temperatureUnit.name
        prefs[SettingsKeys.WEATHER_API_KEY] = settings.weatherApiKey
        prefs[SettingsKeys.APP_LANGUAGE] = settings.appLanguage.name

        prefs[SettingsKeys.ASSISTANT_ICON] = settings.assistantIcon.name
        prefs[SettingsKeys.AUTO_SPLIT_ON_BOOT] = settings.autoSplitOnBoot
        prefs[SettingsKeys.SCHEDULE_PROFILES] = serializeScheduleProfiles(settings.scheduleProfiles)

        prefs[SettingsKeys.BRIDGE_ENABLED] = settings.navigationBridge.enabled
        prefs[SettingsKeys.BRIDGE_LIGHT_THEME] = settings.navigationBridge.displayLightTheme
        prefs[SettingsKeys.BRIDGE_BRIGHTNESS] = settings.navigationBridge.displayBrightness
        prefs[SettingsKeys.BRIDGE_SPEED_WARNING_LIMIT] = settings.navigationBridge.speedWarningLimit
        prefs[SettingsKeys.BRIDGE_SPEED_SIGN_CAPTURE_ENABLED] =
            settings.navigationBridge.speedSignCapture.enabled
        prefs[SettingsKeys.BRIDGE_SPEED_SIGN_CAPTURE_INTERVAL_SEC] =
            settings.navigationBridge.speedSignCapture.intervalSeconds.coerceIn(2, 30)
        prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_X] = settings.navigationBridge.speedSignCapture.roiX
        prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_Y] = settings.navigationBridge.speedSignCapture.roiY
        prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_WIDTH] =
            settings.navigationBridge.speedSignCapture.roiWidth.coerceAtLeast(80)
        prefs[SettingsKeys.BRIDGE_SPEED_SIGN_ROI_HEIGHT] =
            settings.navigationBridge.speedSignCapture.roiHeight.coerceAtLeast(80)

        if (settings.navigationBridge.lastDeviceName != null) {
            prefs[SettingsKeys.BRIDGE_LAST_DEVICE_NAME] = settings.navigationBridge.lastDeviceName
        } else prefs.remove(SettingsKeys.BRIDGE_LAST_DEVICE_NAME)

        if (settings.navigationBridge.lastDeviceAddress != null) {
            prefs[SettingsKeys.BRIDGE_LAST_DEVICE_ADDRESS] = settings.navigationBridge.lastDeviceAddress
        } else prefs.remove(SettingsKeys.BRIDGE_LAST_DEVICE_ADDRESS)
    }

    internal fun parseScheduleProfiles(jsonString: String): List<ScheduleProfile> {
        val list = mutableListOf<ScheduleProfile>()
        try {
            val array = org.json.JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val daysArray = obj.optJSONArray("days")
                val daysSet = mutableSetOf<Int>()
                if (daysArray != null) {
                    for (j in 0 until daysArray.length()) {
                        daysSet.add(daysArray.getInt(j))
                    }
                }
                list.add(
                    ScheduleProfile(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        enabled = obj.optBoolean("enabled", true),
                        startHour = obj.optInt("startHour", 7),
                        startMinute = obj.optInt("startMinute", 0),
                        endHour = obj.optInt("endHour", 8),
                        endMinute = obj.optInt("endMinute", 0),
                        lastTriggeredDayOfYear = obj.optInt("lastTriggeredDayOfYear", -1),
                        days = daysSet,
                        autoNavigate = obj.optBoolean("autoNavigate", false),
                        navAddress = obj.optString("navAddress", ""),
                        autoMusic = obj.optBoolean("autoMusic", false),
                        musicKeyword = obj.optString("musicKeyword", "")
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsDataStore", "Failed to parse schedule profiles", e)
        }
        return list
    }

    internal fun serializeScheduleProfiles(profiles: List<ScheduleProfile>): String {
        fun escapeJson(value: String): String {
            return buildString {
                value.forEach { ch ->
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
            }
        }

        return profiles.joinToString(prefix = "[", postfix = "]") { profile ->
            val days = profile.days.joinToString(prefix = "[", postfix = "]")
            "{" +
                "\"id\":\"${escapeJson(profile.id)}\"," +
                "\"name\":\"${escapeJson(profile.name)}\"," +
                "\"enabled\":${profile.enabled}," +
                "\"startHour\":${profile.startHour}," +
                "\"startMinute\":${profile.startMinute}," +
                "\"endHour\":${profile.endHour}," +
                "\"endMinute\":${profile.endMinute}," +
                "\"lastTriggeredDayOfYear\":${profile.lastTriggeredDayOfYear}," +
                "\"days\":$days," +
                "\"autoNavigate\":${profile.autoNavigate}," +
                "\"navAddress\":\"${escapeJson(profile.navAddress)}\"," +
                "\"autoMusic\":${profile.autoMusic}," +
                "\"musicKeyword\":\"${escapeJson(profile.musicKeyword)}\"" +
                "}"
        }
    }
}

class SettingsDataStore(private val context: Context) {

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data.map(SettingsPreferencesMapper::read)

    suspend fun updateSettings(settings: LauncherSettings) {
        context.dataStore.edit { prefs -> SettingsPreferencesMapper.write(prefs, settings) }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
