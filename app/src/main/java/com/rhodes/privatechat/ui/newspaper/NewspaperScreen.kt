package com.rhodes.privatechat.ui.newspaper

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.newspaper.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.ui.common.TerminalPanelShape
import com.rhodes.privatechat.ui.common.WechatTopBar
import com.rhodes.privatechat.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NewspaperScreen(settings: SettingsRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val service = remember { NewspaperService.get(context, settings) }
    val state by service.state.collectAsState()
    val balance by settings.lmbFlow.collectAsState(initial = settings.lmb)
    var today by remember { mutableStateOf(newspaperToday()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingEdition by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var topicName by rememberSaveable { mutableStateOf("GENERAL") }
    var topicMenu by remember { mutableStateOf(false) }
    var showPurchase by remember { mutableStateOf(false) }
    var previousIds by remember { mutableStateOf<Set<String>?>(null) }
    var wasRunning by remember { mutableStateOf(false) }
    val topic = NewspaperTopic.from(topicName)
    var expandedWindow by rememberSaveable { mutableStateOf(false) }
    var fontSize by rememberSaveable { mutableIntStateOf(16) }
    var exportText by remember { mutableStateOf("") }
    var exportMessage by remember { mutableStateOf("") }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportText) } ?: error("无法保存") }.isSuccess
            }
            exportMessage = if (ok) "已导出报纸正文与现实来源" else "导出失败，请重试"
        }
    }
    LaunchedEffect(Unit) {
        service.reload()
        while (true) {
            delay(30_000)
            val currentDay = newspaperToday()
            if (currentDay != today) { today = currentDay; service.reload() }
        } // Local clock and storage only, no network.
    }
    LaunchedEffect(state.editions, state.running) {
        if (awaitingEdition != null && state.editions.any { it.id == awaitingEdition }) {
            selected = awaitingEdition
            awaitingEdition = null
        }
    }
    LaunchedEffect(state.running, state.startedAt) {
        while (state.running) {
            elapsedSeconds = ((System.currentTimeMillis() - state.startedAt) / 1000L).coerceAtLeast(0)
            delay(1000)
        }
    }
    val edition = state.editions.firstOrNull { it.id == selected }
    val listState = remember(edition?.id) { LazyListState() }
    LaunchedEffect(state.editions) {
        val ids = state.editions.map { it.id }.toSet()
        val before = previousIds
        if (before != null && wasRunning) state.editions.firstOrNull { it.id !in before }?.let { selected = it.id; showPurchase = false }
        previousIds = ids
    }
    LaunchedEffect(state.running) { if (state.running) wasRunning = true }
    val current = state.editions.firstOrNull { it.date == today }
    BackHandler(enabled = edition != null) { selected = null }
    val shape = if (isRhodesTerminal) TerminalPanelShape else RoundedCornerShape(4.dp)
    Column(Modifier.fillMaxSize().background(BG).navigationBarsPadding()) {
        WechatTopBar("报刊亭") {
            IconButton(onClick = { if (edition != null) selected = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, if (edition != null) "返回报刊亭" else "返回", tint = TextPrimary)
            }
            IconButton(onClick = { showSettings = true }, enabled = !state.running) {
                Icon(Icons.Default.Settings, "报纸服务设置", tint = Primary)
            }
        }
        if (state.running) {
            Column(Modifier.fillMaxWidth().background(PrimaryContainer).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("正在购买报纸 · 已等待 ${elapsedSeconds} 秒", color = Primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("${state.stage}，请稍候。可先去其他页面，完成后会提醒你。", color = TextSecondary,
                    fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 6.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "masthead-${edition?.date ?: "stand"}") {
                    Column(Modifier.fillMaxWidth().background(Surface, shape).border(1.dp, StrokeStrong, shape).padding(20.dp)) {
                        Text("T E R R A   /   O B S E R V E R", color = Primary, fontSize = 10.sp,
                            letterSpacing = 1.2.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                        Text("泰拉见闻报", color = TextPrimary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Black,
                            fontSize = 34.sp, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = TextAlign.Center)
                        HorizontalDivider(thickness = 3.dp, color = TextPrimary)
                        Spacer(Modifier.height(3.dp))
                        HorizontalDivider(color = TextPrimary)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatPaperDate(edition?.date ?: today), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text("${edition?.let { NewspaperTopic.from(it.topic).label } ?: "今日"} 刊", color = TextSecondary, fontSize = 11.sp)
                        }
                        Text("社会见闻   /   技术与工造   /   城际短讯", color = TextSecondary,
                            fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text(NEWSPAPER_DISCLAIMER, color = TextTertiary, fontSize = 11.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (edition == null) {
                    item(key = "purchase") {
                        Column(Modifier.fillMaxWidth().background(Surface, shape).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (current == null) "今日刊，待你开启" else "今日刊已送达",
                                fontFamily = FontFamily.Serif, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("从现实世界的今日资讯出发，读一份发生在泰拉的新闻。", color = TextSecondary, fontSize = 14.sp, lineHeight = 23.sp)
                            Text("读见泰拉的城市与生活。已购报纸可反复阅读，也可再买一份。",
                                color = TextSecondary, fontSize = 13.sp, lineHeight = 22.sp)
                            run {
                                Box {
                                    OutlinedButton(onClick = { topicMenu = true }, enabled = !state.running, modifier = Modifier.fillMaxWidth()) { Text("想看什么：${topic.label} ▾", color = Primary) }
                                    DropdownMenu(expanded = topicMenu, onDismissRequest = { topicMenu = false }) {
                                        NewspaperTopic.entries.forEach { choice -> DropdownMenuItem(text = { Text(choice.label) }, onClick = { topicName = choice.name; topicMenu = false }) }
                                    }
                                }
                                Row(Modifier.toggleable(value = expandedWindow, enabled = !state.running, role = Role.Checkbox,
                                    onValueChange = { expandedWindow = it }), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = expandedWindow, onCheckedChange = null, enabled = !state.running,
                                        colors = CheckboxDefaults.colors(checkedColor = Primary))
                                    Text("允许采用昨日资讯（本期会明确标注）", color = TextSecondary, fontSize = 12.sp)
                                }
                            }
                            Button(
                                onClick = {
                                    if (current != null) selected = current.id
                                    else if (!state.ready) showSettings = true
                                    else { awaitingEdition = today; service.purchase(expandedWindow, topic = topic) }
                                },
                                enabled = !state.loading && !state.running && (current != null || !state.ready || balance >= NewspaperService.PRICE),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary),
                                shape = shape,
                            ) {
                                Text(when {
                                    state.running -> "正在购买中…"
                                    current != null -> "展开今日报纸"
                                    !state.ready -> "配置 DeepSeek 后购买"
                                    balance < NewspaperService.PRICE -> "龙门币不足"
                                    else -> "购买今日报纸 · ${NewspaperService.PRICE} 龙门币"
                                })
                            }
                            if (current != null) Button(onClick = {
                                if (!state.ready) showSettings = true
                                else service.purchase(expandedWindow, topic = topic, another = true)
                            }, enabled = !state.running && !state.loading && balance >= NewspaperService.PRICE,
                                modifier = Modifier.fillMaxWidth(), shape = shape,
                                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)) {
                                Text("再来一份 · ${NewspaperService.PRICE} 龙门币")
                            }
                            Text("余额 $balance 龙门币 · 仅点击购买才联网\n另有 DeepSeek API 费用；生成失败不扣龙门币，已成功的 API 调用仍可能计费。",
                                fontSize = 11.sp, lineHeight = 19.sp, color = TextTertiary)
                        }
                    }
                    if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary) }
                    if (state.error.isNotBlank()) item {
                        Column(Modifier.fillMaxWidth().border(1.dp, ErrorRed.copy(alpha = .35f), shape).padding(16.dp)) {
                            Text(state.error, color = ErrorRed, fontSize = 13.sp, lineHeight = 22.sp)
                            if (state.retryDate != null && state.pendingDate != state.retryDate) {
                                Text("重试可能产生 API 费用；不会自动重试。", color = TextTertiary, fontSize = 11.sp)
                                TextButton(onClick = {
                                    awaitingEdition = state.retryDate
                                    service.purchase(expanded = state.retryExpanded, recoverDate = state.retryDate, topic = NewspaperTopic.from(state.retryTopic))
                                }, enabled = !state.running && balance >= NewspaperService.PRICE && state.ready) {
                                    Text("重试 ${state.retryDate?.take(10)} 刊 · ${NewspaperService.PRICE} 龙门币", color = Primary)
                                }
                            }
                        }
                    }
                    state.pendingDate?.let { date ->
                        item {
                            Column(Modifier.fillMaxWidth().background(Surface, shape).padding(16.dp)) {
                                Text("${date.take(10)} 刊已编排，尚未完成购买", color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text("可直接完成购买，无需再次搜索或生成。", fontSize = 12.sp, color = TextSecondary)
                                TextButton(onClick = { awaitingEdition = date; service.purchase(recoverDate = date) },
                                    enabled = !state.running && balance >= NewspaperService.PRICE) {
                                    Text("完成购买 · ${NewspaperService.PRICE} 龙门币", color = Primary)
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("往 期 馆 藏", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            HorizontalDivider(Modifier.weight(1f).padding(start = 14.dp), color = StrokeStrong)
                        }
                    }
                    if (!state.loading && state.editions.isEmpty()) item {
                        Text("尚无已购报纸。报刊亭不会在后台自动制报。", color = TextTertiary, fontSize = 13.sp)
                    }
                    itemsIndexed(state.editions, key = { _, it -> it.id }) { _, paper ->
                        OutlinedButton(onClick = { selected = paper.id }, modifier = Modifier.fillMaxWidth(), shape = shape) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text("${paper.date} · ${NewspaperTopic.from(paper.topic).label} · 已购买", color = Primary, fontSize = 12.sp)
                                Text(paper.articles.firstOrNull()?.title ?: "泰拉见闻报", color = TextPrimary,
                                    fontFamily = FontFamily.Serif, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                            }
                        }
                    }
                    item { Text("报纸保留最近30天，较早刊物会自动清理。想长久收藏，可在阅读页导出。\n测试阶段暂不支持随应用备份迁移报纸，请妥善保存导出文件。",
                        color = TextTertiary, fontSize = 11.sp, lineHeight = 19.sp) }
                } else {
                    item(key = "reading-tools") {
                        Button(onClick = { selected = null; showPurchase = true }, enabled = !state.running,
                            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)) {
                            Text("再来一份 · ${NewspaperService.PRICE} 龙门币")
                        }
                        FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                            TextButton(onClick = { selected = null }) { Text("返回馆藏", color = Primary) }
                            TextButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(14) }) { Text("字－", color = Primary) }
                            TextButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(22) }) { Text("字＋", color = Primary) }
                            TextButton(onClick = {
                                exportText = exportNewspaper(edition)
                                export.launch("泰拉见闻报-${edition.id}.txt")
                            }) { Text("导出", color = Primary) }
                        }
                        if (exportMessage.isNotBlank()) Text(exportMessage, color = TextSecondary, fontSize = 12.sp)
                        Text(if (edition.expandedWindow) "资讯取材范围：本期日期及前一日（北京时间）" else "资讯取材范围：本期日期（北京时间）",
                            color = TextTertiary, fontSize = 11.sp)
                    }
                    itemsIndexed(edition.articles, key = { index, _ -> "${edition.id}-$index" }) { index, article ->
                        NewspaperArticleView(article, index, edition, fontSize)
                    }
                    item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            HorizontalDivider(thickness = 2.dp, color = TextPrimary)
                            Text("本 期 完", color = TextPrimary, fontFamily = FontFamily.Serif, fontSize = 19.sp,
                                modifier = Modifier.fillMaxWidth().padding(18.dp), textAlign = TextAlign.Center)
                            Button(onClick = { selected = null; showPurchase = true }, enabled = !state.running, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = OnPrimary)) { Text("再来一份 · ${NewspaperService.PRICE} 龙门币") }
                            Text("$NEWSPAPER_DISCLAIMER\n再次阅读无需重新购买。", color = TextTertiary, fontSize = 11.sp, lineHeight = 19.sp)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(showPurchase) { if (showPurchase) { listState.scrollToItem(1); showPurchase = false } }
    if (showSettings) NewspaperSettingsDialog(service, onDismiss = { showSettings = false })
}

