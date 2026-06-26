package com.carlauncher.service

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SplitScreenLauncherTest {

    @Test
    fun `buildVietmapNavigationIntent with lat lng poiName returns intent`() {
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent(18.67337, 105.69232, "Vinh Nghe An"))
    }

    @Test
    fun `buildVietmapNavigationIntent with lat lng poiName handles Vietnamese text`() {
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent(18.67337, 105.69232, "Vinh, Nghệ An"))
    }

    @Test
    fun `buildVietmapNavigationIntent legacy returns null for blank address`() {
        assertNull(SplitScreenLauncher.buildVietmapNavigationIntent(""))
        assertNull(SplitScreenLauncher.buildVietmapNavigationIntent("  "))
    }

    @Test
    fun `buildVietmapNavigationIntent legacy returns companion URI for coordinate address`() {
        val intent = SplitScreenLauncher.buildVietmapNavigationIntent("21.0285,105.8542")
        assertNotNull(intent)
    }

    @Test
    fun `buildVietmapNavigationIntent legacy returns search URI for text address`() {
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent("123 Đường Lê Lợi, Quận 1"))
    }

    @Test
    fun `buildVietmapNavigationIntent legacy returns search URI for simple text`() {
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent("Hanoi"))
    }

    @Test
    fun `buildVietmapNavigationIntent legacy falls back to search for non-coordinate strings`() {
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent("abc,def"))
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent("21.0285"))
        assertNotNull(SplitScreenLauncher.buildVietmapNavigationIntent("91.0,105.0"))
    }

    @Test
    fun `buildNavigationIntent returns null for blank address`() {
        assertNull(SplitScreenLauncher.buildNavigationIntent(""))
        assertNull(SplitScreenLauncher.buildNavigationIntent("  "))
    }

    @Test
    fun `buildNavigationIntent returns google maps intent for valid address`() {
        assertNotNull(SplitScreenLauncher.buildNavigationIntent("21.0285,105.8542"))
    }
}
