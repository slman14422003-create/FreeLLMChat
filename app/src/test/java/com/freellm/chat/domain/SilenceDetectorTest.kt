package com.freellm.chat.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilenceDetectorTest {
    @Test
    fun noSpeechNeverEnds() {
        val d = SilenceDetector()
        for (t in 0..5000 step 100) assertFalse(d.update(0f, t.toLong()))
    }

    @Test
    fun endsAfterSilenceFollowingSpeech() {
        val d = SilenceDetector()
        for (t in 0..1000 step 100) assertFalse(d.update(0.6f, t.toLong()))
        assertFalse(d.update(0f, 1100))
        assertFalse(d.update(0f, 2000))
        assertTrue(d.update(0f, 2200))
    }

    @Test
    fun firesOnlyOnce() {
        val d = SilenceDetector()
        d.update(0.6f, 0)
        d.update(0.6f, 300)
        assertTrue(d.update(0f, 1600))
        assertFalse(d.update(0f, 1800))
        assertFalse(d.update(0f, 3000))
    }

    @Test
    fun shortBlipIsIgnored() {
        val d = SilenceDetector()
        d.update(0.8f, 0)
        for (t in 50..4000 step 50) assertFalse(d.update(0f, t.toLong()))
        assertFalse(d.hasSpoken())
    }

    @Test
    fun speechResetsSilenceTimer() {
        val d = SilenceDetector()
        d.update(0.5f, 0)
        d.update(0.5f, 300)
        assertFalse(d.update(0f, 1000))
        assertFalse(d.update(0.5f, 1200))
        assertFalse(d.update(0f, 2000))
        assertTrue(d.update(0f, 2500))
    }

    @Test
    fun resetAllowsNewUtterance() {
        val d = SilenceDetector()
        d.update(0.5f, 0)
        d.update(0.5f, 300)
        assertTrue(d.update(0f, 1600))
        d.reset()
        assertFalse(d.hasSpoken())
        d.update(0.5f, 2000)
        d.update(0.5f, 2300)
        assertTrue(d.update(0f, 3600))
    }
}
