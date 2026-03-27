package com.carlauncher.bridge.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.util.Log
import com.carlauncher.utils.AppLogger
import android.os.Bundle
import android.os.IBinder
import android.util.Size
import androidx.core.app.NotificationCompat
import com.carlauncher.LauncherActivity
import com.carlauncher.R
import com.carlauncher.bridge.core.BleCharacteristics
import com.carlauncher.bridge.core.BleWriteQueue
import com.carlauncher.bridge.core.BitmapHelper
import com.carlauncher.bridge.core.BridgePayloadSerializer
import com.carlauncher.bridge.core.NavigationBridgeRepository
import com.carlauncher.bridge.core.NavigationIconHasher
import com.carlauncher.bridge.core.SentIconRegistry
import com.carlauncher.bridge.model.BleConnectionState
import com.carlauncher.bridge.model.NavigationData
import com.carlauncher.bridge.permission.NavigationBridgePermissionHelper
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.NavigationBridgeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil

@SuppressLint("MissingPermission")
class EspBleBridgeService : Service(), LocationListener {
    companion object {
        const val CHANNEL_ID = "esp32_bridge"
        const val NOTIFICATION_ID = 1202
        const val EXTRA_DEVICE = "device"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var bluetoothManager: BluetoothManager

    private var currentSettings = NavigationBridgeSettings()
    private var bluetoothGatt: BluetoothGatt? = null
    private var currentDevice: BluetoothDevice? = null
    private var connectionState = BluetoothProfile.STATE_DISCONNECTED
    private var writeQueue = BleWriteQueue()
    private val isSending = AtomicBoolean(false)
    private var lastNavigationData: NavigationData? = null
    private var pingTimer: Timer? = null
    private var reconnectTimer: Timer? = null
    private var firstPing = true
    private val sentIconRegistry = SentIconRegistry()
    private val pendingDescriptors = mutableListOf<BluetoothGattDescriptor>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        createNotificationChannel()

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { it.navigationBridge }
                .distinctUntilChanged()
                .collect { settings ->
                    currentSettings = settings
                    NavigationBridgeRepository.updateSettings(settings)
                    if (NavigationBridgeRepository.uiState.value.serviceRunning &&
                        connectionState == BluetoothProfile.STATE_CONNECTED
                    ) {
                        sendPreferencesToDevice()
                    }
                }
        }

