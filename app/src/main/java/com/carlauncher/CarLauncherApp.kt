package com.carlauncher

import android.app.Application
import com.carlauncher.bridge.core.NavigationBridgeRepository
import com.carlauncher.data.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class CarLauncherApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this

        val settingsDataStore = SettingsDataStore(this)
        applicationScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                NavigationBridgeRepository.updateSettings(settings.navigationBridge)
            }
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: CarLauncherApp
            private set
    }
}
