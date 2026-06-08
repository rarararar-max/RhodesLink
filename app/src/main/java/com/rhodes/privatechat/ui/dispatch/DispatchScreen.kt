package com.rhodes.privatechat.ui.dispatch

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import androidx.compose.ui.layout.ContentScale
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.data.db.entity.DispatchRecordEntity
import com.rhodes.privatechat.ui.theme.AccentOrange
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Card
import com.rhodes.privatechat.ui.theme.Divider
import com.rhodes.privatechat.ui.theme.ErrorRed
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.PrimaryContainer
import com.rhodes.privatechat.ui.theme.Surface
import com.rhodes.privatechat.ui.theme.SurfaceVariant
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.ui.theme.TextTertiary
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject
import java.util.UUID

private val tasks = listOf("野外物资搜集", "矿区勘探调查", "城市街区巡逻", "遗迹浅层探索", "后勤物资押运")
private val durations = listOf(1, 3, 5)

@Composable
fun DispatchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val operators by viewModel.operators.collectAsState()
    var balance by remember { mutableIntStateOf(settings.lmb) }
    LaunchedEffect(Unit) { while (true) { balance = settings.lmb; delay(5000) } }
    var activeDispatch by remember { mutableStateOf<DispatchRecordEntity?>(null) }

    LaunchedEffect(Unit) {
        val active = viewModel.repository.getActiveDispatches()
        activeDispatch = active.firstOrNull()
    }

    var selectedTask by remember { mutableIntStateOf(0) }
    var selectedDuration by remember { mutableIntStateOf(0) }
    var customTask by remember { mutableStateOf("") }
    val tasksWithCustom = tasks + "自定义任务"
    var budgetText by remember { mutableStateOf("200") }
    val team = remember { mutableStateListOf<OperatorEntity>() }
    var showPicker by remember { mutableStateOf(false) }

    val budget = budgetText.toIntOrNull() ?: 0
    val teamAllExist = team.all { m -> operators.any { it.id == m.id } }
    val canStart = team.size == 5 && teamAllExist && budget >= 100 && budget <= balance && activeDispatch == null && !viewModel.dispatchViewModel.isStarting

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Icon(Icons.AutoMirrored.Filled.SendToMobile, null, tint = Primary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("干员派遣", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Card).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("龙门币: ", fontSize = 13.sp, color = TextSecondary)
                Text("${balance}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
            }
        }
        HorizontalDivider(color = Divider)
        // Active dispatch banner (after header, before content)
        if (activeDispatch != null) {
            Row(modifier = Modifier.fillMaxWidth().background(PrimaryContainer).clickable { onStart(activeDispatch!!.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.SendToMobile, null, tint = Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("小队1 · ${activeDispatch!!.taskType}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                    Text("点击查看进度", fontSize = 11.sp, color = TextSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Primary)
            }
        }

        if (operators.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无数据", color = TextTertiary)
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            // 1. 任务选择
            SectionCard("任务类型") {
                tasksWithCustom.forEachIndexed { i, t ->
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (selectedTask == i) PrimaryContainer else SurfaceVariant).clickable { selectedTask = i }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = if (selectedTask == i) Primary else TextTertiary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(t, fontSize = 14.sp, color = if (selectedTask == i) Primary else TextPrimary)
                    }
                    if (i < tasksWithCustom.size - 1) Spacer(modifier = Modifier.height(6.dp))
                }
                if (selectedTask == tasksWithCustom.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = customTask, onValueChange = { customTask = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("输入自定义任务...", color = TextTertiary) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 2. 时长
            SectionCard("派遣时长") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    durations.forEachIndexed { i, h ->
                        Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (selectedDuration == i) PrimaryContainer else SurfaceVariant).clickable { selectedDuration = i }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("${h}小时", fontSize = 14.sp, fontWeight = if (selectedDuration == i) FontWeight.Bold else FontWeight.Normal, color = if (selectedDuration == i) Primary else TextPrimary)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 3. 小队
            SectionCard("派遣小队") {
                Text("已选 ${team.size}/5 名干员", fontSize = 13.sp, color = if (team.size < 5) ErrorRed else Primary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(team) { op ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
                            OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(48.dp))
                            Text(op.name, fontSize = 11.sp, color = TextPrimary, textAlign = TextAlign.Center)
                        }
                    }
                    if (team.size < 5) {
                        item {
                            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(SurfaceVariant).clickable { showPicker = true }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 4. 预算
            SectionCard("投入后勤预算") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = budgetText, onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) budgetText = it },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("余额 ${balance}", fontSize = 13.sp, color = TextSecondary)
                }
                if (budget > balance) Text("余额不足", fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(top = 4.dp))
                else if (budget < 100) Text("最少投入100龙门币", fontSize = 12.sp, color = ErrorRed, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(modifier = Modifier.height(20.dp))

            // 5. 按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)) {
                    Icon(Icons.Default.History, null, modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("历史日志")
                }
                Button(onClick = {
                    if (viewModel.dispatchViewModel.isStarting) return@Button
                    if (activeDispatch != null) return@Button
                    if (canStart) {
                        val id = UUID.randomUUID().toString()
                        viewModel.startDispatch(id, if (selectedTask == tasksWithCustom.size - 1) customTask else tasks[selectedTask], durations[selectedDuration], budget, team.map { it.id }) { onStart(id) }
                    }
                }, modifier = Modifier.weight(1f).height(44.dp), shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (canStart) Primary else Divider)) {
                    Text(when {
                        activeDispatch != null -> "已有小队正在派遣"
                        viewModel.dispatchViewModel.isStarting -> "正在启动..."
                        else -> "开始派遣"
                    }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    }

    if (showPicker) {
        OperatorPickerDialog(operators = operators, currentTeam = team,
            onDismiss = { showPicker = false },
            onConfirm = { selected ->
                team.clear(); team.addAll(selected.take(5))
                showPicker = false
            })
    }
}

@Composable
private fun OperatorPickerDialog(
    operators: List<OperatorEntity>,
    currentTeam: List<OperatorEntity>,
    onDismiss: () -> Unit,
    onConfirm: (List<OperatorEntity>) -> Unit
) {
    val selected = remember { mutableStateListOf<OperatorEntity>().also { it.addAll(currentTeam) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择干员 (${selected.size}/5)", color = TextPrimary) }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            operators.forEach { op ->
                val checked = selected.any { it.id == op.id }
                Row(modifier = Modifier.fillMaxWidth().clickable {
                    if (checked) selected.removeAll { it.id == op.id }
                    else if (selected.size < 5) selected.add(op)
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(op.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    if (checked) Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onConfirm(selected.toList()) }) { Text("确认", color = if (selected.size == 5) Primary else TextTertiary) }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text("取消", color = TextSecondary) }
    })
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}

fun getBalance(context: Context): Int {
    val settings: SettingsRepository = org.koin.java.KoinJavaComponent.get(SettingsRepository::class.java)
    return settings.lmb
}
fun saveBalance(context: Context, value: Int) {
    val settings: SettingsRepository = org.koin.java.KoinJavaComponent.get(SettingsRepository::class.java)
    settings.lmb = value
}
