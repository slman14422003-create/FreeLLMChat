package com.freellm.chat.data

import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.ModelDef
import com.freellm.chat.domain.Target
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.Pipe
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiClientStallTest {
    private val target = Target(
        providerId = "groq",
        providerName = "Groq",
        baseUrl = "https://direct.example/v1",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m", provider = "groq", displayName = "m", priority = 1)
    )

    @Test
    fun stalledStreamThrowsTimeout() {
        val pipe = Pipe(8192)
        pipe.source.timeout().timeout(3, TimeUnit.SECONDS)
        val sink = pipe.sink.buffer()
        sink.writeUtf8("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"hmm\"},\"finish_reason\":null}]}\n\n")
        sink.flush()
        val source = pipe.source.buffer()
        val body = object : ResponseBody() {
            override fun contentType() = "text/event-stream".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = source
        }
        val http = OkHttpClient.Builder().addInterceptor(Interceptor {
            Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok").body(body).build()
        }).build()
        val client = OpenAiClient(http, idleStallMs = 300L)
        var err: LlmException? = null
        try {
            runBlocking { client.stream(target, listOf(ChatMsg("user", "hi")), null).toList() }
        } catch (e: LlmException) {
            err = e
        }
        assertNotNull(err)
        assertEquals(ErrorKind.TIMEOUT, err!!.kind)
        assertEquals("stalled", err.message)
    }
}
