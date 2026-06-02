package com.rhodes.privatechat.ui.mahjong

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.game.mahjong.GameState
import com.rhodes.privatechat.game.mahjong.MahjongHistoryEntry
import com.rhodes.privatechat.game.mahjong.GameSerializer
import org.koin.compose.koinInject
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

private val json = Json { ignoreUnknownKeys = true }

@Composable
fun SelectScreen(
    operators: List<OperatorEntity>,
    userLmb: Int,
    userAvatarUri: String,
    userName: String,
    onBack: () -> Unit,
    onStart: (GameState) -> Unit
) {
    val opponentIds = remember { mutableStateListOf<String>() }
    var assistantId by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    val settings: SettingsRepository = koinInject()
    val lmbPrefs = settings
    val historyEntries = remember(showHistory) {
        if (!showHistory) emptyList<MahjongHistoryEntry>()
        else try {
            val jsonStr = settings.mahjongHistoryJson.ifBlank { "[]" }
            json.decodeFromString<List<MahjongHistoryEntry>>(jsonStr)
        } catch (_: Exception) { emptyList() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("选择对手", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = { showHistory = true }) { Icon(Icons.Default.History, "历史记录", tint = TextSecondary) }
        }
        HorizontalDivider(color = Divider)

        // 规则提示
        Text("日麻·简化版（一番起胡·宝牌）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(Primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp)).padding(12.dp))
        HorizontalDivider(color = Divider)

        // 对手选择
        Text("选择对手（${opponentIds.size}/3）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).fillMaxWidth().background(Surface).padding(8.dp)) {
            items(operators) { op ->
                val selected = opponentIds.contains(op.id)
                val opLmb = lmbPrefs.getInt(op.id, op.lmb)
                val disabled = opLmb < 100
                Column(modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Primary.copy(alpha = 0.15f) else if (disabled) Card else Color.Transparent)
                    .border(if (selected) 2.dp else 0.dp, if (selected) Primary else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(enabled = !disabled) {
                        if (selected) opponentIds.remove(op.id)
                        else if (opponentIds.size < 3) opponentIds.add(op.id)
                    }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                        if (op.avatarUri.isNotBlank()) {
                            AsyncImage(model = op.avatarUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Text(op.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Text(op.name.take(6), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (disabled) TextTertiary else TextPrimary, maxLines = 1, textAlign = TextAlign.Center)
                    Text("余额${opLmb}", fontSize = 9.sp, color = if (disabled) ErrorRed else TextSecondary)
                    if (disabled) Text("不足", fontSize = 9.sp, color = ErrorRed)
                    if (selected) Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(14.dp))
                }
            }
        }

        // 助手选择
        Text("选择助手（1名）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(8.dp).horizontalScroll(rememberScrollState())) {
            operators.filter { it.id !in opponentIds }.forEach { op ->
                val sel = assistantId == op.id
                Column(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (sel) Primary.copy(alpha = 0.15f) else Color.Transparent).clickable { assistantId = op.id }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                        if (op.avatarUri.isNotBlank()) AsyncImage(model = op.avatarUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Text(op.name.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(op.name.take(4), fontSize = 9.sp, color = TextPrimary)
                    if (sel) Text("已选", fontSize = 8.sp, color = Primary)
                }
            }
        }

        HorizontalDivider(color = Divider)

        // 底部
        Row(Modifier.fillMaxWidth().background(Surface).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("入场费：100龙门币", fontSize = 13.sp, color = TextSecondary)
                Text("你的余额：${userLmb}龙门币", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (userLmb >= 100) TextPrimary else ErrorRed)
            }
            Button(onClick = {
                if (opponentIds.size != 3 || assistantId.isBlank() || userLmb < 100) return@Button
                val ops = opponentIds.mapNotNull { id -> operators.find { it.id == id } }
                val styles = ops.map { Triple(it.attack, it.defense, it.meldPref) }
                val game = GameState.create(
                    opponentIds, ops.map { it.name }, styles,
                    "user", userName, assistantId
                )
                onStart(game)
            }, enabled = opponentIds.size == 3 && assistantId.isNotBlank() && userLmb >= 100,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                Text("开始牌局", fontWeight = FontWeight.SemiBold)
            }
        }
    }
    }

    // 历史记录弹窗
    if (showHistory) {
        AlertDialog(onDismissRequest = { showHistory = false }, title = { Text("对局历史", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = {
                if (historyEntries.isEmpty()) { Text("暂无对局记录", color = TextSecondary, modifier = Modifier.padding(16.dp)) }
                else {
                    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        itemsIndexed(historyEntries) { _, e ->
                            val rankColor = when (e.userRank) { 1 -> Color(0xFFFFD700); 2 -> Color(0xFFC0C0C0); 3 -> Color(0xFFCD7F32); else -> TextSecondary }
                            val medal = when (e.userRank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "  " }
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(medal, fontSize = 14.sp)
                                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(e.opponents.joinToString("、"), fontSize = 12.sp, color = TextPrimary, maxLines = 1)
                                    Text(sdf.format(Date(e.time)) + if (e.winType.isNotEmpty()) " · ${e.winType}" else "", fontSize = 10.sp, color = TextSecondary)
                                }
                                Text(if (e.userNetGain >= 0) "+${e.userNetGain}" else "${e.userNetGain}", fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold, color = if (e.userNetGain >= 0) Color(0xFF4CAF50) else Color(0xFFC62828))
                            }
                            HorizontalDivider(color = Divider.copy(alpha = 0.3f))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("关闭", color = Primary) } })
    }
}

