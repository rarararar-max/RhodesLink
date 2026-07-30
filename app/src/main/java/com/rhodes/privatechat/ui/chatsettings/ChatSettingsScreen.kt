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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    onManageMemories: () -> Unit = {},
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
                3 -> MemoryTab(settings, onManageMemories)
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
    ParamSlider(settings, "dia_seg_min", "最少台词段数", 1, 1f..10f, "角色每次回复最少说几段话。建议1。太低（0）可能不出声，太高（超过3）角色为了凑段数会说一堆车轱辘话。", pairKey = "dia_seg_max", pairDefaultVal = 3, isMinSide = true)
    ParamSlider(settings, "dia_seg_max", "最多台词段数", 3, 1f..10f, "角色每次回复最多说几段话。建议2-3。太高（超过5）角色容易啰嗦，每个回复都像在写作文。", pairKey = "dia_seg_min", pairDefaultVal = 1, isMinSide = false)
    ParamSlider(settings, "dia_min", "台词最少字数", 10, 1f..1000f, "每段话最少写几个字。建议10-20。太低（低于5）角色答得很敷衍，太高（超过50）每句话都像在写小作文。", step = 5f, pairKey = "dia_max", pairDefaultVal = 300, isMinSide = true)
    ParamSlider(settings, "dia_max", "台词最多字数", 300, 1f..1000f, "每段话最多写几个字。建议200-300。太低（低于50）角色只能一问一答没法好好说话，太高（超过500）角色会变得非常啰嗦。", step = 5f, pairKey = "dia_min", pairDefaultVal = 10, isMinSide = false)
    Spacer(modifier = Modifier.height(12.dp))

    SectionTitle("旁白（线下/导演模式）")
    ParamSlider(settings, "nar_seg_min", "最少旁白段数", 1, 1f..10f, "线下和导演模式每轮至少保留一段旁白；此设置用于增加旁白数量。线上模式用不到这个。", pairKey = "nar_seg_max", pairDefaultVal = 3, isMinSide = true)
    ParamSlider(settings, "nar_seg_max", "最多旁白段数", 3, 1f..10f, "每次回复最多带几段环境描写。建议2-3。太高（超过5）旁白比台词还多，像在读剧本不像在聊天。", pairKey = "nar_seg_min", pairDefaultVal = 1, isMinSide = false)
    ParamSlider(settings, "nar_min", "旁白最少字数", 20, 0f..1000f, "每段环境描写最少写几个字。建议20-50。太低写不出画面感，太高（超过100）一段动作描写就占了大半篇幅。", step = 5f, pairKey = "nar_max", pairDefaultVal = 300, isMinSide = true)
    ParamSlider(settings, "nar_max", "旁白最多字数", 300, 1f..1000f, "每段环境描写最多写几个字。建议200-300。太高（超过400）旁白抢了台词的风头，而且每次回复花的钱也更多。", step = 5f, pairKey = "nar_min", pairDefaultVal = 20, isMinSide = false)
}

// ── Tab 1: 群聊 ──

