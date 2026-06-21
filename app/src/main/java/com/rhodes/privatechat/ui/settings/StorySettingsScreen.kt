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
    var worldSchedulerEnabled by remember { mutableStateOf(settings.worldSchedulerEnabled) }
    val worldEnabled = autoEnabled && worldSchedulerEnabled
    SaveableSettingsScaffold(
        title = "日记与派遣",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Primary) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            StoryInfoCard("这里影响什么？", "日记参数会影响“自动日记”和你手动查看时生成的日记；派遣参数只影响派遣故事文本长度，不影响收益、成功率或耗时。")
            SettingsSectionTitle("日记生成")
            SettingsSwitchCard("自动生成日记", "开启后，系统会在后台为部分活跃干员写昨日记事。需要“后台自动 AI”和“大世界运行”同时开启；手动查看日记不受影响。", settings.autoDiaryEnabled, enabled = worldEnabled) { settings.autoDiaryEnabled = it }
            SettingsParamSlider(settings, "daily_diary_operator_limit", "每日自动日记人数", 3, 0f..20f, "每天最多自动给多少名干员生成日记。按活跃度选人。建议2-5人，太多会消耗AI额度。", step = 1f, enabled = worldEnabled)
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
