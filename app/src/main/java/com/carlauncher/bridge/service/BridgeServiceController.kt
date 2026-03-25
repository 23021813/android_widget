package com.carlauncher.bridge.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.os.Build

object BridgeServiceController {
    fun enable(context: Context) {
        startForegroundServiceCompat(context, Intent(context, EspBleBridgeService::class.java).apply {
            action = BridgeActions.ENABLE_BRIDGE
        })
    }

    fun disable(context: Context) {
        context.startService(Intent(context, EspBleBridgeService::class.java).apply {
            action = BridgeActions.DISABLE_BRIDGE
        })
    }

    fun connectDevice(context: Context, device: BluetoothDevice) {
        context.startService(Intent(context, EspBleBridgeService::class.java).apply {
            action = BridgeActions.CONNECT_DEVICE
            putExtra(EspBleBridgeService.EXTRA_DEVICE, device)
        })
    }

    fun disconnectDevice(context: Context) {
        context.startService(Intent(context, EspBleBridgeService::class.java).apply {
            action = BridgeActions.DISCONNECT_DEVICE
        })
    }

    fun sendSettings(context: Context) {
        context.startService(Intent(context, EspBleBridgeService::class.java).apply {
            action = BridgeActions.SEND_SETTINGS
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
