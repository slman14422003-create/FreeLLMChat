package com.freellm.chat.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {
    @Test
    fun spacingIsOrdered() {
        assertTrue(Spacing.xs < Spacing.sm)
        assertTrue(Spacing.sm < Spacing.md)
        assertTrue(Spacing.md < Spacing.lg)
        assertTrue(Spacing.lg < Spacing.xl)
    }

    @Test
    fun cornersAreOrdered() {
        assertTrue(Corners.xs < Corners.sm)
        assertTrue(Corners.sm < Corners.md)
        assertTrue(Corners.md < Corners.lg)
        assertTrue(Corners.lg < Corners.xl)
    }
}
