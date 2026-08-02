package com.carlauncher.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.carlauncher.data.models.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferencesMapperTest {

    @Test
    fun `restModeAutoExitMinutes defaults to 0 when absent`() {
        val prefs = mutablePreferencesOf()
        val settings = SettingsPreferencesMapper.read(prefs)
        assertEquals(0, settings.restModeAutoExitMinutes)
    }

    @Test
    fun `restModeAutoExitMinutes round-trips through read and write`() {
        val prefs = mutablePreferencesOf()
        SettingsPreferencesMapper.write(prefs, LauncherSettings(restModeAutoExitMinutes = 30))
        val restored = SettingsPreferencesMapper.read(prefs)
        assertEquals(30, restored.restModeAutoExitMinutes)
    }
}
