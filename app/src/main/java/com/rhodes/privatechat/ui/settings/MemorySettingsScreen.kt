package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.TextSecondary
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@Composable
fun MemorySettingsScreen(
    onBack: () -> Unit,
    onManageMemories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings: SettingsRepository = koinInject()
    SaveableSettingsScaffold(
        title = "记忆设置",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { androidx.compose.material3.Icon(Icons.Default.Hub, null, tint = Primary) },
    ) {
        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        val tabs = listOf("总览", "生成", "注入", "隔离", "管理")
        ScrollableTabRow(selectedTabIndex = tabIndex, edgePadding = 0.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(title) })
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            when (tabIndex) {
                0 -> MemoryOverview(settings)
                1 -> MemoryGeneration(settings)
                2 -> MemoryInjection(settings)
                3 -> MemoryIsolation(settings)
                4 -> MemoryManagement(settings, onManageMemories)
            }
        }
    }
}

@Composable
private fun MemoryOverview(settings: SettingsRepository) {
            Text("记忆生成和记忆注入是分开的。关闭生成不会删除已有记忆；关闭注入只会阻止某个场景读取该来源。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.padding(4.dp))
            SettingsSectionTitle("总开关")
            SettingsSwitchCard("统一记忆系统", "允许生成、索引和召回 Memory V2 记忆。关闭后已有记忆保留，但不会参与对话。", settings.memoryV2Enabled) { settings.memoryV2Enabled = it }
            SettingsSectionTitle("当前策略")
            Text("生成开关决定是否创建新的 L1 和记忆向量；长期沉淀决定 L1 是否可合并为 L2/L3；注入开关决定每个场景可读取的来源。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.padding(4.dp))
            SettingsSwitchCard("摘要游标", "只处理上次提取后新增的消息", settings.summaryCursorEnabled) { settings.summaryCursorEnabled = it }
}

@Composable
private fun MemoryGeneration(settings: SettingsRepository) {
            var privateGenerationEnabled by remember { mutableStateOf(settings.privateMemoryGenerationEnabled) }
            var groupGenerationEnabled by remember { mutableStateOf(settings.groupMemoryGenerationEnabled) }
            SettingsSectionTitle("记忆生成")
            SettingsSwitchCard("私聊记忆", "从私聊中提取重要事件、偏好和约定", privateGenerationEnabled) { privateGenerationEnabled = it; settings.privateMemoryGenerationEnabled = it }
            SettingsSwitchCard("私聊滚动摘要", "用 LLM 总结近期私聊，保证对话连续性", settings.privateSummaryGenerationEnabled) { settings.privateSummaryGenerationEnabled = it }
            SettingsSwitchCard("私聊每日摘要", "用 LLM 生成私聊每日回顾", settings.privateDailySummaryGenerationEnabled) { settings.privateDailySummaryGenerationEnabled = it }
            SettingsParamSlider(settings, "private_memory_extraction_threshold", "私聊每多少条消息提取一次", 12, 3f..30f, "私聊新增消息达到数量后，AI 提取一次可检索记忆。", step = 1f, enabled = privateGenerationEnabled)
            SettingsSwitchCard("群聊记忆", "从群聊中提取公开话题和群内约定", groupGenerationEnabled) { groupGenerationEnabled = it; settings.groupMemoryGenerationEnabled = it }
            SettingsSwitchCard("群聊滚动摘要", "用 LLM 总结近期群聊，保证群聊连续性", settings.groupSummaryGenerationEnabled) { settings.groupSummaryGenerationEnabled = it }
            SettingsSwitchCard("群聊每日摘要", "用 LLM 生成群聊每日回顾", settings.groupDailySummaryGenerationEnabled) { settings.groupDailySummaryGenerationEnabled = it }
            SettingsParamSlider(settings, "group_memory_extraction_threshold", "群聊每多少条消息提取一次", 12, 3f..30f, "群聊新增消息达到数量后，AI 提取一次可检索记忆。", step = 1f, enabled = groupGenerationEnabled)
            SettingsSwitchCard("群聊记忆复制给成员", "仅复制高重要度的承诺、提醒、偏好、观点和自我认知；普通事件与情绪不会复制。", settings.groupMemoryCopyToMembersEnabled) { settings.groupMemoryCopyToMembersEnabled = it }
            SettingsSwitchCard("动态记忆", "将公开动态保存为可检索记忆", settings.momentMemoryGenerationEnabled) { settings.momentMemoryGenerationEnabled = it }
            SettingsSwitchCard("动态评论记忆", "将动态评论保存为可检索记忆", settings.momentCommentMemoryGenerationEnabled) { settings.momentCommentMemoryGenerationEnabled = it }
            SettingsSwitchCard("日记记忆", "将生成的日记保存为角色记忆", settings.diaryMemoryGenerationEnabled) { settings.diaryMemoryGenerationEnabled = it }
            SettingsSwitchCard("私聊长期沉淀", "允许私聊 L1 继续合并为 L2/L3", settings.privateMemoryPromotionEnabled) { settings.privateMemoryPromotionEnabled = it }
            SettingsSwitchCard("群聊长期沉淀", "允许群聊 L1 合并为 L2/L3。群聊会同时参考近期事实和少量长期背景。", settings.groupMemoryPromotionEnabled) { settings.groupMemoryPromotionEnabled = it }
            SettingsSwitchCard("动态长期沉淀", "允许动态记忆继续合并为 L2/L3", settings.momentMemoryPromotionEnabled) { settings.momentMemoryPromotionEnabled = it }
            SettingsSwitchCard("评论长期沉淀", "允许评论记忆继续合并为 L2/L3", settings.momentCommentMemoryPromotionEnabled) { settings.momentCommentMemoryPromotionEnabled = it }
            SettingsSwitchCard("日记长期沉淀", "允许日记记忆继续合并为 L2/L3", settings.diaryMemoryPromotionEnabled) { settings.diaryMemoryPromotionEnabled = it }
            SettingsParamSlider(settings, "memory_v2_promote_l1_threshold", "L1 合并为 L2 的阈值", 20, 5f..100f, "同一话题中，内容有效且重要度至少 20 的 L1 达到此条数后尝试合并。高重要度承诺和提醒可使用下方优先阈值。", step = 1f)
            SettingsParamSlider(settings, "memory_v2_promote_l2_threshold", "L2 合并为 L3 的阈值", 10, 3f..50f, "同一话题中，内容有效且重要度至少 20 的 L2 达到此条数后尝试合并；还需满足长期稳定性判断。", step = 1f)
            SettingsParamSlider(settings, "memory_v2_important_promotion_threshold", "重要承诺/提醒优先沉淀阈值", 2, 2f..20f, "高重要度、承诺或关怀提醒达到此条数后可优先尝试沉淀，可能早于上述常规阈值。", step = 1f)
}

@Composable
private fun MemoryInjection(settings: SettingsRepository) {
            SettingsSectionTitle("私聊允许读取")
            InjectionSwitch(settings, "private_chat", "PRIVATE_CHAT", "私聊记忆")
            InjectionSwitch(settings, "private_chat", "GROUP_CHAT", "群聊记忆")
            InjectionSwitch(settings, "private_chat", "MOMENT", "动态")
            InjectionSwitch(settings, "private_chat", "MOMENT_COMMENT", "动态评论")
            SettingsSwitchCard("公开动态与评论总检索", "关闭后私聊不检索全局公开动态和评论", settings.globalPublicMemoryEnabled) { settings.globalPublicMemoryEnabled = it }
            InjectionSwitch(settings, "private_chat", "DIARY", "日记")
            InjectionSwitch(settings, "private_chat", "MANUAL_MEMORY", "手动记忆")
            InjectionSwitch(settings, "private_chat", "RELATIONSHIP", "关系网传递")
            SettingsParamSlider(settings, "private_group_context_count", "私聊最多读取几个相关群摘要", 2, 0f..10f, "控制私聊自动参考的相关群滚动摘要数量；不是群向量记忆数量。", step = 1f)

            SettingsSectionTitle("群聊允许读取")
            InjectionSwitch(settings, "group_chat", "GROUP_CHAT", "本群向量记忆")
            InjectionSwitch(settings, "group_chat", "MOMENT", "动态")
            InjectionSwitch(settings, "group_chat", "MOMENT_COMMENT", "动态评论")
            InjectionSwitch(settings, "group_chat", "RELATIONSHIP", "成员关系提示")
            InjectionSwitch(settings, "group_chat", "MEMBER_PRIVATE_CHAT", "成员个人私聊记忆（仅点名时）")
            SettingsParamSlider(settings, "group_member_memory_count", "群聊按需读取成员记忆数", 2, 0f..2f, "仅当用户本轮明确点名成员时，最多读取该成员多少条私聊背景。", step = 1f)

            SettingsSectionTitle("动态允许读取")
            InjectionSwitch(settings, "moment", "PRIVATE_CHAT", "私聊记忆")
            InjectionSwitch(settings, "moment", "GROUP_CHAT", "群聊记忆")
            InjectionSwitch(settings, "moment", "MOMENT", "动态")
            InjectionSwitch(settings, "moment", "MOMENT_COMMENT", "动态评论")
            InjectionSwitch(settings, "moment", "DIARY", "日记")
            InjectionSwitch(settings, "moment", "MANUAL_MEMORY", "手动记忆")

            SettingsSectionTitle("评论允许读取")
            InjectionSwitch(settings, "comment", "PRIVATE_CHAT", "私聊记忆")
            InjectionSwitch(settings, "comment", "GROUP_CHAT", "群聊记忆")
            InjectionSwitch(settings, "comment", "MOMENT", "动态")
            InjectionSwitch(settings, "comment", "MOMENT_COMMENT", "动态评论")
            InjectionSwitch(settings, "comment", "DIARY", "日记")
            InjectionSwitch(settings, "comment", "MANUAL_MEMORY", "手动记忆")

            SettingsSectionTitle("日记允许读取")
            InjectionSwitch(settings, "diary", "PRIVATE_CHAT", "私聊记忆与昨天私聊")
            InjectionSwitch(settings, "diary", "GROUP_CHAT", "群聊记忆与群聊摘要")
            InjectionSwitch(settings, "diary", "MOMENT", "动态")
            InjectionSwitch(settings, "diary", "MOMENT_COMMENT", "动态评论")
            InjectionSwitch(settings, "diary", "DIARY", "历史日记")
            InjectionSwitch(settings, "diary", "MANUAL_MEMORY", "手动记忆")
            InjectionSwitch(settings, "diary", "RELATIONSHIP", "关系事件")
}

@Composable
private fun MemoryIsolation(settings: SettingsRepository) {
            var selectedMode by remember { mutableStateOf(settings.memoryRecallMode) }
            SettingsSectionTitle("检索策略")
            Text("快速减少各层候选数量，优先近期记忆；平衡使用下方候选上限；深度固定使用更大的候选池。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.padding(2.dp))
            Row(Modifier.fillMaxWidth()) {
                RecallModeButton("快速", "fast", selectedMode) { mode ->
                    settings.memoryRecallMode = mode
                    selectedMode = mode
                }
                Spacer(Modifier.padding(2.dp))
                RecallModeButton("平衡", "balanced", selectedMode) { mode ->
                    settings.memoryRecallMode = mode
                    selectedMode = mode
                }
                Spacer(Modifier.padding(2.dp))
                RecallModeButton("深度", "deep", selectedMode) { mode ->
                    settings.memoryRecallMode = mode
                    selectedMode = mode
                }
            }
            SettingsParamSlider(settings, "memory_recall_candidate_limit", "通用记忆候选上限", 300, 50f..1000f, "候选越多越容易找到旧记忆，但检索更慢。", step = 50f)
            SettingsSectionTitle("重新开始")
            Text("重新开始私聊或群聊后，新回复会过滤旧聊天历史、旧摘要和旧向量记忆。群聊会等待旧记忆清理完成后再生成第一条新回复。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.padding(4.dp))
            Text("手动记忆、公开动态和角色基础设定不属于某次会话，是否注入由“注入”页对应来源开关决定。", fontSize = 12.sp, color = TextSecondary)
}

@Composable
private fun MemoryManagement(settings: SettingsRepository, onManageMemories: () -> Unit) {
            val viewModel: MainViewModel = koinViewModel()
            val scope = rememberCoroutineScope()
            var health by remember { mutableStateOf<MainViewModel.MemoryIndexHealth?>(null) }
            var rebuilding by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf("") }
            LaunchedEffect(settings.memoryV2Enabled) { health = viewModel.getMemoryIndexHealth() }
            SettingsSectionTitle("管理")
            Text("关系网传递的开关位于“注入 > 私聊允许读取”，避免同一功能出现两个不同状态。", fontSize = 12.sp, color = TextSecondary)
            Spacer(Modifier.padding(4.dp))
            MemoryManagementButton(onManageMemories)
            Spacer(Modifier.padding(8.dp))
            SettingsSectionTitle("向量索引状态")
            val state = health
            Text(
                if (state == null) "正在读取索引状态…" else "有效记忆 ${state.eligible} 条 · 已验证 ${state.indexed} 条 · 待补建 ${state.pending} 条" + if (state.stale > 0) " · 失效引用 ${state.stale} 条" else "",
                fontSize = 12.sp, color = TextSecondary
            )
            Spacer(Modifier.padding(4.dp))
            Row {
                Button(enabled = !rebuilding, onClick = { scope.launch { health = viewModel.getMemoryIndexHealth() } }) { Text("刷新状态") }
                Spacer(Modifier.padding(4.dp))
                Button(enabled = settings.memoryV2Enabled && !rebuilding && (health?.pending ?: 0) > 0, onClick = {
                    scope.launch {
                        rebuilding = true
                        val rebuild = viewModel.rebuildPendingMemoryIndexes()
                        result = "补建完成：成功 ${rebuild.succeeded}，失败 ${rebuild.failed}" + if (rebuild.errors.isNotEmpty()) "。${rebuild.errors.joinToString("；")}" else ""
                        health = viewModel.getMemoryIndexHealth()
                        rebuilding = false
                    }
                }) { Text(if (rebuilding) "补建中" else "补建待索引") }
            }
            if (result.isNotBlank()) Text(result, fontSize = 12.sp, color = TextSecondary)
}

@Composable
private fun RecallModeButton(label: String, mode: String, selectedMode: String, onSelect: (String) -> Unit) {
    Button(
        onClick = { onSelect(mode) },
        enabled = selectedMode != mode,
    ) { Text(if (selectedMode == mode) "$label（当前）" else label, fontSize = 11.sp) }
}

@Composable
private fun InjectionSwitch(settings: SettingsRepository, surface: String, source: String, title: String) {
    SettingsSwitchCard(title, "控制该来源是否进入${surfaceLabel(surface)}上下文", settings.isMemoryInjectionAllowed(surface, source)) {
        settings.setMemoryInjectionAllowed(surface, source, it)
        when (surface to source) {
            "private_chat" to "PRIVATE_CHAT" -> settings.privateRecallPrivateChatMemory = it
            "private_chat" to "GROUP_CHAT" -> settings.privateRecallGroupChatMemory = it
            "private_chat" to "MOMENT" -> settings.privateRecallMomentMemory = it
            "private_chat" to "MOMENT_COMMENT" -> settings.privateRecallMomentCommentMemory = it
            "private_chat" to "DIARY" -> settings.privateRecallDiaryMemory = it
            "private_chat" to "MANUAL_MEMORY" -> settings.privateRecallManualMemory = it
            "private_chat" to "RELATIONSHIP" -> settings.privateRecallRelationshipMemory = it
        }
    }
}

private fun surfaceLabel(surface: String): String = when (surface) {
    "private_chat" -> "私聊"
    "group_chat" -> "群聊"
    "moment" -> "动态"
    "comment" -> "评论"
    "diary" -> "日记"
    else -> surface
}

@Composable
private fun MemoryManagementButton(onClick: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text("打开记忆管理")
    }
    Spacer(Modifier.padding(4.dp))
}
