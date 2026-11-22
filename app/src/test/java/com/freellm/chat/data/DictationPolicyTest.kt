package com.freellm.chat.data

import com.freellm.chat.data.dictation.DictationPolicy
import com.freellm.chat.data.dictation.EngineKind
import com.freellm.chat.data.dictation.VoiceInputMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DictationPolicyTest {
    @Test fun systemAlwaysSystem() {
        assertEquals(EngineKind.SYSTEM, DictationPolicy.choose(VoiceInputMode.SYSTEM, true, false, false))
    }
    @Test fun offlineFallsBackWithoutModel() {
        assertEquals(EngineKind.SYSTEM, DictationPolicy.choose(VoiceInputMode.OFFLINE, false, true, false))
        assertEquals(EngineKind.OFFLINE, DictationPolicy.choose(VoiceInputMode.OFFLINE, true, true, false))
    }
    @Test fun autoPrefersOfflineWhenNoNetworkOrMetered() {
        assertEquals(EngineKind.OFFLINE, DictationPolicy.choose(VoiceInputMode.AUTO, true, false, false))
        assertEquals(EngineKind.OFFLINE, DictationPolicy.choose(VoiceInputMode.AUTO, true, true, true))
        assertEquals(EngineKind.SYSTEM, DictationPolicy.choose(VoiceInputMode.AUTO, true, true, false))
        assertEquals(EngineKind.SYSTEM, DictationPolicy.choose(VoiceInputMode.AUTO, false, false, false))
    }
    @Test fun languageTag() {
        assertEquals("ar", DictationPolicy.languageTag("ar"))
        assertEquals("ar", DictationPolicy.languageTag("ar-SA"))
        assertNull(DictationPolicy.languageTag("en"))
        assertNull(DictationPolicy.languageTag(null))
    }
}
