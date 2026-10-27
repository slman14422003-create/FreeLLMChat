package com.freellm.chat.domain

import com.freellm.chat.R

enum class ErrorKind {
    RATE_LIMIT, PAYMENT, SERVER, AUTH, NOT_FOUND, TIMEOUT, NETWORK, EMPTY, INTERRUPTED, BAD_REQUEST
}

class LlmException(
    val kind: ErrorKind,
    val httpCode: Int = 0,
    val retryAfterSec: Long? = null,
    message: String = ""
) : Exception(message)

fun ErrorKind.labelRes(): Int = when (this) {
    ErrorKind.RATE_LIMIT -> R.string.err_label_rate_limit
    ErrorKind.PAYMENT -> R.string.err_label_payment
    ErrorKind.SERVER -> R.string.err_label_server
    ErrorKind.AUTH -> R.string.err_label_auth
    ErrorKind.NOT_FOUND -> R.string.err_label_not_found
    ErrorKind.TIMEOUT -> R.string.err_label_timeout
    ErrorKind.NETWORK -> R.string.err_label_network
    ErrorKind.EMPTY -> R.string.err_label_empty
    ErrorKind.INTERRUPTED -> R.string.err_label_interrupted
    ErrorKind.BAD_REQUEST -> R.string.err_label_bad_request
}

fun LlmException.messageRes(): Int = when (kind) {
    ErrorKind.AUTH -> if (httpCode == 401) R.string.err_msg_auth_401 else R.string.err_msg_auth_403
    ErrorKind.RATE_LIMIT -> R.string.err_msg_rate_limit
    ErrorKind.PAYMENT -> R.string.err_msg_payment
    ErrorKind.NOT_FOUND -> R.string.err_msg_not_found
    ErrorKind.TIMEOUT -> R.string.err_msg_timeout
    ErrorKind.NETWORK -> R.string.err_msg_network
    ErrorKind.SERVER -> R.string.err_msg_server
    ErrorKind.BAD_REQUEST -> R.string.err_msg_bad_request
    ErrorKind.EMPTY -> R.string.err_msg_empty
    ErrorKind.INTERRUPTED -> R.string.err_msg_interrupted
}

fun LlmException.localized(get: (Int, String) -> String): String = get(messageRes(), message.orEmpty()).trim()

fun labelOf(name: String, get: (Int) -> String): String =
    ErrorKind.entries.firstOrNull { it.name == name }?.let { get(it.labelRes()) } ?: name
