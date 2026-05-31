package com.rhodes.privatechat.ui.chat

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ShareMessage(
    val senderName: String,
    val content: String,
    val isMe: Boolean,
    val isSystem: Boolean = false,
    val isNarration: Boolean = false
)

@Composable
fun ChatShareDialog(
    titleContent: @Composable () -> Unit,
    messages: List<ShareMessage>,
    userName: String,
    userAvatarUri: String = "",
    operatorAvatarUri: String = "",
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享图片", color = TextPrimary) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.graphicsLayer(scaleX = 0.9f, scaleY = 0.9f)) {
                    SharePreview(titleContent = titleContent, messages = messages, userName = userName, userAvatarUri = userAvatarUri, operatorAvatarUri = operatorAvatarUri)
                }
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("关闭", color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val widthPx = with(density) { 380.dp.toPx().toInt() }
                                    val bmp = withContext(Dispatchers.IO) {
                                        drawShareBitmap(widthPx, messages, userName)
                                    }
                                    if (bmp != null) {
                                        val file = File(ctx.cacheDir, "share_${System.currentTimeMillis()}.png")
                                        withContext(Dispatchers.IO) { FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
                                        Toast.makeText(ctx, "已保存", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(ctx, "渲染失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (_: Exception) {
                                    Toast.makeText(ctx, "保存失败", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("下载", color = OnPrimary)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

private fun drawShareBitmap(widthPx: Int, messages: List<ShareMessage>, userName: String): Bitmap? {
    try {
        val titleHeight = 60f
        val lineHeight = 50f
        val msgCount = messages.size
        val totalHeight = (titleHeight + msgCount * lineHeight + 60).toInt().coerceAtLeast(120)
        val result = Bitmap.createBitmap(widthPx, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val bgPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.FILL }
        canvas.drawRect(0f, 0f, widthPx.toFloat(), totalHeight.toFloat(), bgPaint)

        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = android.graphics.Color.parseColor("#2C2C2E") }
        val divPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor("#E0E0E0") }
        val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; color = android.graphics.Color.parseColor("#333333") }
        val grayPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; color = android.graphics.Color.parseColor("#888888") }
        val bubblePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }
        val avatarPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.FILL }

        var y = 20f
        val senderName = messages.firstOrNull { !it.isSystem }?.senderName ?: "聊天"
        canvas.drawText(senderName, 20f, y + 20f, titlePaint)
        y += 40f
        canvas.drawRect(20f, y, widthPx - 20f, y + 1f, divPaint)
        y += 16f

        for (msg in messages) {
            if (msg.isNarration || msg.isSystem) {
                val text = msg.content.take(80)
                grayPaint.textSize = 16f
                val tw = grayPaint.measureText(text)
                val bx = (widthPx - tw) / 2f - 8f
                bubblePaint.color = android.graphics.Color.parseColor("#F0F0F0")
                canvas.drawRoundRect(bx, y, bx + tw + 16f, y + 32f, 8f, 8f, bubblePaint)
                canvas.drawText(text, bx + 8f, y + 22f, grayPaint)
                y += 40f
            } else {
                val text = msg.content.take(60)
                textPaint.textSize = 18f
                val tw = textPaint.measureText(text)
                if (msg.isMe) {
                    val avatarRight = widthPx - 10f
                    val avatarCenterX = avatarRight - 14f
                    val bubbleRight = avatarCenterX - 20f
                    val bx = bubbleRight - tw - 24f
                    bubblePaint.color = android.graphics.Color.parseColor("#95EC69")
                    canvas.drawRoundRect(bx, y, bubbleRight, y + 36f, 12f, 12f, bubblePaint)
                    textPaint.color = android.graphics.Color.parseColor("#1C1C1E")
                    canvas.drawText(text, bx + 12f, y + 24f, textPaint)
                    avatarPaint.color = android.graphics.Color.parseColor("#6B7280")
                    canvas.drawCircle(avatarCenterX, y + 18f, 14f, avatarPaint)
                    grayPaint.textSize = 12f
                    grayPaint.color = android.graphics.Color.WHITE
                    canvas.drawText(userName.take(1), avatarCenterX - 4f, y + 22f, grayPaint)
                } else {
                    val ax = 20f
                    avatarPaint.color = android.graphics.Color.parseColor("#5B8DEF")
                    canvas.drawCircle(ax + 14f, y + 18f, 14f, avatarPaint)
                    grayPaint.textSize = 12f
                    grayPaint.color = android.graphics.Color.WHITE
                    canvas.drawText(msg.senderName.take(1), ax + 10f, y + 22f, grayPaint)
                    val bx = ax + 40f
                    bubblePaint.color = android.graphics.Color.WHITE
                    canvas.drawRoundRect(bx, y, bx + tw + 24f, y + 36f, 12f, 12f, bubblePaint)
                    val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { style = android.graphics.Paint.Style.STROKE; strokeWidth = 1f; color = android.graphics.Color.parseColor("#E0E0E0") }
                    canvas.drawRoundRect(bx, y, bx + tw + 24f, y + 36f, 12f, 12f, strokePaint)
                    textPaint.color = android.graphics.Color.parseColor("#333333")
                    canvas.drawText(text, bx + 12f, y + 24f, textPaint)
                }
                y += 44f
            }
        }
        y += 8f
        canvas.drawRect(20f, y, widthPx - 20f, y + 1f, divPaint)
        y += 12f
        val footer = "——罗德岛通讯端"
        val fw = grayPaint.measureText(footer)
        grayPaint.textSize = 16f
        canvas.drawText(footer, (widthPx - fw) / 2f, y + 16f, grayPaint)
        return result
    } catch (_: Exception) { return null }
}

@Composable
private fun SharePreview(titleContent: @Composable () -> Unit, messages: List<ShareMessage>, userName: String, userAvatarUri: String = "", operatorAvatarUri: String = "") {
    Column(modifier = Modifier.width(380.dp).background(Card).padding(12.dp)) {
        titleContent()
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Spacer(Modifier.height(6.dp))

        messages.forEach { msg ->
            if (msg.isNarration || msg.isSystem) {
                Box(Modifier.fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F0F0)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(msg.content.take(120), fontSize = 13.sp, fontStyle = FontStyle.Italic, color = Color(0xFF888888), textAlign = TextAlign.Center)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start) {
                    if (!msg.isMe) {
                        if (operatorAvatarUri.isNotBlank()) {
                            coil3.compose.AsyncImage(model = operatorAvatarUri, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                                Text(msg.senderName.take(1), fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(if (msg.isMe) 12.dp else 12.dp, if (msg.isMe) 4.dp else 12.dp, if (msg.isMe) 12.dp else 12.dp, if (msg.isMe) 12.dp else 4.dp)).background(if (msg.isMe) Color(0xFF95EC69) else Card).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(msg.content.take(120), fontSize = 14.sp, color = if (msg.isMe) Color(0xFF1C1C1E) else Color(0xFF333333))
                    }
                    if (msg.isMe) {
                        Spacer(Modifier.width(6.dp))
                        if (userAvatarUri.isNotBlank()) {
                            coil3.compose.AsyncImage(model = userAvatarUri, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF6B7280)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFFE0E0E0))
        Spacer(Modifier.height(6.dp))
        Text("——罗德岛通讯端", fontSize = 13.sp, color = Color(0xFF888888), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
