package com.carlauncher.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.receiver.ScheduleReceiver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Manages scheduling of automated tasks using AlarmManager.
 *
 * Head units power off when the car turns off, so alarms are lost.
 * We re-register alarms on every boot via BootReceiver and whenever
 * the user changes schedule settings.
 */
object ScheduleManager {

    private const val TAG = "ScheduleManager"
    private const val REQUEST_CODE = 9001

    /**
     * Re-calculates and re-registers all enabled schedules.
     * Cancels alarms for disabled or deleted profiles.
     */
    fun syncAlarms(context: Context) {
        val settings = runBlocking {
            SettingsDataStore(context).settingsFlow.first()
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // First, conceptually we would cancel ALL known alarms to start fresh, 
        // but since we only use ids internally, we'll iterate through settings.
        // We might leave orphaned alarms if a profile is deleted, so it's best to 
        // always manually call cancelAlarm(context, profile.id) when deleting.
        
        for (profile in settings.scheduleProfiles) {
            if (!profile.enabled || profile.days.isEmpty()) {
                cancelAlarm(context, profile.id)
                continue
            }

            val requestCode = profile.id.hashCode()
            val intent = Intent(context, ScheduleReceiver::class.java).apply {
                data = android.net.Uri.parse("carlauncher://schedule/${profile.id}")
                putExtra("PROFILE_ID", profile.id)
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate next exact trigger time based on startHour/startMinute
            val triggerTime = getNextTriggerTime(
                profile.days,
                profile.startHour,
                profile.startMinute
            )

            if (triggerTime <= 0) {
                Log.w(TAG, "Could not compute next trigger time for profile ${profile.id}")
                continue
            }



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ may need SCHEDULE_EXACT_ALARM permission
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                } else {
                    // Fallback to inexact
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                    )
                    Log.w(TAG, "Exact alarm permission not granted – using inexact alarm")
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
            }

            val cal = Calendar.getInstance().apply { timeInMillis = triggerTime }
            Log.d(TAG, "Alarm registered for profile '${profile.name}' (${profile.id}) at ${cal.time}")
        }
    }

    fun cancelAlarm(context: Context, profileId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            data = android.net.Uri.parse("carlauncher://schedule/$profileId")
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            profileId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarm cancelled for profile $profileId")
    }

    /**
     * Find the next Calendar time that matches one of [days] at [hour]:[minute].
     * If today matches and the time hasn't passed, return today's time.
     * Otherwise, find the next matching day within the coming week.
     */
    private fun getNextTriggerTime(days: Set<Int>, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Check today first
        if (now.get(Calendar.DAY_OF_WEEK) in days && candidate.after(now)) {
            return candidate.timeInMillis
        }

        // Advance day by day up to 7 days
        for (i in 1..7) {
            candidate.add(Calendar.DAY_OF_YEAR, 1)
            if (candidate.get(Calendar.DAY_OF_WEEK) in days) {
                // Reset time since we added a day
                candidate.set(Calendar.HOUR_OF_DAY, hour)
                candidate.set(Calendar.MINUTE, minute)
                candidate.set(Calendar.SECOND, 0)
                candidate.set(Calendar.MILLISECOND, 0)
                return candidate.timeInMillis
            }
        }

        return -1
    }

    /**
     * Checks if the current time falls within any active schedule's start and end range.
     * If so, and it hasn't triggered today, it triggers the schedule manually.
     */
    fun checkAndTriggerMissedSchedules(context: Context, skipSplitScreen: Boolean = false) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val settings = SettingsDataStore(context).settingsFlow.first()
                val now = Calendar.getInstance()
                val currentDay = now.get(Calendar.DAY_OF_WEEK)
                val currentDayOfYear = now.get(Calendar.DAY_OF_YEAR)
                val currentHour = now.get(Calendar.HOUR_OF_DAY)
                val currentMinute = now.get(Calendar.MINUTE)
                val currentTotalMinutes = currentHour * 60 + currentMinute
                
                for (profile in settings.scheduleProfiles) {
                    if (!profile.enabled || currentDay !in profile.days) continue
                    if (profile.lastTriggeredDayOfYear == currentDayOfYear) continue // Already triggered today
                    
                    val startTotalMinutes = profile.startHour * 60 + profile.startMinute
                    val endTotalMinutes = profile.endHour * 60 + profile.endMinute
                    
                    if (currentTotalMinutes in startTotalMinutes..endTotalMinutes) {
                        Log.d(TAG, "Triggering in-range schedule ${profile.id} (${profile.name})")
                        val intent = Intent(context, ScheduleReceiver::class.java).apply {
                            data = android.net.Uri.parse("carlauncher://schedule/${profile.id}")
                            putExtra("PROFILE_ID", profile.id)
                            putExtra("SKIP_SPLIT_SCREEN", skipSplitScreen)
                        }
                        context.sendBroadcast(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking missed schedules", e)
            }
        }
    }
}
