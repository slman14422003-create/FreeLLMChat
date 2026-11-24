package com.freellm.chat.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldenCheckTest {
    @Test fun passesWhenAllMustPresent() {
        assertTrue(GoldenCheck.pass("The capital is Rome.", listOf("rome"), emptyList()))
    }

    @Test fun failsWhenMustMissing() {
        assertFalse(GoldenCheck.pass("The capital is Paris.", listOf("Rome"), emptyList()))
    }

    @Test fun failsWhenMustNotPresent() {
        assertFalse(GoldenCheck.pass("The answer is 391 or 408.", listOf("408"), listOf("391")))
    }

    @Test fun arabicDiacriticsIgnored() {
        assertTrue(GoldenCheck.pass("\u0628\u0627\u0631\u064A\u0633\u064F", listOf("\u0628\u0627\u0631\u064A\u0633"), emptyList()))
    }
}
