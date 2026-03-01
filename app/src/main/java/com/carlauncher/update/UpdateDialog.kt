package com.carlauncher.update

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carlauncher.R
import com.carlauncher.ui.theme.*

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!updateInfo.forceUpdate) onDismiss() },
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
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = DarkBackground)
            ) {
                Text(stringResource(R.string.update_now))
            }
        },
        dismissButton = {
            if (!updateInfo.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_later), color = TextSecondary)
                }
            }
        }
    )
}
