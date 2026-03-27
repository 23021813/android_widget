package com.carlauncher.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.carlauncher.data.models.AppInfo
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.service.SplitScreenLauncher
import com.carlauncher.bridge.ui.EspBridgeScreen
import com.carlauncher.ui.screens.SettingsScreen
import com.carlauncher.update.UpdateInfo

private object AppRoutes {
    const val SETTINGS = "settings"
    const val ESP_BRIDGE = "esp_bridge"
}

@Composable
fun NavGraph(
    settings: LauncherSettings,
    installedApps: List<AppInfo>,
    updateInfo: UpdateInfo? = null,
    onSettingsUpdate: (LauncherSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AppRoutes.SETTINGS
    ) {
        composable(AppRoutes.SETTINGS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            SettingsScreen(
                settings = settings,
                installedApps = installedApps,
                updateInfo = updateInfo,
                onSettingsUpdate = onSettingsUpdate,
                onLaunchSplitView = {
                    if (settings.frame1App != null && settings.frame2App != null) {
                        SplitScreenLauncher.launchSplitScreen(
                            context,
                            settings.frame1App,
                            settings.frame2App
                        )
                    }
                },
                onResetDefaults = onResetDefaults,
                onCheckUpdate = onCheckUpdate,
                onOpenEspBridge = {
                    navController.navigate(AppRoutes.ESP_BRIDGE)
                }
            )
        }

        composable(AppRoutes.ESP_BRIDGE) {
            EspBridgeScreen(
                bridgeSettings = settings.navigationBridge,
                onSettingsUpdate = { bridgeSettings ->
                    onSettingsUpdate(settings.copy(navigationBridge = bridgeSettings))
                },
                onBack = { navController.popBackStack() }
            )
        }

    }
}
