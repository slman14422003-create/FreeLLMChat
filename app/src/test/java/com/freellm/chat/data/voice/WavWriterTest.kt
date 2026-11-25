package com.freellm.chat.data.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class WavWriterTest {
    @Test fun headerIsStandardRiffWave() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = WavWriter.pcm16Mono(pcm)
        assertEquals(pcm.size + 44, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        val rate = (wav[24].toInt() and 0xFF) or ((wav[25].toInt() and 0xFF) shl 8) or ((wav[26].toInt() and 0xFF) shl 16) or ((wav[27].toInt() and 0xFF) shl 24)
        assertEquals(24000, rate)
        assertEquals(pcm.toList(), wav.copyOfRange(44, wav.size).toList())
    }
}
