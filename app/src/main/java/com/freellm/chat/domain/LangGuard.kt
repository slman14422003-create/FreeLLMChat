package com.freellm.chat.domain

object LangGuard {
    private val english = setOf("the", "is", "are", "and", "you", "what", "how", "why", "can", "please", "this", "that", "with", "for", "have", "do", "i", "to", "of", "in", "it", "me")
    private val translate = Regex("\\b(translate|translation|in arabic|in english|in french)\\b|\\u062a\\u0631\\u062c\\u0645|\\u0628\\u0627\\u0644\\u0625\\u0646\\u062c\\u0644\\u064a\\u0632\\u064a\\u0629|\\u0628\\u0627\\u0644\\u0639\\u0631\\u0628\\u064a\\u0629", RegexOption.IGNORE_CASE)
    private val greeting = Regex(
        "^\\s*(hi|hello|hey|thanks|thank you|good (morning|evening|night)|bye|ok|okay|\\u0645\\u0631\\u062d\\u0628\\u0627|\\u0627\\u0647\\u0644\\u0627|\\u0623\\u0647\\u0644\\u0627|\\u0647\\u0644\\u0627|\\u0634\\u0643\\u0631\\u0627|\\u0627\\u0644\\u0633\\u0644\\u0627\\u0645 \\u0639\\u0644\\u064a\\u0643\\u0645|\\u0635\\u0628\\u0627\\u062d \\u0627\\u0644\\u062e\\u064a\\u0631|\\u0645\\u0633\\u0627\\u0621 \\u0627\\u0644\\u062e\\u064a\\u0631)[\\s!.\\u061f?]*\\u0020*[\\p{L}\\s]{0,12}$",
        RegexOption.IGNORE_CASE
    )

    fun detect(text: String): String? {
        val letters = text.count { it.isLetter() }
        if (letters < 3) return null
        if (QuestionClassifier.arabicRatio(text) > 0.5) return "ar"
        val latin = text.count { it.isLetter() && it.code < 128 }
        if (latin < letters * 0.9) return null
        val words = text.lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        return if (words.count { it in english } >= 2) "en" else null
    }

    fun name(code: String?): String? = when (code) {
        "ar" -> "Arabic"
        "en" -> "English"
        else -> null
    }

    fun instruction(code: String?): String = name(code)?.let { "Reply in $it." }.orEmpty()

    fun translationIntent(text: String): Boolean = translate.containsMatchIn(text)

    fun isGreeting(text: String): Boolean = text.trim().length <= 30 && greeting.matches(text)

    fun mismatch(expected: String?, reply: String): Boolean {
        if (expected == null) return false
        val letters = reply.count { it.isLetter() }
        if (letters < 6) return false
        val ar = QuestionClassifier.arabicRatio(reply)
        return when (expected) {
            "ar" -> ar < 0.2
            "en" -> ar > 0.5
            else -> false
        }
    }
}
