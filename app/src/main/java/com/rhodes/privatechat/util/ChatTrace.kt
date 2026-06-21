package com.rhodes.privatechat.util

import android.util.Log

object ChatTrace {
    const val TAG = "RHODES_CHAT_TRACE"

    fun d(area: String, message: String) {
        Log.d(TAG, "[$area] $message")
    }

    fun e(area: String, message: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$area] $message", throwable)
    }

    fun ids(values: List<Long>, limit: Int = 12): String =
        values.take(limit).joinToString(prefix = "[", postfix = if (values.size > limit) ", ...]" else "]")

    fun short(value: String, max: Int = 80): String =
        value.replace('\n', ' ').let { if (it.length > max) it.take(max) + "..." else it }
}
