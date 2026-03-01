package com.carlauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.service.OverlayService
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "onReceive called with action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val settingsDataStore = SettingsDataStore(context)

            val settings = runBlocking {
                settingsDataStore.settingsFlow.firstOrNull()
            }

            Log.d(TAG, "autoStartOnBoot = ${settings?.autoStartOnBoot}")

            if (settings?.autoStartOnBoot == true) {
                if (Settings.canDrawOverlays(context)) {
                    Log.d(TAG, "Permission granted, starting OverlayService...")
                    try {
                        OverlayService.start(context)
                        Log.d(TAG, "OverlayService started successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start OverlayService", e)
                    }
                } else {
                    Log.w(TAG, "Overlay permission not granted, skipping auto-start")
                }
            } else {
                Log.d(TAG, "Auto-start disabled in settings, skipping")
            }
        }
    }
}
