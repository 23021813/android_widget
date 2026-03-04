package com.carlauncher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

        val profileId = intent?.getStringExtra("PROFILE_ID")
        if (profileId == null) {
            Log.e(TAG, "No profile ID in intent, aborting.")
            return
        }

        val skipSplitScreen = intent.getBooleanExtra("SKIP_SPLIT_SCREEN", false)

        val pendingResult = goAsync() // keep receiver alive for coroutine work
        CoroutineScope(Dispatchers.Main).launch {
            try {
                executeSchedule(context, profileId, skipSplitScreen)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing schedule", e)
            } finally {
                // Re-register alarms to schedule next occurrences
                ScheduleManager.syncAlarms(context)
                pendingResult.finish()
            }
        }
    }

    private suspend fun executeSchedule(context: Context, profileId: String, skipSplitScreen: Boolean) {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val profile = settings.scheduleProfiles.find { it.id == profileId }

        if (profile == null) {
            Log.d(TAG, "Profile $profileId not found, skipping")
            return
        }

        if (!profile.enabled) {
            Log.d(TAG, "Profile ${profile.name} disabled, skipping")
            return
        }

        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        if (today !in profile.days) {
            Log.d(TAG, "Today ($today) not in scheduled days ${profile.days}, skipping")
            return
        }

        val navAddress = if (profile.autoNavigate) profile.navAddress else ""
        val musicKeyword = if (profile.autoMusic) profile.musicKeyword else ""

        if (!skipSplitScreen) {
            val frame1 = settings.frame1App
            val frame2 = settings.frame2App
            if (frame1 != null && frame2 != null) {
                Log.d(TAG, "Launching split-screen with actions: $frame1 | $frame2 | nav=$navAddress | music=$musicKeyword")
                val splitIntent = Intent(context, SplitScreenProxyActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("pkg1", frame1)
                    putExtra("pkg2", frame2)
                    if (navAddress.isNotBlank()) putExtra("nav_address", navAddress)
                    if (musicKeyword.isNotBlank()) putExtra("music_keyword", musicKeyword)
                }
                context.startActivity(splitIntent)
                delay(4000L)
            }
        } else {
            Log.d(TAG, "Split-screen already active, sending actions via SplitScreenProxyActivity")
            val frame1 = settings.frame1App
            val frame2 = settings.frame2App
            if (frame1 != null && frame2 != null && (navAddress.isNotBlank() || musicKeyword.isNotBlank())) {
                val actionIntent = Intent(context, SplitScreenProxyActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("pkg1", frame1)
                    putExtra("pkg2", frame2)
                    if (navAddress.isNotBlank()) putExtra("nav_address", navAddress)
                    if (musicKeyword.isNotBlank()) putExtra("music_keyword", musicKeyword)
                }
                context.startActivity(actionIntent)
                delay(4000L)
            }
        }

        try {
            val currentList = settings.scheduleProfiles.toMutableList()
            val idx = currentList.indexOfFirst { it.id == profileId }
            if (idx != -1) {
                currentList[idx] = profile.copy(lastTriggeredDayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR))
                settingsDataStore.updateSettings(settings.copy(scheduleProfiles = currentList))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update lastTriggeredDayOfYear", e)
        }

        Log.d(TAG, "Schedule execution completed")
    }
}
