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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.rhodes.privatechat.ui.settings.SaveableSettingsScaffold
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
    val tabs = listOf("预设", "私聊", "群聊", "记忆")
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    SaveableSettingsScaffold(
        title = "聊天参数设置",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.Default.Tune, null, tint = Primary) }
    ) {

        ScrollableTabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tabIndex) {
                0 -> GeneralTab(settings, onPromptEditor)
                1 -> PrivateTab(settings)
                2 -> GroupTab(settings)
                3 -> MemoryTab(settings)
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
    ParamSlider(settings, "nar_seg_min", "最少旁白段数", 0, 0f..10f, "只在线下或导演模式有效；线上模式不会显示旁白。设为0时，仅在场景推进需要时自然加入旁白。")
    ParamSlider(settings, "nar_seg_max", "最多旁白段数", 3, 1f..10f, "环境描写最多出现几次。线下和导演模式建议不超过3段，超过了像在读剧本。")
    ParamSlider(settings, "nar_min", "旁白最少字数", 20, 0f..1000f, "每段环境描写最少写几个字。只在线下或导演模式有效，建议20-50字。", step = 5f, pairKey = "nar_max", isMinSide = true)
    ParamSlider(settings, "nar_max", "旁白最多字数", 300, 1f..1000f, "每段环境描写最多写几个字。太长会显得啰嗦，建议200-300字。数字越大消耗的AI额度越多。", step = 5f, pairKey = "nar_min", isMinSide = false)
}

// ── Tab 1: 群聊 ──

