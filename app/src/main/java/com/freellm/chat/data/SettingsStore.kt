package com.freellm.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class GenSettings(
    val contextSummary: Boolean,
    val smartAttachments: Boolean,
    val tokenSaver: Boolean,
    val smartRouting: Boolean,
    val cacheTtlHours: Int,
    val answerCache: Boolean,
    val style: String,
    val instructions: String,
    val facts: String,
    val brainLocalFirst: Boolean,
    val autoSwitch: Boolean,
    val deepThink: Boolean,
    val brainContext: Boolean,
    val localSearch: Boolean,
    val webSearch: Boolean?,
    val searchProvider: String,
    val proxyUrl: String,
    val searchLimits: Map<com.freellm.chat.domain.SearchKind, Int>,
    val searchCacheHours: Int,
    val toolsEnabled: Boolean,
    val toolsOff: Set<String>,
    val toolsMode: String,
    val answerTemperature: Boolean,
    val dynamicTokens: Boolean,
    val budgetAlerts: Boolean,
    val brainLearn: Boolean,
    val brainLearnMode: String,
    val verifyAnswers: Boolean,
    val verifyFactual: Boolean,
    val sideTasks: Boolean,
    val autoResume: Boolean
)

internal const val REDUCE_MOTION_DEFAULT = false

class SettingsStore(context: Context) {
    private val ds = context.applicationContext.dataStore

    private object K {
        val THEME = stringPreferencesKey("theme")
        val VOICE = stringPreferencesKey("voice_input")
        val WHISPER_MODEL = stringPreferencesKey("whisper_model")
        val WELCOME = booleanPreferencesKey("welcome_done")
        val ENABLED = stringSetPreferencesKey("enabled")
        val ORDER = stringPreferencesKey("order")
        val STATUSES = stringPreferencesKey("statuses")
        val SELECTION = stringPreferencesKey("selection")
        val PROXY = stringPreferencesKey("proxy_url")
        val PROXY_NOTICE = booleanPreferencesKey("proxy_notice_seen")
        val AUTO_SWITCH = booleanPreferencesKey("auto_switch")
        val STYLE = stringPreferencesKey("reply_style")
        val INSTRUCTIONS = stringPreferencesKey("instructions")
        val FACTS = stringPreferencesKey("personal_facts")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val TOKEN_SAVER = booleanPreferencesKey("token_saver")
        val BRAIN_LEARN = booleanPreferencesKey("brain_learn")
        val BRAIN_LEARN_MODE = stringPreferencesKey("brain_learn_mode")
        val BRAIN_LOCAL_FIRST = booleanPreferencesKey("brain_local_first")
        val BRAIN_CONTEXT = booleanPreferencesKey("brain_context")
        val DEEP_THINK = booleanPreferencesKey("deep_think")
        val AVOID_TRAINING = booleanPreferencesKey("avoid_training")
        val KEYLESS_OFF = stringSetPreferencesKey("keyless_off")
        val CONTEXT_SUMMARY = booleanPreferencesKey("context_summary")
        val ANSWER_CACHE = booleanPreferencesKey("answer_cache")
        val CACHE_TTL = stringPreferencesKey("cache_ttl_hours")
        val DYNAMIC_TOKENS = booleanPreferencesKey("dynamic_tokens")
        val SMART_ATTACH = booleanPreferencesKey("smart_attachments")
        val BUDGET_ALERTS = booleanPreferencesKey("budget_alerts")
        val SMART_ROUTING = booleanPreferencesKey("smart_routing")
        val COMPARE_MERGE = booleanPreferencesKey("compare_merge")
        val WEB_SEARCH = booleanPreferencesKey("web_search")
        val LOCAL_SEARCH = booleanPreferencesKey("local_search")
        val ANSWER_TEMPERATURE = booleanPreferencesKey("answer_temperature")
        val VERIFY_ANSWERS = booleanPreferencesKey("verify_answers")
        val VERIFY_FACTUAL = booleanPreferencesKey("verify_factual")
        val SIDE_TASKS = booleanPreferencesKey("side_tasks")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val SEARCH_CACHE_HOURS = stringPreferencesKey("search_cache_hours")
        val SEARCH_LIMITS = stringPreferencesKey("search_limits")
        val TOOLS = booleanPreferencesKey("tools_enabled")
        val FAST_SWITCH = booleanPreferencesKey("fast_switch")
        val TOOLS_MODE = stringPreferencesKey("tools_mode")
        val PROJECT_AUTOFIX = booleanPreferencesKey("project_autofix")
        val TOOLS_OFF = stringSetPreferencesKey("tools_off")
        val RUNNER_URL = stringPreferencesKey("runner_url")
        val NET_PATH = stringPreferencesKey("net_path")
        val SAVER = stringPreferencesKey("battery_saver")
        val VOICE_ENGINE = stringPreferencesKey("voice_engine")
        val VOICE_PRELOAD = stringPreferencesKey("voice_preload")
        val VOICE_FALLBACK = booleanPreferencesKey("voice_fallback")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val VOICE_EDGE_VOICE = stringPreferencesKey("voice_edge_voice")
        val VOICE_GEMINI_VOICE = stringPreferencesKey("voice_gemini_voice")
        val VOICE_GEMINI_MODEL = stringPreferencesKey("voice_gemini_model")
        val VOICE_AZURE_REGION = stringPreferencesKey("voice_azure_region")
        val VOICE_AZURE_VOICE = stringPreferencesKey("voice_azure_voice")
    }

