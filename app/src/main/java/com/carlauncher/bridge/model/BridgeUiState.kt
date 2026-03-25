package com.carlauncher.bridge.model

import com.carlauncher.data.models.NavigationBridgeSettings

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

data class BridgePermissionState(
    val notificationListener: Boolean = false,
    val postNotifications: Boolean = false,
    val location: Boolean = false,
    val bluetooth: Boolean = false
) {
    val allGranted: Boolean
        get() = notificationListener && postNotifications && location && bluetooth
}

data class BridgeUiState(
    val bridgeSettings: NavigationBridgeSettings = NavigationBridgeSettings(),
    val serviceRunning: Boolean = false,
    val bleConnectionState: BleConnectionState = BleConnectionState.DISCONNECTED,
    val selectedDeviceName: String? = null,
    val selectedDeviceAddress: String? = null,
    val navigationData: NavigationData? = null,
    val gpsSpeedKmh: Int = 0,
    val permissions: BridgePermissionState = BridgePermissionState(),
    val lastUpdateTimestamp: Long? = null
)
