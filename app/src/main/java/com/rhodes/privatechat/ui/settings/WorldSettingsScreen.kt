package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Card
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import org.koin.compose.koinInject

@Composable
fun WorldSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    var autoEnabled by remember { mutableStateOf(settings.autoAiEnabled) }
    SaveableSettingsScaffold(
        title = "每日自动内容",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = Primary) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding().navigationBarsPadding()) {
            InfoCard("固定日计划", "每天按北京时间 05:00 开始新的内容周期。动态和私聊会预先分散安排，并在应用关闭时由系统后台任务投递；系统省电策略可能让实际到达时间略晚。")
            SettingsSectionTitle("总开关")
            SettingsSwitchCard("自动内容", "关闭后不再生成计划动态或主动私聊；手动聊天、手动催发动态不受影响。", autoEnabled) {
                autoEnabled = it
                settings.autoAiEnabled = it
            }
            SettingsSectionTitle("每日动态")
            SettingsSwitchCard("每日固定动态", "开启动态权限的角色每天会生成设定数量的公开动态，不会由评论、群聊或世界事件追加。", settings.dailyAutoMomentEnabled, enabled = autoEnabled) {
                settings.dailyAutoMomentEnabled = it
            }
            SettingsParamSlider(settings, "daily_moment_target", "每角色每日动态数", 1, 0f..3f, "每位开启动态权限的角色每天固定发布几条。角色多时信息流会相应增多。", step = 1f, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_min_chars", "动态最少字数", 50, 20f..300f, "建议 20-50。", step = 5f, pairKey = "moment_max_chars", isMinSide = true, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_max_chars", "动态最多字数", 200, 80f..500f, "建议 120-200。", step = 5f, pairKey = "moment_min_chars", isMinSide = false, enabled = autoEnabled)
            SettingsSectionTitle("主动私聊")
            InfoCard("自然分散发送", "每天会从已开启私聊权限、且有聊天或记忆依据的角色中抽取。每人当天最多主动联系一次；刚和该角色聊过 15 分钟内不会再主动开场。")
            SettingsParamSlider(settings, "daily_proactive_chance", "角色每日主动概率", 80, 0f..100f, "每个开启私聊权限的角色每天独立参与抽取的概率。", step = 5f, enabled = autoEnabled)
            SettingsParamSlider(settings, "daily_proactive_max", "每天最多主动角色数", 5, 0f..20f, "当天最多有多少位角色主动联系你。", step = 1f, enabled = autoEnabled)
            SettingsSwitchCard("免打扰时段", "开启后，主动私聊会避开下方设定的时段并顺延；动态不受影响。", settings.quietHoursEnabled, enabled = autoEnabled) { settings.quietHoursEnabled = it }
            SettingsParamSlider(settings, "quiet_hours_start", "免打扰开始时间", 1, 0f..23f, "按北京时间整点设置。", step = 1f, enabled = autoEnabled && settings.quietHoursEnabled)
            SettingsParamSlider(settings, "quiet_hours_end", "免打扰结束时间", 9, 0f..23f, "跨午夜时可将结束时间设为较小的小时数。", step = 1f, enabled = autoEnabled && settings.quietHoursEnabled)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun InfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
    Spacer(Modifier.height(10.dp))
}