    private val data: Flow<Preferences> = ds.data.catch { emit(emptyPreferences()) }

    val theme: Flow<String> = data.map { it[K.THEME] ?: "dark" }.distinctUntilChanged()
    val welcomeDone: Flow<Boolean> = data.map { it[K.WELCOME] ?: false }.distinctUntilChanged()
    val enabled: Flow<Set<String>> = data.map { it[K.ENABLED] ?: emptySet() }.distinctUntilChanged()
    val order: Flow<List<String>> = data
        .map { (it[K.ORDER] ?: "").split(",").filter { s -> s.isNotBlank() } }
        .distinctUntilChanged()
    val statuses: Flow<Map<String, String>> = data.map { decode(it[K.STATUSES]) }.distinctUntilChanged()
    val selection: Flow<String> = data.map { it[K.SELECTION] ?: "ADAPT" }.distinctUntilChanged()

    val proxyUrl: Flow<String> = data.map { it[K.PROXY]?.trim().orEmpty() }.distinctUntilChanged()

    val saverMode: Flow<String> = data.map { com.freellm.chat.domain.SaverPolicy.normalize(it[K.SAVER]) }.distinctUntilChanged()

    val netPath: Flow<String> = data.map { if (it[K.NET_PATH] == "vpn_only") "vpn_only" else "auto" }.distinctUntilChanged()

    val proxyNoticeSeen: Flow<Boolean> = data.map { it[K.PROXY_NOTICE] ?: false }.distinctUntilChanged()

    val autoSwitch: Flow<Boolean> = data.map { it[K.AUTO_SWITCH] ?: true }.distinctUntilChanged()
    val autoResume: Flow<Boolean> = data.map { it[K.AUTO_RESUME] ?: true }.distinctUntilChanged()
    val tokenSaver: Flow<Boolean> = data.map { it[K.TOKEN_SAVER] ?: true }.distinctUntilChanged()
    val brainLearn: Flow<Boolean> = data.map { it[K.BRAIN_LEARN] ?: true }.distinctUntilChanged()
    val brainLearnMode: Flow<String> = data.map { it[K.BRAIN_LEARN_MODE] ?: "rated" }.distinctUntilChanged()
    val brainLocalFirst: Flow<Boolean> = data.map { it[K.BRAIN_LOCAL_FIRST] ?: false }.distinctUntilChanged()
    val brainContext: Flow<Boolean> = data.map { it[K.BRAIN_CONTEXT] ?: false }.distinctUntilChanged()
    val avoidTraining: Flow<Boolean> = data.map { it[K.AVOID_TRAINING] ?: false }.distinctUntilChanged()
    val keylessOff: Flow<Set<String>> = data.map { it[K.KEYLESS_OFF] ?: emptySet() }.distinctUntilChanged()
    val deepThink: Flow<Boolean> = data.map { it[K.DEEP_THINK] ?: false }.distinctUntilChanged()
    val style: Flow<String> = data.map { it[K.STYLE] ?: "default" }.distinctUntilChanged()
    val facts: Flow<String> = data.map { it[K.FACTS] ?: "" }.distinctUntilChanged()
    val instructions: Flow<String> = data.map { it[K.INSTRUCTIONS] ?: "" }.distinctUntilChanged()

