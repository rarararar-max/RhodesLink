package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.data.db.entity.ChatSessionEntity
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun PermissionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val groups = allSessions.filter { it.operatorId.startsWith("group_") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs = listOf("干员", "群聊")
    var tabIndex by remember { mutableIntStateOf(0) }
    var pendingDeletionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var finishSave by remember { mutableStateOf<(() -> Unit)?>(null) }

    SaveableSettingsScaffold(
        title = "权限管理",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.Default.Build, null, tint = Primary) },
        onSaveRequest = { completeSave ->
            val finish = {
                completeSave()
                viewModel.refreshAutoGroupChats()
            }
            if (pendingDeletionIds.isEmpty()) finish()
            else {
                finishSave = finish
                showDeleteConfirm = true
            }
        }
    ) {

        TabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }

        when (tabIndex) {
            0 -> OperatorPermTab(operators = operators, pendingDeletionIds = pendingDeletionIds, onDeletionChanged = { pendingDeletionIds = it })
            1 -> GroupPermTab(groups = groups, viewModel = viewModel)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除干员", color = TextPrimary) },
            text = { Text("已勾选删除 ${pendingDeletionIds.size} 个干员。将同时删除这些干员的私聊、记忆、关系、动态和相关数据，此操作无法恢复。是否确认？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteOperators(pendingDeletionIds) {
                        finishSave?.invoke()
                        finishSave = null
                    }
                }) { Text("确认删除", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextSecondary) } }
        )
    }
}

@Composable
private fun OperatorPermTab(
    operators: List<com.rhodes.privatechat.data.db.entity.OperatorEntity>,
    pendingDeletionIds: Set<String>,
    onDeletionChanged: (Set<String>) -> Unit
) {
    val settings: SettingsRepository = koinInject()

    Column {
        Text("批量设置角色主动私聊和动态参与权限。删除勾选会在点击保存并确认后执行，且会同时删除该干员的相关聊天、记忆、关系和动态数据。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("干员", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("主动私聊", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
            Text("动态权限", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
            Text("删除", fontSize = 11.sp, color = ErrorRed, modifier = Modifier.width(56.dp))
        }

        LazyColumn {
            items(operators) { op ->
                var allowMsg by remember(op.id) { mutableStateOf(settings.getOperatorMsgPermission(op.id)) }
                var allowDyn by remember(op.id) { mutableStateOf(settings.getOperatorDynPermission(op.id)) }
                val markedForDeletion = op.id in pendingDeletionIds
                Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(op.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = allowMsg, onCheckedChange = { b -> allowMsg = b; settings.putOperatorMsgPermission(op.id, b) }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                    Switch(checked = allowDyn, onCheckedChange = { b -> allowDyn = b; settings.putOperatorDynPermission(op.id, b) }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = AccentOrange, checkedTrackColor = AccentOrange.copy(alpha = 0.2f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                    Switch(checked = markedForDeletion, onCheckedChange = { checked ->
                        onDeletionChanged(if (checked) pendingDeletionIds + op.id else pendingDeletionIds - op.id)
                    }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = ErrorRed, checkedTrackColor = ErrorRed.copy(alpha = 0.2f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                }
                HorizontalDivider(color = Divider)
            }
        }
    }
}

@Composable
private fun GroupPermTab(groups: List<com.rhodes.privatechat.data.db.entity.ChatSessionEntity>, viewModel: MainViewModel) {
    val settings: SettingsRepository = koinInject()

    Column {
        Text("只有你为群聊开启“空闲自动”后，群聊才会在到达设定时间时自动聊天。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("暂无群聊", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("群聊", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text("空闲自动", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
            }
            LazyColumn {
                items(groups, key = { it.id }) { g ->
                    var idleAuto by remember(g.id) { mutableStateOf(settings.getGroupAuto(g.id)) }
                    Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OperatorAvatarImage(avatarUri = g.avatarUri, name = g.operatorName, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.operatorName, fontSize = 14.sp, color = TextPrimary)
                            Text("到达设定时间后自动聊天", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = idleAuto, onCheckedChange = { b ->
                            idleAuto = b
                            settings.putGroupAuto(g.id, b)
                        }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                    }
                    HorizontalDivider(color = Divider)
                }
            }
        }
    }
}
