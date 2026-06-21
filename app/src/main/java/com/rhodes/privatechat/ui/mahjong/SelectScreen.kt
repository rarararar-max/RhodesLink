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
import androidx.compose.material.icons.filled.Casino
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
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.game.mahjong.GameState
import com.rhodes.privatechat.game.mahjong.MahjongHistoryEntry
import com.rhodes.privatechat.game.mahjong.MatchMode
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
    onStart: (GameState) -> Unit,
    onResume: ((GameState) -> Unit)? = null,
    savedGame: GameState? = null
) {
    val opponentIds = remember { mutableStateListOf<String>() }
    var assistantId by remember { mutableStateOf("") }
    var matchMode by remember { mutableStateOf(MatchMode.QUICK) }
    var showHistory by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val settings: SettingsRepository = koinInject()
    val lmbPrefs = settings
    val canSeatTable = operators.size >= 4
    val historyEntries = remember(showHistory) {
        if (!showHistory) emptyList<MahjongHistoryEntry>()
        else try {
            val jsonStr = settings.mahjongHistoryJson.ifBlank { "[]" }
            json.decodeFromString<List<MahjongHistoryEntry>>(jsonStr)
        } catch (_: Exception) { emptyList() }
    }
    LaunchedEffect(opponentIds.toList(), operators) {
        if (assistantId in opponentIds || operators.none { it.id == assistantId }) {
            assistantId = operators.firstOrNull { it.id !in opponentIds }?.id.orEmpty()
        }
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(Color(0xFF101820), RoundedCornerShape(12.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Casino, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("活动室麻将", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD54F))
            }
            Spacer(Modifier.height(4.dp))
            val ruleDesc = when (matchMode) {
                MatchMode.QUICK -> "基础麻将：四组牌加一对将即可胡，支持自摸、点炮、吃碰杠、七对、对对胡、清一色。快速一局，结算写入锚点、动态和龙门币变化。"
                MatchMode.EAST -> "四局积分战，基础胡牌规则，累计筹码排名。庄家胡牌可连庄，每局结算后进入下一局。"
                MatchMode.HALF -> "八局积分战，基础胡牌规则，适合更完整的活动室对局。"
            }
            Text(ruleDesc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f), lineHeight = 17.sp)
            Text("龙门币只按结算输赢变化；干员次日会自动保底补到2000，防止大家破产散桌。", fontSize = 11.sp, color = Color(0xFFFFD54F).copy(alpha = 0.82f), lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MatchMode.QUICK to "快速一局", MatchMode.EAST to "东风战", MatchMode.HALF to "半庄").forEach { (mode, label) ->
                    val selected = matchMode == mode
                    Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.1f)).clickable { matchMode = mode }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f))
                    }
                }
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.1f)).clickable { showRules = true }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("规则", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        HorizontalDivider(color = Divider)

        // 对手选择
        Text("选择对手（${opponentIds.size}/3）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).fillMaxWidth().background(Surface).padding(8.dp)) {
            items(operators) { op ->
                val selected = opponentIds.contains(op.id)
                Column(modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent)
                    .border(if (selected) 2.dp else 0.dp, if (selected) Primary else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable {
                        if (selected) opponentIds.remove(op.id)
                        else if (opponentIds.size < 3) {
                            opponentIds.add(op.id)
                            if (assistantId == op.id) assistantId = operators.firstOrNull { it.id !in opponentIds }?.id.orEmpty()
                        }
                    }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(44.dp))
                    Text(op.name.take(6), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, textAlign = TextAlign.Center)
                    Text("龙门币${op.lmb}", fontSize = 9.sp, color = TextSecondary)
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
                    OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(36.dp))
                    Text(op.name.take(4), fontSize = 9.sp, color = TextPrimary)
                    if (sel) Text("已选", fontSize = 8.sp, color = Primary)
                }
            }
        }

        HorizontalDivider(color = Divider)

        // 继续上次牌局
        if (savedGame != null && onResume != null) {
            val savedPlayers = savedGame.players.filter { !it.isHuman }.joinToString("、") { it.name }
            val savedRound = savedGame.roundLabel()
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF4CAF50).copy(alpha = 0.12f)).clickable { onResume(savedGame) }.padding(12.dp)) {
                Text("继续上次牌局", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4CAF50))
                Text("${savedRound} · 对手：${savedPlayers}", fontSize = 11.sp, color = TextSecondary)
            }
            HorizontalDivider(color = Divider)
        }

        // 底部
        Row(Modifier.fillMaxWidth().background(Surface).padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("${when(matchMode){MatchMode.QUICK->"快速一局";MatchMode.EAST->"东风战";MatchMode.HALF->"半庄"}} · 结算后计入龙门币", fontSize = 13.sp, color = TextSecondary)
                Text("你的龙门币：${userLmb}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            OutlinedButton(onClick = {
                val pool = operators.filter { op -> op.id != assistantId }.shuffled()
                opponentIds.clear()
                opponentIds.addAll(pool.take(3).map { it.id })
                if (assistantId.isBlank() || assistantId in opponentIds) assistantId = operators.firstOrNull { it.id !in opponentIds }?.id.orEmpty()
            }, enabled = canSeatTable) { Text("随机组桌", fontSize = 12.sp) }
            Button(onClick = {
                if (opponentIds.size != 3 || assistantId.isBlank() || assistantId in opponentIds || operators.none { it.id == assistantId }) return@Button
                val ops = opponentIds.mapNotNull { id -> operators.find { it.id == id } }
                val styles = ops.map { Triple(it.attack, it.defense, it.meldPref) }
                val game = GameState.create(
                    opponentIds, ops.map { it.name }, styles,
                    "user", userName, assistantId, matchMode
                )
                onStart(game)
            }, enabled = opponentIds.size == 3 && assistantId.isNotBlank() && assistantId !in opponentIds && operators.any { it.id == assistantId },
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
                    val sdf = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }
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
    if (showRules) {
        BasicMahjongRuleDialog(onDismiss = { showRules = false })
    }
}

