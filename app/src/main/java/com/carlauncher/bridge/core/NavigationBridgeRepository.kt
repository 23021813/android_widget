package com.carlauncher.bridge.core

import com.carlauncher.bridge.model.BleConnectionState
import com.carlauncher.bridge.model.BridgePermissionState
import com.carlauncher.bridge.model.BridgeUiState
import com.carlauncher.bridge.model.NavigationData
import com.carlauncher.data.models.NavigationBridgeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object NavigationBridgeRepository {
    private val _uiState = MutableStateFlow(BridgeUiState())
    val uiState: StateFlow<BridgeUiState> = _uiState.asStateFlow()

    fun updateSettings(settings: NavigationBridgeSettings) {
        _uiState.update {
            it.copy(
                bridgeSettings = settings,
                selectedDeviceName = settings.lastDeviceName ?: it.selectedDeviceName,
                selectedDeviceAddress = settings.lastDeviceAddress ?: it.selectedDeviceAddress
            )
        }
    }

    fun updatePermissions(permissions: BridgePermissionState) {
        _uiState.update { it.copy(permissions = permissions) }
    }

    fun updateServiceRunning(running: Boolean) {
        _uiState.update {
            it.copy(
                serviceRunning = running,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun updateBleState(state: BleConnectionState, deviceName: String? = null, deviceAddress: String? = null) {
        _uiState.update {
            it.copy(
                bleConnectionState = state,
                selectedDeviceName = deviceName ?: it.selectedDeviceName,
                selectedDeviceAddress = deviceAddress ?: it.selectedDeviceAddress,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun updateNavigationData(data: NavigationData?) {
        _uiState.update {
            it.copy(
                navigationData = data,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun updateGpsSpeed(speedKmh: Int) {
        _uiState.update {
            it.copy(
                gpsSpeedKmh = speedKmh,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun clearNavigation() {
        _uiState.update {
            it.copy(
                navigationData = null,
                lastUpdateTimestamp = System.currentTimeMillis()
            )
        }
    }
}
