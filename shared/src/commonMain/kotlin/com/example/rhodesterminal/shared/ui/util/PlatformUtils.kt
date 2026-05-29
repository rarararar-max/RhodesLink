package com.example.rhodesterminal.shared.ui.util

expect fun showToast(message: String)

expect fun copyToClipboard(text: String)

expect fun getClipboardText(): String?

expect fun shareText(text: String)

expect fun minimizeApp()
