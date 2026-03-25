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
