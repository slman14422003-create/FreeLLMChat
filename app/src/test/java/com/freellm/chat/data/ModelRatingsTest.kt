package com.freellm.chat.data

import com.freellm.chat.domain.UsageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRatingsTest {
    private class Mem : UsageStore {
        val m = HashMap<String, String>()
        override fun read(key: String): String? = m[key]
        override fun write(key: String, value: String) {
            m[key] = value
        }
    }

    @Test fun noBonusBeforeEnoughSamples() {
        val r = ModelRatings(Mem())
        repeat(4) { r.outcome("k", false, null) }
        assertEquals(0, r.autoBonus("k"))
    }

    @Test fun frequentFailuresPenalize() {
        val r = ModelRatings(Mem())
        repeat(3) { r.outcome("k", true, 900) }
        repeat(4) { r.outcome("k", false, null) }
        assertEquals(-2, r.autoBonus("k"))
    }

    @Test fun reliableFastModelGetsBonus() {
        val r = ModelRatings(Mem())
        repeat(20) { r.outcome("k", true, 800) }
        assertEquals(1, r.autoBonus("k"))
    }

    @Test fun slowReliableModelGetsNoBonus() {
        val r = ModelRatings(Mem())
        repeat(20) { r.outcome("k", true, 6000) }
        assertEquals(0, r.autoBonus("k"))
    }

    @Test fun arabicRatingOnlyCountsForArabicQuestions() {
        val r = ModelRatings(Mem())
        r.rate("k", true)
        r.rateArabic("k", true)
        r.rateArabic("k", true)
        assertEquals(1, r.effective("k", false))
        assertEquals(3, r.effective("k", true))
    }

    @Test fun historyIsDecayedSoOldFailuresFade() {
        val r = ModelRatings(Mem())
        repeat(10) { r.outcome("k", false, null) }
        repeat(60) { r.outcome("k", true, 700) }
        assertTrue(r.autoBonus("k") >= 0)
    }
}
