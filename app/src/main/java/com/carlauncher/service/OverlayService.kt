package com.carlauncher.service

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.carlauncher.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.*
import androidx.savedstate.*
import com.carlauncher.LauncherActivity
import com.carlauncher.SplitScreenProxyActivity
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.AssistantIcon
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.data.models.WeatherInfo
import com.carlauncher.data.WeatherRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "car_launcher_overlay"
        const val NOTIFICATION_ID = 1001
        const val ACTION_TOGGLE_CLICK_THROUGH = "com.carlauncher.TOGGLE_CLICK_THROUGH"

        // In-memory flag: reset on every process start (boot)
        var bootSplitDone = false

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }

    private var windowManager: WindowManager? = null
    private var statusView: View? = null
    private var assistantView: View? = null
    private var dragHandleView: View? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var settingsDataStore: SettingsDataStore
    private val weatherRepository = WeatherRepository()

    // Position save debounce
    private var positionSaveJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Auto split-view on first boot
        serviceScope.launch {
            val settings = settingsDataStore.settingsFlow.first()
            if (settings.autoSplitOnBoot && !bootSplitDone
                && settings.frame1App != null && settings.frame2App != null) {
                delay(5000L) // Wait for system to stabilize
                bootSplitDone = true
                val intent = Intent(this@OverlayService, SplitScreenProxyActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("pkg1", settings.frame1App)
                    putExtra("pkg2", settings.frame2App)
                }
                startActivity(intent)
            }
        }

        // Observe settings changes for dynamic widget visibility
        serviceScope.launch {
            settingsDataStore.settingsFlow.collectLatest { settings ->
                // Status Widget
                if (settings.showStatusWidget && statusView == null) {
                    showStatusOverlay()
                } else if (!settings.showStatusWidget && statusView != null) {
                    removeStatusOverlay()
                }

                // Assistant Widget
                if (settings.showAssistantWidget && assistantView == null) {
                    showAssistantOverlay()
                } else if (!settings.showAssistantWidget && assistantView != null) {
                    removeAssistantOverlay()
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // Connectivity State Helpers
    // ═══════════════════════════════════════

    private fun isWifiEnabled(): Boolean {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    private fun isBluetoothEnabled(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return try {
            bluetoothManager?.adapter?.isEnabled == true
        } catch (e: SecurityException) {
            false
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    // ═══════════════════════════════════════
    // Position Save (debounced)
    // ═══════════════════════════════════════

    private fun saveWidgetPosition(isStatus: Boolean, x: Int, y: Int) {
        positionSaveJob?.cancel()
        positionSaveJob = serviceScope.launch {
            delay(500L)
            val current = settingsDataStore.settingsFlow.first()
            val updated = if (isStatus) {
                current.copy(statusWidgetX = x, statusWidgetY = y)
            } else {
                current.copy(assistantWidgetX = x, assistantWidgetY = y)
            }
            settingsDataStore.updateSettings(updated)
        }
    }

    // ═══════════════════════════════════════
    // Build LayoutParams flags
    // ═══════════════════════════════════════

    private fun buildOverlayFlags(clickThrough: Boolean = false, overlap: Boolean = false): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        if (clickThrough) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (overlap) {
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }
        return flags
    }

    // ═══════════════════════════════════════
    // Status Overlay
    // ═══════════════════════════════════════

    private fun showStatusOverlay() {
        if (statusView != null) return

        val initialSettings = runBlocking { settingsDataStore.settingsFlow.first() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            buildOverlayFlags(
                clickThrough = initialSettings.clockClickThrough,
                overlap = initialSettings.allowOverlapSystemBars
            ),
            PixelFormat.TRANSLUCENT
        ).apply {
            if (initialSettings.statusWidgetX != Int.MIN_VALUE) {
                gravity = Gravity.TOP or Gravity.START
                x = initialSettings.statusWidgetX
                y = initialSettings.statusWidgetY
            } else {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 16
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(OverlayLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(OverlayLifecycleOwner())

            setContent {
                val settings by settingsDataStore.settingsFlow.collectAsState(initial = LauncherSettings())

                var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }

                // Read live connectivity states, refresh every 3 seconds
                var wifiActive by remember { mutableStateOf(isWifiEnabled()) }
                var btActive by remember { mutableStateOf(isBluetoothEnabled()) }
                var gpsActive by remember { mutableStateOf(isLocationEnabled()) }

                LaunchedEffect(Unit) {
                    while (isActive) {
                        wifiActive = isWifiEnabled()
                        btActive = isBluetoothEnabled()
                        gpsActive = isLocationEnabled()
                        delay(3000L)
                    }
                }

                // Update flags dynamically when click-through or overlap changes
                LaunchedEffect(settings.clockClickThrough, settings.allowOverlapSystemBars) {
                    params.flags = buildOverlayFlags(settings.clockClickThrough, settings.allowOverlapSystemBars)
                    try { windowManager?.updateViewLayout(this@apply, params) } catch (_: Exception) {}
                    // Show/hide drag handle based on click-through
                    if (settings.clockClickThrough) {
                        showDragHandle(params)
                    } else {
                        removeDragHandle()
                    }
                }

                // Fetch weather periodically
                LaunchedEffect(settings.showWeather, settings.weatherCity, settings.weatherApiKey) {
                    if (settings.showWeather && settings.weatherCity.isNotBlank() && settings.weatherApiKey.isNotBlank()) {
                        while (isActive) {
                            weatherInfo = weatherRepository.getWeatherByCity(
                                city = settings.weatherCity,
                                apiKey = settings.weatherApiKey,
                                unit = settings.temperatureUnit
                            )
                            delay(10 * 60 * 1000L)
                        }
                    } else {
                        weatherInfo = null
                    }
                }

                FloatingStatusWidget(
                    settings = settings,
                    weatherInfo = weatherInfo,
                    wifiActive = wifiActive,
                    btActive = btActive,
                    gpsActive = gpsActive,
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        // Ensure Gravity.START so x/y coords are absolute
                        params.gravity = Gravity.TOP or Gravity.START
                        windowManager?.updateViewLayout(this, params)
                        saveWidgetPosition(true, params.x, params.y)
                    },
                    onClockClick = {
                        val f1 = settings.frame1App
                        val f2 = settings.frame2App
                        if (f1 != null && f2 != null) {
                            val intent = Intent(this@OverlayService, SplitScreenProxyActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                putExtra("pkg1", f1)
                                putExtra("pkg2", f2)
                            }
                            startActivity(intent)
                        }
                    },
                    onClockLongPress = {
                        val intent = Intent(this@OverlayService, LauncherActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    },
                    onWeatherClick = {
                        val intent = Intent(this@OverlayService, LauncherActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
                    },
                    onWifiClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    onBluetoothClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        } catch (e: Exception) { e.printStackTrace() }
                    },
                    onLocationClick = {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(intent)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                )
            }
        }

        statusView = composeView
        try {
            windowManager?.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeStatusOverlay() {
        removeDragHandle()
        statusView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        statusView = null
    }

    // ═══════════════════════════════════════
    // Click-Through Drag Handle (small invisible touch target)
    // ═══════════════════════════════════════

    private fun showDragHandle(statusParams: WindowManager.LayoutParams) {
        if (dragHandleView != null) return

        val handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = statusParams.x
            y = statusParams.y
        }

        val handleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(OverlayLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(OverlayLifecycleOwner())
            setContent {
                // Large invisible touch target (48.dp) with small visual dot (12.dp)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset ->
                                    change.consume()
                                    statusParams.x += dragAmount.x.toInt()
                                    statusParams.y += dragAmount.y.toInt()
                                    statusParams.gravity = Gravity.TOP or Gravity.START
                                    try {
                                        statusView?.let { windowManager?.updateViewLayout(it, statusParams) }
                                        handleParams.x = statusParams.x
                                        handleParams.y = statusParams.y
                                        windowManager?.updateViewLayout(this@apply, handleParams)
                                    } catch (_: Exception) {}
                                    saveWidgetPosition(true, statusParams.x, statusParams.y)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    )
                }
            }
        }

        dragHandleView = handleView
        try {
            windowManager?.addView(handleView, handleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeDragHandle() {
        dragHandleView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        dragHandleView = null
    }

    // ═══════════════════════════════════════
    // Assistant Overlay
    // ═══════════════════════════════════════

    private fun showAssistantOverlay() {
        if (assistantView != null) return

        val initialSettings = runBlocking { settingsDataStore.settingsFlow.first() }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            buildOverlayFlags(overlap = initialSettings.allowOverlapSystemBars),
            PixelFormat.TRANSLUCENT
        ).apply {
            if (initialSettings.assistantWidgetX != Int.MIN_VALUE) {
                gravity = Gravity.TOP or Gravity.START
                x = initialSettings.assistantWidgetX
                y = initialSettings.assistantWidgetY
            } else {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                x = 32
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(OverlayLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(OverlayLifecycleOwner())

            setContent {
                val settings by settingsDataStore.settingsFlow.collectAsState(initial = LauncherSettings())

                // Update flags dynamically when overlap changes
                LaunchedEffect(settings.allowOverlapSystemBars) {
                    params.flags = buildOverlayFlags(overlap = settings.allowOverlapSystemBars)
                    try { windowManager?.updateViewLayout(this@apply, params) } catch (_: Exception) {}
                }

                AssistantFloatingButton(
                    settings = settings,
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        params.gravity = Gravity.TOP or Gravity.START
                        windowManager?.updateViewLayout(this, params)
                        saveWidgetPosition(false, params.x, params.y)
                    },
                    onClick = {
                        try {
                            if (settings.assistantApp != null) {
                                SplitScreenLauncher.launchApp(this@OverlayService, settings.assistantApp!!)
                            } else {
                                val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(intent)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onLongPress = {
                        try {
                            if (settings.assistantLongPressApp != null) {
                                SplitScreenLauncher.launchApp(this@OverlayService, settings.assistantLongPressApp!!)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onDoubleTap = {
                        try {
                            if (settings.assistantDoubleTapApp != null) {
                                SplitScreenLauncher.launchApp(this@OverlayService, settings.assistantDoubleTapApp!!)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }
        }

        assistantView = composeView
        try {
            windowManager?.addView(composeView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeAssistantOverlay() {
        assistantView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        assistantView = null
    }

    // ═══════════════════════════════════════
    // Notification (with click-through toggle action)
    // ═══════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, LauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        removeStatusOverlay()
        removeAssistantOverlay()
        serviceScope.cancel()
        super.onDestroy()
    }
}

// Minimal Lifecycle & SavedStateRegistry Owner for Compose in Service
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}

// ═══════════════════════════════════════
// Helper: Resolve AssistantIcon to ImageVector
// ═══════════════════════════════════════
fun resolveAssistantIcon(icon: AssistantIcon): ImageVector {
    return when (icon) {
        AssistantIcon.MIC -> Icons.Rounded.Mic
        AssistantIcon.HEADSET -> Icons.Rounded.Headset
        AssistantIcon.ASSISTANT -> Icons.Rounded.SmartToy
        AssistantIcon.RECORD -> Icons.Rounded.FiberManualRecord
        AssistantIcon.VOICE -> Icons.Rounded.RecordVoiceOver
        AssistantIcon.CHAT -> Icons.Rounded.Chat
        AssistantIcon.STAR -> Icons.Rounded.Star
        AssistantIcon.HOME -> Icons.Rounded.Home
        AssistantIcon.MUSIC -> Icons.Rounded.MusicNote
        AssistantIcon.PHONE -> Icons.Rounded.Phone
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingStatusWidget(
    settings: LauncherSettings,
    weatherInfo: WeatherInfo?,
    wifiActive: Boolean,
    btActive: Boolean,
    gpsActive: Boolean,
    onDrag: (Float, Float) -> Unit,
    onClockClick: () -> Unit,
    onClockLongPress: () -> Unit,
    onWeatherClick: () -> Unit,
    onWifiClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(settings.clockFormat) {
        while (true) {
            val sdf = SimpleDateFormat(settings.clockFormat.pattern, Locale.getDefault())
            currentTime = sdf.format(Date())
            delay(1000L)
        }
    }

    val alpha = settings.widgetOpacity.coerceIn(0f, 1f)
    val scale = settings.statusWidgetScale

    Box(modifier = Modifier.padding(16.dp * scale)) {
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .border(1.dp * scale, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp * scale))
                .clip(RoundedCornerShape(24.dp * scale))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF161B22).copy(alpha = alpha),
                            Color(0xFF0D1117).copy(alpha = alpha),
                            Color(0xFF161B22).copy(alpha = alpha)
                        )
                    )
                )
                .padding(horizontal = 24.dp * scale, vertical = 12.dp * scale),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp * scale)
            ) {
                // 1. Clock
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 22.sp * scale,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp * scale,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp * scale))
                        .combinedClickable(
                            onClick = { onClockClick() },
                            onLongClick = { onClockLongPress() }
                        )
                        .padding(horizontal = 8.dp * scale, vertical = 4.dp * scale)
                )

                Box(modifier = Modifier.height(24.dp * scale).width(1.dp * scale).background(Color.White.copy(alpha = 0.2f)))

                // 2. Weather
                if (settings.showWeather && weatherInfo != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp * scale))
                            .clickable { onWeatherClick() }
                            .padding(horizontal = 4.dp * scale, vertical = 4.dp * scale)
                    ) {
                        Text(text = "🌤️", fontSize = 20.sp * scale)
                        Spacer(modifier = Modifier.width(4.dp * scale))
                        Text(
                            text = "${weatherInfo.temperature.toInt()}°",
                            color = Color.White,
                            fontSize = 20.sp * scale,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(modifier = Modifier.height(24.dp * scale).width(1.dp * scale).background(Color.White.copy(alpha = 0.2f)))
                }

                // 3. Status icons
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                    if (settings.showWifi) {
                        Icon(
                            imageVector = if (wifiActive) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = if (wifiActive) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp * scale)
                                .clip(CircleShape)
                                .clickable { onWifiClick() }
                        )
                    }
                    if (settings.showBluetooth) {
                        Icon(
                            imageVector = if (btActive) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                            contentDescription = null,
                            tint = if (btActive) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp * scale)
                                .clip(CircleShape)
                                .clickable { onBluetoothClick() }
                        )
                    }
                    if (settings.showGps) {
                        Icon(
                            imageVector = if (gpsActive) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
                            contentDescription = null,
                            tint = if (gpsActive) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(24.dp * scale)
                                .clip(CircleShape)
                                .clickable { onLocationClick() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssistantFloatingButton(
    settings: LauncherSettings,
    onDrag: (Float, Float) -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDoubleTap: () -> Unit
) {
    val alpha = settings.widgetOpacity.coerceIn(0f, 1f)
    val scale = settings.assistantButtonScale
    val iconVector = resolveAssistantIcon(settings.assistantIcon)

    Box(modifier = Modifier.padding(16.dp * scale)) {
        Box(
            modifier = Modifier
                .size(64.dp * scale)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .border(1.dp * scale, Color.White.copy(alpha = 0.15f), CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF161B22).copy(alpha = alpha),
                            Color(0xFF0D1117).copy(alpha = alpha)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() },
                        onDoubleTap = { onDoubleTap() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = stringResource(R.string.assistant),
                tint = Color.White,
                modifier = Modifier.size(32.dp * scale)
            )
        }
    }
}
