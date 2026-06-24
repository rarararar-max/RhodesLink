package com.rhodes.privatechat.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// -- 深色模式全局状态（响应式，切换时自动触发重组） --
var isDarkMode by mutableStateOf(true)

// -- 颜色状态（响应式，切换主题时自动触发重组） --
var BG by mutableStateOf(Color(0xFFF2F2F7))   // 底背景
var Surface by mutableStateOf(Color(0xFFFFFFFF)) // 表面
var Card by mutableStateOf(Color(0xFFFFFFFF))    // 卡片
var Divider by mutableStateOf(Color(0xFFD1D1D6)) // 分割线
var InputBg by mutableStateOf(Color(0xFFE8E8ED)) // 输入框背景

var TextPrimary by mutableStateOf(Color(0xFF1C1C1E))
var TextSecondary by mutableStateOf(Color(0xFF6E6E72))
var TextTertiary by mutableStateOf(Color(0xFFA0A0A4))

var Primary by mutableStateOf(Color(0xFF4A7FDC))
var PrimaryContainer by mutableStateOf(Color(0xFFD6E4FF))
var OnPrimary by mutableStateOf(Color(0xFFFFFFFF))

var SurfaceVariant by mutableStateOf(Color(0xFFF2F2F7))

var ErrorRed by mutableStateOf(Color(0xFFFF3B30))
var AccentGreen by mutableStateOf(Color(0xFF34C759))
var AccentOrange by mutableStateOf(Color(0xFFFF9500))
var AccentBlue by mutableStateOf(Color(0xFF007AFF))
var AccentPurple by mutableStateOf(Color(0xFFAF52DE))

var Gray100 by mutableStateOf(Color(0xFFF2F2F7))
var Gray500 by mutableStateOf(Color(0xFFA0A0A4))

var Blue400 by mutableStateOf(Color(0xFF4A7FDC))

var NavBarBg by mutableStateOf(Color(0xFF252529))   // 底部导航栏背景

var ElevatedSurface by mutableStateOf(Color(0xFFFFFFFF))
var Stroke by mutableStateOf(Color(0x1A000000))
var StrokeStrong by mutableStateOf(Color(0x33000000))
var Glow by mutableStateOf(Color(0x334A7FDC))
var Scrim by mutableStateOf(Color(0x66000000))
var HeaderStart by mutableStateOf(Color(0xFFFFFFFF))
var HeaderEnd by mutableStateOf(Color(0xFFF4F7FF))
var BubbleMine by mutableStateOf(Color(0xFFDCEAFF))
var BubbleMineEnd by mutableStateOf(Color(0xFFBFD7FF))
var BubbleOther by mutableStateOf(Color(0xFFFFFFFF))
var WarningContainer by mutableStateOf(Color(0xFFFFF1D6))
var SuccessContainer by mutableStateOf(Color(0xFFE3F8EA))

// -- 深色主题 --
fun applyDarkTheme() {
    BG          = Color(0xFF1A1A1E)
    Surface     = Color(0xFF252528)
    Card        = Color(0xFF2E2E32)
    Divider     = Color(0xFF38383E)
    InputBg     = Color(0xFF323238)
    TextPrimary = Color(0xFFE8E8EC)
    TextSecondary = Color(0xFF9E9EA4)
    TextTertiary  = Color(0xFF6A6A70)
    Primary        = Color(0xFF6B8CFF)
    PrimaryContainer = Color(0xFF1C2E4A)
    OnPrimary      = Color(0xFFFFFFFF)
    SurfaceVariant = Card
    ErrorRed       = Color(0xFFFF5252)
    AccentGreen    = Color(0xFF4CAF50)
    AccentOrange   = Color(0xFFFFA726)
    AccentBlue     = Color(0xFF64B5F6)
    AccentPurple   = Color(0xFF9575CD)
    Gray100 = Color(0xFF353538)
    Gray500 = Color(0xFF6A6A70)
    Blue400 = Color(0xFF6B8CFF)
    NavBarBg = Color(0xFF1A1A1E)
    ElevatedSurface = Color(0xFF2B2B31)
    Stroke = Color(0x24FFFFFF)
    StrokeStrong = Color(0x3DFFFFFF)
    Glow = Color(0x336B8CFF)
    Scrim = Color(0x99000000)
    HeaderStart = Color(0xFF272832)
    HeaderEnd = Color(0xFF1A1B22)
    BubbleMine = Color(0xFF314A7A)
    BubbleMineEnd = Color(0xFF496CBA)
    BubbleOther = Color(0xFF2E2E34)
    WarningContainer = Color(0xFF4A3219)
    SuccessContainer = Color(0xFF1F3A2A)
}

// -- 浅色主题（默认） --
fun applyLightTheme() {
    BG          = Color(0xFFF2F2F7)
    Surface     = Color(0xFFFFFFFF)
    Card        = Color(0xFFFFFFFF)
    Divider     = Color(0xFFD1D1D6)
    InputBg     = Color(0xFFE8E8ED)
    TextPrimary = Color(0xFF1C1C1E)
    TextSecondary = Color(0xFF6E6E72)
    TextTertiary  = Color(0xFFA0A0A4)
    Primary        = Color(0xFF4A7FDC)
    PrimaryContainer = Color(0xFFD6E4FF)
    OnPrimary      = Color(0xFFFFFFFF)
    SurfaceVariant = Color(0xFFF2F2F7)
    ErrorRed       = Color(0xFFFF3B30)
    AccentGreen    = Color(0xFF34C759)
    AccentOrange   = Color(0xFFFF9500)
    AccentBlue     = Color(0xFF007AFF)
    AccentPurple   = Color(0xFFAF52DE)
    Gray100 = Color(0xFFF2F2F7)
    Gray500 = Color(0xFFA0A0A4)
    Blue400 = Color(0xFF4A7FDC)
    NavBarBg = Color(0xFF252529)
    ElevatedSurface = Color(0xFFFFFFFF)
    Stroke = Color(0x1A17345C)
    StrokeStrong = Color(0x3317345C)
    Glow = Color(0x264A7FDC)
    Scrim = Color(0x66000000)
    HeaderStart = Color(0xFFFFFFFF)
    HeaderEnd = Color(0xFFEFF5FF)
    BubbleMine = Color(0xFFDDEBFF)
    BubbleMineEnd = Color(0xFFBFD8FF)
    BubbleOther = Color(0xFFFFFFFF)
    WarningContainer = Color(0xFFFFF1D6)
    SuccessContainer = Color(0xFFE3F8EA)
}