    val contextSummary: Flow<Boolean> = data.map { it[K.CONTEXT_SUMMARY] ?: true }.distinctUntilChanged()
    val answerCache: Flow<Boolean> = data.map { it[K.ANSWER_CACHE] ?: true }.distinctUntilChanged()
    val cacheTtlHours: Flow<Int> = data.map { it[K.CACHE_TTL]?.toIntOrNull()?.coerceIn(1, 720) ?: 24 }.distinctUntilChanged()
    val dynamicTokens: Flow<Boolean> = data.map { it[K.DYNAMIC_TOKENS] ?: true }.distinctUntilChanged()
    val smartAttachments: Flow<Boolean> = data.map { it[K.SMART_ATTACH] ?: true }.distinctUntilChanged()
    val smartRouting: Flow<Boolean> = data.map { it[K.SMART_ROUTING] ?: true }.distinctUntilChanged()
    val budgetAlerts: Flow<Boolean> = data.map { it[K.BUDGET_ALERTS] ?: true }.distinctUntilChanged()
    val compareMerge: Flow<Boolean> = data.map { it[K.COMPARE_MERGE] ?: true }.distinctUntilChanged()
    val answerTemperature: Flow<Boolean> = data.map { it[K.ANSWER_TEMPERATURE] ?: true }.distinctUntilChanged()
    val verifyAnswers: Flow<Boolean> = data.map { it[K.VERIFY_ANSWERS] ?: false }.distinctUntilChanged()
    val sideTasks: Flow<Boolean> = data.map { it[K.SIDE_TASKS] ?: true }.distinctUntilChanged()
    val verifyFactual: Flow<Boolean> = data.map { it[K.VERIFY_FACTUAL] ?: false }.distinctUntilChanged()
    val webSearch: Flow<Boolean?> = data.map { it[K.WEB_SEARCH] }.distinctUntilChanged()
    val localSearch: Flow<Boolean> = data.map { it[K.LOCAL_SEARCH] ?: false }.distinctUntilChanged()
    val searchProvider: Flow<String> = data.map { it[K.SEARCH_PROVIDER] ?: "tavily" }.distinctUntilChanged()
    val searchCacheHours: Flow<Int> = data.map { it[K.SEARCH_CACHE_HOURS]?.toIntOrNull()?.coerceIn(1, 168) ?: 6 }.distinctUntilChanged()
    val searchLimits: Flow<Map<com.freellm.chat.domain.SearchKind, Int>> =
        data.map { com.freellm.chat.domain.SearchLimits.decode(it[K.SEARCH_LIMITS]) }.distinctUntilChanged()

    val fastSwitchRaw: Flow<Boolean?> = data.map { it[K.FAST_SWITCH] }.distinctUntilChanged()
    val fastSwitch: Flow<Boolean> = fastSwitchRaw.map { it ?: true }.distinctUntilChanged()
    val toolsEnabled: Flow<Boolean> = data.map { it[K.TOOLS] ?: true }.distinctUntilChanged()
    val toolsMode: Flow<String> = data.map { it[K.TOOLS_MODE] ?: "smart" }.distinctUntilChanged()
    val projectAutoFix: Flow<Boolean> = data.map { it[K.PROJECT_AUTOFIX] ?: true }.distinctUntilChanged()
    val toolsOff: Flow<Set<String>> = data.map { it[K.TOOLS_OFF] ?: emptySet() }.distinctUntilChanged()
    val runnerUrl: Flow<String> = data.map { it[K.RUNNER_URL]?.trim().orEmpty() }.distinctUntilChanged()

