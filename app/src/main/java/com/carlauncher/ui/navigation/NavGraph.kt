package com.carlauncher.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.carlauncher.data.AppRepository
import com.carlauncher.data.models.AppInfo
import com.carlauncher.data.models.LauncherSettings
import com.carlauncher.service.SplitScreenLauncher
import com.carlauncher.ui.screens.SettingsScreen
import com.carlauncher.update.UpdateInfo

@Composable
fun NavGraph(
    settings: LauncherSettings,
    installedApps: List<AppInfo>,
    updateInfo: UpdateInfo? = null,
    onSettingsUpdate: (LauncherSettings) -> Unit,
    onResetDefaults: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    // installedApps is now passed in from LauncherActivity

    SettingsScreen(
        settings = settings,
        installedApps = installedApps,
        updateInfo = updateInfo,
        onSettingsUpdate = onSettingsUpdate,
        onLaunchSplitView = {
            if (settings.frame1App != null && settings.frame2App != null) {
                val intent = android.content.Intent(context, com.carlauncher.SplitScreenProxyActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("pkg1", settings.frame1App)
                    putExtra("pkg2", settings.frame2App)
                }
                context.startActivity(intent)
            }
        },
        onResetDefaults = onResetDefaults,
        onCheckUpdate = onCheckUpdate
    )
}
