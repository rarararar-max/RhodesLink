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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun OperatorEditScreen(
    viewModel: MainViewModel,
    operator: OperatorEntity?,
    onBack: () -> Unit,
    onManageMemories: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val operators by viewModel.operators.collectAsState()
    val isNew = operator == null
    val customOperatorId = remember(operator?.id) { operator?.id ?: "custom_${java.util.UUID.randomUUID()}" }
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
    val initialOpKey = customOperatorId
    var privateSlot by remember { mutableIntStateOf(settings.getInt("operator_prompt_slot_${initialOpKey}_private", 1).coerceIn(1, 3)) }
    var groupSlot by remember { mutableIntStateOf(settings.getInt("operator_prompt_slot_${initialOpKey}_group", 1).coerceIn(1, 3)) }
    var description by remember { mutableStateOf(operator?.description ?: "") }
    var userRelation by remember { mutableStateOf(operator?.userRelation ?: "") }
    var voiceName by remember { mutableStateOf(operator?.voiceName ?: "") }
    var voiceVolume by remember { mutableFloatStateOf(settings.getOperatorVoiceVolume(customOperatorId)) }
    var relationships by remember { mutableStateOf<List<RelationshipEntity>>(emptyList()) }
    var initialRelationships by remember { mutableStateOf<List<RelationshipEntity>>(emptyList()) }
    var showAddPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnsavedConfirm by remember { mutableStateOf(false) }
    var showPromptInput by remember { mutableStateOf(false) }
    var showPromptResult by remember { mutableStateOf(false) }
    var promptResult by remember { mutableStateOf<com.rhodes.privatechat.viewmodel.MainViewModel.OperatorPromptResult?>(null) }
    var promptReqText by remember { mutableStateOf("") }
    var isGeneratingPrompt by remember { mutableStateOf(false) }

    fun opKey(): String = customOperatorId
    fun slotKey(type: String, slot: Int): String = "operator_prompt_slot_${opKey()}_${type}_$slot"
    fun activeSlotKey(type: String): String = "operator_prompt_slot_${opKey()}_${type}"
    fun loadPromptSlot(type: String, slot: Int, fallback: String): String = settings.getString(slotKey(type, slot), "")?.ifBlank { null } ?: fallback
    fun savePromptSlot(type: String, slot: Int, content: String) { settings.putString(slotKey(type, slot), content) }
    var privateSlotDrafts by remember { mutableStateOf(mapOf(privateSlot to loadPromptSlot("private", privateSlot, operator?.privatePrompt.orEmpty()))) }
    var groupSlotDrafts by remember { mutableStateOf(mapOf(groupSlot to loadPromptSlot("group", groupSlot, operator?.groupPrompt.orEmpty()))) }
    var slotDirty by remember { mutableStateOf(false) }

    LaunchedEffect(operator?.id, privateSlot) { privatePrompt = privateSlotDrafts[privateSlot] ?: loadPromptSlot("private", privateSlot, operator?.privatePrompt.orEmpty()) }
    LaunchedEffect(operator?.id, groupSlot) { groupPrompt = groupSlotDrafts[groupSlot] ?: loadPromptSlot("group", groupSlot, operator?.groupPrompt.orEmpty()) }

    // 加载已有关系
    LaunchedEffect(operator) {
        if (operator != null) {
            viewModel.loadRelationships(operator.id) {
                relationships = it
                initialRelationships = it
            }
        }
    }

    val onSave: () -> Unit = save@{
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            android.widget.Toast.makeText(context, "请输入干员名称", android.widget.Toast.LENGTH_SHORT).show()
            return@save
        }
        if (cleanName != operator?.name?.trim() && operators.any { it.id != customOperatorId && it.name.trim().equals(cleanName, ignoreCase = true) }) {
            android.widget.Toast.makeText(context, "已存在同名干员，请使用其他名称", android.widget.Toast.LENGTH_LONG).show()
            return@save
        }
        privateSlotDrafts = privateSlotDrafts + (privateSlot to privatePrompt)
        groupSlotDrafts = groupSlotDrafts + (groupSlot to groupPrompt)
        privateSlotDrafts.forEach { (slot, content) -> savePromptSlot("private", slot, content) }
        groupSlotDrafts.forEach { (slot, content) -> savePromptSlot("group", slot, content) }
        settings.putInt(activeSlotKey("private"), privateSlot)
        settings.putInt(activeSlotKey("group"), groupSlot)
        settings.putOperatorVoiceVolume(customOperatorId, voiceVolume)
        viewModel.saveOperator(
            id = customOperatorId,
            name = cleanName, title = title,
            description = description.ifBlank { "${cleanName}，罗德岛干员" },
            privatePrompt = privatePrompt, groupPrompt = groupPrompt,
            memoryInjection = operator?.memoryInjection.orEmpty(),
            userRelation = userRelation, avatarUri = avatarUri,
            autoPost = autoPost, allowChat = allowChat,
            relationships = relationships,
            activityLevel = activity,
            gender = gender,
            voiceName = voiceName,
            onComplete = { error ->
                if (error == null) onBack()
                else android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }
    val hasUnsavedChanges = name != (operator?.name ?: "新干员") ||
        title != (operator?.title ?: "") ||
        gender != (operator?.gender ?: "") ||
        activity != (operator?.activityLevel ?: 0.5f) ||
        autoPost != settings.getOperatorDynPermission(operator?.id ?: "") ||
        allowChat != settings.getOperatorMsgPermission(operator?.id ?: "") ||
        avatarUri != (operator?.avatarUri ?: "") ||
        privatePrompt != (operator?.privatePrompt ?: "") ||
        groupPrompt != (operator?.groupPrompt ?: "") ||
        description != (operator?.description ?: "") ||
        userRelation != (operator?.userRelation ?: "") ||
        voiceName != (operator?.voiceName ?: "") ||
        voiceVolume != settings.getOperatorVoiceVolume(customOperatorId) ||
        relationships != initialRelationships || slotDirty
    val requestBack = { if (hasUnsavedChanges) showUnsavedConfirm = true else onBack() }

    BackHandler { requestBack() }

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        TopBar(
            title = if (isNew) "新建干员" else operator?.name ?: name,
            onBack = requestBack,
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
                    ToggleItem("允许动态互动", checked = autoPost, onCheckedChange = { autoPost = it })
                    ToggleItem("允许主动发私聊", checked = allowChat, onCheckedChange = { allowChat = it })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                SectionTitle("头像")
                val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                    cropTarget = uri?.let { copyToCache(context, it) }
                    if (uri != null && cropTarget == null) android.widget.Toast.makeText(context, "无法读取此图片，请尝试选择JPG/PNG图片", android.widget.Toast.LENGTH_SHORT).show()
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OperatorAvatarImage(avatarUri = avatarUri, name = name, modifier = Modifier.size(80.dp).clickable { avatarPicker.launch("image/*") })
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedButton(onClick = { avatarPicker.launch("image/*") }) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("上传头像")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            SectionCard {
                SectionTitle("语音音色")
                Text("语音通话、陪睡和 TTS 会优先使用这里填写的音色ID；未填写时使用当前 TTS 服务商提供的默认音色。", fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                LabeledField("音色ID") {
                    OutlinedTextField(
                        value = voiceName,
                        onValueChange = { voiceName = it },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = fieldColors(),
                        singleLine = true,
                        placeholder = { Text("如：male-qn-qingse 或 Vocu Voice ID", fontSize = 13.sp, color = TextTertiary) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LabeledField("播放音量 (${(voiceVolume * 100).toInt()}%)") {
                    Slider(
                        value = voiceVolume,
                        onValueChange = { voiceVolume = it },
                        valueRange = 0.2f..1.0f,
                        steps = 15,
                        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Blue400, activeTrackColor = Blue400)
                    )
                }
                Text("影响私聊、群聊、陪睡和语音通话；100% 为默认和最大音量。", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val settings = org.koin.java.KoinJavaComponent.get<com.rhodes.privatechat.shared.settings.SettingsRepository>(com.rhodes.privatechat.shared.settings.SettingsRepository::class.java)
                                val key = settings.ttsApiKey.ifBlank { settings.apiKey }
                                if (settings.ttsBaseUrl.isBlank() || key.isBlank()) {
                                    android.widget.Toast.makeText(context, "请先在模型设置中配置 TTS", android.widget.Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                val testVoiceId = voiceName.ifBlank {
                                    if (settings.ttsProvider == "vocu") {
                                        android.widget.Toast.makeText(context, "Vocu 请先填写该角色的 Voice ID", android.widget.Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    "male-qn-qingse"
                                }
                                val audioBytes = com.rhodes.privatechat.shared.voice.createTtsGateway(settings.ttsBaseUrl, key, settings.ttsModelName, settings.ttsProvider)
                                    .synthesize(com.rhodes.privatechat.shared.voice.TtsRequest("你好", testVoiceId)).audioBytes
                                if (audioBytes != null && audioBytes.isNotEmpty()) {
                                    val file = java.io.File(context.cacheDir, "tts_test_voice.mp3")
                                    file.writeBytes(audioBytes)
                                    android.media.MediaPlayer().apply {
                                        setDataSource(file.absolutePath)
                                        setVolume(voiceVolume, voiceVolume)
                                        prepare()
                                        start()
                                        setOnCompletionListener { release(); file.delete() }
                                    }
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "音色测试失败：${e.message?.take(180) ?: "未知错误"}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = com.rhodes.privatechat.ui.theme.Primary)
                ) {
                    androidx.compose.material3.Text("测试音色", color = androidx.compose.ui.graphics.Color.White)
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
                PromptSlotBar(
                    selected = privateSlot,
                    onSelect = { slot ->
                        privateSlotDrafts = privateSlotDrafts + (privateSlot to privatePrompt)
                        slotDirty = true
                        privateSlot = slot
                        privatePrompt = privateSlotDrafts[slot] ?: loadPromptSlot("private", slot, if (slot == 1) operator?.privatePrompt ?: "" else "")
                    }
                )
                PromptField(title = "私聊人设 ${privateSlot}号", value = privatePrompt, onValueChange = { privatePrompt = it; slotDirty = true }, placeholder = "输入该干员的私聊性格描述...")
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (!isNew) {
                SectionCard {
                    SectionTitle("角色记忆")
                    Text("查看、删除或手动补充该角色的结构化记忆；记忆索引会随之同步。", fontSize = 12.sp, color = TextSecondary)
                    TextButton(onClick = onManageMemories, modifier = Modifier.align(Alignment.End)) { Text("管理记忆", color = Primary) }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            SectionCard {
                SectionTitle("群聊人设")
                PromptSlotBar(
                    selected = groupSlot,
                    onSelect = { slot ->
                        groupSlotDrafts = groupSlotDrafts + (groupSlot to groupPrompt)
                        slotDirty = true
                        groupSlot = slot
                        groupPrompt = groupSlotDrafts[slot] ?: loadPromptSlot("group", slot, if (slot == 1) operator?.groupPrompt ?: "" else "")
                    }
                )
                PromptField(title = "群聊人设 ${groupSlot}号", value = groupPrompt, onValueChange = { groupPrompt = it; slotDirty = true }, placeholder = "输入该干员的群聊性格描述...")
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
                RelationshipHelpCard()
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
                    val operatorId = operator?.id.orEmpty()
                    if (operatorId.isBlank()) return@TextButton
                    showDeleteConfirm = false
                    viewModel.deleteOperators(listOf(operatorId)) { error ->
                        if (error == null) onBack()
                        else android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
                    }
                }) { Text("确认删除", color = ErrorRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消", color = TextSecondary) } }
        )
    }

    if (showUnsavedConfirm) {
        AlertDialog(
            onDismissRequest = { showUnsavedConfirm = false },
            title = { Text("放弃修改？", color = TextPrimary) },
            text = { Text("当前编辑内容尚未保存，返回后会丢失。", color = TextSecondary) },
            confirmButton = { TextButton(onClick = { showUnsavedConfirm = false; onBack() }) { Text("放弃", color = ErrorRed) } },
            dismissButton = { TextButton(onClick = { showUnsavedConfirm = false }) { Text("继续编辑", color = TextSecondary) } },
            containerColor = Card
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
            onConfirm = { cropped -> scope.launch { avatarUri = com.rhodes.privatechat.util.copyToInternalStorageAsync(context, cropped); cropTarget = null } },
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
            onDismissRequest = {
                viewModel.cancelOperatorPromptGeneration()
                isGeneratingPrompt = false
            },
            title = { Text("AI 生成中...", color = TextPrimary) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("通常会在 30 秒内完成；可随时取消后重新生成", fontSize = 14.sp, color = TextSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.cancelOperatorPromptGeneration()
                    isGeneratingPrompt = false
                }) { Text("取消") }
            },
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
private fun RelationshipHelpCard() {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Primary.copy(alpha = 0.08f)).padding(12.dp)) {
        Text("关系是当前干员眼中的关系", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            "例如在能天使页面添加“德克萨斯：挚友”，表示能天使把德克萨斯视为挚友。德克萨斯是否也这样看待能天使，需要到德克萨斯页面单独设置。",
            fontSize = 11.sp,
            color = TextSecondary,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(4.dp))
        Text("关系会影响群聊互动和日记内容。当前角色与对方越亲近，对方在私聊中越可能自然知道当前角色与用户的近期私聊近况；关系默认单向。", fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun IntimacyHelpButton() {
    var show by remember { mutableStateOf(false) }
    Text(
        "?",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Primary,
        modifier = Modifier.clip(CircleShape).background(Primary.copy(alpha = 0.12f)).clickable { show = true }.padding(horizontal = 6.dp, vertical = 2.dp)
    )
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text("亲密度有什么用", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "亲密度表示两个干员之间的熟悉和信任程度，也决定对方在私聊中能从当前角色处自然知道多少用户近况。\n\n0-19：基本不知道\n20-39：只会知道一件很重要的事\n40-59：会知道部分重要计划、偏好或经历\n60-79：会知道较多近期近况\n80+：非常亲近，能自然了解更多互动经历\n\n关系默认单向：当前角色与对方越亲近，对方越可能从当前角色处听说用户的私聊近况；如需双方互相知道，请分别设置双向关系。关系网近况只在私聊中使用，不会同步到群聊或公开内容。",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    lineHeight = 20.sp
                )
            },
            confirmButton = { TextButton(onClick = { show = false }) { Text("知道了", color = Primary) } },
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
private fun PromptSlotBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..3).forEach { slot ->
            val active = slot == selected
            Text(
                text = "${slot}号",
                fontSize = 13.sp,
                color = if (active) Color.White else Primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (active) Primary else PrimaryContainer)
                    .clickable { onSelect(slot) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
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
        Text("当前视角：${opName}是${rel.relatedOperatorName}的", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        TypePicker(selected = rel.type, onSelect = { onUpdate(rel.copy(type = it)) })
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("亲密度", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.width(48.dp))
            Spacer(Modifier.width(4.dp))
            IntimacyHelpButton()
            Spacer(Modifier.width(6.dp))
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
                    Text("请选择“当前干员是对方的什么”。例如在 A 的页面选择“姐姐”，表示“A 是 B 的姐姐”。如果希望 B 也知道这层关系，需要到 B 的页面再设置“妹妹”。", fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
                    Spacer(Modifier.height(8.dp))
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
    var query by remember { mutableStateOf("") }
    val filtered = remember(operators, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) operators else operators.filter { op ->
            op.name.contains(keyword, ignoreCase = true) || op.title.contains(keyword, ignoreCase = true)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择关联干员") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("搜索角色名称或称号") },
                    shape = RoundedCornerShape(8.dp),
                    colors = fieldColors()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (filtered.isEmpty()) {
                    Text("没有匹配的角色", fontSize = 13.sp, color = TextTertiary, modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                filtered.forEach { op ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(op) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OperatorAvatarImage(avatarUri = op.avatarUri, name = op.name, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(op.name, fontSize = 14.sp)
                            if (op.title.isNotBlank()) Text(op.title, fontSize = 11.sp, color = TextSecondary)
                        }
                    }
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
    if (type == RelationshipType.LOVE_RIVAL) return "情敌"
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
