package com.freellm.chat.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentMatchTest {
    @Test
    fun negationDiffers() {
        assertFalse(IntentMatch.same("how to install tool with docker", "how to install tool without docker"))
    }

    @Test
    fun identicalMatches() {
        assertTrue(IntentMatch.same("how to install tool", "how to install tool"))
    }

    @Test
    fun differentEntity() {
        assertFalse(IntentMatch.same("capital of france", "capital of germany"))
    }

    @Test
    fun differentNumber() {
        assertFalse(IntentMatch.same("convert 5 km to miles", "convert 6 km to miles"))
    }

    @Test
    fun punctuationIgnored() {
        assertTrue(IntentMatch.same("what is a kotlin coroutine", "what is a kotlin coroutine?"))
    }
}