    suspend fun snapshot(): GenSettings {
        val p = data.first()
        return GenSettings(
            contextSummary = p[K.CONTEXT_SUMMARY] ?: true,
            smartAttachments = p[K.SMART_ATTACH] ?: true,
            tokenSaver = p[K.TOKEN_SAVER] ?: true,
            smartRouting = p[K.SMART_ROUTING] ?: true,
            cacheTtlHours = p[K.CACHE_TTL]?.toIntOrNull()?.coerceIn(1, 720) ?: 24,
            answerCache = p[K.ANSWER_CACHE] ?: true,
            style = p[K.STYLE] ?: "default",
            instructions = p[K.INSTRUCTIONS] ?: "",
            facts = p[K.FACTS] ?: "",
            brainLocalFirst = p[K.BRAIN_LOCAL_FIRST] ?: false,
            autoSwitch = p[K.AUTO_SWITCH] ?: true,
            deepThink = p[K.DEEP_THINK] ?: false,
            brainContext = p[K.BRAIN_CONTEXT] ?: false,
            localSearch = p[K.LOCAL_SEARCH] ?: false,
            webSearch = p[K.WEB_SEARCH],
            searchProvider = p[K.SEARCH_PROVIDER] ?: "tavily",
            proxyUrl = p[K.PROXY]?.trim().orEmpty(),
            searchLimits = com.freellm.chat.domain.SearchLimits.decode(p[K.SEARCH_LIMITS]),
            searchCacheHours = p[K.SEARCH_CACHE_HOURS]?.toIntOrNull()?.coerceIn(1, 168) ?: 6,
            toolsEnabled = p[K.TOOLS] ?: true,
            toolsOff = p[K.TOOLS_OFF] ?: emptySet(),
            toolsMode = p[K.TOOLS_MODE] ?: "smart",
            answerTemperature = p[K.ANSWER_TEMPERATURE] ?: true,
            dynamicTokens = p[K.DYNAMIC_TOKENS] ?: true,
            budgetAlerts = p[K.BUDGET_ALERTS] ?: true,
            brainLearn = p[K.BRAIN_LEARN] ?: true,
            brainLearnMode = p[K.BRAIN_LEARN_MODE] ?: "rated",
            verifyAnswers = p[K.VERIFY_ANSWERS] ?: false,
            verifyFactual = p[K.VERIFY_FACTUAL] ?: false,
            sideTasks = p[K.SIDE_TASKS] ?: true,
            autoResume = p[K.AUTO_RESUME] ?: true
        )
    }

    private fun decode(s: String?): Map<String, String> =
        (s ?: "").split(",").mapNotNull { e ->
            val i = e.indexOf('=')
            if (i > 0) e.substring(0, i) to e.substring(i + 1) else null
        }.toMap()

    val voiceInput: Flow<String> = data.map { it[K.VOICE] ?: "system" }.distinctUntilChanged()

    suspend fun setVoiceInput(v: String) {
        ds.edit { it[K.VOICE] = v }
    }

    val voiceEngine: Flow<String> = data.map { it[K.VOICE_ENGINE] ?: "system" }.distinctUntilChanged()

    suspend fun setVoiceEngine(v: String) {
        ds.edit { it[K.VOICE_ENGINE] = v }
    }

    val voicePreload: Flow<String> = data.map { it[K.VOICE_PRELOAD] ?: "auto" }.distinctUntilChanged()

    suspend fun setVoicePreload(v: String) {
        ds.edit { it[K.VOICE_PRELOAD] = v }
    }

    val voiceFallback: Flow<Boolean> = data.map { it[K.VOICE_FALLBACK] ?: true }.distinctUntilChanged()

    suspend fun setVoiceFallback(v: Boolean) {
        ds.edit { it[K.VOICE_FALLBACK] = v }
    }

    val reduceMotion: Flow<Boolean> = data.map { it[K.REDUCE_MOTION] ?: REDUCE_MOTION_DEFAULT }.distinctUntilChanged()

    suspend fun setReduceMotion(v: Boolean) {
        ds.edit { it[K.REDUCE_MOTION] = v }
    }

    val textScale: Flow<Float> = data.map { (it[K.TEXT_SCALE] ?: 1f).coerceIn(0.85f, 1.3f) }.distinctUntilChanged()

    suspend fun setTextScale(v: Float) {
        ds.edit { it[K.TEXT_SCALE] = v.coerceIn(0.85f, 1.3f) }
    }

    val dynamicColor: Flow<Boolean> = data.map { it[K.DYNAMIC_COLOR] ?: false }.distinctUntilChanged()

    suspend fun setDynamicColor(v: Boolean) {
        ds.edit { it[K.DYNAMIC_COLOR] = v }
    }

    val voiceEdgeVoice: Flow<String> = data.map { it[K.VOICE_EDGE_VOICE] ?: "ar-SY-AmanyNeural" }.distinctUntilChanged()

    suspend fun setVoiceEdgeVoice(v: String) {
        ds.edit { it[K.VOICE_EDGE_VOICE] = v }
    }

    val voiceGeminiVoice: Flow<String> = data.map { it[K.VOICE_GEMINI_VOICE] ?: "Kore" }.distinctUntilChanged()

    suspend fun setVoiceGeminiVoice(v: String) {
        ds.edit { it[K.VOICE_GEMINI_VOICE] = v }
    }

    val voiceGeminiModel: Flow<String> = data.map { it[K.VOICE_GEMINI_MODEL] ?: "gemini-3.8-flash-lite-tts" }.distinctUntilChanged()

