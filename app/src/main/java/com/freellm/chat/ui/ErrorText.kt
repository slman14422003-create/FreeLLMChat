package com.freellm.chat.ui

import android.content.Context
import com.freellm.chat.R
import com.freellm.chat.domain.FailKind
import com.freellm.chat.domain.FailReason
import com.freellm.chat.domain.LlmException
import com.freellm.chat.domain.ReasonKind
import com.freellm.chat.domain.RouteEvent
import com.freellm.chat.domain.labelRes
import com.freellm.chat.domain.localized

fun Context.errorText(e: LlmException): String = e.localized { res, detail -> getString(res, detail) }

fun Context.durationText(sec: Long): String =
    if (sec < 60) getString(R.string.duration_seconds, sec) else getString(R.string.duration_minutes, (sec + 59) / 60)

private fun Context.reasonText(r: FailReason): String = when (r.reason) {
    ReasonKind.UNAVAILABLE -> getString(R.string.fail_reason_unavailable)
    ReasonKind.COOLING -> getString(R.string.fail_reason_cooling)
    ReasonKind.USAGE_LIMIT -> getString(R.string.fail_reason_limit)
    ReasonKind.ERROR -> r.error?.let { getString(it.labelRes()) } ?: getString(R.string.fail_reason_unavailable)
}

fun Context.failureText(ev: RouteEvent.Failed): String = when (ev.kind) {
    FailKind.NO_PROVIDER -> getString(R.string.fail_no_provider)
    FailKind.PINNED_MISSING -> getString(R.string.fail_pinned_missing)
    FailKind.NO_VISION -> getString(R.string.fail_no_vision)
    FailKind.ALL_FAILED -> buildString {
        append(getString(R.string.fail_all))
        ev.reasons.forEach { append("\n• ").append(it.provider).append(": ").append(reasonText(it)) }
        ev.retryInSec?.let { append("\n").append(getString(R.string.fail_retry_in, durationText(it))) }
    }
}
