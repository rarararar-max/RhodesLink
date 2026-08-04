package com.rhodes.privatechat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.common.AppBackground
import com.rhodes.privatechat.ui.common.GradientHeader
import com.rhodes.privatechat.ui.theme.AppearanceSkin
import com.rhodes.privatechat.ui.theme.Card
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.Stroke
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextTertiary
import com.rhodes.privatechat.ui.theme.currentAppearanceSkin
import com.rhodes.privatechat.ui.theme.isDarkMode
import org.koin.compose.koinInject

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings: SettingsRepository = koinInject()
    AppBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            GradientHeader("外观设置", onBack = onBack, icon = Icons.Default.Palette)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                Text("界面皮肤", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "选择后立即应用并自动保存。经典皮肤保持原有黑白界面。",
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    fontSize = 12.sp,
                    color = TextSecondary,
                )
                AppearanceSkin.entries.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { skin ->
                            SkinCard(
                                skin = skin,
                                selected = skin == currentAppearanceSkin,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentAppearanceSkin = skin
                                    isDarkMode = skin.isDark
                                    settings.appearanceSkin = skin.storageValue
                                    settings.darkMode = skin.isDark
                                },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text("皮肤说明", modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    "罗德岛终端皮肤会统一替换应用的色彩、信息层级与强调色；聊天记录和功能入口不会改变。",
                    modifier = Modifier.padding(top = 5.dp),
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    color = TextTertiary,
                )
            }
        }
    }
}

@Composable
private fun SkinCard(skin: AppearanceSkin, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val border = if (selected) Primary else Stroke
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        SkinPreview(skin, selected)
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(skin.title, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(Primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, "使用中", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(skin.subtitle, modifier = Modifier.padding(top = 2.dp), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = if (selected) Primary else TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(skin.description, modifier = Modifier.padding(top = 5.dp), fontSize = 10.sp, lineHeight = 14.sp, color = TextSecondary, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SkinPreview(skin: AppearanceSkin, selected: Boolean) {
    val colors = when (skin) {
        AppearanceSkin.ClassicLight -> PreviewColors(Color(0xFFF2F2F7), Color.White, Color(0xFF4A7FDC), Color(0xFFE8E8ED), Color(0xFF1C1C1E))
        AppearanceSkin.ClassicDark -> PreviewColors(Color(0xFF1A1A1E), Color(0xFF2E2E32), Color(0xFF6B8CFF), Color(0xFF323238), Color(0xFFE8E8EC))
        AppearanceSkin.RhodesDay -> PreviewColors(Color(0xFFE7E9E8), Color(0xFFF9FAF7), Color(0xFFD96700), Color(0xFFE1E4E1), Color(0xFF1B2020))
        AppearanceSkin.RhodesNight -> PreviewColors(Color(0xFF15171C), Color(0xFF2A3039), Color(0xFFFF7800), Color(0xFF292F38), Color(0xFFE9ECE8))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.35f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.background)
            .border(1.dp, colors.foreground.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(Brush.horizontalGradient(listOf(colors.surface, colors.background)))
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(3.dp).background(colors.accent))
                Spacer(Modifier.width(4.dp))
                Text(if (skin.name.startsWith("Rhodes")) "TERMINAL" else "CHAT", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = colors.foreground)
            }
            Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.fillMaxWidth(0.70f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(colors.surface))
                Box(Modifier.fillMaxWidth(0.86f).height(9.dp).clip(RoundedCornerShape(3.dp)).background(colors.input))
                Box(Modifier.align(Alignment.End).fillMaxWidth(0.57f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(colors.accent))
            }
        }
        if (selected) Box(Modifier.align(Alignment.TopEnd).width(18.dp).height(3.dp).background(colors.accent))
    }
}

private data class PreviewColors(val background: Color, val surface: Color, val accent: Color, val input: Color, val foreground: Color)
