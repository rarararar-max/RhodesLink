package com.example.rhodesterminal.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rhodesterminal.ui.theme.Primary
import com.example.rhodesterminal.ui.theme.TextPrimary
import com.example.rhodesterminal.ui.theme.TextSecondary
import com.example.rhodesterminal.ui.theme.BG

@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().background(BG)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            Text("鸣谢", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Color(0xFF3A3A3E))

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Icon(Icons.Default.Favorite, null, tint = Primary, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("罗德岛终端", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Arknights Role-Playing Chat", fontSize = 12.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(24.dp))

            listOf(
                "DeepSeek AI" to "提供对话生成能力",
                "Jetpack Compose" to "Android 现代化 UI 框架",
                "Room Database" to "本地数据持久化",
                "OkHttp" to "网络请求库",
                "Coil" to "图片加载库",
                "Gson" to "JSON 解析"
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
