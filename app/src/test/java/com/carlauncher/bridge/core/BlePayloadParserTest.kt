package com.carlauncher.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Test

class BlePayloadParserTest {
    @Test
    fun `parses multi line key value format`() {
        val payload = """
            type=status.snapshot
            deviceName=CarMap-TEST
            wifiConfigured=true
            wifiState=connected
        """.trimIndent()

        val map = BlePayloadParser.parse(payload)
        
        assertEquals("status.snapshot", map["type"])
        assertEquals("CarMap-TEST", map["deviceName"])
        assertEquals("true", map["wifiConfigured"])
        assertEquals("connected", map["wifiState"])
        assertEquals(4, map.size)
    }

    @Test
    fun `handles empty strings and no value keys correctly`() {
        val payload = "type=status.snapshot\nwifiLastError=\n\nkeyWithoutEquals"
        val map = BlePayloadParser.parse(payload)
        
        assertEquals("status.snapshot", map["type"])
        assertEquals("", map["wifiLastError"])
        assertEquals(null, map["keyWithoutEquals"])
        assertEquals(2, map.size)
    }

    @Test
    fun `splits only at first equals sign to allow equals in values`() {
        val payload = "password=my=secret=password"
        val map = BlePayloadParser.parse(payload)
        
        assertEquals("my=secret=password", map["password"])
    }
}
