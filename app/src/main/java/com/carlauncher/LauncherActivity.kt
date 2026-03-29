package com.carlauncher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.carlauncher.bridge.core.NavigationBridgeRepository
import com.carlauncher.bridge.permission.NavigationBridgePermissionHelper
import com.carlauncher.bridge.service.BridgeServiceController
import com.carlauncher.bridge.service.SpeedSignCaptureController
import com.carlauncher.bridge.service.SpeedSignProjectionStore
import com.carlauncher.data.SettingsDataStore
import com.carlauncher.data.models.AppLanguage
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.data.models.WeatherLocationMode
import com.carlauncher.service.OverlayService
import com.carlauncher.ui.navigation.NavGraph
import com.carlauncher.ui.theme.CarLauncherTheme
import com.carlauncher.util.LocaleHelper
import com.carlauncher.update.OtaUpdateManager
import com.carlauncher.update.UpdateDialog
import com.carlauncher.update.UpdateInfo
import com.carlauncher.data.AppRepository
import com.carlauncher.data.models.AppInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LauncherActivity : ComponentActivity() {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val permissionState = mutableStateOf(false)
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    private val screenCapturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            SpeedSignProjectionStore.update(result.resultCode, data)
            lifecycleScope.launch {
                val settings = settingsDataStore.settingsFlow.first()
                if (settings.navigationBridge.enabled && settings.navigationBridge.speedSignCapture.enabled) {
                    SpeedSignCaptureController.start(this@LauncherActivity)
                }
            }
            Toast.makeText(this, "Đã cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
        } else {
            SpeedSignProjectionStore.clear()
            lifecycleScope.launch {
                val settings = settingsDataStore.settingsFlow.first()
                if (settings.navigationBridge.speedSignCapture.enabled) {
                    settingsDataStore.updateSettings(
                        settings.copy(
                            navigationBridge = settings.navigationBridge.copy(
                                speedSignCapture = settings.navigationBridge.speedSignCapture.copy(
                                    enabled = false
                                )
                            )
                        )
                    )
                }
            }
            Toast.makeText(this, "Bạn chưa cấp quyền chụp màn hình", Toast.LENGTH_SHORT).show()
        }
    }

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
        mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        permissionState.value = Settings.canDrawOverlays(this)
        checkOverlayPermissionAndStartService()

        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState(
                initial = LauncherSettings()
            )
            val scope = rememberCoroutineScope()
            val hasPermission by permissionState
            var hasRequestedScreenCapturePermission by remember { mutableStateOf(false) }

            // OTA update check
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var showUpdateDialog by remember { mutableStateOf(false) }
            val isDownloading by OtaUpdateManager.isDownloading.collectAsState()
            val downloadProgress by OtaUpdateManager.downloadProgress.collectAsState()

            val appRepository = remember { AppRepository(this@LauncherActivity) }
            var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

            LaunchedEffect(Unit) {
                installedApps = appRepository.getInstalledApps()

                val info = OtaUpdateManager.checkForUpdate(this@LauncherActivity)
                if (info != null) {
                    updateInfo = info
                    showUpdateDialog = true
                }
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

            LaunchedEffect(settings.navigationBridge.enabled) {
                if (settings.navigationBridge.enabled) {
                    BridgeServiceController.enable(this@LauncherActivity)
                } else {
                    BridgeServiceController.disable(this@LauncherActivity)
                }
            }

            val speedSignCaptureEnabled =
                settings.navigationBridge.enabled && settings.navigationBridge.speedSignCapture.enabled
            LaunchedEffect(speedSignCaptureEnabled) {
                if (speedSignCaptureEnabled) {
                    if (SpeedSignProjectionStore.hasProjection()) {
                        SpeedSignCaptureController.start(this@LauncherActivity)
                    } else if (!hasRequestedScreenCapturePermission) {
                        hasRequestedScreenCapturePermission = true
                        screenCapturePermissionLauncher.launch(
                            mediaProjectionManager.createScreenCaptureIntent()
                        )
                    }
                } else {
                    hasRequestedScreenCapturePermission = false
                    SpeedSignCaptureController.stop(this@LauncherActivity)
                    OverlayService.hideSpeedSignRoiCalibration(this@LauncherActivity)
                }
            }

            var hasRequestedLocationPermission by remember { mutableStateOf(false) }
            LaunchedEffect(settings.showWeather, settings.weatherLocationMode) {
                val shouldRequestLocation =
                    settings.showWeather &&
                        settings.weatherLocationMode == WeatherLocationMode.GPS &&
                        !hasLocationPermission()

                if (shouldRequestLocation && !hasRequestedLocationPermission) {
                    hasRequestedLocationPermission = true
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                } else if (!shouldRequestLocation) {
                    hasRequestedLocationPermission = false
                }
            }

            CarLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (hasPermission) {
                        NavGraph(
                            settings = settings,
                            installedApps = installedApps,
                            updateInfo = updateInfo,
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
                                    android.widget.Toast.makeText(this@LauncherActivity, "Checking for updates...", android.widget.Toast.LENGTH_SHORT).show()
                                    val info = OtaUpdateManager.checkForUpdate(this@LauncherActivity)
                                    if (info != null) {
                                        updateInfo = info
                                        showUpdateDialog = true
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


                    // Show update dialog if user hasn't dismissed it or manually checked
                    if (showUpdateDialog) {
                        updateInfo?.let { info ->
                            var cacheClearedTrigger by remember { mutableStateOf(0) }
                            val installFileExists = remember(info, isDownloading, cacheClearedTrigger) {
                                OtaUpdateManager.getDownloadedFile(this@LauncherActivity, info) != null
                            }

                            UpdateDialog(
                                updateInfo = info,
                                isDownloading = isDownloading,
                                progress = downloadProgress,
                                installFileExists = installFileExists,
                                onUpdate = {
                                    OtaUpdateManager.downloadAndInstall(this@LauncherActivity, info)
                                },
                                onClearCache = {
                                    OtaUpdateManager.clearCache(this@LauncherActivity, info)
                                    cacheClearedTrigger++
                                },
                                onDismiss = { 
                                    if (!isDownloading) {
                                        showUpdateDialog = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionState.value = Settings.canDrawOverlays(this)
        checkOverlayPermissionAndStartService()
        com.carlauncher.service.ScheduleManager.checkAndTriggerMissedSchedules(this)
        NavigationBridgeRepository.updatePermissions(
            NavigationBridgePermissionHelper.currentState(this)
        )
    }

    private fun checkOverlayPermissionAndStartService() {
        if (Settings.canDrawOverlays(this)) {
            OverlayService.start(this)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
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
