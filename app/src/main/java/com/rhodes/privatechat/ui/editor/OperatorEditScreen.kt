package com.rhodes.privatechat.ui.editor

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.data.db.entity.OperatorEntity
import com.rhodes.privatechat.ui.common.FullscreenTextField
import com.rhodes.privatechat.ui.common.OperatorAvatarImage
import com.rhodes.privatechat.data.db.entity.RelationshipEntity
import com.rhodes.privatechat.data.db.entity.RelationshipType
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.util.copyToCache
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.koin.compose.koinInject

@Composable
fun OperatorEditScreen(
    viewModel: MainViewModel,
    operator: OperatorEntity?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val operators by viewModel.operators.collectAsState()
    val isNew = operator == null
    var cropTarget by remember { mutableStateOf<android.net.Uri?>(null) }

    var name by remember { mutableStateOf(operator?.name ?: "新干员") }
    var title by remember { mutableStateOf(operator?.title ?: "") }
    var gender by remember { mutableStateOf(operator?.gender ?: "") }
    var activity by remember { mutableFloatStateOf(operator?.activityLevel ?: 0.5f) }
    val settings: SettingsRepository = koinInject()
    var autoPost by remember { mutableStateOf(settings.getOperatorDynPermission(operator?.id ?: "")) }
    var allowChat by remember { mutableStateOf(settings.getOperatorMsgPermission(operator?.id ?: "")) }
    var avatarUri by remember { mutableStateOf(operator?.avatarUri ?: "") }
    var privatePrompt by remember { mutableStateOf(operator?.privatePrompt ?: "") }
    var groupPrompt by remember { mutableStateOf(operator?.groupPrompt ?: "") }
    var description by remember { mutableStateOf(operator?.description ?: "") }
    var userRelation by remember { mutableStateOf(operator?.userRelation ?: "") }
    var relationships by remember { mutableStateOf<List<RelationshipEntity>>(emptyList()) }
    var showAddPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPromptInput by remember { mutableStateOf(false) }
    var showPromptResult by remember { mutableStateOf(false) }
    var promptResult by remember { mutableStateOf<com.rhodes.privatechat.viewmodel.MainViewModel.OperatorPromptResult?>(null) }
    var promptReqText by remember { mutableStateOf("") }
    var isGeneratingPrompt by remember { mutableStateOf(false) }

    // 加载已有关系
    LaunchedEffect(operator) {
        if (operator != null) {
            viewModel.loadRelationships(operator.id) { relationships = it }
        }
    }

    val onSave: () -> Unit = {
        viewModel.saveOperator(
            id = operator?.id ?: name.lowercase(),
            name = name, title = title,
            description = description.ifBlank { "${name}，罗德岛干员" },
            privatePrompt = privatePrompt, groupPrompt = groupPrompt,
            userRelation = userRelation, avatarUri = avatarUri,
            autoPost = autoPost, allowChat = allowChat,
            relationships = relationships,
            activityLevel = activity,
            gender = gender,
            onComplete = { onBack() }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        TopBar(
            title = if (isNew) "新建干员" else operator!!.name,
            onBack = onBack,
            onSave = onSave
        )

        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionCard {
                SectionTitle("基础信息")
                LabeledField("干员名称") {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LabeledField("性别（选填）") {
                    OutlinedTextField(
                        value = gender, onValueChange = { gender = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                        singleLine = true,
                        placeholder = { Text("如：男、女、无性别", fontSize = 13.sp, color = TextTertiary) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LabeledField("身份") {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                        singleLine = true,
                        placeholder = { Text("如：罗德岛公开领袖", fontSize = 13.sp, color = TextTertiary) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                LabeledField("活跃度 (${String.format("%.1f", activity)})") {
                    Slider(value = activity, onValueChange = { activity = it },
                        valueRange = 0f..1f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = Blue400, activeTrackColor = Blue400
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ToggleItem("自动发布动态", checked = autoPost, onCheckedChange = { autoPost = it })
                    ToggleItem("允许主动私聊", checked = allowChat, onCheckedChange = { allowChat = it })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                SectionTitle("头像")
                val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    cropTarget = uri?.let { copyToCache(context, it) }
                    if (uri != null && cropTarget == null) android.widget.Toast.makeText(context, "无法读取此图片，请尝试选择JPG/PNG图片", android.widget.Toast.LENGTH_SHORT).show()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OperatorAvatarImage(avatarUri = avatarUri, name = name, modifier = Modifier.size(80.dp).clickable { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(onClick = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("上传头像")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                SectionTitle("AI 人设助手")
                Spacer(Modifier.height(4.dp))
                Text("输入角色需求，由 AI 自动生成私聊和群聊人设。如有已填内容会作为优化基础。", fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showPromptInput = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isGeneratingPrompt,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text(if (promptReqText.isNotBlank()) "🧠 AI 人设助手（点击重新输入）" else "🧠 AI 人设助手")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                SectionTitle("私聊人设")
                PromptField(title = "私聊人设", value = privatePrompt, onValueChange = { privatePrompt = it }, placeholder = "输入该干员的私聊性格描述...")
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                SectionTitle("群聊人设")
                PromptField(title = "群聊人设", value = groupPrompt, onValueChange = { groupPrompt = it }, placeholder = "输入该干员的群聊性格描述...")
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                Text("${name}是用户的", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                OutlinedTextField(value = userRelation, onValueChange = { userRelation = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    placeholder = { Text("例如：指挥官、病人、朋友...", fontSize = 13.sp, color = TextTertiary) },
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = com.rhodes.privatechat.ui.theme.Divider, unfocusedBorderColor = com.rhodes.privatechat.ui.theme.Divider))
                Spacer(modifier = Modifier.height(4.dp))
                Text("（仅私聊有用）", fontSize = 11.sp, color = TextTertiary)
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("关系网编辑")
                    TextButton(onClick = { showAddPicker = true }) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加关系")
                    }
                }
                if (relationships.isEmpty()) {
                    Text("暂无关系，点击上方添加", fontSize = 13.sp, color = TextTertiary, modifier = Modifier.padding(vertical = 8.dp))
                }
                relationships.forEachIndexed { i, rel ->
                    RelationshipEditItem(opName = operator?.name ?: name, rel = rel, allOperators = operators,
                        onUpdate = { updated -> relationships = relationships.toMutableList().also { it[i] = updated } },
                        onDelete = { relationships = relationships.toMutableList().also { it.removeAt(i) } }
                    )
                    if (i < relationships.size - 1) Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 删除按钮（仅编辑模式）
            if (!isNew) {
                Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ErrorRed.copy(alpha = 0.1f)).clickable {
                    showDeleteConfirm = true
                }.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("删除干员", fontSize = 15.sp, color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除干员", color = TextPrimary) },
            text = { Text("确定要删除${operator?.name ?: ""}吗？删除后相关数据将永久丢失且无法恢复。", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteOperator(operator?.id ?: "")
                    showDeleteConfirm = false
                    onBack()
                }) { Text("确认删除", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextSecondary) } }
        )
    }

    if (showAddPicker) {
        OperatorPickerDialog(
            operators = operators.filter { op -> operator == null || op.id != operator.id },
            onDismiss = { showAddPicker = false },
            onSelect = { op ->
                relationships = relationships + RelationshipEntity(
                    operatorId = operator?.id ?: "",
                    relatedOperatorId = op.id,
                    relatedOperatorName = op.name,
                    type = RelationshipType.FRIEND,
                    intimacy = 50
                )
                showAddPicker = false
            }
        )
    }
    cropTarget?.let { uri ->
        com.rhodes.privatechat.ui.common.ImageCropperDialog(
            imageUri = uri, aspectX = 1f, aspectY = 1f,
            onConfirm = { cropped -> avatarUri = com.rhodes.privatechat.util.copyToInternalStorage(context, cropped); cropTarget = null },
            onCancel = { cropTarget = null }
        )
    }

    // AI 人设生成 - 输入需求弹窗
    if (showPromptInput) {
        var inputText by remember { mutableStateOf(promptReqText) }
        AlertDialog(
            onDismissRequest = { if (!isGeneratingPrompt) showPromptInput = false },
            title = { Text("角色需求描述", color = TextPrimary) },
            text = {
                Column {
                    Text("请描述你想要的角色风格、性格特点、背景等：", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        placeholder = { Text("例如：一个沉默寡言的退役骑士，表面冷淡但内心温柔，偶尔会说出一针见血的话", fontSize = 13.sp, color = TextTertiary) },
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputText.isBlank()) return@Button
                        promptReqText = inputText; isGeneratingPrompt = true
                        showPromptInput = false
                        viewModel.generateOperatorPrompt(inputText, privatePrompt) { result ->
                            isGeneratingPrompt = false
                            if (result.privatePrompt.isNotBlank() || result.title.isNotBlank()) {
                                promptResult = result; showPromptResult = true
                            } else {
                                android.widget.Toast.makeText(context, "生成失败，请检查网络和 API Key", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = inputText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) { Text("开始生成") }
            },
            dismissButton = { TextButton(onClick = { showPromptInput = false }) { Text("取消", color = TextSecondary) } },
            containerColor = Card
        )
    }

    // AI 人设生成 - 加载中
    if (isGeneratingPrompt) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 生成中...", color = TextPrimary) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("正在根据需求生成人设，请稍候", fontSize = 14.sp, color = TextSecondary)
                }
            },
            confirmButton = {},
            containerColor = Card
        )
    }

    // AI 人设生成 - 预览结果弹窗
    if (showPromptResult && promptResult != null) {
        val r = promptResult!!
        AlertDialog(
            onDismissRequest = { showPromptResult = false },
            title = { Text("AI 生成的人设", color = TextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (r.title.isNotBlank() || r.gender.isNotBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (r.title.isNotBlank()) {
                                Column(Modifier.weight(1f)) {
                                    Text("身份", fontSize = 11.sp, color = TextTertiary)
                                    Text(r.title, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (r.gender.isNotBlank()) {
                                Column(Modifier.weight(1f)) {
                                    Text("性别", fontSize = 11.sp, color = TextTertiary)
                                    Text(r.gender, fontSize = 14.sp, color = TextPrimary)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (r.description.isNotBlank()) {
                        Text("简介", fontSize = 11.sp, color = TextTertiary)
                        Text(r.description, fontSize = 13.sp, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                    }
                    Text("私聊人设", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(r.privatePrompt.ifBlank { "（无）" }, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("群聊人设", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Primary)
                    Spacer(Modifier.height(4.dp))
                    Text(r.groupPrompt.ifBlank { "（无）" }, fontSize = 13.sp, color = TextPrimary, lineHeight = 20.sp)
                }
            },
            confirmButton = {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(onClick = { showPromptResult = false; showPromptInput = true }) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("重新生成")
                        }
                        TextButton(onClick = { showPromptResult = false }) {
                            Text("取消", color = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            title = r.title; gender = r.gender
                            description = r.description
                            privatePrompt = r.privatePrompt
                            groupPrompt = r.groupPrompt
                            showPromptResult = false
                            android.widget.Toast.makeText(context, "已应用到当前角色", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) { Text("✓ 使用这份人设") }
                }
            },
            dismissButton = {},
            containerColor = Card
        )
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Surface).padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
        Spacer(modifier = Modifier.weight(1f))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onSave) {
            Icon(Icons.Default.Check, null, tint = Blue400, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text("保存", color = Blue400, fontWeight = FontWeight.SemiBold)
        }
    }
    HorizontalDivider(color = Divider)
}

@Composable
private fun AvatarSection(avatarUri: String, onPick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(Gray100).border(2.dp, Divider, CircleShape).clickable(onClick = onPick),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri.isNotBlank()) {
                coil3.compose.AsyncImage(model = avatarUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoCamera, null, tint = TextTertiary, modifier = Modifier.size(32.dp))
                    Text("点击上传", fontSize = 11.sp, color = TextTertiary)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(16.dp)) {
        content()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        content()
    }
}

@Composable
private fun ToggleItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Blue400, checkedTrackColor = PrimaryContainer, uncheckedThumbColor = TextSecondary, uncheckedTrackColor = Divider))
    }
}

@Composable
private fun PromptField(title: String, value: String, onValueChange: (String) -> Unit, placeholder: String) {
    FullscreenTextField(title = title, value = value, onValueChange = onValueChange, placeholder = placeholder, minHeight = 120.dp)
}

@Composable
private fun RelationshipEditItem(
    opName: String, rel: RelationshipEntity, allOperators: List<OperatorEntity>,
    onUpdate: (RelationshipEntity) -> Unit, onDelete: () -> Unit
) {
    val op = allOperators.find { it.id == rel.relatedOperatorId }
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Gray100).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OperatorAvatarImage(avatarUri = op?.avatarUri ?: "", name = rel.relatedOperatorName, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(rel.relatedOperatorName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(op?.title ?: "", fontSize = 11.sp, color = TextSecondary)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "删除", tint = ErrorRed, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("${opName}是${rel.relatedOperatorName}的", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        TypePicker(selected = rel.type, onSelect = { onUpdate(rel.copy(type = it)) })
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("亲密度", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(48.dp))
            Slider(value = rel.intimacy.toFloat(), onValueChange = { onUpdate(rel.copy(intimacy = it.toInt())) },
                valueRange = 0f..100f, modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400))
            Text("${rel.intimacy}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Blue400, modifier = Modifier.width(32.dp))
        }
    }
}

@Composable
private fun TypePicker(selected: RelationshipType, onSelect: (RelationshipType) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Column {
        Text("关系类型", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Surface)
                .clickable { showPicker = true }.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text(typeName(selected), fontSize = 13.sp, color = TextPrimary)
            Text("▼", fontSize = 10.sp, color = TextTertiary)
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("选择关系类型", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RelationshipType.values().forEach { type ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (type == selected) PrimaryContainer else Color.Transparent)
                                .clickable { onSelect(type); showPicker = false }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                typeName(type),
                                fontSize = 14.sp,
                                color = if (type == selected) Primary else TextPrimary,
                                fontWeight = if (type == selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        if (type != RelationshipType.values().last()) {
                            HorizontalDivider(color = Divider.copy(alpha = 0.3f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("取消", color = TextSecondary)
                }
            },
            containerColor = Card
        )
    }
}

@Composable
private fun OperatorPickerDialog(
    operators: List<OperatorEntity>,
    onDismiss: () -> Unit,
    onSelect: (OperatorEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择关联干员") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                operators.forEach { op ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(op) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(op.name, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun typeName(type: RelationshipType): String {
    if (type == RelationshipType.BIG_SISTER) return "姐姐"
    if (type == RelationshipType.LITTLE_SISTER) return "妹妹"
    if (type == RelationshipType.BIG_BROTHER) return "哥哥"
    if (type == RelationshipType.LITTLE_BROTHER) return "弟弟"
    if (type == RelationshipType.MOTHER) return "母亲"
    if (type == RelationshipType.FATHER) return "父亲"
    if (type == RelationshipType.DAUGHTER) return "女儿"
    if (type == RelationshipType.SON) return "儿子"
    if (type == RelationshipType.GUARDIAN) return "监护人"
    if (type == RelationshipType.BOSS) return "上司"
    if (type == RelationshipType.SUBORDINATE) return "下属"
    if (type == RelationshipType.CAPTAIN) return "队长"
    if (type == RelationshipType.MEMBER) return "队员"
    if (type == RelationshipType.MENTOR) return "导师"
    if (type == RelationshipType.STUDENT) return "学生"
    if (type == RelationshipType.CLOSE_FRIEND) return "挚友"
    if (type == RelationshipType.FRIEND) return "朋友"
    if (type == RelationshipType.COMRADE) return "战友"
    if (type == RelationshipType.TEAMMATE) return "队友"
    if (type == RelationshipType.RIVAL) return "对手"
    if (type == RelationshipType.CRUSH) return "暗恋对象"
    if (type == RelationshipType.LOVER) return "恋人"
    if (type == RelationshipType.FAMILY) return "家人"
    return "陌生"
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Blue400,
    unfocusedBorderColor = Divider
)
