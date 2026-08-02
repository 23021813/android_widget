package com.carlauncher.data.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VirtualActionsTest {

    @Test
    fun `ACTION_REST_MODE is defined with expected value`() {
        assertEquals("com.carlauncher.ACTION_REST_MODE", VirtualActions.ACTION_REST_MODE)
    }

    @Test
    fun `ACTION_REST_MODE is unique among virtual actions`() {
        val actions = setOf(
            VirtualActions.ACTION_HOME,
            VirtualActions.ACTION_SPLIT_VIEW,
            VirtualActions.ACTION_QUICK_MENU,
            VirtualActions.ACTION_REST_MODE
        )
        assertEquals(4, actions.size)
    }
}
