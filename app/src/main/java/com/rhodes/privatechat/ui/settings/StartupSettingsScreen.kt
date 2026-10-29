package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.RadioButton
import com.rhodes.privatechat.ui.startup.StartupMode
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.common.GradientHeader
import com.rhodes.privatechat.ui.startup.StartupPreferences
import com.rhodes.privatechat.ui.startup.TerminalStartup
import com.rhodes.privatechat.ui.theme.*

@Composable
fun StartupSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { StartupPreferences(context) }
    var mode by remember { mutableStateOf(prefs.mode) }
    var sound by remember { mutableStateOf(prefs.sound) }
    var preview by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(BG)) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            GradientHeader("开屏设置", onBack = onBack)
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("开屏展示方式", color = TextPrimary, fontSize = 16.sp)
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    StartupMode.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().selectable(selected = mode == option, role = Role.RadioButton,
                            onClick = { mode = option; prefs.mode = option }).padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mode == option, onClick = null)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(option.title, color = TextPrimary, fontSize = 14.sp)
                                Text(option.description, color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingsSwitchCard("开屏提示音", "默认静音。开启后播放轻微电子接入音，音量跟随媒体音量。", sound) {
                    sound = it; prefs.sound = it
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { preview = true }, modifier = Modifier.fillMaxWidth()) { Text("重播启动演出") }
                Spacer(Modifier.height(14.dp))
                Text("轻触或长按指纹后松开，即可启动约3秒演出；滑出感应区可取消。通知直达聊天时跳过开屏，后台返回不重复。重播不改变每日记录。", color = TextSecondary, fontSize = 12.sp)
            }
        }
        if (preview) TerminalStartup(sound) { preview = false }
    }
}
