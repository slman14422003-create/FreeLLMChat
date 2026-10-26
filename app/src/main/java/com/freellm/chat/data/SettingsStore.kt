package com.freellm.chat.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
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
        val AUTO_SWITCH = booleanPreferencesKey("auto_switch")
        val STYLE = stringPreferencesKey("reply_style")
        val INSTRUCTIONS = stringPreferencesKey("instructions")
    }

    private val data: Flow<Preferences> = ds.data.catch { emit(emptyPreferences()) }

    val theme: Flow<String> = data.map { it[K.THEME] ?: "system" }.distinctUntilChanged()
    val welcomeDone: Flow<Boolean> = data.map { it[K.WELCOME] ?: false }.distinctUntilChanged()
    val enabled: Flow<Set<String>> = data.map { it[K.ENABLED] ?: emptySet() }.distinctUntilChanged()
    val order: Flow<List<String>> = data
        .map { (it[K.ORDER] ?: "").split(",").filter { s -> s.isNotBlank() } }
        .distinctUntilChanged()
    val statuses: Flow<Map<String, String>> = data.map { decode(it[K.STATUSES]) }.distinctUntilChanged()
    val selection: Flow<String> = data.map { it[K.SELECTION] ?: "AUTO" }.distinctUntilChanged()


    val autoSwitch: Flow<Boolean> = data.map { it[K.AUTO_SWITCH] ?: true }.distinctUntilChanged()
    val style: Flow<String> = data.map { it[K.STYLE] ?: "default" }.distinctUntilChanged()
    val instructions: Flow<String> = data.map { it[K.INSTRUCTIONS] ?: "" }.distinctUntilChanged()

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

    suspend fun setSelection(v: String) {
        ds.edit { it[K.SELECTION] = v }
    }

    suspend fun setAutoSwitch(v: Boolean) {
        ds.edit { it[K.AUTO_SWITCH] = v }
    }

    suspend fun setStyle(v: String) {
        ds.edit { it[K.STYLE] = v }
    }

    suspend fun setInstructions(v: String) {
        ds.edit { it[K.INSTRUCTIONS] = v }
    }
}
