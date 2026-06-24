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
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
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

    SaveableSettingsScaffold(
        title = "权限管理",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.Default.Build, null, tint = Primary) }
    ) {

        TabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }

        when (tabIndex) {
            0 -> OperatorPermTab(operators = operators)
            1 -> GroupPermTab(groups = groups, viewModel = viewModel)
        }
    }
}

@Composable
private fun OperatorPermTab(operators: List<com.rhodes.privatechat.data.db.entity.OperatorEntity>) {
    val settings: SettingsRepository = koinInject()

    Column {
        Text("批量设置角色主动私聊和动态参与权限。动态权限会影响每日自动动态、事件触发动态、自动评论/围观回复；主动私聊权限只影响角色在你未发消息时主动找你，不影响你主动聊天后的正常回复。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("干员", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("主动私聊", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
            Text("动态权限", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
        }

        LazyColumn {
            items(operators) { op ->
                var allowMsg by remember(op.id) { mutableStateOf(settings.getOperatorMsgPermission(op.id)) }
                var allowDyn by remember(op.id) { mutableStateOf(settings.getOperatorDynPermission(op.id)) }
                Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(op.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = allowMsg, onCheckedChange = { b -> allowMsg = b; settings.putOperatorMsgPermission(op.id, b) }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                    Switch(checked = allowDyn, onCheckedChange = { b -> allowDyn = b; settings.putOperatorDynPermission(op.id, b) }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = AccentOrange, checkedTrackColor = AccentOrange.copy(alpha = 0.2f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                }
                HorizontalDivider(color = Divider)
            }
        }
    }
}

@Composable
private fun GroupPermTab(groups: List<com.rhodes.privatechat.data.db.entity.ChatSessionEntity>, viewModel: MainViewModel) {
    val settings: SettingsRepository = koinInject()
    var eventEnabledCount by remember(groups) { mutableIntStateOf(groups.count { settings.getGroupEventAuto(it.id) }) }

    Column {
        Text("空闲自动聊天会按时间一直聊；大世界事件唤起只在动态、评论等事件发生后聊几轮。当前允许事件唤起的群：${eventEnabledCount} 个。", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("暂无群聊", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("群聊", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
                Text("空闲自动", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
                Text("事件唤起", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(64.dp))
            }
            LazyColumn {
                items(groups, key = { it.id }) { g ->
                    var idleAuto by remember(g.id) { mutableStateOf(settings.getGroupAuto(g.id)) }
                    var eventAuto by remember(g.id) { mutableStateOf(settings.getGroupEventAuto(g.id)) }
                    Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        OperatorAvatarImage(avatarUri = g.avatarUri, name = g.operatorName, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.operatorName, fontSize = 14.sp, color = TextPrimary)
                            Text("空闲自动 / 事件唤起", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = idleAuto, onCheckedChange = { b ->
                            idleAuto = b
                            settings.putGroupAuto(g.id, b)
                        }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = eventAuto, onCheckedChange = { b ->
                            if (eventAuto != b) eventEnabledCount += if (b) 1 else -1
                            eventAuto = b
                            settings.putGroupEventAuto(g.id, b)
                        }, colors = SwitchDefaults.colors(checkedThumbColor = AccentOrange, checkedTrackColor = AccentOrange.copy(alpha = 0.2f), uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
                    }
                    HorizontalDivider(color = Divider)
                }
            }
        }
    }
}
