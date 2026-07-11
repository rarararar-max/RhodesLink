package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun StorySettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    var autoEnabled by remember { mutableStateOf(settings.autoAiEnabled) }
    SaveableSettingsScaffold(
        title = "动态、日记与派遣",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Primary) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            StoryInfoCard("这里影响什么？", "每日动态参数控制角色每天固定发动态；日记只在你手动点击生成时才写；派遣参数只影响派遣故事文本长度，不影响收益、成功率或耗时。")
            SettingsSectionTitle("每日动态")
            SettingsSwitchCard("后台自动 AI", "控制每日自动动态。关闭后，每日固定动态也不会自动生成；你手动聊天和手动生成不受影响。", autoEnabled) { autoEnabled = it; settings.autoAiEnabled = it }
            SettingsSwitchCard("每日自动动态", "开启后，每天按每人每日自动动态上限为有动态权限的角色补动态。", settings.dailyAutoMomentEnabled, enabled = autoEnabled) { settings.dailyAutoMomentEnabled = it }
            SettingsParamSlider(settings, "daily_auto_ai_limit", "每日后台 AI 预算", 40, 0f..500f, "每日自动动态最多消耗多少次 AI 调用。设 0 等同于关闭后台自动 AI。建议30-50。", step = 5f, enabled = autoEnabled)
            SettingsParamSlider(settings, "daily_moment_target", "每人每日自动动态上限", 2, 0f..10f, "每天每个有动态权限的角色最多自动发几条动态。0=不自动发动态；用户主动催发动态不受此限制。建议1-3。", step = 1f, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_min_chars", "动态最少字数", 50, 20f..300f, "每条动态最少写几个字。建议20-50。", step = 5f, pairKey = "moment_max_chars", isMinSide = true, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_max_chars", "动态最多字数", 200, 80f..500f, "每条动态最多写几个字。建议150-250。", step = 5f, pairKey = "moment_min_chars", isMinSide = false, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_anchor_count", "动态参考记忆", 3, 0f..10f, "生成动态时最多参考几条公开记忆。建议3-5。", step = 1f, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_recent_post_count", "近期动态参考", 3, 0f..10f, "参考该干员最近几条动态，避免连续重复话题。建议2-3。", step = 1f, enabled = autoEnabled)

            SettingsSectionTitle("日记生成")
            SettingsParamSlider(settings, "diary_min_chars", "日记最少字数", 50, 20f..500f, "每篇日记最少写几个字。太短了内容单薄，建议50-100字。", step = 10f, pairKey = "diary_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "diary_max_chars", "日记最多字数", 300, 100f..800f, "每篇日记最多写几个字。设太小没内容，设太大消耗AI额度。建议200-400字。", step = 10f, pairKey = "diary_min_chars", isMinSide = false)
            SettingsParamSlider(settings, "diary_anchor_count", "日记参考记忆", 5, 0f..20f, "生成日记时最多参考几条关键记忆。设太多日记容易变流水账，建议3-5条。", step = 1f)
            SettingsParamSlider(settings, "diary_group_summary_count", "日记群聊摘要", 3, 0f..10f, "生成日记时最多参考几个群聊里发生的事。建议2-3个。", step = 1f)
            SettingsParamSlider(settings, "diary_relation_event_count", "日记关系事件", 3, 0f..10f, "生成日记时最多参考几条和关系网（朋友、队友等）有关的事件。建议2-3条。", step = 1f)

            SettingsSectionTitle("派遣故事")
            SettingsSwitchCard("派遣快速模式", "开启后派遣段落会更快刷新，适合测试或想快速看结果。正常游玩建议关闭。", settings.dispatchFastMode) { settings.dispatchFastMode = it }
            SettingsParamSlider(settings, "dispatch_min_chars", "每段最少字数", 50, 20f..400f, "派遣故事的每个阶段最少写几个字。只影响文本长度，不影响收益。建议50-100字。", step = 10f, pairKey = "dispatch_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "dispatch_max_chars", "每段最多字数", 300, 100f..800f, "派遣故事的每个阶段最多写几个字。设太小故事单薄，设太大消耗AI额度。建议200-400字。", step = 10f, pairKey = "dispatch_min_chars", isMinSide = false)
        }
    }
}

@Composable
private fun StoryInfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
    Spacer(Modifier.height(10.dp))
}
