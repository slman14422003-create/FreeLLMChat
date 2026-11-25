package com.freellm.chat.domain

object SaverPolicy {
    const val OFF = "off"
    const val AUTO = "auto"
    const val ON = "on"

    fun normalize(v: String?): String = if (v == ON || v == AUTO) v else OFF

    fun active(mode: String?, powerSave: Boolean, metered: Boolean): Boolean = when (normalize(mode)) {
        ON -> true
        AUTO -> powerSave || metered
        else -> false
    }

    fun reduceMotion(saverActive: Boolean, systemAnimationsOff: Boolean): Boolean = saverActive || systemAnimationsOff
}
