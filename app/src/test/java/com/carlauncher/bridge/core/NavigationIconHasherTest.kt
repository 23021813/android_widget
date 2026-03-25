package com.carlauncher.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationIconHasherTest {
    @Test
    fun `hash suffix returns last 10 md5 chars`() {
        val hash = NavigationIconHasher.hashSuffix("test-icon".toByteArray())
        assertEquals(10, hash.length)
        assertEquals("1127f00c07", hash)
    }

    @Test
    fun `sent icon registry deduplicates within same session`() {
        val registry = SentIconRegistry()

        assertTrue(registry.shouldSend("abc1234567"))
        assertFalse(registry.shouldSend("abc1234567"))

        registry.clear()

        assertTrue(registry.shouldSend("abc1234567"))
    }
}
