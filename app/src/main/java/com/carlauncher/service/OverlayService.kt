package com.carlauncher.service

import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.view.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.carlauncher.data.WeatherRepository
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.data.models.WeatherInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "car_launcher_overlay"
        const val NOTIFICATION_ID = 1001

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
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var settingsDataStore: SettingsDataStore
    private val weatherRepository = WeatherRepository()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settingsDataStore = SettingsDataStore(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
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
    // Status Overlay
    // ═══════════════════════════════════════

    private fun showStatusOverlay() {
        if (statusView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 16
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
                
                // Fetch weather periodically
                LaunchedEffect(settings.showWeather, settings.weatherCity, settings.weatherApiKey) {
                    if (settings.showWeather && settings.weatherCity.isNotBlank() && settings.weatherApiKey.isNotBlank()) {
                        while (isActive) {
                            weatherInfo = weatherRepository.getWeatherByCity(
                                city = settings.weatherCity,
                                apiKey = settings.weatherApiKey,
                                unit = settings.temperatureUnit
                            )
                            delay(10 * 60 * 1000L) // Refresh every 10 mins
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
                        windowManager?.updateViewLayout(this, params)
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
                    onSettingsClick = {
                        val intent = Intent(this@OverlayService, LauncherActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(intent)
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
        statusView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        statusView = null
    }

    // ═══════════════════════════════════════
    // Assistant Overlay
    // ═══════════════════════════════════════

    private fun showAssistantOverlay() {
        if (assistantView != null) return

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            x = 32
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(OverlayLifecycleOwner())
            setViewTreeSavedStateRegistryOwner(OverlayLifecycleOwner())
            
            setContent {
                val settings by settingsDataStore.settingsFlow.collectAsState(initial = LauncherSettings())
                
                AssistantFloatingButton(
                    settings = settings,
                    onDrag = { dx, dy ->
                        params.x -= dx.toInt() // Subtract because Gravity.END logic reverses x
                        params.y += dy.toInt()
                        windowManager?.updateViewLayout(this, params)
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
    // Notification
    // ═══════════════════════════════════════

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Car Launcher Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status overlay cho Car Launcher"
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
            .setContentTitle("Car Launcher")
            .setContentText("Overlay đang hoạt động")
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
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}

@Composable
fun FloatingStatusWidget(
    settings: LauncherSettings,
    weatherInfo: WeatherInfo?,
    wifiActive: Boolean,
    btActive: Boolean,
    gpsActive: Boolean,
    onDrag: (Float, Float) -> Unit,
    onClockClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(settings.clockFormat) {
        while (true) {
            val sdf = SimpleDateFormat(settings.clockFormat.pattern, Locale.getDefault())
            currentTime = sdf.format(Date())
            delay(1000L)
        }
    }
    
    // Apply user-defined scaling and opacity
    val alpha = settings.widgetOpacity.coerceIn(0f, 1f)
    val scale = settings.widgetScale

    // Padding ensures the shadow doesn't get clipped by the WindowManager bounds
    Box(modifier = Modifier.padding(16.dp * scale)) {
        // Glassmorphism floating widget
        Box(
            modifier = Modifier
                .pointerInput(kotlin.Unit) {
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
                // 1. Clock (Click -> Launch Split Screen)
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 22.sp * scale,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp * scale,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp * scale))
                        .clickable { onClockClick() }
                        .padding(horizontal = 8.dp * scale, vertical = 4.dp * scale)
                )
                Box(modifier = Modifier.height(24.dp * scale).width(1.dp * scale).background(Color.White.copy(alpha = 0.2f)))

                // Section for Settings (Click -> Launch Settings)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp * scale),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp * scale))
                        .clickable { onSettingsClick() }
                        .padding(horizontal = 8.dp * scale, vertical = 4.dp * scale)
                ) {
                    // 2. Weather
                    if (settings.showWeather && weatherInfo != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🌤️", // Simple weather icon mapping fallback
                                fontSize = 20.sp * scale
                            )
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

                    // 3. Status icons with LIVE connectivity states
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        if (settings.showWifi) {
                            Icon(
                                imageVector = if (wifiActive) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                                contentDescription = if (wifiActive) "WiFi On" else "WiFi Off",
                                tint = if (wifiActive) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp * scale)
                            )
                        }
                        if (settings.showBluetooth) {
                            Icon(
                                imageVector = if (btActive) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
                                contentDescription = if (btActive) "Bluetooth On" else "Bluetooth Off",
                                tint = if (btActive) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp * scale)
                            )
                        }
                        if (settings.showGps) {
                            Icon(
                                imageVector = if (gpsActive) Icons.Rounded.LocationOn else Icons.Rounded.LocationOff,
                                contentDescription = if (gpsActive) "GPS On" else "GPS Off",
                                tint = if (gpsActive) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(24.dp * scale)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssistantFloatingButton(
    settings: LauncherSettings,
    onDrag: (Float, Float) -> Unit,
    onClick: () -> Unit
) {
    val alpha = settings.widgetOpacity.coerceIn(0f, 1f)
    val scale = settings.widgetScale
    
    // Padding ensures the shadow doesn't get clipped by the WindowManager bounds
    Box(modifier = Modifier.padding(16.dp * scale)) {
        Box(
            modifier = Modifier
                .size(64.dp * scale)
                .pointerInput(kotlin.Unit) {
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
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = "Assistant",
                tint = Color.White,
                modifier = Modifier.size(32.dp * scale)
            )
        }
    }
}
