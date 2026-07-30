package com.rhodes.privatechat.ui.mahjong

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.game.mahjong.AiChat
import com.rhodes.privatechat.game.mahjong.PlayerState
import com.rhodes.privatechat.game.mahjong.SettlementResult
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*

@Composable
fun SettlementScreen(
    result: SettlementResult,
    onBack: () -> Unit,
    onPlayAgain: (() -> Unit)? = null,
    onNextRound: (() -> Unit)? = null,
    avatarMap: Map<String, String> = emptyMap(),
    players: List<PlayerState> = emptyList(),
    isFinalSettlement: Boolean = true,
    onGenerateLine: ((PlayerState?, String, Boolean, Boolean, Int, Int, String, String, (String) -> Unit) -> Unit)? = null
) {
    val settlementLines = remember(result) {
        result.rankings.associate { r ->
            val player = players.find { it.opId == r.opId || it.name == r.name }
            val isWinner = result.winnerName == r.name || r.rank == 1
            val line = if (player?.isHuman == true) "" else AiChat.settlementLine(player, r.name, isWinner, result.winType == "流局", r.rank, r.netGain).substringAfter("：")
            (r.opId.ifBlank { r.name }) to line
        }
    }
    val generatedLines = remember(result.gameId) { mutableStateMapOf<String, String>() }
    LaunchedEffect(result.gameId) {
        result.rankings.forEach { ranking ->
            val player = players.find { it.opId == ranking.opId || it.name == ranking.name }
            if (player?.isHuman == true) return@forEach
            val key = ranking.opId.ifBlank { ranking.name }
            val fallback = settlementLines[key].orEmpty()
            onGenerateLine?.invoke(
                player, ranking.name, result.winnerName == ranking.name || ranking.rank == 1,
                result.winType == "流局", ranking.rank, ranking.netGain, result.summary, fallback
            ) { generated ->
                if (generated.isNotBlank()) generatedLines[key] = generated
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1B3A2D)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("牌局结算", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Column(modifier = Modifier.weight(1f).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
            Text("活动室牌局结束", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            if (result.matchMode != com.rhodes.privatechat.game.mahjong.MatchMode.QUICK) {
                Spacer(Modifier.height(4.dp))
                Text("第${result.currentRound}/${result.maxRounds}局", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.08f)).padding(14.dp)) {
                Text(result.summary.ifBlank { "本局已经结算。" }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFFD54F), lineHeight = 20.sp)
                Spacer(Modifier.height(6.dp))
                val detail = buildString {
                    append("结果：${result.winType}")
                    if (result.winnerName.isNotBlank()) append(" · 赢家：${result.winnerName}")
                    if (result.loserName.isNotBlank()) append(" · 放铳：${result.loserName}")
                }
                Text(detail, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(16.dp))

            result.rankings.forEach { r ->
                val medal = when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "  " }
                val gainColor = if (r.netGain >= 0) Primary else ErrorRed
                val gainPrefix = if (r.netGain >= 0) "+" else ""
                val player = players.find { it.opId == r.opId || it.name == r.name }
                val shouldSpeak = player?.isHuman != true
                val isWinner = result.winnerName == r.name || r.rank == 1
                val lineKey = r.opId.ifBlank { r.name }
                val line = generatedLines[lineKey] ?: settlementLines[lineKey].orEmpty()
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (r.rank == 1) Color(0xFFFFD54F).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(medal, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    val rAvatar = avatarMap[r.opId]
                    if (rAvatar != null) {
                        OperatorAvatarImage(avatarUri = rAvatar, name = r.name, modifier = Modifier.size(36.dp).clip(CircleShape))
                    } else {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                            Text(r.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 16.sp, fontWeight = if (r.rank <= 2) FontWeight.SemiBold else FontWeight.Normal, color = Color.White)
                        if (r.yakus.isNotEmpty()) Text("牌型：${r.yakus.joinToString("·")} · ${r.han}点", fontSize = 11.sp, color = Color(0xFFFFD54F))
                        if (shouldSpeak && line.isNotBlank()) Text(line, fontSize = 11.sp, color = Color.White.copy(alpha = 0.68f), lineHeight = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("筹码${r.finalPoints}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.66f))
                        Text("${gainPrefix}${r.netGain}${if (isFinalSettlement) "龙门币" else "暂计"}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = gainColor)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            val userGainLabel = if (isFinalSettlement) "你的净收益" else "当前累计暂计"
            val userGainUnit = if (isFinalSettlement) "龙门币" else "，最终局后入账"
            Text("${userGainLabel}：${if (result.userNetGain >= 0) "+" else ""}${result.userNetGain}${userGainUnit}",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (result.userNetGain >= 0) Primary else ErrorRed,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Text("返回", fontWeight = FontWeight.SemiBold)
        }
        val hasNextRound = result.matchMode != com.rhodes.privatechat.game.mahjong.MatchMode.QUICK && result.currentRound < result.maxRounds
        if (hasNextRound && onNextRound != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onNextRound, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                Text("下一局（第${result.currentRound + 1}/${result.maxRounds}局）", fontWeight = FontWeight.SemiBold)
            }
        }
        if (onPlayAgain != null) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00).copy(alpha = 0.8f))) {
                Text("再来一局", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
    }
}
