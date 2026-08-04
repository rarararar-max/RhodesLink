package com.rhodes.privatechat.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppearanceSkin(
    val storageValue: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val isDark: Boolean,
) {
    ClassicLight(
        storageValue = "classic_light",
        title = "经典白昼",
        subtitle = "CLASSIC / DAY",
        description = "保留当前明亮、简洁的白天界面。",
        isDark = false,
    ),
    ClassicDark(
        storageValue = "classic_dark",
        title = "经典黑夜",
        subtitle = "CLASSIC / NIGHT",
        description = "保留当前柔和、低刺激的黑夜界面。",
        isDark = true,
    ),
    RhodesDay(
        storageValue = "rhodes_day",
        title = "罗德岛终端・昼",
        subtitle = "RHODES TERMINAL / DAY",
        description = "冷灰档案底色，橙色行动标记与工业结构线。",
        isDark = false,
    ),
    RhodesNight(
        storageValue = "rhodes_night",
        title = "罗德岛终端・夜",
        subtitle = "RHODES TERMINAL / NIGHT",
        description = "深蓝灰终端面板，适合沉浸式夜间使用。",
        isDark = true,
    );

    companion object {
        fun fromStorage(value: String, fallbackDark: Boolean): AppearanceSkin =
            entries.firstOrNull { it.storageValue == value }
                ?: if (fallbackDark) ClassicDark else ClassicLight
    }
}

var currentAppearanceSkin by mutableStateOf(AppearanceSkin.ClassicDark)

val isRhodesTerminal: Boolean
    get() = currentAppearanceSkin == AppearanceSkin.RhodesDay || currentAppearanceSkin == AppearanceSkin.RhodesNight
