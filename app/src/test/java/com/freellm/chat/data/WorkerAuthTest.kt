package com.freellm.chat.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerAuthTest {
    private val proxy = "https://app.example.workers.dev/s3cretvalue"

    @Test fun parseExtractsSecret() {
        val p = WorkerAuth.parse(proxy)
        assertNotNull(p)
        assertEquals("s3cretvalue", p!!.secret)
        assertEquals("app.example.workers.dev", p.host)
    }

    @Test fun parseWithoutSecretIsNull() {
        assertNull(WorkerAuth.parse("https://app.example.workers.dev"))
        assertNull(WorkerAuth.parse("not a url"))
    }

    @Test fun splitMovesSecretOutOfPath() {
        val p = WorkerAuth.parse(proxy)
        val (url, secret) = WorkerAuth.split("https://app.example.workers.dev/s3cretvalue/groq/openai/v1/chat/completions".toHttpUrl(), p)
        assertEquals("s3cretvalue", secret)
        assertEquals("https://app.example.workers.dev/groq/openai/v1/chat/completions", url.toString())
    }

    @Test fun splitLeavesOtherHostsAlone() {
        val p = WorkerAuth.parse(proxy)
        val orig = "https://api.groq.com/s3cretvalue/x".toHttpUrl()
        val (url, secret) = WorkerAuth.split(orig, p)
        assertNull(secret)
        assertEquals(orig, url)
    }

    @Test fun splitLeavesNonSecretPathAlone() {
        val p = WorkerAuth.parse(proxy)
        val (_, secret) = WorkerAuth.split("https://app.example.workers.dev/groq/v1".toHttpUrl(), p)
        assertNull(secret)
    }

    @Test fun maskUrlHidesSecret() {
        assertEquals("https://app.example.workers.dev/***", WorkerAuth.maskUrl(proxy))
        assertEquals("https://app.example.workers.dev", WorkerAuth.maskUrl("https://app.example.workers.dev"))
    }

    @Test fun redactRemovesKeysAndSecrets() {
        val t = Redact.text("Authorization: Bearer abcdef123456789 key=AIzaSyA1234567890123456789 https://x.workers.dev/s3cretvalue/groq")
        assertFalse(t.contains("abcdef123456789"))
        assertFalse(t.contains("AIzaSyA"))
        assertFalse(t.contains("s3cretvalue"))
        assertTrue(t.contains("workers.dev/***"))
    }

    @Test fun redactExtraWords() {
        assertEquals("a *** b", Redact.text("a hunter2hunter b", listOf("hunter2hunter")))
    }
}
