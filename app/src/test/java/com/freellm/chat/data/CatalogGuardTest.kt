package com.freellm.chat.data

import com.freellm.chat.domain.Catalog
import com.freellm.chat.domain.ProviderDef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogGuardTest {
    private val bundled = Catalog(
        providers = listOf(ProviderDef("groq", "Groq", "https://api.groq.com/openai/v1"))
    )

    private fun remote(vararg p: ProviderDef) = Catalog(version = 9, providers = p.toList())

    @Test
    fun knownProviderKeepsBundledHost() {
        val out = CatalogGuard.sanitize(remote(ProviderDef("groq", "Groq", "https://evil.example.com/v1")), bundled)
        assertEquals("https://api.groq.com/openai/v1", out.providers.single().baseUrl)
    }

    @Test
    fun samePathChangeOnSameHostIsAllowed() {
        val out = CatalogGuard.sanitize(remote(ProviderDef("groq", "Groq", "https://api.groq.com/openai/v2")), bundled)
        assertEquals("https://api.groq.com/openai/v2", out.providers.single().baseUrl)
    }

    @Test
    fun newProviderMustUseHttps() {
        val out = CatalogGuard.sanitize(
            remote(
                ProviderDef("x", "X", "http://public.example.com/v1"),
                ProviderDef("y", "Y", "https://api.y.example.com/v1")
            ),
            bundled
        )
        assertEquals(listOf("y"), out.providers.map { it.id })
    }

    @Test
    fun lanHttpProviderIsKept() {
        val out = CatalogGuard.sanitize(remote(ProviderDef("lan", "Lan", "http://192.168.1.5:11434/v1")), bundled)
        assertEquals(1, out.providers.size)
    }

    @Test
    fun customProviderIsUntouched() {
        val out = CatalogGuard.sanitize(remote(ProviderDef("custom", "Custom", "", custom = true)), bundled)
        assertTrue(out.providers.single().custom)
    }

    @Test
    fun versionIsPreserved() {
        assertEquals(9, CatalogGuard.sanitize(remote(), bundled).version)
    }
}
