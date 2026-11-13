package com.freellm.chat.domain

enum class VadEvent { NONE, START, END, TOO_SHORT }

class VoiceVad(
    private val silenceMs: Int = 900,
    private val minSpeechMs: Int = 300,
    private val maxMs: Int = 30_000,
    private val minRms: Double = 320.0
) {
    private var floor = 120.0
    private var speaking = false
    private var startMs = 0
    private var speechMs = 0
    private var silentMs = 0
    private var totalMs = 0

    val isSpeaking: Boolean get() = speaking

    fun reset() {
        speaking = false
        startMs = 0
        speechMs = 0
        silentMs = 0
        totalMs = 0
    }

    fun feed(rms: Double, frameMs: Int): VadEvent {
        val loud = rms > maxOf(minRms, floor * 2.5)
        if (!speaking) {
            if (loud) {
                startMs += frameMs
                if (startMs >= START_MS) {
                    speaking = true
                    speechMs = startMs
                    silentMs = 0
                    totalMs = startMs
                    return VadEvent.START
                }
            } else {
                startMs = 0
                floor = floor * 0.95 + rms * 0.05
            }
            return VadEvent.NONE
        }
        totalMs += frameMs
        if (loud) {
            speechMs += frameMs
            silentMs = 0
        } else {
            silentMs += frameMs
        }
        if (silentMs >= silenceMs) {
            val ok = speechMs >= minSpeechMs
            reset()
            return if (ok) VadEvent.END else VadEvent.TOO_SHORT
        }
        if (totalMs >= maxMs) {
            reset()
            return VadEvent.END
        }
        return VadEvent.NONE
    }

    companion object {
        const val START_MS = 90

        fun rms(pcm: ShortArray, n: Int): Double {
            if (n <= 0) return 0.0
            var sum = 0.0
            for (i in 0 until n) {
                val v = pcm[i].toDouble()
                sum += v * v
            }
            return Math.sqrt(sum / n)
        }
    }
}

class WavInfo(val sampleRate: Int, val channels: Int, val bits: Int, val dataOffset: Int, val dataLength: Int)

object Wav {
    fun encode(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val out = ByteArray(44 + pcm.size)
        fun put32(o: Int, v: Int) {
            out[o] = v.toByte()
            out[o + 1] = (v shr 8).toByte()
            out[o + 2] = (v shr 16).toByte()
            out[o + 3] = (v shr 24).toByte()
        }
        fun put16(o: Int, v: Int) {
            out[o] = v.toByte()
            out[o + 1] = (v shr 8).toByte()
        }
        "RIFF".forEachIndexed { i, c -> out[i] = c.code.toByte() }
        put32(4, 36 + pcm.size)
        "WAVEfmt ".forEachIndexed { i, c -> out[8 + i] = c.code.toByte() }
        put32(16, 16)
        put16(20, 1)
        put16(22, channels)
        put32(24, sampleRate)
        put32(28, sampleRate * channels * 2)
        put16(32, channels * 2)
        put16(34, 16)
        "data".forEachIndexed { i, c -> out[36 + i] = c.code.toByte() }
        put32(40, pcm.size)
        System.arraycopy(pcm, 0, out, 44, pcm.size)
        return out
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun tag(b: ByteArray, o: Int, s: String): Boolean = o + s.length <= b.size && s.indices.all { b[o + it].toInt().toChar() == s[it] }

    fun parse(b: ByteArray): WavInfo? {
        if (b.size < 44 || !tag(b, 0, "RIFF") || !tag(b, 8, "WAVE")) return null
        var pos = 12
        var rate = 0
        var ch = 0
        var bits = 0
        while (pos + 8 <= b.size) {
            val size = le32(b, pos + 4)
            if (tag(b, pos, "fmt ") && pos + 8 + 16 <= b.size) {
                ch = le16(b, pos + 10)
                rate = le32(b, pos + 12)
                bits = le16(b, pos + 22)
            } else if (tag(b, pos, "data")) {
                if (rate <= 0 || ch <= 0 || bits != 16) return null
                val start = pos + 8
                val len = if (size < 0 || start + size > b.size) b.size - start else size
                return WavInfo(rate, ch, bits, start, len)
            }
            if (size < 0) return null
            pos += 8 + size + (size and 1)
        }
        return null
    }
}

class SpeechSplitter(private val max: Int = 190) {
    private val buf = StringBuilder()
    private val enders = setOf('.', '!', '?', '؟', '\n', '。', '!', '?')

    fun push(delta: String): List<String> {
        buf.append(delta)
        val s = buf.toString()
        var limit = s.length
        val fences = Regex("```").findAll(s).count()
        if (fences % 2 == 1) limit = s.lastIndexOf("```")
        var cut = -1
        for (i in limit - 1 downTo 0) {
            if (s[i] in enders && (i + 1 >= s.length || s[i + 1].isWhitespace() || s[i] == '\n')) {
                cut = i + 1
                break
            }
        }
        if (cut < 0 && limit >= max * 2) {
            val sp = s.lastIndexOf(' ', max)
            cut = if (sp > 20) sp else max
        }
        if (cut <= 0) return emptyList()
        buf.delete(0, cut)
        return chunks(s.substring(0, cut))
    }

    fun flush(): List<String> {
        val s = buf.toString()
        buf.setLength(0)
        return chunks(s)
    }

    private fun chunks(raw: String): List<String> {
        val plain = SpeechText.plain(raw)
        if (plain.isEmpty()) return emptyList()
        return split(plain, max)
    }

    companion object {
        fun split(text: String, max: Int): List<String> {
            val out = ArrayList<String>()
            var rest = text.trim()
            while (rest.length > max) {
                var cut = -1
                for (i in max downTo 1) {
                    if (rest[i - 1] in ".!?؟،,;؛:") {
                        cut = i
                        break
                    }
                }
                if (cut < max / 3) {
                    val sp = rest.lastIndexOf(' ', max)
                    cut = if (sp > 0) sp else max
                }
                out.add(rest.substring(0, cut).trim())
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) out.add(rest)
            return out.filter { it.isNotBlank() }
        }
    }
}
