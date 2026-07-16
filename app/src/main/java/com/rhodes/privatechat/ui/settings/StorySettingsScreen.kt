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
            StoryInfoCard("这里影响什么？", "每日动态参数按北京时间自然日 00:00-23:59 统计；日记只在你手动点击生成时才写；派遣参数只影响派遣故事文本长度，不影响收益、成功率或耗时。")
            SettingsSectionTitle("每日动态")
            SettingsSwitchCard("后台自动 AI", "控制是否让角色自动发动态。关闭后角色不会自己发动态，但你手动聊天和手动刷新不受影响。", autoEnabled) { autoEnabled = it; settings.autoAiEnabled = it }
            SettingsSwitchCard("每日自动动态", "开启后每天会给有权限的角色自动补发新动态。", settings.dailyAutoMomentEnabled, enabled = autoEnabled) { settings.dailyAutoMomentEnabled = it }
            SettingsParamSlider(settings, "daily_auto_ai_limit", "每天自动对话次数上限", 40, 0f..500f, "自动动态、自动评论这类后台任务，每天总共能自动执行多少次。建议30-50。太低（低于10）基本不自动，太高（超过100）后台消耗很大。设为0就是不自动。", step = 5f, enabled = autoEnabled)
            SettingsParamSlider(settings, "daily_moment_target", "每人每天自动动态数", 2, 0f..3f, "每个角色每天最多自动发几条新动态。建议1-2。太高（超过5）信息流会刷屏。设为0就不自动发。", step = 1f, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_min_chars", "动态最少字数", 50, 20f..300f, "每条动态最少写几个字。建议20-50。太低（低于10）只有一句话太敷衍。", step = 5f, pairKey = "moment_max_chars", isMinSide = true, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_max_chars", "动态最多字数", 200, 80f..500f, "每条动态最多写几个字。建议150-250。太高（超过400）每条都是小作文，不像朋友圈。", step = 5f, pairKey = "moment_min_chars", isMinSide = false, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_recent_post_count", "近期动态参考", 3, 0f..10f, "写动态时会参考该角色最近几条旧动态，避免连续重复同一话题。建议2-3。太低（0）可能连着说同一件事，太高（超过5）参考太多反而放不开。", step = 1f, enabled = autoEnabled)

            SettingsSectionTitle("日记生成")
            SettingsParamSlider(settings, "diary_min_chars", "日记最少字数", 50, 20f..500f, "每篇日记最少写几个字。建议50-100。太低（低于30）日记太短没内容。", step = 10f, pairKey = "diary_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "diary_max_chars", "日记最多字数", 300, 100f..800f, "每篇日记最多写几个字。建议200-400。太高（超过500）每篇日记都很长，花钱多且像在写作文。", step = 10f, pairKey = "diary_min_chars", isMinSide = false)
            SettingsParamSlider(settings, "diary_group_summary_count", "日记群聊摘要", 3, 0f..10f, "写日记时最多参考几个群的聊天内容。建议2-3。太低（0）日记里不会提到群聊的事，太高（超过5）日记里塞太多群信息。", step = 1f)
            SettingsParamSlider(settings, "diary_relation_event_count", "日记关系事件", 3, 0f..10f, "写日记时最多参考几条和其他角色的互动事件。建议2-3。太低（0）日记不提和其他人的交往，太高（超过5）日记变成报流水账。", step = 1f)

            SettingsSectionTitle("派遣故事")
            SettingsSwitchCard("派遣快速模式", "开启后派遣剧情刷新更快，适合想快速看结果。正常玩建议关掉。", settings.dispatchFastMode) { settings.dispatchFastMode = it }
            SettingsParamSlider(settings, "dispatch_min_chars", "每段最少字数", 50, 20f..400f, "派遣故事的每段最少写几个字。建议50-100。太低（低于30）故事太短没看头。", step = 10f, pairKey = "dispatch_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "dispatch_max_chars", "每段最多字数", 300, 100f..800f, "派遣故事的每段最多写几个字。建议200-400。太高（超过500）故事太长，等得久花钱也多。", step = 10f, pairKey = "dispatch_min_chars", isMinSide = false)
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
