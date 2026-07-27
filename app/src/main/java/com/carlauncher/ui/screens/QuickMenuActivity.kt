package com.carlauncher.ui.screens

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.carlauncher.R
import com.carlauncher.data.AppRepository
import com.carlauncher.data.models.AppInfo
import com.carlauncher.data.models.VirtualActions
import com.carlauncher.SplitScreenProxyActivity
import com.carlauncher.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.carlauncher.data.SettingsDataStore

class QuickMenuActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appRepository = AppRepository(applicationContext)

        setContent {
            var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                val installed = appRepository.getInstalledApps()
                val context = this@QuickMenuActivity
                
                val homeApp = AppInfo(
                    packageName = VirtualActions.ACTION_HOME,
                    label = context.getString(R.string.action_home_label),
                    icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_crop)
                )
                val splitViewApp = AppInfo(
                    packageName = VirtualActions.ACTION_SPLIT_VIEW,
                    label = context.getString(R.string.action_split_view_label),
                    icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_sort_by_size)
                )
                val wifiPopupApp = AppInfo(
                    packageName = "__ACTION_WIFI_POPUP__",
                    label = context.getString(R.string.action_wifi_popup_label),
                    icon = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_menu_preferences)
                )

                apps = listOf(homeApp, splitViewApp, wifiPopupApp) + installed
                isLoading = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { finish() },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = AccentCyan)
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .fillMaxHeight(0.85f)
                            .clickable(enabled = false) {}, // Prevent dismiss when clicking inside
                        shape = RoundedCornerShape(24.dp),
                        color = DarkSurface,
                        tonalElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getString(R.string.action_quick_menu_label),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = TextPrimary
                                )
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TextSecondary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(80.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(apps, key = { it.packageName }) { app ->
                                    QuickMenuAppItem(
                                        app = app,
                                        onClick = { handleAppSelected(app) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun handleAppSelected(app: AppInfo) {
        when (app.packageName) {
            VirtualActions.ACTION_HOME -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            VirtualActions.ACTION_SPLIT_VIEW -> {
                lifecycleScope.launch {
                    val settingsDataStore = SettingsDataStore(applicationContext)
                    val settings = settingsDataStore.settingsFlow.first()
                    val f1 = settings.frame1App
                    val f2 = settings.frame2App
                    if (f1 != null && f2 != null) {
                        val intent = Intent(this@QuickMenuActivity, SplitScreenProxyActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("pkg1", f1)
                            putExtra("pkg2", f2)
                        }
                        startActivity(intent)
                    }
                }
            }
            "__ACTION_WIFI_POPUP__" -> {
                val intent = Intent(this, WifiPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
            }
            else -> {
                com.carlauncher.service.SplitScreenLauncher.launchApp(this, app.packageName)
            }
        }
        finish()
    }
}

@Composable
private fun QuickMenuAppItem(
    app: AppInfo,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        app.icon?.let { drawable ->
            val bitmap = remember(app.packageName) {
                drawable.toBitmap(64, 64).asImageBitmap()
            }
            androidx.compose.foundation.Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
