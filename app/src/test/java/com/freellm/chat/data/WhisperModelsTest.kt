package com.freellm.chat.data

import com.freellm.chat.data.dictation.ModelLevel
import com.freellm.chat.data.dictation.ModelStore
import com.freellm.chat.data.dictation.VoiceInputMode
import com.freellm.chat.data.dictation.WhisperRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperModelsTest {
    @Test fun fileNamesAreUniqueAndBin() {
        val names = ModelLevel.values().map { it.fileName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.startsWith("ggml-") && it.endsWith(".bin") })
    }

    @Test fun urlsPointToWhisperCppRepo() {
        for (l in ModelLevel.values()) {
            assertTrue(ModelStore.url(l).startsWith("https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-"))
            assertTrue(ModelStore.pageUrl(l).contains("/blob/main/" + l.fileName))
        }
    }

    @Test fun quantizedAreSmaller() {
        assertTrue(ModelLevel.FAST_Q.approxBytes < ModelLevel.FAST.approxBytes)
        assertTrue(ModelLevel.BALANCED_Q.approxBytes < ModelLevel.BALANCED.approxBytes)
        assertTrue(ModelLevel.ACCURATE_Q.approxBytes < ModelLevel.ACCURATE.approxBytes)
        assertTrue(ModelLevel.MOST_ACCURATE_Q.approxBytes < ModelLevel.MOST_ACCURATE.approxBytes)
    }

    @Test fun defaultModelExists() {
        assertEquals(ModelLevel.BALANCED_Q, ModelStore.levelOf(ModelStore.DEFAULT))
        assertNull(ModelStore.levelOf("nope"))
    }

    @Test fun linkedEtagParsing() {
        val sha = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(sha, ModelStore.parseLinkedEtag("\"$sha\""))
        assertEquals(sha, ModelStore.parseLinkedEtag("W/\"" + sha.uppercase() + "\""))
        assertNull(ModelStore.parseLinkedEtag("abc"))
        assertNull(ModelStore.parseLinkedEtag(null))
    }

    @Test fun ggmlMagic() {
        assertTrue(ModelStore.isGgml(byteArrayOf(0x6c, 0x6d, 0x67, 0x67, 1)))
        assertFalse(ModelStore.isGgml(byteArrayOf(0x47, 0x47, 0x55, 0x46)))
        assertFalse(ModelStore.isGgml(byteArrayOf(0x6c)))
    }

    @Test fun pickPrefersSelectedThenFirstInstalled() {
        assertEquals("FAST", ModelStore.pick("FAST", listOf("BALANCED", "FAST")))
        assertEquals("BALANCED", ModelStore.pick("TURBO_Q", listOf("BALANCED", "FAST")))
        assertNull(ModelStore.pick("FAST", emptyList()))
    }

    @Test fun voiceModeParsing() {
        assertEquals(VoiceInputMode.SYSTEM, VoiceInputMode.parse("system"))
        assertEquals(VoiceInputMode.SYSTEM, VoiceInputMode.parse(null))
        assertEquals(VoiceInputMode.OFFLINE, VoiceInputMode.parse("offline"))
        assertEquals(VoiceInputMode.AUTO, VoiceInputMode.parse("auto"))
    }

    @Test fun decodeDropsBrokenBytes() {
        val ok = "مرحبا".toByteArray(Charsets.UTF_8)
        assertEquals("مرحبا", WhisperRuntime.decode(ok))
        val cut = ok.copyOf(ok.size - 1)
        assertFalse(WhisperRuntime.decode(cut).contains('\uFFFD'))
        assertEquals("", WhisperRuntime.decode(ByteArray(0)))
    }
}
