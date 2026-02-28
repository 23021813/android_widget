package com.carlauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.carlauncher.data.models.ClockFormat
import com.carlauncher.data.models.WeatherInfo
import com.carlauncher.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

@Composable
fun StatusOverlay(
    clockFormat: ClockFormat,
    showWifi: Boolean,
    showBluetooth: Boolean,
    showGps: Boolean,
    showVolume: Boolean,
    weatherInfo: WeatherInfo?,
    showWeather: Boolean,
    assignableApp: String?,
    onAssignableAppClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // This overlay should NOT intercept touches for the area below
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Settings gear icon (small)
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Cài đặt",
                    tint = TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Weather
            if (showWeather && weatherInfo != null) {
                WeatherWidget(weatherInfo = weatherInfo)
            }

            // Status icons
            StatusIcons(
                showWifi = showWifi,
                showBluetooth = showBluetooth,
                showGps = showGps,
                showVolume = showVolume
            )

            // Assignable app button
            if (assignableApp != null) {
                IconButton(
                    onClick = onAssignableAppClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = "Quick app",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Clock
            ClockWidget(format = clockFormat)
        }
    }
}

@Composable
fun ClockWidget(
    format: ClockFormat,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf("") }

    LaunchedEffect(format) {
        while (true) {
            val sdf = SimpleDateFormat(format.pattern, Locale.getDefault())
            currentTime = sdf.format(Date())
            delay(1000L)
        }
    }

    Text(
        text = currentTime,
        style = if (format == ClockFormat.TIME_ONLY) {
            MaterialTheme.typography.headlineSmall
        } else {
            MaterialTheme.typography.titleMedium
        },
        color = TextPrimary,
        modifier = modifier
    )
}

@Composable
fun StatusIcons(
    showWifi: Boolean,
    showBluetooth: Boolean,
    showGps: Boolean,
    showVolume: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showWifi) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = "WiFi",
                tint = WifiActive,
                modifier = Modifier.size(18.dp)
            )
        }
        if (showBluetooth) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = "Bluetooth",
                tint = BluetoothActive,
                modifier = Modifier.size(18.dp)
            )
        }
        if (showGps) {
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "GPS",
                tint = GpsActive,
                modifier = Modifier.size(18.dp)
            )
        }
        if (showVolume) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Volume",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun WeatherWidget(
    weatherInfo: WeatherInfo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Weather icon based on condition code
        val weatherIcon = when {
            weatherInfo.iconCode.contains("01") -> Icons.Default.WbSunny
            weatherInfo.iconCode.contains("02") -> Icons.Default.WbCloudy
            weatherInfo.iconCode.contains("03") || weatherInfo.iconCode.contains("04") -> Icons.Default.Cloud
            weatherInfo.iconCode.contains("09") || weatherInfo.iconCode.contains("10") -> Icons.Default.WaterDrop
            weatherInfo.iconCode.contains("11") -> Icons.Default.Thunderstorm
            weatherInfo.iconCode.contains("13") -> Icons.Default.AcUnit
            else -> Icons.Default.WbCloudy
        }

        Icon(
            imageVector = weatherIcon,
            contentDescription = weatherInfo.condition,
            tint = AccentOrange,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = weatherInfo.displayTemp,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
    }
}
