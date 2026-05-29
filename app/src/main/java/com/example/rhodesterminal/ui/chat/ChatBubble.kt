package com.example.rhodesterminal.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.rhodesterminal.data.db.entity.ChatMessageEntity
import com.example.rhodesterminal.ui.theme.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.example.rhodesterminal.network.Segment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

fun formatChatTime(timestamp: Long): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai")); cal.timeInMillis = timestamp
    val now = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val period = when { hour in 0..5 -> "凌晨"; hour in 6..11 -> "上午"; hour == 12 -> "中午"; hour in 13..17 -> "下午"; else -> "晚上" }
    val timeStr = "${period}${if (hour > 12) hour - 12 else if (hour == 0) 12 else hour}:${String.format("%02d", minute)}"

    return if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
        timeStr
    } else {
        val yesterday = now.clone() as Calendar; yesterday.add(Calendar.DAY_OF_YEAR, -1)
        if (cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) && cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)) {
            "昨天$timeStr"
        } else {
            "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日$timeStr"
        }
    }
}

fun shouldShowTimeSeparator(currentTimestamp: Long, previousTimestamp: Long): Boolean {
    return previousTimestamp == 0L || (currentTimestamp - previousTimestamp) > 3 * 60 * 1000
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(message: ChatMessageEntity, aiAvatarUri: String, userAvatarUri: String = "", onRecall: (Long) -> Unit, onRegenerate: (Long) -> Unit, onContinue: (Long) -> Unit, showTime: Boolean = false, modifier: Modifier = Modifier) {
    when (message.type) {
        "system" -> SystemBubble(message = message, modifier = modifier)
        "narration" -> NarrationBubble(text = message.content, modifier = modifier)
        "ai_json" -> JsonBubble(message = message, aiAvatarUri = aiAvatarUri, userAvatarUri = userAvatarUri, onRecall = onRecall, onRegenerate = onRegenerate, onContinue = onContinue, showTime = showTime, modifier = modifier)
        else -> TextBubble(message = message, aiAvatarUri = aiAvatarUri, userAvatarUri = userAvatarUri, onRecall = onRecall, onRegenerate = onRegenerate, onContinue = onContinue, showTime = showTime, modifier = modifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextBubble(message: ChatMessageEntity, aiAvatarUri: String, userAvatarUri: String = "", onRecall: (Long) -> Unit, onRegenerate: (Long) -> Unit, onContinue: (Long) -> Unit, showTime: Boolean, modifier: Modifier) {
    val isMe = message.isMe
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = if (isMe) Color(0xFF95EC69) else Card
    val bubbleShape = if (isMe) RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp) else RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)) {
        // Time separator
        if (showTime) {
            Text(formatChatTime(message.timestamp), fontSize = 12.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
        }

        Box {
            Row(modifier = Modifier.fillMaxWidth().combinedClickable(onLongClick = { showMenu = true }, onClick = {}), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
                if (!isMe) { AiAvatar(uri = aiAvatarUri, name = message.senderName); Spacer(modifier = Modifier.width(8.dp)) }
            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
                if (message.mode == "offline" && !isMe) OfflineInfo(message = message)
                Box(modifier = Modifier.widthIn(max = 260.dp).clip(bubbleShape).background(bubbleColor).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(message.content.ifEmpty { if (isMe) "" else "..." }, fontSize = 16.sp, color = if (isMe) Color(0xFF1C1C1E) else TextPrimary, fontWeight = FontWeight.Normal)
                    }
                }
                if (isMe) { Spacer(modifier = Modifier.width(8.dp)); UserAvatar(uri = userAvatarUri) }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Row { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("复制", color = TextPrimary) } }, onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("msg", message.content)); Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show(); showMenu = false })
                DropdownMenuItem(text = { Row { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("撤回", color = ErrorRed) } }, onClick = { onRecall(message.id); showMenu = false })
                if (!isMe) {
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.Refresh, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("重说", color = Primary) } }, onClick = { onRegenerate(message.id); showMenu = false })
                    DropdownMenuItem(text = { Row { Icon(Icons.Default.SkipNext, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("继续说", color = Primary) } }, onClick = { onContinue(message.id); showMenu = false })
                }
            }
        }
    }
}

@Composable private fun SystemBubble(message: ChatMessageEntity, modifier: Modifier) { Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) { Text(message.content, fontSize = 13.sp, color = TextSecondary, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center) } }

@Composable private fun NarrationBubble(text: String, modifier: Modifier) { Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp)) { Box(modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(text, fontSize = 13.sp, color = TextTertiary, fontStyle = FontStyle.Italic, textAlign = TextAlign.Start) } } }

@Composable private fun NarrationText(text: String) { if (text.isNotBlank()) Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp)) { Box(modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) { Text(text, fontSize = 13.sp, color = TextTertiary, fontStyle = FontStyle.Italic, textAlign = TextAlign.Start) } } }

