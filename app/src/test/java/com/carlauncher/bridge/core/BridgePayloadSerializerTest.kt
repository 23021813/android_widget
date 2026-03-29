package com.carlauncher.bridge.core

import com.carlauncher.bridge.model.NavigationData
import com.carlauncher.bridge.model.NavigationDirection
import com.carlauncher.bridge.model.NavigationEta
import com.carlauncher.data.models.NavigationBridgeSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class BridgePayloadSerializerTest {
    @Test
    fun `build settings payload keeps firmware wire format`() {
        val payload = BridgePayloadSerializer.buildSettingsPayload(
            NavigationBridgeSettings(
                displayLightTheme = true,
                displayBrightness = 20,
                speedWarningLimit = 60
            )
        )

        assertEquals(
            "lightTheme=true\nbrightness=20\nspeedLimit=60",
            payload
        )
    }

    @Test
    fun `build time sync command follows proto 1 format`() {
        val payload = BridgePayloadSerializer.buildTimeSyncCommand(1679900000L, 420, "1001")
        assertEquals(
            "proto=1\ncmd=time.sync\nrequestId=1001\nepoch=1679900000\ntzOffsetMinutes=420",
            payload
        )
    }

    @Test
    fun `build wifi connect command handles empty password`() {
        val payloadWithPass = BridgePayloadSerializer.buildWifiConnectCommand("MyWiFi", "12345678", "1002")
        assertEquals(
            "proto=1\ncmd=wifi.connect\nrequestId=1002\nssid=MyWiFi\npassword=12345678",
            payloadWithPass
        )

        val payloadNoPass = BridgePayloadSerializer.buildWifiConnectCommand("OpenNet", "", "1003")
        assertEquals(
            "proto=1\ncmd=wifi.connect\nrequestId=1003\nssid=OpenNet",
            payloadNoPass
        )
    }

    @Test
    fun `build speed signs update command omits null fields`() {
        val payload = BridgePayloadSerializer.buildSpeedSignsUpdateCommand(
            currentLimit = 60,
            upcomingLimit = 80,
            distanceMeters = null,
            requestId = "2001"
        )

        assertEquals(
            "proto=1\ncmd=speed.signs.update\nrequestId=2001\ncurrentLimit=60\nupcomingLimit=80",
            payload
        )
    }

    @Test
    fun `build navigation payload keeps expected key order and sanitizes text`() {
        val payload = BridgePayloadSerializer.buildNavigationPayload(
            data = NavigationData(
                nextDirection = NavigationDirection(
                    nextRoad = "Vo Van\u00a0Kiet",
                    nextRoadAdditionalInfo = "keep\nleft",
                    distance = "350 m"
                ),
                eta = NavigationEta(
                    eta = "08:15 PM",
                    ete = "20 min",
                    distance = "12 km"
                )
            ),
            iconHash = "abc1234567"
        )

        assertEquals(
            "nextRd=Vo Van Kiet\n" +
                "nextRdDesc=keep left\n" +
                "distToNext=350 m\n" +
                "totalDist=12 km\n" +
                "eta=08:15 PM\n" +
                "ete=20 min\n" +
                "iconHash=abc1234567",
            payload
        )
    }
}
