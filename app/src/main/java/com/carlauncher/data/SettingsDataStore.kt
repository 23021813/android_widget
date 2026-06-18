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
    val PRE_SPLIT_APP = stringPreferencesKey("pre_split_app")
    val PRE_SPLIT_DELAY_MS = intPreferencesKey("pre_split_delay_ms")
    val SCHEDULE_PROFILES = stringPreferencesKey("schedule_profiles")

    // Parking Alert
    val PARKING_ENABLED = booleanPreferencesKey("parking_enabled")
    val PARKING_IDLE_MIN = intPreferencesKey("parking_idle_min")
    val PARKING_DISTANCE_M = intPreferencesKey("parking_distance_m")
    val PARKING_COOLDOWN_MIN = intPreferencesKey("parking_cooldown_min")
    val PARKING_SEND_TELEGRAM = booleanPreferencesKey("parking_send_telegram")
    val PARKING_SEND_EMAIL = booleanPreferencesKey("parking_send_email")
    val PARKING_TG_CHAT_ID = stringPreferencesKey("parking_tg_chat_id")
    val PARKING_SMTP_HOST = stringPreferencesKey("parking_smtp_host")
    val PARKING_SMTP_PORT = intPreferencesKey("parking_smtp_port")
    val PARKING_SMTP_USER = stringPreferencesKey("parking_smtp_user")
    val PARKING_SMTP_RECIPIENT = stringPreferencesKey("parking_smtp_recipient")
    val PARKING_LAST_MOVEMENT_TS = longPreferencesKey("parking_last_movement_ts")
    val PARKING_LAST_ALERT_TS = longPreferencesKey("parking_last_alert_ts")
    val PARKING_HAS_TG_TOKEN = booleanPreferencesKey("parking_has_tg_token")
    val PARKING_HAS_SMTP_PASS = booleanPreferencesKey("parking_has_smtp_pass")


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
            preSplitApp = prefs[SettingsKeys.PRE_SPLIT_APP],
            preSplitDelayMs = prefs[SettingsKeys.PRE_SPLIT_DELAY_MS] ?: 1500,

            scheduleProfiles = prefs[SettingsKeys.SCHEDULE_PROFILES]?.let {
                parseScheduleProfiles(it)
            } ?: emptyList(),

            parkingAlert = ParkingAlertConfig(
                enabled = prefs[SettingsKeys.PARKING_ENABLED] ?: false,
                idleMinutes = prefs[SettingsKeys.PARKING_IDLE_MIN] ?: 15,
                distanceMeters = prefs[SettingsKeys.PARKING_DISTANCE_M] ?: 50,
                cooldownMinutes = prefs[SettingsKeys.PARKING_COOLDOWN_MIN] ?: 30,
                sendTelegram = prefs[SettingsKeys.PARKING_SEND_TELEGRAM] ?: true,
                sendEmail = prefs[SettingsKeys.PARKING_SEND_EMAIL] ?: false,
                telegramChatId = prefs[SettingsKeys.PARKING_TG_CHAT_ID] ?: "",
                smtpHost = prefs[SettingsKeys.PARKING_SMTP_HOST] ?: "smtp.gmail.com",
                smtpPort = prefs[SettingsKeys.PARKING_SMTP_PORT] ?: 587,
                smtpUser = prefs[SettingsKeys.PARKING_SMTP_USER] ?: "",
                smtpRecipient = prefs[SettingsKeys.PARKING_SMTP_RECIPIENT] ?: "",
                lastMovementTimestamp = prefs[SettingsKeys.PARKING_LAST_MOVEMENT_TS] ?: 0L,
                lastAlertTimestamp = prefs[SettingsKeys.PARKING_LAST_ALERT_TS] ?: 0L,
                hasTelegramToken = prefs[SettingsKeys.PARKING_HAS_TG_TOKEN] ?: false,
                hasSmtpPassword = prefs[SettingsKeys.PARKING_HAS_SMTP_PASS] ?: false
            ),

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
        if (settings.preSplitApp != null) prefs[SettingsKeys.PRE_SPLIT_APP] = settings.preSplitApp
        else prefs.remove(SettingsKeys.PRE_SPLIT_APP)
        prefs[SettingsKeys.PRE_SPLIT_DELAY_MS] = settings.preSplitDelayMs
        prefs[SettingsKeys.SCHEDULE_PROFILES] = serializeScheduleProfiles(settings.scheduleProfiles)

        val p = settings.parkingAlert
        prefs[SettingsKeys.PARKING_ENABLED] = p.enabled
        prefs[SettingsKeys.PARKING_IDLE_MIN] = p.idleMinutes
        prefs[SettingsKeys.PARKING_DISTANCE_M] = p.distanceMeters
        prefs[SettingsKeys.PARKING_COOLDOWN_MIN] = p.cooldownMinutes
        prefs[SettingsKeys.PARKING_SEND_TELEGRAM] = p.sendTelegram
        prefs[SettingsKeys.PARKING_SEND_EMAIL] = p.sendEmail
        if (p.telegramChatId.isNotEmpty()) prefs[SettingsKeys.PARKING_TG_CHAT_ID] = p.telegramChatId
        else prefs.remove(SettingsKeys.PARKING_TG_CHAT_ID)
        prefs[SettingsKeys.PARKING_SMTP_HOST] = p.smtpHost
        prefs[SettingsKeys.PARKING_SMTP_PORT] = p.smtpPort
        if (p.smtpUser.isNotEmpty()) prefs[SettingsKeys.PARKING_SMTP_USER] = p.smtpUser
        else prefs.remove(SettingsKeys.PARKING_SMTP_USER)
        if (p.smtpRecipient.isNotEmpty()) prefs[SettingsKeys.PARKING_SMTP_RECIPIENT] = p.smtpRecipient
        else prefs.remove(SettingsKeys.PARKING_SMTP_RECIPIENT)
        prefs[SettingsKeys.PARKING_LAST_MOVEMENT_TS] = p.lastMovementTimestamp
        prefs[SettingsKeys.PARKING_LAST_ALERT_TS] = p.lastAlertTimestamp
        prefs[SettingsKeys.PARKING_HAS_TG_TOKEN] = p.hasTelegramToken
        prefs[SettingsKeys.PARKING_HAS_SMTP_PASS] = p.hasSmtpPassword

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
        com.carlauncher.data.secrets.SecretsStore(context).clear()
    }
}
