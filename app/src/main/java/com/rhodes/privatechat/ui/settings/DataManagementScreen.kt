package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.DataViewModel
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

data class CleanupItem(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val count: Int,
    val prefKey: String,
    val defaultDays: Int,
    val options: List<Pair<Int, String>> = listOf(
        3 to "3天", 7 to "7天", 14 to "14天",
        30 to "30天", 90 to "90天", -1 to "永不"
    )
)

@Composable
fun DataManagementScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onBackupRestore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    var stats by remember { mutableStateOf(DataViewModel.DataStats(0,0,0,0,0,0)) }
    var refreshKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    var showCleanupConfirm by remember { mutableStateOf(false) }
    var cleaningUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey) { stats = viewModel.getDataStats() }

    val items = remember(stats) {
        listOf(
            CleanupItem(Icons.Default.AutoDelete, "记忆锚点", stats.anchors, "clean_days_anchors", 7),
            CleanupItem(Icons.Default.Forum, "聊天记录", stats.messages, "clean_days_messages", 30),
            CleanupItem(Icons.AutoMirrored.Filled.MenuBook, "干员日记", stats.diaries, "clean_days_diaries", 30),
            CleanupItem(Icons.Default.Share, "动态记录", stats.moments, "clean_days_moments", 7),
            CleanupItem(Icons.AutoMirrored.Filled.SendToMobile, "派遣历史", stats.dispatches, "clean_days_dispatches", 30)
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
            Text("数据管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card)
                    .clickable(onClick = onBackupRestore).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Backup, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("备份与恢复", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("导出完整数据、迁移新设备、校验备份文件", fontSize = 12.sp, color = TextSecondary)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoDelete, null, tint = Primary, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("记忆来源说明", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("角色会在合适的场景下自然表达共同经历、群聊或公开动态等信息来源。", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            items.forEach { item ->
                val current = settings.getInt(item.prefKey, item.defaultDays)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(PrimaryContainer), contentAlignment = Alignment.Center) {
                            Icon(item.icon, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text("${item.count} 项", fontSize = 12.sp, color = TextSecondary)
                        }
                        val currentLabel = item.options.find { it.first == current }?.second ?: "${current}天"
                        Text(currentLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.options.forEach { (days, label) ->
                            val selected = current == days
                            Text(
                                label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Primary else TextSecondary,
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                     .background(if (selected) PrimaryContainer else Color.Transparent)
                                     .clickable {
                                         settings.putInt(item.prefKey, days)
                                         refreshKey++
                                         android.widget.Toast.makeText(context, "已保存新的保留规则", android.widget.Toast.LENGTH_SHORT).show()
                                     }.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { showCleanupConfirm = true }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("立即清理所有过期数据", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    }
    if (showCleanupConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanupConfirm = false },
            title = { Text("确认清理过期数据", color = TextPrimary) },
            text = { Text("将立即清理所有已超过当前保留期限的数据，此操作无法恢复。", color = TextSecondary) },
            confirmButton = { TextButton(onClick = {
                showCleanupConfirm = false
                cleaningUp = true
                scope.launch {
                    runCatching { viewModel.cleanupAllExpired() }
                        .onSuccess {
                            refreshKey++
                            android.widget.Toast.makeText(context, "已按当前规则清理过期数据", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { android.widget.Toast.makeText(context, "清理失败：${it.message ?: "请稍后重试"}", android.widget.Toast.LENGTH_LONG).show() }
                    cleaningUp = false
                }
            }) { Text("确认清理", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showCleanupConfirm = false }) { Text("取消", color = TextSecondary) } }
        )
    }
    if (cleaningUp) {
        AlertDialog(onDismissRequest = {}, title = { Text("正在清理") }, text = { Text("正在按当前保留规则清理过期数据，请稍候。", color = TextSecondary) }, confirmButton = {})
    }
}
