package com.freellm.chat.domain

object Tuning {
    const val LOCAL_CONFIDENT = 0.95
    const val LOCAL_FALLBACK = 0.40
    const val FOLLOW_UP_CHARS = 80
    const val KEEP_RECENT = 14
    const val SEARCH_BUDGET_MS = 7_000L
    const val LATE_SEARCH_MS = 20_000L
    const val QUICK_MAX_TOKENS = 1800
}
