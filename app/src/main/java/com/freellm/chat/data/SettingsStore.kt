package com.freellm.chat.data

import android.content.Context
import com.freellm.chat.BuildConfig
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

class SettingsStore(context: Context) {
    private val ds = context.applicationContext.dataStore

    private object K {
        val THEME = stringPreferencesKey("theme")
        val WELCOME = booleanPreferencesKey("welcome_done")
        val ENABLED = stringSetPreferencesKey("enabled")
        val ORDER = stringPreferencesKey("order")
        val STATUSES = stringPreferencesKey("statuses")
        val SELECTION = stringPreferencesKey("selection")
        val PROXY = stringPreferencesKey("proxy_url")
        val AUTO_SWITCH = booleanPreferencesKey("auto_switch")
        val STYLE = stringPreferencesKey("reply_style")
        val INSTRUCTIONS = stringPreferencesKey("instructions")
        val FACTS = stringPreferencesKey("personal_facts")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val TOKEN_SAVER = booleanPreferencesKey("token_saver")
        val BRAIN_LEARN = booleanPreferencesKey("brain_learn")
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
        val ANSWER_TEMPERATURE = booleanPreferencesKey("answer_temperature")
        val VERIFY_ANSWERS = booleanPreferencesKey("verify_answers")
        val SEARCH_PROVIDER = stringPreferencesKey("search_provider")
        val SEARCH_CACHE_HOURS = stringPreferencesKey("search_cache_hours")
        val SEARCH_LIMITS = stringPreferencesKey("search_limits")
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

    val proxyUrl: Flow<String> = data.map { it[K.PROXY]?.trim()?.takeIf { v -> v.isNotEmpty() } ?: BuildConfig.DEFAULT_PROXY }.distinctUntilChanged()

    val autoSwitch: Flow<Boolean> = data.map { it[K.AUTO_SWITCH] ?: true }.distinctUntilChanged()
    val autoResume: Flow<Boolean> = data.map { it[K.AUTO_RESUME] ?: true }.distinctUntilChanged()
    val tokenSaver: Flow<Boolean> = data.map { it[K.TOKEN_SAVER] ?: true }.distinctUntilChanged()
    val brainLearn: Flow<Boolean> = data.map { it[K.BRAIN_LEARN] ?: true }.distinctUntilChanged()
    val brainLocalFirst: Flow<Boolean> = data.map { it[K.BRAIN_LOCAL_FIRST] ?: true }.distinctUntilChanged()
    val brainContext: Flow<Boolean> = data.map { it[K.BRAIN_CONTEXT] ?: true }.distinctUntilChanged()
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
    val webSearch: Flow<Boolean?> = data.map { it[K.WEB_SEARCH] }.distinctUntilChanged()
    val searchProvider: Flow<String> = data.map { it[K.SEARCH_PROVIDER] ?: "tavily" }.distinctUntilChanged()
    val searchCacheHours: Flow<Int> = data.map { it[K.SEARCH_CACHE_HOURS]?.toIntOrNull()?.coerceIn(1, 168) ?: 6 }.distinctUntilChanged()
    val searchLimits: Flow<Map<com.freellm.chat.domain.SearchKind, Int>> =
        data.map { com.freellm.chat.domain.SearchLimits.decode(it[K.SEARCH_LIMITS]) }.distinctUntilChanged()

    private fun decode(s: String?): Map<String, String> =
        (s ?: "").split(",").mapNotNull { e ->
            val i = e.indexOf('=')
            if (i > 0) e.substring(0, i) to e.substring(i + 1) else null
        }.toMap()

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
        ds.edit { it[K.PROXY] = v }
    }

    suspend fun setSelection(v: String) {
        ds.edit { it[K.SELECTION] = v }
    }

    suspend fun setAutoSwitch(v: Boolean) {
        ds.edit { it[K.AUTO_SWITCH] = v }
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

    suspend fun setVerifyAnswers(v: Boolean) {
        ds.edit { it[K.VERIFY_ANSWERS] = v }
    }
}
