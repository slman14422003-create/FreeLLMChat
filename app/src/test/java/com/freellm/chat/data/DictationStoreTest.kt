package com.freellm.chat.data

import com.freellm.chat.data.dictation.ModelLevel
import com.freellm.chat.data.dictation.ModelStore
import com.freellm.chat.data.dictation.Vad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DictationStoreTest {
    private fun tone(ms: Int, amp: Float) = FloatArray(16 * ms) { amp }

    @Test fun vadCutsAfterSilence() {
        val v = Vad()
        var seg: FloatArray? = null
        repeat(10) { assertNull(v.push(tone(100, 0.2f))) }
        repeat(8) { if (seg == null) seg = v.push(tone(100, 0f)) }
        assertNotNull(seg)
        assertTrue(seg!!.size >= 16 * 1000)
    }

    @Test fun vadCapsSegmentLength() {
        val v = Vad()
        var seg: FloatArray? = null
        for (i in 0 until 300) { seg = v.push(tone(100, 0.2f)); if (seg != null) break }
        assertNotNull(seg)
        assertTrue(seg!!.size <= 16 * 25_100)
    }

    @Test fun vadDropsShortBlips() {
        val v = Vad()
        v.push(tone(50, 0.2f))
        assertNull(v.flush())
    }

    @Test fun resumeAndRange() {
        val f = File.createTempFile("m", ".part").apply { writeBytes(ByteArray(10)); deleteOnExit() }
        assertEquals(10L, ModelStore.resumeOffset(f))
        assertEquals("bytes=10-", ModelStore.rangeHeader(10))
        assertNull(ModelStore.rangeHeader(0))
        assertEquals("append", ModelStore.responseAction(206))
        assertEquals("restart", ModelStore.responseAction(200))
        assertNull(ModelStore.responseAction(416))
    }

    @Test fun checksumVerification() {
        val f = File.createTempFile("m", ".bin").apply { writeText("abc"); deleteOnExit() }
        val good = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(ModelStore.verify(f, good))
        assertFalse(ModelStore.verify(f, "00"))
    }

    @Test fun spaceAndRamChecks() {
        assertTrue(ModelStore.hasSpace(200L shl 20, 140L shl 20, 0))
        assertFalse(ModelStore.hasSpace(100L shl 20, 140L shl 20, 0))
        assertTrue(ModelStore.needsRamWarning(ModelLevel.MOST_ACCURATE, 4096))
        assertFalse(ModelStore.needsRamWarning(ModelLevel.FAST, 2048))
    }
}
