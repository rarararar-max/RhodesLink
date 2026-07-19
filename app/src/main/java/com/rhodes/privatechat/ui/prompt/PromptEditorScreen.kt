package com.rhodes.privatechat.ui.prompt

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel

@Composable
fun PromptEditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("私聊", "群聊", "动态", "评论", "日记", "说明")
    val tabKeys = listOf("private", "group", "moment", "moment_comment", "diary", "help")
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val privModeLabels = listOf("线上模式", "线下模式", "导演模式", "主动消息")
    val privModes = listOf("online", "offline", "director", "proactive")
    // Automatic group chat reuses the group's current online/offline/director template.
    val grpModeLabels = listOf("线上模式", "线下模式", "导演模式")
    val grpModes = listOf("online", "offline", "director")

    var privModeIdx by remember { mutableIntStateOf(0) }
    var grpModeIdx by remember { mutableIntStateOf(0) }

    val textMap = remember { mutableStateMapOf<String, TextFieldValue>() }
    val dirtyKeys = remember { mutableStateListOf<String>() }

    fun loadTemplate(type: String, mode: String) = viewModel.getPromptTemplate(type, mode)

    fun currentKey(): String = when (tabIndex) {
        0 -> "private:${privModes[privModeIdx]}"
        1 -> "group:${grpModes[grpModeIdx]}"
        2 -> "moment:"
        3 -> "moment_comment:"
        4 -> "diary:"
        else -> "help:"
    }

    fun currentType(): String = tabKeys[tabIndex]

    fun currentMode(): String = when (tabIndex) {
        0 -> privModes[privModeIdx]
        1 -> grpModes[grpModeIdx]
        else -> ""
    }

    val key = currentKey()
    var textFieldValue by remember(key) {
        mutableStateOf(
            if (tabIndex < 5) {
                textMap[key] ?: TextFieldValue(loadTemplate(currentType(), currentMode()))
            } else {
                TextFieldValue("")
            }
        )
    }

    val saveCurrent: () -> Boolean = saveCurrent@{
        if (tabIndex < 5 && currentKey() in dirtyKeys) {
            textMap[currentKey()] = textFieldValue
            val result = runCatching { viewModel.savePromptTemplate(currentType(), currentMode(), textFieldValue.text) }
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
        if (tabIndex < 5) textMap[currentKey()] = textFieldValue
        val warnings = mutableListOf<String>()
        val editedTemplates = textMap.filterKeys { it in dirtyKeys }.toMap()
        for ((templateKey, value) in editedTemplates) {
            val type = templateKey.substringBefore(":")
            val mode = templateKey.substringAfter(":", "")
            val result = runCatching { viewModel.savePromptTemplate(type, mode, value.text) }
            if (result.isFailure) {
                Toast.makeText(context, "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                return@saveAllEdited false
            }
            warnings += result.getOrThrow()
        }
        dirtyKeys.clear()
        if (warnings.isNotEmpty()) {
            android.widget.Toast.makeText(context, warnings.distinct().joinToString("\n"), android.widget.Toast.LENGTH_LONG).show()
        }
        true
    }

    BackHandler(onBack = { if (saveAllEdited()) onBack() })

    var showResetDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val allPlaceholders = listOf(
        "{{OPERATOR_NAME}}" to "干员名称",
        "{{OPERATOR_TITLE}}" to "干员身份/头衔",
        "{{OPERATOR_PERSONA}}" to "干员人设描述（privatePrompt）",
        "{{USER_NAME}}" to "用户昵称",
        "{{USER_GENDER}}" to "用户性别",
        "{{USER_BIO}}" to "用户简介",
        "{{USER_GENDER_BIO}}" to "用户性别+简介拼接",
        "{{CURRENT_TIME}}" to "当前北京时间",
        "{{AI_ANALYSIS}}" to "双模型分析结果（为空则不注入）",
        "{{HYPNOSIS}}" to "催眠指令（为空则不注入）",
        "{{TRANSITION_NOTICE}}" to "模式切换、继续说、重说等临时系统指令",
        "{{PROACTIVE_TRIGGER_TYPE}}" to "主动触发类型（idle）",
        "{{PROACTIVE_TRIGGER_CONTEXT}}" to "主动私聊上下文",
        "{{OPERATOR_GENDER}}" to "干员性别设定",
        "{{MEMORY_INJECTION}}" to "系统自动注入的记忆上下文（每日摘要/对话摘要/长期印象/锚点/共享记忆）",
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
        "{{USER_MESSAGE}}" to "用户最新发言内容",
        "{{OUTPUT_FORMAT}}" to "输出JSON格式规范",
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
        "{{RECENT_POSTS}}" to "近期动态",
        "{{RECENT_SOCIAL_CONTEXT}}" to "指定角色和用户最近三天的相关公开动态、评论",
        "{{RECENT_DAILY_SUMMARY}}" to "动态可参考的近期每日摘要",
        "{{COMMENTER_NAME}}" to "评论者名称",
        "{{COMMENTER_PERSONA}}" to "评论者人设",
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
        "{{DIARY_MAX_CHARS}}" to "日记字数上限"
    )

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (saveAllEdited()) onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
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
                        if (tabIndex < 5) textMap[currentKey()] = textFieldValue
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
        }

        Column(modifier = Modifier.weight(1f).imePadding()) {
        if (tabIndex < 5) {
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = {
                        textFieldValue = it
                        if (currentKey() !in dirtyKeys) dirtyKeys += currentKey()
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
                allPlaceholders.forEach { (key, desc) ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            key,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Blue400,
                            modifier = Modifier.width(200.dp)
                        )
                        Text(desc, fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }
        }
    }
    }
    }

    if (showResetDialog) {
        val resetLabel = when (tabIndex) {
            0 -> "${tabs[0]} - ${privModeLabels[privModeIdx]}"
            1 -> "${tabs[1]} - ${grpModeLabels[grpModeIdx]}"
            else -> tabs[tabIndex]
        }
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认模板", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = { Text("确定要将「${resetLabel}」模板恢复为默认值吗？当前编辑的内容将会丢失。", fontSize = 14.sp, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetPromptTemplate(currentType(), currentMode())
                    val fresh = TextFieldValue(viewModel.getPromptTemplate(currentType(), currentMode()))
                    textMap[currentKey()] = fresh
                    textFieldValue = fresh
                    dirtyKeys.remove(currentKey())
                    showResetDialog = false
                }) { Text("确定", color = Color(0xFFFF9500), fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消", color = TextSecondary) }
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
                        "提示：私聊和群聊下的子标签页（线上/线下/导演模式）的模板会根据所选模式独立加载和保存。未识别的占位符会原样保留在最终提示词中，请核对拼写。",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    allPlaceholders.forEach { (key, desc) ->
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
                            Text(desc, fontSize = 13.sp, color = TextSecondary)
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
}
