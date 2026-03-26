package com.carlauncher.bridge.core

import android.app.Notification
import android.content.Context
import android.service.notification.StatusBarNotification
import com.carlauncher.bridge.model.NavigationData

internal open class NavigationNotification(
    context: Context,
    statusBarNotification: StatusBarNotification
) {
    protected val notification: Notification = statusBarNotification.notification
    protected val context: Context = context
    protected val appSourceContext: Context = try {
        context.createPackageContext(
            statusBarNotification.packageName,
            Context.CONTEXT_IGNORE_SECURITY
        )
    } catch (e: Exception) {
        android.util.Log.e(
            "NavigationNotification",
            "createPackageContext failed for ${statusBarNotification.packageName}: $e"
        )
        context
    }

    protected var navigationData: NavigationData = NavigationData(postTime = statusBarNotification.postTime)
}