@Composable
private fun NewspaperArticleView(article: NewspaperArticle, index: Int, edition: NewspaperEdition, bodySize: Int) {
    var sourcesOpen by rememberSaveable(edition.id, index) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var linkError by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().background(Surface).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(index + 1), color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text(article.section, color = Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            HorizontalDivider(Modifier.weight(1f).padding(start = 14.dp), color = StrokeStrong)
        }
        Text(article.title, color = TextPrimary, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            fontSize = if (index == 0) 28.sp else 23.sp, lineHeight = if (index == 0) 39.sp else 33.sp,
            modifier = Modifier.padding(top = 15.dp, bottom = 12.dp))
        if (article.subtitle.isNotBlank()) Text(article.subtitle, color = TextSecondary, fontSize = 14.sp,
            lineHeight = 23.sp, modifier = Modifier.padding(bottom = 16.dp))
        HorizontalDivider(color = StrokeStrong)
        article.paragraphs.forEach { paragraph ->
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text("　　$paragraph", color = TextPrimary, fontSize = bodySize.sp, lineHeight = (bodySize * 1.85f).sp,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
        TextButton(onClick = { sourcesOpen = !sourcesOpen }, contentPadding = PaddingValues(top = 12.dp)) {
            Text(if (sourcesOpen) "收起现实来源 －" else "查看现实来源 ＋", color = Primary, fontSize = 12.sp)
        }
        if (sourcesOpen) {
            Text("以下为现实新闻来源；上方报道已作同人改编。", color = TextTertiary, fontSize = 11.sp, lineHeight = 18.sp)
            edition.sources.filter { it.id in article.sourceIds }.forEach { source ->
                TextButton(onClick = {
                    runCatching { if (safeNewsUrl(source.url)) uriHandler.openUri(source.url) }
                        .onFailure { linkError = "无法打开链接，请导出报纸后查看来源地址" }
                }, contentPadding = PaddingValues(vertical = 5.dp)) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(source.title, color = Primary, fontSize = 12.sp, lineHeight = 20.sp)
                        Text("${source.site} · ${source.publishedAt.take(10)}（来源标注）",
                            color = TextTertiary, fontSize = 10.sp)
                    }
                }
            }
            if (linkError.isNotBlank()) Text(linkError, color = ErrorRed, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NewspaperSettingsDialog(service: NewspaperService, onDismiss: () -> Unit) {
    var dedicated by remember { mutableStateOf(service.usesDedicatedKey()) }
    // Deliberately not saveable: API keys are not stored in activity saved-state bundles.
    var apiKey by remember { mutableStateOf(service.dedicatedKey()) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface,
        title = { Text("报纸服务设置", color = TextPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (service.hasDefaultKey()) "已自动使用模型设置中的 DeepSeek，无需重复配置。"
                    else "报纸功能目前处于测试阶段，暂仅支持 DeepSeek 官方模型，后续可逐步扩展其他模型。这里的配置独立于聊天。", color = TextSecondary, fontSize = 13.sp)
                Row(Modifier.toggleable(value = dedicated, role = Role.Checkbox, onValueChange = { dedicated = it }),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = dedicated, onCheckedChange = null,
                        colors = CheckboxDefaults.colors(checkedColor = Primary))
                    Text("使用报纸专用密钥", color = TextPrimary, fontSize = 14.sp)
                }
                if (dedicated) OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, singleLine = true,
                    label = { Text("DeepSeek 官方 API Key") }, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Text(if (dedicated) "密钥仅发往 api.deepseek.com，按现有安全设置方式保存在本机。"
                    else "仅当聊天配置为 DeepSeek 官方服务时复用密钥；其他厂商或中转请勾选专用密钥。", color = TextSecondary, fontSize = 12.sp, lineHeight = 20.sp)
                Text("仅购买报纸时联网，保存设置不产生费用。每次购报另有 DeepSeek API 费用，失败后可手动重试。", color = TextTertiary, fontSize = 11.sp, lineHeight = 19.sp)
            }
        },
        confirmButton = { TextButton(onClick = { service.saveConfiguration(dedicated, apiKey); onDismiss() },
            enabled = !dedicated || apiKey.isNotBlank()) { Text("保存", color = Primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) } },
    )
}

private fun formatPaperDate(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))
}.getOrDefault(date)

private fun exportNewspaper(edition: NewspaperEdition): String = buildString {
    appendLine("泰拉见闻报 · ${edition.date} 刊")
    appendLine(NEWSPAPER_DISCLAIMER)
    appendLine(if (edition.expandedWindow) "取材范围：本日与前一日" else "取材范围：本日")
    edition.articles.forEach {
        appendLine("\n【${it.section}】${it.title}\n${it.subtitle}\n")
        appendLine(it.paragraphs.joinToString("\n\n"))
        appendLine("\n现实来源：")
        edition.sources.filter { source -> source.id in it.sourceIds }.forEach { source ->
            appendLine("${source.title} · ${source.site} · ${source.publishedAt}\n${source.url}")
        }
    }
}
