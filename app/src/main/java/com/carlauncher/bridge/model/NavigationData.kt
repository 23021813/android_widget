package com.carlauncher.bridge.model

import android.graphics.Bitmap

data class NavigationDirection(
    val nextRoad: String? = null,
    val nextRoadAdditionalInfo: String? = null,
    val distance: String? = null
)

data class NavigationEta(
    val eta: String? = null,
    val ete: String? = null,
    val distance: String? = null
)

data class NavigationIcon(
    val bitmap: Bitmap? = null
) {
    override fun equals(other: Any?): Boolean {
        return if (other is NavigationIcon && bitmap != null && other.bitmap != null) {
            bitmap.sameAs(other.bitmap)
        } else {
            super.equals(other)
        }
    }

    override fun hashCode(): Int = bitmap?.hashCode() ?: 0
}

data class NavigationData(
    val nextDirection: NavigationDirection = NavigationDirection(),
    val eta: NavigationEta = NavigationEta(),
    val actionIcon: NavigationIcon = NavigationIcon(),
    val postTime: Long = 0L
)