@Composable
private fun GroupTab(settings: SettingsRepository) {
    Text("群聊自动聊天只在你为该群开启“空闲自动聊天”后生效，并且到达设定时间才会触发。具体群是否启用，请到权限管理或群聊编辑页设置。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Spacer(Modifier.height(8.dp))
    SectionTitle("消息长度")
    ParamSlider(settings, "group_msg_min", "每条消息最小字数", 10, 5f..50f, "群聊里每名干员每次说话最少写几个字。建议10-20字，太少了说话没内容。")
    ParamSlider(settings, "group_msg_max", "每条消息最大字数", 100, 30f..200f, "群聊里每名干员每次说话最多写几个字。群聊不是写作文，建议50-100字就够了，太长了一个人刷屏大家就冷场了。", step = 5f, pairKey = "group_msg_min", isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("发言频率")
    ParamSlider(settings, "group_speech_min", "每轮每人最少发言", 1, 1f..3f, "每次群聊每名未禁言干员至少说几次话。建议1次。")
    ParamSlider(settings, "group_speech_max", "每轮每人最多发言", 2, 1f..5f, "每次群聊每名干员最多说几次话。建议2次就够了，设大了会有个别干员一直刷屏。")

    Spacer(Modifier.height(12.dp)); SectionTitle("空闲自动聊天")
    ParamSlider(settings, "group_chat_min_interval", "空闲最小间隔(秒)", 60, 5f..600f, "开启群的空闲自动聊天后，AI干员自己聊起来的最短等待时间。建议60秒以上，太快容易刷屏。", step = 5f)
    ParamSlider(settings, "group_chat_max_interval", "空闲最大间隔(秒)", 180, 30f..900f, "开启群的空闲自动聊天后，AI干员自己聊起来的最长等待时间。建议180-300秒。", step = 10f)
    ParamSlider(settings, "group_auto_max_rounds", "空闲连续轮数", 20, 1f..300f, "空闲自动聊天最多连续聊多少轮。建议10-30，太高容易刷屏。", step = 1f)

    Spacer(Modifier.height(12.dp)); SectionTitle("群聊旁白(线下/导演)")
    Text("线下和导演模式中，每位活跃成员会自动对应一段旁白和至少一段台词；成员越多，生成时间和消耗越高。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    ParamSlider(settings, "group_nar_min", "旁白最小字数", 20, 0f..200f, "每段环境描写最少写几个字。只在线下或导演模式有效。", step = 5f, pairKey = "group_nar_max", isMinSide = true)
    ParamSlider(settings, "group_nar_max", "旁白最大字数", 100, 50f..300f, "每段环境描写最多写几个字。群聊旁白建议100字以内，太长了像私聊剧本。", step = 5f, pairKey = "group_nar_min", isMinSide = false)
}

// ── Tab 2: 记忆与印象 ──

@Composable
private fun MemoryTab(settings: SettingsRepository) {
    ParamSlider(settings, "summary_threshold", "触发总结的聊天条数", 20, 3f..200f, "聊多少句话后，AI会自动总结前面聊的内容。设太小（低于10）频繁总结浪费AI额度，太大（超过100）AI记不住前面聊了什么。建议20-50条。", step = 1f)
    ParamSlider(settings, "summary_retain", "保留最近原始消息", 5, 1f..50f, "滚动摘要会先保留最近几条原始消息，较早内容才会合并进摘要。设太小会过早压缩上下文，太大则摘要更新变慢。建议3-5条。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "impression_threshold", "触发印象更新的聊天条数", 50, 5f..100f, "聊多少句话后，AI会重新总结对你的整体印象。设太小会频繁消耗额度，设太大则印象变化较慢。建议30-80条。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "daily_intimacy_cap", "每日好感变化上限", 5, 1f..20f, "每名干员每天最多涨或掉多少好感。调高数值关系推进更快，调低更慢热。建议3-5。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "history_messages", "每次回复最多回看几句", 20, 0f..200f, "AI每次回复最多参考最近多少句聊天。设0表示不按条数限制，但仍会受模型上下文上限影响。建议15-30句。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "clean_days", "记忆过期天数", 30, 0f..365f, "AI对你的对话总结和印象保留多少天。0=不自动过期；太短AI容易忘记，太长旧印象会长期生效。建议30天。", step = 5f)
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("记忆生成")
    SettingsSwitchCard(
        title = "摘要游标",
        subtitle = "只总结上次以后新增的消息，避免旧聊天被反复总结",
        tip = "开启后系统会记录每个会话已经总结到哪条消息，下一次只处理新增消息。建议开启。关闭后会退回旧逻辑。",
        checked = settings.summaryCursorEnabled,
        onCheckedChange = { settings.summaryCursorEnabled = it }
    )
    SettingsSwitchCard(
        title = "Memory V2 分层记忆",
        subtitle = "把聊天提取成 L1/L2/L3 结构化记忆，并写入向量库",
        tip = "开启后私聊和群聊会把有价值信息沉淀为结构化记忆。会额外消耗少量AI额度。建议开启。",
        checked = settings.memoryV2Enabled,
        onCheckedChange = { settings.memoryV2Enabled = it }
    )
    SettingsSwitchCard(
        title = "动态/评论进入 Memory V2",
        subtitle = "用户动态和评论会进入公开记忆，便于后续私聊自然提起",
        tip = "开启后，用户发的动态和评论会被提取为公开事件记忆。动态默认全员可见。",
        checked = settings.momentMemoryV2Enabled,
        onCheckedChange = { settings.momentMemoryV2Enabled = it }
    )
    ParamSlider(settings, "memory_v2_promote_l1_threshold", "L1 合并为 L2 阈值", 20, 5f..100f, "同一干员的 L1 记忆达到多少条后，合并成中期 L2 记忆。越小越频繁消耗额度，建议20。", step = 1f)
    ParamSlider(settings, "memory_v2_promote_l2_threshold", "L2 合并为 L3 阈值", 10, 3f..50f, "同一干员的 L2 记忆达到多少条后，合并成长期 L3 记忆。建议10。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("记忆注入")
    var distinguishPrivateMemory by remember { mutableStateOf(settings.distinguishPrivateMemory) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("区分私密与公开信息", fontSize = 13.sp, color = TextPrimary)
                HelpButton("开启：私聊中被标为私密的记忆只给当前干员自己使用，不会通过关系网、动态、评论、群聊等公开场景传给别人。关闭：私聊和公开来源的锚点都按普通记忆参与传递，关系网也可以读取私密锚点。默认建议开启，想让世界信息完全流通时可关闭。")
            }
            Text("关掉后，私聊记忆也会像公开记忆一样参与关系传递", fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = distinguishPrivateMemory, onCheckedChange = {
            distinguishPrivateMemory = it
            settings.distinguishPrivateMemory = it
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(8.dp))
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
    SettingsSwitchCard(
        title = "全局公开记忆池",
        subtitle = "动态、评论等公开内容可被任意干员私聊按话题召回",
        tip = "开启后用户动态和评论会进入 global/public 公开池。私聊时会按当前话题召回相关公开信息。建议开启。",
        checked = settings.globalPublicMemoryEnabled,
        onCheckedChange = { settings.globalPublicMemoryEnabled = it }
    )
    ParamSlider(settings, "global_public_memory_count", "全局公开召回数量", 5, 0f..20f, "每次私聊最多额外召回多少条公开动态/评论/世界事件。设0可关闭公开池注入。建议3-5条。", step = 1f)
    Spacer(modifier = Modifier.height(8.dp))
    ParamSlider(settings, "private_anchor_count", "私聊锚点数量", 5, 0f..20f, "每次私聊最多给AI看的关键记忆数量。越高聊天越有连续性，也越消耗AI额度。建议5-8条。", step = 1f)
    ParamSlider(settings, "private_shared_memory_count", "关系共享记忆", 3, 0f..20f, "私聊时，当前干员可能通过关系网「听说」其他干员的公开记忆。数值越高，能参考的关系记忆越多。只传公开记忆，不会直接泄露私聊秘密。设0可关闭。建议1-3条。", step = 1f)
    ParamSlider(settings, "private_group_context_count", "私聊群聊回顾", 2, 0f..10f, "私聊时最多回顾该干员最近参与过的群聊摘要数量。设0就不回顾群聊内容。建议1-2条。", step = 1f)
    ParamSlider(settings, "group_member_memory_count", "群成员记忆数量", 2, 0f..10f, "群聊生成时每名成员最多携带几条近期公开记忆。设大了帮助记住历史，也消耗更多额度。建议2-3条。", step = 1f)
    ParamSlider(settings, "moment_anchor_count", "动态参考记忆", 3, 0f..10f, "生成动态时最多参考几条公开记忆。设太多动态总是围绕旧事，设太少动态内容容易空洞。建议3-5条。", step = 1f)
    ParamSlider(settings, "comment_context_count", "评论上下文条数", 5, 0f..20f, "AI回复评论时最多回看几条评论区上下文。建议3-5条，多了消耗额度。", step = 1f)
    ParamSlider(settings, "diary_anchor_count", "日记参考记忆", 5, 0f..20f, "生成日记时最多参考几条关键记忆。建议3-5条，多了日记易成流水账。", step = 1f)
}

@Composable
private fun SettingsSwitchCard(title: String, subtitle: String, tip: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var value by remember { mutableStateOf(checked) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 13.sp, color = TextPrimary)
                HelpButton(tip)
            }
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = value, onCheckedChange = {
            value = it
            onCheckedChange(it)
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(8.dp))
}

// ── Tab 3: 通用 ──

@Composable
private fun GeneralTab(settings: SettingsRepository, onPromptEditor: () -> Unit = {}) {
    SectionTitle("推荐预设")
    var contextMode by remember { mutableStateOf(settings.contextMode) }
    Text("当前：${modeLabel(contextMode)}。选择预设会批量调整记忆数量、历史消息和自动世界预算；已自定义过的 Prompt 模板不会被覆盖。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton("省钱", contextMode == "economy", Modifier.weight(1f)) { settings.applyContextMode("economy"); contextMode = "economy" }
        ModeButton("标准", contextMode == "standard", Modifier.weight(1f)) { settings.applyContextMode("standard"); contextMode = "standard" }
        ModeButton("完整", contextMode == "full", Modifier.weight(1f)) { settings.applyContextMode("full"); contextMode = "full" }
    }
    Spacer(modifier = Modifier.height(8.dp))
    ModeInfoCard(
        "省钱模式",
        "减少自动生成和记忆注入，适合 API 额度紧张或主要手动聊天。回看最近12句，私聊记忆3条，每日后台AI预算20次，自动日记关闭。"
    )
    ModeInfoCard(
        "标准模式（推荐）",
        "聊天、记忆、动态和日记比较均衡，适合日常使用。回看最近20句，私聊记忆5条，每人每日自动动态2条，每日后台AI预算40次。"
    )
    ModeInfoCard(
        "完整模式",
        "更多记忆、更活跃的世界联动，但会明显增加 AI 调用。回看最近40句，私聊记忆8条，每人每日自动动态3条，每日后台AI预算80次。"
    )
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("生成风格")
    ParamSlider(settings, "ai_temperature", "AI 温度", 80, 0f..200f, step = 5f, tip = "AI说话的风格。数字越低越正经稳重，越高越活泼发散。建议60-90之间调；当前值除以100就是实际使用的温度。")
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


    Text("高级功能：修改各场景的提示词模板。不了解 Prompt 时建议保持默认。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Button(
        onClick = onPromptEditor,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Blue400)
    ) {
        Text("编辑 Prompt 模板", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun ModeInfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(10.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(3.dp))
        Text(body, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
    }
    Spacer(Modifier.height(6.dp))
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
                    value = v.coerceAtMost(pairValue)
                } else {
                    value = v.coerceAtLeast(pairValue)
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
