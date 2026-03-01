package com.carlauncher.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlauncher.R
import com.carlauncher.ui.theme.*

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!updateInfo.forceUpdate && !isDownloading) onDismiss() },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                text = stringResource(R.string.update_available),
                color = AccentCyan,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${stringResource(R.string.new_version)}: ${updateInfo.versionName}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (updateInfo.changelog.isNotBlank()) {
                    Text(
                        text = updateInfo.changelog,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = AccentCyan,
                        trackColor = AccentCyan.copy(alpha = 0.2f),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Text(
                        text = "Downloading: ${(progress * 100).toInt()}%",
                        color = AccentCyan,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (!isDownloading) onUpdate() },
                enabled = !isDownloading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan, 
                    contentColor = DarkBackground,
                    disabledContainerColor = DarkSurface,
                    disabledContentColor = TextTertiary
                )
            ) {
                Text(if (isDownloading) "Downloading..." else stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate && !isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later), color = TextSecondary)
                }
            }
        }
    )
}
