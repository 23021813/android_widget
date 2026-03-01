package com.carlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carlauncher.R
import com.carlauncher.data.models.*
import com.carlauncher.service.resolveAssistantIcon
import com.carlauncher.ui.components.AppPickerDialog
import com.carlauncher.ui.theme.*
import com.carlauncher.update.UpdateInfo
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: LauncherSettings,
    installedApps: List<AppInfo>,
    updateInfo: UpdateInfo? = null,
    onSettingsUpdate: (LauncherSettings) -> Unit,
    onLaunchSplitView: () -> Unit,
    onResetDefaults: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }
    var appPickerTarget by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }

    val clockFormatLabels = mapOf(
        ClockFormat.TIME_ONLY to stringResource(R.string.clock_time_only),
        ClockFormat.DATE_TIME to stringResource(R.string.clock_date_time),
        ClockFormat.FULL to stringResource(R.string.clock_full)
    )
    val weatherModeLabels = mapOf(
        WeatherLocationMode.GPS to stringResource(R.string.weather_gps),
        WeatherLocationMode.MANUAL to stringResource(R.string.weather_manual)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground, titleContentColor = TextPrimary)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ═══ SPLIT-SCREEN ═══
            item {
                SettingsSection(title = stringResource(R.string.section_split_screen)) {
                    Text(stringResource(R.string.split_screen_hint), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsAppSelector(
                        label = stringResource(R.string.app_left_top),
                        currentApp = settings.frame1App?.let { pkg -> installedApps.find { it.packageName == pkg }?.label ?: pkg } ?: stringResource(R.string.not_selected),
                        onClick = { appPickerTarget = "frame1"; showAppPicker = true },
                        onClear = { onSettingsUpdate(settings.copy(frame1App = null)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsAppSelector(
                        label = stringResource(R.string.app_right_bottom),
                        currentApp = settings.frame2App?.let { pkg -> installedApps.find { it.packageName == pkg }?.label ?: pkg } ?: stringResource(R.string.not_selected),
                        onClick = { appPickerTarget = "frame2"; showAppPicker = true },
                        onClear = { onSettingsUpdate(settings.copy(frame2App = null)) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsToggle(
                        label = stringResource(R.string.auto_split_on_boot),
                        checked = settings.autoSplitOnBoot,
                        onCheckedChange = { onSettingsUpdate(settings.copy(autoSplitOnBoot = it)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val canLaunch = settings.frame1App != null && settings.frame2App != null
                    Button(
                        onClick = onLaunchSplitView,
                        enabled = canLaunch,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground, disabledContainerColor = DarkSurfaceVariant, disabledContentColor = TextTertiary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VerticalSplit, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.launch_split_view), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // ═══ ASSISTANT ═══
            item {
                SettingsSection(title = stringResource(R.string.section_assistant)) {
                    SettingsAppSelector(
                        label = stringResource(R.string.assistant_app) + " (Tap)",
                        currentApp = settings.assistantApp?.let { pkg -> installedApps.find { it.packageName == pkg }?.label ?: pkg } ?: stringResource(R.string.tap_to_select),
                        onClick = { appPickerTarget = "assistant"; showAppPicker = true },
                        onClear = { onSettingsUpdate(settings.copy(assistantApp = null)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsAppSelector(
                        label = stringResource(R.string.assistant_long_press_app),
                        currentApp = settings.assistantLongPressApp?.let { pkg -> installedApps.find { it.packageName == pkg }?.label ?: pkg } ?: stringResource(R.string.tap_to_select),
                        onClick = { appPickerTarget = "assistantLongPress"; showAppPicker = true },
                        onClear = { onSettingsUpdate(settings.copy(assistantLongPressApp = null)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsAppSelector(
                        label = stringResource(R.string.assistant_double_tap_app),
                        currentApp = settings.assistantDoubleTapApp?.let { pkg -> installedApps.find { it.packageName == pkg }?.label ?: pkg } ?: stringResource(R.string.tap_to_select),
                        onClick = { appPickerTarget = "assistantDoubleTap"; showAppPicker = true },
                        onClear = { onSettingsUpdate(settings.copy(assistantDoubleTapApp = null)) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Icon Picker
                    Text(stringResource(R.string.assistant_icon_label), color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.height(120.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(AssistantIcon.entries.toList()) { iconEnum ->
                            val isSelected = settings.assistantIcon == iconEnum
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) AccentCyan.copy(alpha = 0.3f) else DarkSurfaceVariant)
                                    .clickable { onSettingsUpdate(settings.copy(assistantIcon = iconEnum)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = resolveAssistantIcon(iconEnum),
                                    contentDescription = iconEnum.displayName,
                                    tint = if (isSelected) AccentCyan else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ═══ SYSTEM ═══
            item {
                SettingsSection(title = stringResource(R.string.section_system)) {
                    SettingsToggle(label = stringResource(R.string.auto_start_on_boot), checked = settings.autoStartOnBoot, onCheckedChange = { onSettingsUpdate(settings.copy(autoStartOnBoot = it)) })
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggle(label = stringResource(R.string.show_status_widget), checked = settings.showStatusWidget, onCheckedChange = { onSettingsUpdate(settings.copy(showStatusWidget = it)) })
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggle(label = stringResource(R.string.show_assistant_widget), checked = settings.showAssistantWidget, onCheckedChange = { onSettingsUpdate(settings.copy(showAssistantWidget = it)) })
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggle(label = stringResource(R.string.allow_overlap_system_bars), checked = settings.allowOverlapSystemBars, onCheckedChange = { onSettingsUpdate(settings.copy(allowOverlapSystemBars = it)) })
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggle(label = stringResource(R.string.clock_click_through), checked = settings.clockClickThrough, onCheckedChange = { onSettingsUpdate(settings.copy(clockClickThrough = it)) })
                }
            }

            // ═══ WIDGET APPEARANCE ═══
            item {
                SettingsSection(title = stringResource(R.string.section_widget_appearance)) {
                    SettingsSlider(
                        label = stringResource(R.string.status_widget_size),
                        value = settings.statusWidgetScale,
                        valueRange = 0.5f..2.0f,
                        displayValue = "${(settings.statusWidgetScale * 100).toInt()}%",
                        onValueChange = { onSettingsUpdate(settings.copy(statusWidgetScale = it)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSlider(
                        label = stringResource(R.string.assistant_button_size),
                        value = settings.assistantButtonScale,
                        valueRange = 0.5f..2.0f,
                        displayValue = "${(settings.assistantButtonScale * 100).toInt()}%",
                        onValueChange = { onSettingsUpdate(settings.copy(assistantButtonScale = it)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSlider(
                        label = stringResource(R.string.widget_opacity),
                        value = settings.widgetOpacity,
                        valueRange = 0.0f..1.0f,
                        displayValue = "${(settings.widgetOpacity * 100).toInt()}%",
                        onValueChange = { onSettingsUpdate(settings.copy(widgetOpacity = it)) }
                    )
                }
            }

            // ═══ CLOCK & STATUS ═══
            item {
                SettingsSection(title = stringResource(R.string.section_clock_status)) {
                    SettingsDropdown(
                        label = stringResource(R.string.clock_format),
                        value = clockFormatLabels[settings.clockFormat] ?: "",
                        options = ClockFormat.entries.map { clockFormatLabels[it] ?: it.name },
                        onSelect = { label -> val format = ClockFormat.entries.first { clockFormatLabels[it] == label }; onSettingsUpdate(settings.copy(clockFormat = format)) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsToggle(label = stringResource(R.string.show_wifi), checked = settings.showWifi, onCheckedChange = { onSettingsUpdate(settings.copy(showWifi = it)) })
                    SettingsToggle(label = stringResource(R.string.show_bluetooth), checked = settings.showBluetooth, onCheckedChange = { onSettingsUpdate(settings.copy(showBluetooth = it)) })
                    SettingsToggle(label = stringResource(R.string.show_gps), checked = settings.showGps, onCheckedChange = { onSettingsUpdate(settings.copy(showGps = it)) })
                }
            }

            // ═══ WEATHER ═══
            item {
                SettingsSection(title = stringResource(R.string.section_weather)) {
                    SettingsToggle(label = stringResource(R.string.show_weather), checked = settings.showWeather, onCheckedChange = { onSettingsUpdate(settings.copy(showWeather = it)) })
                    if (settings.showWeather) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsDropdown(
                            label = stringResource(R.string.location_source),
                            value = weatherModeLabels[settings.weatherLocationMode] ?: "",
                            options = WeatherLocationMode.entries.map { weatherModeLabels[it] ?: it.name },
                            onSelect = { label -> val mode = WeatherLocationMode.entries.first { weatherModeLabels[it] == label }; onSettingsUpdate(settings.copy(weatherLocationMode = mode)) }
                        )
                        if (settings.weatherLocationMode == WeatherLocationMode.MANUAL) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SettingsTextField(label = stringResource(R.string.city), value = settings.weatherCity, onValueChange = { onSettingsUpdate(settings.copy(weatherCity = it)) })
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsDropdown(
                            label = stringResource(R.string.unit),
                            value = settings.temperatureUnit.label,
                            options = TemperatureUnit.entries.map { it.label },
                            onSelect = { label -> val unit = TemperatureUnit.entries.first { it.label == label }; onSettingsUpdate(settings.copy(temperatureUnit = unit)) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsTextField(
                            label = stringResource(R.string.api_key_label),
                            value = settings.weatherApiKey,
                            onValueChange = { onSettingsUpdate(settings.copy(weatherApiKey = it)) },
                            isPassword = !showApiKey,
                            trailingIcon = {
                                IconButton(onClick = { showApiKey = !showApiKey }) {
                                    Icon(imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null, tint = TextSecondary)
                                }
                            }
                        )
                    }
                }
            }

            // ═══ LANGUAGE ═══
            item {
                SettingsSection(title = stringResource(R.string.section_language)) {
                    SettingsDropdown(
                        label = stringResource(R.string.language_label),
                        value = settings.appLanguage.displayName,
                        options = AppLanguage.entries.map { it.displayName },
                        onSelect = { displayName -> val lang = AppLanguage.entries.first { it.displayName == displayName }; onSettingsUpdate(settings.copy(appLanguage = lang)) }
                    )
                }
            }

            // ═══ HELP & GESTURE GUIDE ═══
            item {
                SettingsSection(title = stringResource(R.string.section_help)) {
                    HelpRow(emoji = "🕐", title = stringResource(R.string.help_clock_title), lines = listOf(
                        stringResource(R.string.help_clock_tap),
                        stringResource(R.string.help_clock_long),
                        stringResource(R.string.help_clock_drag)
                    ))
                    Spacer(modifier = Modifier.height(12.dp))
                    HelpRow(emoji = "🌤️", title = stringResource(R.string.help_weather_title), lines = listOf(
                        stringResource(R.string.help_weather_tap)
                    ))
                    Spacer(modifier = Modifier.height(12.dp))
                    HelpRow(emoji = "📶", title = stringResource(R.string.help_icons_title), lines = listOf(
                        stringResource(R.string.help_icons_tap)
                    ))
                    Spacer(modifier = Modifier.height(12.dp))
                    HelpRow(emoji = "🎙️", title = stringResource(R.string.help_voice_title), lines = listOf(
                        stringResource(R.string.help_voice_tap),
                        stringResource(R.string.help_voice_long),
                        stringResource(R.string.help_voice_double),
                        stringResource(R.string.help_voice_drag)
                    ))
                    Spacer(modifier = Modifier.height(12.dp))
                    HelpRow(emoji = "👻", title = stringResource(R.string.help_clickthrough_title), lines = listOf(
                        stringResource(R.string.help_clickthrough_desc)
                    ))
                }
            }

            // ═══ ABOUT ═══
            item {
                SettingsSection(title = stringResource(R.string.section_about)) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.version), color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                            Text("1.3.2", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                        }
                        
                        if (updateInfo != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.update_available_badge, updateInfo.versionName),
                                color = AccentGreen,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (updateInfo != null) AccentCyan else TextPrimary 
                        )
                    ) {
                        Icon(if (updateInfo != null) Icons.Default.NewReleases else Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (updateInfo != null) stringResource(R.string.update_now) else "Check for Updates")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onResetDefaults,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed)
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.reset_defaults))
                    }
                }
            }
        }
    }

    // App picker dialog
    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            title = when (appPickerTarget) {
                "frame1" -> stringResource(R.string.choose_app_left)
                "frame2" -> stringResource(R.string.choose_app_right)
                else -> stringResource(R.string.choose_app)
            },
            onAppSelected = { app ->
                when (appPickerTarget) {
                    "frame1" -> onSettingsUpdate(settings.copy(frame1App = app.packageName))
                    "frame2" -> onSettingsUpdate(settings.copy(frame2App = app.packageName))
                    "assistant" -> onSettingsUpdate(settings.copy(assistantApp = app.packageName))
                    "assistantLongPress" -> onSettingsUpdate(settings.copy(assistantLongPressApp = app.packageName))
                    "assistantDoubleTap" -> onSettingsUpdate(settings.copy(assistantDoubleTapApp = app.packageName))
                }
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

// ═══════════════════════════════════════
// Help Row Component
// ═══════════════════════════════════════

@Composable
fun HelpRow(emoji: String, title: String, lines: List<String>) {
    Column {
        Text(text = "$emoji  $title", color = AccentCyan, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        lines.forEach { line ->
            Text(text = "  •  $line", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ═══════════════════════════════════════
// Reusable Settings Components
// ═══════════════════════════════════════

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = DarkSurface, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = AccentCyan)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onCheckedChange(!checked) }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = AccentCyan))
    }
}

@Composable
fun SettingsSlider(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, displayValue: String, onValueChange: (Float) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(text = displayValue, style = MaterialTheme.typography.bodyMedium, color = AccentCyan)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan, inactiveTrackColor = DarkSurfaceVariant))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = value, onValueChange = {}, readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = DarkSurfaceVariant, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface),
                shape = RoundedCornerShape(8.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DarkSurface)) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option, color = if (option == value) AccentCyan else TextPrimary) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}

@Composable
fun SettingsTextField(label: String, value: String, onValueChange: (String) -> Unit, isPassword: Boolean = false, trailingIcon: @Composable (() -> Unit)? = null) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), trailingIcon = trailingIcon,
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentCyan, unfocusedBorderColor = DarkSurfaceVariant, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedContainerColor = DarkSurface, unfocusedContainerColor = DarkSurface),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

@Composable
fun SettingsAppSelector(label: String, currentApp: String, onClick: () -> Unit, onClear: () -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(DarkSurface).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = currentApp, style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
            Row {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, contentDescription = null, tint = AccentRed) }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}
