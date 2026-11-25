package com.freellm.chat.data.dictation

enum class VoiceInputMode {
    SYSTEM, OFFLINE, AUTO;

    companion object {
        fun parse(v: String?): VoiceInputMode = when (v) {
            "offline" -> OFFLINE
            "auto" -> AUTO
            else -> SYSTEM
        }
    }
}

enum class EngineKind { SYSTEM, OFFLINE }

object DictationPolicy {
    fun choose(mode: VoiceInputMode, offlineInstalled: Boolean, networkAvailable: Boolean, metered: Boolean): EngineKind = when (mode) {
        VoiceInputMode.SYSTEM -> EngineKind.SYSTEM
        VoiceInputMode.OFFLINE -> if (offlineInstalled) EngineKind.OFFLINE else EngineKind.SYSTEM
        VoiceInputMode.AUTO ->
            if (offlineInstalled && (!networkAvailable || metered)) EngineKind.OFFLINE else EngineKind.SYSTEM
    }

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
