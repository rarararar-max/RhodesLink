package com.rhodes.privatechat.ui.chatsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun ChatSettingsScreen(
    onBack: () -> Unit,
    onPromptEditor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val tabs = listOf("私聊", "群聊", "记忆与印象", "功能", "通用", "其他")
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("聊天参数设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        ScrollableTabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tabIndex) {
                0 -> PrivateTab(settings)
                1 -> GroupTab(settings)
                2 -> MemoryTab(settings)
                3 -> FeatureTab(settings)
                 4 -> GeneralTab(settings, onPromptEditor)
                 5 -> OtherTab(settings)
            }
        }
    }
    }

}

// ── Utility composables ──

@Composable
private fun SectionTitle(title: String, small: Boolean = false) {
    Text(title, fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun HelpButton(message: String) {
    var show by remember { mutableStateOf(false) }
    Box(modifier = Modifier.size(20.dp).clip(CircleShape).clickable { show = true }, contentAlignment = Alignment.Center) {
        Text("?", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTertiary)
    }
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false }) { Text("知道了", color = Primary) } },
            text = { Text(message, fontSize = 14.sp, color = TextPrimary) }
        )
    }
}

// ── Tab 0: 私聊 ──

@Composable
private fun PrivateTab(settings: SettingsRepository) {
    SectionTitle("台词（共用：线上/线下/导演）")
    ParamSlider(settings, "dia_seg_min", "最少台词段数", 1, 1f..10f, "所有模式共用：AI回复中台词段落的最少段数")
    ParamSlider(settings, "dia_seg_max", "最多台词段数", 3, 1f..10f, "所有模式共用：AI回复中台词段落的最多段数")
    ParamSlider(settings, "dia_min", "台词最少字数", 10, 1f..1000f, "所有模式共用：每段台词的最少字数", step = 5f, pairKey = "dia_max", isMinSide = true)
    ParamSlider(settings, "dia_max", "台词最多字数", 80, 1f..1000f, "所有模式共用：每段台词的最多字数", step = 5f, pairKey = "dia_min", isMinSide = false)
    Spacer(modifier = Modifier.height(12.dp))

    SectionTitle("旁白（线下/导演模式）")
    ParamSlider(settings, "nar_seg_min", "最少旁白段数", 1, 1f..10f, "线下/导演模式：旁白段落最少段数。线上模式旁白极简≤20字，不由本参数控制")
    ParamSlider(settings, "nar_seg_max", "最多旁白段数", 3, 1f..10f, "线下/导演模式：旁白段落最多段数")
    ParamSlider(settings, "nar_min", "旁白最少字数", 20, 1f..1000f, "线下/导演模式：每段旁白的最少字数", step = 5f, pairKey = "nar_max", isMinSide = true)
    ParamSlider(settings, "nar_max", "旁白最多字数", 150, 1f..1000f, "线下/导演模式：每段旁白的最多字数", step = 5f, pairKey = "nar_min", isMinSide = false)
}

// ── Tab 1: 群聊 ──

