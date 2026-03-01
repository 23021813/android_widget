package com.carlauncher.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.carlauncher.data.AppRepository
import com.carlauncher.data.models.AppInfo
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.service.SplitScreenLauncher
import com.carlauncher.ui.screens.SettingsScreen

@Composable
fun NavGraph(
    settings: LauncherSettings,
    onSettingsUpdate: (LauncherSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val appRepository = remember { AppRepository(context) }
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedApps = appRepository.getInstalledApps()
    }

    SettingsScreen(
        settings = settings,
        installedApps = installedApps,
        onSettingsUpdate = onSettingsUpdate,
        onLaunchSplitView = {
            if (settings.frame1App != null && settings.frame2App != null) {
                SplitScreenLauncher.launchSplitScreen(context, settings.frame1App, settings.frame2App)
            }
        },
        onResetDefaults = onResetDefaults,
        onCheckUpdate = onCheckUpdate
    )
}
