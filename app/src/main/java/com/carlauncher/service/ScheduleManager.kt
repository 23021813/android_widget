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
     * Register (or re-register) the daily alarm based on current settings.
     * Safe to call from BootReceiver or Settings UI.
     */
    fun registerAlarm(context: Context) {
        val settings = runBlocking {
            SettingsDataStore(context).settingsFlow.first()
        }

        if (!settings.scheduleEnabled || settings.scheduleDays.isEmpty()) {
            Log.d(TAG, "Schedule disabled or no days selected – cancelling any existing alarm")
            cancelAlarm(context)
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate next trigger time
        val triggerTime = getNextTriggerTime(
            settings.scheduleDays,
            settings.scheduleHour,
            settings.scheduleMinute
        )

        if (triggerTime <= 0) {
            Log.w(TAG, "Could not compute next trigger time")
            return
        }

        // Use setExactAndAllowWhileIdle for precision (head units are always charging)
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
        Log.d(TAG, "Alarm registered for ${cal.time}")
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Alarm cancelled")
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
}
