package com.rhodes.privatechat.shared.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

object AndroidPlatformUtils {
    lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}

actual fun showToast(message: String) {
    Toast.makeText(AndroidPlatformUtils.appContext, message, Toast.LENGTH_SHORT).show()
}

actual fun copyToClipboard(text: String) {
    val clipboard = AndroidPlatformUtils.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("rhodes_terminal", text)
    clipboard.setPrimaryClip(clip)
}

actual fun getClipboardText(): String? {
    val clipboard = AndroidPlatformUtils.appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString()
}

actual fun shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    AndroidPlatformUtils.appContext.startActivity(intent)
}

actual fun minimizeApp() {
    // Android doesn't have a direct minimize API from non-Activity context
}
