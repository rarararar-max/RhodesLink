package com.rhodes.privatechat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import java.io.File
import java.io.FileOutputStream
import com.rhodes.privatechat.data.db.entity.ChatMessageEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun ChatExportDialog(
    operatorName: String,
    messages: List<ChatMessageEntity>,
    userProfile: com.rhodes.privatechat.viewmodel.shared.UserProfile,
    operatorAvatarUri: String = "",
    onDismiss: () -> Unit
) {
    val shareMsgs = messages.take(8).map { msg ->
        val text = if (msg.type == "ai_json") {
            try {
                val obj = Json.parseToJsonElement(msg.content).jsonObject
                val segs = obj["segments"] as? JsonArray
                if (segs != null) {
                    segs.mapNotNull {
                        val seg = it.jsonObject
                        if (seg["type"]?.jsonPrimitive?.content == "dialogue") seg["content"]?.jsonPrimitive?.content else null
                    }.joinToString(" ")
                } else msg.content.take(80)
            } catch (_: Exception) { msg.content.take(80) }
        } else msg.content
        ShareMessage(
            senderName = msg.senderName,
            content = text,
            isMe = msg.isMe,
            isSystem = msg.type == "system" || msg.senderName == "系统",
            isNarration = msg.type == "narration" || msg.type == "ai_json"
        )
    }

    ChatShareDialog(
        titleContent = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                if (operatorAvatarUri.isNotBlank()) {
                    coil3.compose.AsyncImage(model = operatorAvatarUri, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                        Text(operatorName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(operatorName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
        },
        messages = shareMsgs,
        userName = userProfile.nickname,
        userAvatarUri = userProfile.avatarUri,
        operatorAvatarUri = operatorAvatarUri,
        onDismiss = onDismiss
    )
}