        serviceScope.launch {
            NavigationBridgeRepository.uiState
                .map { it.navigationData }
                .distinctUntilChanged()
                .collect { data ->
                    lastNavigationData = data
                    if (connectionState == BluetoothProfile.STATE_CONNECTED &&
                        NavigationBridgeRepository.uiState.value.serviceRunning
                    ) {
                        sendToDevice(data)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            BridgeActions.ENABLE_BRIDGE -> enableBridge()
            BridgeActions.DISABLE_BRIDGE -> disableBridge()
            BridgeActions.CONNECT_DEVICE -> {
                if (NavigationBridgePermissionHelper.hasBluetoothPermission(this)) {
                    intent.readBluetoothDevice()?.let(::connect)
                }
            }
            BridgeActions.DISCONNECT_DEVICE -> disconnect()
            BridgeActions.SEND_SETTINGS -> sendPreferencesToDevice()
            BridgeActions.WIFI_SCAN -> sendWifiScan()
            BridgeActions.WIFI_CONNECT -> {
                val ssid = intent.getStringExtra("ssid").orEmpty()
                val password = intent.getStringExtra("password").orEmpty()
                if (ssid.isNotEmpty()) sendWifiConnect(ssid, password)
            }
            BridgeActions.WIFI_FORGET -> sendWifiForget()
            BridgeActions.STATUS_GET -> sendStatusGet()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopPingTimer()
        stopReconnectTimer()
        bluetoothGatt?.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enableBridge() {
        if (!NavigationBridgePermissionHelper.currentState(this).allGranted) {
            stopSelf()
            return
        }
        startForeground(NOTIFICATION_ID, buildNotification("ESP32 Bridge đang chạy"))
        NavigationBridgeRepository.updateServiceRunning(true)
        subscribeToLocationUpdates()
        if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {
            startReconnectTimer()
        }
    }

    private fun disableBridge() {
        NavigationBridgeRepository.updateServiceRunning(false)
        stopPingTimer()
        stopReconnectTimer()
        unsubscribeFromLocationUpdates()
        disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                AppLogger.i("EspBleBridgeService", "GATT Connected. Status: $status")
                connectionState = BluetoothProfile.STATE_CONNECTING
                NavigationBridgeRepository.updateBleState(
                    BleConnectionState.CONNECTING,
                    currentDevice?.name,
                    currentDevice?.address
                )
                gatt?.requestMtu(517)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                AppLogger.w("EspBleBridgeService", "GATT Disconnected. Status: $status")
                val lastName = currentDevice?.name
                val lastAddress = currentDevice?.address
                cleanupGattConnection()
                NavigationBridgeRepository.updateBleState(
                    BleConnectionState.DISCONNECTED,
                    lastName,
                    lastAddress
                )
                if (NavigationBridgeRepository.uiState.value.serviceRunning) {
                    updateNotification("Chưa kết nối thiết bị")
                    startReconnectTimer()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            gatt?.discoverServices()
            connectionState = BluetoothProfile.STATE_CONNECTING
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) return
            connectionState = BluetoothProfile.STATE_CONNECTED
            
            pendingDescriptors.clear()
            val service = gatt.getService(UUID.fromString(BleCharacteristics.SERVICE_UUID))
            if (service != null) {
                listOf(BleCharacteristics.CHA_DEVICE_STATUS, BleCharacteristics.CHA_SETTINGS).forEach { uuidStr ->
                    service.getCharacteristic(UUID.fromString(uuidStr))?.let { char ->
                        gatt.setCharacteristicNotification(char, true)
                        char.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let { desc ->
                            @Suppress("DEPRECATION")
                            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            pendingDescriptors.add(desc)
                        }
                    }
                }
            }
            writeNextDescriptor(gatt)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            gatt?.let { writeNextDescriptor(it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleBleNotification(characteristic.uuid.toString(), String(value, Charsets.UTF_8))
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val value = characteristic?.value ?: return
            handleBleNotification(characteristic.uuid.toString(), String(value, Charsets.UTF_8))
        }

        private fun writeNextDescriptor(gatt: BluetoothGatt) {
            if (pendingDescriptors.isEmpty()) {
                onSubscriptionsComplete()
                return
            }
            val desc = pendingDescriptors.removeAt(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(desc)
            }
        }

        private fun onSubscriptionsComplete() {
            isSending.set(false)
            writeQueue.clear()
            sentIconRegistry.clear()
            stopReconnectTimer()
            startPingTimer()
            updateNotification("Đã kết nối ${currentDevice?.name ?: "ESP32"}")
            NavigationBridgeRepository.updateBleState(
                BleConnectionState.CONNECTED,
                currentDevice?.name,
                currentDevice?.address
            )
            persistLastDevice(currentDevice)
            sendTimeSync()
            sendToDevice(lastNavigationData)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            AppLogger.d("EspBleBridgeService", "Write completed. Status: $status")
            isSending.set(false)
            processNextWrite()
        }
    }

    private fun write(item: BleWriteQueue.QueueItem) {
        val gatt = bluetoothGatt
        if (gatt == null || connectionState != BluetoothProfile.STATE_CONNECTED) {
            isSending.set(false)
            return
        }

        synchronized(writeQueue) {
            if (isSending.get()) {
                writeQueue.add(item)
                return
            }
            isSending.set(true)
        }

        AppLogger.d("EspBleBridgeService", "Sending to ${item.uuid}")
        val characteristic = findCharacteristic(item.uuid) ?: run {
            AppLogger.e("EspBleBridgeService", "Characteristic not found: ${item.uuid}")
            isSending.set(false)
            processNextWrite()
            return
        }

        val success: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                item.data,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = item.data
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            gatt.writeCharacteristic(characteristic)
        }

        if (!success) {
            AppLogger.e("EspBleBridgeService", "Failed to initiate write for ${item.uuid}")
            isSending.set(false)
            processNextWrite()
        } else {
            AppLogger.d("EspBleBridgeService", "Write initiated for ${item.uuid}")
        }
    }

    private fun processNextWrite() {
        synchronized(writeQueue) {
            if (writeQueue.size > 0) {
                write(writeQueue.pop())
            }
        }
    }

    private fun handleBleNotification(uuid: String, payload: String) {
        AppLogger.i("EspBleBridgeService", "Notification from $uuid: $payload")
        val map = com.carlauncher.bridge.core.BlePayloadParser.parse(payload)
        val type = map["type"] ?: return
        when (type) {
            "settings.updated" -> {
                val state = NavigationBridgeRepository.uiState.value.deviceState
                NavigationBridgeRepository.updateDeviceState(state.copy(
                    lightTheme = map["lightTheme"]?.toBooleanStrictOrNull() ?: state.lightTheme,
                    brightness = map["brightness"]?.toIntOrNull() ?: state.brightness,
                    speedLimit = map["speedLimit"]?.toIntOrNull() ?: state.speedLimit
                ))
            }
            "status.snapshot" -> {
                val state = NavigationBridgeRepository.uiState.value.deviceState
                NavigationBridgeRepository.updateDeviceState(state.copy(
                    deviceName = map["deviceName"] ?: state.deviceName,
                    bleConnected = map["bleConnected"]?.toBooleanStrictOrNull() ?: state.bleConnected,
                    wifiConfigured = map["wifiConfigured"]?.toBooleanStrictOrNull() ?: state.wifiConfigured,
                    wifiState = map["wifiState"] ?: state.wifiState,
                    wifiSsid = map["wifiSsid"] ?: state.wifiSsid,
                    wifiIp = map["wifiIp"] ?: state.wifiIp,
                    wifiLastError = map["wifiLastError"] ?: state.wifiLastError,
                    timeSource = map["timeSource"] ?: state.timeSource,
                    timeSynced = map["timeSynced"]?.toBooleanStrictOrNull() ?: state.timeSynced,
                    lastTimeSyncEpoch = map["lastTimeSyncEpoch"]?.toLongOrNull() ?: state.lastTimeSyncEpoch,
                    tzOffsetMinutes = map["tzOffsetMinutes"]?.toIntOrNull() ?: state.tzOffsetMinutes,
                    brightness = map["brightness"]?.toIntOrNull() ?: state.brightness,
                    speedLimit = map["speedLimit"]?.toIntOrNull() ?: state.speedLimit,
                    screen = map["screen"] ?: state.screen,
                    screenLocked = map["screenLocked"]?.toBooleanStrictOrNull() ?: state.screenLocked,
                    navReady = map["navReady"]?.toBooleanStrictOrNull() ?: state.navReady,
                    firmwareVersion = map["firmwareVersion"] ?: state.firmwareVersion
                ))
            }
            "wifi.scan.result" -> {
                val ssid = map["ssid"] ?: return
                NavigationBridgeRepository.addWifiScanResult(
                    com.carlauncher.bridge.model.WifiScanResult(
                        ssid = ssid,
                        rssi = map["rssi"]?.toIntOrNull() ?: 0,
                        auth = map["auth"] ?: "unknown",
                        channel = map["channel"]?.toIntOrNull() ?: 0,
                        hidden = map["hidden"]?.toBooleanStrictOrNull() ?: false
                    )
                )
            }
            "wifi.scan.done" -> {
                NavigationBridgeRepository.setWifiScanning(false)
            }
            "wifi.connect.state" -> {
                if (map["state"] == "connecting") {
                    NavigationBridgeRepository.setWifiConnecting(true)
                }
            }
            "wifi.connect.result" -> {
                NavigationBridgeRepository.setWifiConnecting(false)
            }
        }
    }

    private fun findCharacteristic(uuid: String): BluetoothGattCharacteristic? {
        return bluetoothGatt
            ?.getService(UUID.fromString(BleCharacteristics.SERVICE_UUID))
            ?.characteristics
            ?.firstOrNull { it.uuid.toString() == uuid }
    }

    private fun connect(device: BluetoothDevice) {
        disconnect(closeOnly = true)
        currentDevice = device
        connectionState = BluetoothProfile.STATE_CONNECTING
        NavigationBridgeRepository.updateBleState(
            BleConnectionState.CONNECTING,
            device.name,
            device.address
        )
        bluetoothGatt = device.connectGatt(
            this,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        ).also {
            it.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
        }
    }

    private fun connectToLastDevice() {
        val address = currentSettings.lastDeviceAddress ?: return
        if (!NavigationBridgePermissionHelper.hasBluetoothPermission(this)) return
        val adapter = bluetoothManager.adapter ?: return
        runCatching {
            adapter.getRemoteDevice(address)
        }.getOrNull()?.let(::connect)
    }

    private fun disconnect(closeOnly: Boolean = false) {
        stopPingTimer()
        if (!closeOnly) {
            stopReconnectTimer()
        }

        val lastName = currentDevice?.name
        val lastAddress = currentDevice?.address
        cleanupGattConnection()
        NavigationBridgeRepository.updateBleState(
            BleConnectionState.DISCONNECTED,
            lastName,
            lastAddress
        )
    }

    private fun cleanupGattConnection() {
        isSending.set(false)
        writeQueue.clear()
        connectionState = BluetoothProfile.STATE_DISCONNECTED
        bluetoothGatt?.let { gatt ->
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
        }
        bluetoothGatt = null
        currentDevice = null
    }

    private fun sendPreferencesToDevice() {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            AppLogger.w("EspBleBridgeService", "Cannot send settings: Not connected")
            return
        }
        AppLogger.i("EspBleBridgeService", "Syncing settings to device: $currentSettings")
        val reqId = com.carlauncher.bridge.core.RequestIdGenerator.next()
        val payload = BridgePayloadSerializer.buildSettingsSetCommand(currentSettings, reqId)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_DEVICE_CONTROL, payload.toByteArray()))
    }

