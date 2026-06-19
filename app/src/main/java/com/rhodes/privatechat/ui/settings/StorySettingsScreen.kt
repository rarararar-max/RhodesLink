package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun StorySettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    Column(modifier = modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Primary)
            Spacer(Modifier.width(6.dp))
            Text("日记与派遣", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsSectionTitle("日记生成")
            SettingsSwitchCard("自动生成日记", "开：世界调度每天会为部分活跃干员自动写日记，记录前一天的事情。手动偷看日记不受自动AI预算限制，不影响自动日记的次数。", settings.autoDiaryEnabled) { settings.autoDiaryEnabled = it }
            SettingsParamSlider(settings, "daily_diary_operator_limit", "每日自动日记人数", 3, 0f..20f, "每天最多自动给多少名干员生成日记。按活跃度选人。建议2-5人，太多了消耗AI额度。", step = 1f)
            SettingsParamSlider(settings, "diary_min_chars", "日记最少字数", 50, 50f..500f, "每篇日记最少写几个字。太短了内容单薄，建议50-100字。", step = 10f, pairKey = "diary_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "diary_max_chars", "日记最多字数", 300, 100f..800f, "每篇日记最多写几个字。设太小没内容，设太大消耗AI额度。建议200-400字。", step = 10f, pairKey = "diary_min_chars", isMinSide = false)
            SettingsParamSlider(settings, "diary_anchor_count", "日记参考记忆", 5, 0f..20f, "生成日记时最多参考几条关键记忆。设太多日记容易变流水账，建议3-5条。", step = 1f)
            SettingsParamSlider(settings, "diary_group_summary_count", "日记群聊摘要", 3, 0f..10f, "生成日记时最多参考几个群聊里发生的事。建议2-3个。", step = 1f)
            SettingsParamSlider(settings, "diary_relation_event_count", "日记关系事件", 3, 0f..10f, "生成日记时最多参考几条和关系网（朋友、队友等）有关的事件。建议2-3条。", step = 1f)

            SettingsSectionTitle("派遣故事")
            SettingsSwitchCard("派遣快速模式", "开：派遣过程推进更快，适合测试功能。关：正常游玩建议关掉，慢慢体验故事。", settings.dispatchFastMode) { settings.dispatchFastMode = it }
            SettingsParamSlider(settings, "dispatch_min_chars", "每段最少字数", 50, 50f..400f, "派遣故事的每个阶段（准备出发/途中经过/结局归来）最少写几个字。建议50-100字。", step = 10f, pairKey = "dispatch_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "dispatch_max_chars", "每段最多字数", 300, 200f..800f, "派遣故事的每个阶段最多写几个字。设太小故事太单薄，设太大消耗AI额度。建议200-400字。", step = 10f, pairKey = "dispatch_min_chars", isMinSide = false)
        }
    }
}
