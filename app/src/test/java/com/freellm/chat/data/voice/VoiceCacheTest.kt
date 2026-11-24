package com.freellm.chat.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class VoiceCacheTest {
    private fun tmp() = Files.createTempDirectory("vc").toFile()

    @Test fun keyIsStableAndSensitiveToEveryPart() {
        val c = VoiceCache(tmp())
        val k = c.key("edge", "v1", "hello")
        assertEquals(k, c.key("edge", "v1", "hello"))
        assertEquals(64, k.length)
        assertNotEquals(k, c.key("azure", "v1", "hello"))
        assertNotEquals(k, c.key("edge", "v2", "hello"))
        assertNotEquals(k, c.key("edge", "v1", "hello!"))
    }

    @Test fun putThenGetReturnsFile() {
        val c = VoiceCache(tmp())
        assertNull(c.get("k", "mp3"))
        val f = c.put("k", "mp3", ByteArray(10) { 1 })
        val g = c.get("k", "mp3")
        assertNotNull(g)
        assertEquals(f.absolutePath, g!!.absolutePath)
        assertEquals(10L, c.size())
    }

    @Test fun trimsOldestWhenOverCap() {
        val dir = tmp()
        val c = VoiceCache(dir, capBytes = 100, trimToBytes = 50)
        val a = c.put("a", "bin", ByteArray(40))
        a.setLastModified(1_000_000L)
        val b = c.put("b", "bin", ByteArray(40))
        b.setLastModified(2_000_000L)
        c.put("c", "bin", ByteArray(40))
        assertFalse(a.exists())
        assertFalse(b.exists())
        assertTrue(java.io.File(dir, "c.bin").exists())
        assertEquals(40L, c.size())
    }

    @Test fun clearEmptiesDirectory() {
        val c = VoiceCache(tmp())
        c.put("a", "bin", ByteArray(5))
        c.clear()
        assertEquals(0L, c.size())
    }
}
