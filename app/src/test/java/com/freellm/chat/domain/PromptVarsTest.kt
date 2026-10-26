package com.freellm.chat.domain

import org.junit.Assert.assertEquals
import java.time.LocalDateTime
import org.junit.Test

class PromptVarsTest {
    @Test
    fun expandsPlaceholders() {
        val out = PromptVars.expand("{date} {time} {device} {language}", LocalDateTime.of(2026, 10, 26, 9, 5), "Pixel", "Arabic")
        assertEquals("2026-10-26 09:05 Pixel Arabic", out)
    }
}
