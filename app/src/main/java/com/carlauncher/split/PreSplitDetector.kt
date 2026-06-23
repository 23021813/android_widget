package com.carlauncher.split

import android.util.Log
import kotlinx.coroutines.delay

typealias ForegroundChecker = (sinceMs: Long, untilMs: Long) -> Boolean

class PreSplitDetector(
    private val foregroundChecker: ForegroundChecker,
    val packageName: String,
    private val pollIntervalMs: Long = POLL_INTERVAL_MS
) {
    val tag = "PreSplitDetector"

    suspend fun waitForApp(maxWaitMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        val deadline = startTime + maxWaitMs

        var pollCount = 0
        while (System.currentTimeMillis() < deadline) {
            pollCount++
            val now = System.currentTimeMillis()
            if (foregroundChecker(startTime, now)) {
                Log.d(tag, "App $packageName detected in foreground after ~${now - startTime}ms (${pollCount}polls)")
                return true
            }
            delay(pollIntervalMs)
        }

        Log.d(tag, "Timeout after ${System.currentTimeMillis() - startTime}ms waiting for $packageName")
        return false
    }

    companion object {
        private const val POLL_INTERVAL_MS = 500L
    }
}
