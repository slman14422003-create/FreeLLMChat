package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaverPolicyTest {
    @Test fun offNeverActive() {
        assertFalse(SaverPolicy.active("off", true, true))
        assertFalse(SaverPolicy.active(null, true, true))
        assertFalse(SaverPolicy.active("garbage", true, true))
    }

    @Test fun onAlwaysActive() {
        assertTrue(SaverPolicy.active("on", false, false))
    }

    @Test fun autoFollowsPowerSaveOrMetered() {
        assertFalse(SaverPolicy.active("auto", false, false))
        assertTrue(SaverPolicy.active("auto", true, false))
        assertTrue(SaverPolicy.active("auto", false, true))
    }

    @Test fun normalizeDefaultsToOff() {
        assertEquals("off", SaverPolicy.normalize("x"))
        assertEquals("auto", SaverPolicy.normalize("auto"))
    }

    @Test fun reduceMotionCombines() {
        assertTrue(SaverPolicy.reduceMotion(true, false))
        assertTrue(SaverPolicy.reduceMotion(false, true))
        assertFalse(SaverPolicy.reduceMotion(false, false))
    }
}
