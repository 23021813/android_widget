package com.carlauncher

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.carlauncher.service.SplitScreenLauncher
import com.carlauncher.split.PreSplitDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplitScreenProxyActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg1 = intent.getStringExtra("pkg1")
        val pkg2 = intent.getStringExtra("pkg2")
        val navAddress = intent.getStringExtra("nav_address")
        val musicKeyword = intent.getStringExtra("music_keyword")
        val preSplitPkg = intent.getStringExtra("pre_split_pkg")
        val preSplitDelayMs = intent.getIntExtra("pre_split_delay_ms", 1500)

        Log.d(TAG, "onCreate: pkg1=$pkg1 pkg2=$pkg2 navAddress=$navAddress musicKeyword=$musicKeyword")

        if (pkg1 == null || pkg2 == null) {
            Log.w(TAG, "Missing pkg1/pkg2 in intent, aborting split")
            mainHandler.postDelayed({ finish() }, 1500)
            return
        }

        val actionIntent1 = resolveActionIntent(pkg1, navAddress, musicKeyword)
        val actionIntent2 = resolveActionIntent(pkg2, navAddress, musicKeyword)

        Log.d(TAG, "Resolved actions: action1=${actionIntent1?.data} action2=${actionIntent2?.data}")

        if (preSplitPkg != null) {
            SplitScreenLauncher.launchApp(this, preSplitPkg)
            scope.launch {
                waitForPreSplitApp(preSplitPkg, preSplitDelayMs.toLong())
                launchSplit(pkg1, pkg2, actionIntent1, actionIntent2)
            }
        } else {
            launchSplit(pkg1, pkg2, actionIntent1, actionIntent2)
        }
    }

    private suspend fun waitForPreSplitApp(packageName: String, maxWaitMs: Long) {
        if (hasUsageStatsPermission()) {
            Log.d(TAG, "UsageStats granted — waiting for $packageName to reach foreground (max ${maxWaitMs}ms)")
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val detector = PreSplitDetector(
                foregroundChecker = { sinceMs, untilMs ->
                    val events = usageStatsManager.queryEvents(sinceMs, untilMs)
                    var found = false
                    val event = UsageEvents.Event()
                    while (events.hasNextEvent()) {
                        events.getNextEvent(event)
                        if (event.packageName == packageName &&
                            event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                        ) {
                            found = true
                            break
                        }
                    }
                    found
                },
                packageName = packageName,
                pollIntervalMs = 500L
            )
            if (!detector.waitForApp(maxWaitMs)) {
                Log.w(TAG, "Timeout waiting for $packageName, proceeding anyway")
            }
        } else {
            Log.d(TAG, "UsageStats not granted — falling back to fixed delay of ${maxWaitMs}ms")
            Toast.makeText(
                this,
                "Enable 'Usage access' for CarFloat in Settings for faster pre-split detection",
                Toast.LENGTH_LONG
            ).show()
            delay(maxWaitMs)
        }
    }

    private fun launchSplit(
        pkg1: String, pkg2: String,
        actionIntent1: Intent?, actionIntent2: Intent?
    ) {
        SplitScreenLauncher.launchSplitScreen(
            this, pkg1, pkg2,
            actionIntent1 = actionIntent1,
            actionIntent2 = actionIntent2
        )
        mainHandler.postDelayed({ finish() }, 1500)
    }

    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check usage stats permission", e)
            false
        }
    }

    private fun resolveActionIntent(pkg: String, navAddress: String?, musicKeyword: String?): Intent? {
        if (pkg == "com.google.android.apps.maps" && !navAddress.isNullOrBlank()) {
            return SplitScreenLauncher.buildNavigationIntent(navAddress)
        }
        if (pkg == "com.google.android.apps.youtube.music" && !musicKeyword.isNullOrBlank()) {
            return SplitScreenLauncher.buildMusicSearchIntent(musicKeyword)
        }
        return null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SplitScreenProxy"
        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    }
}
