package com.freellm.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class LocalSearchParseTest {
    @Test fun duckDuckGoRedirectIsUnwrapped() {
        val u = "https://duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fa%3Fb%3D1&rut=abc"
        assertEquals("https://example.com/a?b=1", LocalSearchParse.unwrap(u))
    }

    @Test fun bingRedirectIsUnwrapped() {
        val target = "https://example.org/page"
        val enc = "a1" + Base64.getUrlEncoder().withoutPadding().encodeToString(target.toByteArray())
        assertEquals(target, LocalSearchParse.unwrap("https://www.bing.com/ck/a?!&&p=x&u=$enc&ntb=1"))
    }

    @Test fun plainUrlIsUnchanged() {
        assertEquals("https://a.com/x", LocalSearchParse.unwrap("https://a.com/x"))
    }

    @Test fun parseDropsEngineHostsDuplicatesAndNonHttp() {
        val raw = """[
            {"title":"A","url":"https://duckduckgo.com/l/?uddg=https%3A%2F%2Fa.com%2F","snippet":"sa"},
            {"title":"A2","url":"https://a.com/","snippet":"dup"},
            {"title":"Ad","url":"https://duckduckgo.com/y.js?ad=1","snippet":""},
            {"title":"B","url":"javascript:void(0)","snippet":""},
            {"title":"","url":"https://b.com/p","snippet":"sb"}
        ]"""
        val out = LocalSearchParse.parse(raw, 5)
        assertEquals(2, out.size)
        assertEquals("https://a.com/", out[0].url)
        assertEquals("sa", out[0].snippet)
        assertEquals("b.com", out[1].title)
    }

    @Test fun parseHonorsMax() {
        val raw = (1..5).joinToString(",", "[", "]") { """{"title":"t$it","url":"https://s$it.com","snippet":""}""" }
        assertEquals(2, LocalSearchParse.parse(raw, 2).size)
    }

    @Test fun badJsonGivesEmpty() {
        assertTrue(LocalSearchParse.parse("nope", 3).isEmpty())
        assertTrue(LocalSearchParse.parse("{}", 3).isEmpty())
    }

    @Test fun evaluateResultIsDecodedFromJsString() {
        val inner = """[{"title":"x","url":"https://x.com","snippet":"y"}]"""
        val quoted = "\"" + inner.replace("\"", "\\\"") + "\""
        assertEquals(inner, LocalSearchParse.fromEvaluate(quoted))
        assertNull(LocalSearchParse.fromEvaluate("null"))
        assertNull(LocalSearchParse.fromEvaluate(null))
    }

    @Test fun googleRedirectIsUnwrapped() {
        assertEquals("https://e.com/x", LocalSearchParse.unwrap("https://www.google.com/url?q=https%3A%2F%2Fe.com%2Fx&sa=U"))
    }

    @Test fun googleIsTriedFirstAndOthersFollow() {
        val e = LocalSearchParse.engines("abc")
        assertTrue(e[0].contains("google.com/search?q=abc"))
        assertEquals(3, e.size)
    }

    @Test fun captchaAndConsentPagesAreDetected() {
        assertTrue(LocalSearchParse.isBlockedPage("https://www.google.com/sorry/index?continue=x"))
        assertTrue(LocalSearchParse.isBlockedPage("https://consent.google.com/m?continue=x"))
        assertTrue(!LocalSearchParse.isBlockedPage("https://www.google.com/search?q=a"))
        assertTrue(!LocalSearchParse.isBlockedPage(null))
    }
}
