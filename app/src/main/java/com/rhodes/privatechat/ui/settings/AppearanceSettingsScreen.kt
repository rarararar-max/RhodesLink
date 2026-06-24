package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.common.AppBackground
import com.rhodes.privatechat.ui.common.GradientHeader
import com.rhodes.privatechat.ui.common.SoftCard
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    AppBackground(modifier = modifier.fillMaxSize()) { Column(modifier = Modifier.fillMaxSize()) {
        GradientHeader("外观设置", onBack = onBack, icon = Icons.Default.Palette)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()) {
            SettingsSectionTitle("主题")
            SettingsSwitchCard(
                title = "深色模式",
                desc = "切换应用界面的深色/浅色主题。设置会立即保存，下次启动继续生效。",
                checked = isDarkMode,
                onCheckedChange = {
                    isDarkMode = it
                    settings.darkMode = it
                }
            )
            SoftCard(modifier = Modifier.fillMaxWidth(), shadow = false) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DarkMode, null, tint = TextSecondary)
                Spacer(Modifier.width(8.dp))
                Text("背景与聊天壁纸仍沿用各聊天页面的现有入口，后续可继续集中到这里。", fontSize = 12.sp, color = TextSecondary)
            } }
        }
    } }
}
