package com.rhodes.privatechat.util

object ChatTrace {
    const val TAG = "RHODES_CHAT_TRACE"
    var enabled: Boolean = false

    fun d(area: String, message: String) {
        if (!enabled) return
    }

    fun e(area: String, message: String, throwable: Throwable? = null) {
        if (!enabled) return
    }

    fun ids(values: List<Long>, limit: Int = 12): String =
        values.take(limit).joinToString(prefix = "[", postfix = if (values.size > limit) ", ...]" else "]")

    fun short(value: String, max: Int = 80): String =
        value.replace('\n', ' ').let { if (it.length > max) it.take(max) + "..." else it }
}
