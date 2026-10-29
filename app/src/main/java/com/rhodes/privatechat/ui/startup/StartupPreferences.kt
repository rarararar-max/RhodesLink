package com.rhodes.privatechat.ui.startup

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

enum class StartupMode(val key: String, val title: String, val description: String) {
    NEVER("never", "不显示开屏", "启动后直接进入 APP"),
    ALWAYS("always", "每次启动时显示", "每次正常启动时展示；短暂切后台返回不重复"),
    DAILY("daily", "每日首次显示", "北京时间每天首次启动时展示")
}

internal fun resolveStartupMode(saved: String?, legacyEnabled: Boolean): StartupMode =
    StartupMode.entries.firstOrNull { it.key == saved }
        ?: if (legacyEnabled) StartupMode.DAILY else StartupMode.NEVER

internal fun shouldPlayStartup(mode: StartupMode, restored: Boolean, directEntry: Boolean, lastDate: String, today: String) =
    !restored && !directEntry && when (mode) {
        StartupMode.NEVER -> false
        StartupMode.ALWAYS -> true
        StartupMode.DAILY -> lastDate != today
    }

class StartupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("rhodes_startup", Context.MODE_PRIVATE)
    var mode: StartupMode
        get() = resolveStartupMode(prefs.getString("mode", null), prefs.getBoolean("enabled", true))
        set(value) { prefs.edit().putString("mode", value.key).apply() }
    var sound: Boolean
        get() = prefs.getBoolean("sound", false)
        set(value) { prefs.edit().putBoolean("sound", value).apply() }

    fun claim(restored: Boolean, directEntry: Boolean): Boolean {
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
        val selected = mode
        if (!shouldPlayStartup(selected, restored, directEntry, prefs.getString("last_date", "").orEmpty(), today)) return false
        // Only DAILY consumes the date; previews and ALWAYS must not affect the daily record.
        if (selected == StartupMode.DAILY) prefs.edit().putString("last_date", today).apply()
        return true
    }
}