@Composable
private fun GroupTab(settings: SettingsRepository) {
    SectionTitle("消息长度")
    ParamSlider(settings, "group_msg_min", "每条消息最小字数", 10, 5f..50f, "群聊中每条AI消息的最少字数")
    ParamSlider(settings, "group_msg_max", "每条消息最大字数", 100, 30f..200f, "群聊中每条AI消息的最多字数", step = 5f, pairKey = "group_msg_min", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("发言频率")
    ParamSlider(settings, "group_speech_min", "每轮每人最少发言次数", 1, 0f..3f, "每个干员每轮群聊至少发言几次")
    ParamSlider(settings, "group_speech_max", "每轮每人最多发言次数", 2, 1f..5f, "每个干员每轮群聊最多发言几次")

    Spacer(Modifier.height(12.dp)); SectionTitle("自动发言间隔")
    ParamSlider(settings, "group_chat_min_interval", "自动发言最小间隔(秒)", 60, 5f..600f, "AI自动发言的最小间隔时间", step = 5f)
    ParamSlider(settings, "group_chat_max_interval", "自动发言最大间隔(秒)", 180, 30f..900f, "AI自动发言的最大间隔时间", step = 10f)

    Spacer(Modifier.height(12.dp)); SectionTitle("群聊旁白(线上/线下/导演)")
    ParamSlider(settings, "group_nar_seg_min", "最少旁白段数", 1, 1f..10f, "群聊每轮最少旁白段数，线上模式旁白用户不可见")
    ParamSlider(settings, "group_nar_seg_max", "最多旁白段数", 3, 1f..10f, "群聊每轮最多旁白段数，线上模式旁白用户不可见")
    ParamSlider(settings, "group_nar_min", "旁白最小字数", 20, 20f..200f, "群聊线下/导演模式每段旁白最少字数。线上模式旁白不超过20字不由本参数控制", step = 5f, pairKey = "group_nar_max", isMinSide = true)
    ParamSlider(settings, "group_nar_max", "旁白最大字数", 100, 50f..300f, "群聊线下/导演模式每段旁白最多字数。线上模式旁白不超过20字不由本参数控制", step = 5f, pairKey = "group_nar_min", isMinSide = false)
}

// ── Tab 2: 记忆与印象 ──

@Composable
private fun MemoryTab(settings: SettingsRepository) {
    ParamSlider(settings, "summary_threshold", "触发滚动摘要的消息轮数", 20, 3f..200f, "每N条消息后自动生成一次对话摘要，用于长期上下文记忆", step = 1f)
    ParamSlider(settings, "summary_retain", "滚动摘要保留条数", 10, 1f..20f, "保留最近N条摘要用于注入对话上下文", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "impression_threshold", "触发长期印象更新的最少消息数", 50, 1f..50f, "每N条消息后更新一次AI对用户的长期印象", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "history_messages", "保留最近消息轮数（0=全部）", 20, 0f..200f, "只向AI发送最近N条历史消息，超出部分由摘要补充上下文，防止Token爆表", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "clean_days", "记忆过期天数", 30, 1f..365f, "超过此天数的短期摘要、每日摘要和长期印象将自动删除", step = 5f)
}

// ── Tab 3: 功能 ──

@Composable
private fun FeatureTab(settings: SettingsRepository) {
    SectionTitle("日记生成")
    ParamSlider(settings, "diary_min_chars", "日记最小字数", 50, 50f..500f, "控制AI生成日记的最小字数", step = 10f, pairKey = "diary_max_chars", isMinSide = true)
    ParamSlider(settings, "diary_max_chars", "日记最大字数", 300, 100f..800f, "控制AI生成日记的最大字数，越大内容越详细", step = 10f, pairKey = "diary_min_chars", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("动态生成")
    ParamSlider(settings, "moment_min_chars", "动态最小字数", 30, 20f..300f, "控制AI生成动态的最小字数", step = 5f, pairKey = "moment_max_chars", isMinSide = true)
    ParamSlider(settings, "moment_max_chars", "动态最大字数", 200, 80f..500f, "控制AI生成动态的最大字数", step = 5f, pairKey = "moment_min_chars", isMinSide = false)
    ParamSlider(settings, "daily_moment_target", "每人每日动态目标数", 2, 1f..10f, "每天自动触发动态生成的条数目标，少于这个数会自动补充")

    Spacer(Modifier.height(12.dp)); SectionTitle("评论生成")
    ParamSlider(settings, "comment_min_chars", "评论最少字数", 10, 5f..30f, "AI评论每条最少字数")
    ParamSlider(settings, "comment_max_chars", "评论最多字数", 40, 10f..100f, "AI评论每条最多字数", step = 5f, pairKey = "comment_min_chars", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("派遣故事")
    ParamSlider(settings, "dispatch_min_chars", "每段最小字数", 50, 50f..400f, "派遣故事每段（准备/过程/结局各段）的字数下限。总字数=每段字数×段数", step = 10f, pairKey = "dispatch_max_chars", isMinSide = true)
    ParamSlider(settings, "dispatch_max_chars", "每段最大字数", 200, 200f..800f, "派遣故事每段的字数上限", step = 10f, pairKey = "dispatch_min_chars", isMinSide = false)
}

// ── Tab 4: 通用 ──

@Composable
private fun GeneralTab(settings: SettingsRepository, onPromptEditor: () -> Unit = {}) {
    ParamSlider(settings, "ai_temperature", "AI 温度", 95, 0f..200f, step = 5f, tip = "控制AI回复的随机性，值越低越保守，值越高越有创意（当前值/100）")
    Spacer(modifier = Modifier.height(12.dp))
    var dualModel by remember { mutableStateOf(settings.dualModel) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("深度分析模式（双模型生成回复）", fontSize = 13.sp, color = TextPrimary)
                HelpButton("先让一个分析模型理解用户意图，再让生成模型产出回复。更贴合上下文但消耗双倍Token。")
            }
            Text("分析模型预处理 + 生成模型输出，回复更贴合上下文", fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = dualModel, onCheckedChange = {
            dualModel = it
            settings.dualModel = it
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(6.dp))

    Button(
        onClick = onPromptEditor,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Blue400)
    ) {
        Text("高级编辑", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
    Spacer(modifier = Modifier.height(12.dp))
}

// ── Tab 5: 其他 ──

@Composable
private fun OtherTab(settings: SettingsRepository) {

    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("深色模式", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Beta", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AccentOrange, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(AccentOrange.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Text("切换应用界面的深色/浅色主题，默认深色", fontSize = 12.sp, color = TextSecondary)
            }
            Switch(checked = isDarkMode, onCheckedChange = {
                isDarkMode = it
                settings.darkMode = it
            }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
        }
    }
}

// ── Shared slider components ──

@Composable
private fun ParamSlider(settings: SettingsRepository, key: String, label: String, defaultVal: Int, range: ClosedFloatingPointRange<Float>, tip: String, step: Float = 1f, pairKey: String? = null, isMinSide: Boolean = true) {
    var value by remember { mutableFloatStateOf(settings.getInt(key, defaultVal).toFloat().coerceIn(range)) }
    var pairValue by remember { mutableFloatStateOf(if (pairKey != null) settings.getInt(pairKey, defaultVal).toFloat() else 0f) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = TextPrimary)
            Spacer(Modifier.width(2.dp))
            HelpButton(tip)
            Spacer(Modifier.weight(1f))
            Text("${value.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Blue400)
        }
        Slider(value = value, onValueChange = { v ->
            if (pairKey != null) {
                if (isMinSide) {
                    if (v <= pairValue) { value = v }
                    else { value = pairValue; pairValue = v }
                } else {
                    if (v >= pairValue) { value = v }
                    else { value = pairValue; pairValue = v }
                }
            } else { value = v }
        }, onValueChangeFinished = {
            settings.putInt(key, value.toInt())
            if (pairKey != null) settings.putInt(pairKey, pairValue.toInt())
        }, valueRange = range, steps = ((range.endInclusive - range.start) / step).toInt(), colors = SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400))
    }
    Spacer(Modifier.height(4.dp))
}
