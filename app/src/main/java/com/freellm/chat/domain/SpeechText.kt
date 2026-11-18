package com.freellm.chat.domain

object SpeechText {
    const val MAX = 3900

    private val block = Regex("(?s)(`{3,}|~{3,}).*?(\\1|$)")
    private val link = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val url = Regex("https?://\\S+")
    private val marks = Regex("[*#`_>|~\\[\\]]")
    private val spaces = Regex("\\s+")

    fun plain(text: String): String =
        text.replace(block, " ")
            .replace(link, "$1")
            .replace(url, " ")
            .replace(marks, " ")
            .replace(spaces, " ")
            .trim()
            .take(MAX)

    fun isArabic(text: String): Boolean = text.any { it in '؀'..'ۿ' }
}
