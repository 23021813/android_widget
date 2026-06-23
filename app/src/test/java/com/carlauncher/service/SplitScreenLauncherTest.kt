package com.carlauncher.service

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SplitScreenLauncherTest {

    @Test
    fun `buildVietmapNavigationIntent returns null for blank address`() {
        assertNull(SplitScreenLauncher.buildVietmapNavigationIntent(""))
        assertNull(SplitScreenLauncher.buildVietmapNavigationIntent("  "))
    }

    @Test
    fun `buildVietmapNavigationIntent returns intent for valid address`() {
        val intent = SplitScreenLauncher.buildVietmapNavigationIntent("21.0285,105.8542")
        assertNotNull(intent)
    }

    @Test
    fun `buildVietmapNavigationIntent handles special characters in address`() {
        val intent = SplitScreenLauncher.buildVietmapNavigationIntent("123 Đường Lê Lợi, Quận 1")
        assertNotNull(intent)
    }

    @Test
    fun `buildNavigationIntent returns null for blank address`() {
        assertNull(SplitScreenLauncher.buildNavigationIntent(""))
        assertNull(SplitScreenLauncher.buildNavigationIntent("  "))
    }

    @Test
    fun `buildNavigationIntent returns google maps intent for valid address`() {
        val intent = SplitScreenLauncher.buildNavigationIntent("21.0285,105.8542")
        assertNotNull(intent)
    }
}
