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
            Text("感谢名单与联系方式", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
            Text("版本 1.14.1", fontSize = 12.sp, color = Color(0xFF636366), modifier = Modifier.padding(bottom = 2.dp))
            Text("© 2026 Rhodes Terminal", fontSize = 12.sp, color = Color(0xFF636366), modifier = Modifier.padding(bottom = 16.dp))
            SponsorSection()
        }
    }
    }
}

// ─── 赞助商名单 ───

private data class Sponsor(val name: String, val amount: String, val platform: String, val note: String = "")

@Composable
private fun SponsorSection() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("赞助与联系方式", "微信", "哔哩哔哩", "支付宝", "爱发电", "QQ")

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        Text("展示的数据来自：6月11日 - 8月18日", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(horizontal = 16.dp))
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
        com.rhodes.privatechat.R.drawable.sponsor_qr_4,
        com.rhodes.privatechat.R.drawable.sponsor_qr_1,
        com.rhodes.privatechat.R.drawable.sponsor_qr_2,
        com.rhodes.privatechat.R.drawable.sponsor_qr_3
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.name, fontSize = 13.sp, color = TextPrimary)
                    if (s.note.isNotBlank()) Text(s.note, fontSize = 11.sp, color = TextSecondary)
                }
                Text(s.amount, fontSize = 13.sp, color = Primary)
            }
        }
    }
}

