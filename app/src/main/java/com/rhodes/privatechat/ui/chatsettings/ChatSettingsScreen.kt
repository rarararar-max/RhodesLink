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
import com.rhodes.privatechat.util.DebugLogger
import org.koin.compose.koinInject

@Composable
fun ChatSettingsScreen(
    onBack: () -> Unit,
    onPromptEditor: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val tabs = listOf("私聊", "群聊", "记忆上下文", "通用")
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
                3 -> GeneralTab(settings, onPromptEditor)
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
    ParamSlider(settings, "dia_seg_min", "最少台词段数", 1, 1f..10f, "干员每次回复最少说几句话。建议设为1，让AI说完整的事就好。设太高（超过3）AI会为了凑段数说一堆废话。")
    ParamSlider(settings, "dia_seg_max", "最多台词段数", 3, 1f..10f, "干员每次回复最多说几句话。建议2-3段，多了容易啰嗦。如果AI说话像在写作文，试试把数字调小。")
    ParamSlider(settings, "dia_min", "台词最少字数", 10, 1f..1000f, "每句话最少写几个字。建议10-20字，太少了AI答得敷衍，太多了每句话都像在写小作文。", step = 5f, pairKey = "dia_max", isMinSide = true)
    ParamSlider(settings, "dia_max", "台词最多字数", 300, 1f..1000f, "每句话最多写几个字。建议200-300字，少于20字AI只能回一问一答，超过500字AI就变得啰嗦了。", step = 5f, pairKey = "dia_min", isMinSide = false)
    Spacer(modifier = Modifier.height(12.dp))

    SectionTitle("旁白（线下/导演模式）")
    ParamSlider(settings, "nar_seg_min", "最少旁白段数", 1, 1f..10f, "环境描写最少出现几次。只在「线下」或「导演」模式下有效。线上模式旁白很短。建议1-2段就好，太多会喧宾夺主。")
    ParamSlider(settings, "nar_seg_max", "最多旁白段数", 3, 1f..10f, "环境描写最多出现几次。线下和导演模式建议不超过3段，超过了像在读剧本。")
    ParamSlider(settings, "nar_min", "旁白最少字数", 50, 1f..1000f, "每段环境描写最少写几个字。太短了画面感不够，建议30-50字。", step = 5f, pairKey = "nar_max", isMinSide = true)
    ParamSlider(settings, "nar_max", "旁白最多字数", 300, 1f..1000f, "每段环境描写最多写几个字。太长会显得啰嗦，建议200-300字。数字越大消耗的AI额度越多。", step = 5f, pairKey = "nar_min", isMinSide = false)
}

// ── Tab 1: 群聊 ──

