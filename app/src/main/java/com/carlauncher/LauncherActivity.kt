package com.carlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.AppLanguage
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.service.OverlayService
import com.carlauncher.ui.navigation.NavGraph
import com.carlauncher.ui.theme.CarLauncherTheme
import com.carlauncher.util.LocaleHelper
import com.carlauncher.update.OtaUpdateManager
import com.carlauncher.update.UpdateDialog
import com.carlauncher.update.UpdateInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LauncherActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore

    private val permissionState = mutableStateOf(false)

    // Read locale synchronously before UI is set up
    override fun attachBaseContext(newBase: Context) {
        val store = SettingsDataStore(newBase)
        val lang = runBlocking {
            try { store.settingsFlow.first().appLanguage } catch (e: Exception) { AppLanguage.SYSTEM }
        }
        super.attachBaseContext(LocaleHelper.applyLocale(newBase, lang.locale))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(this)

        permissionState.value = Settings.canDrawOverlays(this)
        checkOverlayPermissionAndStartService()

        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState(
                initial = LauncherSettings()
            )
            val scope = rememberCoroutineScope()
            val hasPermission by permissionState

            // OTA update check
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            LaunchedEffect(Unit) {
                updateInfo = OtaUpdateManager.checkForUpdate(this@LauncherActivity)
            }

            // Track language changes and recreate activity so Android reloads resources
            var lastLang by remember { mutableStateOf(settings.appLanguage) }
            LaunchedEffect(settings.appLanguage) {
                if (settings.appLanguage != lastLang) {
                    lastLang = settings.appLanguage
                    kotlinx.coroutines.delay(200)
                    recreate()
                }
            }

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
                            },
                            onCheckUpdate = {
                                scope.launch {
                                    val info = OtaUpdateManager.checkForUpdate(this@LauncherActivity)
                                    if (info != null) {
                                        updateInfo = info
                                    } else {
                                        android.widget.Toast.makeText(this@LauncherActivity, "You are on the latest version", android.widget.Toast.LENGTH_SHORT).show()
                                    }
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

                    // Show update dialog if available
                    updateInfo?.let { info ->
                        UpdateDialog(
                            updateInfo = info,
                            onUpdate = {
                                OtaUpdateManager.downloadAndInstall(this@LauncherActivity, info)
                                updateInfo = null
                            },
                            onDismiss = { updateInfo = null }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionState.value = Settings.canDrawOverlays(this)
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
            text = stringResource(R.string.perm_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.perm_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onRequestPermission) {
            Text(stringResource(R.string.perm_grant))
        }
    }
}
