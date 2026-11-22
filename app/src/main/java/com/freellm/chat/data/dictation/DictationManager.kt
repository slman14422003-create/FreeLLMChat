package com.freellm.chat.data.dictation

enum class VoiceInputMode { SYSTEM, OFFLINE, AUTO }

enum class EngineKind { SYSTEM, OFFLINE }

// Pure selection logic. Offline stays unavailable until a model is installed.
object DictationPolicy {
    fun choose(mode: VoiceInputMode, offlineInstalled: Boolean, networkAvailable: Boolean, metered: Boolean): EngineKind = when (mode) {
        VoiceInputMode.SYSTEM -> EngineKind.SYSTEM
        VoiceInputMode.OFFLINE -> if (offlineInstalled) EngineKind.OFFLINE else EngineKind.SYSTEM
        VoiceInputMode.AUTO ->
            if (offlineInstalled && (!networkAvailable || metered)) EngineKind.OFFLINE else EngineKind.SYSTEM
    }

    // Arabic only when the app locale is Arabic, else device default (null).
    fun languageTag(appLanguage: String?): String? = if (appLanguage?.lowercase()?.startsWith("ar") == true) "ar" else null
}

class DictationManager(
    private val system: () -> DictationEngine,
    private val offline: (() -> DictationEngine)? = null
) {
    fun engine(mode: VoiceInputMode, networkAvailable: Boolean, metered: Boolean): DictationEngine {
        val kind = DictationPolicy.choose(mode, offline != null, networkAvailable, metered)
        return if (kind == EngineKind.OFFLINE && offline != null) offline.invoke() else system()
    }
}
