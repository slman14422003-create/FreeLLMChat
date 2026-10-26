package com.freellm.chat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SecretStore(context: Context) {
    private val prefs: SharedPreferences = create(context.applicationContext)
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes

    private fun create(ctx: Context): SharedPreferences = try {
        open(ctx)
    } catch (e: Exception) {
        ctx.deleteSharedPreferences(FILE)
        open(ctx)
    }

    private fun open(ctx: Context): SharedPreferences {
        val master = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            ctx,
            FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun put(k: String, v: String?) {
        val e = prefs.edit()
        if (v.isNullOrBlank()) e.remove(k) else e.putString(k, v)
        e.apply()
        _changes.value = _changes.value + 1
    }

    fun apiKey(id: String): String? = prefs.getString("key_$id", null)
    fun accountId(id: String): String? = prefs.getString("acct_$id", null)
    fun baseUrl(id: String): String? = prefs.getString("base_$id", null)
    fun modelId(id: String): String? = prefs.getString("model_$id", null)

    fun extra(k: String): String? = prefs.getString("x_$k", null)
    fun setExtra(k: String, v: String?) = put("x_$k", v)

    fun setApiKey(id: String, v: String?) = put("key_$id", v)
    fun setAccountId(id: String, v: String?) = put("acct_$id", v)
    fun setBaseUrl(id: String, v: String?) = put("base_$id", v)
    fun setModelId(id: String, v: String?) = put("model_$id", v)

    fun clear(id: String) {
        prefs.edit().remove("key_$id").remove("acct_$id").remove("base_$id").remove("model_$id").apply()
        _changes.value = _changes.value + 1
    }

    fun exportAll(): Map<String, String> =
        prefs.all.filterKeys { !it.startsWith("x_") }.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()

    fun importAll(data: Map<String, String>, replace: Boolean) {
        val e = prefs.edit()
        if (replace) e.clear()
        data.forEach { (k, v) -> if (v.isNotBlank()) e.putString(k, v) }
        e.commit()
        _changes.value = _changes.value + 1
    }

    companion object {
        private const val FILE = "secrets_v1"
    }
}
