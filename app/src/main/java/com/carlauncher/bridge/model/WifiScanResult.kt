package com.carlauncher.bridge.model

data class WifiScanResult(
    val ssid: String,
    val rssi: Int,
    val auth: String,
    val channel: Int,
    val hidden: Boolean
)
