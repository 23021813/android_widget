package com.carlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.carlauncher.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "car_launcher_settings")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val FRAME1_APP = stringPreferencesKey("frame1_app")
        val FRAME2_APP = stringPreferencesKey("frame2_app")
        val ASSISTANT_APP = stringPreferencesKey("assistant_app")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val SHOW_STATUS_WIDGET = booleanPreferencesKey("show_status_widget")
        val SHOW_ASSISTANT_WIDGET = booleanPreferencesKey("show_assistant_widget")

        // Independent sizing
        val STATUS_WIDGET_SCALE = floatPreferencesKey("status_widget_scale")
        val ASSISTANT_BUTTON_SCALE = floatPreferencesKey("assistant_button_scale")
        val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

        // Widget position
        val STATUS_WIDGET_X = intPreferencesKey("status_widget_x")
        val STATUS_WIDGET_Y = intPreferencesKey("status_widget_y")
        val ASSISTANT_WIDGET_X = intPreferencesKey("assistant_widget_x")
        val ASSISTANT_WIDGET_Y = intPreferencesKey("assistant_widget_y")

        // System bar overlap
        val ALLOW_OVERLAP_SYSTEM_BARS = booleanPreferencesKey("allow_overlap_system_bars")

        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val SHOW_WIFI = booleanPreferencesKey("show_wifi")
        val SHOW_BLUETOOTH = booleanPreferencesKey("show_bluetooth")
        val SHOW_GPS = booleanPreferencesKey("show_gps")

        // Click-through
        val CLOCK_CLICK_THROUGH = booleanPreferencesKey("clock_click_through")

        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val WEATHER_LOCATION_MODE = stringPreferencesKey("weather_location_mode")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        val APP_LANGUAGE = stringPreferencesKey("app_language")

        // Assistant button
        val ASSISTANT_ICON = stringPreferencesKey("assistant_icon")
        val ASSISTANT_LONG_PRESS_APP = stringPreferencesKey("assistant_long_press_app")
        val ASSISTANT_DOUBLE_TAP_APP = stringPreferencesKey("assistant_double_tap_app")

        // Boot split
        val AUTO_SPLIT_ON_BOOT = booleanPreferencesKey("auto_split_on_boot")

        // Schedule Automation
        val SCHEDULE_PROFILES = stringPreferencesKey("schedule_profiles")
    }

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            frame1App = prefs[Keys.FRAME1_APP],
            frame2App = prefs[Keys.FRAME2_APP],
            assistantApp = prefs[Keys.ASSISTANT_APP],
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            showStatusWidget = prefs[Keys.SHOW_STATUS_WIDGET] ?: true,
            showAssistantWidget = prefs[Keys.SHOW_ASSISTANT_WIDGET] ?: true,

            statusWidgetScale = prefs[Keys.STATUS_WIDGET_SCALE] ?: 1.0f,
            assistantButtonScale = prefs[Keys.ASSISTANT_BUTTON_SCALE] ?: 1.0f,
            widgetOpacity = prefs[Keys.WIDGET_OPACITY] ?: 0.85f,

            statusWidgetX = prefs[Keys.STATUS_WIDGET_X] ?: Int.MIN_VALUE,
            statusWidgetY = prefs[Keys.STATUS_WIDGET_Y] ?: Int.MIN_VALUE,
            assistantWidgetX = prefs[Keys.ASSISTANT_WIDGET_X] ?: Int.MIN_VALUE,
            assistantWidgetY = prefs[Keys.ASSISTANT_WIDGET_Y] ?: Int.MIN_VALUE,

            allowOverlapSystemBars = prefs[Keys.ALLOW_OVERLAP_SYSTEM_BARS] ?: false,

            clockFormat = prefs[Keys.CLOCK_FORMAT]?.let {
                try { ClockFormat.valueOf(it) } catch (e: Exception) { ClockFormat.TIME_ONLY }
            } ?: ClockFormat.TIME_ONLY,
            showWifi = prefs[Keys.SHOW_WIFI] ?: true,
            showBluetooth = prefs[Keys.SHOW_BLUETOOTH] ?: true,
            showGps = prefs[Keys.SHOW_GPS] ?: true,

            clockClickThrough = prefs[Keys.CLOCK_CLICK_THROUGH] ?: false,

            showWeather = prefs[Keys.SHOW_WEATHER] ?: true,
            weatherLocationMode = prefs[Keys.WEATHER_LOCATION_MODE]?.let {
                try { WeatherLocationMode.valueOf(it) } catch (e: Exception) { WeatherLocationMode.GPS }
            } ?: WeatherLocationMode.GPS,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "Hanoi",
            temperatureUnit = prefs[Keys.TEMPERATURE_UNIT]?.let {
                try { TemperatureUnit.valueOf(it) } catch (e: Exception) { TemperatureUnit.CELSIUS }
            } ?: TemperatureUnit.CELSIUS,
            weatherApiKey = prefs[Keys.WEATHER_API_KEY] ?: "",
            appLanguage = prefs[Keys.APP_LANGUAGE]?.let {
                try { AppLanguage.valueOf(it) } catch (e: Exception) { AppLanguage.SYSTEM }
            } ?: AppLanguage.SYSTEM,

            assistantIcon = prefs[Keys.ASSISTANT_ICON]?.let {
                try { AssistantIcon.valueOf(it) } catch (e: Exception) { AssistantIcon.MIC }
            } ?: AssistantIcon.MIC,
            assistantLongPressApp = prefs[Keys.ASSISTANT_LONG_PRESS_APP],
            assistantDoubleTapApp = prefs[Keys.ASSISTANT_DOUBLE_TAP_APP],

            autoSplitOnBoot = prefs[Keys.AUTO_SPLIT_ON_BOOT] ?: true,

            scheduleProfiles = prefs[Keys.SCHEDULE_PROFILES]?.let { parseScheduleProfiles(it) } ?: emptyList()
        )
    }

    private fun parseScheduleProfiles(jsonString: String): List<ScheduleProfile> {
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

    suspend fun updateSettings(settings: LauncherSettings) {
        context.dataStore.edit { prefs ->
            if (settings.frame1App != null) prefs[Keys.FRAME1_APP] = settings.frame1App
            else prefs.remove(Keys.FRAME1_APP)
            if (settings.frame2App != null) prefs[Keys.FRAME2_APP] = settings.frame2App
            else prefs.remove(Keys.FRAME2_APP)
            if (settings.assistantApp != null) prefs[Keys.ASSISTANT_APP] = settings.assistantApp
            else prefs.remove(Keys.ASSISTANT_APP)
            if (settings.assistantLongPressApp != null) prefs[Keys.ASSISTANT_LONG_PRESS_APP] = settings.assistantLongPressApp
            else prefs.remove(Keys.ASSISTANT_LONG_PRESS_APP)
            if (settings.assistantDoubleTapApp != null) prefs[Keys.ASSISTANT_DOUBLE_TAP_APP] = settings.assistantDoubleTapApp
            else prefs.remove(Keys.ASSISTANT_DOUBLE_TAP_APP)

            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.SHOW_STATUS_WIDGET] = settings.showStatusWidget
            prefs[Keys.SHOW_ASSISTANT_WIDGET] = settings.showAssistantWidget

            prefs[Keys.STATUS_WIDGET_SCALE] = settings.statusWidgetScale
            prefs[Keys.ASSISTANT_BUTTON_SCALE] = settings.assistantButtonScale
            prefs[Keys.WIDGET_OPACITY] = settings.widgetOpacity

            prefs[Keys.STATUS_WIDGET_X] = settings.statusWidgetX
            prefs[Keys.STATUS_WIDGET_Y] = settings.statusWidgetY
            prefs[Keys.ASSISTANT_WIDGET_X] = settings.assistantWidgetX
            prefs[Keys.ASSISTANT_WIDGET_Y] = settings.assistantWidgetY

            prefs[Keys.ALLOW_OVERLAP_SYSTEM_BARS] = settings.allowOverlapSystemBars

            prefs[Keys.CLOCK_FORMAT] = settings.clockFormat.name
            prefs[Keys.SHOW_WIFI] = settings.showWifi
            prefs[Keys.SHOW_BLUETOOTH] = settings.showBluetooth
            prefs[Keys.SHOW_GPS] = settings.showGps

            prefs[Keys.CLOCK_CLICK_THROUGH] = settings.clockClickThrough

            prefs[Keys.SHOW_WEATHER] = settings.showWeather
            prefs[Keys.WEATHER_LOCATION_MODE] = settings.weatherLocationMode.name
            prefs[Keys.WEATHER_CITY] = settings.weatherCity
            prefs[Keys.TEMPERATURE_UNIT] = settings.temperatureUnit.name
            prefs[Keys.WEATHER_API_KEY] = settings.weatherApiKey
            prefs[Keys.APP_LANGUAGE] = settings.appLanguage.name

            prefs[Keys.ASSISTANT_ICON] = settings.assistantIcon.name
            prefs[Keys.AUTO_SPLIT_ON_BOOT] = settings.autoSplitOnBoot

            // Schedule
            val jsonArray = org.json.JSONArray()
            for (profile in settings.scheduleProfiles) {
                val obj = org.json.JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("enabled", profile.enabled)
                    put("startHour", profile.startHour)
                    put("startMinute", profile.startMinute)
                    put("endHour", profile.endHour)
                    put("endMinute", profile.endMinute)
                    put("lastTriggeredDayOfYear", profile.lastTriggeredDayOfYear)
                    val daysArray = org.json.JSONArray()
                    for (day in profile.days) daysArray.put(day)
                    put("days", daysArray)
                    put("autoNavigate", profile.autoNavigate)
                    put("navAddress", profile.navAddress)
                    put("autoMusic", profile.autoMusic)
                    put("musicKeyword", profile.musicKeyword)
                }
                jsonArray.put(obj)
            }
            prefs[Keys.SCHEDULE_PROFILES] = jsonArray.toString()
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
