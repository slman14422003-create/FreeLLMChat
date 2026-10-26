package com.freellm.chat.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PromptVars {
    fun expand(text: String, now: LocalDateTime, device: String, language: String): String =
        text
            .replace("{date}", now.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .replace("{time}", now.format(DateTimeFormatter.ofPattern("HH:mm")))
            .replace("{device}", device)
            .replace("{language}", language)
}
