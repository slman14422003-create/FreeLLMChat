package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryImagesTest {
    private val history = listOf(
        ChatMsg("user", "old", listOf("data:image/jpeg;base64,AAA")),
        ChatMsg("assistant", "seen"),
        ChatMsg("user", "new question")
    )

    @Test fun imagesAreDroppedWhenAnyCandidateLacksVision() {
        val keep = HistoryImages.keep(false, listOf(true, false))
        assertFalse(keep)
        val out = HistoryImages.prepare(history, keep, "[image omitted]")
        assertTrue(out.all { it.images.isEmpty() })
        assertTrue(out[0].content.startsWith("[image omitted]"))
        assertEquals("new question", out[2].content)
    }

    @Test fun imagesAreKeptWhenEveryCandidateSupportsVision() {
        val keep = HistoryImages.keep(false, listOf(true, true))
        assertTrue(keep)
        assertEquals(history, HistoryImages.prepare(history, keep, "x"))
    }

    @Test fun lastMessageImagesAlwaysKeepHistory() {
        assertTrue(HistoryImages.keep(true, listOf(false)))
        assertFalse(HistoryImages.keep(false, emptyList()))
    }
}

class ResumePolicyTest {
    @Test fun structuralFailuresDoNotResume() {
        assertTrue(ResumePolicy.skip(FailKind.NO_PROVIDER, emptyList()))
        assertTrue(ResumePolicy.skip(FailKind.NO_VISION, emptyList()))
        assertTrue(ResumePolicy.skip(FailKind.PINNED_MISSING, emptyList()))
    }

    @Test fun authAndPolicyDoNotResume() {
        assertTrue(ResumePolicy.skip(FailKind.ALL_FAILED, listOf(FailReason("a", ReasonKind.ERROR, ErrorKind.AUTH))))
        assertTrue(ResumePolicy.skip(FailKind.ALL_FAILED, listOf(FailReason("a", ReasonKind.ERROR, ErrorKind.POLICY))))
    }

    @Test fun transientFailuresResume() {
        assertFalse(ResumePolicy.skip(FailKind.ALL_FAILED, listOf(FailReason("a", ReasonKind.ERROR, ErrorKind.NETWORK))))
        assertFalse(ResumePolicy.skip(FailKind.ALL_FAILED, listOf(FailReason("a", ReasonKind.COOLING))))
    }
}

class XmlCheckTest {
    @Test fun wellFormedXmlPasses() {
        assertNull(XmlCheck.check("<a><b>1</b></a>"))
    }

    @Test fun brokenXmlReportsPosition() {
        assertTrue(XmlCheck.check("<a><b></a>")!!.contains("line"))
    }

    @Test fun doctypeAndEntitiesAreRejected() {
        val xxe = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>"
        assertTrue(XmlCheck.hasDoctype(xxe))
        assertTrue(XmlCheck.check(xxe)!!.contains("not allowed"))
    }
}

class FetchGuardHostTest {
    @Test fun hostParsingIgnoresCredentialsPortAndWww() {
        assertEquals("example.com", FetchGuard.hostOf("https://user:pw@www.Example.com:8443/a?b=1"))
        assertNull(FetchGuard.hostOf("ftp://example.com"))
    }
}
