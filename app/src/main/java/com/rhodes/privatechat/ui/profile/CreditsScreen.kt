package com.rhodes.privatechat.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("感谢名单", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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

            // 策划/AI架构
            Text("策划/AI架构/基础开发", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "@不自动售货机",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Primary,
                modifier = Modifier.clickable { openUrl("https://space.bilibili.com/599298") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 开发
            Text("开发/代码架构重构", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "@Polymer_Meteor",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Primary,
                modifier = Modifier.clickable { openUrl("https://space.bilibili.com/1871689796") }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // 美术
            Text("美术", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "@Nebula",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Primary,
                modifier = Modifier.clickable { openUrl("https://xhslink.com/m/973GEqQSle2") }
            )
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color(0xFF3A3A3E))
            Text("版本 1.12", fontSize = 12.sp, color = Color(0xFF636366), modifier = Modifier.padding(bottom = 2.dp))
            Text("© 2026 Rhodes Terminal", fontSize = 12.sp, color = Color(0xFF636366), modifier = Modifier.padding(bottom = 16.dp))
            SponsorSection()
        }
    }
    }
}

// ─── 赞助商名单 ───

private data class Sponsor(val name: String, val amount: String, val platform: String)

@Composable
private fun SponsorSection() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("成为赞助商", "微信", "哔哩哔哩", "支付宝", "爱发电", "QQ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("展示的数据来自：4月5日 - 6月10日", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(12.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Primary,
            edgePadding = 0.dp
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected = selectedTab == i,
                    onClick = { selectedTab = i },
                    text = { Text(title, fontSize = 13.sp, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) },
                    selectedContentColor = Primary,
                    unselectedContentColor = TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            SponsorQRList()
        } else {
            SponsorList(platform = tabs[selectedTab])
        }
    }
}

