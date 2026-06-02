package com.rhodes.privatechat.ui.mahjong

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.game.mahjong.SettlementResult
import com.rhodes.privatechat.ui.theme.*

@Composable
fun SettlementScreen(
    result: SettlementResult,
    onBack: () -> Unit,
    onPlayAgain: (() -> Unit)? = null
) {
    Box(Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("牌局结算", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Column(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.Center) {
            Text("牌局结束！", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp))

            result.rankings.forEach { r ->
                val medal = when (r.rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "  " }
                val gainColor = if (r.netGain >= 0) Primary else ErrorRed
                val gainPrefix = if (r.netGain >= 0) "+" else ""
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (r.rank == 1) Primary.copy(alpha = 0.08f) else Card).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(medal, fontSize = 20.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, fontSize = 16.sp, fontWeight = if (r.rank <= 2) FontWeight.SemiBold else FontWeight.Normal, color = TextPrimary)
                        if (r.yakus.isNotEmpty()) Text(r.yakus.joinToString("·") + " ${r.han}番", fontSize = 11.sp, color = Primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${r.finalPoints}点", fontSize = 14.sp, color = TextSecondary)
                        Text("${gainPrefix}${r.netGain}龙门币", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = gainColor)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))

            // AI结算发言（简化为固定文本，后续接入AI）
            val userResult = result.rankings.find { it.name == result.rankings.firstOrNull { r -> result.userNetGain != 0 }?.name }
            if (result.userNetGain != 0) {
                Text("你的净收益：${if (result.userNetGain > 0) "+" else ""}${result.userNetGain}龙门币",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (result.userNetGain >= 0) Primary else ErrorRed,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            if (result.chatLog.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("💬 聊天记录", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)).background(Card).padding(8.dp).verticalScroll(rememberScrollState())) {
                    result.chatLog.takeLast(30).forEach { line ->
                        val isAssistant = line.contains("⭐") || line.startsWith("[助手]") || line.startsWith("[助理]")
                        val cl = if (isAssistant) Color(0xFFFF8F00) else TextSecondary
                        Text(line, fontSize = 10.sp, color = cl, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
            Text("返回", fontWeight = FontWeight.SemiBold)
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
