package com.freellm.chat.data

object UiLang {
    fun resolve(stored: String?, deviceLanguage: String): String = when {
        stored == "system" -> "system"
        stored == "ar" || stored == "en" -> stored
        deviceLanguage.lowercase() == "ar" -> "ar"
        else -> "en"
    }

    fun device(): String = android.content.res.Resources.getSystem().configuration.locales.get(0).language
}
