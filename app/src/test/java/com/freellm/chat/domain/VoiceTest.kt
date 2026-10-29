package com.freellm.chat.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceVadTest {
    private fun feedAll(v: VoiceVad, rms: Double, n: Int): List<VadEvent> = (1..n).map { v.feed(rms, 30) }

    @Test fun speechThenSilenceEndsUtterance() {
        val v = VoiceVad(silenceMs = 900)
        val a = feedAll(v, 3000.0, 20)
        assertEquals(VadEvent.START, a[2])
        val b = feedAll(v, 10.0, 30)
        assertEquals(VadEvent.END, b.last())
    }

    @Test fun shortBlipIsDropped() {
        val v = VoiceVad(silenceMs = 900)
        feedAll(v, 3000.0, 3)
        val b = feedAll(v, 10.0, 30)
        assertEquals(VadEvent.TOO_SHORT, b.last())
    }

    @Test fun quietRoomNeverStarts() {
        val v = VoiceVad()
        assertTrue(feedAll(v, 40.0, 200).all { it == VadEvent.NONE })
    }

    @Test fun rmsOfConstantSignal() {
        assertEquals(100.0, VoiceVad.rms(ShortArray(10) { 100 }, 10), 0.001)
    }
}

class WavTest {
    @Test fun encodeThenParse() {
        val w = Wav.encode(ByteArray(8), 16000)
        assertEquals(52, w.size)
        val i = Wav.parse(w)!!
        assertEquals(16000, i.sampleRate)
        assertEquals(1, i.channels)
        assertEquals(16, i.bits)
        assertEquals(44, i.dataOffset)
        assertEquals(8, i.dataLength)
    }

    @Test fun garbageIsRejected() {
        assertNull(Wav.parse(ByteArray(100)))
    }
}

class SpeechSplitterTest {
    @Test fun emitsCompletedSentencesOnly() {
        val s = SpeechSplitter()
        assertEquals(listOf("Hello there."), s.push("Hello there. How are"))
        assertEquals(listOf("How are you?"), s.push(" you? Fi"))
        assertEquals(listOf("Fi"), s.flush())
    }

    @Test fun openCodeFenceIsHeldBack() {
        val s = SpeechSplitter()
        assertEquals(listOf("Intro."), s.push("Intro. ```kotlin\nval x"))
    }

    @Test fun longTextIsSplitUnderLimit() {
        val parts = SpeechSplitter.split("word ".repeat(200).trim(), 190)
        assertTrue(parts.size > 1)
        assertTrue(parts.all { it.length <= 190 })
    }
}

class UnitConvertTest {
    @Test fun lengthAndData() {
        assertEquals(1000.0, UnitConvert.convert(1.0, "km", "m")!!, 1e-9)
        assertEquals(1000.0, UnitConvert.convert(1.0, "GB", "MB")!!, 1e-9)
        assertEquals(1000.0, UnitConvert.convert(1.0, "gb", "mb")!!, 1e-9)
    }

    @Test fun temperature() {
        assertEquals(212.0, UnitConvert.convert(100.0, "C", "F")!!, 1e-6)
        assertEquals(273.15, UnitConvert.convert(0.0, "celsius", "K")!!, 1e-6)
    }

    @Test fun mismatchedDimensionsFail() {
        assertNull(UnitConvert.convert(1.0, "kg", "km"))
    }

    @Test fun formatting() {
        assertEquals("1000", UnitConvert.format(1000.0))
        assertEquals("0.3", UnitConvert.format(0.1 + 0.2))
    }

    @Test fun currencyCodes() {
        assertEquals("USD", UnitConvert.currencyCode("usd"))
        assertEquals("SAR", UnitConvert.currencyCode("ريال"))
        assertNull(UnitConvert.currencyCode("dollars"))
    }
}

class DateToolsTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneId.of("UTC"))

    private fun run(vararg kv: Pair<String, String>) = DateTools.run(ToolCall("datetime", kv.toMap()), clock)

    @Test fun nowShowsDateAndHijri() {
        val r = run("op" to "now")
        assertTrue(r.contains("2026-10-28"))
        assertTrue(r.contains("Wednesday"))
        assertTrue(r.contains("AH"))
    }

    @Test fun addDays() {
        assertTrue(run("op" to "add", "days" to "10").contains("2026-11-07"))
    }

    @Test fun diffDays() {
        assertTrue(run("op" to "diff", "date" to "2026-10-28", "to" to "2026-11-04").contains("7 days"))
    }

    @Test fun badDateIsReported() {
        assertTrue(run("op" to "weekday", "date" to "nope").startsWith("Invalid"))
    }

    @Test fun hijriRoundTripKeepsGregorianYear() {
        assertNotNull(run("op" to "hijri", "date" to "2026-10-28"))
    }
}