@Composable private fun OfflineInfo(message: ChatMessageEntity) {
    Column(modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) {
        if (message.emotion.isNotBlank()) Text(message.emotion, fontSize = 12.sp, color = TextSecondary, fontStyle = FontStyle.Italic, modifier = Modifier.padding(bottom = 2.dp))
        if (message.location.isNotBlank() || message.activity.isNotBlank()) { Row(modifier = Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) { if (message.location.isNotBlank()) { InfoBadge(message.location, Color(0xFFE0E7FF), Color(0xFF4338CA)); Spacer(modifier = Modifier.width(4.dp)) }; if (message.activity.isNotBlank()) InfoBadge(message.activity, Color(0xFFFEE2E2), Color(0xFFB91C1C)) } }
    }
}

@Composable private fun InfoBadge(text: String, color: Color, textColor: Color) { Text(text, fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Medium, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp).border(0.5.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))) }

@Composable private fun AiAvatar(uri: String, name: String) { if (uri.isNotBlank()) AsyncImage(model = uri, contentDescription = "avatar", modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) { Text(name.take(1), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) } }

@Composable private fun UserAvatar(uri: String = "") { if (uri.isNotBlank()) AsyncImage(model = uri, contentDescription = "avatar", modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop) else Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF6B7280)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, "我的头像", tint = Color.White, modifier = Modifier.size(20.dp)) } }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JsonBubble(message: ChatMessageEntity, aiAvatarUri: String, userAvatarUri: String = "", onRecall: (Long) -> Unit, onRegenerate: (Long) -> Unit, onContinue: (Long) -> Unit, showTime: Boolean, modifier: Modifier) {
    val jsonSegments = remember(message.content) {
        val content = message.content
        fun parse(raw: String): Pair<String?, List<Segment>> = try {
            val obj = JsonParser.parseString(raw).asJsonObject
            val emotion = obj.get("emotion")?.asString ?: ""
            val segments = Gson().fromJson(obj.get("segments"), Array<Segment>::class.java)?.toList() ?: emptyList()
            emotion to segments
        } catch (_: Exception) { null to emptyList<Segment>() }
        var result = parse(content)
        if (result.first == null) {
            val cleaned = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                .replace("，", ",").replace("：", ":")
            result = parse(cleaned)
        }
        if (result.first == null) {
            var s = content.trim()
                .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                .replace("，", ",").replace("：", ":")
            s = s.replace(", }", "}").replace(",}", "}")
            if (!s.startsWith("{")) { val start = s.indexOf('{'); if (start >= 0) s = s.substring(start) }
            if (!s.endsWith("}")) { val end = s.lastIndexOf('}'); if (end >= 0) s = s.substring(0, end + 1) }
            result = parse(s)
        }
        result
    }
    val (emotion, segments) = jsonSegments
    if (emotion == null || segments.isEmpty()) {
        TextBubble(message = message, aiAvatarUri = aiAvatarUri, userAvatarUri = userAvatarUri, onRecall = onRecall, onRegenerate = onRegenerate, onContinue = onContinue, showTime = showTime, modifier = modifier)
        return
    }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = Card
    val bubbleShape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp).combinedClickable(onLongClick = { showMenu = true }, onClick = {})) {
        if (showTime) {
            Text(formatChatTime(message.timestamp), fontSize = 12.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center)
        }
        val isOnline = message.mode == "online"
        segments.forEachIndexed { segIdx, seg ->
            if (seg.type == "narration") {
                // 旁白：线下/导演模式显示；线上模式隐藏
                if (!isOnline) {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 6.dp)) {
                        Box(modifier = Modifier.widthIn(max = 260.dp).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)).background(Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(seg.content, fontSize = 13.sp, color = TextTertiary, fontStyle = FontStyle.Italic, textAlign = TextAlign.Start)
                        }
                    }
                }
            } else {
                // 台词：正常 AI 气泡，带头像
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
                    if (segIdx == 0 || segments[segIdx - 1].type == "narration") {
                        AiAvatar(uri = aiAvatarUri, name = message.senderName)
                    } else {
                        Spacer(modifier = Modifier.width(36.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.widthIn(max = 260.dp).clip(bubbleShape).background(bubbleColor).padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(seg.content, fontSize = 16.sp, color = TextPrimary)
                    }
                }
            }
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(text = { Row { Icon(Icons.Default.ContentCopy, null, tint = TextPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("复制", color = TextPrimary) } }, onClick = { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("msg", message.content)); Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show(); showMenu = false })
            DropdownMenuItem(text = { Row { Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("撤回", color = ErrorRed) } }, onClick = { onRecall(message.id); showMenu = false })
            if (!message.isMe) {
                DropdownMenuItem(text = { Row { Icon(Icons.Default.Refresh, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("重说", color = Primary) } }, onClick = { onRegenerate(message.id); showMenu = false })
                DropdownMenuItem(text = { Row { Icon(Icons.Default.SkipNext, null, tint = Primary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("继续说", color = Primary) } }, onClick = { onContinue(message.id); showMenu = false })
            }
        }
    }
}
