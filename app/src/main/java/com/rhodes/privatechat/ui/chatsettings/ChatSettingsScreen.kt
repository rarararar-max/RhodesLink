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
    ParamSlider(settings, "dia_seg_min", "最少台词段数", 1, 1f..10f, "AI每次回复最少说几句话。设太高（>3）AI会被迫凑字数，不自然")
    ParamSlider(settings, "dia_seg_max", "最多台词段数", 3, 1f..10f, "AI每次回复最多说几句话。设太小（<2）AI只能回一句短的，没深度")
    ParamSlider(settings, "dia_min", "台词最少字数", 10, 1f..1000f, "每句话最少写多少个字。设太大（>50）AI每句都像写作文，不像聊天", step = 5f, pairKey = "dia_max", isMinSide = true)
    ParamSlider(settings, "dia_max", "台词最多字数", 300, 1f..1000f, "每句话最多写多少个字。设太小（<20）AI答得敷衍，设太大（>500）AI变得啰嗦", step = 5f, pairKey = "dia_min", isMinSide = false)
    Spacer(modifier = Modifier.height(12.dp))

    SectionTitle("旁白（线下/导演模式）")
    ParamSlider(settings, "nar_seg_min", "最少旁白段数", 1, 1f..10f, "环境描写最少出现几次。线下/导演模式有效，线上模式旁白很短不由这里控制")
    ParamSlider(settings, "nar_seg_max", "最多旁白段数", 3, 1f..10f, "环境描写最多出现几次。设多了（>5）剧本文案太浓")
    ParamSlider(settings, "nar_min", "旁白最少字数", 50, 1f..1000f, "每段环境描写最少写多少个字。太短（<20）描写没画面感", step = 5f, pairKey = "nar_max", isMinSide = true)
    ParamSlider(settings, "nar_max", "旁白最多字数", 300, 1f..1000f, "每段环境描写最多写多少个字。太大（>500）场景描写喧宾夺主", step = 5f, pairKey = "nar_min", isMinSide = false)
}

// ── Tab 1: 群聊 ──

