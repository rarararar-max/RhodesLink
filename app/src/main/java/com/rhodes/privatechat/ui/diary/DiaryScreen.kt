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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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

data class DiaryOp(val id: String, val name: String, val color: Color, val hasDiary: Boolean = false)

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
    val diaryMap = remember { mutableStateMapOf<String, Boolean>() }
    val yesterdayKey = remember {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.add(Calendar.DAY_OF_MONTH, -1)
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(cal.time)
    }
    // 异步查询各干员是否有日记
    LaunchedEffect(operators, yesterdayKey) {
        for (op in operators) {
            val d = withContext(Dispatchers.IO) { viewModel.repository.getDiary(op.id, yesterdayKey) }
            diaryMap[op.id] = d != null
        }
    }
    val diaryOps: List<DiaryOp> = remember(operators, diaryMap) {
        operators.mapIndexed { i, op ->
            DiaryOp(op.id, op.name, colorPalette[i % colorPalette.size], diaryMap[op.id] == true)
        }
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
    var showPanel by remember { mutableStateOf(true) }
    var diaryContent by rememberSaveable { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // 每次进入或切换干员时从 DB 加载已有日记
    LaunchedEffect(selectedName) {
        isGenerating = false
        diaryContent = null
        val opId = diaryOps.find { it.name == selectedName }?.id ?: return@LaunchedEffect
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.format(cal.time)
        val existing = withContext(Dispatchers.IO) {
            viewModel.repository.getDiary(opId, yesterday)
        }
        if (existing != null) diaryContent = existing.content
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
                                OperatorAvatarImage(avatarUri = opEntity?.avatarUri ?: "", name = op.name, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(op.name, fontSize = 10.sp, color = if (op.name == selectedName) Primary else TextSecondary)
                            }
                        }
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                if (diaryContent != null) {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val selOp = diaryOps.find { it.name == selectedName }
                                val selEntity = selOp?.let { s -> operators.find { it.id == s.id || it.name == s.name } }
                                OperatorAvatarImage(avatarUri = selEntity?.avatarUri ?: "", name = selectedName, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(selectedName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(diaryContent!!, fontSize = 14.sp, color = TextPrimary, lineHeight = 24.sp)
                    }
                } else if (!isGenerating) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("点击下方按钮偷看${selectedName}的日记", fontSize = 14.sp, color = TextTertiary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                val opId = diaryOps.find { it.name == selectedName }?.id ?: return@Button
                                isGenerating = true
                                viewModel.generateDiary(opId) { text ->
                                    if (text.isNotBlank()) {
                                        diaryContent = text
                                        diaryMap[opId] = true
                                    } else {
                                        android.widget.Toast.makeText(context, "AI生成失败，请检查网络和API Key后重试", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    isGenerating = false
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("偷看日记", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("偷看日记中...", fontSize = 16.sp, color = Primary)
                    }
                }
            }
        }
    }
    }
}
