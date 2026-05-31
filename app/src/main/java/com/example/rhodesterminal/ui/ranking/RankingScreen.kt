package com.example.rhodesterminal.ui.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.rhodesterminal.shared.data.SenderCount
import com.example.rhodesterminal.ui.theme.*
import com.example.rhodesterminal.viewmodel.MainViewModel

private val operatorColors = listOf(Primary, AccentOrange, AccentGreen, AccentPurple, ErrorRed, AccentBlue, Color(0xFFFF6B9D), Color(0xFF64FFDA))

@Composable
fun RankingScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOperatorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableIntStateOf(0) }
    var ranking by remember { mutableStateOf<List<SenderCount>>(emptyList()) }
    val operators by viewModel.operators.collectAsState()
    val tabs = listOf("昨日排行", "历史总榜")

    LaunchedEffect(Unit) {
        ranking = viewModel.getMessageRanking()
    }

    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Spacer(modifier = Modifier.width(4.dp))
            Text("聊天排行榜", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Row(modifier = Modifier.fillMaxWidth().background(Surface)) {
            tabs.forEachIndexed { i, label ->
                Column(modifier = Modifier.weight(1f).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(if (tab == i) PrimaryContainer else Surface).clickable { tab = i }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (tab == i) Primary else TextSecondary)
                }
            }
        }
        HorizontalDivider(color = Divider)

        val sorted = ranking.sortedByDescending { it.cnt }
        if (sorted.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无数据", color = TextTertiary)
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("排名", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(40.dp))
                Text("干员", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
                Text("消息数", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                Text("好感+", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
            }

            LazyColumn {
                itemsIndexed(sorted) { i, entry ->
                    val color = operatorColors[i % operatorColors.size]
                    val rankingOp = remember(entry, operators) { operators.find { op -> op.name == entry.senderName } }
                    val rankingAvatar = rankingOp?.avatarUri
                    Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 10.dp).clickable { onOperatorClick(entry.senderName) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(rankText(i), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = rankColor(i), modifier = Modifier.width(40.dp))
                        if (rankingAvatar.isNullOrBlank()) {
                            Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                                Text(entry.senderName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            AsyncImage(model = rankingAvatar, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(entry.senderName, fontSize = 14.sp, modifier = Modifier.weight(1f), color = TextPrimary)
                        Text("${entry.cnt}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                        Text("+0", fontSize = 13.sp, color = Color(0xFFFF9800), modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                    }
                    HorizontalDivider(color = BG)
                }
            }
        }
    }
}

private fun rankText(i: Int) = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "#${i + 1}" }
private fun rankColor(i: Int) = when (i) { 0 -> Color(0xFFFFD700); 1 -> Color(0xFFC0C0C0); 2 -> Color(0xFFCD7F32); else -> TextSecondary }
