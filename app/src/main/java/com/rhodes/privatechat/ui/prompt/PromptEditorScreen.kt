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
    val tabs = listOf("私聊", "群聊", "动态", "日记", "说明")
    val tabKeys = listOf("private", "group", "moment", "diary", "help")
    var tabIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val privModeLabels = listOf("线上模式", "线下模式", "导演模式")
    val privModes = listOf("online", "offline", "director")
    val grpModeLabels = listOf("线上模式", "线下模式", "导演模式")
    val grpModes = listOf("online", "offline", "director")

    var privModeIdx by remember { mutableIntStateOf(0) }
    var grpModeIdx by remember { mutableIntStateOf(0) }

    val textMap = remember { mutableStateMapOf<String, TextFieldValue>() }

    fun loadTemplate(type: String, mode: String) = viewModel.getPromptTemplate(type, mode)

    fun currentKey(): String = when (tabIndex) {
        0 -> "private:${privModes[privModeIdx]}"
        1 -> "group:${grpModes[grpModeIdx]}"
        2 -> "moment:"
        3 -> "diary:"
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
            if (tabIndex < 4) {
                textMap[key] ?: TextFieldValue(loadTemplate(currentType(), currentMode()))
            } else {
                TextFieldValue("")
            }
        )
    }

    val saveCurrent: () -> Unit = {
        if (tabIndex < 4) {
            textMap[currentKey()] = textFieldValue
            viewModel.savePromptTemplate(currentType(), currentMode(), textFieldValue.text)
        }
    }

    BackHandler(onBack = { saveCurrent(); onBack() })

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
        "{{MEMORY_INJECTION}}" to "系统自动注入的记忆上下文（每日摘要/对话摘要/长期印象/锚点/共享记忆/附近干员）",
        "{{MODE_RULES}}" to "当前模式的对话规则（线上禁止旁白/线下多段叙事/导演模式）",
        "{{INJECTION}}" to "通用注入点（已废弃，请用具体占位符）",
        "{{GROUP_NAME}}" to "群聊名称",
        "{{GROUP_INJECTION}}" to "群聊记忆上下文（关系/私聊摘要/印象/每日摘要）",
        "{{MEMBER_PROFILES}}" to "成员简介与人设（自动生成）",
        "{{USER_MESSAGE}}" to "用户最新发言内容",
        "{{OUTPUT_FORMAT}}" to "输出JSON格式规范",
        "{{GROUP_MSG_MIN}}" to "群聊每条消息字数下限",
        "{{GROUP_MSG_MAX}}" to "群聊每条消息字数上限",
        "{{GROUP_SPEECH_MIN}}" to "群聊每人发言次数下限",
        "{{GROUP_SPEECH_MAX}}" to "群聊每人发言次数上限",
        "{{MOMENT_MIN_CHARS}}" to "动态字数下限",
        "{{MOMENT_MAX_CHARS}}" to "动态字数上限",
        "{{DIARY_CONTEXT}}" to "日记上下文（昨日回顾/近期对话/群聊动态/事件锚点）",
        "{{DIARY_MIN_CHARS}}" to "日记字数下限",
        "{{DIARY_MAX_CHARS}}" to "日记字数上限"
    )

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { saveCurrent(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("提示词模板编辑", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { showHelpDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, "帮助", tint = Blue400, modifier = Modifier.size(22.dp))
            }
            TextButton(onClick = { saveCurrent(); Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show(); onBack() }) {
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
                        if (tabIndex < 4) textMap[currentKey()] = textFieldValue
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
        if (tabIndex < 4) {
            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
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
                        "提示：私聊和群聊下的子标签页（线上/线下/导演模式）的模板会根据所选模式独立加载和保存。",
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
