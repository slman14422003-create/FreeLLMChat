package com.freellm.chat.domain

object CutMark {
    const val PREFIX = "\u2702"

    fun has(notes: String?): Boolean = notes?.lineSequence()?.any { it.startsWith(PREFIX) } == true

    fun strip(notes: String?): String? =
        notes?.lineSequence()?.filter { !it.startsWith(PREFIX) }?.joinToString("\n")?.ifBlank { null }

    fun add(notes: List<String>, label: String, cut: Boolean): List<String> =
        if (cut) notes + (PREFIX + " " + label) else notes
}
