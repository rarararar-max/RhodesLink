package com.rhodes.privatechat.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.rhodes.privatechat.MainActivity
import com.rhodes.privatechat.R

object RhodesNotificationCenter {
    private const val CHANNEL_ID = "rhodes_messages"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "通讯消息",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "AI 主动消息、提醒和聊天通知"
        }
        manager.createNotificationChannel(channel)
    }

    fun show(context: Context, title: String, content: String, sessionId: String? = null, isGroup: Boolean = false, avatarUri: String = "") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("rhodes://notification/${sessionId.orEmpty()}/${System.currentTimeMillis()}")
            if (sessionId != null) {
                putExtra("nav_session_id", sessionId)
                putExtra("nav_is_group", isGroup)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context, intent.data.toString().hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val avatar = runCatching {
            if (avatarUri.isBlank()) null else context.contentResolver.openInputStream(Uri.parse(avatarUri))?.use(BitmapFactory::decodeStream)
        }.getOrNull()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .apply { avatar?.let { setLargeIcon(it) } }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((title + content).hashCode(), notification) }
    }
}
