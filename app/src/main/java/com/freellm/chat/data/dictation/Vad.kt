package com.freellm.chat.data.dictation

import kotlin.math.sqrt

class Vad(
    private val sampleRate: Int = 16000,
    private val threshold: Float = 0.015f,
    private val silenceMs: Int = 700,
    private val maxSegmentMs: Int = 25_000,
    private val minSpeechMs: Int = 200
) {
    private val buf = ArrayList<FloatArray>()
    private var samples = 0
    private var silent = 0
    private var speech = 0

    private fun ms(n: Int) = n * 1000 / sampleRate

    fun rms(frame: FloatArray): Float {
        if (frame.isEmpty()) return 0f
        var s = 0.0
        for (x in frame) s += x * x
        return sqrt(s / frame.size).toFloat()
    }

    fun push(frame: FloatArray): FloatArray? {
        val loud = rms(frame) >= threshold
        if (buf.isEmpty() && !loud) return null
        buf.add(frame)
        samples += frame.size
        if (loud) { speech += frame.size; silent = 0 } else silent += frame.size
        return if (ms(silent) >= silenceMs || ms(samples) >= maxSegmentMs) flush() else null
    }

    fun flush(): FloatArray? {
        val enough = ms(speech) >= minSpeechMs
        val out = if (enough) FloatArray(samples).also { o ->
            var p = 0
            for (f in buf) { f.copyInto(o, p); p += f.size }
        } else null
        buf.clear(); samples = 0; silent = 0; speech = 0
        return out
    }
}
