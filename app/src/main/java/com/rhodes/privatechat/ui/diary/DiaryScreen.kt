package com.rhodes.privatechat.ui.diary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.shared.model.Diary
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DiaryOp(val id: String, val name: String, val color: Color, val hasDiary: Boolean = false, val unread: Boolean = false)

private val colorPalette = listOf(
    Color(0xFF5B8DEF), Color(0xFF4DB6AC), Color(0xFFFF7043), Color(0xFF607D8B),
    Color(0xFF455A64), Color(0xFF5C6BC0), Color(0xFF81D4FA), Color(0xFFFFD54F),
    Color(0xFF2979FF), Color(0xFFFF5722), Color(0xFF9C27B0), Color(0xFF00BCD4),
    Color(0xFFFF4081), Color(0xFF8BC34A), Color(0xFF795548), Color(0xFFE91E63)
)

@Composable
fun DiaryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val operators by viewModel.operators.collectAsState()
    var unreadIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(operators) { unreadIds = viewModel.getUnreadDiaryOperatorIds() }
    val diaryOps: List<DiaryOp> = remember(operators, unreadIds) {
        operators.mapIndexed { i, op ->
            DiaryOp(op.id, op.name, colorPalette[i % colorPalette.size], unread = op.id in unreadIds)
        }.sortedWith(compareByDescending<DiaryOp> { it.unread }.thenBy { it.name })
    }
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    val filteredOps = if (searchText.isBlank()) diaryOps
        else diaryOps.filter { it.name.contains(searchText, ignoreCase = true) }
    var selectedName by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(diaryOps) {
        if (selectedName.isEmpty() || diaryOps.none { it.name == selectedName }) {
            selectedName = diaryOps.firstOrNull()?.name ?: ""
        }
    }
    val scope = rememberCoroutineScope()
    var showPanel by remember { mutableStateOf(true) }
    var diaryContent by rememberSaveable { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var diaryEntries by remember { mutableStateOf<List<com.rhodes.privatechat.shared.model.Diary>>(emptyList()) }
    var currentDateIdx by remember { mutableIntStateOf(0) }
    var currentDateLabel by remember { mutableStateOf("") }
    var dataLoaded by remember { mutableStateOf(false) }

    // 加载干员日记条目（独立的挂起函数，不会被级联取消）
    fun loadDiaryEntries(name: String) {
        val opId = diaryOps.find { it.name == name }?.id
        com.rhodes.privatechat.util.DebugLogger.log("Diary", "loadDiaryEntries: name=$name, opId=$opId, diaryOps.size=${diaryOps.size}")
        if (opId == null) {
            com.rhodes.privatechat.util.DebugLogger.log("Diary", "opId为空, 跳过")
            return
        }
        scope.launch {
            try {
                isGenerating = false
                currentDateIdx = 0
                dataLoaded = false
                com.rhodes.privatechat.util.DebugLogger.log("Diary", "开始加载日记: opId=$opId")
                val entries = withContext(Dispatchers.IO) {
                    viewModel.repository.getAllDiaryEntries(opId)
                }
                com.rhodes.privatechat.util.DebugLogger.log("Diary", "getAllDiaryEntries返回: count=${entries.size}, ids=${entries.map { it.id }}")
                val totalCount = withContext(Dispatchers.IO) { viewModel.repository.getDiaryCount() }
                com.rhodes.privatechat.util.DebugLogger.log("Diary", "全表diary数: total=$totalCount, matched=${entries.size}")
                diaryEntries = entries
                entries.maxOfOrNull { it.createdAt }?.let { latest ->
                    viewModel.markDiaryRead(opId, latest)
                    unreadIds = unreadIds - opId
                }
                dataLoaded = true
                // 计算昨天的日期（北京时间），优先定位到昨天的日记
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                }.format(cal.time)
                val yesterdayIdx = entries.indexOfFirst { it.date == yesterdayStr }
                if (yesterdayIdx >= 0) {
                    currentDateIdx = yesterdayIdx
                    diaryContent = entries[yesterdayIdx].content
                    currentDateLabel = entries[yesterdayIdx].date
                    com.rhodes.privatechat.util.DebugLogger.log("Diary", "定位到昨日日记: id=${entries[yesterdayIdx].id}, date=${entries[yesterdayIdx].date}")
                } else {
                    currentDateIdx = 0
                    diaryContent = null
                    com.rhodes.privatechat.util.DebugLogger.log("Diary", "无昨日日记, 设为null, 有${entries.size}条过往日记可翻看")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                com.rhodes.privatechat.util.DebugLogger.log("Diary", "加载被取消: $name")
                dataLoaded = true
            } catch (e: Exception) {
                com.rhodes.privatechat.util.DebugLogger.log("Diary/ERROR", "加载日记异常: ${e.message}")
                dataLoaded = true
            }
            com.rhodes.privatechat.util.DebugLogger.log("Diary", "加载结束: diaryContent=${diaryContent?.take(20) ?: "null"}, diaryEntries.size=${diaryEntries.size}")
        }
    }

    // 用 snapshotFlow 观察 selectedName，200ms 稳定后再加载
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { selectedName }
            .collect { name ->
                if (name.isNotBlank()) {
                    kotlinx.coroutines.delay(200)
                    if (name == selectedName) {
                        loadDiaryEntries(name)
                    }
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("干员日记", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = Divider)

        Row(modifier = Modifier.fillMaxWidth().background(Surface).clickable { showPanel = !showPanel }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Primary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (showPanel) "选择干员：$selectedName" else "显示干员列表", fontSize = 13.sp, color = Primary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Text(if (showPanel) "▲ 隐藏" else "▼ 展开", fontSize = 11.sp, color = TextTertiary)
        }
        HorizontalDivider(color = Divider)

        // 搜索框
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchText, onValueChange = { searchText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索干员...", fontSize = 14.sp, color = TextTertiary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchText.isNotBlank()) IconButton(onClick = { searchText = "" }, modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Clear, "清除", tint = TextTertiary, modifier = Modifier.size(14.dp))
                    }
                },
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Divider, unfocusedBorderColor = Divider),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { })
            )
        }
        HorizontalDivider(color = Divider)

        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = showPanel, enter = slideInHorizontally(initialOffsetX = { -it }), exit = slideOutHorizontally(targetOffsetX = { -it })) {
                Column(modifier = Modifier.fillMaxHeight().width(88.dp).background(Surface)) {
                    LazyColumn {
                        if (filteredOps.isEmpty() && searchText.isNotBlank()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                                    Text("无匹配", fontSize = 12.sp, color = TextTertiary)
                                }
                            }
                        } else {
                            items(filteredOps) { op ->
                            val opEntity = operators.find { it.id == op.id || it.name == op.name }
                            Column(modifier = Modifier.fillMaxWidth().background(if (op.name == selectedName) Primary.copy(alpha = 0.1f) else Color.Transparent).clickable { selectedName = op.name }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    OperatorAvatarImage(avatarUri = opEntity?.avatarUri ?: "", name = op.name, modifier = Modifier.size(40.dp))
                                    if (op.unread) Box(Modifier.size(10.dp).clip(CircleShape).background(ErrorRed).align(Alignment.TopEnd))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(op.name, fontSize = 10.sp, color = if (op.name == selectedName) Primary else TextSecondary)
                            }
                        }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                // 日记按日期倒序排列：左箭头查看更新，右箭头查看更早。
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val canNewer = currentDateIdx > 0 && diaryEntries.isNotEmpty()
                    IconButton(
                        onClick = {
                            com.rhodes.privatechat.util.DebugLogger.log("Diary", "翻更新: idx=$currentDateIdx, entries=${diaryEntries.size}")
                            if (currentDateIdx > 0) {
                                val prev = diaryEntries.getOrNull(currentDateIdx - 1)
                                if (prev != null) {
                                    currentDateIdx--
                                    diaryContent = prev.content; currentDateLabel = prev.date
                                }
                            }
                        },
                        enabled = canNewer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "更新一篇", tint = if (canNewer) Primary else TextTertiary, modifier = Modifier.size(18.dp))
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val selOp = diaryOps.find { it.name == selectedName }
                            val selEntity = selOp?.let { s -> operators.find { it.id == s.id || it.name == s.name } }
                            OperatorAvatarImage(avatarUri = selEntity?.avatarUri ?: "", name = selectedName, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(selectedName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                        }
                        Text(if (diaryEntries.isNotEmpty()) "$currentDateLabel · 第 ${currentDateIdx + 1}/${diaryEntries.size} 篇" else "暂无日记", fontSize = 11.sp, color = TextTertiary)
                    }

                    val canOlder = currentDateIdx < diaryEntries.lastIndex && diaryEntries.isNotEmpty()
                    IconButton(
                        onClick = {
                            com.rhodes.privatechat.util.DebugLogger.log("Diary", "翻更早: idx=$currentDateIdx, entries=${diaryEntries.size}")
                            if (currentDateIdx < diaryEntries.lastIndex) {
                                val next = diaryEntries.getOrNull(currentDateIdx + 1)
                                if (next != null) {
                                    currentDateIdx++
                                    diaryContent = next.content; currentDateLabel = next.date
                                }
                            }
                        },
                        enabled = canOlder,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "更早一篇", tint = if (canOlder) Primary else TextTertiary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (diaryContent != null) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                        diaryContent?.let { content ->
                            Text(content, fontSize = 14.sp, color = TextPrimary, lineHeight = 24.sp)
                        }
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = {
                                val opId = diaryOps.find { it.name == selectedName }?.id
                                if (opId == null) { com.rhodes.privatechat.util.DebugLogger.log("Diary", "重新生成失败: 找不到opId"); return@OutlinedButton }
                                com.rhodes.privatechat.util.DebugLogger.log("Diary", "重新生成: opId=$opId, selectedName=$selectedName")
                                isGenerating = true
                                viewModel.generateDiary(opId) { text ->
                                    com.rhodes.privatechat.util.DebugLogger.log("Diary", "重新生成回调触发: text=${text.take(30)}, isBlank=${text.isBlank()}")
                                    scope.launch {
                                        try {
                                            if (text.isNotBlank()) {
                                                diaryContent = text
                                                com.rhodes.privatechat.util.DebugLogger.log("Diary", "重生成后 DB reload 开始")
                                                val newEntries = withContext(Dispatchers.IO) { viewModel.repository.getAllDiaryEntries(opId) }
                                                com.rhodes.privatechat.util.DebugLogger.log("Diary", "重生成后 DB reload: count=${newEntries.size}, ids=${newEntries.map { it.id }}")
                                                diaryEntries = newEntries; currentDateIdx = 0
                                                if (newEntries.isNotEmpty()) {
                                                    currentDateLabel = newEntries[0].date
                                                    if (newEntries[0].content != text) {
                                                        com.rhodes.privatechat.util.DebugLogger.log("Diary/WARN", "!! diaryContent与DB内容不一致: text=${text.take(20)}, DB=${newEntries[0].content.take(20)}")
                                                    }
                                                }
                                            } else {
                                                android.widget.Toast.makeText(context, "AI生成失败，请检查网络和API Key后重试", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            // scope 取消，忽略
                                        } catch (e: Exception) {
                                            com.rhodes.privatechat.util.DebugLogger.log("Diary/ERROR", "重生成刷新异常: ${e.message}")
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("重新生成这篇日记")
                        }
                    }
                } else if (!isGenerating) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没有找到${selectedName}的日记", fontSize = 14.sp, color = TextTertiary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("可以让 AI 根据近期聊天、动态、群聊和关系事件补写一篇。", fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                val opId = diaryOps.find { it.name == selectedName }?.id
                                if (opId == null) { com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看失败: 找不到opId"); return@Button }
                                com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看日记: opId=$opId, selectedName=$selectedName")
                                isGenerating = true
                                viewModel.generateDiary(opId) { text ->
                                    com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看回调触发: text=${text.take(30)}, isBlank=${text.isBlank()}")
                                    if (text.isNotBlank()) {
                                        diaryContent = text
                                        com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看后立即显示 diaryContent")
                                    }
                                    scope.launch {
                                        try {
                                            com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看后 DB reload 开始")
                                            val newEntries = withContext(Dispatchers.IO) { viewModel.repository.getAllDiaryEntries(opId) }
                                            com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看后 DB reload: count=${newEntries.size}, ids=${newEntries.map { it.id }}")
                                            diaryEntries = newEntries; currentDateIdx = 0
                                            if (newEntries.isNotEmpty()) {
                                                currentDateLabel = newEntries[0].date
                                                if (newEntries[0].content != text) {
                                                    com.rhodes.privatechat.util.DebugLogger.log("Diary/WARN", "!! diaryContent与DB内容不一致: text=${text.take(20)}, DB=${newEntries[0].content.take(20)}")
                                                }
                                            }
                                            if (text.isBlank()) {
                                                android.widget.Toast.makeText(context, "AI生成失败，请检查网络和API Key后重试", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            // scope 取消，忽略
                                        } catch (e: Exception) {
                                            com.rhodes.privatechat.util.DebugLogger.log("Diary/ERROR", "偷看刷新异常: ${e.message}")
                                        } finally {
                                            isGenerating = false
                                        }
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("查看日记", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("正在生成${selectedName}的日记...", fontSize = 16.sp, color = Primary)
                            Spacer(Modifier.height(6.dp))
                            Text("会参考近期聊天、动态、群聊和关系事件", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
    }
}
