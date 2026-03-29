package com.carlauncher.bridge.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.carlauncher.R
import com.carlauncher.bridge.core.NavigationBridgeRepository
import com.carlauncher.bridge.model.BleConnectionState
import com.carlauncher.bridge.permission.NavigationBridgePermissionHelper
import com.carlauncher.bridge.service.BridgeServiceController
import com.carlauncher.data.models.NavigationBridgeSettings
import com.carlauncher.service.OverlayService
import com.carlauncher.ui.screens.SettingsSection
import com.carlauncher.ui.screens.SettingsSlider
import com.carlauncher.ui.screens.SettingsTextField
import com.carlauncher.ui.screens.SettingsToggle
import com.carlauncher.ui.theme.AccentCyan
import com.carlauncher.ui.theme.AccentGreen
import com.carlauncher.ui.theme.AccentOrange
import com.carlauncher.ui.theme.AccentRed
import com.carlauncher.ui.theme.DarkBackground
import com.carlauncher.ui.theme.DarkSurface
import com.carlauncher.ui.theme.DarkSurfaceVariant
import com.carlauncher.ui.theme.TextPrimary
import com.carlauncher.ui.theme.TextSecondary

private data class ScannedBleDevice(
    val name: String,
    val address: String,
    val device: BluetoothDevice
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspBridgeScreen(
    bridgeSettings: NavigationBridgeSettings,
    onSettingsUpdate: (NavigationBridgeSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val bridgeState by NavigationBridgeRepository.uiState.collectAsState()

    val scannedDevices = remember { mutableStateListOf<ScannedBleDevice>() }
    var showDevicePicker by rememberSaveable { mutableStateOf(false) }
    var isScanning by rememberSaveable { mutableStateOf(false) }
    var scanStopJob by remember { mutableStateOf<Job?>(null) }
    var speedLimitInput by remember(bridgeSettings.speedWarningLimit) {
        mutableStateOf(bridgeSettings.speedWarningLimit.toString())
    }
    var showWifiPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var selectedWifiSsid by rememberSaveable { mutableStateOf("") }
    var wifiPasswordInput by rememberSaveable { mutableStateOf("") }
    var captureIntervalInput by remember(bridgeSettings.speedSignCapture.intervalSeconds) {
        mutableStateOf(bridgeSettings.speedSignCapture.intervalSeconds.toString())
    }
    var roiCalibrationVisible by rememberSaveable { mutableStateOf(false) }

    fun refreshPermissions() {
        NavigationBridgeRepository.updatePermissions(
            NavigationBridgePermissionHelper.currentState(context)
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
    }
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshPermissions()
    }

    val bluetoothManager = remember(context) {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    fun upsertScannedDevice(device: BluetoothDevice) {
        val name = runCatching { device.name }.getOrNull().orEmpty()
            .ifBlank { context.getString(R.string.bridge_device_unnamed) }
        val scanned = ScannedBleDevice(
            name = name,
            address = device.address,
            device = device
        )
        val existingIndex = scannedDevices.indexOfFirst { it.address == scanned.address }
        if (existingIndex == -1) {
            scannedDevices.add(scanned)
        } else {
            scannedDevices[existingIndex] = scanned
        }
    }

    fun preloadBondedDevices(adapter: BluetoothAdapter?) {
        adapter?.bondedDevices
            ?.sortedBy { runCatching { it.name }.getOrNull().orEmpty() }
            ?.forEach(::upsertScannedDevice)
    }

    val scanCallback = remember {
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.let(::upsertScannedDevice)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { result -> result.device?.let(::upsertScannedDevice) }
            }

            override fun onScanFailed(errorCode: Int) {
                scanStopJob?.cancel()
                isScanning = false
                Toast.makeText(
                    context,
                    context.getString(R.string.bridge_scan_failed, errorCode),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun stopScan() {
        scanStopJob?.cancel()
        scanStopJob = null
        val scanner = bluetoothManager.adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(scanCallback) }
        isScanning = false
    }

    fun startScan() {
        if (!bridgeSettings.enabled) {
            Toast.makeText(
                context,
                context.getString(R.string.bridge_enable_service_first),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        if (!NavigationBridgePermissionHelper.hasBluetoothPermission(context)) {
            bluetoothPermissionLauncher.launch(NavigationBridgePermissionHelper.bluetoothPermissions())
            return
        }
        if (NavigationBridgePermissionHelper.requiresLocationForBleScan() &&
            !NavigationBridgePermissionHelper.hasLocationPermission(context)
        ) {
            locationPermissionLauncher.launch(NavigationBridgePermissionHelper.locationPermissions())
            return
        }
        if (NavigationBridgePermissionHelper.requiresLocationForBleScan() &&
            !NavigationBridgePermissionHelper.isLocationServicesEnabled(context)
        ) {
            Toast.makeText(
                context,
                context.getString(R.string.bridge_location_service_required),
                Toast.LENGTH_SHORT
            ).show()
            NavigationBridgePermissionHelper.openLocationSettings(context)
            return
        }
        if (!NavigationBridgePermissionHelper.isBluetoothEnabled(context)) {
            enableBluetoothLauncher.launch(NavigationBridgePermissionHelper.bluetoothEnableIntent())
            return
        }

        val adapter = bluetoothManager.adapter
        if (adapter == null) {
            Toast.makeText(
                context,
                context.getString(R.string.bridge_scanner_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        stopScan()
        scannedDevices.clear()
        preloadBondedDevices(adapter)
        showDevicePicker = true
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Toast.makeText(
                context,
                context.getString(R.string.bridge_scanner_paired_only),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val started = runCatching {
            scanner.startScan(
                emptyList(),
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCallback
            )
        }.isSuccess

        if (!started) {
            Toast.makeText(
                context,
                context.getString(R.string.bridge_scanner_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        isScanning = true
        scanStopJob = scope.launch {
            delay(12_000L)
            stopScan()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopScan()
            OverlayService.hideSpeedSignRoiCalibration(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.bridge_screen_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = androidx.compose.ui.res.stringResource(R.string.action_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SettingsSection(title = androidx.compose.ui.res.stringResource(R.string.bridge_section_runtime)) {
                    StatusBadgeRow(
                        serviceEnabled = bridgeState.serviceRunning,
                        bleStatus = bridgeState.bleConnectionState.name,
                        selectedDevice = bridgeState.selectedDeviceName
                            ?: bridgeState.selectedDeviceAddress
                            ?: androidx.compose.ui.res.stringResource(R.string.bridge_no_device_saved)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsToggle(
                        label = androidx.compose.ui.res.stringResource(R.string.bridge_toggle_label),
                        checked = bridgeSettings.enabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !bridgeState.permissions.allGranted) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.bridge_permissions_required),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onSettingsUpdate(bridgeSettings.copy(enabled = enabled))
                            }
                        }
                    )
                }
            }

            item {
                SettingsSection(title = androidx.compose.ui.res.stringResource(R.string.bridge_section_permissions)) {
                    PermissionStatusRow(
                        title = androidx.compose.ui.res.stringResource(R.string.bridge_permission_notification_listener),
                        granted = bridgeState.permissions.notificationListener,
                        icon = Icons.Default.NotificationsActive,
                        actionLabel = androidx.compose.ui.res.stringResource(R.string.bridge_action_open_settings),
                        onAction = { NavigationBridgePermissionHelper.openNotificationListenerSettings(context) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionStatusRow(
                        title = androidx.compose.ui.res.stringResource(R.string.bridge_permission_notifications),
                        granted = bridgeState.permissions.postNotifications,
                        icon = Icons.Default.NotificationsActive,
                        actionLabel = androidx.compose.ui.res.stringResource(R.string.bridge_action_grant),
                        onAction = {
                            val permissions = NavigationBridgePermissionHelper.postNotificationPermissions()
                            if (permissions.isNotEmpty()) {
                                notificationPermissionLauncher.launch(permissions)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionStatusRow(
                        title = androidx.compose.ui.res.stringResource(R.string.bridge_permission_location),
                        granted = bridgeState.permissions.location,
                        icon = Icons.Default.Route,
                        actionLabel = androidx.compose.ui.res.stringResource(R.string.bridge_action_grant),
                        onAction = {
                            locationPermissionLauncher.launch(
                                NavigationBridgePermissionHelper.locationPermissions()
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionStatusRow(
                        title = androidx.compose.ui.res.stringResource(R.string.bridge_permission_bluetooth),
                        granted = bridgeState.permissions.bluetooth,
                        icon = Icons.Default.Bluetooth,
                        actionLabel = androidx.compose.ui.res.stringResource(R.string.bridge_action_grant),
                        onAction = {
                            bluetoothPermissionLauncher.launch(
                                NavigationBridgePermissionHelper.bluetoothPermissions()
                            )
                        }
                    )
                }
            }

            item {
                SettingsSection(title = androidx.compose.ui.res.stringResource(R.string.bridge_section_device)) {
                    Text(
                        text = bridgeState.selectedDeviceName
                            ?: bridgeState.selectedDeviceAddress
                            ?: androidx.compose.ui.res.stringResource(R.string.bridge_no_device_saved),
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(R.string.bridge_scan_hint),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = ::startScan,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentCyan,
                                contentColor = DarkBackground
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = if (isScanning) {
                                    androidx.compose.ui.res.stringResource(R.string.bridge_scanning)
                                } else {
                                    androidx.compose.ui.res.stringResource(R.string.bridge_action_scan)
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = { BridgeServiceController.disconnectDevice(context) },
                            modifier = Modifier.weight(1f),
                            enabled = bridgeState.bleConnectionState != BleConnectionState.DISCONNECTED
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(androidx.compose.ui.res.stringResource(R.string.bridge_action_disconnect))
                        }
                    }
                }
            }

            if (bridgeState.bleConnectionState == BleConnectionState.CONNECTED) {
                item {
                    SettingsSection(title = "Device Status & Wi-Fi") {
                        val state = bridgeState.deviceState
                        DiagnosticText(label = "Screen", value = "${state.screen} (Locked: ${state.screenLocked})")
                        DiagnosticText(label = "Time Source", value = "${state.timeSource} (Synced: ${state.timeSynced})")
                        DiagnosticText(label = "Firmware", value = state.firmwareVersion)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val wifiStatusStr = when (state.wifiState) {
                            "connected" -> "Connected to ${state.wifiSsid} (${state.wifiIp})"
                            "disconnected" -> "Disconnected (Saved: ${state.wifiSsid})"
                            "unconfigured" -> "Unconfigured"
                            else -> state.wifiState
                        }
                        DiagnosticText(label = "Wi-Fi Status", value = wifiStatusStr)
                        if (state.wifiLastError.isNotEmpty()) {
                            DiagnosticText(label = "Last Error", value = state.wifiLastError)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { BridgeServiceController.wifiScan(context) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (bridgeState.isWifiScanning) "Scanning..." else "Scan Wi-Fi")
                            }
                            OutlinedButton(
                                onClick = { BridgeServiceController.wifiForget(context) },
                                modifier = Modifier.weight(1f),
                                enabled = state.wifiConfigured
                            ) {
                                Text("Forget Wi-Fi")
                            }
                        }
                        
                        if (bridgeState.wifiScanResults.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Scan Results:", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                bridgeState.wifiScanResults.sortedByDescending { it.rssi }.take(5).forEach { result ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                        onClick = { 
                                            selectedWifiSsid = result.ssid
                                            wifiPasswordInput = ""
                                            showWifiPasswordDialog = true
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(text = result.ssid, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                                                Text(text = "Sec: ${result.auth} | RSSI: ${result.rssi}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                            }
                                            Icon(Icons.Default.Wifi, contentDescription = null, tint = AccentCyan)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = androidx.compose.ui.res.stringResource(R.string.bridge_section_settings)) {
                    SettingsToggle(
                        label = androidx.compose.ui.res.stringResource(R.string.bridge_light_theme),
                        checked = bridgeSettings.displayLightTheme,
                        onCheckedChange = {
                            onSettingsUpdate(bridgeSettings.copy(displayLightTheme = it))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSlider(
                        label = androidx.compose.ui.res.stringResource(R.string.bridge_brightness),
                        value = bridgeSettings.displayBrightness.toFloat(),
                        valueRange = 0f..100f,
                        displayValue = bridgeSettings.displayBrightness.toString(),
                        onValueChange = {
                            onSettingsUpdate(bridgeSettings.copy(displayBrightness = it.toInt()))
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        label = androidx.compose.ui.res.stringResource(R.string.bridge_speed_limit),
                        value = speedLimitInput,
                        onValueChange = { newValue ->
                            speedLimitInput = newValue.filter(Char::isDigit).take(3)
                            speedLimitInput.toIntOrNull()?.let { speed ->
                                onSettingsUpdate(bridgeSettings.copy(speedWarningLimit = speed))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggle(
                        label = "Enable speed sign capture",
                        checked = bridgeSettings.speedSignCapture.enabled,
                        onCheckedChange = { enabled ->
                            if (!enabled && roiCalibrationVisible) {
                                roiCalibrationVisible = false
                                OverlayService.hideSpeedSignRoiCalibration(context)
                            }
                            onSettingsUpdate(
                                bridgeSettings.copy(
                                    speedSignCapture = bridgeSettings.speedSignCapture.copy(enabled = enabled)
                                )
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTextField(
                        label = "Capture interval (seconds)",
                        value = captureIntervalInput,
                        onValueChange = { newValue ->
                            captureIntervalInput = newValue.filter(Char::isDigit).take(2)
                            captureIntervalInput.toIntOrNull()?.let { seconds ->
                                onSettingsUpdate(
                                    bridgeSettings.copy(
                                        speedSignCapture = bridgeSettings.speedSignCapture.copy(
                                            intervalSeconds = seconds.coerceIn(2, 30)
                                        )
                                    )
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ROI: x=${bridgeSettings.speedSignCapture.roiX}, y=${bridgeSettings.speedSignCapture.roiY}, w=${bridgeSettings.speedSignCapture.roiWidth}, h=${bridgeSettings.speedSignCapture.roiHeight}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            roiCalibrationVisible = !roiCalibrationVisible
                            if (roiCalibrationVisible) {
                                OverlayService.showSpeedSignRoiCalibration(context)
                            } else {
                                OverlayService.hideSpeedSignRoiCalibration(context)
                            }
                        },
                        enabled = bridgeSettings.speedSignCapture.enabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (roiCalibrationVisible) "Hide ROI calibrator" else "Show ROI calibrator")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { BridgeServiceController.sendSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(androidx.compose.ui.res.stringResource(R.string.bridge_send_settings_now))
                    }
                }
            }

            item {
                SettingsSection(title = androidx.compose.ui.res.stringResource(R.string.bridge_section_diagnostics)) {
                    val speedSign = bridgeState.speedSignDetection
                    DiagnosticText(
                        label = "Capture Pipeline",
                        value = if (bridgeState.speedSignCaptureRunning) {
                            "Running (${bridgeState.speedSignCaptureStatus})"
                        } else {
                            "Stopped (${bridgeState.speedSignCaptureStatus})"
                        }
                    )
                    DiagnosticText(
                        label = "Current / Upcoming",
                        value = "${speedSign?.currentLimit ?: "--"} / ${speedSign?.upcomingLimit ?: "--"}"
                    )
                    DiagnosticText(
                        label = "Layout / Source",
                        value = "${speedSign?.layoutType ?: "--"} / ${speedSign?.captureSource ?: "--"}"
                    )
                    DiagnosticText(
                        label = "Candidates",
                        value = speedSign?.candidates?.takeIf { it.isNotEmpty() }?.joinToString(",") ?: "--"
                    )
                    DiagnosticText(
                        label = "Upcoming Distance",
                        value = speedSign?.upcomingDistanceMeters?.let { "$it m" } ?: "--"
                    )
                    if (!speedSign?.debugSummary.isNullOrBlank()) {
                        DiagnosticText(
                            label = "OCR Debug",
                            value = speedSign?.debugSummary.orEmpty().take(160)
                        )
                    }
                    if (!speedSign?.rawText.isNullOrBlank()) {
                        DiagnosticText(
                            label = "OCR Raw",
                            value = speedSign?.rawText.orEmpty().take(120)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    DiagnosticsCard(
                        title = androidx.compose.ui.res.stringResource(R.string.bridge_diag_speed),
                        icon = Icons.Default.Speed,
                        value = "${bridgeState.gpsSpeedKmh} km/h"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val navData = bridgeState.navigationData
                    if (navData == null) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.bridge_no_navigation),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val bitmap = navData.actionIcon.bitmap
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(DarkSurfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Directions,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                DiagnosticText(
                                    label = androidx.compose.ui.res.stringResource(R.string.bridge_diag_next_road),
                                    value = navData.nextDirection.nextRoad
                                )
                                DiagnosticText(
                                    label = androidx.compose.ui.res.stringResource(R.string.bridge_diag_road_desc),
                                    value = navData.nextDirection.nextRoadAdditionalInfo
                                )
                                DiagnosticText(
                                    label = androidx.compose.ui.res.stringResource(R.string.bridge_diag_distance),
                                    value = navData.nextDirection.distance
                                )
                                DiagnosticText(
                                    label = androidx.compose.ui.res.stringResource(R.string.bridge_diag_ete),
                                    value = navData.eta.ete
                                )
                                DiagnosticText(
                                    label = androidx.compose.ui.res.stringResource(R.string.bridge_diag_eta),
                                    value = navData.eta.eta
                                )
                                DiagnosticText(
                                    label = androidx.compose.ui.res.stringResource(R.string.bridge_diag_total_distance),
                                    value = navData.eta.distance
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDevicePicker) {
        ModalBottomSheet(
            onDismissRequest = {
                showDevicePicker = false
                stopScan()
            },
            containerColor = DarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.bridge_picker_title),
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (scannedDevices.isEmpty()) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(
                            if (isScanning) R.string.bridge_picker_scanning_hint
                            else R.string.bridge_picker_empty
                        ),
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(scannedDevices, key = { it.address }) { device ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                                onClick = {
                                    BridgeServiceController.connectDevice(context, device.device)
                                    showDevicePicker = false
                                    stopScan()
                                }
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = device.name,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = device.address,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWifiPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showWifiPasswordDialog = false },
            containerColor = DarkSurface,
            title = { Text(text = "Connect to $selectedWifiSsid", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = wifiPasswordInput,
                    onValueChange = { wifiPasswordInput = it },
                    label = { Text("Password", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        BridgeServiceController.wifiConnect(context, selectedWifiSsid, wifiPasswordInput)
                        showWifiPasswordDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWifiPasswordDialog = false }) {
                    Text("Cancel", color = AccentCyan)
                }
            }
        )
    }
}

@Composable
private fun StatusBadgeRow(
    serviceEnabled: Boolean,
    bleStatus: String,
    selectedDevice: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                label = if (serviceEnabled) "Service ON" else "Service OFF",
                highlighted = serviceEnabled
            )
            StatusPill(
                label = bleStatus,
                highlighted = bleStatus == "CONNECTED"
            )
        }
        Text(
            text = selectedDevice,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StatusPill(label: String, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (highlighted) AccentGreen.copy(alpha = 0.18f) else DarkSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (highlighted) AccentGreen else TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    granted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (granted) AccentGreen.copy(alpha = 0.18f) else AccentRed.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) AccentGreen else AccentRed
                )
            }
            Column {
                Text(text = title, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (granted) {
                        androidx.compose.ui.res.stringResource(R.string.bridge_permission_granted)
                    } else {
                        androidx.compose.ui.res.stringResource(R.string.bridge_permission_missing)
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun DiagnosticsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = AccentCyan)
                Column {
                    Text(text = title, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = value,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticText(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(text = label, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text(text = value, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}
