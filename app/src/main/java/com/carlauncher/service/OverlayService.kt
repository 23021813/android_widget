package com.carlauncher.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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
        
        showStatusOverlay()
        showAssistantOverlay()
    }

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
                    onDrag = { dx, dy ->
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        windowManager?.updateViewLayout(this, params)
                    },
                    onClockClick = {
                        val f1 = settings.frame1App
                        val f2 = settings.frame2App
                        if (f1 != null && f2 != null) {
                            SplitScreenLauncher.launchSplitScreen(this@OverlayService, f1, f2)
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
        statusView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        assistantView?.let { try { windowManager?.removeView(it) } catch (_: Exception) {} }
        statusView = null
        assistantView = null
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
                        // Drag amount exactly matches screen pixels, no need to scale the movement
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

                    // 3. Status icons (Flat Colors)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp * scale)) {
                        if (settings.showWifi) {
                            Icon(
                                imageVector = Icons.Rounded.Wifi,
                                contentDescription = "WiFi",
                                tint = Color.White, // Flat White
                                modifier = Modifier.size(24.dp * scale)
                            )
                        }
                        if (settings.showBluetooth) {
                            Icon(
                                imageVector = Icons.Rounded.Bluetooth,
                                contentDescription = "Bluetooth",
                                tint = Color.White, // Flat White
                                modifier = Modifier.size(24.dp * scale)
                            )
                        }
                        if (settings.showGps) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = "GPS",
                                tint = Color.White, // Flat White
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
