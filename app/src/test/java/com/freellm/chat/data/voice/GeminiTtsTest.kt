package com.freellm.chat.data.voice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class GeminiTtsTest {
    private fun response(mime: String, bytes: ByteArray): String {
        val b64 = Base64.getEncoder().encodeToString(bytes)
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"$mime\",\"data\":\"$b64\"}}]}}]}"
    }

    @Test fun bodyHasModalitiesVoiceAndText() {
        val body = GeminiTts.buildBody("hello world", "Kore")
        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("hello world", root["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertTrue(body.contains("responseModalities"))
        assertTrue(body.contains("\"voiceName\":\"Kore\""))
    }

    @Test fun l16IsWrappedAsWav() {
        val (bytes, ext) = GeminiTts.parseAudio(response("audio/L16;rate=24000", byteArrayOf(1, 2, 3, 4)))
        assertEquals(48, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("wav", ext)
    }

    @Test fun wavIsReturnedUnchanged() {
        val raw = byteArrayOf(9, 8, 7, 6, 5)
        val (bytes, ext) = GeminiTts.parseAudio(response("audio/wav", raw))
        assertEquals(raw.toList(), bytes.toList())
        assertEquals("wav", ext)
    }

    @Test fun unsupportedMimeThrows() {
        try {
            GeminiTts.parseAudio(response("audio/ogg", byteArrayOf(1)))
            fail()
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("unsupported audio"))
        }
    }
}
