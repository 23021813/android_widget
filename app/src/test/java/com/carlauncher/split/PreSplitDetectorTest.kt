package com.carlauncher.split

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreSplitDetectorTest {

    @Test
    fun `when app appears in foreground before timeout, returns true`() = runBlocking {
        var checkCount = 0
        val detector = PreSplitDetector(
            foregroundChecker = { sinceMs, _ ->
                checkCount++
                checkCount >= 3 // appears on 3rd poll
            },
            packageName = "com.test.app",
            pollIntervalMs = 10
        )
        val result = detector.waitForApp(maxWaitMs = 1000)
        assertTrue(result)
    }

    @Test
    fun `when app never appears before timeout, returns false`() = runBlocking {
        val detector = PreSplitDetector(
            foregroundChecker = { _, _ -> false },
            packageName = "com.test.app",
            pollIntervalMs = 10
        )
        val result = detector.waitForApp(maxWaitMs = 50)
        assertFalse(result)
    }

    @Test
    fun `when app already in foreground, returns true immediately`() = runBlocking {
        var firstCall = true
        val detector = PreSplitDetector(
            foregroundChecker = { _, _ ->
                if (firstCall) { firstCall = false; true } else false
            },
            packageName = "com.test.app",
            pollIntervalMs = 10
        )
        val result = detector.waitForApp(maxWaitMs = 1000)
        assertTrue(result)
    }

    @Test
    fun `timeout respects max wait milliseconds`() = runBlocking {
        val start = System.currentTimeMillis()
        val detector = PreSplitDetector(
            foregroundChecker = { _, _ -> false },
            packageName = "com.test.app",
            pollIntervalMs = 5
        )
        detector.waitForApp(maxWaitMs = 30)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("elapsed=$elapsed should be >= 30", elapsed >= 30)
        assertTrue("elapsed=$elapsed should be < 200 (no long wait)", elapsed < 200)
    }

    @Test
    fun `zero max wait returns immediately`() = runBlocking {
        val detector = PreSplitDetector(
            foregroundChecker = { _, _ -> false },
            packageName = "com.test.app",
            pollIntervalMs = 10
        )
        val start = System.currentTimeMillis()
        val result = detector.waitForApp(maxWaitMs = 0)
        val elapsed = System.currentTimeMillis() - start
        assertFalse(result)
        assertTrue("Should return immediately, elapsed=$elapsed", elapsed < 100)
    }

    @Test
    fun `package name is used in log tag`() {
        val detector = PreSplitDetector(
            foregroundChecker = { _, _ -> false },
            packageName = "com.example.myapp",
            pollIntervalMs = 10
        )
        assertEquals("PreSplitDetector", detector.tag)
        assertEquals("com.example.myapp", detector.packageName)
    }
}
