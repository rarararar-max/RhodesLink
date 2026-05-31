package com.example.rhodesterminal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.rhodesterminal.data.db.entity.ChatSessionEntity
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import com.example.rhodesterminal.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun PermissionsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val groups = remember { sessions.filter { it.operatorId.startsWith("group_") } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs = listOf("干员", "群聊")
    var tabIndex by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("权限管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        TabRow(selectedTabIndex = tabIndex, containerColor = Surface, contentColor = Blue400) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tabIndex == i, onClick = { tabIndex = i }, text = { Text(title, fontWeight = if (tabIndex == i) FontWeight.SemiBold else FontWeight.Normal) })
            }
        }

        when (tabIndex) {
            0 -> OperatorPermTab(operators = operators)
            1 -> GroupPermTab(groups = groups)
        }
    }
}

@Composable
private fun OperatorPermTab(operators: List<com.example.rhodesterminal.data.db.entity.OperatorEntity>) {
    val settings: SettingsRepository = koinInject()

    Column {
        Text("批量设置干员的主动消息和自动动态权限", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("干员", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text("主动消息", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
            Text("自动动态", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(56.dp))
        }

        LazyColumn {
            items(operators) { op ->
                var allowMsg by remember(op.id) { mutableStateOf(settings.getOperatorMsgPermission(op.id)) }
                var allowDyn by remember(op.id) { mutableStateOf(settings.getOperatorDynPermission(op.id)) }
                Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (op.avatarUri.isNotBlank()) {
                        AsyncImage(model = op.avatarUri, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) { Text(op.name.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(op.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = allowMsg, onCheckedChange = { b -> allowMsg = b; settings.putOperatorMsgPermission(op.id, b) }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer))
                    Switch(checked = allowDyn, onCheckedChange = { b -> allowDyn = b; settings.putOperatorDynPermission(op.id, b) }, modifier = Modifier.width(56.dp), colors = SwitchDefaults.colors(checkedThumbColor = AccentOrange, checkedTrackColor = AccentOrange.copy(alpha = 0.2f)))
                }
                HorizontalDivider(color = Divider)
            }
        }
    }
}

@Composable
private fun GroupPermTab(groups: List<com.example.rhodesterminal.data.db.entity.ChatSessionEntity>) {
    val settings: SettingsRepository = koinInject()

    Column {
        Text("控制各群聊是否开启自动聊天（发送消息后自动触发干员闲聊）", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (groups.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("暂无群聊", fontSize = 14.sp, color = TextTertiary)
            }
        } else {
            LazyColumn {
                items(groups, key = { it.id }) { g ->
                    var autoSpeak by remember(g.id) { mutableStateOf(settings.getGroupAuto(g.id)) }
                    Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (g.avatarUri.isNotBlank()) {
                            AsyncImage(model = g.avatarUri, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(36.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                                Text(g.operatorName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.operatorName, fontSize = 14.sp, color = TextPrimary)
                            Text("自动聊天", fontSize = 11.sp, color = TextSecondary)
                        }
                        Switch(checked = autoSpeak, onCheckedChange = { b ->
                            autoSpeak = b
                            settings.putGroupAuto(g.id, b)
                        }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer))
                    }
                    HorizontalDivider(color = Divider)
                }
            }
        }
    }
}
