package com.freellm.chat.data

import android.content.Context
import com.freellm.chat.domain.UsageStore

class UsagePrefs(context: Context) : UsageStore {
    private val prefs = context.applicationContext.getSharedPreferences("usage_v1", Context.MODE_PRIVATE)

    override fun read(key: String): String? = prefs.getString(key, null)

    override fun write(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
