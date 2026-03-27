package com.carlauncher.bridge.model

data class DeviceState(
    val deviceName: String = "CarMap",
    val bleConnected: Boolean = false,
    val wifiConfigured: Boolean = false,
    val wifiState: String = "unconfigured",
    val wifiSsid: String = "",
    val wifiIp: String = "",
    val wifiLastError: String = "",
    val timeSource: String = "unsynced",
    val timeSynced: Boolean = false,
    val lastTimeSyncEpoch: Long = 0,
    val tzOffsetMinutes: Int = 0,
    val lightTheme: Boolean = false,
    val brightness: Int = 50,
    val speedLimit: Int = 60,
    val screen: String = "clock",
    val screenLocked: Boolean = false,
    val navReady: Boolean = false,
    val firmwareVersion: String = ""
)
