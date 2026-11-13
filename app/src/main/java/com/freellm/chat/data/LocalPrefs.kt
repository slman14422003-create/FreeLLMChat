package com.freellm.chat.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Saved prompt templates and the unsent draft, kept in private preferences. */
class LocalPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(String.serializer())

    fun templates(): List<String> = runCatching {
        json.decodeFromString(ser, prefs.getString("templates", null) ?: "[]")
    }.getOrDefault(emptyList())

    fun saveTemplates(list: List<String>) {
        prefs.edit().putString("templates", json.encodeToString(ser, list)).apply()
    }

    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(v) { prefs.edit().putBoolean("app_lock", v).apply() }

    var autoBackupOn: Boolean
        get() = prefs.getBoolean("ab_on", false)
        set(v) { prefs.edit().putBoolean("ab_on", v).apply() }

    var autoBackupFolder: String?
        get() = prefs.getString("ab_folder", null)
        set(v) { prefs.edit().putString("ab_folder", v).apply() }

    var autoBackupDays: Int
        get() = prefs.getInt("ab_days", 7)
        set(v) { prefs.edit().putInt("ab_days", v).apply() }

    var autoBackupLast: Long
        get() = prefs.getLong("ab_last", 0L)
        set(v) { prefs.edit().putLong("ab_last", v).apply() }

    fun draft(): String = prefs.getString("draft", "").orEmpty()

    fun saveDraft(text: String) {
        prefs.edit().putString("draft", text).apply()
    }
}
