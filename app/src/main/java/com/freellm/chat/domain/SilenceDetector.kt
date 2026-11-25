package com.freellm.chat.domain

class SilenceDetector(
    private val threshold: Float = 0.12f,
    private val silenceMs: Long = 1200L,
    private val minSpeechMs: Long = 200L
) {
    private var speechStart = -1L
    private var lastLoud = -1L
    private var spoke = false
    private var fired = false

    fun reset() {
        speechStart = -1L
        lastLoud = -1L
        spoke = false
        fired = false
    }

    fun hasSpoken(): Boolean = spoke

    fun update(level: Float, nowMs: Long): Boolean {
        if (fired) return false
        if (level >= threshold) {
            if (speechStart < 0) speechStart = nowMs
            lastLoud = nowMs
            if (nowMs - speechStart >= minSpeechMs) spoke = true
            return false
        }
        if (!spoke) {
            speechStart = -1L
            return false
        }
        if (nowMs - lastLoud >= silenceMs) {
            fired = true
            return true
        }
        return false
    }
}
