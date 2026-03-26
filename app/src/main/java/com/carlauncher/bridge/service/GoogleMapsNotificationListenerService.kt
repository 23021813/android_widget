package com.carlauncher.bridge.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.carlauncher.bridge.core.GMAPS_PACKAGE
import com.carlauncher.bridge.core.GoogleMapsNavigationNotification
import com.carlauncher.bridge.core.NavigationBridgeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GoogleMapsNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var parsingJob: Job? = null
    private var bridgeEnabled = false
    private var lastSeenNotification: StatusBarNotification? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            NavigationBridgeRepository.uiState
                .map { it.bridgeSettings.enabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    bridgeEnabled = enabled
                    if (enabled) {
                        checkActiveNotifications()
                    } else {
                        lastSeenNotification = null
                        NavigationBridgeRepository.clearNavigation()
                    }
                }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (bridgeEnabled) {
            checkActiveNotifications()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (isGoogleMapsNotification(sbn)) {
            handleGoogleNotification(sbn!!)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (isGoogleMapsNotification(sbn)) {
            parsingJob?.cancel()
            lastSeenNotification = null
            NavigationBridgeRepository.clearNavigation()
        }
    }

    override fun onDestroy() {
        parsingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun checkActiveNotifications() {
        runCatching {
            activeNotifications?.forEach(::onNotificationPosted)
        }
    }

    private fun isGoogleMapsNotification(sbn: StatusBarNotification?): Boolean {
        if (!bridgeEnabled || sbn == null || !sbn.isOngoing) return false
        if (!sbn.packageName.contains(GMAPS_PACKAGE)) return false
        return sbn.id == 1
    }

    private fun handleGoogleNotification(statusBarNotification: StatusBarNotification) {
        lastSeenNotification = statusBarNotification // luôn cập nhật notification mới nhất
        if (parsingJob?.isActive == true) return    // job đang chạy sẽ tự đọc lastSeenNotification

        parsingJob = serviceScope.launch {
            val notificationToProcess = lastSeenNotification ?: return@launch
            val parsed = withContext(Dispatchers.Default) {
                GoogleMapsNavigationNotification(
                    this@GoogleMapsNotificationListenerService,
                    notificationToProcess
                ).toNavigationData()
            }
            NavigationBridgeRepository.updateNavigationData(parsed)
        }
    }
}
