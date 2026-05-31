package com.rhodes.privatechat.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary

@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    Column(modifier = modifier.fillMaxSize().systemBarsPadding().background(BG)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("关于", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Color(0xFF3A3A3E))

        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Favorite, null, tint = Primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("罗德岛终端", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Arknights Role-Playing Chat", fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(24.dp))

            // 作者
            Text("作者", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "不自动售货机",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Primary,
                modifier = Modifier.clickable { openUrl("https://space.bilibili.com/599298") }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Polymer_Meteor",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Primary,
                modifier = Modifier.clickable { openUrl("https://space.bilibili.com/1871689796") }
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 开源库致谢
            Text("开源库致谢", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(12.dp))

            listOf(
                "Jetpack Compose" to "Android 现代化 UI 框架",
                "Voyager" to "页面导航与路由",
                "Koin" to "轻量级依赖注入",
                "Ktor" to "跨平台网络请求（Android 端基于 OkHttp）",
                "kotlinx.serialization" to "跨平台 JSON 序列化",
                "SQLDelight" to "跨平台本地数据持久化",
                "Coil" to "图片加载库",
                "DeepSeek AI" to "对话生成能力"
            ).forEach { (name, desc) ->
                Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(desc, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("版本 1.0", fontSize = 12.sp, color = Color(0xFF636366))
            Text("© 2026 Rhodes Terminal", fontSize = 12.sp, color = Color(0xFF636366))
        }
    }
}
