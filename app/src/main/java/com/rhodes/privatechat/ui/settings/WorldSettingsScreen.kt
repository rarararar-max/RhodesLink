package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.rhodes.privatechat.ui.theme.*
import org.koin.compose.koinInject

@Composable
fun WorldSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    Column(modifier = modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.AutoAwesome, null, tint = Primary)
            Spacer(Modifier.width(6.dp))
            Text("世界与动态", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SettingsSectionTitle("世界运行")
            SettingsSwitchCard("世界调度（总开关）", "开：世界会根据评论、群聊、日记、状态等事件自动运转。关：以下所有功能都停用。通常建议开着，体验更丰富。", settings.worldSchedulerEnabled) { settings.worldSchedulerEnabled = it }
            SettingsSwitchCard("世界调度触发动态", "开：有评论、群聊话题、状态变化时，AI干员可能会主动发朋友圈动态。受每人每日动态目标和自动AI次数限制。", settings.autoMomentEnabled) { settings.autoMomentEnabled = it }
            SettingsSwitchCard("世界调度触发群聊", "开：有动态或评论话题时，AI群聊可能会自动接住话题聊起来。受群聊自动开关和自动AI次数限制。", settings.worldAutoGroupEnabled) { settings.worldAutoGroupEnabled = it }
            SettingsParamSlider(settings, "daily_world_event_limit", "每日世界事件上限", 30, 0f..200f, "世界事件就像罗德岛的「每日新闻」，每天最多记录多少条。设太少了新鲜事会丢，设太多了AI聊天时信息太杂。建议20-50条。", step = 1f)
            SettingsParamSlider(settings, "event_context_count", "事件上下文数量", 5, 0f..20f, "每次聊天、动态或日记时，最多参考几条尚未处理的罗德岛新鲜事。建议3-8条，多了AI会分心。", step = 1f)
            SettingsParamSlider(settings, "daily_auto_ai_limit", "每日自动AI调用上限", 40, 0f..500f, "自动动态、自动评论、自动日记、主动私聊等后台行为，每天总共消耗多少次AI调用。手动聊天不受限制。设0=关闭所有自动AI行为。建议30-50次。", step = 5f)
            SettingsParamSlider(settings, "tick_auto_ai_limit", "单次调度AI调用上限", 3, 0f..50f, "每次世界调度中最多触发几次自动AI，防止评论和动态连环套层层消耗AI额度。建议3-5次。", step = 1f)

            SettingsSectionTitle("朋友圈动态")
            SettingsParamSlider(settings, "daily_moment_target", "每人每日动态目标", 2, 1f..10f, "每名有动态权限的干员每天最多自动发几条朋友圈。设太大首页会被刷屏。建议1-3条。", step = 1f)
            SettingsParamSlider(settings, "moment_min_chars", "动态最少字数", 50, 20f..300f, "每名干员发动态最少写几个字。太短了没内容，建议20-50字。", step = 5f, pairKey = "moment_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "moment_max_chars", "动态最多字数", 200, 80f..500f, "每名干员发动态最多写几个字。太长了像写作文，建议150-250字。", step = 5f, pairKey = "moment_min_chars", isMinSide = false)
            SettingsParamSlider(settings, "moment_anchor_count", "动态参考记忆", 3, 0f..10f, "生成动态时最多参考几条公开记忆。设太多每条动态都提旧事，设太少动态内容泛泛。建议3-5条。", step = 1f)
            SettingsParamSlider(settings, "moment_recent_post_count", "近期动态参考", 3, 0f..10f, "生成动态时最多参考该干员最近几条动态，避免连续发一样的话题。建议2-3条。", step = 1f)
            SettingsParamSlider(settings, "moment_user_related_rate", "用户相关概率", 20, 0f..100f, "干员发动态时自然提到用户的概率。只影响公开可见的日常事件，不会泄露私聊隐私。建议10-30%。", step = 5f)

            SettingsSectionTitle("评论与互动")
            SettingsParamSlider(settings, "comment_min_chars", "评论最少字数", 10, 5f..30f, "干员评论动态最少写几个字。建议10-15字。", step = 1f, pairKey = "comment_max_chars", isMinSide = true)
            SettingsParamSlider(settings, "comment_max_chars", "评论最多字数", 40, 10f..100f, "干员评论动态最多写几个字。建议30-60字，评论不是写文章。", step = 5f, pairKey = "comment_min_chars", isMinSide = false)
            SettingsParamSlider(settings, "comment_context_count", "评论上下文条数", 5, 0f..20f, "AI回复评论时最多回看几条评论区前面的对话。建议3-5条，多了反而乱。", step = 1f)
            SettingsParamSlider(settings, "comment_memory_count", "评论参考记忆", 2, 0f..10f, "AI回复评论时最多参考几条与该干员有关的公开记忆。建议1-2条。", step = 1f)
            SettingsParamSlider(settings, "comment_bystander_min", "围观评论最少人数", 1, 0f..10f, "你评论动态后，最少有几个不在现场的干员碰巧看到并参与评论。建议1-2个。", step = 1f, pairKey = "comment_bystander_max", isMinSide = true)
            SettingsParamSlider(settings, "comment_bystander_max", "围观评论最多人数", 3, 0f..10f, "你评论动态后，最多有几个不在现场的干员碰巧看到并参与评论。受自动AI次数限制。建议2-3个。", step = 1f, pairKey = "comment_bystander_min", isMinSide = false)
            SettingsParamSlider(settings, "comment_to_private_trigger_rate", "评论触发私聊强度", 30, 0f..100f, "你在动态下评论后，相关干员可能主动私聊你。设越高越容易触发。还受主动消息权限和冷却时间影响。建议20-40%。", step = 5f)
            SettingsParamSlider(settings, "moment_to_group_trigger_rate", "动态触发群聊强度", 40, 0f..100f, "动态或评论话题可能被自动群聊接住开始聊。设越高越容易接话题。建议30-50%。", step = 5f)
            SettingsParamSlider(settings, "moment_trigger_strength", "动态触发强度", 50, 0f..100f, "有世界事件（评论/群聊等）时触发自动动态的概率。设越高干员越爱发朋友圈。建议40-60%。", step = 5f)
            SettingsParamSlider(settings, "group_trigger_strength", "群聊触发强度", 50, 0f..100f, "有世界事件时刷新自动群聊的概率。设越高群聊越活跃。建议40-60%。", step = 5f)
        }
    }
}