    suspend fun setVoiceGeminiModel(v: String) {
        ds.edit { it[K.VOICE_GEMINI_MODEL] = v }
    }

    val voiceAzureRegion: Flow<String> = data.map { it[K.VOICE_AZURE_REGION] ?: "westeurope" }.distinctUntilChanged()

    suspend fun setVoiceAzureRegion(v: String) {
        ds.edit { it[K.VOICE_AZURE_REGION] = v }
    }

    val voiceAzureVoice: Flow<String> = data.map { it[K.VOICE_AZURE_VOICE] ?: "ar-SY-AmanyNeural" }.distinctUntilChanged()

    suspend fun setVoiceAzureVoice(v: String) {
        ds.edit { it[K.VOICE_AZURE_VOICE] = v }
    }

    val whisperModel: Flow<String> = data.map { it[K.WHISPER_MODEL] ?: "BALANCED_Q" }.distinctUntilChanged()

    suspend fun setWhisperModel(v: String) {
        ds.edit { it[K.WHISPER_MODEL] = v }
    }

    suspend fun setTheme(v: String) {
        ds.edit { it[K.THEME] = v }
    }

    suspend fun setWelcomeDone() {
        ds.edit { it[K.WELCOME] = true }
    }

    suspend fun setEnabled(id: String, on: Boolean) {
        ds.edit { p ->
            val cur = p[K.ENABLED] ?: emptySet()
            p[K.ENABLED] = if (on) cur + id else cur - id
        }
    }

    suspend fun setStatus(id: String, status: String?) {
        ds.edit { p ->
            val m = decode(p[K.STATUSES]).toMutableMap()
            if (status == null) m.remove(id) else m[id] = status
            p[K.STATUSES] = m.entries.joinToString(",") { "${it.key}=${it.value}" }
        }
    }

    suspend fun setOrder(list: List<String>) {
        ds.edit { it[K.ORDER] = list.joinToString(",") }
    }

    suspend fun setProxyUrl(v: String) {
        ds.edit { it[K.PROXY] = v.trim() }
    }

    suspend fun setSaverMode(v: String) {
        ds.edit { it[K.SAVER] = com.freellm.chat.domain.SaverPolicy.normalize(v) }
    }

    suspend fun setNetPath(v: String) {
        ds.edit { it[K.NET_PATH] = if (v == "vpn_only") "vpn_only" else "auto" }
    }

    suspend fun setProxyNoticeSeen() {
        ds.edit { it[K.PROXY_NOTICE] = true }
    }

    suspend fun setSelection(v: String) {
        ds.edit { it[K.SELECTION] = v }
    }

    suspend fun setAutoSwitch(v: Boolean) {
        ds.edit { it[K.AUTO_SWITCH] = v }
    }

    suspend fun setBrainLearnMode(v: String) {
        ds.edit { it[K.BRAIN_LEARN_MODE] = v }
    }

    suspend fun setStyle(v: String) {
        ds.edit { it[K.STYLE] = v }
    }

    suspend fun setFacts(v: String) {
        ds.edit { it[K.FACTS] = v }
    }

    suspend fun setInstructions(v: String) {
        ds.edit { it[K.INSTRUCTIONS] = v }
    }

    suspend fun setAutoResume(v: Boolean) {
        ds.edit { it[K.AUTO_RESUME] = v }
    }

    suspend fun setTokenSaver(v: Boolean) {
        ds.edit { it[K.TOKEN_SAVER] = v }
    }

    suspend fun setBrainLearn(v: Boolean) {
        ds.edit { it[K.BRAIN_LEARN] = v }
    }

    suspend fun setBrainLocalFirst(v: Boolean) {
        ds.edit { it[K.BRAIN_LOCAL_FIRST] = v }
    }

    suspend fun setBrainContext(v: Boolean) {
        ds.edit { it[K.BRAIN_CONTEXT] = v }
    }

    suspend fun setAvoidTraining(v: Boolean) {
        ds.edit { it[K.AVOID_TRAINING] = v }
    }

    suspend fun setKeylessOff(id: String, off: Boolean) {
        ds.edit { p ->
            val cur = p[K.KEYLESS_OFF] ?: emptySet()
            p[K.KEYLESS_OFF] = if (off) cur + id else cur - id
        }
    }

