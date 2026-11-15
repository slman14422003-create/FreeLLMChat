package com.freellm.chat.data

import com.freellm.chat.domain.Catalog
import com.freellm.chat.domain.ModelDef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class Round11DataTest {
    private val proxy = "https://proxy.example/groq/openai/v1"
    private val direct = "https://api.groq.com/openai/v1"

    @Test fun proxyFailureFallsThroughToDirect() = runTest {
        val seen = ArrayList<String>()
        val out = VoiceFallback.run(listOf("k"), listOf(proxy, direct)) { base, _ ->
            seen.add(base)
            if (base == proxy) throw VoiceErrors.map(502, "bad gateway") else "ok"
        }
        assertEquals("ok", out)
        assertEquals(listOf(proxy, direct), seen)
    }

    @Test fun everyNonAuthErrorFallsThrough() = runTest {
        for (code in listOf(400, 404, 500, 503)) {
            val out = VoiceFallback.run(listOf("k"), listOf(proxy, direct)) { base, _ ->
                if (base == proxy) throw VoiceErrors.map(code, "x") else "ok"
            }
            assertEquals("ok", out)
        }
        val net = VoiceFallback.run(listOf("k"), listOf(proxy, direct)) { base, _ ->
            if (base == proxy) throw VoiceException(VoiceFail.NETWORK) else "ok"
        }
        assertEquals("ok", net)
    }

    @Test fun unauthorizedSkipsDirectAndTriesNextKey() = runTest {
        val seen = ArrayList<String>()
        val out = VoiceFallback.run(listOf("bad", "good"), listOf(proxy, direct)) { base, key ->
            seen.add("$key@$base")
            if (key == "bad") throw VoiceErrors.map(401, "no") else "ok"
        }
        assertEquals("ok", out)
        assertEquals(listOf("bad@$proxy", "good@$proxy"), seen)
    }

    @Test fun rateLimitWithAllKeysExhaustedThrows() = runTest {
        try {
            VoiceFallback.run(listOf("a", "b"), listOf(proxy, direct)) { _, _ -> throw VoiceErrors.map(429, "slow") }
            fail()
        } catch (e: VoiceException) {
            assertEquals(VoiceFail.RATE, e.kind)
            assertEquals(429, e.httpCode)
        }
    }

    @Test fun missingKeyIsReported() = runTest {
        try {
            VoiceFallback.run(emptyList(), listOf(direct)) { _, _ -> "x" }
            fail()
        } catch (e: VoiceException) {
            assertEquals(VoiceFail.NO_KEY, e.kind)
        }
    }

    @Test fun termsDetectionIsStructured() {
        val real = """{"error":{"message":"The model requires terms acceptance. Please accept the terms","type":"invalid_request_error","code":"model_terms_required"}}"""
        assertEquals(VoiceFail.TERMS, VoiceErrors.map(400, real).kind)
        assertEquals(VoiceFail.OTHER, VoiceErrors.map(502, "<html>Terms of service gateway error</html>").kind)
        assertEquals(VoiceFail.OTHER, VoiceErrors.map(400, """{"error":{"message":"bad terms in field","code":"x"}}""").kind)
    }

    @Test fun diagnosticsKeepCodeAndRedactSecrets() {
        val e = VoiceErrors.map(502, "upstream failed for Bearer gsk_abcdef123456 key gsk_zzzzzzzzzz")
        assertEquals(502, e.httpCode)
        assertFalse(e.snippet.contains("gsk_"))
        assertTrue(e.snippet.length <= 100)
    }

    @Test fun expiredModelsArePruned() {
        val cat = Catalog(
            models = listOf(
                ModelDef("a", "p", "a", 1, expiresAt = "2026-01-01"),
                ModelDef("b", "p", "b", 2, expiresAt = "2027-01-01"),
                ModelDef("c", "p", "c", 3)
            )
        )
        val out = CatalogGuard.prune(cat, LocalDate.parse("2026-11-15"))
        assertEquals(listOf("b", "c"), out.models.map { it.id })
    }
}