    private fun sendTimeSync() {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) return
        val reqId = com.carlauncher.bridge.core.RequestIdGenerator.next()
        val epoch = System.currentTimeMillis() / 1000L
        val tzOffset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        val payload = BridgePayloadSerializer.buildTimeSyncCommand(epoch, tzOffset, reqId)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_DEVICE_CONTROL, payload.toByteArray()))
    }
    
    private fun sendStatusGet() {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) return
        val reqId = com.carlauncher.bridge.core.RequestIdGenerator.next()
        val payload = BridgePayloadSerializer.buildStatusGetCommand(reqId)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_DEVICE_CONTROL, payload.toByteArray()))
    }
    
    private fun sendWifiScan() {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) {
            AppLogger.w("EspBleBridgeService", "Cannot scan WiFi: Not connected")
            return
        }
        AppLogger.i("EspBleBridgeService", "Initiating WiFi scan")
        NavigationBridgeRepository.clearWifiScanResults()
        NavigationBridgeRepository.setWifiScanning(true)
        val reqId = com.carlauncher.bridge.core.RequestIdGenerator.next()
        val payload = BridgePayloadSerializer.buildWifiScanCommand(reqId)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_DEVICE_CONTROL, payload.toByteArray()))
    }
    
    private fun sendWifiConnect(ssid: String, pass: String) {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) return
        NavigationBridgeRepository.setWifiConnecting(true)
        val reqId = com.carlauncher.bridge.core.RequestIdGenerator.next()
        val payload = BridgePayloadSerializer.buildWifiConnectCommand(ssid, pass, reqId)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_DEVICE_CONTROL, payload.toByteArray()))
    }
    
    private fun sendWifiForget() {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) return
        val reqId = com.carlauncher.bridge.core.RequestIdGenerator.next()
        val payload = BridgePayloadSerializer.buildWifiForgetCommand(reqId)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_DEVICE_CONTROL, payload.toByteArray()))
    }

    private fun sendToDevice(data: NavigationData?) {
        if (connectionState != BluetoothProfile.STATE_CONNECTED) return

        val bitmap = data?.actionIcon?.bitmap
        val compressed = bitmap?.let {
            val helper = BitmapHelper()
            helper.toBlackAndWhiteBuffer(helper.compressBitmap(it, Size(64, 62)))
        }
        val iconHash = compressed?.let { NavigationIconHasher.hashSuffix(it) }.orEmpty()
        val navPayload = BridgePayloadSerializer.buildNavigationPayload(data, iconHash)
        write(BleWriteQueue.QueueItem(BleCharacteristics.CHA_NAV, navPayload.toByteArray()))

        if (compressed != null && sentIconRegistry.shouldSend(iconHash)) {
            write(
                BleWriteQueue.QueueItem(
                    uuid = BleCharacteristics.CHA_NAV_TBT_ICON,
                    data = "$iconHash;".toByteArray() + compressed,
                    overwrite = false
                )
            )
        }
    }

    private fun subscribeToLocationUpdates() {
        if (!NavigationBridgePermissionHelper.hasLocationPermission(this)) return
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, this)
        }
    }

    private fun unsubscribeFromLocationUpdates() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        val speed = ceil(location.speed * 3600f / 1000f).toInt()
        NavigationBridgeRepository.updateGpsSpeed(speed)
        if (connectionState == BluetoothProfile.STATE_CONNECTED) {
            write(
                BleWriteQueue.QueueItem(
                    BleCharacteristics.CHA_GPS_SPEED,
                    speed.toString().toByteArray()
                )
            )
        }
    }

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun startPingTimer() {
        stopPingTimer()
        pingTimer = Timer().also { timer ->
            firstPing = true
            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (connectionState != BluetoothProfile.STATE_CONNECTED) return
                    if (firstPing) {
                        sendPreferencesToDevice()
                        firstPing = false
                    } else {
                        sendToDevice(lastNavigationData)
                    }
                }
            }, 1_000L, 25_000L)
        }
    }

    private fun stopPingTimer() {
        pingTimer?.cancel()
        pingTimer?.purge()
        pingTimer = null
    }

    private fun startReconnectTimer() {
        stopReconnectTimer()
        reconnectTimer = Timer().also { timer ->
            timer.schedule(object : TimerTask() {
                override fun run() {
                    if (!NavigationBridgeRepository.uiState.value.serviceRunning) return
                    if (connectionState != BluetoothProfile.STATE_DISCONNECTED) return
                    if (!NavigationBridgePermissionHelper.hasBluetoothPermission(this@EspBleBridgeService)) return
                    if (!NavigationBridgePermissionHelper.isBluetoothEnabled(this@EspBleBridgeService)) return
                    connectToLastDevice()
                }
            }, 15_000L, 15_000L)
        }
    }

    private fun stopReconnectTimer() {
        reconnectTimer?.cancel()
        reconnectTimer?.purge()
        reconnectTimer = null
    }

    private fun persistLastDevice(device: BluetoothDevice?) {
        if (device == null) return
        serviceScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            settingsDataStore.updateSettings(
                settings.copy(
                    navigationBridge = settings.navigationBridge.copy(
                        lastDeviceName = device.name,
                        lastDeviceAddress = device.address
                    )
                )
            )
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LauncherActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.bridge_notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bridge_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    private fun Intent.readBluetoothDevice(): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_DEVICE)
        }
    }
}
