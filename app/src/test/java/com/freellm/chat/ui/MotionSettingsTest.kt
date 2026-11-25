package com.freellm.chat.ui

import com.freellm.chat.data.REDUCE_MOTION_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MotionSettingsTest {
    @Test
    fun reduceMotionDefaultsToFalse() {
        assertFalse(REDUCE_MOTION_DEFAULT)
    }

    @Test
    fun motionDurations() {
        assertEquals(150, Motion.short)
        assertEquals(280, Motion.medium)
        assertEquals(450, Motion.long)
    }
}
