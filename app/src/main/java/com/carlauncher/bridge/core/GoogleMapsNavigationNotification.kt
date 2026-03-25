package com.carlauncher.bridge.core

import android.app.Notification
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.service.notification.StatusBarNotification
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.view.children
import com.carlauncher.bridge.model.NavigationData
import com.carlauncher.bridge.model.NavigationDirection
import com.carlauncher.bridge.model.NavigationEta
import com.carlauncher.bridge.model.NavigationIcon

internal const val GMAPS_PACKAGE = "com.google.android.apps.maps"

internal class GoogleMapsNavigationNotification(
    context: Context,
    sbn: StatusBarNotification
) : NavigationNotification(context, sbn) {
    init {
        val normalContent = getContentView(big = false)
        if (normalContent != null) {
            parseRemoteView(getRemoteViewGroup(normalContent))
        }

        val bestContent = getContentView(big = true)
        if (bestContent != null && bestContent != normalContent) {
            parseRemoteView(getRemoteViewGroup(bestContent))
        }
    }

    fun toNavigationData(): NavigationData = navigationData

    private fun getContentView(big: Boolean): RemoteViews? {
        val recovered = Notification.Builder.recoverBuilder(context, notification)
        return if (big) {
            recovered.createBigContentView() ?: recovered.createContentView()
        } else {
            recovered.createContentView()
        }
    }

    private fun getRemoteViewGroup(remoteViews: RemoteViews): ViewGroup {
        val layoutInflater = appSourceContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val viewGroup = layoutInflater.inflate(remoteViews.layoutId, null) as? ViewGroup
            ?: error("Unable to inflate Google Maps notification view")
        remoteViews.reapply(appSourceContext, viewGroup)
        return viewGroup
    }

    private fun getEntryName(item: View): String {
        return runCatching {
            if (item.id > 0) appSourceContext.resources.getResourceEntryName(item.id) else ""
        }.getOrDefault("")
    }

    private fun findChildByName(group: ViewGroup, name: CharSequence): View? {
        for (child in group.children) {
            if (getEntryName(child) == name) return child
            if (child is ViewGroup) {
                val nested = findChildByName(child, name)
                if (nested != null) return nested
            }
        }
        return null
    }

    private fun parseRemoteView(group: ViewGroup) {
        val directionText = findChildByName(group, "text") as? TextView
        val etaText = findChildByName(group, "header_text") as? TextView
        val titleText = findChildByName(group, "title") as? TextView
        val rightIcon = findChildByName(group, "right_icon") as? ImageView

        var eta = navigationData.eta
        if (etaText != null) {
            val etaList = etaText.text.split("·")
            if (etaList.size == 3) {
                eta = NavigationEta(
                    eta = etaList[2].removeSuffix("ETA").trim(),
                    ete = etaList[0].trim(),
                    distance = etaList[1].trim()
                )
            }
        }

        val nextDistance = titleText?.text?.trim()?.takeIf { it.isNotEmpty() }?.toString().orEmpty()
        var nextRoad = ""
        var nextRoadDesc = ""
        if (directionText?.text !is Spanned) {
            nextRoad = directionText?.text?.toString().orEmpty()
        } else {
            val directionList = ParserHelper.splitByStyleSpan(
                directionText.text as Spanned,
                Typeface.NORMAL,
                2
            )
            if (directionList.isNotEmpty()) {
                val nextRoadList = mutableListOf(directionList.first())
                val nextRoadDescList = mutableListOf<ParserHelper.SpanSplitResult>()
                val rest = directionList.drop(1)
                val index = rest.indexOfFirst { it.isKeySpan && it.text.trim() != "/" }
                if (index == -1) {
                    nextRoadList.addAll(rest)
                } else {
                    nextRoadList.addAll(rest.subList(0, index))
                    nextRoadDescList.addAll(rest.subList(index, rest.size))
                }
                nextRoad = nextRoadList.joinToString(" ") { it.text }
                nextRoadDesc = nextRoadDescList.joinToString(" ") { it.text }
            }
        }

        val icon = (rightIcon?.drawable as? BitmapDrawable)?.bitmap?.let {
            NavigationIcon(it.copy(it.config ?: android.graphics.Bitmap.Config.ARGB_8888, false))
        } ?: navigationData.actionIcon

        navigationData = navigationData.copy(
            nextDirection = NavigationDirection(
                nextRoad = nextRoad,
                nextRoadAdditionalInfo = nextRoadDesc,
                distance = nextDistance
            ),
            eta = eta,
            actionIcon = icon
        )
    }
}
