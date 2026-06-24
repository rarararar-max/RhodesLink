package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.MenuBook
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
import com.rhodes.privatechat.ui.common.WechatIconTile
import com.rhodes.privatechat.ui.common.WechatListGroup
import com.rhodes.privatechat.ui.common.WechatTopBar
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

data class SettingEntry(val icon: ImageVector, val title: String, val desc: String, val iconColor: Color = Blue400)

@Composable
fun SettingsScreen(
    onProfile: () -> Unit = {}, onModel: () -> Unit = {},
    onChatParams: () -> Unit = {}, onDataManage: () -> Unit = {},
    onPermissions: () -> Unit = {}, onCredits: () -> Unit = {},
    onAppearance: () -> Unit = {}, onWorld: () -> Unit = {}, onStory: () -> Unit = {},
    onDebugLog: () -> Unit = {},
    userNickname: String = "博士", userGender: String = "", userAvatarUri: String = "", modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val balance by settings.lmbFlow.collectAsState(initial = settings.lmb)
    Column(modifier = modifier.fillMaxSize().background(BG)) {
        WechatTopBar("我")
        Row(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onProfile).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (userAvatarUri.isNotBlank()) {
                AsyncImage(model = userAvatarUri, contentDescription = null, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            } else {
                Box(modifier = Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).background(Blue400), contentAlignment = Alignment.Center) {
                    Text(userNickname.take(1), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(userNickname, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    if (userGender.isNotBlank()) { Spacer(modifier = Modifier.width(6.dp)); Text(userGender, fontSize = 12.sp, color = TextTertiary) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("点击查看个人资料", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("龙门币: ${balance}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentOrange)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary, modifier = Modifier.size(22.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp)) {
            Spacer(Modifier.height(8.dp))
            WechatListGroup {
                SettingItem(SettingEntry(Icons.Default.Person, "身份设置", "昵称、性别、简介、头像", iconColor = Blue400), onClick = onProfile)
                SettingItem(SettingEntry(Icons.Default.SmartToy, "模型设置", "AI厂商、API Key、TTS", iconColor = Color(0xFFFF9800)), onClick = onModel)
            }
            Spacer(Modifier.height(8.dp))
            WechatListGroup {
                SettingItem(SettingEntry(Icons.Default.Tune, "聊天表现", "回复长短、上下文记忆、角色说话风格", iconColor = Color(0xFF00BCD4)), onClick = onChatParams)
                SettingItem(SettingEntry(Icons.Default.AutoAwesome, "自动世界", "自动动态、主动私聊、事件联动、群聊唤起", iconColor = Color(0xFF4CAF50)), onClick = onWorld)
                SettingItem(SettingEntry(Icons.AutoMirrored.Filled.MenuBook, "日记与派遣", "干员日记、派遣故事长度、自动生成规则", iconColor = Color(0xFF795548)), onClick = onStory)
                SettingItem(SettingEntry(Icons.Default.Build, "权限管理", "干员主动消息、动态和群聊权限", iconColor = Color(0xFFFF9800)), onClick = onPermissions)
            }
            Spacer(Modifier.height(8.dp))
            WechatListGroup {
                SettingItem(SettingEntry(Icons.Default.AutoFixHigh, "数据管理", "统计信息与自动清理", iconColor = Color(0xFF8B5CF6)), onClick = onDataManage)
                SettingItem(SettingEntry(Icons.Default.Favorite, "感谢", "支持股东名单", iconColor = Primary), onClick = onCredits)
                SettingItem(SettingEntry(Icons.Default.DarkMode, "外观设置", "白天/黑夜模式、界面外观", iconColor = Color(0xFF607D8B)), onClick = onAppearance)
            }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun SettingItem(entry: SettingEntry, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            WechatIconTile(entry.icon, entry.iconColor, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(entry.desc, fontSize = 12.sp, color = TextSecondary)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
        HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 68.dp))
    }
}
