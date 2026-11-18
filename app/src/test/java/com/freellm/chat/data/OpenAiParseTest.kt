package com.freellm.chat.data

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiParseTest {
    private val c = OpenAiClient(OkHttpClient())

    @Test fun lengthFinishIsDetected() {
        val p = c.parseChunk("""{"choices":[{"delta":{"content":"x"},"finish_reason":"length"}]}""")!!
        assertTrue(p.finished)
        assertTrue(p.length)
        assertEquals("x", p.content)
    }

    @Test fun stopFinishIsNotLength() {
        val p = c.parseChunk("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""")!!
        assertTrue(p.finished)
        assertFalse(p.length)
    }

    @Test fun nullFinishMeansStillStreaming() {
        val p = c.parseChunk("""{"choices":[{"delta":{"content":"a"},"finish_reason":null}]}""")!!
        assertFalse(p.finished)
        assertFalse(p.length)
    }

    @Test fun errorChunkIsReported() {
        assertNotNull(c.parseChunk("""{"error":{"message":"boom"}}""")!!.error)
    }

    @Test fun garbageGivesNull() {
        assertNull(c.parseChunk("not json"))
    }
}
