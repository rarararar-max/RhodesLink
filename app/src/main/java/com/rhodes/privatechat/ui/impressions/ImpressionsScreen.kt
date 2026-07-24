package com.rhodes.privatechat.ui.impressions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.model.MemoryItem
import com.rhodes.privatechat.shared.model.MemoryLevel
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun ImpressionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOperatorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsState()
    var impressions by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MemoryItem?>(null) }
    var editContent by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun refreshImpressions() {
        scope.launch { impressions = viewModel.getCurrentImpressions() }
    }

    LaunchedEffect(Unit) { impressions = viewModel.getCurrentImpressions() }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有印象") },
            text = { Text("将清除当前页面展示的所有印象记录，且无法恢复。不会删除聊天记录、动态或评论。") },
            confirmButton = { TextButton(onClick = { scope.launch { viewModel.deleteAllCurrentImpressions(); impressions = viewModel.getCurrentImpressions() }; showClearDialog = false }) { Text("确认清空", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消", color = TextSecondary) } }
        )
    }

    editing?.let { entry ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (entry.memoryLevel == MemoryLevel.L3) "编辑长期印象" else "编辑近期了解") },
            text = { OutlinedTextField(value = editContent, onValueChange = { editContent = it }, label = { Text("印象内容") }, modifier = Modifier.fillMaxWidth(), minLines = 4) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.updateCurrentImpression(entry, editContent)
                        impressions = viewModel.getCurrentImpressions()
                        editing = null
                    }
                }, enabled = editContent.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("取消", color = TextSecondary) } }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(modifier = Modifier.width(4.dp))
            Text("大家的印象", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = ::refreshImpressions) { Icon(Icons.Default.Refresh, "刷新印象", tint = TextSecondary) }
            TextButton(onClick = { showClearDialog = true }) { Text("清空", color = ErrorRed, fontSize = 14.sp) }
        }
        HorizontalDivider(color = Divider)

        DetailImpressionsPage(
            impressions = impressions,
            operators = operators,
            onOperatorClick = onOperatorClick,
            onEdit = { entry -> editing = entry; editContent = entry.content },
            onDelete = { entry -> scope.launch { viewModel.deleteOperatorMemoryItem(entry); impressions = viewModel.getCurrentImpressions() } }
        )
    }
    }
}

@Composable
private fun DetailImpressionsPage(
    impressions: List<MemoryItem>,
    operators: List<Operator>,
    onOperatorClick: (String) -> Unit = {},
    onEdit: (MemoryItem) -> Unit,
    onDelete: (MemoryItem) -> Unit
) {
    val longTerm = impressions.filter { it.memoryLevel == MemoryLevel.L3 }
    val sustained = impressions.filter { it.memoryLevel == MemoryLevel.L2 }
    val interaction = impressions.filter { it.memoryLevel == MemoryLevel.L1 }
    if (longTerm.isEmpty() && sustained.isEmpty() && interaction.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无印象数据", fontSize = 16.sp, color = TextTertiary)
                Spacer(Modifier.height(6.dp))
                Text("多聊几次后，干员会逐渐形成对你的近期了解和长期印象", fontSize = 12.sp, color = TextTertiary)
            }
        }
        return
    }
    LazyColumn {
        if (longTerm.isNotEmpty()) {
            item { ImpressionSectionHeader("长期印象", "反复互动后形成的稳定了解") }
            items(longTerm, key = { it.id }) { ImpressionEntry(it, operators, "长期印象", onOperatorClick, onEdit, onDelete) }
        }
        if (sustained.isNotEmpty()) {
            item { ImpressionSectionHeader("持续了解", "经多次互动合并出的偏好、约定和关心点") }
            items(sustained, key = { it.id }) { ImpressionEntry(it, operators, "持续了解", onOperatorClick, onEdit, onDelete) }
        }
        if (interaction.isNotEmpty()) {
            item { ImpressionSectionHeader("互动印象", "偏好、情绪和身体状态会按记录日期保留") }
            items(interaction, key = { it.id }) { ImpressionEntry(it, operators, "互动印象", onOperatorClick, onEdit, onDelete) }
        }
    }
}

@Composable
private fun ImpressionSectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.fillMaxWidth().background(BG).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(subtitle, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun ImpressionEntry(
    entry: MemoryItem,
    operators: List<Operator>,
    levelLabel: String,
    onOperatorClick: (String) -> Unit,
    onEdit: (MemoryItem) -> Unit,
    onDelete: (MemoryItem) -> Unit
) {
    val op = operators.find { it.id == entry.ownerId }
    val displayName = op?.name ?: entry.ownerId
    Column(modifier = Modifier.fillMaxWidth().background(Surface).padding(16.dp)) {
        Row(modifier = Modifier.clickable { onOperatorClick(entry.ownerId) }, verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatarImage(avatarUri = op?.avatarUri ?: "", name = displayName, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("$levelLabel · ${impressionDate(entry)}", fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { onEdit(entry) }) { Icon(Icons.Default.Edit, "编辑印象", tint = TextSecondary) }
            IconButton(onClick = { onDelete(entry) }) { Icon(Icons.Default.Delete, "删除印象", tint = ErrorRed) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(entry.content, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(memoryTypeLabel(entry.memoryType), fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Primary.copy(alpha = 0.1f)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
    }
    HorizontalDivider(color = Divider)
}

private fun impressionDate(entry: MemoryItem): String {
    val eventTime = entry.eventTime?.trim().orEmpty()
    if (eventTime.isNotBlank()) return "发生于 $eventTime"
    val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }
    return "记录于 ${formatter.format(Date(entry.createdAt))}"
}

private fun memoryTypeLabel(type: String): String = when (type) {
    "preference_expression" -> "偏好"
    "emotion_state" -> "情绪"
    "physiological_state" -> "身体状态"
    "agreement_commitment" -> "约定"
    "care_reminder" -> "关心提醒"
    "intent_wish" -> "意图愿望"
    "evaluation_opinion" -> "评价观点"
    "self_cognition_statement" -> "自我认知"
    else -> type.replace('_', ' ')
}