@Composable
private fun GroupTab(settings: SettingsRepository) {
    SectionTitle("消息长度")
    ParamSlider(settings, "group_msg_min", "每条消息最小字数", 10, 5f..50f, "群聊里每名干员每次说话最少写几个字。建议10-20字，太少了说话没内容。")
    ParamSlider(settings, "group_msg_max", "每条消息最大字数", 100, 30f..200f, "群聊里每名干员每次说话最多写几个字。群聊不是写作文，建议50-100字就够了，太长了一个人刷屏大家就冷场了。", step = 5f, pairKey = "group_msg_min", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("发言频率")
    ParamSlider(settings, "group_speech_min", "每轮每人最少发言", 1, 0f..3f, "每次群聊每名干员至少说几次话。建议1次。设0的话有些干员可能一直不参与对话。")
    ParamSlider(settings, "group_speech_max", "每轮每人最多发言", 2, 1f..5f, "每次群聊每名干员最多说几次话。建议2次就够了，设大了会有个别干员一直刷屏。")

    Spacer(Modifier.height(12.dp)); SectionTitle("自动发言间隔")
    ParamSlider(settings, "group_chat_min_interval", "自动发言最小间隔(秒)", 60, 5f..600f, "群聊没人说话时，AI干员自动聊起来的最短等待时间（秒）。建议30-60秒，太快了群聊停不下来。", step = 5f)
    ParamSlider(settings, "group_chat_max_interval", "自动发言最大间隔(秒)", 180, 30f..900f, "群聊没人说话时，AI干员自动聊起来的最长等待时间（秒）。建议180-300秒，太久群就冷清了。", step = 10f)
    ParamSlider(settings, "group_auto_max_rounds", "自动聊天连续轮数", 50, 1f..300f, "AI干员自动聊天能连续多少轮。到了这个数就自动暂停了，用户发消息或重新开自动会继续。", step = 1f)

    Spacer(Modifier.height(12.dp)); SectionTitle("群聊旁白(线上/线下/导演)")
    ParamSlider(settings, "group_nar_seg_min", "最少旁白段数", 1, 1f..10f, "群聊里环境描写最少出现几次。线上模式下用户看不到旁白。建议1-2段就好。")
    ParamSlider(settings, "group_nar_seg_max", "最多旁白段数", 3, 1f..10f, "群聊里环境描写最多出现几次。超过3段就像在读剧本了。")
    ParamSlider(settings, "group_nar_min", "旁白最小字数", 20, 20f..200f, "每段环境描写最少写几个字。线上模式旁白很短，不受这里控制。", step = 5f, pairKey = "group_nar_max", isMinSide = true)
    ParamSlider(settings, "group_nar_max", "旁白最大字数", 100, 50f..300f, "每段环境描写最多写几个字。群聊旁白建议100字以内，太长了像私聊剧本。", step = 5f, pairKey = "group_nar_min", isMinSide = false)
}

// ── Tab 2: 记忆与印象 ──

@Composable
private fun MemoryTab(settings: SettingsRepository) {
    ParamSlider(settings, "summary_threshold", "触发总结的聊天条数", 20, 3f..200f, "聊多少句话后，AI会自动总结前面聊的内容。设太小（低于10）频繁总结浪费AI额度，太大（超过100）AI记不住前面聊了什么。建议20-50条。", step = 1f)
    ParamSlider(settings, "summary_retain", "保留最近几条总结", 5, 1f..20f, "保留最近几次对话总结，下次聊天时给AI参考。设太小（1-2条）之前的总结容易被丢掉，太大（超过10条）消耗AI上下文额度太多。建议3-5条。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "impression_threshold", "触发印象更新的聊天条数", 50, 1f..50f, "聊多少句话后，AI会重新总结对你的整体印象。设太小频繁更新没必要，建议设5-10。设0关闭印象更新。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "daily_intimacy_cap", "每日好感变化上限", 5, 1f..20f, "每名干员每天最多涨或掉多少好感。调高数值关系推进更快，调低更慢热。建议3-5。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "history_messages", "每次回复最多回看几句", 20, 0f..200f, "AI回看最近多少句话来生成回复。设0=全部回看。建议15-30句，太少AI记不住上下文，太多消耗AI额度。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "clean_days", "记忆过期天数", 30, 1f..365f, "AI对你的对话总结和印象保留多少天。太短（少于7天）AI频繁忘记，太长（超过90天）旧印象一直生效。建议30天。", step = 5f)
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("记忆注入")
    var sourceAwareMemory by remember { mutableStateOf(settings.sourceAwareMemoryEnabled) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("来源感知记忆", fontSize = 13.sp, color = TextPrimary)
                HelpButton("开启后AI会自然地说「你上次跟我说过」「我在朋友圈看到」「之前在群里聊过」之类的话，而不是干巴巴地背记忆。旧记忆会自动推断来源，推断失败就显示为「过去记住的事」。")
            }
            Text("AI会更自然地说「你上次跟我说过」「我在动态下看到」", fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = sourceAwareMemory, onCheckedChange = {
            sourceAwareMemory = it
            settings.sourceAwareMemoryEnabled = it
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(8.dp))
    ParamSlider(settings, "private_anchor_count", "私聊锚点数量", 5, 0f..20f, "每次私聊最多给AI看的关键记忆数量。越高聊天越有连续性，也越消耗AI额度。建议5-8条。", step = 1f)
    ParamSlider(settings, "private_shared_memory_count", "关系共享记忆", 3, 0f..20f, "私聊时通过你建立的关系网，从其他干员那里「听说」的公开记忆数量。设0就关掉这个功能。建议1-3条。", step = 1f)
    ParamSlider(settings, "private_group_context_count", "私聊群聊回顾", 2, 0f..10f, "私聊时最多回顾该干员最近参与过的群聊摘要数量。设0就不回顾群聊内容。建议1-2条。", step = 1f)
    ParamSlider(settings, "group_member_memory_count", "群成员记忆数量", 2, 0f..10f, "群聊生成时每名成员最多携带几条近期公开记忆。设大了帮助记住历史，也消耗更多额度。建议2-3条。", step = 1f)
    ParamSlider(settings, "moment_anchor_count", "动态参考记忆", 3, 0f..10f, "生成动态时最多参考几条公开记忆。设太多动态总是围绕旧事，设太少动态内容容易空洞。建议3-5条。", step = 1f)
    ParamSlider(settings, "comment_context_count", "评论上下文条数", 5, 0f..20f, "AI回复评论时最多回看几条评论区上下文。建议3-5条，多了消耗额度。", step = 1f)
    ParamSlider(settings, "diary_anchor_count", "日记参考记忆", 5, 0f..20f, "生成日记时最多参考几条关键记忆。建议3-5条，多了日记易成流水账。", step = 1f)
}

// ── Tab 3: 通用 ──

@Composable
private fun GeneralTab(settings: SettingsRepository, onPromptEditor: () -> Unit = {}) {
    SectionTitle("上下文模式")
    Text("当前：${modeLabel(settings.contextMode)}。选择模式会批量调整记忆数量、历史消息和自动世界预算，方便快速获得不同的使用体验。已自定义过的Prompt模板不会被覆盖。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton("省钱", settings.contextMode == "economy", Modifier.weight(1f)) { settings.applyContextMode("economy") }
        ModeButton("标准", settings.contextMode == "standard", Modifier.weight(1f)) { settings.applyContextMode("standard") }
        ModeButton("完整", settings.contextMode == "full", Modifier.weight(1f)) { settings.applyContextMode("full") }
    }
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "ai_temperature", "AI 温度", 80, 0f..200f, step = 5f, tip = "AI说话的风格。数字越低越正经稳重（像靠谱助手），越高越奔放活泼（容易跑偏）。建议60-90之间调。当前值除以100就是实际使用的数值。")
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
                HelpButton("开启后，AI会先派一个模型分析你的意图，再让第二个模型生成回复。回复会更贴合上下文，但每次消息会花双倍的AI额度。建议省着用，不差额度的话可以常开。")
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

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) Blue400 else Card)
    ) {
        Text(label, fontSize = 13.sp, color = if (selected) Color.White else TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

private fun modeLabel(mode: String): String = when (mode) {
    "economy" -> "经济"
    "standard" -> "标准"
    "full" -> "完整"
    else -> "自定义"
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
            val oldValue = settings.getInt(key, defaultVal)
            val oldPairValue = if (pairKey != null) settings.getInt(pairKey, defaultVal) else null
            settings.putInt(key, value.toInt())
            DebugLogger.log("Settings/Param", "参数调整: $label($key) $oldValue -> ${value.toInt()}")
            if (pairKey != null) {
                settings.putInt(pairKey, pairValue.toInt())
                DebugLogger.log("Settings/Param", "联动参数: $pairKey ${oldPairValue ?: 0} -> ${pairValue.toInt()}")
            }
        }, valueRange = range, steps = ((range.endInclusive - range.start) / step).toInt(), colors = SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400))
    }
    Spacer(Modifier.height(4.dp))
}
