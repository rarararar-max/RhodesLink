package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
fun WorldSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    var autoEnabled by remember { mutableStateOf(settings.autoAiEnabled) }
    var worldSchedulerEnabled by remember { mutableStateOf(settings.worldSchedulerEnabled) }
    var tabIndex by remember { mutableIntStateOf(0) }
    val worldEnabled = autoEnabled && worldSchedulerEnabled
    val tabs = listOf("总览", "自动行为", "状态库", "事件联动", "动态评论", "群聊私聊")
    SaveableSettingsScaffold(
        title = "自动与世界",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = Primary) }
    ) {
        ScrollableTabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400, edgePadding = 0.dp) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding().navigationBarsPadding()) {
            when (tabIndex) {
                0 -> WorldGeneralTab(settings, autoEnabled, worldSchedulerEnabled, onAuto = { autoEnabled = it }, onWorld = { worldSchedulerEnabled = it })
                1 -> WorldAutoBehaviorTab(settings, autoEnabled, worldEnabled)
                2 -> WorldStatusPoolTab(settings)
                3 -> WorldEventTab(settings, worldEnabled)
                4 -> WorldMomentCommentTab(settings, autoEnabled, worldEnabled)
                5 -> WorldGroupPrivateTab(settings, worldEnabled)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WorldStatusPoolTab(settings: SettingsRepository) {
    var locations by remember { mutableStateOf(settings.statusLocationPool) }
    var activities by remember { mutableStateOf(settings.statusActivityPool) }
    var emotions by remember { mutableStateOf(settings.statusEmotionPool) }

    InfoCard("状态库说明", "后台自动状态刷新会从这里随机选择位置、状态和心情。每行一个词。清空某一项时会自动使用默认库。私聊中 AI 自己返回的位置/状态/心情暂时不强制受这个库限制。")
    StatusPoolField("位置库", locations, "例如：宿舍\n训练室\n食堂", onChange = { locations = it; settings.statusLocationPool = it })
    StatusPoolField("状态库", activities, "例如：休息\n训练\n阅读", onChange = { activities = it; settings.statusActivityPool = it })
    StatusPoolField("心情库", emotions, "例如：平静\n开心\n专注", onChange = { emotions = it; settings.statusEmotionPool = it })
    TextButton(onClick = {
        locations = settings.defaultStatusLocations
        activities = settings.defaultStatusActivities
        emotions = settings.defaultStatusEmotions
        settings.statusLocationPool = locations
        settings.statusActivityPool = activities
        settings.statusEmotionPool = emotions
    }) {
        Text("恢复默认状态库", color = Primary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusPoolField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().height(130.dp),
        placeholder = { Text(placeholder, fontSize = 12.sp, color = TextTertiary) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextPrimary)
    )
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun WorldGeneralTab(settings: SettingsRepository, autoEnabled: Boolean, worldSchedulerEnabled: Boolean, onAuto: (Boolean) -> Unit, onWorld: (Boolean) -> Unit) {
    InfoCard("自动世界怎么运作？", "先打开“后台自动 AI”，角色才会自己发动态、评论、写日记或主动聊天。打开“大世界运行”后，这些事情还会互相影响：动态可能引发评论，评论可能唤起群聊，群聊话题又可能成为新的事件。")
    SettingsSectionTitle("总开关")
    SettingsSwitchCard("后台自动 AI", "总开关。关闭后，角色不会自己发动态、评论、写日记、主动私聊或自动群聊；你手动聊天和手动生成不受影响。", autoEnabled) { onAuto(it); settings.autoAiEnabled = it }
    SettingsSwitchCard("大世界运行", "控制“事件带动事件”。关闭后，角色仍可按基础规则自动发内容，但动态、评论、群聊、状态之间不会互相触发。", worldSchedulerEnabled, enabled = autoEnabled) { onWorld(it); settings.worldSchedulerEnabled = it }
    SettingsParamSlider(settings, "daily_auto_ai_limit", "每日后台 AI 预算", 40, 0f..500f, "所有后台自动行为每天最多消耗多少次 AI 调用。设 0 等同于关闭后台自动 AI；用户主动聊天、催发动态、偷看/重新生成日记不计入。建议 30-50。", step = 5f, enabled = autoEnabled)
    SettingsParamSlider(settings, "tick_auto_ai_limit", "单轮后台 AI 预算", 3, 0f..50f, "每 15 分钟周期内最多触发几次后台 AI，防止动态、评论、私聊连环触发。建议 3-5。", step = 1f, enabled = autoEnabled)
}

@Composable
private fun WorldAutoBehaviorTab(settings: SettingsRepository, autoEnabled: Boolean, worldEnabled: Boolean) {
    SettingsSectionTitle("基础自动行为")
    SettingsSwitchCard("每日自动动态", "开启后，每天按每人每日自动动态上限为有动态权限的角色补动态。不依赖大世界事件。", settings.dailyAutoMomentEnabled, enabled = autoEnabled) { settings.dailyAutoMomentEnabled = it }
    SettingsSwitchCard("空闲主动私聊", "开启后，角色在冷却时间满足时可能主动找你聊天。不依赖大世界事件，但需要角色主动私聊权限。", settings.idleProactiveChatEnabled, enabled = autoEnabled) { settings.idleProactiveChatEnabled = it }
    SettingsSwitchCard("自动状态变化", "每 15 分钟刷新干员位置、活动和情绪。开启大世界后，这些状态变化也能成为事件种子。", settings.autoStatusRefresh, enabled = autoEnabled) { settings.autoStatusRefresh = it }
    SettingsSwitchCard("自动日记", "开启后，系统会在后台为部分活跃干员写昨日记事；人数和字数请到“日记与派遣”调整。", settings.autoDiaryEnabled, enabled = worldEnabled) { settings.autoDiaryEnabled = it }
    InfoCard("权限入口", "角色是否允许主动私聊/自动发动态，请在权限管理或角色编辑页设置。群聊分为空闲自动聊天和大世界事件唤起，请在群聊页或权限管理页设置。日记人数、字数和派遣故事长度在“日记与派遣”调整。")
}

@Composable
private fun WorldEventTab(settings: SettingsRepository, worldEnabled: Boolean) {
    SettingsSectionTitle("大世界触发")
    SettingsSwitchCard("事件触发动态", "有评论、群聊话题或状态变化时，符合动态权限的干员可能围绕事件发动态。", settings.autoMomentEnabled, enabled = worldEnabled) { settings.autoMomentEnabled = it }
    SettingsSwitchCard("事件唤起群聊", "有动态或评论话题时，只唤起已开启“大世界事件唤起”的群聊，按设定轮数聊完就停。", settings.worldAutoGroupEnabled, enabled = worldEnabled) { settings.worldAutoGroupEnabled = it }
    SettingsSwitchCard("事件触发主动私聊", "评论动态、被提及等事件可能让相关干员主动私聊你。仍受角色权限、冷却和预算限制。", settings.worldProactiveChatEnabled, enabled = worldEnabled) { settings.worldProactiveChatEnabled = it }
    SettingsParamSlider(settings, "daily_world_trigger_limit", "每日世界触发上限", 20, 0f..200f, "每天最多执行多少次大世界联动动作，例如事件触发动态、唤起群聊、触发私聊。建议 15-30。", step = 1f, enabled = worldEnabled)
    SettingsParamSlider(settings, "tick_world_trigger_limit", "单轮世界触发上限", 2, 0f..20f, "每 15 分钟周期最多执行多少次大世界联动动作。建议 1-3，越高越热闹也越耗额度。", step = 1f, enabled = worldEnabled)
    SettingsParamSlider(settings, "daily_world_event_limit", "每日事件动态上限", 30, 0f..200f, "每天最多由世界事件触发多少条动态。普通评论、聊天和日记事件不受这个参数直接限制。建议 20-50。", step = 1f, enabled = worldEnabled)
    SettingsParamSlider(settings, "event_context_count", "事件上下文数量", 5, 0f..20f, "每次聊天、动态或日记最多参考几条未处理的新鲜事。建议 3-8。", step = 1f, enabled = worldEnabled)
}

@Composable
private fun WorldMomentCommentTab(settings: SettingsRepository, autoEnabled: Boolean, worldEnabled: Boolean) {
    SettingsSectionTitle("动态参数")
    SettingsParamSlider(settings, "daily_moment_target", "每人每日自动动态上限", 2, 0f..10f, "每日自动动态和事件触发动态共享这个上限，避免后台刷屏。0=不自动发动态；用户主动点击催发动态不受此限制。建议 1-3。", step = 1f, enabled = autoEnabled)
    SettingsParamSlider(settings, "moment_trigger_strength", "事件触发动态概率", 50, 0f..100f, "有世界事件时触发自动动态的概率。建议 40-60。", step = 5f, enabled = worldEnabled)
    SettingsParamSlider(settings, "moment_min_chars", "动态最少字数", 50, 20f..300f, "每条动态最少写几个字。建议 20-50。", step = 5f, pairKey = "moment_max_chars", isMinSide = true, enabled = autoEnabled)
    SettingsParamSlider(settings, "moment_max_chars", "动态最多字数", 200, 80f..500f, "每条动态最多写几个字。建议 150-250。", step = 5f, pairKey = "moment_min_chars", isMinSide = false, enabled = autoEnabled)
    SettingsParamSlider(settings, "moment_anchor_count", "动态参考记忆", 3, 0f..10f, "生成动态时最多参考几条公开记忆。建议 3-5。", step = 1f, enabled = autoEnabled)
    SettingsParamSlider(settings, "moment_recent_post_count", "近期动态参考", 3, 0f..10f, "参考该干员最近几条动态，避免连续重复话题。建议 2-3。", step = 1f, enabled = autoEnabled)
    SettingsParamSlider(settings, "moment_user_related_rate", "用户相关概率", 20, 0f..100f, "动态中自然提到用户的概率。不会要求模型泄露私聊。建议 10-30。", step = 5f, enabled = autoEnabled)
    SettingsParamSlider(settings, "moment_user_post_observer_count", "用户动态随机观察者", 3, 0f..10f, "用户发动态时，除被@干员外，随机多少名干员会记住这条动态。建议 2-3。", step = 1f, enabled = autoEnabled)
    SettingsSectionTitle("评论参数")
    SettingsParamSlider(settings, "comment_min_chars", "评论最少字数", 10, 5f..30f, "干员评论动态最少写几个字。建议 10-15。", step = 1f, pairKey = "comment_max_chars", isMinSide = true, enabled = autoEnabled)
    SettingsParamSlider(settings, "comment_max_chars", "评论最多字数", 40, 10f..100f, "干员评论动态最多写几个字。建议 30-60。", step = 5f, pairKey = "comment_min_chars", isMinSide = false, enabled = autoEnabled)
    SettingsParamSlider(settings, "comment_context_count", "评论上下文条数", 5, 0f..20f, "AI 回复评论时最多回看几条前文。建议 3-5。", step = 1f, enabled = autoEnabled)
    SettingsParamSlider(settings, "comment_memory_count", "评论参考记忆", 2, 0f..10f, "AI 回复评论时最多参考几条相关公开记忆。建议 1-2。", step = 1f, enabled = autoEnabled)
    SettingsParamSlider(settings, "comment_bystander_min", "围观评论最少人数", 1, 0f..10f, "你评论动态后，最少几个干员参与围观回复。建议 1-2。", step = 1f, pairKey = "comment_bystander_max", isMinSide = true, enabled = autoEnabled)
    SettingsParamSlider(settings, "comment_bystander_max", "围观评论最多人数", 3, 0f..10f, "你评论动态后，最多几个干员参与围观回复。受后台预算限制。建议 2-3。", step = 1f, pairKey = "comment_bystander_min", isMinSide = false, enabled = autoEnabled)
}

@Composable
private fun WorldGroupPrivateTab(settings: SettingsRepository, worldEnabled: Boolean) {
    SettingsSectionTitle("群聊触发参数")
    SettingsParamSlider(settings, "group_trigger_strength", "事件唤起群聊概率", 50, 0f..100f, "有动态或评论话题时唤起群聊的概率。还需要群自己的“大世界事件唤起”开关。建议 40-60。", step = 5f, enabled = worldEnabled)
    SettingsParamSlider(settings, "event_group_rounds", "事件群聊轮数", 2, 1f..10f, "群被世界事件唤起后，围绕事件连续聊几轮，聊完停止。建议 1-3。", step = 1f, enabled = worldEnabled)
    SettingsParamSlider(settings, "event_group_cooldown_minutes", "同群事件冷却(分钟)", 45, 1f..240f, "同一个群被大世界事件唤起后的冷却时间。建议 30-60 分钟。", step = 5f, enabled = worldEnabled)
    SettingsParamSlider(settings, "event_max_groups_per_trigger", "每次最多唤起群数", 1, 1f..5f, "一次世界触发最多叫醒几个群。建议 1，避免全世界同时热闹。", step = 1f, enabled = worldEnabled)
    SettingsParamSlider(settings, "moment_to_group_trigger_rate", "评论唤起群聊概率", 40, 0f..100f, "你评论动态后，相关话题被群聊接住的概率。建议 30-50。", step = 5f, enabled = worldEnabled)
    SettingsSectionTitle("主动私聊")
    SettingsParamSlider(settings, "comment_to_private_trigger_rate", "评论触发私聊概率", 30, 0f..100f, "你评论动态后，动态作者可能主动私聊你的概率。建议 20-40。", step = 5f, enabled = worldEnabled)
    SettingsParamSlider(settings, "daily_proactive_limit", "每日事件私聊上限", 3, 0f..20f, "大世界事件每天最多触发多少次主动私聊。空闲主动私聊不计入这个数。建议 2-5。", step = 1f, enabled = worldEnabled)
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
    Spacer(Modifier.height(10.dp))
}
