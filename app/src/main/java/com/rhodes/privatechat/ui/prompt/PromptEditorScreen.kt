package com.rhodes.privatechat.ui.prompt

import android.widget.Toast
import com.rhodes.privatechat.data.PromptPlaceholderRegistry
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PromptEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("私聊", "群聊", "动态", "评论", "日记", "派遣", "说明")
    val tabKeys = listOf("private", "group", "moment", "moment_comment", "diary", "dispatch", "help")
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val editorBringIntoViewRequester = remember { BringIntoViewRequester() }
    val editorScope = rememberCoroutineScope()

    val privModeLabels = listOf("线上模式", "线下模式", "导演模式", "主动消息")
    val privModes = listOf("online", "offline", "director", "proactive")
    val grpModeLabels = listOf("线上模式", "线下模式", "导演模式", "自动群聊")
    val grpModes = listOf("online", "offline", "director", "auto")
    val dispatchModeLabels = listOf("开局", "过程", "结局")
    val dispatchModes = listOf("start", "progress", "ending")

    var privModeIdx by remember { mutableIntStateOf(0) }
    var grpModeIdx by remember { mutableIntStateOf(0) }
    var dispatchModeIdx by remember { mutableIntStateOf(0) }
    var moduleIdx by remember { mutableIntStateOf(0) }

    val textMap = remember { mutableStateMapOf<String, TextFieldValue>() }
    val dirtyKeys = remember { mutableStateListOf<String>() }
    val resetKeys = remember { mutableStateListOf<String>() }
    var showExitDialog by remember { mutableStateOf(false) }

    fun loadTemplate(type: String, mode: String) = viewModel.getPromptTemplate(type, mode)

    fun currentModule(): String = if (tabIndex == 0 || tabIndex == 1) {
        listOf("role", "protocol", "runtime")[moduleIdx]
    } else "role"

    fun currentKey(): String = when (tabIndex) {
        0 -> "private:${privModes[privModeIdx]}:${currentModule()}"
        1 -> "group:${grpModes[grpModeIdx]}:${currentModule()}"
        2 -> "moment:"
        3 -> "moment_comment:"
        4 -> "diary:"
        5 -> "dispatch:${dispatchModes[dispatchModeIdx]}"
        else -> "help:"
    }

    fun currentType(): String = tabKeys[tabIndex]

    fun currentMode(): String = when (tabIndex) {
        0 -> privModes[privModeIdx]
        1 -> grpModes[grpModeIdx]
        5 -> dispatchModes[dispatchModeIdx]
        else -> ""
    }

    fun loadCurrentModule(): String = when (currentModule()) {
        "protocol" -> viewModel.getPromptModule("protocol", currentType(), currentMode())
        "runtime" -> viewModel.getPromptModule("runtime", currentType(), currentMode())
        else -> loadTemplate(currentType(), currentMode())
    }

    fun criticalPlaceholders(type: String, mode: String): Set<String> = when (type) {
        "private" -> if (mode == "proactive") {
            setOf("OPERATOR_PERSONA", "PROACTIVE_CONTEXT_MODE", "PROACTIVE_TRIGGER_CONTEXT")
        } else {
            setOf("OPERATOR_PERSONA")
        }
        "group" -> setOf("MEMBER_NAMES", "MEMBER_PROFILES")
        "moment_comment" -> setOf("POST_CONTENT", "COMMENTER_PERSONA")
        "diary" -> setOf("OPERATOR_PERSONA")
        else -> emptySet()
    }

    val key = currentKey()
    var textFieldValue by remember(key) {
        mutableStateOf(
            if (tabIndex < 6) {
                textMap[key] ?: TextFieldValue(loadCurrentModule())
            } else {
                TextFieldValue("")
            }
        )
    }

    val saveCurrent: () -> Boolean = saveCurrent@{
        if (tabIndex < 6 && currentKey() in dirtyKeys) {
            textMap[currentKey()] = textFieldValue
            if (currentKey() in resetKeys) {
                if (currentModule() == "role") viewModel.resetPromptTemplate(currentType(), currentMode())
                else viewModel.resetPromptModule(currentModule(), currentType(), currentMode())
                resetKeys.remove(currentKey())
                dirtyKeys.remove(currentKey())
                return@saveCurrent true
            }
            val result = runCatching {
                if (currentModule() == "role") viewModel.savePromptTemplate(currentType(), currentMode(), textFieldValue.text)
                else viewModel.savePromptModule(currentModule(), currentType(), currentMode(), textFieldValue.text)
            }
            if (result.isFailure) {
                Toast.makeText(context, "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                return@saveCurrent false
            }
            val warnings = result.getOrThrow()
            dirtyKeys.remove(currentKey())
            if (warnings.isNotEmpty()) {
                android.widget.Toast.makeText(context, warnings.joinToString("\n"), android.widget.Toast.LENGTH_LONG).show()
            }
        }
        true
    }
    val saveAllEdited: () -> Boolean = saveAllEdited@{
        if (tabIndex < 6) textMap[currentKey()] = textFieldValue
        val editedTemplates = textMap.filterKeys { it in dirtyKeys }.toMap()
        val validationErrors = editedTemplates.flatMap { (templateKey, value) ->
            val parts = templateKey.split(":")
            if (parts.getOrNull(2) == "role") viewModel.validatePromptTemplate(parts[0], parts.getOrNull(1).orEmpty(), value.text)
                .map { "$templateKey：$it" }
            else emptyList()
        }
        if (validationErrors.isNotEmpty()) {
            Toast.makeText(context, validationErrors.joinToString("\n"), Toast.LENGTH_LONG).show()
            return@saveAllEdited false
        }
        val warnings = mutableListOf<String>()
        for ((templateKey, value) in editedTemplates) {
            val parts = templateKey.split(":")
            val type = parts[0]
            val mode = parts.getOrNull(1).orEmpty()
            val module = parts.getOrNull(2).orEmpty().ifBlank { "role" }
            val result = runCatching {
                if (templateKey in resetKeys) {
                    if (module == "role") viewModel.resetPromptTemplate(type, mode)
                    else viewModel.resetPromptModule(module, type, mode)
                    emptyList()
                } else if (module == "role") viewModel.savePromptTemplate(type, mode, value.text)
                else viewModel.savePromptModule(module, type, mode, value.text)
            }
            if (result.isFailure) {
                Toast.makeText(context, "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                return@saveAllEdited false
            }
            warnings += result.getOrThrow()
        }
        dirtyKeys.clear()
        resetKeys.clear()
        if (warnings.isNotEmpty()) {
            android.widget.Toast.makeText(context, warnings.distinct().joinToString("\n"), android.widget.Toast.LENGTH_LONG).show()
        }
        true
    }

    fun requestBack() {
        if (dirtyKeys.isEmpty()) onBack() else showExitDialog = true
    }

    BackHandler(onBack = ::requestBack)

    var showResetDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showStructureDialog by remember { mutableStateOf(false) }

    val allPlaceholders = listOf(
        "{{OPERATOR_NAME}}" to "干员名称",
        "{{OPERATOR_TITLE}}" to "干员身份/头衔",
        "{{OPERATOR_PERSONA}}" to "干员人设描述（privatePrompt）",
        "{{USER_NAME}}" to "用户昵称",
        "{{USER_GENDER}}" to "用户性别",
        "{{USER_BIO}}" to "用户简介",
        "{{USER_GENDER_BIO}}" to "用户性别+简介拼接",
        "{{CURRENT_TIME}}" to "当前北京时间",
        "{{PRIVATE_CONTINUITY_STATE}}" to "上一轮私聊的状态、心情、位置和本轮简述，用于自然承接当前对话",
        "{{HYPNOSIS}}" to "催眠指令（为空则不注入）",
        "{{TRANSITION_NOTICE}}" to "模式切换、继续说、重说等临时系统指令",
        "{{PROACTIVE_TRIGGER_TYPE}}" to "主动触发类型（idle）",
        "{{PROACTIVE_TRIGGER_CONTEXT}}" to "主动私聊上下文",
        "{{PROACTIVE_CURRENT_TIME}}" to "主动消息发送时的当前北京时间",
        "{{PROACTIVE_CONTEXT_MODE}}" to "主动消息模式（跨日问候/未回应问题/同日续聊/久未联系）",
        "{{PROACTIVE_TIME_RELATION}}" to "当前时间与上次互动的关系，优先于历史措辞",
        "{{PROACTIVE_IDLE_DURATION}}" to "距离上次互动的时长",
        "{{PROACTIVE_LAST_INTERACTION_TIME}}" to "上次互动的北京时间",
        "{{PROACTIVE_LAST_USER_MESSAGE}}" to "用户最后一条可见消息",
        "{{PROACTIVE_LAST_USER_TIME}}" to "用户最后发言时间",
        "{{PROACTIVE_LAST_AI_MESSAGE}}" to "干员最后一条可见消息",
        "{{PROACTIVE_LAST_AI_TIME}}" to "干员最后回复时间",
        "{{PROACTIVE_UNRESOLVED_TOPIC}}" to "明确未回应的问题；无则为无",
        "{{PROACTIVE_RECENT_HISTORY}}" to "最近带时间戳的聊天记录，仅用于核对事实",
        "{{OPERATOR_GENDER}}" to "干员性别设定",
        "{{COMMENTER_GENDER}}" to "评论者性别设定",
        "{{MEMORY_INJECTION}}" to "系统自动注入的记忆上下文（每日摘要/对话摘要/长期印象/锚点/共享记忆）",
        "{{MEMORY_V2_CONTEXT}}" to "当前角色可用的记忆与共同经历；无相关记忆时为空",
        "{{USER_CONTENT}}" to "用户本轮内容；私聊通常由应用单独附加",
        "{{MIND_READ}}" to "读心功能上下文；未启用时为空",
        "{{LONG_TERM_IMPRESSION}}" to "长期印象",
        "{{USER_PREFS}}" to "用户偏好与禁忌",
        "{{MEMORY_ANCHORS}}" to "当前场景挑选出的关键记忆锚点",
        "{{SOURCE_AWARE_MEMORIES}}" to "带来源说明的记忆上下文",
        "{{SOURCE_AWARE_RULES}}" to "不同场景下使用记忆来源的规则",
        "{{KNOWN_FROM_CONTEXT}}" to "来源感知记忆（等价于SOURCE_AWARE_MEMORIES）",
        "{{SHARED_MEMORIES}}" to "通过玩家自建关系共享来的公开记忆",
        "{{RELATION_CONTEXT}}" to "关系上下文（兼容占位符）",
        "{{RELATION_SHARED_MEMORIES}}" to "关系共享记忆（等价于SHARED_MEMORIES）",
        "{{RELATION_RULES}}" to "关系信息使用规则",
        "{{OPERATOR_USER_RELATION}}" to "当前干员与用户的关系（等价于USER_RELATION）",
        "{{USER_RELATION}}" to "当前干员与用户的关系描述",
        "{{DAILY_SUMMARY}}" to "最近每日摘要/私聊每日摘要",
        "{{SHORT_TERM_SUMMARY}}" to "当前私聊或群聊短期摘要",
        "{{GROUP_CONTEXT}}" to "该干员近期参与过的群聊回顾",
        "{{MODE_RULES}}" to "当前模式的对话规则（线上禁止旁白/线下多段叙事/导演模式）",
        "{{INJECTION}}" to "通用注入点（旧版兼容，请优先用具体占位符）",
        "{{GROUP_NAME}}" to "群聊名称",
        "{{GROUP_INJECTION}}" to "群聊记忆上下文（关系/私聊摘要/印象/每日摘要）",
        "{{RELATION_HINTS}}" to "群成员之间的玩家自建关系提示",
        "{{GROUP_RELATION_HINTS}}" to "群关系提示（等价于RELATION_HINTS）",
        "{{MEMBER_PROFILES}}" to "成员简介与人设（自动生成）",
        "{{MEMBER_PRIVATE_CONTEXT}}" to "仅当用户本轮明确点名该成员时注入的相关私聊背景；注入后作为群聊共享上下文使用",
        "{{GROUP_SUMMARY}}" to "群聊短期摘要",
        "{{GROUP_RULES}}" to "群聊自定义规则",
        "{{MEMBER_NAMES}}" to "群成员名单",
        "{{USER_OBSERVING}}" to "用户在场但未发言",
        "{{AUTO_REASON}}" to "自动触发原因",
        "{{AUTO_REASON_TEXT}}" to "自动触发说明",
        "{{GROUP_MODE_FORMAT}}" to "自动群聊格式提示",
        "{{GROUP_PLOT_SUMMARY}}" to "上一轮群聊剧情简述，用于承接当前主线、场景和未结束事项",
        "{{USER_MESSAGE}}" to "用户最新发言内容",
        "{{OUTPUT_FORMAT}}" to "当前运行时标签格式规范",
        "{{GROUP_NAR_SEG_MIN}}" to "群聊旁白段数下限",
        "{{GROUP_NAR_SEG_MAX}}" to "群聊旁白段数上限",
        "{{GROUP_NAR_MIN}}" to "群聊旁白单段字数下限",
        "{{GROUP_NAR_MAX}}" to "群聊旁白单段字数上限",
        "{{GROUP_MSG_MIN}}" to "群聊每条消息字数下限",
        "{{GROUP_MSG_MAX}}" to "群聊每条消息字数上限",
        "{{GROUP_SPEECH_MIN}}" to "群聊每人发言次数下限",
        "{{GROUP_SPEECH_MAX}}" to "群聊每人发言次数上限",
        "{{NAR_SEG_MIN}}" to "线下/导演旁白段数下限",
        "{{NAR_SEG_MAX}}" to "线下/导演旁白段数上限",
        "{{NAR_MIN}}" to "旁白单段字数下限",
        "{{NAR_MAX}}" to "旁白单段字数上限",
        "{{DIA_SEG_MIN}}" to "对话段数下限",
        "{{DIA_SEG_MAX}}" to "对话段数上限",
        "{{DIA_MIN}}" to "对话单段字数下限",
        "{{DIA_MAX}}" to "对话单段字数上限",
        "{{SEG_MIN}}" to "总段数建议下限",
        "{{SEG_MAX}}" to "总段数建议上限",
        "{{MOMENT_MIN_CHARS}}" to "动态字数下限",
        "{{MOMENT_MAX_CHARS}}" to "动态字数上限",
        "{{CURRENT_DATE}}" to "当前日期",
        "{{TIME_OF_DAY}}" to "当前时段",
        "{{RECENT_CHAT_SUMMARY}}" to "近期聊天摘要",
        "{{RECENT_MEMORIES}}" to "近期记忆",
        "{{MOMENT_TRIGGER_TYPE}}" to "动态生成触发方式",
        "{{WORLD_TODAY_STATE}}" to "当日公开世界状态",
        "{{RECENT_POSTS}}" to "近期动态",
        "{{RECENT_SOCIAL_CONTEXT}}" to "指定角色和用户最近三天的相关公开动态、评论",
        "{{RECENT_DAILY_SUMMARY}}" to "动态可参考的近期每日摘要",
        "{{COMMENTER_NAME}}" to "评论者名称",
        "{{COMMENTER_PERSONA}}" to "评论者人设",
        "{{COMMENTER_LOCATION}}" to "评论者当前地点（兼容字段，通常为空）",
        "{{COMMENTER_STATE}}" to "评论者当前状态（兼容字段，通常为空）",
        "{{COMMENTER_EMOTION}}" to "评论者当前情绪（兼容字段，通常为空）",
        "{{POST_AUTHOR_NAME}}" to "动态作者名称",
        "{{POST_AUTHOR_PERSONA}}" to "动态作者公开人设",
        "{{COMMENT_CONTEXT}}" to "当前评论区上下文",
        "{{COMMENTER_MEMORY}}" to "评论者可用的公开记忆",
        "{{PERSONAL_MEMORY_REFERENCE_STYLE}}" to "角色引用与用户共同经历的方式",
        "{{POST_CONTENT}}" to "被评论的动态正文",
        "{{COMMENT_TASK}}" to "评论任务类型：新评论或回复用户评论",
        "{{COMMENT_INSTRUCTION}}" to "本次评论/回复的具体任务说明",
        "{{REPLY_TARGET}}" to "需要回复的对象名称；新评论时为空",
        "{{COMMENT_MIN_CHARS}}" to "评论字数下限",
        "{{COMMENT_MAX_CHARS}}" to "评论字数上限",
        "{{YESTERDAY_DATE}}" to "昨天日期",
        "{{PRIVATE_DAILY_SUMMARY}}" to "私聊每日摘要",
        "{{PRIVATE_SUMMARY}}" to "私聊摘要",
        "{{GROUP_SUMMARIES}}" to "群聊摘要",
        "{{SELF_STATUS_CHANGES}}" to "自身状态变化",
        "{{DIARY_CONTEXT}}" to "日记上下文（昨日回顾/近期对话/群聊动态/事件锚点）",
        "{{RELATION_EVENTS}}" to "日记可参考的关系相关事件",
        "{{STATUS_EVENTS}}" to "日记可参考的自身状态变化",
        "{{DIARY_MIN_CHARS}}" to "日记字数下限",
        "{{DIARY_MAX_CHARS}}" to "日记字数上限",
        "{{TASK_TYPE}}" to "派遣任务类型",
        "{{BUDGET}}" to "派遣投入预算（龙门币）",
        "{{BUDGET_LEVEL}}" to "派遣预算等级",
        "{{DISPATCH_ROUND}}" to "当前过程轮数",
        "{{DISPATCH_SUMMARY}}" to "派遣完整日志摘要",
        "{{RECENT_PLOT}}" to "派遣最近剧情",
        "{{MEMBER_COUNT}}" to "派遣成员数量",
        "{{DURATION_HOURS}}" to "派遣时长（小时）",
        "{{DISPATCH_MIN_CHARS}}" to "派遣叙事字数下限",
        "{{DISPATCH_MAX_CHARS}}" to "派遣叙事字数上限",
        "{{DISPATCH_ENDING_MAX_CHARS}}" to "派遣结局字数上限",
        "{{MAX_CURRENCY_REWARD}}" to "派遣结局最高货币奖励"
    )

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = ::requestBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("提示词模板编辑", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, "帮助", tint = Blue400, modifier = Modifier.size(22.dp))
            }
            TextButton(onClick = { if (saveAllEdited()) { Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show(); onBack() } }) {
                Icon(Icons.Default.Check, null, tint = Blue400, modifier = Modifier.size(20.dp))
                Text("保存", color = Blue400, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = Divider)

        TabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = tabIndex == i,
                    onClick = {
                        if (tabIndex < 6) textMap[currentKey()] = textFieldValue
                        tabIndex = i
                    },
                    text = {
                        Text(
                            title,
                            fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        if (tabIndex == 0) {
            HorizontalDivider(color = Divider)
            TabRow(selectedTabIndex = privModeIdx, containerColor = Surface, contentColor = Blue400) {
                privModeLabels.forEachIndexed { i, label ->
                    Tab(
                        selected = privModeIdx == i,
                        onClick = {
                            textMap[currentKey()] = textFieldValue
                            privModeIdx = i
                        },
                        text = {
                            Text(
                                label,
                                fontWeight = if (privModeIdx == i) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        } else if (tabIndex == 1) {
            HorizontalDivider(color = Divider)
            TabRow(selectedTabIndex = grpModeIdx, containerColor = Surface, contentColor = Blue400) {
                grpModeLabels.forEachIndexed { i, label ->
                    Tab(
                        selected = grpModeIdx == i,
                        onClick = {
                            textMap[currentKey()] = textFieldValue
                            grpModeIdx = i
                        },
                        text = {
                            Text(
                                label,
                                fontWeight = if (grpModeIdx == i) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        } else if (tabIndex == 5) {
            HorizontalDivider(color = Divider)
            TabRow(selectedTabIndex = dispatchModeIdx, containerColor = Surface, contentColor = Blue400) {
                dispatchModeLabels.forEachIndexed { i, label ->
                    Tab(
                        selected = dispatchModeIdx == i,
                        onClick = {
                            textMap[currentKey()] = textFieldValue
                            dispatchModeIdx = i
                        },
                        text = { Text(label, fontWeight = if (dispatchModeIdx == i) FontWeight.SemiBold else FontWeight.Normal) }
                    )
                }
            }
        }

        if (tabIndex == 0 || tabIndex == 1) {
            HorizontalDivider(color = Divider)
            TabRow(selectedTabIndex = moduleIdx, containerColor = Surface, contentColor = Blue400) {
                listOf("角色规则", "输出协议", "运行时资料").forEachIndexed { i, label ->
                    Tab(
                        selected = moduleIdx == i,
                        onClick = {
                            textMap[currentKey()] = textFieldValue
                            moduleIdx = i
                        },
                        text = { Text(label, fontWeight = if (moduleIdx == i) FontWeight.SemiBold else FontWeight.Normal) }
                    )
                }
            }
        }

        Column(modifier = Modifier.weight(1f).imePadding()) {
        if (tabIndex < 6) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                 val persistedCustom = if (currentModule() == "role") viewModel.isPromptTemplateCustom(currentType(), currentMode())
                 else viewModel.isPromptModuleCustom(currentModule(), currentType(), currentMode())
                 val isDirty = currentKey() in dirtyKeys
                 val isCustom = persistedCustom || isDirty
                val missingCritical = criticalPlaceholders(currentType(), currentMode())
                    .filter { "{{$it}}" !in textFieldValue.text }
                 Card(colors = CardDefaults.cardColors(containerColor = Blue400.copy(alpha = 0.08f))) {
                     Column(Modifier.padding(12.dp)) {
                        Text(
                             if (isDirty) "有未保存的模板修改" else if (isCustom) "你正在使用自己的模板" else "你正在使用系统默认模板",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                         Text(
                             when {
                                 isDirty -> "当前编辑内容尚未保存。点击保存后，系统将按你编辑的内容使用，不会自动恢复默认模板。"
                                 currentModule() == "runtime" && isCustom -> "运行时资料会按你保存的内容生成。本编辑框不是完整的最终请求；用户消息、历史消息和群聊必要资料仍由应用单独组装。"
                                 currentModule() == "protocol" && isCustom -> "输出协议会按你保存的内容追加到本轮请求。你可以自由改写格式，程序只会尽量解析模型输出。"
                                 isCustom -> "你可以编辑角色性格、语气、世界观和互动偏好。系统固定规则、运行时资料、历史消息和输出协议仍由应用自动加入；本编辑框不是完整的最终请求。"
                                 else -> "这里显示的是角色规则模板，不是完整的最终请求。系统固定规则、时间、记忆、历史消息和本轮输入会在每次聊天时由应用自动组装。"
                             },
                             fontSize = 11.sp,
                             color = TextSecondary,
                             lineHeight = 16.sp
                         )
                        Spacer(Modifier.height(8.dp))
                        Text(
                             "模板版本 v${PromptTemplates.VERSION} · ${if (isDirty) "有未保存修改" else if (isCustom) "当前为自定义模板" else "当前为系统默认模板"} · ${if (currentModule() == "runtime") "本模块进入 user 消息" else "本模块进入 system 消息"}",
                            fontSize = 10.sp,
                            color = TextTertiary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Shipped templates receive the current user message outside the template.
                // Only warn when a user-authored template deliberately omits critical context.
                if (currentModule() == "role" && isCustom && missingCritical.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9500).copy(alpha = 0.10f))) {
                        Text(
                            "提醒：当前模板没有 ${missingCritical.joinToString { "{{$it}}" }}。保存后相关信息可能不会传给模型，聊天效果可能变差。你仍可保存，但建议确认这是有意修改。",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (tabIndex == 0 && currentModule() == "role") {
                    Card(colors = CardDefaults.cardColors(containerColor = Blue400.copy(alpha = 0.08f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("基础回复规则始终生效", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "系统会优先理解并回应用户当前真正想表达的内容。固定协议：线上为【状态】【心情】【位置】【本轮简述】【台词】；线下/导演额外包含【旁白】。\n\n这里的模板只负责角色规则。输出协议、历史消息、运行时资料和本轮输入由应用自动组装；不要在模板中尝试替换系统协议。",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (tabIndex == 1 && currentModule() == "role") {
                    Card(colors = CardDefaults.cardColors(containerColor = Blue400.copy(alpha = 0.08f))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("群聊回复规则始终生效", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "系统会限制当前模式的旁白规则、成员发言次数和消息字数。固定协议使用【本轮剧情简述】、【旁白】和【发言人: operator_id】。这里的模板只负责群聊氛围、关系和世界观；成员资料、历史消息和当前任务由应用自动组装。",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { showStructureDialog = true },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400)
                ) {
                    Text("查看本场景的请求结构", fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                 if (currentModule() != "role") {
                     Text(
                         if (currentModule() == "protocol") "输出协议是自由文本。可以删除、新增或改写标签；程序会尽量解析可识别内容，无法解析时仍会保留可读回复。"
                         else "运行时资料模板是自由文本。可以调整资料顺序、说明和占位符；只要保存过自定义内容，系统就不会自动恢复默认模板。未知占位符只会产生警告。内容清空并保存后，本场景不会发送这部分资料；如需恢复系统资料，请点击“恢复默认”。",
                         fontSize = 11.sp,
                         color = TextSecondary,
                         lineHeight = 16.sp,
                         modifier = Modifier.padding(bottom = 8.dp)
                     )
                 }
                 if (currentModule() == "runtime" && textFieldValue.text.isEmpty()) {
                     Card(colors = CardDefaults.cardColors(containerColor = Blue400.copy(alpha = 0.08f))) {
                         Text(
                             "当前内容为空。保存后不会自动恢复默认运行时资料。",
                             fontSize = 11.sp,
                             color = TextSecondary,
                             modifier = Modifier.padding(12.dp)
                         )
                     }
                     Spacer(Modifier.height(8.dp))
                 }
                OutlinedTextField(
                    value = textFieldValue,
                     onValueChange = {
                         textFieldValue = it
                         resetKeys.remove(currentKey())
                         if (currentKey() !in dirtyKeys) dirtyKeys += currentKey()
                     },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 360.dp)
                        .bringIntoViewRequester(editorBringIntoViewRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused) {
                                editorScope.launch { editorBringIntoViewRequester.bringIntoView() }
                            }
                        },
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Blue400,
                        unfocusedBorderColor = Divider
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clip?.primaryClip?.getItemAt(0)?.text?.toString()?.let {
                                 textFieldValue = TextFieldValue(it)
                                 resetKeys.remove(currentKey())
                                 if (currentKey() !in dirtyKeys) dirtyKeys += currentKey()
                            }
                        },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400)
                    ) {
                        Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("从剪贴板粘贴", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clip?.setPrimaryClip(android.content.ClipData.newPlainText("prompt", textFieldValue.text))
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400)
                    ) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制到剪贴板", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { showResetDialog = true },
                        modifier = Modifier.height(32.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF9500))
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("恢复默认", fontSize = 12.sp)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                HelpContent(
                    type = currentType(),
                    mode = currentMode(),
                    placeholders = allPlaceholders,
                    onStructure = { showStructureDialog = true }
                )
            }
        }
    }
    }
    }

    if (showResetDialog) {
        val resetLabel = when (tabIndex) {
            0 -> "${tabs[0]} - ${privModeLabels[privModeIdx]}"
            1 -> "${tabs[1]} - ${grpModeLabels[grpModeIdx]}"
            5 -> "${tabs[5]} - ${dispatchModeLabels[dispatchModeIdx]}"
            else -> tabs[tabIndex]
        }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认模板", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = { Text("确定要将「${resetLabel}」恢复为系统默认模板吗？你自己编辑过的内容会丢失。恢复后会重新使用系统的连续聊天优化模板。", fontSize = 14.sp, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    val fresh = TextFieldValue(
                        if (currentModule() == "role") viewModel.getDefaultPromptTemplate(currentType(), currentMode())
                        else viewModel.getDefaultPromptModule(currentModule(), currentType(), currentMode())
                    )
                    textMap[currentKey()] = fresh
                    textFieldValue = fresh
                    if (currentKey() !in dirtyKeys) dirtyKeys += currentKey()
                    if (currentKey() !in resetKeys) resetKeys += currentKey()
                    showResetDialog = false
                }) { Text("确定", color = Color(0xFFFF9500), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("有未保存修改", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = { Text("当前还有未保存的模板修改。请选择如何离开。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    if (saveAllEdited()) onBack()
                }) { Text("保存并离开", color = Primary) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showExitDialog = false; onBack() }) { Text("放弃修改", color = Color(0xFFFF9500)) }
                    TextButton(onClick = { showExitDialog = false }) { Text("继续编辑", color = TextSecondary) }
                }
            }
        )
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("占位符说明", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "每个模式都有独立的角色规则模板。模板不是发送给 AI 的全部内容：应用还会自动加入系统固定规则、运行时资料、历史 user/assistant 消息、本轮输入和输出协议。私聊用户本轮消息通常单独作为 user 消息发送；{{USER_MESSAGE}} 是群聊用户最新发言。占位符必须使用 {{大写英文_数字}} 格式，错误或不适用的占位符不能保存。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (currentType() != "help") {
                        val allowed = PromptPlaceholderRegistry.allowed(currentType(), currentMode())
                        val recommended = PromptPlaceholderRegistry.recommended(currentType(), currentMode())
                        allPlaceholders.distinctBy { it.first }.filter { (key, _) -> key.removePrefix("{{").removeSuffix("}}") in allowed }
                            .sortedBy { (key, _) -> if (key.removePrefix("{{").removeSuffix("}}") in recommended) 0 else 1 }
                            .forEach { (key, desc) ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    key,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue400,
                                    modifier = Modifier.width(170.dp)
                                )
                                Text(
                                    if (key.removePrefix("{{").removeSuffix("}}") in recommended) "$desc（推荐使用）" else "$desc（当前可用；部分场景可能为空）",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("知道了", color = Primary)
                }
            }
        )
    }

    if (showStructureDialog) {
        val preview = promptStructurePreview(currentType(), currentMode())
        AlertDialog(
            onDismissRequest = { showStructureDialog = false },
            title = { Text("${tabs[tabIndex]} · ${modeDisplayName(currentType(), currentMode())} 请求结构") },
            text = {
                SelectionContainer {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text("这是结构示例，不是当前真实聊天的一轮请求。真实资料、历史和用户输入会在发送时动态生成。", fontSize = 12.sp, color = TextSecondary)
                        Spacer(Modifier.height(10.dp))
                        preview.forEach { (title, body, editable) ->
                            Text(title + if (editable) "  · 可编辑" else "  · 只读", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (editable) Blue400 else TextPrimary)
                            Text(body, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStructureDialog = false }) { Text("知道了", color = Primary) } }
        )
    }
}

@Composable
private fun HelpContent(
    type: String,
    mode: String,
    placeholders: List<Pair<String, String>>,
    onStructure: () -> Unit
) {
    Text("这个页面编辑的是角色规则模板，不是完整最终提示词。", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    Spacer(Modifier.height(6.dp))
    Text("实际请求由多个消息模块组成：system 规则、user 运行时资料、历史 user/assistant 消息，以及本轮 user 输入。你可以修改角色规则，但其他模块由应用根据当前聊天自动生成。", fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp)
    Spacer(Modifier.height(10.dp))
    OutlinedButton(onClick = onStructure, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue400)) {
        Text("查看当前场景请求结构")
    }
    Spacer(Modifier.height(14.dp))
    Text("占位符分层", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
    Spacer(Modifier.height(4.dp))
    Text("稳定的角色信息通常进入 system；时间、记忆、摘要等易变资料通常进入 user 资料；本轮消息通常由应用单独追加。", fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp)
    Spacer(Modifier.height(8.dp))
    val isGeneralHelp = type == "help"
    val allowed = if (isGeneralHelp) {
        placeholders.map { keyName(it.first) }.toSet()
    } else PromptPlaceholderRegistry.allowed(type, mode)
    val recommended = if (isGeneralHelp) emptySet() else PromptPlaceholderRegistry.recommended(type, mode)
    val runtime = if (isGeneralHelp) {
        listOf(
            "private" to "online", "private" to "offline", "private" to "director", "private" to "proactive",
            "group" to "online", "group" to "offline", "group" to "director", "group" to "auto",
            "moment" to "", "moment_comment" to "", "diary" to ""
        ).flatMap { (surface, surfaceMode) -> PromptPlaceholderRegistry.runtimeKeys(surface, surfaceMode).toList() }.toSet()
    } else PromptPlaceholderRegistry.runtimeKeys(type, mode)
    placeholders.distinctBy { it.first }
        .filter { (key, _) -> key.removePrefix("{{").removeSuffix("}}") in allowed }
        .sortedWith(compareBy({ layerOrder(keyName(it.first), runtime) }, { it.first }))
        .forEach { (key, desc) ->
            val name = keyName(key)
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                Text(key, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue400, modifier = Modifier.width(170.dp))
                Column(Modifier.weight(1f)) {
                    Text(desc, fontSize = 12.sp, color = TextSecondary)
                    Text("${placeholderLayer(name, runtime)}${if (name in recommended) " · 推荐" else ""}", fontSize = 10.sp, color = TextTertiary)
                }
            }
        }
}

private fun keyName(value: String): String = value.removePrefix("{{").removeSuffix("}}")

private fun placeholderLayer(name: String, runtime: Set<String>): String = when {
    name == "USER_CONTENT" || name == "USER_MESSAGE" -> "本轮 user 输入/任务"
    name in runtime -> "运行时 user 资料"
    else -> "稳定 system 模板"
}

private fun layerOrder(name: String, runtime: Set<String>): Int = when {
    name == "USER_CONTENT" || name == "USER_MESSAGE" -> 2
    name in runtime -> 1
    else -> 0
}

private fun modeDisplayName(type: String, mode: String): String = when {
    type == "private" -> mapOf("online" to "线上模式", "offline" to "线下模式", "director" to "导演模式", "proactive" to "主动消息")[mode] ?: mode
    type == "group" -> mapOf("online" to "线上模式", "offline" to "线下模式", "director" to "导演模式", "auto" to "自动群聊")[mode] ?: mode
    type == "dispatch" -> mapOf("start" to "开局", "progress" to "过程", "ending" to "结局")[mode] ?: mode
    else -> "默认"
}

private fun promptStructurePreview(type: String, mode: String): List<Triple<String, String, Boolean>> {
    if (type == "help") {
        return listOf(
            Triple("system · 固定规则", "应用始终追加的行为边界、场景连续性、安全规则和输出协议。", false),
            Triple("system · 角色规则模板", "用户在私聊/群聊/动态等场景编辑的角色规则模板。", true),
            Triple("user · 运行时资料", "当前时间、状态、记忆、摘要、群聊主线、成员资料和知识库内容。", false),
            Triple("user/assistant · 历史消息", "从真实聊天记录中按历史条数和上下文上限自动裁剪。", false),
            Triple("user · 本轮输入", "当前用户消息或本轮自动任务，由应用在发送前自动追加。", false)
        )
    }
    val role = if (type == "group") "群聊角色规则模板" else "角色规则模板"
    if (type == "dispatch") {
        return listOf(
            Triple("system · 派遣模板", "派遣模板、任务参数、成员档案和输出协议会合并为一条 system 消息。", true),
            Triple("输出协议", "开局/过程：直接输出叙事文本。\n结局：输出规定的 JSON 结果。", false)
        )
    }
    val output = when {
        type == "private" -> "【状态】...\n【心情】...\n【位置】...\n【本轮简述】...\n【台词】...\n（线下/导演模式还会要求【旁白】）"
        type == "group" -> "【本轮剧情简述】...\n【发言人: operator_id】\n角色台词\n（线下/导演模式可有【旁白】）"
        type == "moment" -> "动态纯文本"
        type == "moment_comment" -> "评论纯文本"
        type == "diary" -> "第一人称日记纯文本"
        else -> "按当前任务模板输出"
    }
    val runtime = when {
        type == "private" -> "【当前时间】...\n【上一轮互动状态】...\n【相关记忆】...\n【聊天摘要】..."
        type == "group" -> "【当前时间】...\n【当前群聊主线】...\n【成员资料】...\n【群聊记忆】..."
        type == "diary" -> "【昨天私聊事实】...\n【昨天群聊事实】...\n【近期背景】..."
        else -> "【本轮背景资料】...\n【相关记忆】...\n【任务参数】..."
    }
    return listOf(
        Triple("system · 固定规则", "由应用追加的行为边界、场景规则和安全规则。默认只读。", false),
        Triple("system · $role", "当前编辑框中的模板。占位符会按稳定/运行时分类处理。", true),
        Triple("user · 运行时资料", runtime, false),
        Triple("user/assistant · 历史消息", "[user] 用户上一轮消息\n[assistant] 角色上一轮回复\n（按历史轮数和上下文上限自动裁剪）", false),
        Triple("user · 本轮输入", "【用户本轮消息】\n用户：示例消息\n（自动追加，不需要写入角色模板）", false),
        Triple("输出协议", output, false)
    )
}
