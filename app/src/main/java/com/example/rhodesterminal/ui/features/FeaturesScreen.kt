package com.example.rhodesterminal.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.example.rhodesterminal.ui.theme.*

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
    onMoments: () -> Unit = {},
    onDiary: () -> Unit = {},
    onRanking: () -> Unit = {},
    onImpressions: () -> Unit = {},
    onDispatch: () -> Unit = {},
    onTokenStats: () -> Unit = {},
    onMahjong: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("功能", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            FeatureButton(FeatureEntry(Icons.Default.Share, "动态广场", "查看所有干员发布的动态", badge = momentBadge, iconColor = Primary), commentBadge = commentBadge, onClick = onMoments)
            FeatureButton(FeatureEntry(Icons.AutoMirrored.Filled.MenuBook, "干员日记", "查看干员们的内心独白", iconColor = Primary), onClick = onDiary)
            FeatureButton(FeatureEntry(Icons.Default.EmojiEvents, "聊天排行榜", "昨日聊天数据排名", iconColor = Primary), onClick = onRanking)
            FeatureButton(FeatureEntry(Icons.AutoMirrored.Filled.Assignment, "大家的印象", "干员对你的长期印象总结", iconColor = Primary), onClick = onImpressions)
            FeatureButton(FeatureEntry(Icons.AutoMirrored.Filled.SendToMobile, "干员派遣", "组建小队执行任务", iconColor = Primary), onClick = onDispatch)
            FeatureButton(FeatureEntry(Icons.Default.BarChart, "消费统计", "Token消耗分析", iconColor = Primary), onClick = onTokenStats)
            FeatureButton(FeatureEntry(Icons.Default.EmojiEvents, "打麻将", "在活动室和干员们打一局麻将", iconColor = AccentOrange), onClick = onMahjong)
        }
    }
}

@Composable
private fun FeatureButton(feature: FeatureEntry, commentBadge: Int = 0, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(Surface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(feature.iconColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Icon(feature.icon, null, tint = feature.iconColor, modifier = Modifier.size(22.dp))
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
                    if (feature.badge > 0 || commentBadge > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        val badgeText = when {
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
    }
    HorizontalDivider(color = BG)
}
