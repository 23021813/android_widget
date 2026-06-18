package com.carlauncher.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.carlauncher.R
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.data.models.ParkingAlertConfig
import com.carlauncher.data.secrets.SecretsStore
import com.carlauncher.service.ParkingMonitor
import com.carlauncher.ui.theme.AccentCyan
import com.carlauncher.ui.theme.AccentGreen
import com.carlauncher.ui.theme.AccentRed
import com.carlauncher.ui.theme.DarkSurface
import com.carlauncher.ui.theme.DarkSurfaceVariant
import com.carlauncher.ui.theme.TextPrimary
import com.carlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun ParkingAlertSection(
    settings: LauncherSettings,
    onSettingsUpdate: (LauncherSettings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secrets = remember(context) { SecretsStore(context) }
    val p = settings.parkingAlert

    var tgTokenDraft by remember { mutableStateOf("") }
    var showTgTokenField by remember { mutableStateOf(false) }
    var smtpPassDraft by remember { mutableStateOf("") }
    var showSmtpPassField by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var lastTestResult by remember { mutableStateOf<Boolean?>(null) }

    val tgSet = p.hasTelegramToken
    val smtpSet = p.hasSmtpPassword

    Column {
        SettingsToggle(
            label = stringResource(R.string.parking_enabled),
            checked = p.enabled,
            onCheckedChange = { v ->
                onSettingsUpdate(settings.copy(parkingAlert = p.copy(enabled = v)))
            }
        )

        if (p.enabled) {
            Spacer(Modifier.height(8.dp))

            SettingsSlider(
                label = stringResource(R.string.parking_idle_label),
                value = p.idleMinutes.toFloat(),
                valueRange = 5f..120f,
                displayValue = "${p.idleMinutes} phút",
                onValueChange = { v ->
                    val snapped = (v / 5f).toInt() * 5
                    val clamped = snapped.coerceIn(5, 120)
                    onSettingsUpdate(settings.copy(parkingAlert = p.copy(idleMinutes = clamped)))
                }
            )

            Spacer(Modifier.height(8.dp))
            SettingsSlider(
                label = stringResource(R.string.parking_distance_label),
                value = p.distanceMeters.toFloat(),
                valueRange = 20f..500f,
                displayValue = "${p.distanceMeters} m",
                onValueChange = { v ->
                    val snapped = (v / 10f).toInt() * 10
                    val clamped = snapped.coerceIn(20, 500)
                    onSettingsUpdate(settings.copy(parkingAlert = p.copy(distanceMeters = clamped)))
                }
            )

            Spacer(Modifier.height(8.dp))
            SettingsSlider(
                label = stringResource(R.string.parking_cooldown_label),
                value = p.cooldownMinutes.toFloat(),
                valueRange = 15f..240f,
                displayValue = "${p.cooldownMinutes} phút",
                onValueChange = { v ->
                    val snapped = (v / 15f).toInt() * 15
                    val clamped = snapped.coerceIn(15, 240)
                    onSettingsUpdate(settings.copy(parkingAlert = p.copy(cooldownMinutes = clamped)))
                }
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarkSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // ─── Telegram ───
            SettingsToggle(
                label = stringResource(R.string.parking_send_telegram),
                checked = p.sendTelegram,
                onCheckedChange = { v ->
                    onSettingsUpdate(settings.copy(parkingAlert = p.copy(sendTelegram = v)))
                }
            )
            if (p.sendTelegram) {
                Spacer(Modifier.height(6.dp))
                SettingsTextField(
                    label = stringResource(R.string.parking_tg_chat_id),
                    value = p.telegramChatId,
                    onValueChange = { v ->
                        onSettingsUpdate(settings.copy(parkingAlert = p.copy(telegramChatId = v)))
                    }
                )
                Spacer(Modifier.height(8.dp))
                SecretRow(
                    label = stringResource(R.string.parking_tg_token),
                    isSet = tgSet,
                    draftShown = showTgTokenField,
                    draft = tgTokenDraft,
                    onDraftChange = { tgTokenDraft = it },
                    onShow = { showTgTokenField = true; tgTokenDraft = "" },
                    onCancel = { showTgTokenField = false; tgTokenDraft = "" },
                    onSave = {
                        if (tgTokenDraft.isNotBlank()) {
                            secrets.setTelegramBotToken(tgTokenDraft.trim())
                            onSettingsUpdate(settings.copy(parkingAlert = p.copy(hasTelegramToken = true)))
                            Toast.makeText(context, R.string.parking_secret_saved, Toast.LENGTH_SHORT).show()
                        }
                        showTgTokenField = false
                        tgTokenDraft = ""
                    },
                    onClear = {
                        secrets.clearTelegramBotToken()
                        onSettingsUpdate(settings.copy(parkingAlert = p.copy(hasTelegramToken = false)))
                    }
                )
                Text(
                    text = stringResource(R.string.parking_tg_token_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = DarkSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // ─── Email SMTP ───
            SettingsToggle(
                label = stringResource(R.string.parking_send_email),
                checked = p.sendEmail,
                onCheckedChange = { v ->
                    onSettingsUpdate(settings.copy(parkingAlert = p.copy(sendEmail = v)))
                }
            )
            if (p.sendEmail) {
                Spacer(Modifier.height(6.dp))
                SettingsTextField(
                    label = stringResource(R.string.parking_smtp_host),
                    value = p.smtpHost,
                    onValueChange = { v -> onSettingsUpdate(settings.copy(parkingAlert = p.copy(smtpHost = v))) }
                )
                Spacer(Modifier.height(8.dp))
                SettingsTextField(
                    label = stringResource(R.string.parking_smtp_port),
                    value = p.smtpPort.toString(),
                    onValueChange = { v ->
                        val port = v.trim().toIntOrNull() ?: return@SettingsTextField
                        onSettingsUpdate(settings.copy(parkingAlert = p.copy(smtpPort = port)))
                    }
                )
                Spacer(Modifier.height(8.dp))
                SettingsTextField(
                    label = stringResource(R.string.parking_smtp_user),
                    value = p.smtpUser,
                    onValueChange = { v -> onSettingsUpdate(settings.copy(parkingAlert = p.copy(smtpUser = v))) }
                )
                Spacer(Modifier.height(8.dp))
                SettingsTextField(
                    label = stringResource(R.string.parking_smtp_recipient),
                    value = p.smtpRecipient,
                    onValueChange = { v -> onSettingsUpdate(settings.copy(parkingAlert = p.copy(smtpRecipient = v))) }
                )
                Spacer(Modifier.height(8.dp))
                SecretRow(
                    label = stringResource(R.string.parking_smtp_password),
                    isSet = smtpSet,
                    draftShown = showSmtpPassField,
                    draft = smtpPassDraft,
                    onDraftChange = { smtpPassDraft = it },
                    onShow = { showSmtpPassField = true; smtpPassDraft = "" },
                    onCancel = { showSmtpPassField = false; smtpPassDraft = "" },
                    onSave = {
                        if (smtpPassDraft.isNotBlank()) {
                            secrets.setSmtpPassword(smtpPassDraft)
                            onSettingsUpdate(settings.copy(parkingAlert = p.copy(hasSmtpPassword = true)))
                            Toast.makeText(context, R.string.parking_secret_saved, Toast.LENGTH_SHORT).show()
                        }
                        showSmtpPassField = false
                        smtpPassDraft = ""
                    },
                    onClear = {
                        secrets.clearSmtpPassword()
                        onSettingsUpdate(settings.copy(parkingAlert = p.copy(hasSmtpPassword = false)))
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DarkSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            // ─── Test button ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentCyan.copy(alpha = 0.15f))
                    .clickable(enabled = !testing) {
                        scope.launch {
                            testing = true
                            val ok = ParkingMonitor.current?.sendTestAlert() ?: false
                            lastTestResult = ok
                            val msg = if (ok) R.string.parking_test_ok else R.string.parking_test_fail
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            testing = false
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (testing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = AccentCyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.parking_test),
                    color = AccentCyan,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            if (lastTestResult != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (lastTestResult == true) stringResource(R.string.parking_test_ok)
                    else stringResource(R.string.parking_test_fail),
                    color = if (lastTestResult == true) AccentGreen else AccentRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.parking_security_note),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun SecretRow(
    label: String,
    isSet: Boolean,
    draftShown: Boolean,
    draft: String,
    onDraftChange: (String) -> Unit,
    onShow: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(4.dp))
        if (draftShown) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = DarkSurfaceVariant,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onSave,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkSurface)
                ) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isSet) stringResource(R.string.parking_secret_set)
                    else stringResource(R.string.parking_secret_not_set),
                    color = if (isSet) AccentGreen else TextSecondary,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onShow) {
                    Text(if (isSet) "Đổi" else "Thiết lập", color = AccentCyan)
                }
                if (isSet) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = AccentRed)
                    }
                }
            }
        }
    }
}