@Composable
private fun SponsorQRList() {
    val qrIds = listOf(
        com.rhodes.privatechat.R.drawable.sponsor_qr_1,
        com.rhodes.privatechat.R.drawable.sponsor_qr_2,
        com.rhodes.privatechat.R.drawable.sponsor_qr_3,
        com.rhodes.privatechat.R.drawable.sponsor_qr_4
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        qrIds.forEach { qrId ->
            Image(
                painter = painterResource(qrId),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(0.7f).padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun SponsorList(platform: String) {
    val filtered = sponsors.filter { it.platform == platform }
        .sortedByDescending { it.amount.removePrefix("¥").toDoubleOrNull() ?: 0.0 }
    Column(modifier = Modifier.fillMaxWidth()) {
        filtered.forEach { s ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
                Text(s.name, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                Text(s.amount, fontSize = 13.sp, color = Primary)
            }
        }
    }
}

private val sponsors = listOf(
    Sponsor("AAA食指传令员", "¥20.00", "QQ"),
    Sponsor("Ailkes（L_X）", "¥5.00", "QQ"),
    Sponsor("Sally face", "¥115.00", "QQ"),
    Sponsor("TATOBA", "¥54.00", "QQ"),
    Sponsor("Z / MU", "¥70.00", "QQ"),
    Sponsor("\"顿河\"", "¥75.00", "QQ"),
    Sponsor("七月", "¥52.00", "QQ"),
    Sponsor("之心（瓷岁）", "¥50.00", "QQ"),
    Sponsor("亲亲羽毛笔", "¥10.00", "QQ"),
    Sponsor("今夜无事发生", "¥138.00", "QQ"),
    Sponsor("你已经成为群主了", "¥50.00", "QQ"),
    Sponsor("切利尼娜·德克萨斯", "¥8.11", "QQ"),
    Sponsor("吉良吉影", "¥7.80", "QQ"),
    Sponsor("咲织", "¥10.00", "QQ"),
    Sponsor("我真的是黍厨", "¥5.00", "QQ"),
    Sponsor("扇山·珊", "¥25.00", "QQ"),
    Sponsor("新三国大学士", "¥60.00", "QQ"),
    Sponsor("无敌黍黍[秽土转生]", "¥25.00", "QQ"),
    Sponsor("柴喵喵", "¥24.29", "QQ"),
    Sponsor("橘", "¥5.00", "QQ"),
    Sponsor("猫头鹰", "¥1.00", "QQ"),
    Sponsor("老白", "¥5.00", "QQ"),
    Sponsor("色鱼大罪", "¥5.00", "QQ"),
    Sponsor("苏uVC", "¥9.00", "QQ"),
    Sponsor("莉莉霍瓦特", "¥50.00", "QQ"),
    Sponsor("这辈子就是被粉毛给害了", "¥15.05", "QQ"),
    Sponsor("迟夏饮冰", "¥0.11", "QQ"),
    Sponsor("飛翔的天空丶", "¥20.00", "QQ"),
    Sponsor("F-Kurisu", "¥60.00", "哔哩哔哩"),
    Sponsor("oRotSchwarzo", "¥6.00", "哔哩哔哩"),
    Sponsor("simecAWA", "¥12.00", "哔哩哔哩"),
    Sponsor("五名无名a", "¥6.00", "哔哩哔哩"),
    Sponsor("僮额玄恋酱", "¥6.00", "哔哩哔哩"),
    Sponsor("咕咕咕咕一_", "¥18.00", "哔哩哔哩"),
    Sponsor("小乐一下-", "¥24.00", "哔哩哔哩"),
    Sponsor("快乐的星野厨", "¥6.00", "哔哩哔哩"),
    Sponsor("梅老大大", "¥12.00", "哔哩哔哩"),
    Sponsor("没名字了_QAQ", "¥30.00", "哔哩哔哩"),
    Sponsor("洡良", "¥12.00", "哔哩哔哩"),
    Sponsor("胜邪毫曹", "¥60.00", "哔哩哔哩"),
    Sponsor("阿米娅第一可爱1", "¥60.00", "哔哩哔哩"),
    Sponsor("*L", "¥10.00", "微信"),
    Sponsor("*ฅ", "¥50.00", "微信"),
    Sponsor("*乐", "¥50.00", "微信"),
    Sponsor("*人", "¥110.00", "微信"),
    Sponsor("*华", "¥10.00", "微信"),
    Sponsor("*博", "¥70.00", "微信"),
    Sponsor("*器", "¥10.00", "微信"),
    Sponsor("*宇", "¥30.00", "微信"),
    Sponsor("*尔", "¥170.00", "微信"),
    Sponsor("*尘", "¥10.00", "微信"),
    Sponsor("*川", "¥8.00", "微信"),
    Sponsor("*得", "¥10.00", "微信"),
    Sponsor("*斯", "¥10.00", "微信"),
    Sponsor("*是", "¥3.25", "微信"),
    Sponsor("*杆", "¥100.00", "微信"),
    Sponsor("*来", "¥30.00", "微信"),
    Sponsor("*梨", "¥5.00", "微信"),
    Sponsor("*武", "¥50.00", "微信"),
    Sponsor("*狐", "¥31.00", "微信"),
    Sponsor("*狗", "¥40.00", "微信"),
    Sponsor("*生", "¥10.00", "微信"),
    Sponsor("*白", "¥5.00", "微信"),
    Sponsor("*石", "¥114.00", "微信"),
    Sponsor("*美", "¥100.00", "微信"),
    Sponsor("*羽", "¥50.00", "微信"),
    Sponsor("*菌", "¥10.00", "微信"),
    Sponsor("*謙", "¥29.00", "微信"),
    Sponsor("*！", "¥20.00", "微信"),
    Sponsor("*🎈", "¥88.00", "微信"),
    Sponsor("A*a", "¥10.00", "微信"),
    Sponsor("N*o", "¥10.00", "微信"),
    Sponsor("i*n", "¥12.23", "微信"),
    Sponsor("k*t", "¥100.00", "微信"),
    Sponsor("m*r", "¥50.00", "微信"),
    Sponsor("u*h", "¥5.00", "微信"),
    Sponsor("**凯", "¥100.00", "支付宝"),
    Sponsor("**帆", "¥10.00", "支付宝"),
    Sponsor("**洋", "¥6.00", "支付宝"),
    Sponsor("**涵", "¥50.00", "支付宝"),
    Sponsor("**炜", "¥900.00", "支付宝"),
    Sponsor("*晴", "¥6.66", "支付宝"),
    Sponsor("13905153991", "¥15.00", "爱发电"),
    Sponsor("PolairsT", "¥5.00", "爱发电"),
    Sponsor("Yunchuan~For Love", "¥5.00", "爱发电"),
    Sponsor("一视桐人", "¥5.00", "爱发电"),
    Sponsor("上课", "¥5.20", "爱发电"),
    Sponsor("伏随梦", "¥5.00", "爱发电"),
    Sponsor("八月、某、藍二乗", "¥30.00", "爱发电"),
    Sponsor("唐元", "¥5.00", "爱发电"),
    Sponsor("好丽友派", "¥5.00", "爱发电"),
    Sponsor("小白", "¥5.00", "爱发电"),
    Sponsor("星痕", "¥10.00", "爱发电"),
    Sponsor("月落星野", "¥5.00", "爱发电"),
    Sponsor("爱发电用户_83ca5", "¥5.00", "爱发电"),
    Sponsor("爱发电用户_96BM", "¥5.00", "爱发电"),
    Sponsor("爱发电用户_9WNE", "¥5.00", "爱发电"),
    Sponsor("爱发电用户_WXKp", "¥5.00", "爱发电"),
    Sponsor("爱发电用户_e41e5", "¥50.00", "爱发电"),
    Sponsor("爱发电用户_vskV", "¥100.00", "爱发电"),
    Sponsor("王胖子", "¥30.00", "爱发电"),
    Sponsor("鸡旭", "¥10.00", "爱发电"),
    Sponsor("ew永远的维多利", "¥60.00", "爱发电"),
)
