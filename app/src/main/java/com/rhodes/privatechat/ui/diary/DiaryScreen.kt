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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class DiaryOp(
    val id: String,
    val name: String,
    val color: Color,
    val latestDiaryCreatedAt: Long? = null,
    val unread: Boolean = false
) {
    val hasDiary: Boolean get() = latestDiaryCreatedAt != null
}

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
    val latestDiaryCreatedAtFlow = remember(viewModel.repository) {
        viewModel.repository.getLatestDiaryCreatedAtByOperator()
    }
    val latestDiaryCreatedAt by latestDiaryCreatedAtFlow
        .collectAsState(initial = emptyMap())
    var unreadIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(operators, latestDiaryCreatedAt) {
        unreadIds = viewModel.getUnreadDiaryOperatorIds(latestDiaryCreatedAt)
    }
    val diaryOps: List<DiaryOp> = remember(operators, unreadIds, latestDiaryCreatedAt) {
        operators.mapIndexed { i, op ->
            DiaryOp(op.id, op.name, colorPalette[i % colorPalette.size], latestDiaryCreatedAt[op.id], op.id in unreadIds)
        }.sortedWith(
            compareByDescending<DiaryOp> { it.unread }
                .thenByDescending { it.hasDiary }
                .thenByDescending { it.latestDiaryCreatedAt ?: Long.MIN_VALUE }
                .thenBy { it.name }
        )
    }
    val context = LocalContext.current
    var searchText by remember { mutableStateOf("") }
    val filteredOps = if (searchText.isBlank()) diaryOps
        else diaryOps.filter { it.name.contains(searchText, ignoreCase = true) }
    var selectedOperatorId by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(diaryOps) {
        if (selectedOperatorId.isEmpty() || diaryOps.none { it.id == selectedOperatorId }) {
            selectedOperatorId = diaryOps.firstOrNull()?.id ?: ""
        }
    }
    val selectedName = diaryOps.find { it.id == selectedOperatorId }?.name.orEmpty()
    var showPanel by remember { mutableStateOf(true) }
    var diaryContent by rememberSaveable { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generationToken by remember { mutableLongStateOf(0L) }
    var diaryEntries by remember { mutableStateOf<List<com.rhodes.privatechat.shared.model.Diary>>(emptyList()) }
    var currentDateIdx by remember { mutableIntStateOf(0) }
    var currentDiaryId by remember { mutableStateOf<Long?>(null) }
    var pendingGeneratedContent by remember { mutableStateOf<String?>(null) }
    var currentDateLabel by remember { mutableStateOf("") }
    var dataLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(selectedOperatorId) {
        if (selectedOperatorId.isBlank()) return@LaunchedEffect
        isGenerating = false
        generationToken++
        currentDateIdx = 0
        currentDiaryId = null
        pendingGeneratedContent = null
        currentDateLabel = ""
        diaryContent = null
        diaryEntries = emptyList()
        dataLoaded = false
        try {
            val opId = selectedOperatorId
            viewModel.repository.getDiariesByOperator(opId).collect { entries ->
                diaryEntries = entries
                entries.maxOfOrNull { it.createdAt }?.let { latest ->
                    viewModel.markDiaryRead(opId, latest)
                    unreadIds = unreadIds - opId
                }
                val generatedIndex = pendingGeneratedContent?.let { content ->
                    entries.indexOfFirst { it.content == content }
                } ?: -1
                val currentIndex = currentDiaryId?.let { id -> entries.indexOfFirst { it.id == id } } ?: -1
                val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
                cal.add(Calendar.DAY_OF_MONTH, -1)
                val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
                }.format(cal.time)
                val index = generatedIndex.takeIf { it >= 0 }
                    ?: currentIndex.takeIf { it >= 0 }
                    ?: entries.indexOfFirst { it.date == yesterday }.takeIf { it >= 0 }
                    ?: 0
                entries.getOrNull(index)?.let { entry ->
                    currentDateIdx = index
                    currentDiaryId = entry.id
                    currentDateLabel = entry.date
                    diaryContent = entry.content
                    if (generatedIndex >= 0) pendingGeneratedContent = null
                } ?: run {
                    currentDateIdx = 0
                    currentDiaryId = null
                    currentDateLabel = ""
                    diaryContent = null
                }
                dataLoaded = true
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.rhodes.privatechat.util.DebugLogger.log("Diary/ERROR", "加载日记异常: ${e.message}")
            dataLoaded = true
        }
    }

    LaunchedEffect(pendingGeneratedContent, diaryEntries) {
        val content = pendingGeneratedContent ?: return@LaunchedEffect
        val index = diaryEntries.indexOfFirst { it.content == content }
        if (index >= 0) {
            val entry = diaryEntries[index]
            currentDateIdx = index
            currentDiaryId = entry.id
            currentDateLabel = entry.date
            diaryContent = entry.content
            pendingGeneratedContent = null
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
                            Column(modifier = Modifier.fillMaxWidth().background(if (op.id == selectedOperatorId) Primary.copy(alpha = 0.1f) else Color.Transparent).clickable { selectedOperatorId = op.id }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    OperatorAvatarImage(avatarUri = opEntity?.avatarUri ?: "", name = op.name, modifier = Modifier.size(40.dp))
                                    if (op.unread) Box(Modifier.size(10.dp).clip(CircleShape).background(ErrorRed).align(Alignment.TopEnd))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(op.name, fontSize = 10.sp, color = if (op.id == selectedOperatorId) Primary else TextSecondary)
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
                                    currentDiaryId = prev.id
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
                            val selEntity = operators.find { it.id == selectedOperatorId }
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
                                    currentDiaryId = next.id
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
                                val opId = selectedOperatorId
                                if (opId.isBlank()) { com.rhodes.privatechat.util.DebugLogger.log("Diary", "重新生成失败: 找不到opId"); return@OutlinedButton }
                                com.rhodes.privatechat.util.DebugLogger.log("Diary", "重新生成: opId=$opId, selectedName=$selectedName")
                                val requestToken = ++generationToken
                                isGenerating = true
                                viewModel.generateDiary(opId) { text ->
                                    com.rhodes.privatechat.util.DebugLogger.log("Diary", "重新生成回调触发: text=${text.take(30)}, isBlank=${text.isBlank()}")
                                    if (requestToken != generationToken || selectedOperatorId != opId) return@generateDiary
                                    if (text.isNotBlank() && selectedOperatorId == opId) {
                                        pendingGeneratedContent = text
                                    } else if (text.isBlank()) {
                                        android.widget.Toast.makeText(context, "AI生成失败，请检查网络和API Key后重试", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    isGenerating = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isGenerating
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("再生成一篇")
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
                                val opId = selectedOperatorId
                                if (opId.isBlank()) { com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看失败: 找不到opId"); return@Button }
                                com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看日记: opId=$opId, selectedName=$selectedName")
                                val requestToken = ++generationToken
                                isGenerating = true
                                viewModel.generateDiary(opId) { text ->
                                    com.rhodes.privatechat.util.DebugLogger.log("Diary", "偷看回调触发: text=${text.take(30)}, isBlank=${text.isBlank()}")
                                    if (requestToken != generationToken || selectedOperatorId != opId) return@generateDiary
                                    if (text.isNotBlank() && selectedOperatorId == opId) {
                                        pendingGeneratedContent = text
                                    } else if (text.isBlank()) {
                                        android.widget.Toast.makeText(context, "AI生成失败，请检查网络和API Key后重试", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    isGenerating = false
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
