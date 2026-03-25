package com.carlauncher.bridge.core

import com.carlauncher.bridge.model.NavigationData
import com.carlauncher.data.models.NavigationBridgeSettings

object BridgePayloadSerializer {
    fun buildSettingsPayload(settings: NavigationBridgeSettings): String {
        return keyValuePayload(
            linkedMapOf(
                "lightTheme" to settings.displayLightTheme.toString(),
                "brightness" to settings.displayBrightness.toString(),
                "speedLimit" to settings.speedWarningLimit.toString()
            )
        )
    }

    fun buildNavigationPayload(data: NavigationData?, iconHash: String): String {
        return keyValuePayload(
            linkedMapOf(
                "nextRd" to sanitize(data?.nextDirection?.nextRoad.orEmpty()),
                "nextRdDesc" to sanitize(data?.nextDirection?.nextRoadAdditionalInfo.orEmpty()),
                "distToNext" to sanitize(data?.nextDirection?.distance.orEmpty()),
                "totalDist" to sanitize(data?.eta?.distance.orEmpty()),
                "eta" to sanitize(data?.eta?.eta.orEmpty()),
                "ete" to sanitize(data?.eta?.ete.orEmpty()),
                "iconHash" to iconHash
            )
        )
    }

    fun keyValuePayload(map: Map<String, String>): String {
        return map.entries.joinToString("\n") { (key, value) -> "$key=$value" }
    }

    fun sanitize(value: String): String {
        return value.replace("\u00a0", " ")
            .replace("\n", " ")
            .replace("…", "...")
    }
}
