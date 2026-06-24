package com.rhodes.privatechat.ui.gameroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*
import kotlin.random.Random

private val RoomGreen = Color(0xFF173A2E)
private val CardRed = Color(0xFFC62828)
private val CardBlack = Color(0xFF263238)

data class PokerOpponent(val id: String, val name: String, val avatarUri: String)

@Composable
fun GameRoomScreen(
    onBack: () -> Unit,
    onMahjong: () -> Unit,
    onLandlord: () -> Unit,
    onRunFast: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("游戏室", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("和干员们玩点轻松的", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("这里都是简化娱乐规则，重点是互动和稳定，不做复杂竞技判定。", fontSize = 13.sp, color = TextSecondary, lineHeight = 19.sp)
            GameEntryCard("基础麻将", "四组牌加一对将，支持胡、碰、吃、杠。", "麻将", Color(0xFFFF8F00), onMahjong)
            GameEntryCard("斗地主", "支持三带、顺子、连对、炸弹和王炸。", "扑克", Color(0xFF42A5F5), onLandlord)
            GameEntryCard("跑得快", "支持三带、顺子、连对、炸弹，先出完赢。", "扑克", Color(0xFF66BB6A), onRunFast)
        }
    }
}

@Composable
fun PokerSelectScreen(
    mode: PokerMode,
    operators: List<OperatorEntity>,
    onBack: () -> Unit,
    onStart: (List<PokerOpponent>) -> Unit
) {
    val selected = remember { mutableStateListOf<String>() }
    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text("选择对手", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("${mode.title} · 选择2名干员", fontSize = 11.sp, color = TextSecondary)
            }
            Text("随机", fontSize = 12.sp, color = Primary, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                selected.clear(); selected.addAll(operators.shuffled().take(2).map { it.id })
            }.background(Primary.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        HorizontalDivider(color = Divider)
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).background(Surface).padding(8.dp)) {
            items(operators) { op ->
                val isSelected = op.id in selected
                Column(Modifier.clip(RoundedCornerShape(10.dp)).background(if (isSelected) Primary.copy(alpha = 0.14f) else Color.Transparent).border(if (isSelected) 2.dp else 0.dp, if (isSelected) Primary else Color.Transparent, RoundedCornerShape(10.dp)).clickable {
                    if (isSelected) selected.remove(op.id) else if (selected.size < 2) selected.add(op.id)
                }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OperatorAvatarImage(op.avatarUri, op.name, Modifier.size(46.dp))
                    Text(op.name.take(6), fontSize = 11.sp, color = TextPrimary, maxLines = 1, textAlign = TextAlign.Center)
                    if (isSelected) Text("已选", fontSize = 9.sp, color = Primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Button(
            onClick = {
                val opponents = selected.mapNotNull { id -> operators.find { it.id == id } }.map { PokerOpponent(it.id, it.name, it.avatarUri) }
                if (opponents.size == 2) onStart(opponents)
            },
            enabled = selected.size == 2,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) { Text("开始${mode.title}", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun GameEntryCard(title: String, desc: String, tag: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).border(1.dp, color.copy(alpha = 0.24f), RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Icon(if (tag == "麻将") Icons.Default.Casino else Icons.Default.Style, null, tint = color, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(desc, fontSize = 12.sp, color = TextSecondary, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(tag, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private data class PokerCard(val rank: Int, val suit: String) {
    val label: String get() = "${rankLabel(rank)}$suit"
}

private fun rankLabel(rank: Int): String = when (rank) {
    11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; 15 -> "2"; 16 -> "小王"; 17 -> "大王"; else -> rank.toString()
}

private fun pokerDeck(includeJokers: Boolean = true): MutableList<PokerCard> {
    val deck = mutableListOf<PokerCard>()
    val suits = listOf("♠", "♥", "♣", "♦")
    for (rank in 3..15) suits.forEach { deck.add(PokerCard(rank, it)) }
    if (includeJokers) { deck.add(PokerCard(16, "")); deck.add(PokerCard(17, "")) }
    return deck
}

@Composable
fun SimplePokerGameScreen(mode: PokerMode, opponents: List<PokerOpponent>, userName: String, userAvatarUri: String, balance: Int, onBack: () -> Unit, onSettle: (Int) -> Unit, onGenerateTalk: ((String, String, String, String, List<String>, String, (String) -> Unit) -> Unit)? = null) {
    var game by remember(mode, opponents) { mutableStateOf(SimplePokerState.newGame(mode, opponents.map { it.name })) }
    var selected by remember(game.players.getOrNull(0)?.hand) { mutableStateOf(setOf<Int>()) }
    var settled by remember(game.finished, game.userNetGain) { mutableStateOf(false) }
    var showExitDialog by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var generatedTalkEventId by remember { mutableIntStateOf(-1) }
    var talkBubble by remember { mutableStateOf<PokerTalkBubble?>(null) }
    val displayBalance = balance + if (settled && game.finished) game.userNetGain else 0
    val recentTalk = remember(game.history, game.tableTalk) { game.history.flatten().takeLast(6).map { it.label } + listOf(game.tableTalk).filter { it.isNotBlank() } }
    val avatarMap = remember(opponents, userAvatarUri) { opponents.associate { it.name to it.avatarUri } + ("你" to userAvatarUri) + (userName to userAvatarUri) }
    LaunchedEffect(game.finished, game.userNetGain) {
        if (game.finished && !settled) {
            settled = true
            onSettle(game.userNetGain)
        }
    }
    Column(Modifier.fillMaxSize().background(RoomGreen).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.24f)).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showExitDialog = true }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(mode.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(mode.subtitle, fontSize = 11.sp, color = Color.White.copy(alpha = 0.62f))
            }
            Text("余额 $displayBalance", fontSize = 11.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
            Text("规则", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)).clickable { showRules = true }.padding(horizontal = 10.dp, vertical = 6.dp))
            Text("重开", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)).clickable { game = SimplePokerState.newGame(mode, opponents.map { it.name }); selected = emptySet(); settled = false; generatedTalkEventId = -1; talkBubble = null }.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        LaunchedEffect(game.turn, game.finished) {
            if (!game.finished && game.turn != 0) {
                kotlinx.coroutines.delay(650)
                game = game.autoUntilUser()
            }
        }
        LaunchedEffect(game.talkEventId) {
            if (game.talkEventId <= 0 || generatedTalkEventId == game.talkEventId) return@LaunchedEffect
            val speaker = game.tableTalk.substringBefore("：", "").ifBlank { game.players.getOrNull(game.lastPlayer)?.name.orEmpty() }
            if (speaker.isBlank()) return@LaunchedEffect
            if (speaker == "你" || game.tableTalk.isBlank()) return@LaunchedEffect
            generatedTalkEventId = game.talkEventId
            val fallback = game.tableTalk.substringAfter("：", game.tableTalk)
            val speakerRole = game.players.firstOrNull { it.name == speaker }?.role.orEmpty()
            val landlord = game.players.getOrNull(game.landlordIndex)?.name.orEmpty()
            val tableInfo = buildString {
                append(game.lastCombo?.label ?: "刚开局")
                if (speakerRole.isNotBlank()) append("；$speaker 是$speakerRole")
                if (landlord.isNotBlank()) append("；地主是$landlord")
                if (game.mode == PokerMode.LANDLORD) append("；当前倍率x${game.multiplier}")
            }
            talkBubble = PokerTalkBubble(speaker, fallback, game.talkEventId)
            onGenerateTalk?.invoke(speaker, mode.title, game.message, tableInfo, recentTalk, fallback) { generated ->
                if (generated.isNotBlank() && game.talkEventId == generatedTalkEventId) {
                    game = game.copy(tableTalk = "$speaker：$generated")
                    talkBubble = PokerTalkBubble(speaker, generated, generatedTalkEventId)
                }
            }
            kotlinx.coroutines.delay(5200)
            if (talkBubble?.eventId == generatedTalkEventId) talkBubble = null
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PokerRulesCard(mode)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val leftPlayer = game.players.getOrNull(1)
                val rightPlayer = game.players.getOrNull(2)
                if (leftPlayer != null) OpponentPokerPanel(leftPlayer, game.turn == 1, game.lastPlay.getOrNull(1), game.history.getOrNull(1).orEmpty(), avatarMap[leftPlayer.name].orEmpty(), Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                if (rightPlayer != null) OpponentPokerPanel(rightPlayer, game.turn == 2, game.lastPlay.getOrNull(2), game.history.getOrNull(2).orEmpty(), avatarMap[rightPlayer.name].orEmpty(), Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            }
            val userPlayer = game.players.getOrNull(0)
            if (userPlayer != null) {
            TableInfo(game, avatarMap, Modifier.weight(1f).fillMaxWidth())
            UserPokerPanel(
                player = userPlayer,
                active = game.turn == 0,
                avatarUri = avatarMap["你"].orEmpty(),
                history = game.history.getOrNull(0).orEmpty(),
                selected = selected,
                onToggle = if (game.turn == 0 && !game.finished) { i -> selected = if (i in selected) selected - i else selected + i } else null
            )
            }
            if (game.finished) {
                Text("本局结算：${if (game.userNetGain >= 0) "+" else ""}${game.userNetGain}龙门币", fontSize = 16.sp, color = if (game.userNetGain >= 0) Color(0xFFFFD54F) else Color(0xFFFF8A80), fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { game = game.playUserSelected(selected.toList()); selected = emptySet() }, enabled = !game.finished && game.turn == 0 && selected.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))) { Text("出牌") }
                OutlinedButton(onClick = { selected = emptySet() }, enabled = selected.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("取消") }
                OutlinedButton(onClick = { game = game.passUser(); selected = emptySet() }, enabled = !game.finished && game.turn == 0 && game.lastCombo != null, modifier = Modifier.weight(1f)) { Text("过") }
            }
            if (!game.finished && game.turn != 0) {
                Text("${game.players.getOrNull(game.turn)?.name ?: "对手"}正在思考...", fontSize = 12.sp, color = Color.White.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        talkBubble?.let { PokerTalkBubbleOverlay(it, game.players, avatarMap) }
        }
        if (game.finished) {
            PokerSettlementDialog(game, avatarMap, mode.title, recentTalk, onGenerateTalk, onBack = onBack, onAgain = { game = SimplePokerState.newGame(mode, opponents.map { it.name }); selected = emptySet(); settled = false; generatedTalkEventId = -1; talkBubble = null })
        }
        if (showExitDialog) {
            AlertDialog(
                onDismissRequest = { showExitDialog = false },
                title = { Text("退出牌局", fontWeight = FontWeight.SemiBold, color = TextPrimary) },
                text = { Text("当前扑克局不会保存，确定退出？", color = TextSecondary) },
                confirmButton = { TextButton(onClick = { showExitDialog = false; onBack() }) { Text("退出", color = ErrorRed) } },
                dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("继续", color = Primary) } }
            )
        }
        if (showRules) PokerRuleDialog(mode, onDismiss = { showRules = false })
    }
}

@Composable
private fun PokerRuleDialog(mode: PokerMode, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101C18),
        title = { Text("${mode.title}规则", fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("牌型", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("单张、对子、三张、三带一、三带二、顺子、连对、炸弹${if (mode == PokerMode.LANDLORD) "、王炸" else ""}。", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("怎么压牌", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("通常要同牌型、同长度并且点数更大。炸弹可以压普通牌。两家都过后，上一手出牌的人重新出牌。", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
                if (mode == PokerMode.LANDLORD) {
                    Spacer(Modifier.height(8.dp))
                    Text("斗地主结算", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("随机地主，地主拿3张底牌并先出。基础分100，地主赢两份、农民各输一份；农民赢各赢一份、地主输两份。炸弹/王炸翻倍，春天/反春天再翻倍。", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("页面说明", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("上方能看到对手头像、剩余牌数、刚出牌和最近出牌历史。中间桌面显示当前牌和AI发言。底部点击自己的牌选择，点出牌即可。", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), lineHeight = 18.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了", color = Color(0xFFFFD54F)) } }
    )
}

@Composable
private fun PokerSettlementDialog(game: SimplePokerState, avatarMap: Map<String, String>, gameName: String, recentTalk: List<String>, onGenerateTalk: ((String, String, String, String, List<String>, String, (String) -> Unit) -> Unit)?, onBack: () -> Unit, onAgain: () -> Unit) {
    val userWin = game.winnerIndex == 0
    val fixedLines = remember(game.winnerIndex) {
        game.players.associate { p ->
            p.name to when {
                p.name == "你" -> ""
                game.players.indexOf(p) == game.winnerIndex -> "这局我先收下了，别不服。"
                userWin -> "博士这把打得挺稳，下局我会认真点。"
                else -> "这把没接住，下一局再来。"
            }
        }
    }
    val generatedLines = remember(game.winnerIndex) { mutableStateMapOf<String, String>() }
    LaunchedEffect(game.winnerIndex) {
        game.players.filter { it.name != "你" }.forEach { p ->
            val fallback = fixedLines[p.name].orEmpty()
            onGenerateTalk?.invoke(p.name, gameName, "牌局结算", "赢家：${game.players.getOrNull(game.winnerIndex)?.name ?: "未知"}，博士净收益${game.userNetGain}龙门币", recentTalk, fallback) { generated ->
                if (generated.isNotBlank()) generatedLines[p.name] = generated
            }
        }
    }
    AlertDialog(
        onDismissRequest = {},
        containerColor = Color(0xFF101C18),
        title = { Text(if (userWin) "你赢了" else "牌局结束", fontWeight = FontWeight.Bold, color = if (userWin) Color(0xFFFFD54F) else Color.White) },
        text = {
            Column {
                val winner = game.players.getOrNull(game.winnerIndex)?.name ?: "未知"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OperatorAvatarImage(avatarMap[winner].orEmpty(), winner, Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("赢家：$winner", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (game.winnerIndex == 0) "博士这把打得漂亮。" else (generatedLines[winner] ?: "$winner：这局我先收下了。"), fontSize = 12.sp, color = Color.White.copy(alpha = 0.68f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("龙门币：${if (game.userNetGain >= 0) "+" else ""}${game.userNetGain}", fontSize = 16.sp, color = if (game.userNetGain >= 0) Color(0xFFFFD54F) else Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                if (game.mode == PokerMode.LANDLORD) {
                    Spacer(Modifier.height(8.dp))
                    val landlord = game.players.getOrNull(game.landlordIndex)?.name ?: "未知"
                    val multiplierText = buildString {
                        append("x${game.multiplier * (if (game.springType.isNotBlank()) 2 else 1)}")
                        if (game.bombCount > 0) append(" · 炸弹${game.bombCount}次")
                        if (game.springType.isNotBlank()) append(" · ${game.springType}")
                    }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.07f)).padding(10.dp)) {
                        Text("地主：$landlord", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        Text("基础分：${game.baseScore} · 倍率：$multiplierText", fontSize = 12.sp, color = Color(0xFFFFD54F))
                        if (game.bottomCards.isNotEmpty()) Text("底牌：${game.bottomCards.joinToString("、") { it.label }}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.62f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                game.players.forEachIndexed { i, p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (i == game.winnerIndex) "★" else " ", color = Color(0xFFFFD54F), modifier = Modifier.width(18.dp))
                        OperatorAvatarImage(avatarMap[p.name].orEmpty(), p.name, Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        val gain = game.settlement.getOrNull(i) ?: 0
                        Text(if (game.mode == PokerMode.LANDLORD) "${if (gain >= 0) "+" else ""}$gain" else "剩${p.hand.size}张", color = if (gain >= 0) Color(0xFFFFD54F) else Color(0xFFFF8A80), fontSize = 12.sp)
                    }
                    if (p.name != "你") {
                        Text("${p.name}：${generatedLines[p.name] ?: fixedLines[p.name].orEmpty()}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.62f), lineHeight = 15.sp, modifier = Modifier.padding(start = 50.dp, bottom = 4.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(if (userWin) "对面已经记住这把了。" else "下把再把龙门币赢回来。", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            }
        },
        confirmButton = { TextButton(onClick = onAgain) { Text("再来一局", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onBack) { Text("返回游戏室", color = Color.White) } }
    )
}

enum class PokerMode(val title: String, val subtitle: String) {
    LANDLORD("斗地主", "地主拿3张底牌，炸弹和春天翻倍"),
    RUN_FAST("跑得快", "三人局，支持顺子、连对、三带、炸弹，能压就压")
}

private data class SimplePokerPlayer(val name: String, val hand: List<PokerCard>, val role: String = "")
private enum class ComboType { SINGLE, PAIR, TRIPLE, TRIPLE_ONE, TRIPLE_PAIR, STRAIGHT, PAIR_CHAIN, BOMB }
private data class PokerCombo(val type: ComboType, val rank: Int, val cards: List<PokerCard>) {
    val length: Int get() = cards.size
    val label: String get() = when (type) {
        ComboType.SINGLE -> "单张 ${cards.first().label}"
        ComboType.PAIR -> "对子 ${rankLabel(rank)}"
        ComboType.TRIPLE -> "三张 ${rankLabel(rank)}"
        ComboType.TRIPLE_ONE -> "三带一 ${rankLabel(rank)}"
        ComboType.TRIPLE_PAIR -> "三带二 ${rankLabel(rank)}"
        ComboType.STRAIGHT -> "顺子 ${cards.size}张到${rankLabel(rank)}"
        ComboType.PAIR_CHAIN -> "连对 ${cards.size / 2}连到${rankLabel(rank)}"
        ComboType.BOMB -> if (cards.size == 2) "王炸" else "炸弹 ${rankLabel(rank)}"
    }
}
private data class PokerHistoryEntry(val player: String, val combo: PokerCombo, val label: String)
private data class PokerTalkBubble(val speaker: String, val text: String, val eventId: Int)

private data class SimplePokerState(
    val mode: PokerMode,
    val players: List<SimplePokerPlayer>,
    val turn: Int,
    val lastCombo: PokerCombo?,
    val lastPlayer: Int,
    val passCount: Int,
    val lastPlay: List<PokerCombo?>,
    val history: List<List<PokerHistoryEntry>>,
    val message: String,
    val tableTalk: String = "",
    val talkEventId: Int = 0,
    val finished: Boolean = false,
    val winnerIndex: Int = -1,
    val userNetGain: Int = 0,
    val landlordIndex: Int = -1,
    val bottomCards: List<PokerCard> = emptyList(),
    val baseScore: Int = 100,
    val multiplier: Int = 1,
    val bombCount: Int = 0,
    val springType: String = "",
    val playerPlayCounts: List<Int> = listOf(0, 0, 0),
    val settlement: List<Int> = listOf(0, 0, 0)
) {
    companion object {
        fun newGame(mode: PokerMode, opponentNames: List<String> = emptyList()): SimplePokerState {
            val deck = pokerDeck(includeJokers = mode == PokerMode.LANDLORD).apply { shuffle() }
            val fallback = if (mode == PokerMode.LANDLORD) listOf("能天使", "德克萨斯") else listOf("可颂", "空")
            val names = listOf("你") + List(2) { opponentNames.getOrNull(it) ?: fallback[it] }
            val hands = List(3) { mutableListOf<PokerCard>() }
            val landlord = if (mode == PokerMode.LANDLORD) Random.nextInt(3) else -1
            val bottomCards = if (mode == PokerMode.LANDLORD) deck.takeLast(3) else emptyList()
            val dealCards = if (mode == PokerMode.LANDLORD) deck.dropLast(3) else deck
            dealCards.forEachIndexed { i, card -> hands[i % 3].add(card) }
            if (landlord >= 0) hands[landlord].addAll(bottomCards)
            val players = names.mapIndexed { i, n ->
                SimplePokerPlayer(n, hands[i].sortedWith(compareBy<PokerCard> { it.rank }.thenBy { it.suit }), if (i == landlord) "地主" else if (mode == PokerMode.LANDLORD) "农民" else "")
            }
            val first = if (landlord >= 0) landlord else 0
            val bottomText = if (mode == PokerMode.LANDLORD) "，底牌${bottomCards.joinToString("、") { it.label }}归${players[first].name}" else ""
            return SimplePokerState(mode, players, first, null, -1, 0, listOf(null, null, null), List(3) { emptyList() }, "${players[first].name}先出牌$bottomText", "先说好，输了别赖账。", landlordIndex = landlord, bottomCards = bottomCards)
        }
    }

    fun playUserSelected(indices: List<Int>): SimplePokerState {
        if (finished || turn != 0) return this
        val hand = players.getOrNull(0)?.hand ?: return this
        val cards = indices.distinct().sorted().mapNotNull { hand.getOrNull(it) }
        val commenter = players.drop(1).randomOrNull()?.name ?: "系统"
        val calmCommenter = players.getOrNull(2)?.name ?: commenter
        val combo = detectCombo(cards) ?: return copy(message = "这组牌不能这样出。支持单张、对子、三张、三带、顺子、连对、炸弹。", tableTalk = "$commenter：博士，这牌型不对吧？", lastPlayer = players.indexOfFirst { it.name == commenter }.takeIf { it >= 0 } ?: 1, talkEventId = talkEventId + 1)
        if (!canBeat(combo, lastCombo)) return copy(message = "${combo.label} 压不过上家。", tableTalk = "$calmCommenter：这手不够大，先别急。", talkEventId = talkEventId + 1)
        return playCombo(0, combo)
    }

    fun passUser(): SimplePokerState = pass(0)

    fun autoUntilUser(): SimplePokerState {
        var s = this
        var guard = 0
        while (!s.finished && s.turn != 0 && guard++ < 8) s = s.aiStep()
        return s
    }

    private fun aiStep(): SimplePokerState {
        if (finished || turn == 0) return this
        val player = players.getOrNull(turn) ?: return copy(turn = 0)
        val combo = findAiCombo(player.hand, lastCombo)
        val lead = if (lastCombo == null) smallestLead(player.hand) else combo
        return if (lead == null && lastCombo != null) pass(turn) else if (lead != null) playCombo(turn, lead) else copy(turn = 0)
    }

    private fun pass(playerIndex: Int): SimplePokerState {
        if (finished || playerIndex != turn || lastCombo == null) return this
        val player = players.getOrNull(playerIndex) ?: return copy(turn = 0)
        val nextPass = passCount + 1
        val next = (playerIndex + 1) % players.size
        return if (nextPass >= 2) {
            val lead = if (lastPlayer >= 0) lastPlayer else next
            copy(turn = lead, lastCombo = null, passCount = 0, message = "两家都过，${players.getOrNull(lead)?.name ?: "对手"}重新出牌", tableTalk = "${player.name}：过，这手先让你。", talkEventId = talkEventId + 1)
        } else {
            copy(turn = next, passCount = nextPass, message = "${player.name}选择过牌，轮到${players.getOrNull(next)?.name ?: "对手"}", tableTalk = "${player.name}：过。", talkEventId = talkEventId + 1)
        }
    }

    private fun playCombo(playerIndex: Int, combo: PokerCombo): SimplePokerState {
        if (finished || playerIndex != turn) return this
        val player = players.getOrNull(playerIndex) ?: return copy(turn = 0)
        val newHand = player.hand.toMutableList()
        if (!combo.cards.all { newHand.contains(it) }) return copy(message = "选牌状态已变化，请重新选择。")
        combo.cards.forEach { newHand.remove(it) }
        val newPlayers = players.toMutableList().also { it[playerIndex] = player.copy(hand = newHand) }
        val won = newHand.isEmpty()
        val newLast = lastPlay.toMutableList().also { it[playerIndex] = combo }
        val newHistory = history.toMutableList().also { rows ->
            rows[playerIndex] = rows[playerIndex] + PokerHistoryEntry(player.name, combo, "${player.name}：${combo.label}")
        }
        val next = (playerIndex + 1) % players.size
        val nextPlayCounts = playerPlayCounts.toMutableList().also { if (playerIndex in it.indices) it[playerIndex] = it[playerIndex] + 1 }
        val bombed = mode == PokerMode.LANDLORD && combo.type == ComboType.BOMB
        val nextBombCount = bombCount + if (bombed) 1 else 0
        val nextMultiplier = multiplier * if (bombed) 2 else 1
        val finalSettlement = if (won) calculateSettlement(playerIndex, nextPlayCounts, nextMultiplier) else settlement to ""
        val net = if (won) finalSettlement.first.getOrNull(0) ?: 0 else 0
        val msg = if (won) {
            val side = if (mode == PokerMode.LANDLORD && playerIndex == landlordIndex) "地主" else if (mode == PokerMode.LANDLORD) "农民" else player.name
            "${player.name}出完了，${side}获胜！"
        } else {
            val bombText = if (bombed) "，倍率x$nextMultiplier" else ""
            "${player.name}出了 ${combo.label}$bombText，轮到${players.getOrNull(next)?.name ?: "对手"}"
        }
        val talk = when {
            won && playerIndex == 0 -> "${players.drop(1).randomOrNull()?.name ?: "系统"}：博士这把可以啊，龙门币拿走！"
            won -> "${player.name}：承让承让，这局我收下了。"
            combo.type == ComboType.BOMB -> "${player.name}：炸一下，别怪我。"
            combo.type == ComboType.PAIR -> "${player.name}：对子压上。"
            combo.type == ComboType.STRAIGHT -> "${player.name}：顺子，走一串。"
            combo.type == ComboType.PAIR_CHAIN -> "${player.name}：连对，别接不上。"
            else -> "${player.name}：我出${combo.cards.first().label}。"
        }
        return copy(players = newPlayers, turn = next, lastCombo = combo, lastPlayer = playerIndex, passCount = 0, lastPlay = newLast, history = newHistory, message = msg, tableTalk = talk, talkEventId = talkEventId + 1, finished = won, winnerIndex = if (won) playerIndex else -1, userNetGain = net, multiplier = nextMultiplier, bombCount = nextBombCount, springType = if (won) finalSettlement.second else "", playerPlayCounts = nextPlayCounts, settlement = if (won) finalSettlement.first else settlement)
    }

    private fun calculateSettlement(winnerIndex: Int, playCounts: List<Int>, currentMultiplier: Int): Pair<List<Int>, String> {
        if (mode != PokerMode.LANDLORD || landlordIndex !in players.indices) {
            val userGain = if (winnerIndex == 0) 200 else -120
            return listOf(userGain, if (winnerIndex == 1) 200 else 0, if (winnerIndex == 2) 200 else 0) to ""
        }
        val landlordWin = winnerIndex == landlordIndex
        val spring = when {
            landlordWin && playCounts.withIndex().filter { it.index != landlordIndex }.all { it.value == 0 } -> "春天"
            !landlordWin && playCounts.getOrElse(landlordIndex) { 0 } <= 1 -> "反春天"
            else -> ""
        }
        val finalMultiplier = currentMultiplier * if (spring.isNotBlank()) 2 else 1
        val gains = MutableList(3) { 0 }
        for (i in gains.indices) {
            gains[i] = when {
                i == landlordIndex && landlordWin -> 2 * baseScore * finalMultiplier
                i == landlordIndex -> -2 * baseScore * finalMultiplier
                landlordWin -> -baseScore * finalMultiplier
                else -> baseScore * finalMultiplier
            }
        }
        return gains to spring
    }

    private fun detectCombo(cards: List<PokerCard>): PokerCombo? {
        if (cards.isEmpty()) return null
        val sorted = cards.sortedBy { it.rank }
        val ranks = sorted.map { it.rank }
        return when {
            cards.size == 1 -> PokerCombo(ComboType.SINGLE, sorted.first().rank, sorted)
            cards.size == 2 && ranks.containsAll(listOf(16, 17)) -> PokerCombo(ComboType.BOMB, 99, sorted)
            cards.size == 2 && ranks.distinct().size == 1 -> PokerCombo(ComboType.PAIR, ranks.first(), sorted)
            cards.size == 3 && ranks.distinct().size == 1 -> PokerCombo(ComboType.TRIPLE, ranks.first(), sorted)
            cards.size == 4 && ranks.distinct().size == 1 -> PokerCombo(ComboType.BOMB, ranks.first(), sorted)
            cards.size == 4 && ranks.groupingBy { it }.eachCount().values.sorted() == listOf(1, 3) -> {
                val main = ranks.groupingBy { it }.eachCount().entries.first { it.value == 3 }.key
                PokerCombo(ComboType.TRIPLE_ONE, main, sorted)
            }
            cards.size == 5 && ranks.groupingBy { it }.eachCount().values.sorted() == listOf(2, 3) -> {
                val main = ranks.groupingBy { it }.eachCount().entries.first { it.value == 3 }.key
                PokerCombo(ComboType.TRIPLE_PAIR, main, sorted)
            }
            cards.size >= 5 && ranks.all { it < 15 } && ranks.distinct().size == ranks.size && ranks.zipWithNext().all { it.second == it.first + 1 } -> PokerCombo(ComboType.STRAIGHT, ranks.last(), sorted)
            cards.size >= 6 && cards.size % 2 == 0 && ranks.all { it < 15 } && ranks.groupingBy { it }.eachCount().values.all { it == 2 } && ranks.distinct().zipWithNext().all { it.second == it.first + 1 } -> PokerCombo(ComboType.PAIR_CHAIN, ranks.distinct().last(), sorted)
            else -> null
        }
    }

    private fun canBeat(combo: PokerCombo, target: PokerCombo?): Boolean {
        if (target == null) return true
        if (combo.type == ComboType.BOMB && target.type != ComboType.BOMB) return true
        if (combo.type != target.type) return false
        if ((combo.type == ComboType.STRAIGHT || combo.type == ComboType.PAIR_CHAIN) && combo.length != target.length) return false
        return combo.rank > target.rank
    }

    private fun findAiCombo(hand: List<PokerCard>, target: PokerCombo?): PokerCombo? {
        if (target == null) return smallestLead(hand)
        return allCombos(hand).filter { canBeat(it, target) }.minWithOrNull(compareBy<PokerCombo> { if (it.type == ComboType.BOMB) 1 else 0 }.thenBy { it.cards.size }.thenBy { it.rank })
    }

    private fun smallestLead(hand: List<PokerCard>): PokerCombo? {
        if (hand.isEmpty()) return null
        val choices = allCombos(hand).filter { it.type != ComboType.BOMB }.sortedWith(compareBy<PokerCombo> { it.cards.size }.thenBy { it.rank })
        return choices.firstOrNull() ?: hand.minBy { it.rank }.let { PokerCombo(ComboType.SINGLE, it.rank, listOf(it)) }
    }

    private fun allCombos(hand: List<PokerCard>): List<PokerCombo> {
        val groups = hand.groupBy { it.rank }
        val combos = mutableListOf<PokerCombo>()
        hand.forEach { combos.add(PokerCombo(ComboType.SINGLE, it.rank, listOf(it))) }
        groups.filter { it.value.size >= 2 }.forEach { combos.add(PokerCombo(ComboType.PAIR, it.key, it.value.take(2))) }
        groups.filter { it.value.size >= 3 }.forEach { e ->
            combos.add(PokerCombo(ComboType.TRIPLE, e.key, e.value.take(3)))
            hand.firstOrNull { it.rank != e.key }?.let { combos.add(PokerCombo(ComboType.TRIPLE_ONE, e.key, e.value.take(3) + it)) }
            groups.firstNotNullOfOrNull { p -> if (p.key != e.key && p.value.size >= 2) p.value.take(2) else null }?.let { combos.add(PokerCombo(ComboType.TRIPLE_PAIR, e.key, e.value.take(3) + it)) }
        }
        groups.filter { it.value.size >= 4 }.forEach { combos.add(PokerCombo(ComboType.BOMB, it.key, it.value.take(4))) }
        val jokers = hand.filter { it.rank >= 16 }
        if (jokers.size == 2) combos.add(PokerCombo(ComboType.BOMB, 99, jokers))
        combos.addAll(straights(hand))
        combos.addAll(pairChains(hand))
        return combos
    }

    private fun straights(hand: List<PokerCard>): List<PokerCombo> {
        val byRank = hand.filter { it.rank < 15 }.groupBy { it.rank }.toSortedMap()
        val ranks = byRank.keys.toList()
        val out = mutableListOf<PokerCombo>()
        for (start in ranks.indices) for (end in start + 4 until ranks.size) {
            val slice = ranks.subList(start, end + 1)
            if (slice.zipWithNext().all { it.second == it.first + 1 }) out.add(PokerCombo(ComboType.STRAIGHT, slice.last(), slice.map { byRank[it]!!.first() }))
        }
        return out
    }

    private fun pairChains(hand: List<PokerCard>): List<PokerCombo> {
        val byRank = hand.filter { it.rank < 15 }.groupBy { it.rank }.filterValues { it.size >= 2 }.toSortedMap()
        val ranks = byRank.keys.toList()
        val out = mutableListOf<PokerCombo>()
        for (start in ranks.indices) for (end in start + 2 until ranks.size) {
            val slice = ranks.subList(start, end + 1)
            if (slice.zipWithNext().all { it.second == it.first + 1 }) out.add(PokerCombo(ComboType.PAIR_CHAIN, slice.last(), slice.flatMap { byRank[it]!!.take(2) }))
        }
        return out
    }
}

@Composable
private fun PokerRulesCard(mode: PokerMode) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.20f)).padding(12.dp)) {
        Text("玩法说明", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
        val text = if (mode == PokerMode.LANDLORD) "规则：随机地主拿3张底牌并先出。基础分100，地主赢两份，农民各一份；炸弹/王炸、春天/反春天都会翻倍。" else "规则：单张、对子、三张、三带一、三带二、顺子、连对、炸弹。牌型一致且更大才能压，先出完获胜。"
        Text(text, fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f), lineHeight = 17.sp)
    }
}

@Composable
private fun OpponentPokerPanel(player: SimplePokerPlayer, active: Boolean, last: PokerCombo?, history: List<PokerHistoryEntry>, avatarUri: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(if (active) Color(0xFFFFD54F).copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.18f)).border(1.dp, if (active) Color(0xFFFFD54F).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatarImage(avatarUri, player.name, Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(6.dp))
            Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (player.role.isNotBlank()) { Spacer(Modifier.width(6.dp)); Text(player.role, fontSize = 10.sp, color = Color(0xFFFFD54F)) }
            Spacer(Modifier.weight(1f))
            Text("剩${player.hand.size}张", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy((-18).dp)) {
            repeat(player.hand.size.coerceAtMost(20)) { PokerBackCard() }
        }
        if (last != null) Text("刚出：${last.label}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.72f), modifier = Modifier.padding(top = 6.dp), maxLines = 1)
        if (history.isNotEmpty()) {
            Row(Modifier.padding(top = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                history.takeLast(5).forEach { MiniComboChip(it.combo) }
            }
        }
    }
}

@Composable
private fun UserPokerPanel(player: SimplePokerPlayer, active: Boolean, avatarUri: String, history: List<PokerHistoryEntry>, selected: Set<Int>, onToggle: ((Int) -> Unit)?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (active) Color.Black.copy(alpha = 0.32f) else Color.Black.copy(alpha = 0.22f)).border(1.dp, if (active) Color(0xFFFFD54F).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatarImage(avatarUri, "你", Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.width(8.dp))
            Text("你的手牌", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("剩${player.hand.size}张", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) Text("已选${selected.size}张", fontSize = 11.sp, color = Color(0xFFFFD54F))
        }
        Spacer(Modifier.height(8.dp))
        val firstRow = (player.hand.size + 1) / 2
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
            player.hand.take(firstRow).forEachIndexed { i, card -> PokerCardView(card, faceUp = true, selected = i in selected, onClick = onToggle?.let { { it(i) } }) }
        }
        if (player.hand.size > firstRow) {
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.Center) {
                player.hand.drop(firstRow).forEachIndexed { i, card -> val idx = firstRow + i; PokerCardView(card, faceUp = true, selected = idx in selected, onClick = onToggle?.let { { it(idx) } }) }
            }
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("已出", fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.CenterVertically))
                history.takeLast(6).forEach { MiniComboChip(it.combo) }
            }
        }
    }
}

@Composable
private fun TableInfo(game: SimplePokerState, avatarMap: Map<String, String>, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.22f)).border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (game.mode == PokerMode.LANDLORD) {
            val landlord = game.players.getOrNull(game.landlordIndex)?.name ?: "未知"
            Text("地主：$landlord · 倍率 x${game.multiplier}${if (game.bombCount > 0) " · 炸弹${game.bombCount}次" else ""}", fontSize = 12.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (game.bottomCards.isNotEmpty()) Text("底牌：${game.bottomCards.joinToString("、") { it.label }}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.62f), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 3.dp))
            Spacer(Modifier.height(8.dp))
        }
        Text(game.message, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (game.lastCombo != null) {
            Spacer(Modifier.height(8.dp))
            Text("桌面", fontSize = 11.sp, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.Center) { game.lastCombo.cards.forEach { PokerCardView(it, faceUp = true) } }
            Text("${game.lastCombo.label} · ${game.players.getOrNull(game.lastPlayer)?.name ?: "对手"}出的", fontSize = 12.sp, color = Color(0xFFFFD54F), modifier = Modifier.padding(top = 4.dp))
        }
        if (game.tableTalk.isNotBlank()) {
            val speaker = game.tableTalk.substringBefore("：", "")
            Row(Modifier.padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.08f)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (speaker.isNotBlank()) OperatorAvatarImage(avatarMap[speaker].orEmpty(), speaker, Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)))
                Spacer(Modifier.width(8.dp))
                Text(game.tableTalk, fontSize = 12.sp, color = Color.White.copy(alpha = 0.82f), lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun PokerTalkBubbleOverlay(bubble: PokerTalkBubble, players: List<SimplePokerPlayer>, avatarMap: Map<String, String>) {
    val index = players.indexOfFirst { it.name == bubble.speaker }
    if (index <= 0) return
    val player = players.getOrNull(index) ?: return
    val alignment = if (index == 1) Alignment.TopStart else Alignment.TopEnd
    val start = if (index == 1) 18.dp else 96.dp
    val end = if (index == 1) 96.dp else 18.dp
    Box(Modifier.fillMaxSize().padding(start = start, top = 92.dp, end = end), contentAlignment = alignment) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.shadow(12.dp, RoundedCornerShape(18.dp)).background(Color(0xFF141A1F).copy(alpha = 0.94f), RoundedCornerShape(18.dp)).border(1.dp, Color(0xFFFFD54F).copy(alpha = 0.38f), RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            OperatorAvatarImage(avatarMap[player.name].orEmpty(), player.name, Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 250.dp)) {
                Text(if (player.role.isNotBlank()) "${player.name} · ${player.role}" else player.name, fontSize = 10.sp, color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold)
                Text(bubble.text, fontSize = 14.sp, color = Color.White, lineHeight = 19.sp, maxLines = 3)
            }
        }
    }
}

@Composable
private fun MiniComboChip(combo: PokerCombo) {
    Text(combo.label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.78f), maxLines = 1, modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color.White.copy(alpha = 0.10f)).padding(horizontal = 5.dp, vertical = 3.dp))
}

@Composable
private fun PokerBackCard() {
    Box(Modifier.width(30.dp).height(42.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF263F7A)).border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        Text("◆", fontSize = 11.sp, color = Color.White.copy(alpha = 0.65f))
    }
}

@Composable
private fun PokerCardView(card: PokerCard, faceUp: Boolean, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val red = card.suit == "♥" || card.suit == "♦" || card.rank >= 16
    Box(Modifier.offset(y = if (selected) (-8).dp else 0.dp).width(38.dp).height(54.dp).clip(RoundedCornerShape(6.dp)).background(if (faceUp) Color(0xFFFFFBEC) else Color(0xFF263F7A)).border(if (selected) 2.dp else 1.dp, if (selected) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }, contentAlignment = Alignment.Center) {
        Text(if (faceUp) card.label else "◆", fontSize = if (card.rank >= 16) 10.sp else 12.sp, fontWeight = FontWeight.Bold, color = if (faceUp) (if (red) CardRed else CardBlack) else Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
    }
}