@Composable
private fun GroupTab(settings: SettingsRepository) {
    SectionTitle("消息长度")
    ParamSlider(settings, "group_msg_min", "每条消息最小字数", 10, 5f..50f, "群里每条消息最少写几个字")
    ParamSlider(settings, "group_msg_max", "每条消息最大字数", 100, 30f..200f, "群里每条消息最多写几个字。群聊不宜太长，设大（>200）一人发一段话群就冷场了", step = 5f, pairKey = "group_msg_min", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("发言频率")
    ParamSlider(settings, "group_speech_min", "每轮每人最少发言", 1, 0f..3f, "每次群聊每个干员至少说几次话。设0等于允许干员不加入对话")
    ParamSlider(settings, "group_speech_max", "每轮每人最多发言", 2, 1f..5f, "每次群聊每个干员最多说几次话。设太大（>5）有人一直刷屏")

    Spacer(Modifier.height(12.dp)); SectionTitle("自动发言间隔")
    ParamSlider(settings, "group_chat_min_interval", "自动发言最小间隔(秒)", 60, 5f..600f, "群聊没说话时，系统自动让干员聊起来的最小等待时间。设太小（<10）群像发疯一样不停聊", step = 5f)
    ParamSlider(settings, "group_chat_max_interval", "自动发言最大间隔(秒)", 180, 30f..900f, "群聊没说话时，系统自动让干员开始聊的最大等待时间。设太大（>600）群活跃不起来", step = 10f)

    Spacer(Modifier.height(12.dp)); SectionTitle("群聊旁白(线上/线下/导演)")
    ParamSlider(settings, "group_nar_seg_min", "最少旁白段数", 1, 1f..10f, "群聊每轮环境描写最少几次。线上模式旁白用户不可见")
    ParamSlider(settings, "group_nar_seg_max", "最多旁白段数", 3, 1f..10f, "群聊每轮环境描写最多几次。设多了（>5）剧本文案太浓")
    ParamSlider(settings, "group_nar_min", "旁白最小字数", 20, 20f..200f, "每段环境描写最少几个字。线上模式旁白很短不由这里控制", step = 5f, pairKey = "group_nar_max", isMinSide = true)
    ParamSlider(settings, "group_nar_max", "旁白最大字数", 100, 50f..300f, "每段环境描写最多几个字。群聊旁白太长（>200）像私聊剧本", step = 5f, pairKey = "group_nar_min", isMinSide = false)
}

// ── Tab 2: 记忆与印象 ──

@Composable
private fun MemoryTab(settings: SettingsRepository) {
    ParamSlider(settings, "summary_threshold", "触发总结的聊天条数", 20, 3f..200f, "聊多少句话后，AI会自动把前面聊的内容总结一遍。设太小（<5）频繁总结浪费钱，太大（>100）AI记不住前面聊了什么", step = 1f)
    ParamSlider(settings, "summary_retain", "保留最近几条总结", 5, 1f..20f, "保留最近几次的总结，下次聊天时给AI看。设太小（1）之前的总结被丢掉，设太大（>20）消耗Token多", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "impression_threshold", "触发印象更新的聊天条数", 50, 1f..50f, "聊多少句话后，AI会重新总结对你的整体印象。设太小（<10）频繁更新没必要，设0关闭", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "history_messages", "每次回复最多回看几句", 20, 0f..200f, "AI回看最近多少句话来生成回复。0=全部。设太小（<5）AI记不住上下文，太大（>200）消耗Token多", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "clean_days", "记忆过期天数", 30, 1f..365f, "AI对你的总结和印象保留多少天。太短（<7）AI频繁忘记，太长（>90）旧印象一直生效", step = 5f)
}

// ── Tab 3: 功能 ──

@Composable
private fun FeatureTab(settings: SettingsRepository) {
    SectionTitle("日记生成")
    ParamSlider(settings, "diary_min_chars", "日记最小字数", 50, 50f..500f, "干员日记每篇最少写几个字", step = 10f, pairKey = "diary_max_chars", isMinSide = true)
    ParamSlider(settings, "diary_max_chars", "日记最大字数", 300, 100f..800f, "干员日记每篇最多写几个字。设太小（<100）日记没内容，太大（>500）太长了", step = 10f, pairKey = "diary_min_chars", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("动态生成")
    ParamSlider(settings, "moment_min_chars", "动态最小字数", 50, 20f..300f, "干员发朋友圈最少写几个字", step = 5f, pairKey = "moment_max_chars", isMinSide = true)
    ParamSlider(settings, "moment_max_chars", "动态最大字数", 200, 80f..500f, "干员发朋友圈最多写几个字。太小（<30）没内容，太大（>400）像写小作文", step = 5f, pairKey = "moment_min_chars", isMinSide = false)
    ParamSlider(settings, "daily_moment_target", "每人每日动态目标", 2, 1f..10f, "每天最多自动生成几条新动态。设太大（>5）首页全是历史动态刷屏。只在启动App时触发")

    Spacer(Modifier.height(12.dp)); SectionTitle("评论生成")
    ParamSlider(settings, "comment_min_chars", "评论最少字数", 10, 5f..30f, "干员评论动态最少写几个字")
    ParamSlider(settings, "comment_max_chars", "评论最多字数", 40, 10f..100f, "干员评论动态最多写几个字", step = 5f, pairKey = "comment_min_chars", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("派遣故事")
    ParamSlider(settings, "dispatch_min_chars", "每段最小字数", 50, 50f..400f, "派遣故事每段（准备/过程/结局）最少写几个字。总字数=每段字数×段数", step = 10f, pairKey = "dispatch_max_chars", isMinSide = true)
    ParamSlider(settings, "dispatch_max_chars", "每段最大字数", 300, 200f..800f, "派遣故事每段最多写几个字。设太小（<100）故事太单薄", step = 10f, pairKey = "dispatch_min_chars", isMinSide = false)
}

// ── Tab 4: 通用 ──

@Composable
private fun GeneralTab(settings: SettingsRepository, onPromptEditor: () -> Unit = {}) {
    ParamSlider(settings, "ai_temperature", "AI 温度", 80, 0f..200f, step = 5f, tip = "AI说话的风格。越低越正经（机器人感强），越高越奔放（容易跑偏）。建议60-90之间调。当前值/100")
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
            Switch(checked = com.rhodes.privatechat.ui.theme.isDarkMode, onCheckedChange = {
                com.rhodes.privatechat.ui.theme.isDarkMode = it
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
                pairValue = settings.getInt(pairKey, defaultVal).toFloat()
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
