package com.carlauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.service.OverlayService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settingsDataStore = SettingsDataStore(context)
            
            // Read settings synchronously since we're in a receiver
            val settings = runBlocking {
                settingsDataStore.settingsFlow.firstOrNull()
            }
            
            if (settings?.autoStartOnBoot == true) {
                // Determine if we have overlay permissions
                if (Settings.canDrawOverlays(context)) {
                    // Pre-start the OverlayService directly
                    try {
                        OverlayService.start(context)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
