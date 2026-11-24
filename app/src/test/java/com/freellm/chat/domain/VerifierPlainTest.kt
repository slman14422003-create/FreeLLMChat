package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierPlainTest {
    @Test fun runsOnlyForLongUnsourcedFactualAnswers() {
        assertTrue(Verifier.shouldVerifyPlain(true, Difficulty.HARD, TaskKind.GENERAL, false, true, 300))
        assertFalse(Verifier.shouldVerifyPlain(true, Difficulty.HARD, TaskKind.GENERAL, true, true, 300))
        assertFalse(Verifier.shouldVerifyPlain(true, Difficulty.HARD, TaskKind.CODE, false, true, 300))
        assertFalse(Verifier.shouldVerifyPlain(true, Difficulty.HARD, TaskKind.GENERAL, false, true, 50))
    }

    @Test fun parsesClaims() {
        assertEquals(2, Verifier.parse("- the capital is X\n- the year is Y")!!.unsupported.size)
        assertEquals(emptyList<String>(), Verifier.parse("OK")!!.unsupported)
    }
}
