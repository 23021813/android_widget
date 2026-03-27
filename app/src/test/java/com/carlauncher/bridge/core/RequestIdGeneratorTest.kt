package com.carlauncher.bridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RequestIdGeneratorTest {
    @Test
    fun `generates unique ids sequentially`() {
        val id1 = RequestIdGenerator.next()
        val id2 = RequestIdGenerator.next()
        
        assertNotEquals(id1, id2)
        assertEquals((id1.toInt() + 1).toString(), id2)
    }
}
