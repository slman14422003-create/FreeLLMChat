package com.freellm.chat.domain

enum class ErrorKind {
    RATE_LIMIT, PAYMENT, SERVER, AUTH, NOT_FOUND, TIMEOUT, NETWORK, EMPTY, INTERRUPTED, BAD_REQUEST
}

class LlmException(
    val kind: ErrorKind,
    val httpCode: Int = 0,
    val retryAfterSec: Long? = null,
    message: String = ""
) : Exception(message)

fun ErrorKind.label(): String = when (this) {
    ErrorKind.RATE_LIMIT -> "تجاوز الحد المسموح (429)"
    ErrorKind.PAYMENT -> "انتهى الرصيد أو السقف (402)"
    ErrorKind.SERVER -> "خطأ في خادم المزود"
    ErrorKind.AUTH -> "المفتاح مرفوض أو المنطقة محجوبة (401/403)"
    ErrorKind.NOT_FOUND -> "المودل غير متاح (404)"
    ErrorKind.TIMEOUT -> "انتهت المهلة"
    ErrorKind.NETWORK -> "مشكلة في الشبكة"
    ErrorKind.EMPTY -> "رد فارغ"
    ErrorKind.INTERRUPTED -> "انقطع الستريم"
    ErrorKind.BAD_REQUEST -> "طلب مرفوض (400)"
}

fun LlmException.arabic(): String = when (kind) {
    ErrorKind.AUTH ->
        if (httpCode == 401) "المفتاح غير صحيح (401). تأكد من نسخه كاملاً."
        else "الوصول مرفوض (403): قد تكون منطقتك محجوبة أو المفتاح بلا صلاحية. جرّب حقل Base URL مع وسيط."
    ErrorKind.RATE_LIMIT -> "تم تجاوز الحد المجاني حالياً (429). جرّب بعد قليل."
    ErrorKind.PAYMENT -> "المزود يطلب رصيداً أو انتهى السقف المجاني (402)."
    ErrorKind.NOT_FOUND -> "الرابط أو المودل غير موجود (404). تحقق من Base URL."
    ErrorKind.TIMEOUT -> "انتهت مهلة الاتصال. تحقق من الإنترنت أو جرّب وسيطاً."
    ErrorKind.NETWORK -> "تعذّر الاتصال بالشبكة. تحقق من الإنترنت أو من حجب المزود في بلدك. (${message.orEmpty()})"
    ErrorKind.SERVER -> "خطأ من خادم المزود. جرّب لاحقاً. ${message.orEmpty()}".trim()
    ErrorKind.BAD_REQUEST -> "رفض المزود الطلب (400). ${message.orEmpty()}".trim()
    ErrorKind.EMPTY -> "رد المزود فارغ."
    ErrorKind.INTERRUPTED -> "انقطع الاتصال أثناء الرد."
}
