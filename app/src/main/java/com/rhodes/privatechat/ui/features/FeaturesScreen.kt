package com.rhodes.privatechat.ui.features

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.ui.common.WechatIconTile
import com.rhodes.privatechat.ui.common.WechatListGroup
import com.rhodes.privatechat.ui.common.WechatTopBar

data class FeatureEntry(
    val icon: ImageVector,
    val title: String,
    val desc: String,
    val badge: Int = 0,
    val badgeColor: Color = ErrorRed,
    val iconColor: Color = Primary
)

@Composable
fun FeaturesScreen(
    momentBadge: Int = 0,
    commentBadge: Int = 0,
    diaryBadge: Int = 0,
    onMoments: () -> Unit = {},
    onDiary: () -> Unit = {},
    onRanking: () -> Unit = {},
    onImpressions: () -> Unit = {},
    onDispatch: () -> Unit = {},
    onTokenStats: () -> Unit = {},
    onGameRoom: () -> Unit = {},
    onSleep: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(BG)) {
        WechatTopBar("功能")

        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp).navigationBarsPadding()) {
            WechatListGroup {
                FeatureButton(FeatureEntry(Icons.Default.Share, "动态广场", "查看所有干员发布的动态", badge = momentBadge, iconColor = Color(0xFF07C160)), commentBadge = commentBadge, onClick = onMoments)
                FeatureButton(FeatureEntry(Icons.AutoMirrored.Filled.MenuBook, "干员日记", "查看干员们的内心独白", badge = diaryBadge, iconColor = Color(0xFF1989FA)), diaryBadge = diaryBadge, onClick = onDiary)
                FeatureButton(FeatureEntry(Icons.AutoMirrored.Filled.Assignment, "大家的印象", "干员对你的长期印象总结", iconColor = Color(0xFFFF9500)), onClick = onImpressions)
            }
            Spacer(Modifier.height(8.dp))
            WechatListGroup {
                FeatureButton(FeatureEntry(Icons.AutoMirrored.Filled.SendToMobile, "干员派遣", "组建小队执行任务", iconColor = Color(0xFF8B5CF6)), onClick = onDispatch)
            }
            Spacer(Modifier.height(8.dp))
            WechatListGroup {
                FeatureButton(FeatureEntry(Icons.Default.EmojiEvents, "聊天排行榜", "昨日聊天数据排名", iconColor = Color(0xFFFFB300)), onClick = onRanking)
                FeatureButton(FeatureEntry(Icons.Default.BarChart, "消费统计", "Token消耗分析", iconColor = Color(0xFF00BCD4)), onClick = onTokenStats)
                FeatureButton(FeatureEntry(Icons.Default.Casino, "游戏室", "麻将、斗地主、跑得快", iconColor = Color(0xFFFF8F00)), onClick = onGameRoom)
                FeatureButton(FeatureEntry(Icons.Default.Bedtime, "陪睡", "凯尔希陪睡语音模式", iconColor = Color(0xFF7C4DFF)), onClick = onSleep)
            }
        }
    }
}

@Composable
private fun FeatureButton(feature: FeatureEntry, commentBadge: Int = 0, diaryBadge: Int = 0, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                WechatIconTile(feature.icon, feature.iconColor)
                Box {
                    if (feature.badge > 0) {
                        Box(modifier = Modifier.size(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ErrorRed)
                            .align(Alignment.TopEnd).padding(0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${feature.badge}", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(feature.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    if (feature.badge > 0 || commentBadge > 0 || diaryBadge > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val badgeText = when {
                            diaryBadge > 0 -> "${diaryBadge}篇新日记"
                            feature.badge > 0 && commentBadge > 0 -> "${feature.badge}条未读动态  ${commentBadge}条新回复"
                            feature.badge > 0 -> "${feature.badge}条未读动态"
                            else -> "${commentBadge}条新回复"
                        }
                        Text(badgeText, fontSize = 11.sp, color = ErrorRed)
                    }
                }
                Text(feature.desc, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
            }
        }
        HorizontalDivider(color = Divider.copy(alpha = 0.45f), modifier = Modifier.padding(start = 70.dp))
    }
}
