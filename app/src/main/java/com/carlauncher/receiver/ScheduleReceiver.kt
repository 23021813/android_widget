package com.carlauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.carlauncher.SplitScreenProxyActivity
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.service.ScheduleManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Triggered by AlarmManager at the user-configured schedule time.
 *
 * Flow:
 * 1. Verify today is in the configured days-of-week
 * 2. Launch split-screen with configured apps
 * 3. Optionally launch Google Maps navigation
 * 4. Optionally launch YouTube Music search
 * 5. Re-schedule alarm for the next matching day
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScheduleReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        Log.d(TAG, "Schedule alarm fired")

        val pendingResult = goAsync() // keep receiver alive for coroutine work
        CoroutineScope(Dispatchers.Main).launch {
            try {
                executeSchedule(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing schedule", e)
            } finally {
                // Re-register alarm for next occurrence
                ScheduleManager.registerAlarm(context)
                pendingResult.finish()
            }
        }
    }

    private suspend fun executeSchedule(context: Context) {
        val settings = SettingsDataStore(context).settingsFlow.first()

        if (!settings.scheduleEnabled) {
            Log.d(TAG, "Schedule disabled, skipping")
            return
        }

        // Verify today is in the configured days
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        if (today !in settings.scheduleDays) {
            Log.d(TAG, "Today ($today) not in scheduled days ${settings.scheduleDays}, skipping")
            return
        }

        // Step 1: Split-screen
        val frame1 = settings.frame1App
        val frame2 = settings.frame2App
        if (frame1 != null && frame2 != null) {
            Log.d(TAG, "Launching split-screen: $frame1 | $frame2")
            val splitIntent = Intent(context, SplitScreenProxyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("pkg1", frame1)
                putExtra("pkg2", frame2)
            }
            context.startActivity(splitIntent)
            delay(4000L) // Wait for split-screen to settle
        }

        // Step 2: Google Maps navigation (optional)
        if (settings.scheduleAutoNavigate && settings.scheduleNavigationAddress.isNotBlank()) {
            Log.d(TAG, "Launching Google Maps navigation to: ${settings.scheduleNavigationAddress}")
            try {
                val address = settings.scheduleNavigationAddress
                val gmmUri = Uri.parse("google.navigation:q=${Uri.encode(address)}&mode=d")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmUri).apply {
                    setPackage("com.google.android.apps.maps")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(mapIntent)
                delay(2000L)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Google Maps", e)
            }
        }

        // Step 3: YouTube Music search (optional)
        if (settings.scheduleAutoMusic && settings.scheduleMusicKeyword.isNotBlank()) {
            Log.d(TAG, "Launching YouTube Music search: ${settings.scheduleMusicKeyword}")
            try {
                val keyword = settings.scheduleMusicKeyword
                val musicUri = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(keyword)}")
                val musicIntent = Intent(Intent.ACTION_VIEW, musicUri).apply {
                    setPackage("com.google.android.apps.youtube.music")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(musicIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch YouTube Music, trying fallback", e)
                // Fallback: try MEDIA_PLAY_FROM_SEARCH
                try {
                    val fallbackIntent = Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
                        putExtra(android.app.SearchManager.QUERY, settings.scheduleMusicKeyword)
                        setPackage("com.google.android.apps.youtube.music")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback also failed", e2)
                }
            }
        }

        Log.d(TAG, "Schedule execution completed")
    }
}
