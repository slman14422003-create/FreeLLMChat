package com.freellm.chat.domain

object Concepts {
    private val groups: List<List<String>> = listOf(
        listOf("\u0633\u0639\u0631", "\u062B\u0645\u0646", "\u062A\u0643\u0644\u0641\u0629", "\u0643\u0644\u0641\u0629", "price", "cost"),
        listOf("\u0634\u0631\u0627\u0621", "\u0627\u0634\u062A\u0631\u064A", "buy", "purchase"),
        listOf("\u0627\u0635\u0644\u0627\u062D", "\u062A\u0635\u0644\u064A\u062D", "fix", "repair"),
        listOf("\u062E\u0637\u0623", "\u0639\u0637\u0644", "\u0645\u0634\u0643\u0644\u0629", "\u062E\u0644\u0644", "error", "bug", "issue", "problem", "fault"),
        listOf("\u062D\u0630\u0641", "\u0645\u0633\u062D", "\u0627\u0632\u0627\u0644\u0629", "delete", "remove", "erase"),
        listOf("\u062A\u062B\u0628\u064A\u062A", "\u062A\u0646\u0635\u064A\u0628", "install", "setup"),
        listOf("\u062A\u062D\u0645\u064A\u0644", "\u062A\u0646\u0632\u064A\u0644", "download"),
        listOf("\u0645\u062C\u0627\u0646\u064A", "\u0645\u062C\u0627\u0646\u0627", "free"),
        listOf("\u0633\u0631\u064A\u0639", "\u0628\u0633\u0631\u0639\u0629", "fast", "quick", "quickly"),
        listOf("\u0643\u0628\u064A\u0631", "\u0636\u062E\u0645", "big", "large", "huge"),
        listOf("\u0635\u063A\u064A\u0631", "small", "tiny"),
        listOf("\u062A\u0639\u0644\u0645", "\u062A\u0639\u0644\u064A\u0645", "learn", "study"),
        listOf("\u0645\u0639\u0646\u0649", "\u062A\u0639\u0631\u064A\u0641", "\u0645\u0641\u0647\u0648\u0645", "meaning", "definition", "define"),
        listOf("\u0641\u0631\u0642", "\u0627\u062E\u062A\u0644\u0627\u0641", "difference"),
        listOf("\u0627\u0641\u0636\u0644", "\u0627\u062D\u0633\u0646", "best", "top"),
        listOf("\u0647\u0627\u062A\u0641", "\u062C\u0648\u0627\u0644", "\u0645\u0648\u0628\u0627\u064A\u0644", "phone", "mobile", "smartphone"),
        listOf("\u062D\u0627\u0633\u0648\u0628", "\u0643\u0645\u0628\u064A\u0648\u062A\u0631", "computer", "laptop"),
        listOf("\u062A\u0637\u0628\u064A\u0642", "app", "application"),
        listOf("\u0627\u0646\u062A\u0631\u0646\u062A", "internet", "network", "\u0634\u0628\u0643\u0629"),
        listOf("\u0645\u0627\u0644", "\u0641\u0644\u0648\u0633", "\u0646\u0642\u0648\u062F", "money", "cash"),
        listOf("\u0637\u0628\u064A\u0628", "\u062F\u0643\u062A\u0648\u0631", "doctor", "physician"),
        listOf("\u0645\u0631\u0636", "\u062F\u0627\u0621", "disease", "illness", "sickness"),
        listOf("\u062F\u0648\u0627\u0621", "\u0639\u0644\u0627\u062C", "medicine", "drug", "treatment"),
        listOf("\u0637\u0628\u062E", "\u0637\u0647\u064A", "cook", "cooking"),
        listOf("\u0648\u0635\u0641\u0629", "recipe"),
        listOf("\u062A\u0631\u062C\u0645\u0629", "translate", "translation"),
        listOf("\u0628\u062F\u0627\u064A\u0629", "\u0628\u062F\u0621", "start", "begin"),
        listOf("\u0646\u0647\u0627\u064A\u0629", "\u0627\u0646\u062A\u0647\u0627\u0621", "end", "finish"),
        listOf("\u0633\u064A\u0627\u0631\u0629", "\u0639\u0631\u0628\u064A\u0629", "car", "vehicle"),
        listOf("\u0645\u0646\u0632\u0644", "\u0628\u064A\u062A", "house", "home"),
        listOf("\u0639\u0645\u0644", "\u0648\u0638\u064A\u0641\u0629", "job", "career"),
        listOf("\u0645\u062F\u0631\u0633\u0629", "school"),
        listOf("\u062C\u0627\u0645\u0639\u0629", "university", "college"),
        listOf("\u0627\u0645\u0646", "\u062D\u0645\u0627\u064A\u0629", "security", "protection"),
        listOf("\u0643\u0644\u0645\u0629 \u0627\u0644\u0633\u0631", "password"),
        listOf("\u0628\u0637\u0627\u0631\u064A\u062A\u064A", "\u0628\u0637\u0627\u0631\u064A\u0629", "battery"),
        listOf("\u0630\u0627\u0643\u0631\u0629", "memory", "ram"),
        listOf("\u0633\u0631\u0639\u0629", "speed")
    )

    private val canon: Map<String, String> by lazy {
        val m = HashMap<String, String>()
        for (g in groups) {
            val forms = g.map { LocalBrain.stem(LocalBrain.normalize(it)) }.filter { it.isNotEmpty() && ' ' !in it }
            val head = forms.firstOrNull() ?: continue
            forms.forEach { m.putIfAbsent(it, head) }
        }
        m
    }

    fun canonical(token: String): String = canon[token] ?: token
}
