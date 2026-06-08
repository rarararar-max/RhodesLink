package com.rhodes.privatechat.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject

data class MemberState(val op: OperatorEntity, val muted: Boolean = false)

@Composable
fun GroupEditScreen(
    viewModel: MainViewModel,
    groupId: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings: SettingsRepository = koinInject()
    val operators by viewModel.operators.collectAsState()
    var groupName by remember { mutableStateOf("") }
    val members = remember { mutableStateListOf<MemberState>() }
    var rules by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var showDismissConfirm by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var autoSpeak by remember { mutableStateOf(settings.getGroupAuto(groupId)) }
    var avatarUri by remember { mutableStateOf("") }
    var cropTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> cropTarget = uri }

    // 加载已有群头像
    LaunchedEffect(groupId) {
        if (groupId.isNotBlank()) {
            val session = viewModel.repository.getSession(groupId)
            if (session != null) avatarUri = session.avatarUri
            viewModel.loadGroupData(groupId) { name, mems, rls, mutedIds ->
                groupName = name
                members.clear()
                mems.forEach { m -> members.add(MemberState(m, muted = m.id in mutedIds || m.name in mutedIds)) }
                rules = rls
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Spacer(modifier = Modifier.weight(1f))
            Text("群聊编辑", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val resolvedId = if (groupId.isBlank()) "group_${java.util.UUID.randomUUID()}" else groupId
                viewModel.saveGroup(resolvedId, groupName, members.map { it.op.id }, rules, avatarUri, members.filter { it.muted }.map { it.op.id }) {
                    viewModel.setAutoGroupChatEnabled(resolvedId, autoSpeak)
                    onBack()
                }
            }) {
                Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text("保存", color = Primary, fontWeight = FontWeight.SemiBold)
            }
        }
        HorizontalDivider(color = Divider)

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionCard("基本信息") {
                OutlinedTextField(value = groupName, onValueChange = { groupName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp), placeholder = { Text("输入群名称", color = TextSecondary) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(52.dp).clip(CircleShape).background(Primary).clickable { avatarPicker.launch("image/*") }, contentAlignment = Alignment.Center) {
                        if (avatarUri.isNotBlank()) {
                            coil3.compose.AsyncImage(model = avatarUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(if (avatarUri.isNotBlank()) "点击更换群头像" else "点击设置群头像", fontSize = 13.sp, color = TextSecondary, modifier = Modifier.clickable { avatarPicker.launch("image/*") })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard("群规") {
                OutlinedTextField(value = rules, onValueChange = { rules = it }, modifier = Modifier.fillMaxWidth().height(100.dp), shape = RoundedCornerShape(8.dp), placeholder = { Text("输入群聊行为规则...", color = TextSecondary) }, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("群聊自动聊天", fontSize = 14.sp, color = TextPrimary)
                Switch(checked = autoSpeak, onCheckedChange = { autoSpeak = it }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard("群成员") {
                val avatarScrollState = rememberScrollState()
                Box {
                    Row(modifier = Modifier.horizontalScroll(avatarScrollState).padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(SurfaceVariant).clickable { showPicker = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Primary, modifier = Modifier.size(22.dp)) }
                        members.forEach { m ->
                            OperatorAvatarImage(avatarUri = m.op.avatarUri, name = m.op.name, modifier = Modifier.size(44.dp))
                        }
                    }
                    // 左侧渐变遮罩
                    if (avatarScrollState.value > 0) {
                        Box(modifier = Modifier.align(Alignment.CenterStart).size(width = 20.dp, height = 44.dp).background(Brush.horizontalGradient(listOf(Card, Color.Transparent))))
                    }
                    // 右侧渐变遮罩
                    if (avatarScrollState.value < avatarScrollState.maxValue) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd).size(width = 20.dp, height = 44.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, Card))))
                    }
                }
                if (members.isEmpty()) {
                    Text("暂无成员，点击添加", fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(top = 8.dp))
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    members.forEachIndexed { i, m ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceVariant).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OperatorAvatarImage(avatarUri = m.op.avatarUri, name = m.op.name, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(m.op.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text(if (m.muted) "已禁言" else "正常", fontSize = 11.sp, color = if (m.muted) ErrorRed else TextSecondary)
                            }
                            Switch(checked = !m.muted, onCheckedChange = { b -> members[i] = m.copy(muted = !b) }, colors = SwitchDefaults.colors(checkedThumbColor = Primary, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = ErrorRed, uncheckedTrackColor = ErrorRed.copy(alpha = 0.3f)), modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { members.removeAt(i) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "移除", tint = TextTertiary, modifier = Modifier.size(16.dp)) }
                        }
                        if (i < members.size - 1) Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (groupId.isNotBlank()) {
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ErrorRed.copy(alpha = 0.1f)).clickable { showDismissConfirm = true }.padding(16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("解散该群", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed)
                }
            }
        }
    }
    }

    if (showDismissConfirm) {
        AlertDialog(
            onDismissRequest = { showDismissConfirm = false },
            title = { Text("解散群聊", color = TextPrimary) },
            text = { Text("解散后群聊将永久删除且无法恢复，确定要解散吗？", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(groupId)
                    showDismissConfirm = false
                    onBack()
                }) { Text("确认解散", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showDismissConfirm = false }) { Text("取消", color = TextSecondary) } }
        )
    }

    cropTarget?.let { uri ->
        com.rhodes.privatechat.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 1f, aspectY = 1f,
            onConfirm = { cropped -> avatarUri = com.rhodes.privatechat.util.copyToInternalStorage(ctx, cropped); cropTarget = null },
            onCancel = { cropTarget = null }
        )
    }
    if (showPicker) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredOperators = remember(searchQuery, operators) {
            if (searchQuery.isBlank()) operators
            else operators.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        AlertDialog(onDismissRequest = { showPicker = false; searchQuery = "" }, title = { Text("选择群成员", color = TextPrimary) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("搜索干员...", color = TextTertiary) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextTertiary, modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = Divider)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        filteredOperators.forEach { op ->
                            val checked = members.any { it.op.id == op.id }
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                if (checked) members.removeAll { it.op.id == op.id }
                                else members.add(MemberState(op))
                            }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(op.name, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                                if (checked) Icon(Icons.Default.Check, null, tint = Primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false; searchQuery = "" }) { Text("完成", color = Primary) } }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Card).padding(16.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 10.dp))
        content()
    }
}
