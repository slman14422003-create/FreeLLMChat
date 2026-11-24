package com.freellm.chat.data

import com.freellm.chat.domain.ErrorKind
import com.freellm.chat.domain.ChatMsg
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.ModelDef
import com.freellm.chat.domain.NetRoute
import com.freellm.chat.domain.Target
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class OpenAiClientRouteTest {
    private val sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"},\"finish_reason\":null}]}\n\ndata: [DONE]\n\n"

    private fun target(via: NetRoute = NetRoute.DIRECT, worker: Boolean = true) = Target(
        providerId = "groq",
        providerName = "Groq",
        baseUrl = "https://direct.example/v1",
        headers = emptyMap(),
        apiKey = "k",
        model = ModelDef(id = "m", provider = "groq", displayName = "m", priority = 1),
        altBaseUrl = if (worker) "https://worker.example/groq/v1" else "",
        via = via
    )

    private fun reply(req: Request, code: Int, body: String, type: String = "text/event-stream"): Response =
        Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("x")
            .body(body.toResponseBody(type.toMediaType()))
            .build()

    private fun client(handler: (Request) -> Response): OpenAiClient =
        OpenAiClient(OkHttpClient.Builder().addInterceptor(Interceptor { handler(it.request()) }).build())

    private fun collect(c: OpenAiClient, t: Target): Pair<List<String>, LlmException?> = runBlocking {
        try {
            c.stream(t, listOf(ChatMsg("user", "hi")), null).toList() to null
        } catch (e: LlmException) {
            emptyList<String>() to e
        }
    }

    @Test fun connectFlagIsSetForIoFailures() {
        val c = client { throw IOException("boom") }
        val (_, err) = collect(c, target())
        assertNotNull(err)
        assertEquals(ErrorKind.NETWORK, err!!.kind)
        assertTrue(err.connect)
    }

    @Test fun vpnRequiredMapsToNetworkWithoutConnect() {
        val c = client { throw IOException("vpn required") }
        val (_, err) = collect(c, target())
        assertNotNull(err)
        assertEquals(ErrorKind.NETWORK, err!!.kind)
        assertEquals("vpn required", err.message)
        assertFalse(err.connect)
    }

    @Test fun httpErrorsDoNotCarryConnectFlag() {
        val c = client { reply(it, 500, "oops", "text/plain") }
        val (_, err) = collect(c, target())
        assertEquals(ErrorKind.SERVER, err!!.kind)
        assertFalse(err.connect)
    }

    @Test fun regionBodyMapsToRegion() {
        val c = client { reply(it, 403, "{\"error\":\"unsupported_country_region_territory\"}", "application/json") }
        val (_, err) = collect(c, target())
        assertEquals(ErrorKind.REGION, err!!.kind)
    }

    @Test fun workerViaUsesAltBaseUrlAndTtfbHeader() {
        val seen = mutableListOf<Request>()
        val c = client {
            seen.add(it)
            reply(it, 200, sse)
        }
        val (out, err) = collect(c, target(NetRoute.WORKER))
        assertNull(err)
        assertEquals(listOf("Hi"), out)
        assertEquals("https://worker.example/groq/v1/chat/completions", seen.single().url.toString())
        assertNotNull(seen.single().header("x-freellm-ttfb"))
    }

    @Test fun directViaUsesBaseUrlWithoutTtfbHeader() {
        val seen = mutableListOf<Request>()
        val c = client {
            seen.add(it)
            reply(it, 200, sse)
        }
        val (out, err) = collect(c, target(NetRoute.DIRECT))
        assertNull(err)
        assertEquals(listOf("Hi"), out)
        assertEquals("https://direct.example/v1/chat/completions", seen.single().url.toString())
        assertNull(seen.single().header("x-freellm-ttfb"))
    }

    @Test fun streamIsSingleAttemptOnActiveBase() {
        val hosts = mutableListOf<String>()
        val c = client {
            hosts.add(it.url.host)
            throw IOException("down")
        }
        collect(c, target())
        assertEquals(listOf("direct.example"), hosts)
    }

    @Test fun testTriesDirectThenWorkerOnRegionBlock() {
        val hosts = mutableListOf<String>()
        val c = client {
            hosts.add(it.url.host)
            if (it.url.host == "direct.example") reply(it, 403, "unsupported_country_region_territory", "text/plain")
            else reply(it, 200, "{}", "application/json")
        }
        val err = runBlocking { c.test(target()) }
        assertNull(err)
        assertEquals(listOf("direct.example", "worker.example"), hosts)
    }

    @Test fun testKeepsTrailWhenBothFail() {
        val c = client { throw IOException("down") }
        val err = runBlocking { c.test(target()) }
        assertNotNull(err)
        val msg = err!!.message.orEmpty()
        assertTrue(msg.contains("direct.example"))
        assertTrue(msg.contains("worker.example"))
        assertTrue(msg.contains(" | "))
    }

    @Test fun testWithoutWorkerTriesOnlyDirect() {
        val hosts = mutableListOf<String>()
        val c = client {
            hosts.add(it.url.host)
            throw IOException("down")
        }
        val err = runBlocking { c.test(target(worker = false)) }
        assertNotNull(err)
        assertEquals(listOf("direct.example"), hosts)
        if (hosts.isEmpty()) fail()
    }
}
