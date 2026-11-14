package com.freellm.chat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GroqTermsTest {
    @Test fun termsUrlPointsAtPlaygroundModel() {
        assertEquals("https://console.groq.com/playground?model=canopylabs/orpheus-v1-english", GroqVoice.termsUrl(false))
        assertEquals("https://console.groq.com/playground?model=canopylabs/orpheus-arabic-saudi", GroqVoice.termsUrl(true))
    }
}
