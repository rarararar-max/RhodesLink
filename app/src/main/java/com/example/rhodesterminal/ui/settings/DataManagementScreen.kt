package com.example.rhodesterminal.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.viewmodel.MainViewModel

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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chat_prefs", 0) }
    var stats by remember { mutableStateOf(MainViewModel.DataStats(0,0,0,0,0,0)) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) { stats = viewModel.getDataStats() }

    val items = remember(stats) {
        listOf(
            CleanupItem(Icons.Default.AutoDelete, "记忆锚点", stats.anchors, "clean_days_anchors", 3),
            CleanupItem(Icons.Default.Forum, "聊天摘要", stats.messages, "clean_days_messages", 30),
            CleanupItem(Icons.Default.MenuBook, "干员日记", stats.diaries, "clean_days_diaries", 30),
            CleanupItem(Icons.Default.Share, "动态记录", stats.moments, "clean_days_moments", 30),
            CleanupItem(Icons.Default.SendToMobile, "派遣历史", stats.dispatches, "clean_days_dispatches", 30)
        )
    }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(Modifier.width(4.dp))
            Text("数据管理", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            items.forEach { item ->
                val current = prefs.getInt(item.prefKey, item.defaultDays)
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
                                        prefs.edit().putInt(item.prefKey, days).apply()
                                        viewModel.cleanupAllExpired()
                                        refreshKey++
                                    }.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                viewModel.cleanupAllExpired()
                refreshKey++
            }, modifier = Modifier.fillMaxWidth().height(44.dp), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("立即清理所有过期数据", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
