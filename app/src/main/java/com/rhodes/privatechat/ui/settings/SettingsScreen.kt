package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.foundation.clickable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

data class SettingEntry(val icon: ImageVector, val title: String, val desc: String, val iconColor: Color = Blue400)

@Composable
fun SettingsScreen(
    onProfile: () -> Unit = {}, onModel: () -> Unit = {},
    onChatParams: () -> Unit = {}, onDataManage: () -> Unit = {},
    onPermissions: () -> Unit = {}, onCredits: () -> Unit = {},
    userNickname: String = "博士", userGender: String = "", userAvatarUri: String = "", modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val balance by settings.lmbFlow.collectAsState(initial = settings.lmb)
    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).statusBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onProfile).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (userAvatarUri.isNotBlank()) {
                AsyncImage(model = userAvatarUri, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(Blue400), contentAlignment = Alignment.Center) {
                    Text(userNickname.take(1), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(userNickname, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (userGender.isNotBlank()) { Spacer(modifier = Modifier.width(6.dp)); Text(userGender, fontSize = 12.sp, color = TextTertiary) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("点击查看个人资料", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("龙门币: ${balance}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                }
            }
        }
        HorizontalDivider(color = Divider)

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingItem(SettingEntry(Icons.Default.Person, "身份设置", "昵称、性别、简介、头像", iconColor = Blue400), onClick = onProfile)
            SettingItem(SettingEntry(Icons.Default.SmartToy, "模型设置", "AI厂商、API Key、TTS", iconColor = Color(0xFFFF9800)), onClick = onModel)
            SettingItem(SettingEntry(Icons.Default.Tune, "聊天参数设置", "字数、段数、记忆参数", iconColor = Color(0xFF00BCD4)), onClick = onChatParams)
            SettingItem(SettingEntry(Icons.Default.AutoFixHigh, "数据管理", "统计信息与自动清理", iconColor = Color(0xFF8B5CF6)), onClick = onDataManage)
            SettingItem(SettingEntry(Icons.Default.Build, "权限管理", "干员主动消息与动态权限", iconColor = Color(0xFFFF9800)), onClick = onPermissions)
            SettingItem(SettingEntry(Icons.Default.Info, "关于", "作者与开源组件致谢", iconColor = TextSecondary), onClick = onCredits)
        }
    }
    }
}

@Composable
private fun SettingItem(entry: SettingEntry, onClick: () -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(entry.iconColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(entry.icon, null, tint = entry.iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(entry.desc, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = BG)
    }
}
