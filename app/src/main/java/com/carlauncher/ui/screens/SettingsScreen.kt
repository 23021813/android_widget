package com.carlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.carlauncher.data.models.*
import com.carlauncher.ui.components.AppPickerDialog
import com.carlauncher.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    installedApps: List<AppInfo>,
    onSettingsUpdate: (LauncherSettings) -> Unit,
    onLaunchSplitView: () -> Unit,
    onResetDefaults: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var appPickerTarget by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "⚙️ Cài đặt Car Overlay",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary
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
            
            // ═══════════════════════════════════════
            // 🚀 SPLIT-SCREEN LAUNCHER
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "🚀 Mở Ứng Dụng Chia Đôi") {
                    
                    Text("Thiết lập 2 ứng dụng để khởi chạy chế độ chia màn hình:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Frame 1 app
                    SettingsAppSelector(
                        label = "App Trái / Trên",
                        currentApp = settings.frame1App?.let { pkg ->
                            installedApps.find { it.packageName == pkg }?.label ?: pkg
                        } ?: "Chưa chọn",
                        onClick = {
                            appPickerTarget = "frame1"
                            showAppPicker = true
                        },
                        onClear = {
                            onSettingsUpdate(settings.copy(frame1App = null))
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Frame 2 app
                    SettingsAppSelector(
                        label = "App Phải / Dưới",
                        currentApp = settings.frame2App?.let { pkg ->
                            installedApps.find { it.packageName == pkg }?.label ?: pkg
                        } ?: "Chưa chọn",
                        onClick = {
                            appPickerTarget = "frame2"
                            showAppPicker = true
                        },
                        onClear = {
                            onSettingsUpdate(settings.copy(frame2App = null))
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val canLaunch = settings.frame1App != null && settings.frame2App != null
                    Button(
                        onClick = onLaunchSplitView,
                        enabled = canLaunch,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentCyan,
                            contentColor = DarkBackground,
                            disabledContainerColor = DarkSurfaceVariant,
                            disabledContentColor = TextTertiary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalSplit,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Khởi chạy Split View",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
            
            // ═══════════════════════════════════════
            // 🎙️ TRỢ LÝ ẢO
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "🎙️ Trợ lý ảo (Assistant)") {
                    SettingsAppSelector(
                        label = "Ứng dụng Trợ lý",
                        currentApp = settings.assistantApp?.let { pkg ->
                            installedApps.find { it.packageName == pkg }?.label ?: pkg
                        } ?: "Nhấn để chọn",
                        onClick = {
                            appPickerTarget = "assistant"
                            showAppPicker = true
                        },
                        onClear = {
                            onSettingsUpdate(settings.copy(assistantApp = null))
                        }
                    )
                }
            }

            // ═══════════════════════════════════════
            // ⚙️ HỆ THỐNG
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "⚙️ Hệ thống") {
                    SettingsToggle(
                        label = "Khởi động cùng xe (Tự động chạy Overlay khi bật máy)",
                        checked = settings.autoStartOnBoot,
                        onCheckedChange = {
                            onSettingsUpdate(settings.copy(autoStartOnBoot = it))
                        }
                    )
                }
            }
            
            // ═══════════════════════════════════════
            // 🎨 GIAO DIỆN WIDGET
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "🎨 Giao diện Widget Nổi") {
                    // Size slider
                    SettingsSlider(
                        label = "Kích thước Widget",
                        value = settings.widgetScale,
                        valueRange = 0.5f..1.5f,
                        displayValue = "${(settings.widgetScale * 100).toInt()}%",
                        onValueChange = {
                            onSettingsUpdate(settings.copy(widgetScale = it))
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Opacity slider
                    SettingsSlider(
                        label = "Độ mờ nền (Opacity)",
                        value = settings.widgetOpacity,
                        valueRange = 0.0f..1.0f,
                        displayValue = "${(settings.widgetOpacity * 100).toInt()}%",
                        onValueChange = {
                            onSettingsUpdate(settings.copy(widgetOpacity = it))
                        }
                    )
                }
            }

            // ═══════════════════════════════════════
            // 🕐 ĐỒNG HỒ & TRẠNG THÁI
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "🕐 Đồng hồ & Trạng thái") {
                    // Clock format
                    SettingsDropdown(
                        label = "Định dạng đồng hồ",
                        value = settings.clockFormat.label,
                        options = ClockFormat.entries.map { it.label },
                        onSelect = { label ->
                            val format = ClockFormat.entries.first { it.label == label }
                            onSettingsUpdate(settings.copy(clockFormat = format))
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Toggle status icons
                    SettingsToggle(
                        label = "Hiện WiFi",
                        checked = settings.showWifi,
                        onCheckedChange = {
                            onSettingsUpdate(settings.copy(showWifi = it))
                        }
                    )
                    SettingsToggle(
                        label = "Hiện Bluetooth",
                        checked = settings.showBluetooth,
                        onCheckedChange = {
                            onSettingsUpdate(settings.copy(showBluetooth = it))
                        }
                    )
                    SettingsToggle(
                        label = "Hiện GPS",
                        checked = settings.showGps,
                        onCheckedChange = {
                            onSettingsUpdate(settings.copy(showGps = it))
                        }
                    )
                }
            }

            // ═══════════════════════════════════════
            // 🌤️ THỜI TIẾT
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "🌤️ Thời tiết") {
                    SettingsToggle(
                        label = "Hiện thời tiết",
                        checked = settings.showWeather,
                        onCheckedChange = {
                            onSettingsUpdate(settings.copy(showWeather = it))
                        }
                    )

                    if (settings.showWeather) {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Location mode
                        SettingsDropdown(
                            label = "Nguồn vị trí",
                            value = settings.weatherLocationMode.label,
                            options = WeatherLocationMode.entries.map { it.label },
                            onSelect = { label ->
                                val mode = WeatherLocationMode.entries.first { it.label == label }
                                onSettingsUpdate(settings.copy(weatherLocationMode = mode))
                            }
                        )

                        // Manual city input
                        if (settings.weatherLocationMode == WeatherLocationMode.MANUAL) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsTextField(
                                label = "Thành phố",
                                value = settings.weatherCity,
                                onValueChange = {
                                    onSettingsUpdate(settings.copy(weatherCity = it))
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Temperature unit
                        SettingsDropdown(
                            label = "Đơn vị",
                            value = settings.temperatureUnit.label,
                            options = TemperatureUnit.entries.map { it.label },
                            onSelect = { label ->
                                val unit = TemperatureUnit.entries.first { it.label == label }
                                onSettingsUpdate(settings.copy(temperatureUnit = unit))
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // API Key
                        SettingsTextField(
                            label = "API Key (OpenWeatherMap)",
                            value = settings.weatherApiKey,
                            onValueChange = {
                                onSettingsUpdate(settings.copy(weatherApiKey = it))
                            },
                            isPassword = !showApiKey,
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(
                                        imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // ═══════════════════════════════════════
            // ℹ️ THÔNG TIN
            // ═══════════════════════════════════════
            item {
                SettingsSection(title = "ℹ️ Thông tin") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                        Text("3.0.0", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset button
                    OutlinedButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AccentRed
                        )
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset về mặc định")
                    }
                }
            }
        }
    }

    // App picker for settings
    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            title = when (appPickerTarget) {
                "frame1" -> "Chọn App Trái / Trên"
                "frame2" -> "Chọn App Phải / Dưới"
                else -> "Chọn ứng dụng"
            },
            onAppSelected = { app ->
                when (appPickerTarget) {
                    "frame1" -> onSettingsUpdate(settings.copy(frame1App = app.packageName))
                    "frame2" -> onSettingsUpdate(settings.copy(frame2App = app.packageName))
                    "assistant" -> onSettingsUpdate(settings.copy(assistantApp = app.packageName))
                }
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

// ═══════════════════════════════════════
// Reusable Settings Components
// ═══════════════════════════════════════

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = AccentCyan,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentCyan,
                checkedThumbColor = TextPrimary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = DarkSurfaceVariant,
                    focusedContainerColor = DarkSurfaceVariant,
                    unfocusedBorderColor = DividerColor,
                    focusedBorderColor = AccentCyan,
                    unfocusedTextColor = TextPrimary,
                    focusedTextColor = TextPrimary
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(displayValue, style = MaterialTheme.typography.bodyMedium, color = AccentCyan)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = DividerColor
            )
        )
    }
}

@Composable
fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = trailingIcon,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = DarkSurfaceVariant,
                focusedContainerColor = DarkSurfaceVariant,
                unfocusedBorderColor = DividerColor,
                focusedBorderColor = AccentCyan,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
                cursorColor = AccentCyan
            )
        )
    }
}

@Composable
fun SettingsAppSelector(
    label: String,
    currentApp: String,
    onClick: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentApp,
                style = MaterialTheme.typography.bodyLarge,
                color = if (currentApp == "Chưa chọn") TextTertiary else TextPrimary
            )
            Row {
                if (currentApp != "Chưa chọn") {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Xóa",
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
