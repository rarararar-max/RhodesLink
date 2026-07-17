package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun DailyContentSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    val viewModel: MainViewModel = koinInject()
    var autoEnabled by remember { mutableStateOf(settings.autoAiEnabled) }
    var dailyMomentsEnabled by remember { mutableStateOf(settings.dailyAutoMomentEnabled) }
    var proactiveEnabled by remember { mutableStateOf(settings.idleProactiveChatEnabled) }
    var quietHoursEnabled by remember { mutableStateOf(settings.quietHoursEnabled) }
    SaveableSettingsScaffold(
        title = "每日自动内容",
        onBack = onBack,
        modifier = modifier.fillMaxSize().background(BG).systemBarsPadding(),
        icon = { Icon(Icons.Default.AutoAwesome, null, tint = Primary) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).imePadding().navigationBarsPadding()) {
            DailyContentInfoCard("固定日计划", "每天按北京时间 00:00 开始新的内容周期。动态和私聊会预先分散安排，并在应用关闭时由系统后台任务投递；系统省电策略可能让实际到达时间略晚。")
            SettingsSectionTitle("总开关")
            SettingsSwitchCard("自动内容", "关闭后不再生成计划动态或主动私聊；手动聊天、手动催发动态不受影响。", autoEnabled) {
                autoEnabled = it
                settings.autoAiEnabled = it
                viewModel.refreshAutoGroupChats()
            }
            SettingsSectionTitle("每日动态")
            SettingsSwitchCard("每日固定动态", "开启动态权限的角色每天会生成设定数量的公开动态。", dailyMomentsEnabled, enabled = autoEnabled) {
                dailyMomentsEnabled = it
                settings.dailyAutoMomentEnabled = it
            }
            SettingsParamSlider(settings, "daily_moment_target", "每角色每日动态数", 1, 0f..3f, "每个角色每天自动发几条动态。建议1。太高（超过3）信息流刷屏太快看不过来。", step = 1f, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_min_chars", "动态最少字数", 50, 20f..300f, "每条动态最少写几个字。建议20-50。太低（低于10）只有几个字太敷衍。", step = 5f, pairKey = "moment_max_chars", isMinSide = true, enabled = autoEnabled)
            SettingsParamSlider(settings, "moment_max_chars", "动态最多字数", 200, 80f..500f, "每条动态最多写几个字。建议120-200。太高（超过300）每条都是小作文。", step = 5f, pairKey = "moment_min_chars", isMinSide = false, enabled = autoEnabled)
            SettingsSectionTitle("主动私聊")
            DailyContentInfoCard("自然分散发送", "每天从有私聊权限的角色中抽人主动找你。每人每天最多一次；刚聊过15分钟内不会又来。")
            SettingsSwitchCard("主动私聊", "开启后，已授予主动消息权限且有聊天上下文的干员可能主动联系你。", proactiveEnabled, enabled = autoEnabled) {
                proactiveEnabled = it
                settings.idleProactiveChatEnabled = it
            }
            SettingsParamSlider(settings, "daily_proactive_chance", "角色每日主动概率", 80, 0f..100f, "每个角色每天可能主动找你的概率。建议80。太低（低于30）基本没人找你，太高（100）每天都有很多人来。", step = 5f, enabled = autoEnabled && proactiveEnabled)
            SettingsParamSlider(settings, "daily_proactive_max", "每天最多主动角色数", 5, 0f..20f, "每天最多有多少个角色主动找你。建议5。太低（低于2）每天就一两个人来，太高（超过10）消息太多看不过来。", step = 1f, enabled = autoEnabled && proactiveEnabled)
            SettingsSwitchCard("免打扰时段", "开启后主动私聊会避开这个时段，过后再发。动态照常。", quietHoursEnabled, enabled = autoEnabled && proactiveEnabled) {
                quietHoursEnabled = it
                settings.quietHoursEnabled = it
            }
            SettingsParamSlider(settings, "quiet_hours_start", "免打扰开始时间", 1, 0f..23f, "几点开始不打扰（24小时制）。建议凌晨1点。", step = 1f, enabled = autoEnabled && proactiveEnabled && quietHoursEnabled)
            SettingsParamSlider(settings, "quiet_hours_end", "免打扰结束时间", 9, 0f..23f, "几点恢复（24小时制）。建议早上9点。", step = 1f, enabled = autoEnabled && proactiveEnabled && quietHoursEnabled)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DailyContentInfoCard(title: String, body: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(14.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
    Spacer(Modifier.height(10.dp))
}
