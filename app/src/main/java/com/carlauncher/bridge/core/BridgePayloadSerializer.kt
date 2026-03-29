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

    fun buildTimeSyncCommand(epoch: Long, tzOffsetMinutes: Int, requestId: String): String {
        return keyValuePayload(
            linkedMapOf(
                "proto" to "1",
                "cmd" to "time.sync",
                "requestId" to requestId,
                "epoch" to epoch.toString(),
                "tzOffsetMinutes" to tzOffsetMinutes.toString()
            )
        )
    }

    fun buildStatusGetCommand(requestId: String): String {
        return keyValuePayload(
            linkedMapOf(
                "proto" to "1",
                "cmd" to "status.get",
                "requestId" to requestId
            )
        )
    }

    fun buildSettingsSetCommand(settings: NavigationBridgeSettings, requestId: String): String {
        return keyValuePayload(
            linkedMapOf(
                "proto" to "1",
                "cmd" to "settings.set",
                "requestId" to requestId,
                "lightTheme" to settings.displayLightTheme.toString(),
                "brightness" to settings.displayBrightness.toString(),
                "speedLimit" to settings.speedWarningLimit.toString()
            )
        )
    }

    fun buildWifiScanCommand(requestId: String): String {
        return keyValuePayload(
            linkedMapOf(
                "proto" to "1",
                "cmd" to "wifi.scan",
                "requestId" to requestId
            )
        )
    }

    fun buildWifiConnectCommand(ssid: String, password: String, requestId: String): String {
        val map = linkedMapOf(
            "proto" to "1",
            "cmd" to "wifi.connect",
            "requestId" to requestId,
            "ssid" to ssid
        )
        if (password.isNotEmpty()) {
            map["password"] = password
        }
        return keyValuePayload(map)
    }

    fun buildWifiForgetCommand(requestId: String): String {
        return keyValuePayload(
            linkedMapOf(
                "proto" to "1",
                "cmd" to "wifi.forget",
                "requestId" to requestId
            )
        )
    }

    fun buildScreenGetCommand(requestId: String): String {
        return keyValuePayload(
            linkedMapOf(
                "proto" to "1",
                "cmd" to "screen.get",
                "requestId" to requestId
            )
        )
    }

    fun buildSpeedSignsUpdateCommand(
        currentLimit: Int?,
        upcomingLimit: Int?,
        distanceMeters: Int?,
        requestId: String
    ): String {
        val payload = linkedMapOf(
            "proto" to "1",
            "cmd" to "speed.signs.update",
            "requestId" to requestId
        )
        currentLimit?.let { payload["currentLimit"] = it.toString() }
        upcomingLimit?.let { payload["upcomingLimit"] = it.toString() }
        distanceMeters?.let { payload["distanceMeters"] = it.toString() }
        return keyValuePayload(payload)
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