@Composable
private fun GroupTab(settings: SettingsRepository) {
    Text("群聊参数调整的是每轮群聊的表现。想开启空闲自动聊天，需要到权限管理或群聊编辑页为具体群打开开关。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Spacer(Modifier.height(8.dp))
    SectionTitle("消息长度")
    ParamSlider(settings, "group_msg_min", "每条消息最小字数", 10, 5f..50f, "群聊里每人每次最少说几个字。建议10-20。太低（低于5）只说一两个字没内容，太高（超过30）每人说话都像在写小作文，群聊节奏拖得很慢。", pairKey = "group_msg_max", pairDefaultVal = 100, isMinSide = true)
    ParamSlider(settings, "group_msg_max", "每条消息最大字数", 100, 30f..200f, "群聊里每人每次最多说几个字。建议50-100。太低（低于30）说不完整一件事，太高（超过150）一个人刷一大段，其他人就冷场了。", step = 5f, pairKey = "group_msg_min", pairDefaultVal = 10, isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("发言频率")
    ParamSlider(settings, "group_speech_min", "每轮每人最少发言", 1, 1f..20f, "每轮群聊中，每位当前成员至少说几次。数值越高，一轮消息越多、等待时间和生成消耗也会增加。", pairKey = "group_speech_max", pairDefaultVal = 2, isMinSide = true)
    ParamSlider(settings, "group_speech_max", "每轮每人最多发言", 2, 1f..20f, "每轮群聊中，每位当前成员最多说几次。可按自己喜欢的群聊密度自由调整；成员越多、数值越高，消息会越多。", pairKey = "group_speech_min", pairDefaultVal = 1, isMinSide = false)

    Spacer(Modifier.height(12.dp)); SectionTitle("空闲自动聊天")
    ParamSlider(settings, "group_chat_min_interval", "空闲最小间隔(秒)", 60, 5f..600f, "上一轮自动群聊完整回复结束后，最快还要等待多久才会开始下一轮。修改后请点击页面顶部保存；建议60。太低（低于30）AI接得太快像在抢话，太高（超过300）冷场太久聊天接不上。", step = 5f, pairKey = "group_chat_max_interval", pairDefaultVal = 180, isMinSide = true)
    ParamSlider(settings, "group_chat_max_interval", "空闲最大间隔(秒)", 180, 30f..900f, "没人说话后，最晚等多久AI一定会接话。建议180-300。太低（低于60）AI总是急着说话，太高（超过600）冷场太久才有人开口。", step = 10f, pairKey = "group_chat_min_interval", pairDefaultVal = 60, isMinSide = false)
    ParamSlider(settings, "group_auto_max_rounds", "空闲连续轮数", 20, 1f..300f, "AI自己聊天最多连续聊多少轮后停下来。建议10-30。太低（低于5）刚聊起来就停了，太高（超过50）AI一直聊不停，刷屏严重。", step = 1f)

    Spacer(Modifier.height(12.dp)); SectionTitle("群聊旁白(线下/导演)")
    Text("只在线下和导演模式生效；线上模式固定不显示旁白。旁白用于动作、环境和气氛描写，段数或长度越高，群聊越像场景演绎，消息数量和生成消耗也会增加。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    ParamSlider(settings, "group_nar_seg_min", "最少旁白段数", 1, 0f..20f, "线下和导演模式每轮最少出现几段旁白。设为0时该模式可以没有旁白；线上模式始终不显示旁白。", pairKey = "group_nar_seg_max", pairDefaultVal = 1, isMinSide = true)
    ParamSlider(settings, "group_nar_seg_max", "最多旁白段数", 1, 0f..20f, "线下和导演模式每轮最多出现几段旁白。默认1段；调高后动作和场景描写会更多。", pairKey = "group_nar_seg_min", pairDefaultVal = 1, isMinSide = false)
    ParamSlider(settings, "group_nar_min", "旁白最小字数", 20, 0f..200f, "每段场景描写最少写几个字。建议20。太低（0）场景描写太短没画面感。", step = 5f, pairKey = "group_nar_max", pairDefaultVal = 100, isMinSide = true)
    ParamSlider(settings, "group_nar_max", "旁白最大字数", 100, 50f..300f, "每段场景描写最多写几个字。建议100。太高（超过200）群聊里大段旁白像在写小说而不是聊天。", step = 5f, pairKey = "group_nar_min", pairDefaultVal = 20, isMinSide = false)
}

// ── Tab 2: 统一记忆 ──

@Composable
private fun MemoryTab(settings: SettingsRepository, onManageMemories: () -> Unit) {
    ParamSlider(settings, "summary_threshold", "触发总结的聊天条数", 20, 3f..200f, "聊多少句话后，AI会总结前面的内容，方便以后记住。建议20-50。太低（低于10）聊几句就总结一次，既花钱又没必要；太高（超过100）AI记不住前面聊了什么，对话容易失忆。", step = 1f)
    ParamSlider(settings, "summary_retain", "保留最近原始消息", 5, 1f..50f, "总结时保留最近几条完整的聊天原文不压缩，保证AI能接住刚说过的话。建议3-5。太低（低于2）刚说完的话就被压缩了，AI接话接不准；太高（超过10）保留太多原文，总结效果变差。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "daily_intimacy_cap", "每日好感变化上限", 5, 1f..20f, "每个角色每天最多涨或掉多少好感。建议3-5。太低（低于2）关系推进非常慢，太高（超过10）聊几句好感就满了。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "history_messages", "每次回复最多回看几轮", 10, 0f..200f, "一轮指用户一次发言及其后完整的 AI 回复；群聊中一整批成员回复也只算一轮。默认10轮。设为0就是不限制，但仍受模型自身上下文上限影响。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    ParamSlider(settings, "clean_days", "普通记忆保留天数", 30, 0f..365f, "角色从聊天中记住的普通事件、近期感受和短期信息默认保留多久。设为0时普通记忆不会自动过期；重要偏好、禁忌和关系会按更长规则保留。不会删除聊天记录、动态、日记或派遣记录。", step = 5f)
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
        title = "自动形成记忆",
        subtitle = "让角色记住你们聊过的重要事情",
        tip = "开启后，角色会记住你们反复提到的重要事，比如约定、喜好和计划，以后聊天时能自然想起来。关闭后不再记新的内容；以前已经记住的内容会保留，可到下方“管理全部统一记忆”里删除。",
        checked = settings.memoryV2Enabled,
        onCheckedChange = { settings.memoryV2Enabled = it }
    )
    SettingsSwitchCard(
        title = "记录公开动态和评论",
        subtitle = "让公开动态和评论形成可检索记忆",
        tip = "开启后，新的公开动态和评论会正常保存、提取并向量化。关闭不会删除已有公开记忆；已有内容是否可在私聊中引用，由下方“私聊可引用的记忆来源”单独控制。",
        checked = settings.momentMemoryV2Enabled,
        onCheckedChange = { settings.momentMemoryV2Enabled = it }
    )
    ParamSlider(settings, "memory_v2_promote_l1_threshold", "短期记忆合并阈值", 20, 5f..100f, "同一话题的短期记忆积累到多少条后，合并成一条中期记忆。建议20。太低（低于10）频繁合并、花钱多；太高（超过50）记忆太零散，角色记不住重点。", step = 1f)
    ParamSlider(settings, "memory_v2_promote_l2_threshold", "中期记忆合并阈值", 10, 3f..50f, "同一话题的中期记忆积累到多少条后，合并成一条长期稳定记忆。建议10。太低（低于5）容易把一次聊天当成长期印象；太高（超过20）重要事情也沉淀不下来。", step = 1f)
    ParamSlider(settings, "memory_v2_important_promotion_threshold", "重要记忆快速沉淀次数", 2, 2f..10f, "明确承诺、重要提醒、高重要度偏好等同一件事重复出现几次后，可提前合并。建议2。调高更谨慎、更省额度；调低会更快记住，但也更容易把一时的话当成长久记忆。", step = 1f)
    ParamSlider(settings, "private_memory_extraction_threshold", "私聊记忆提取条数", 12, 3f..30f, "每积累多少条新消息后，提取一次可检索的记忆。这个和滚动摘要是两回事——摘要负责连续性，这个负责可搜索。建议12。太低（低于5）每条消息都提取，花钱多；太高（超过20）聊了很多才提取一次，中间的可能来不及记住。", step = 1f)
    ParamSlider(settings, "group_memory_extraction_threshold", "群聊记忆提取条数", 12, 3f..30f, "每积累多少条新群消息后，提取一次群聊记忆。群聊自身始终可使用这些记忆；是否允许私聊引用，由下方来源开关控制。建议12。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("角色知识与召回")
    var memoryReferenceStyle by remember { mutableStateOf(settings.personalMemoryReferenceStyle) }
    ChoiceSetting("共同经历引用", memoryReferenceStyle, listOf("restrained" to "克制", "natural" to "自然", "proactive" to "主动关联")) { memoryReferenceStyle = it; settings.personalMemoryReferenceStyle = it }
    Text(
        when (memoryReferenceStyle) {
            "restrained" -> "克制：角色只在很确定的场合才会提起共同经历，私聊内容几乎不会在公开场合出现。适合喜欢界限分明的用户。"
            "natural" -> "自然：角色像真人一样自然地引用共同经历，但会避免泄露明显隐私的内容。平衡推荐。"
            "proactive" -> "主动关联：角色会主动把当前话题和你们的共同经历联系起来，关联性很强但可能偶尔关联过头。"
            else -> ""
        },
        fontSize = 11.sp, color = TextSecondary
    )
    var memoryRecallMode by remember { mutableStateOf(settings.memoryRecallMode) }
    ChoiceSetting("记忆检索强度", memoryRecallMode, listOf("fast" to "省电优先", "balanced" to "均衡推荐", "deep" to "深度回忆")) { memoryRecallMode = it; settings.memoryRecallMode = it }
    Text(
        when (memoryRecallMode) {
            "fast" -> "省电优先：只搜索近 1 个月的重要记忆，匹配条件严格。省电省时间，但久远的事情容易想不起来。适合手机配置不高或追求省电的用户。"
            "balanced" -> "均衡推荐：不限时间范围，记忆和速度之间比较平衡，适合大多数日常使用场景。"
            "deep" -> "深度回忆：不限时间范围、候选多、匹配宽松。更容易想起很久以前的细节，但耗电更多、响应稍慢。适合希望角色记性很好的用户。"
            else -> ""
        },
        fontSize = 11.sp, color = TextSecondary
    )
    Spacer(modifier = Modifier.height(8.dp))
    ParamSlider(settings, "memory_recall_candidate_limit", "通用记忆候选上限", 300, 50f..1000f, "公开记忆等通用搜索每次最多比对多少条。建议300。太低（低于100）容易漏掉相关记忆，太高（超过500）搜索变慢、耗电增加。只在均衡模式下生效；私聊共同经历按来源分别分配候选。", step = 50f)
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("私聊可引用的记忆来源")
    Text("关闭后，相关记忆仍会保存、更新和用于原场景，但不会参与私聊的智能记忆检索或外部上下文。", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    SettingsSwitchCard("私聊共同经历", "允许检索当前角色的私聊智能记忆", "不影响最近消息、短期摘要和长期印象；关闭后仅不进行私聊来源的向量记忆召回。", settings.privateRecallPrivateChatMemory) { settings.privateRecallPrivateChatMemory = it }
    var privateRecallGroupChatMemory by remember(settings.privateRecallGroupChatMemory) { mutableStateOf(settings.privateRecallGroupChatMemory) }
    SettingsSwitchCard("所在群聊", "允许群聊记忆和近期群聊摘要进入私聊", "关闭后，群聊内容不会参与私聊向量检索，也不会自动注入群聊近况。用户在私聊中主动提到群聊时，本版本同样不会回读群聊历史。", privateRecallGroupChatMemory) {
        privateRecallGroupChatMemory = it
        settings.privateRecallGroupChatMemory = it
    }
    if (privateRecallGroupChatMemory) {
        ParamSlider(settings, "private_group_context_count", "群聊回顾数量", 2, 0f..10f, "私聊最多自动回顾几个相关群的近期摘要。不影响群聊记忆的向量召回数量。", step = 1f)
    }
    SettingsSwitchCard("公开动态", "允许相关公开动态进入私聊", "关闭后，动态仍会保存和向量化，但不会参与私聊记忆检索。", settings.privateRecallMomentMemory) { settings.privateRecallMomentMemory = it }
    SettingsSwitchCard("动态评论", "允许相关公开评论进入私聊", "关闭后，评论仍会保存和向量化，但不会参与私聊记忆检索。", settings.privateRecallMomentCommentMemory) { settings.privateRecallMomentCommentMemory = it }
    SettingsSwitchCard("关系网转述", "允许其他角色的私聊记忆经关系网转述", "关闭后，不再根据角色之间的亲密度读取其他角色的私聊记忆。", settings.privateRecallRelationshipMemory) { settings.privateRecallRelationshipMemory = it }
    SettingsSwitchCard("日记", "允许角色日记进入私聊", "关闭后，日记仍会保存和向量化，但不会参与私聊记忆检索。", settings.privateRecallDiaryMemory) { settings.privateRecallDiaryMemory = it }
    SettingsSwitchCard("手动记忆", "允许已保存的手动记忆进入私聊", "关闭后，仍可创建和编辑手动记忆，但它们不会参与私聊记忆检索。", settings.privateRecallManualMemory) { settings.privateRecallManualMemory = it }
    Spacer(modifier = Modifier.height(8.dp))
    ParamSlider(settings, "group_member_memory_count", "群成员按需回忆数量", 2, 0f..2f, "群聊中当有人提到某个成员名字时，最多为该成员查几条个人相关记忆。建议1-2。普通闲聊不会查个人记忆，只靠群摘要。", step = 1f)
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("记忆管理")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("管理全部统一记忆", fontSize = 13.sp, color = TextPrimary)
            Text("查看角色、群和公开动态资料；支持筛选、删除和索引重建。", fontSize = 11.sp, color = TextSecondary)
        }
        TextButton(onClick = onManageMemories) { Text("打开") }
    }
}

@Composable
private fun ChoiceSetting(title: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, color = TextPrimary)
        Box {
            TextButton(onClick = { expanded = true }) { Text(options.firstOrNull { it.first == selected }?.second ?: selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); expanded = false }) }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsSwitchCard(title: String, subtitle: String, tip: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    var value by remember(checked) { mutableStateOf(checked) }
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
    Text("当前：${modeLabel(contextMode)}。选择预设会批量调整历史回看、记忆提取、自动动态和主动联系密度；不会改变“私聊可引用的记忆来源”开关和你自己改过的角色说话规则。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeButton("省钱", contextMode == "economy", Modifier.weight(1f)) { settings.applyContextMode("economy"); contextMode = "economy" }
        ModeButton("标准", contextMode == "standard", Modifier.weight(1f)) { settings.applyContextMode("standard"); contextMode = "standard" }
        ModeButton("完整", contextMode == "full", Modifier.weight(1f)) { settings.applyContextMode("full"); contextMode = "full" }
    }
    Spacer(modifier = Modifier.height(8.dp))
    ModeInfoCard(
        "省钱模式",
        "适合想省着用、主要手动的用户。少回看历史、少提取记忆；每个角色每天最多1条自动动态，最多1位角色主动联系。"
    )
    ModeInfoCard(
        "标准模式（推荐）",
        "平衡聊天连续性、记忆和自动内容，适合大多数新用户。每个角色每天最多1条自动动态，最多3位角色主动联系。"
    )
    ModeInfoCard(
        "完整模式",
        "更容易记住过去互动，角色联动和自动内容更活跃。每个角色每天最多3条自动动态，最多5位角色主动联系，消耗也更高。"
    )
    Spacer(modifier = Modifier.height(12.dp))
    SectionTitle("生成风格")
    ParamSlider(settings, "ai_temperature", "创意程度", 80, 0f..200f, step = 5f, tip = "角色说话风格。建议60-90。太低（低于30）说话非常死板重复，像机器人；太高（超过120）容易跑偏，说话前言不搭后语。当前值÷100就是实际使用的数值。")
    Spacer(modifier = Modifier.height(12.dp))
    var dualModel by remember { mutableStateOf(settings.dualModel) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("私聊连续性优化", fontSize = 13.sp, color = TextPrimary)
                HelpButton("回复前会先整理角色当前情绪、场景和你的表达意图，再生成正式回复。能让私聊更自然地承接上下文；会略微增加等待时间和模型消耗。")
            }
            Text("关闭后直接根据当前对话生成回复，响应更快", fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = dualModel, onCheckedChange = {
            dualModel = it
            settings.dualModel = it
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(6.dp))

    var groupTurnPlannerEnabled by remember { mutableStateOf(settings.groupTurnPlannerEnabled) }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Card).padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("群聊连续性优化", fontSize = 13.sp, color = TextPrimary)
                HelpButton("回复前会先规划群成员各自适合如何回应，再生成正式群聊内容。能减少成员重复附和和话题跑偏；会略微增加等待时间和模型消耗。")
            }
            Text("关闭后直接生成群成员回复，响应更快", fontSize = 11.sp, color = TextSecondary)
        }
        Switch(checked = groupTurnPlannerEnabled, onCheckedChange = {
            groupTurnPlannerEnabled = it
            settings.groupTurnPlannerEnabled = it
        }, colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
    Spacer(modifier = Modifier.height(6.dp))


    Text("高级功能：修改各场景的角色说话规则。不了解的话建议保持默认。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
    Button(
        onClick = onPromptEditor,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(containerColor = Blue400)
    ) {
        Text("编辑角色说话规则", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
private fun ParamSlider(settings: SettingsRepository, key: String, label: String, defaultVal: Int, range: ClosedFloatingPointRange<Float>, tip: String, step: Float = 1f, pairKey: String? = null, pairDefaultVal: Int = defaultVal, isMinSide: Boolean = true) {
    var value by remember { mutableFloatStateOf(settings.getInt(key, defaultVal).toFloat().coerceIn(range)) }
    var pairValue by remember { mutableFloatStateOf(if (pairKey != null) settings.getInt(pairKey, pairDefaultVal).toFloat() else 0f) }
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
                pairValue = settings.getInt(pairKey, pairDefaultVal).toFloat()
                if (isMinSide) {
                    value = v.coerceAtMost(pairValue)
                } else {
                    value = v.coerceAtLeast(pairValue)
                }
            } else { value = v }
        }, onValueChangeFinished = {
            val oldValue = settings.getInt(key, defaultVal)
            val oldPairValue = if (pairKey != null) settings.getInt(pairKey, pairDefaultVal) else null
            settings.putInt(key, value.toInt())
            DebugLogger.log("Settings/Param", "参数调整: $label($key) $oldValue -> ${value.toInt()}")
            if (pairKey != null) {
                settings.putInt(pairKey, pairValue.toInt())
                DebugLogger.log("Settings/Param", "联动参数: $pairKey ${oldPairValue ?: 0} -> ${pairValue.toInt()}")
            }
        }, valueRange = range, steps = (((range.endInclusive - range.start) / step).toInt() - 1).coerceAtLeast(0), colors = SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400))
    }
    Spacer(Modifier.height(4.dp))
}
