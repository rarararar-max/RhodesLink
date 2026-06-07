package com.rhodes.privatechat.ui.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatChatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
