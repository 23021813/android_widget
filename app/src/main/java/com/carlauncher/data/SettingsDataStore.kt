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
        val WIDGET_SCALE = floatPreferencesKey("widget_scale")
        val WIDGET_OPACITY = floatPreferencesKey("widget_opacity")

        val CLOCK_FORMAT = stringPreferencesKey("clock_format")
        val SHOW_WIFI = booleanPreferencesKey("show_wifi")
        val SHOW_BLUETOOTH = booleanPreferencesKey("show_bluetooth")
        val SHOW_GPS = booleanPreferencesKey("show_gps")

        val SHOW_WEATHER = booleanPreferencesKey("show_weather")
        val WEATHER_LOCATION_MODE = stringPreferencesKey("weather_location_mode")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
    }

    val settingsFlow: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            frame1App = prefs[Keys.FRAME1_APP],
            frame2App = prefs[Keys.FRAME2_APP],
            assistantApp = prefs[Keys.ASSISTANT_APP],
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT] ?: false,
            showStatusWidget = prefs[Keys.SHOW_STATUS_WIDGET] ?: true,
            showAssistantWidget = prefs[Keys.SHOW_ASSISTANT_WIDGET] ?: true,
            widgetScale = prefs[Keys.WIDGET_SCALE] ?: 1.0f,
            widgetOpacity = prefs[Keys.WIDGET_OPACITY] ?: 0.85f,

            clockFormat = prefs[Keys.CLOCK_FORMAT]?.let {
                try { ClockFormat.valueOf(it) } catch (e: Exception) { ClockFormat.TIME_ONLY }
            } ?: ClockFormat.TIME_ONLY,
            showWifi = prefs[Keys.SHOW_WIFI] ?: true,
            showBluetooth = prefs[Keys.SHOW_BLUETOOTH] ?: true,
            showGps = prefs[Keys.SHOW_GPS] ?: true,

            showWeather = prefs[Keys.SHOW_WEATHER] ?: true,
            weatherLocationMode = prefs[Keys.WEATHER_LOCATION_MODE]?.let {
                try { WeatherLocationMode.valueOf(it) } catch (e: Exception) { WeatherLocationMode.GPS }
            } ?: WeatherLocationMode.GPS,
            weatherCity = prefs[Keys.WEATHER_CITY] ?: "Hanoi",
            temperatureUnit = prefs[Keys.TEMPERATURE_UNIT]?.let {
                try { TemperatureUnit.valueOf(it) } catch (e: Exception) { TemperatureUnit.CELSIUS }
            } ?: TemperatureUnit.CELSIUS,
            weatherApiKey = prefs[Keys.WEATHER_API_KEY] ?: ""
        )
    }

    suspend fun updateSettings(settings: LauncherSettings) {
        context.dataStore.edit { prefs ->
            if (settings.frame1App != null) prefs[Keys.FRAME1_APP] = settings.frame1App
            else prefs.remove(Keys.FRAME1_APP)
            if (settings.frame2App != null) prefs[Keys.FRAME2_APP] = settings.frame2App
            else prefs.remove(Keys.FRAME2_APP)
            if (settings.assistantApp != null) prefs[Keys.ASSISTANT_APP] = settings.assistantApp
            else prefs.remove(Keys.ASSISTANT_APP)
            
            prefs[Keys.AUTO_START_ON_BOOT] = settings.autoStartOnBoot
            prefs[Keys.SHOW_STATUS_WIDGET] = settings.showStatusWidget
            prefs[Keys.SHOW_ASSISTANT_WIDGET] = settings.showAssistantWidget
            prefs[Keys.WIDGET_SCALE] = settings.widgetScale
            prefs[Keys.WIDGET_OPACITY] = settings.widgetOpacity

            prefs[Keys.CLOCK_FORMAT] = settings.clockFormat.name
            prefs[Keys.SHOW_WIFI] = settings.showWifi
            prefs[Keys.SHOW_BLUETOOTH] = settings.showBluetooth
            prefs[Keys.SHOW_GPS] = settings.showGps

            prefs[Keys.SHOW_WEATHER] = settings.showWeather
            prefs[Keys.WEATHER_LOCATION_MODE] = settings.weatherLocationMode.name
            prefs[Keys.WEATHER_CITY] = settings.weatherCity
            prefs[Keys.TEMPERATURE_UNIT] = settings.temperatureUnit.name
            prefs[Keys.WEATHER_API_KEY] = settings.weatherApiKey
        }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { it.clear() }
    }
}