private val sponsors = listOf(
    Sponsor("西门寺幽幽子", "¥100.00", "QQ"), Sponsor("☙繁星❁✨", "¥100.00", "QQ"), Sponsor("千语桐江", "¥100.00", "QQ"),
    Sponsor("☙繁星❁✨", "¥50.00", "QQ"), Sponsor("☙繁星❁✨", "¥50.00", "QQ"),
    Sponsor("你已经成为群主了，和群员打招呼吧！", "¥50.00", "QQ"), Sponsor("你已经成为群主了，和群员打招呼吧！", "¥50.00", "QQ"),
    Sponsor("旗", "¥45.01", "QQ"), Sponsor("☙繁星❁✨", "¥30.00", "QQ"), Sponsor("罗曼诺娃", "¥20.00", "QQ"),
    Sponsor("夜色不想上班ya（接毛版）", "¥20.00", "QQ"), Sponsor("健康哥哥", "¥18.00", "QQ"), Sponsor("bronx", "¥14.19", "QQ"),
    Sponsor("bronx", "¥11.45", "QQ"), Sponsor("调教大王", "¥10.00", "QQ"), Sponsor("和狐狸哈斯哈斯", "¥10.00", "QQ"),
    Sponsor("和狐狸哈斯哈斯", "¥10.00", "QQ"), Sponsor("👁AAA源石碎片批发商🎩帽子小姐", "¥10.00", "QQ"), Sponsor("爽食芭菲奈", "¥10.00", "QQ"),
    Sponsor("健康哥哥", "¥12.00", "QQ"), Sponsor("沐风游月", "¥9.43", "QQ"), Sponsor("TATOBA", "¥8.00", "QQ"), Sponsor("兽耳控", "¥8.00", "QQ"),
    Sponsor("爽食芭菲奈", "¥5.00", "QQ"), Sponsor("迟夏饮冰", "¥5.00", "QQ"), Sponsor("橘前辈", "¥5.00", "QQ"), Sponsor("TATOBA", "¥5.00", "QQ"), Sponsor("莉墨绫", "¥5.00", "QQ"),
    Sponsor("根本没有人陪我打游戏王", "¥5.00", "QQ"), Sponsor("老白", "¥5.00", "QQ"), Sponsor("亲亲羽毛笔", "¥5.00", "QQ"), Sponsor("亲亲羽毛笔", "¥5.00", "QQ"),
    Sponsor("林筱霞", "¥5.20", "QQ"), Sponsor("林筱霞", "¥5.20", "QQ"), Sponsor("黑白黎", "¥5.20", "QQ"), Sponsor("黑白黎", "¥5.20", "QQ"), Sponsor("柴喵喵", "¥5.15", "QQ"),
    Sponsor("牢橘", "¥4.00", "QQ"), Sponsor("牢橘", "¥4.00", "QQ"), Sponsor("沐风游月", "¥3.00", "QQ"), Sponsor("TATOBA", "¥3.00", "QQ"), Sponsor("トロテム", "¥5.00", "QQ"), Sponsor("トロテム", "¥3.25", "QQ"), Sponsor("QQ转账", "¥5.00", "QQ"), Sponsor("亲亲羽毛笔(喻乐)", "¥10.00", "QQ"), Sponsor("亲亲羽毛笔", "¥7.00", "QQ"),
    Sponsor("时空梦游者", "¥27.00", "哔哩哔哩"), Sponsor("时空梦游者", "¥27.00", "哔哩哔哩"), Sponsor("小乐一下-", "¥36.00", "哔哩哔哩"), Sponsor("灬灬AIR", "¥30.00", "哔哩哔哩"), Sponsor("F-Kurisu", "¥25.00", "哔哩哔哩"), Sponsor("L1KDEh", "¥15.00", "哔哩哔哩"), Sponsor("咕咕咕咕一_", "¥6.00", "哔哩哔哩"), Sponsor("鳊鲧", "¥6.00", "哔哩哔哩"), Sponsor("芜白生", "¥6.00", "哔哩哔哩"), Sponsor("simecAWA", "¥5.00", "哔哩哔哩"), Sponsor("simecAWA", "¥5.00", "哔哩哔哩"), Sponsor("精灵妙妙屋", "¥5.00", "哔哩哔哩"), Sponsor("精灵妙妙屋", "¥5.00", "哔哩哔哩"), Sponsor("洡良", "¥5.00", "哔哩哔哩"), Sponsor("洡良", "¥5.00", "哔哩哔哩"), Sponsor("冽萧", "¥5.00", "哔哩哔哩"), Sponsor("梅老大大", "¥5.00", "哔哩哔哩"), Sponsor("和狐狸哈斯哈斯", "¥5.00", "哔哩哔哩"), Sponsor("和狐狸哈斯哈斯", "¥5.00", "哔哩哔哩"), Sponsor("僮额玄恋酱", "¥5.00", "哔哩哔哩"),
    Sponsor("**炜", "¥500.00", "支付宝"), Sponsor("**炜", "¥200.00", "支付宝"), Sponsor("**修", "¥10.00", "支付宝"),
    Sponsor("霜寒千星", "¥20.00", "爱发电"), Sponsor("qiuwuji", "¥20.00", "爱发电"), Sponsor("利亚德", "¥20.00", "爱发电", "组建的群聊若开的是导演模式，会经常出现一些莫名其妙的\"不存在干员\"..."), Sponsor("?梓秋?", "¥20.00", "爱发电", "您好我想请问一下两个问题..."), Sponsor("去逃避", "¥15.00", "爱发电"), Sponsor("小象", "¥15.00", "爱发电"), Sponsor("阿米娅可爱捏", "¥10.00", "爱发电", "(? ? ?) ?"),
    Sponsor("招笑奉孝", "¥5.00", "爱发电", "111"), Sponsor("招笑奉孝", "¥5.00", "爱发电", "111"), Sponsor("凌", "¥5.00", "爱发电", "老大，加油"), Sponsor("镜安", "¥5.00", "爱发电", "加油口牙牢大"), Sponsor("爱发电用户_31235", "¥5.00", "爱发电", "加油"), Sponsor("利亚德", "¥20.00", "爱发电"),
    Sponsor("*哦", "¥200.00", "微信", "太感谢了大夫😭😭妙手回春啊 终于能正常聊天了"), Sponsor("*哦", "¥100.00", "微信"), Sponsor("*鸟", "¥100.00", "微信"), Sponsor("*晴", "¥100.00", "微信"), Sponsor("*😋", "¥50.00", "微信"), Sponsor("*杆", "¥50.00", "微信", "麻烦老大教我这个笨蛋了"), Sponsor("7*_", "¥50.00", "微信"), Sponsor("*王", "¥20.00", "微信", "❤️❤️❤️❤️❤️❤️❤️❤️❤️❤️❤️❤️❤️❤️"), Sponsor("E*o", "¥20.00", "微信"), Sponsor("第*)", "¥20.00", "微信"), Sponsor("*博", "¥20.00", "微信", "请老大吃夜宵了😋"), Sponsor("H*e", "¥32.50", "微信", "长毛的栗子饼"), Sponsor("*阡", "¥32.50", "微信", "老大，我喜欢你喵，已严肃完成325大学习"), Sponsor("k*t", "¥30.00", "微信"), Sponsor("*阻", "¥30.00", "微信", "给老猫买一张复活卡"), Sponsor("*在", "¥30.00", "微信", "刘联效非署特此资助30块，感谢大佬"), Sponsor("*城", "¥30.00", "微信", "老大要加油啊o(*≧▽≦)ツ"), Sponsor("*愿", "¥30.00", "微信", "给凯尔希"), Sponsor("*醒", "¥10.00", "微信", "老大老大，谬因干员可以出嘛，还有龙门币是不是获取变慢了些..."), Sponsor("*茶", "¥10.00", "微信", "老大，百度网盘那边1.10版本下不了😭"), Sponsor("*哦", "¥15.00", "微信"), Sponsor("*f", "¥30.00", "微信", "牢大，来个群聊的知识库呗，用户自己写😋，然后私聊的知识库为统一的"), Sponsor("*拉", "¥0.35", "微信", "现在只有这么多钱了"), Sponsor("*。", "¥0.01", "微信"), Sponsor("*。", "¥6.00", "微信", "7月26号的第一杯奶茶（或许？）希望牢大越做越好..."), Sponsor("*默", "¥3.25", "微信"), Sponsor("*阻", "¥3.25", "微信"), Sponsor("*赤", "¥5.00", "微信", "巧乐兹"), Sponsor("*ฅ", "¥10.00", "微信", "你已经成为群主了，和群员打招呼吧！"), Sponsor("*张", "¥4.00", "微信", "manman"), Sponsor("N*e", "¥20.00", "微信", "真的很厉害"), Sponsor("*斯", "¥10.00", "微信", "老大早点休息一下吧。"), Sponsor("*生", "¥10.00", "微信", "最新版本的道具 就催眠道具有bug 无法催眠捏"), Sponsor("*发", "¥10.00", "微信", "我想玩新版本🐱☎️"), Sponsor("*海", "¥15.00", "微信", "老大，我只有半张月卡的钱了，群虽然不是每日在线(住校高中生是这样的)..."), Sponsor("*仟", "¥10.00", "微信", "牢大加油喵"), Sponsor("*镜", "¥20.00", "微信"), Sponsor("*略", "¥10.00", "微信"), Sponsor("*子", "¥10.00", "微信"), Sponsor("*子", "¥6.00", "微信"), Sponsor("*了", "¥6.00", "微信"), Sponsor("*鸡", "¥10.00", "微信"), Sponsor("*す", "¥15.00", "微信"), Sponsor("*帝", "¥5.20", "微信"), Sponsor("*赤", "¥5.00", "微信"), Sponsor("*字", "¥10.00", "微信"), Sponsor("*人", "¥10.00", "微信", "谢谢大佬"), Sponsor("l*l", "¥10.00", "微信"), Sponsor("*桔", "¥10.00", "微信"), Sponsor("c*d", "¥5.00", "微信"), Sponsor("*豹", "¥11.45", "微信"), Sponsor("*梢", "¥10.00", "微信"), Sponsor("A*a", "¥10.00", "微信"), Sponsor("-*-", "¥6.00", "微信"), Sponsor("F*n", "¥6.00", "微信"),
    Sponsor("缪尔赛思同学", "¥5.00", "QQ"), Sponsor("人民万岁", "¥20.00", "QQ"), Sponsor("和狐狸哈斯哈斯", "¥8.00", "QQ"), Sponsor("健康哥哥", "¥6.00", "QQ"), Sponsor("健康哥哥", "¥8.00", "QQ"), Sponsor("切利尼娜·德克萨斯", "¥5.00", "QQ"),
    Sponsor("冗木", "¥5.00", "爱发电"), Sponsor("王向红", "¥5.00", "爱发电"), Sponsor("haoye1520", "¥5.00", "爱发电"), Sponsor("sam", "¥5.00", "爱发电"), Sponsor("mmk绝对不喝酒", "¥5.00", "爱发电"), Sponsor("7773", "¥5.00", "爱发电"), Sponsor("(？)！！", "¥5.00", "爱发电"), Sponsor("五宵", "¥5.00", "爱发电"), Sponsor("实名上网", "¥5.00", "爱发电"), Sponsor("小满", "¥5.00", "爱发电"), Sponsor("Joshua", "¥5.00", "爱发电"), Sponsor("Ling", "¥5.00", "爱发电"), Sponsor("爱发电用户_920f0", "¥5.00", "爱发电"), Sponsor("叙拉古传奇出租车司机lapld", "¥5.00", "爱发电"), Sponsor("爱发电用户_ca012", "¥5.00", "爱发电"), Sponsor("嘿嘿嘿ya", "¥5.00", "爱发电"), Sponsor("VoltSummer", "¥5.00", "爱发电"), Sponsor("纪喑", "¥5.00", "爱发电"), Sponsor("姜子", "¥5.00", "爱发电"), Sponsor("a页a", "¥5.00", "爱发电"), Sponsor("爱发电用户_fd277", "¥5.00", "爱发电"), Sponsor("lll", "¥5.00", "爱发电"), Sponsor("AAAmiya", "¥5.00", "爱发电"), Sponsor("爱发电用户_3tvf", "¥5.00", "爱发电"), Sponsor("爱发电用户_0882d", "¥5.00", "爱发电"), Sponsor("Alula", "¥5.00", "爱发电"), Sponsor("狐白梦", "¥5.00", "爱发电"), Sponsor("爱发电用户_HYwg", "¥5.00", "爱发电"), Sponsor("人", "¥5.00", "爱发电"), Sponsor("海棠煎雪", "¥5.00", "爱发电"), Sponsor("分割线发", "¥5.00", "爱发电"), Sponsor("l", "¥5.00", "爱发电"), Sponsor("shu", "¥5.00", "爱发电"), Sponsor("爱桃", "¥5.00", "爱发电"), Sponsor("爱发电用户_rNnt", "¥5.00", "爱发电"), Sponsor("韭菜盒子盖浇饭", "¥5.00", "爱发电"), Sponsor("栉风沐鱼", "¥5.00", "爱发电"), Sponsor("郡守chief", "¥5.00", "爱发电"),
)
