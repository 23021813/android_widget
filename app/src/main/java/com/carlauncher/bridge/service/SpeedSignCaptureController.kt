package com.carlauncher.bridge.service

import android.content.Context
import android.content.Intent
import android.os.Build

object SpeedSignCaptureController {
    fun start(context: Context) {
        val intent = Intent(context, SpeedSignCaptureService::class.java).apply {
            action = SpeedSignCaptureService.ACTION_START_CAPTURE
        }
        startForegroundServiceCompat(context, intent)
    }

    fun stop(context: Context) {
        context.startService(Intent(context, SpeedSignCaptureService::class.java).apply {
            action = SpeedSignCaptureService.ACTION_STOP_CAPTURE
        })
    }

    private fun startForegroundServiceCompat(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
