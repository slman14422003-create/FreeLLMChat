package com.freellm.chat.domain

import java.time.Instant
import java.time.ZoneOffset

object ProviderErrors {
    private const val MIN_DAILY_WAIT = 60L

    fun isPolicy(lower: String): Boolean =
        lower.contains("data policy") || lower.contains("free model publication") || lower.contains("privacy settings")

    fun isDaily(lower: String): Boolean =
        lower.contains("free-models-per-day") || lower.contains("per-day") || lower.contains("per day") || lower.contains("daily")

    fun isQuota(lower: String): Boolean =
        lower.contains("insufficient_quota") || lower.contains("resource_exhausted") || lower.contains("exceeded your current quota") ||
            lower.contains("quota exceeded") || lower.contains("quota has been") || lower.contains("out of credits") ||
            lower.contains("insufficient credit") || lower.contains("insufficient balance") || lower.contains("free allocation") ||
            lower.contains("rate limit") || lower.contains("rate_limit") || lower.contains("too many requests") ||
            (lower.contains("free tier") && lower.contains("limit"))

    private val rateWord = Regex("\\brate[ _-]?limit(ed|s)?\\b|\\b429\\b|\\bquota\\b|too many requests", RegexOption.IGNORE_CASE)

    fun isForbiddenAuth(lower: String): Boolean =
        lower.contains("invalid key") || lower.contains("invalid api key") || lower.contains("invalid_api_key") ||
            lower.contains("api key not valid") || lower.contains("unauthorized") || lower.contains("permission")

    fun secondsToUtcMidnight(nowMs: Long): Long {
        val now = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC)
        return (next.toEpochSecond() - now.toEpochSecond()).coerceAtLeast(MIN_DAILY_WAIT)
    }

    fun kind(code: Int, body: String): ErrorKind {
        val l = body.lowercase()
        val modelMissing = l.contains("model") && (
            l.contains("not found") || l.contains("does not exist") || l.contains("not_found") || l.contains("decommissioned")
            )
        return when {
            code == 429 -> ErrorKind.RATE_LIMIT
            code == 402 -> ErrorKind.PAYMENT
            isPolicy(l) && (code == 400 || code == 404 || code == 403) -> ErrorKind.POLICY
            isQuota(l) && (code == 400 || code == 403 || code == 413) -> ErrorKind.RATE_LIMIT
            code == 401 -> ErrorKind.AUTH
            code == 403 -> if (isForbiddenAuth(l)) ErrorKind.AUTH else ErrorKind.SERVER
            l.contains("no endpoints found") -> ErrorKind.SERVER
            code == 404 || modelMissing -> ErrorKind.NOT_FOUND
            code == 408 || code >= 500 -> ErrorKind.SERVER
            else -> ErrorKind.BAD_REQUEST
        }
    }

    fun retryAfter(code: Int, body: String, header: Long?, nowMs: Long): Long? =
        if ((code == 429 || code == 403 || code == 400) && isDaily(body.lowercase())) secondsToUtcMidnight(nowMs) else header

    fun streamKind(message: String): ErrorKind {
        val l = message.lowercase()
        return when {
            isPolicy(l) -> ErrorKind.POLICY
            rateWord.containsMatchIn(l) || isQuota(l) -> ErrorKind.RATE_LIMIT
            else -> ErrorKind.SERVER
        }
    }

    fun streamRetryAfter(message: String, nowMs: Long): Long? =
        if (isDaily(message.lowercase())) secondsToUtcMidnight(nowMs) else null
}
