package com.carlauncher

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.service.OverlayService
import com.carlauncher.ui.navigation.NavGraph
import com.carlauncher.ui.theme.CarLauncherTheme
import kotlinx.coroutines.launch

class LauncherActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(this)

        checkOverlayPermissionAndStartService()

        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState(
                initial = LauncherSettings()
            )
            val scope = rememberCoroutineScope()
            var hasPermission by remember { mutableStateOf(Settings.canDrawOverlays(this)) }

            CarLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (hasPermission) {
                        NavGraph(
                            settings = settings,
                            onSettingsUpdate = { newSettings ->
                                scope.launch {
                                    settingsDataStore.updateSettings(newSettings)
                                }
                            },
                            onResetDefaults = {
                                scope.launch {
                                    settingsDataStore.resetToDefaults()
                                }
                            }
                        )
                    } else {
                        PermissionRequestScreen(
                            onRequestPermission = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                                startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkOverlayPermissionAndStartService()
    }

    private fun checkOverlayPermissionAndStartService() {
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Yêu cầu quyền hiển thị trên ứng dụng khác",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Car Overlay cần được cấp quyền System Alert Window để vẽ các nút nổi trên bản đồ hoặc ứng dụng khác.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text("Cấp quyền ngay")
        }
    }
}
