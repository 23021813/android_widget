package com.carlauncher.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.data.models.NavigationBridgeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreferencesMapperTest {
    @Test
    fun `read maps bridge preferences into launcher settings`() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.BRIDGE_ENABLED to true,
            SettingsKeys.BRIDGE_LAST_DEVICE_NAME to "ESP32 Dashboard",
            SettingsKeys.BRIDGE_LAST_DEVICE_ADDRESS to "AA:BB:CC:DD:EE:FF",
            SettingsKeys.BRIDGE_LIGHT_THEME to false,
            SettingsKeys.BRIDGE_BRIGHTNESS to 80,
            SettingsKeys.BRIDGE_SPEED_WARNING_LIMIT to 75
        )

        val settings = SettingsPreferencesMapper.read(prefs)

        assertTrue(settings.navigationBridge.enabled)
        assertEquals("ESP32 Dashboard", settings.navigationBridge.lastDeviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", settings.navigationBridge.lastDeviceAddress)
        assertEquals(false, settings.navigationBridge.displayLightTheme)
        assertEquals(80, settings.navigationBridge.displayBrightness)
        assertEquals(75, settings.navigationBridge.speedWarningLimit)
    }

    @Test
    fun `write stores bridge settings into preferences`() {
        val prefs = mutablePreferencesOf()
        val settings = LauncherSettings(
            navigationBridge = NavigationBridgeSettings(
                enabled = true,
                lastDeviceName = "ESP32 HUD",
                lastDeviceAddress = "11:22:33:44:55:66",
                displayLightTheme = true,
                displayBrightness = 35,
                speedWarningLimit = 62
            )
        )

        SettingsPreferencesMapper.write(prefs, settings)

        assertEquals(true, prefs[SettingsKeys.BRIDGE_ENABLED])
        assertEquals("ESP32 HUD", prefs[SettingsKeys.BRIDGE_LAST_DEVICE_NAME])
        assertEquals("11:22:33:44:55:66", prefs[SettingsKeys.BRIDGE_LAST_DEVICE_ADDRESS])
        assertEquals(true, prefs[SettingsKeys.BRIDGE_LIGHT_THEME])
        assertEquals(35, prefs[SettingsKeys.BRIDGE_BRIGHTNESS])
        assertEquals(62, prefs[SettingsKeys.BRIDGE_SPEED_WARNING_LIMIT])
    }
}