    suspend fun exportAll(): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((k, v) in ds.data.first().asMap()) {
            if (k.name == K.WELCOME.name) continue
            when (v) {
                is String -> out["s:${k.name}"] = v
                is Boolean -> out["b:${k.name}"] = v
                is Set<*> -> out["m:${k.name}"] = v.filterIsInstance<String>()
            }
        }
        return out
    }

    suspend fun importAll(data: Map<String, Any>) {
        ds.edit { p ->
            for ((tagged, v) in data) {
                val i = tagged.indexOf(':')
                if (i != 1) continue
                val name = tagged.substring(2)
                when (tagged[0]) {
                    's' -> (v as? String)?.let { p[stringPreferencesKey(name)] = it }
                    'b' -> (v as? Boolean)?.let { p[booleanPreferencesKey(name)] = it }
                    'm' -> (v as? Collection<*>)?.let { c -> p[stringSetPreferencesKey(name)] = c.filterIsInstance<String>().toSet() }
                }
            }
        }
    }

    suspend fun setDeepThink(v: Boolean) {
        ds.edit { it[K.DEEP_THINK] = v }
    }

    suspend fun setContextSummary(v: Boolean) {
        ds.edit { it[K.CONTEXT_SUMMARY] = v }
    }

    suspend fun setAnswerCache(v: Boolean) {
        ds.edit { it[K.ANSWER_CACHE] = v }
    }

    suspend fun setCacheTtlHours(v: Int) {
        ds.edit { it[K.CACHE_TTL] = v.coerceIn(1, 720).toString() }
    }

    suspend fun setDynamicTokens(v: Boolean) {
        ds.edit { it[K.DYNAMIC_TOKENS] = v }
    }

    suspend fun setSmartAttachments(v: Boolean) {
        ds.edit { it[K.SMART_ATTACH] = v }
    }

    suspend fun setSmartRouting(v: Boolean) {
        ds.edit { it[K.SMART_ROUTING] = v }
    }

    suspend fun setBudgetAlerts(v: Boolean) {
        ds.edit { it[K.BUDGET_ALERTS] = v }
    }

    suspend fun setCompareMerge(v: Boolean) {
        ds.edit { it[K.COMPARE_MERGE] = v }
    }

    suspend fun setWebSearch(v: Boolean) {
        ds.edit { it[K.WEB_SEARCH] = v }
    }

    suspend fun setLocalSearch(v: Boolean) {
        ds.edit {
            it[K.LOCAL_SEARCH] = v
            if (v) it[K.WEB_SEARCH] = true
        }
    }

    suspend fun setSearchProvider(v: String) {
        ds.edit { it[K.SEARCH_PROVIDER] = v }
    }

    suspend fun setSearchCacheHours(v: Int) {
        ds.edit { it[K.SEARCH_CACHE_HOURS] = v.coerceIn(1, 168).toString() }
    }

    suspend fun setSearchLimit(kind: com.freellm.chat.domain.SearchKind, amount: Int) {
        ds.edit { p ->
            val cur = com.freellm.chat.domain.SearchLimits.decode(p[K.SEARCH_LIMITS]).toMutableMap()
            cur[kind] = amount.coerceIn(1, 1_000_000)
            p[K.SEARCH_LIMITS] = com.freellm.chat.domain.SearchLimits.encode(cur)
        }
    }

    suspend fun setAnswerTemperature(v: Boolean) {
        ds.edit { it[K.ANSWER_TEMPERATURE] = v }
    }

    suspend fun setSideTasks(v: Boolean) {
        ds.edit { it[K.SIDE_TASKS] = v }
    }

    suspend fun setVerifyFactual(v: Boolean) {
        ds.edit { it[K.VERIFY_FACTUAL] = v }
    }

    suspend fun setVerifyAnswers(v: Boolean) {
        ds.edit { it[K.VERIFY_ANSWERS] = v }
    }

    suspend fun setToolsEnabled(v: Boolean) {
        ds.edit { it[K.TOOLS] = v }
    }

    suspend fun setProjectAutoFix(v: Boolean) {
        ds.edit { it[K.PROJECT_AUTOFIX] = v }
    }

    suspend fun setToolsMode(v: String) {
        ds.edit { it[K.TOOLS_MODE] = v }
    }

    suspend fun setToolOff(id: String, off: Boolean) {
        ds.edit { p ->
            val cur = p[K.TOOLS_OFF] ?: emptySet()
            p[K.TOOLS_OFF] = if (off) cur + id else cur - id
        }
    }

    suspend fun setRunnerUrl(v: String) {
        ds.edit { it[K.RUNNER_URL] = v.trim() }
    }

    suspend fun setFastSwitch(v: Boolean) {
        ds.edit { it[K.FAST_SWITCH] = v }
    }
}
