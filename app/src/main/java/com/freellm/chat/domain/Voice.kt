package com.freellm.chat.domain

enum class VadEvent { NONE, START, END, TOO_SHORT }

object VoiceThreshold {
    const val QUIET_MIN = 200.0
    const val BARGE_MIN = 900.0
    const val MAX = 3500.0

    fun compute(noise: Double, bargeIn: Boolean): Double =
        maxOf(if (bargeIn) BARGE_MIN else QUIET_MIN, noise * 3.0).coerceAtMost(MAX)
}

class VoiceVad(
    private val silenceMs: Int = 900,
    private val minSpeechMs: Int = 300,
    private val maxMs: Int = 30_000,
    minRms: Double = 320.0
) {
    private var threshold = minRms
    private var floor = 120.0
    private var speaking = false
    private var startMs = 0
    private var speechMs = 0
    private var silentMs = 0
    private var totalMs = 0

    var lastSpeechMs = 0
        private set
    var lastTotalMs = 0
        private set

    val isSpeaking: Boolean get() = speaking

    val speechRatio: Double get() = if (lastTotalMs <= 0) 0.0 else lastSpeechMs.toDouble() / lastTotalMs

    val currentThreshold: Double get() = maxOf(threshold, floor * 2.5)

    fun calibrate(noise: Double, bargeIn: Boolean) {
        floor = noise.coerceAtLeast(20.0)
        threshold = VoiceThreshold.compute(noise, bargeIn)
    }

    fun reset() {
        speaking = false
        startMs = 0
        speechMs = 0
        silentMs = 0
        totalMs = 0
    }

    private fun finish(): Boolean {
        lastSpeechMs = speechMs
        lastTotalMs = (totalMs - silentMs).coerceAtLeast(speechMs)
        val ok = speechMs >= minSpeechMs
        reset()
        return ok
    }

    fun feed(rms: Double, frameMs: Int): VadEvent {
        val loud = rms > maxOf(threshold, floor * 2.5)
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
            return if (finish()) VadEvent.END else VadEvent.TOO_SHORT
        }
        if (totalMs >= maxMs) {
            finish()
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

class Utterance(val wav: ByteArray, val durationMs: Int, val speechRatio: Double)

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

class SpeechSplitter(private val max: Int = 190, private val firstChars: Int = 0) {
    private val buf = StringBuilder()
    private val enders = setOf('.', '!', '?', '\u061F', '\n', '\u3002', '\uFF01', '\uFF1F')
    private val clause = enders + setOf(',', ';', ':', '\u060C', '\u061B')
    private var firstDone = firstChars <= 0

    private fun firstCut(s: String, limit: Int): Int {
        val min = firstChars * 3 / 5
        val top = minOf(limit, firstChars * 2)
        for (i in min until top) {
            if (s[i] in clause && i + 1 < s.length && s[i + 1].isWhitespace()) return i + 1
        }
        if (limit >= firstChars * 2) {
            val sp = s.lastIndexOf(' ', firstChars * 2)
            if (sp >= min) return sp
        }
        return -1
    }

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
        if (!firstDone) {
            val fc = firstCut(s, limit)
            if (fc > 0 && (cut < 0 || fc < cut)) cut = fc
        }
        if (cut <= 0) return emptyList()
        firstDone = true
        buf.delete(0, cut)
        return chunks(s.substring(0, cut))
    }

    fun flush(): List<String> {
        val s = buf.toString()
        buf.setLength(0)
        firstDone = true
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
                    if (rest[i - 1] in ".!?\u061F\u060C,;\u061B:") {
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

object VoiceTestAudio {
    fun wav(ms: Int = 600, rate: Int = 16_000): ByteArray {
        val n = rate * ms / 1000
        val pcm = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = (Math.sin(2.0 * Math.PI * 440.0 * i / rate) * 6000.0).toInt()
            pcm[i * 2] = v.toByte()
            pcm[i * 2 + 1] = (v shr 8).toByte()
        }
        return Wav.encode(pcm, rate)
    }
}

object GhostFilter {
    const val SHORT_MS = 1200
    const val LOW_RATIO = 0.35

    private val always = setOf(
        "thanks for watching",
        "\u0634\u0643\u0631\u0627 \u0644\u0644\u0645\u0634\u0627\u0647\u062f\u0629",
        "\u0627\u0634\u062a\u0631\u0643\u0648\u0627 \u0641\u064a \u0627\u0644\u0642\u0646\u0627\u0629",
        "\u062a\u0631\u062c\u0645\u0629 \u0646\u0627\u0646\u0633\u064a \u0642\u0646\u0642\u0631"
    )
    private val weak = setOf(
        "thank you",
        "thanks",
        "you",
        "bye",
        "\u0634\u0643\u0631\u0627",
        "\u0634\u0643\u0631\u0627 \u0644\u0643\u0645"
    )
    private val marks = Regex("[\\u064B-\\u065F]")

    fun normalize(t: String): String =
        marks.replace(t, "").trim().trim('.', '!', '?', '\u061F', ',', '\u060C', ' ').lowercase()

    fun isGhost(text: String, durationMs: Int, speechRatio: Double): Boolean {
        val s = normalize(text)
        if (s.length < 2) return true
        if (s in always) return true
        if (s !in weak) return false
        return durationMs < SHORT_MS || speechRatio < LOW_RATIO
    }
}

object SpeechLang {
    const val WHISPER_PROMPT = "Voice chat with an AI assistant. Terms: FreeLLM, Groq, Whisper, API, Kotlin, Android."

    private val names = mapOf(
        "arabic" to "ar", "english" to "en", "french" to "fr", "turkish" to "tr",
        "urdu" to "ur", "spanish" to "es", "german" to "de"
    )

    fun isoOf(name: String?): String? = name?.trim()?.lowercase()?.let { names[it] ?: if (it.length == 2) it else null }

    fun ui(code: String): String? = code.lowercase().takeIf { it == "ar" || it == "en" }

    fun whisper(setting: String, hint: String?): String =
        if (setting.isNotBlank() && setting != "auto") setting else hint ?: "auto"

    fun deviceTag(setting: String, hint: String?, default: String): String {
        val code = if (setting.isNotBlank() && setting != "auto") setting else hint
        return when (code) {
            null -> default
            "ar" -> "ar-SA"
            "en" -> "en-US"
            "fr" -> "fr-FR"
            "tr" -> "tr-TR"
            "ur" -> "ur-PK"
            "es" -> "es-ES"
            "de" -> "de-DE"
            else -> default
        }
    }
}

enum class CloseReason { NONE, FAILURES, SILENCE }

object SessionPolicy {
    const val MAX_FAILS = 3
    const val MAX_IDLE = 3
    const val IDLE_TIMEOUT_WEIGHT = 2

    fun closeReason(failMisses: Int, idleMisses: Int): CloseReason = when {
        failMisses >= MAX_FAILS -> CloseReason.FAILURES
        idleMisses >= MAX_IDLE -> CloseReason.SILENCE
        else -> CloseReason.NONE
    }
}
