package com.freellm.chat.data

import com.freellm.chat.domain.ProviderDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProxyRoutingTest {
    private val groq = ProviderDef("groq", "Groq", "https://api.groq.com/openai/v1")

    @Test fun emptyProxyMeansDirectOnly() {
        assertEquals("https://api.groq.com/openai/v1" to "", ProxyRouting.baseFor(groq, null, "", ""))
        assertEquals("https://api.groq.com/openai/v1" to "", ProxyRouting.baseFor(groq, "", "   ", ""))
    }

    @Test fun proxyRoutesKnownHostAndKeepsDirectFallback() {
        val (base, alt) = ProxyRouting.baseFor(groq, null, "https://p.example.dev/", "")
        assertEquals("https://p.example.dev/groq/openai/v1", base)
        assertEquals("https://api.groq.com/openai/v1", alt)
    }
}

class ChatSearchEscapeTest {
    @Test fun likeWildcardsAreEscaped() {
        assertEquals("50\\% off", escapeLike("50% off"))
        assertEquals("a\\_b", escapeLike("a_b"))
        assertEquals("c:\\\\dir", escapeLike("c:\\dir"))
        assertEquals("plain", escapeLike("plain"))
    }
}

class BackupPreviewTest {
    @Test fun providersCountOnlyKeyEntries() {
        val keys = setOf("key_groq", "key_gemini", "base_groq", "acct_cloudflare", "model_custom", "key_")
        assertEquals(2, BackupManager.countProviders(keys))
    }
}

class UiLangTest {
    @Test fun deviceArabicAndEnglishAreKept() {
        assertEquals("ar", UiLang.resolve(null, "ar"))
        assertEquals("en", UiLang.resolve(null, "en"))
    }

    @Test fun otherDeviceLanguagesFallBackToEnglish() {
        assertEquals("en", UiLang.resolve(null, "fr"))
        assertEquals("en", UiLang.resolve(null, "de"))
    }

    @Test fun manualOverrideWins() {
        assertEquals("ar", UiLang.resolve("ar", "en"))
        assertEquals("en", UiLang.resolve("en", "ar"))
        assertEquals("system", UiLang.resolve("system", "fr"))
    }
}

class CatalogChecksumTest {
    private val bytes = "{\"version\":1}".toByteArray(Charsets.UTF_8)

    @Test fun matchingChecksumIsAccepted() {
        val sum = CatalogGuard.sha256Hex(bytes)
        assertTrue(CatalogGuard.checksumMatches(bytes, sum))
        assertTrue(CatalogGuard.checksumMatches(bytes, sum + "  models.json\n"))
    }

    @Test fun mismatchMissingOrMalformedIsRejected() {
        assertFalse(CatalogGuard.checksumMatches(bytes, CatalogGuard.sha256Hex("other".toByteArray())))
        assertFalse(CatalogGuard.checksumMatches(bytes, null))
        assertFalse(CatalogGuard.checksumMatches(bytes, "abc"))
        assertFalse(CatalogGuard.checksumMatches(bytes, "z".repeat(64)))
    }
}

class LocalSearchConsentTest {
    @Test fun consentPagesAreDetected() {
        assertTrue(LocalSearchParse.isConsentText("Before you continue to Google Search"))
        assertTrue(LocalSearchParse.isConsentText("Our systems have detected unusual traffic"))
        assertFalse(LocalSearchParse.isConsentText("Kotlin coroutines guide - results"))
        assertFalse(LocalSearchParse.isConsentText(null))
    }
}
