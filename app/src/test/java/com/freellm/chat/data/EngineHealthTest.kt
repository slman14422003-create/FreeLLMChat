package com.freellm.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineHealthTest {
    @Test fun blockedEngineMovesToEndUntilCooldownEnds() {
        var now = 0L
        val h = EngineHealth({ now }, 1000L)
        assertEquals(listOf(0, 1, 2), h.order(3))
        h.block(0)
        assertEquals(listOf(1, 2, 0), h.order(3))
        now = 1001L
        assertEquals(listOf(0, 1, 2), h.order(3))
    }

    @Test fun okClearsBlock() {
        val h = EngineHealth({ 0L }, 1000L)
        h.block(1)
        h.ok(1)
        assertEquals(listOf(0, 1, 2), h.order(3))
    }
}
