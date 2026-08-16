package com.rhodes.privatechat.galgame_full

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min
import com.rhodes.privatechat.viewmodel.shared.SharedUtils

private val Ink = Color(0xFF141522)
private val Panel = Color(0xD9222338)
private val Accent = Color(0xFFE9A8BD)
private val Soft = Color(0xFFC9C7DD)
private val FieldText = Color(0xFFF7F3FF)
private val FieldPlaceholder = Color(0xFFBDB8D2)
private val FieldBorder = Color(0xFF938BAF)
private val FieldFocusedBorder = Color(0xFFFFB6CF)

private fun autoSpriteScale(bitmap: android.graphics.Bitmap): Float {
    var top = bitmap.height
    var bottom = -1
    val step = max(4, max(bitmap.width, bitmap.height) / 600)
    for (y in 0 until bitmap.height step step) {
        for (x in 0 until bitmap.width step step) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 12) {
                top = min(top, y)
                bottom = max(bottom, y)
            }
        }
    }
    val visibleFraction = if (bottom >= top) ((bottom - top + 1).toFloat() / bitmap.height).coerceAtLeast(.05f) else 1f
    return (0.86f / (0.92f * visibleFraction)).coerceIn(.55f, 1.35f)
}

private fun projectIssues(project: ProjectConfig, assets: ProjectAssets): List<String> = buildList {
    if (project.title.isBlank()) add("请填写游戏名称。")
    if (project.description.isBlank()) add("请填写游戏剧情描述。")
    if (project.characters.isEmpty()) add("请至少创建一个角色。")
    project.characters.forEach { character ->
        val label = character.name.ifBlank { "未命名角色" }
        if (character.name.isBlank()) add("角色缺少名称。")
        if (character.personality.isBlank()) add("角色“$label”缺少角色设定。")
        if (character.sprites.isEmpty()) add("角色“$label”至少需要一张立绘。")
        if (character.sprites.any { ref -> assets.sprites.none { it.id == ref.assetId && it.characterId == character.id } }) add("角色“$label”引用了不存在或不匹配的立绘。请重新保存该角色以修复归属。")
    }
    if (project.playerCharacterId.isBlank() || project.characters.none { it.id == project.playerCharacterId }) add("请设置一个有效的玩家角色。")
    if (project.chapters.isEmpty()) add("请至少添加一个章节。")
    project.chapters.forEach { chapter ->
        if (chapter.title.isBlank()) add("第${chapter.id}章缺少章节名称。")
        if (chapter.contentDescription.isBlank()) add("第${chapter.id}章缺少内容描述。")
        if (chapter.allowedCharacterIds.isEmpty()) add("第${chapter.id}章至少需要一名出场角色。")
        if (project.playerCharacterId.isNotBlank() && project.playerCharacterId !in chapter.allowedCharacterIds) add("第${chapter.id}章没有自动包含玩家角色。")
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { androidx.compose.material3.Text("Galgame") }
    }
}

@Composable
private fun EditorHome(state: GameState, project: () -> Unit, assets: () -> Unit, validate: () -> Unit, progress: () -> Unit, resetPlaytest: () -> Unit, playtest: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("创作者编辑器", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("${state.project.title} · 本地草稿", color = Soft, modifier = Modifier.padding(top = 4.dp))
        Text("按章节设计你的 Galgame，检查通过后进入试玩。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
        Text("章节时间线", color = Accent, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
        state.project.chapters.sortedBy { it.id }.forEach { chapter ->
            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("第 ${chapter.id} 章  ${chapter.title}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(chapter.goal, color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
                Text("允许角色：${chapter.allowedCharacterIds.joinToString("、")}", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                OutlinedButton({ project() }, Modifier.padding(top = 10.dp)) { Text("编辑本章") }
            }
        }
        Button(project, Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("编辑项目结构 / 添加章节") }
        EditorCard("资源库", "上传透明 PNG 立绘和场景背景，调整显示适配。", "管理资源", assets)
        EditorCard("项目检查", "检查角色、条件、变量和资源引用是否有效。", "检查项目", validate)
        Spacer(Modifier.height(10.dp))
        Button(playtest, Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("进入游戏试玩", fontWeight = FontWeight.Bold) }
        OutlinedButton(resetPlaytest, Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("重置试玩进度") }
    }
}

@Composable
private fun EditorCard(title: String, description: String, action: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(description, color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
        OutlinedButton(onClick) { Text(action) }
    }
}

@Composable
private fun EditorTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = false, minLines: Int = 1, placeholder: String = "", suggestedLength: Int? = null) {
    val warning = suggestedLength != null && value.length > suggestedLength
    var showHelp by remember(label) { mutableStateOf(false) }
    val help = fieldHelp(label, placeholder)
    Column(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Soft, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text("如何填写？", color = Accent, fontSize = 12.sp, modifier = Modifier.clickable { showHelp = true }.padding(start = 8.dp, top = 4.dp, bottom = 4.dp))
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = null,
        placeholder = placeholder.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = FieldText,
            unfocusedTextColor = FieldText,
            disabledTextColor = FieldPlaceholder,
            cursorColor = Accent,
            focusedBorderColor = FieldFocusedBorder,
            unfocusedBorderColor = FieldBorder,
            focusedLabelColor = FieldFocusedBorder,
            unfocusedLabelColor = Soft,
            focusedPlaceholderColor = FieldPlaceholder,
            unfocusedPlaceholderColor = FieldPlaceholder
        )
    )
    suggestedLength?.let { limit ->
        Text("${value.length} / 建议 $limit 字" + if (warning) "。内容较长，可能增加恢复时间或 AI 运行失败概率。" else "", color = if (warning) Accent else Soft, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 3.dp))
    }
    }
    if (showHelp) AlertDialog(
        onDismissRequest = { showHelp = false },
        containerColor = Panel,
        titleContentColor = Color.White,
        textContentColor = Soft,
        title = { Text(help.title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Column {
            Text(help.description, color = FieldText, lineHeight = 20.sp)
            Text("示例", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text(help.example, color = Color.White, lineHeight = 20.sp, modifier = Modifier.padding(top = 5.dp))
        } },
        confirmButton = { Button({ showHelp = false }) { Text("知道了", color = Ink) } }
    )
}

private data class FieldHelp(val title: String, val description: String, val example: String)

private fun fieldHelp(label: String, placeholder: String): FieldHelp {
    val key = label.substringBefore("（")
    return when (key) {
        "游戏名称" -> FieldHelp("游戏名称", "给作品取一个方便识别的名字。可以是故事名、主角名或你想表达的主题，不需要写剧情简介。", "雨夜的约定")
        "游戏简介" -> FieldHelp("游戏简介", "用几句话告诉 AI：这是一个什么故事、玩家大概会经历什么、整体世界是什么样。写核心设定即可，不用把每章剧情全写进去。", "玩家来到一座与外界隔绝的小岛，在认识几位居民的过程中，逐渐发现岛上隐藏的秘密。")
        "创作风格" -> FieldHelp("创作风格", "告诉 AI 你希望故事读起来是什么感觉，例如节奏快慢、偏日常还是偏悬疑、描写重点是什么。", "节奏偏慢，重点描写角色之间的日常互动和感情变化，不要很快进入高潮。")
        "创作禁区" -> FieldHelp("创作禁区", "写你不希望故事出现的内容。AI 会尽量避开这些内容。没有特别要求可以留空。", "不要出现角色突然死亡、血腥描写，或没有铺垫的超自然设定。")
        "角色名称" -> FieldHelp("角色名称", "这是玩家和 AI 看到的角色名字。写角色平时被称呼的名字即可。", "绫")
        "性格与背景" -> FieldHelp("性格与背景", "写这个人是什么样、经历过什么、在故事里处于什么处境。优先写能影响说话和行动的内容。", "外表冷静，遇到陌生人会保持距离。小时候在岛上生活过，因此对岛上的旧传闻很敏感。")
        "角色目标" -> FieldHelp("角色目标", "写这个角色目前想得到什么、想解决什么。它可以是公开目标，也可以是角色自己心里的愿望。", "想查清岛上异常灯光的来源，但不希望其他人知道自己曾经参与其中。")
        "说话方式" -> FieldHelp("说话方式", "写角色说话的语气和习惯。这样 AI 更容易让角色说话像同一个人。", "说话简短克制，不轻易说出真实情绪；紧张时会把话题转开。")
        "与玩家的关系" -> FieldHelp("与玩家的关系", "写角色现在怎么看玩家。可以写刚认识、信任、疏远、暧昧、争吵后等状态。", "刚认识，表面客气但有所防备；玩家帮过她一次后，开始愿意多说几句话。")
        "角色秘密" -> FieldHelp("角色秘密", "写角色知道但暂时不会主动告诉玩家的事。AI 会把它当作隐藏信息，不会轻易直接说出来。", "她其实知道异常灯光的来源，但担心说出来会牵连家人。")
        "角色禁区" -> FieldHelp("角色禁区", "写角色不会做、不会说，或绝对不能被 AI 改掉的行为习惯。", "不会主动提起童年经历；即使生气也不会辱骂玩家。")
        "立绘名称" -> FieldHelp("立绘名称", "给这张图片起一个你自己能一眼分辨的名字。可以写情绪、状态、地点或装扮。", "受伤后的卧室便装")
        "使用条件" -> FieldHelp("立绘使用条件", "告诉 AI 什么时候适合用这张立绘。可以写受伤、地点、装扮、行为、关系或情绪，不需要只写表情。", "角色受伤后在医院接受治疗时使用，情绪疲惫或虚弱时优先使用。")
        "背景名称" -> FieldHelp("背景名称", "给场景图起一个清楚的名字，最好包含地点和时间或天气。", "雨天的车站")
        "背景使用条件" -> FieldHelp("背景使用条件", "告诉 AI 什么时候适合切换到这个场景。写地点、时间、人物行为或氛围即可。", "放学后的雨天车站，角色和玩家等待回家或进行安静交谈时使用。")
        "章节名称" -> FieldHelp("章节名称", "给这一段故事起一个简短标题，方便你在编辑器里区分和管理。", "雨中的约定")
        "章节内容" -> FieldHelp("章节内容", "写这一章可能发生哪些事、角色会遇到什么、玩家可以参与什么。它是 AI 推进本章剧情的主要依据。", "玩家陪绫前往医院处理伤势。途中绫几次想提起岛上的异常灯光，却又改变话题。玩家可以关心她，也可以追问信号。")
        "开场描述" -> FieldHelp("开场描述", "写进入本章时，玩家最先看到的画面或发生的情况。可以留空，AI 会根据章节内容开始。", "放学后的天空下起了雨，绫独自站在校门口，衣袖已经被雨水打湿。")
        "本章完成目标" -> FieldHelp("本章完成目标", "写故事发展到什么结果时，可以进入下一章。用自然语言写剧情结果，不要写变量、代码或数值。留空也能玩，但 AI 会更保守地判断。", "当玩家和绫建立初步信任，并决定一起调查异常灯光时，本章完成。")
        "章节氛围" -> FieldHelp("章节氛围", "写这一章整体给人的感觉，帮助 AI 控制对话和旁白的节奏。", "安静、克制，带一点暧昧和不安。")
        "本章注意事项" -> FieldHelp("本章注意事项", "写这一章推进时要特别遵守的要求，例如不要太快揭露真相、不要让角色做某件事。", "不要直接揭露异常灯光的来源，只能通过对话和环境给出暗示。")
        "本章不可提前揭露内容" -> FieldHelp("本章不可提前揭露内容", "写这一章绝对不能直接说出的秘密或后续剧情。这样可以防止 AI 过早剧透。", "不要说出绫已经知道异常灯光来源，也不要解释岛上的最终秘密。")
        else -> FieldHelp(label, "填写这个内容来补充项目设定。尽量写具体、能影响剧情或角色行为的信息；没有特别想法可以先留空。", placeholder.ifBlank { "例如：写下你希望 AI 在这里知道的设定。" })
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GamePlayScreen(state: GameState, assets: ProjectAssets, input: String, loading: Boolean, error: String?, onInput: (String) -> Unit, play: (String) -> Unit, history: () -> Unit, saves: () -> Unit, debug: () -> Unit, more: () -> Unit, backToEditor: () -> Unit, advanceLine: (GameState) -> Unit, continueTransition: () -> Unit, undo: () -> Unit, replay: () -> Unit, continueStory: () -> Unit) {
    val background = assets.backgrounds.find { it.id == state.scene.backgroundId }
    val sprite = assets.sprites.find { it.id == state.scene.visibleSpriteId }
    val keyboardOpen = WindowInsets.isImeVisible
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF363254), Ink)))) {
        background?.let { asset -> LoadBitmap(asset.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = BiasAlignment(asset.focusX * 2 - 1, asset.focusY * 2 - 1)) } }
        sprite?.let { asset -> LoadBitmap(asset.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.align(Alignment.BottomCenter).fillMaxHeight(.92f).padding(bottom = 185.dp).graphicsLayer(scaleX = state.scene.spriteScale * asset.scale * assets.globalSpriteScale, scaleY = state.scene.spriteScale * asset.scale * assets.globalSpriteScale, translationX = (state.scene.spriteOffsetX + asset.offsetX + assets.globalSpriteOffsetX) * 200f, translationY = (state.scene.spriteOffsetY + asset.offsetY + assets.globalSpriteOffsetY) * 200f, clip = true), contentScale = ContentScale.Fit) } }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().background(Color(0xAA141522), RoundedCornerShape(14.dp)).padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(state.project.title, color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis); Text("第${state.chapter}章 · ${state.chapterConfig()?.title.orEmpty().take(40)}", color = Color.White.copy(alpha = .82f), fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis); Text("目标：${state.goal.take(100)}", color = Color.White.copy(alpha = .72f), fontSize = 11.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                 Text("更多", color = Accent, modifier = Modifier.clickable(onClick = more).padding(8.dp))
            }
            if (!keyboardOpen) Spacer(Modifier.weight(1f))
            when {
                state.endingShown -> EndingCard(replay, continueStory)
                state.isPlayingLines -> LinePlayer(state, assets, advanceLine)
                state.pendingTransition != null -> TransitionCard(state.pendingTransition, continueTransition)
                else -> {
                    if (!keyboardOpen) {
                        DialoguePanel(state)
                        error?.let { Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        val choices = state.choices.ifEmpty { listOf("继续观察", "询问当前发生的事", "表达自己的想法") }
                         if (loading) LoadingRow() else choices.forEach { choice -> Button({ play(choice) }, Modifier.fillMaxWidth().padding(top = 7.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xE6383651))) { Text(choice, color = Color.White) } }
                         if (!loading && lastTurnAvailable(state)) OutlinedButton(undo, Modifier.fillMaxWidth().padding(top = 7.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xE6383651), contentColor = Color.White)) { Text("撤销本回合") }
                    } else CompactDialoguePanel(state)
                    Row(Modifier.fillMaxWidth().imePadding().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(input, onInput, enabled = !loading, modifier = Modifier.weight(1f).background(Color(0xEE171827), RoundedCornerShape(12.dp)), label = { Text("自由输入") }, placeholder = { Text("你想做什么？") }, minLines = 2, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { play(input) }), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = FieldText, unfocusedTextColor = FieldText, focusedContainerColor = Color(0xEE171827), unfocusedContainerColor = Color(0xEE171827), disabledContainerColor = Color(0xAA171827), cursorColor = Accent, focusedBorderColor = FieldFocusedBorder, unfocusedBorderColor = FieldBorder, focusedLabelColor = FieldFocusedBorder, unfocusedLabelColor = Soft, focusedPlaceholderColor = FieldPlaceholder, unfocusedPlaceholderColor = FieldPlaceholder))
                        Button({ play(input) }, Modifier.padding(start = 8.dp), enabled = input.isNotBlank() && !loading, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink, disabledContainerColor = Color(0xFF5C536E), disabledContentColor = Color(0xFFCBC4D8))) { Text("发送") }
                    }
                }
            }
        }
    }
}

private fun lastTurnAvailable(state: GameState): Boolean = state.messages.isNotEmpty() && !state.isPlayingLines && !state.endingShown

@Composable
fun GalgameFullScreen(context: Context, sharedUtils: SharedUtils, onExit: () -> Unit = {}) {
    GalgameHostBridge.sharedUtils = sharedUtils
    LaunchedEffect(Unit) { GameStore.clearLegacyData(context) }
    var state by remember { mutableStateOf(GameStore.load(context)) }
    var assets by remember { mutableStateOf(GameStore.loadAssets(context)) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var screen by remember { mutableStateOf("editor") }
    var appPage by remember { mutableStateOf("home") }
    var playtestOrigin by remember { mutableStateOf("editor") }
    var library by remember { mutableStateOf(GameStore.loadLibrary(context)) }
    var showPlaytestGuide by remember { mutableStateOf(false) }
    var saves by remember { mutableStateOf(GameStore.loadSaves(context)) }
    var draftToRestore by remember { mutableStateOf(GameStore.loadEditorDraft(context)) }

    fun transitionFor(next: GameState, report: ProgressReport?): StoryTransition? = when {
        report?.completion != true || next.endingShown || next.endingContinued -> null
        !next.canCompleteCurrentChapter() -> null
        next.chapterConfig()?.isFinal != true && next.project.chapters.any { it.id > next.chapter } -> StoryTransition("chapter", "下一章", "${next.chapterConfig()?.title.orEmpty()} 已完成。")
        else -> StoryTransition("ending", "故事已完结", "${next.chapterConfig()?.title.orEmpty()} 的故事告一段落。")
    }

    var lastTurn by remember { mutableStateOf<GameState?>(null) }

    fun play(value: String) {
        if (loading || value.isBlank() || state.isPlayingLines || state.endingShown || state.pendingTransition != null) return
        val normalized = value.trim().take(500)
        input = ""; loading = true; error = null
        val before = state
        lastTurn = before
        val playerState = before.copy(messages = before.messages + StoryMessage("你", normalized), choices = emptyList())
        state = playerState; GameStore.save(context, playerState)
        CoroutineScope(Dispatchers.IO).launch {
            val result = DeepSeekClient.reply(before, normalized, assets)
            val response = result.getOrElse { FallbackStory.reply(before, normalized) }
            val prepared = response.copy(chapterTurns = before.chapterTurns + 1, messages = playerState.messages + response.messages.take(1))
            Handler(Looper.getMainLooper()).post {
                if (result.isFailure) error = "AI 暂时不可用，已使用本地保底剧情。"
                val progress = response.chapterProgress
                val acceptedCompletion = progress?.completion == true && prepared.canCompleteCurrentChapter()
                val shouldSummarize = prepared.chapterTurns % 8 == 0 || acceptedCompletion
                val memoryAddition = progress?.takeIf { shouldSummarize }?.let {
                    listOfNotNull(
                        it.chapterSummary.takeIf(String::isNotBlank)?.let { summary -> "【第${prepared.chapter}章摘要】\n$summary" },
                        it.keyFacts.takeIf { facts -> facts.isNotEmpty() }?.joinToString(separator = "\n- ", prefix = "【关键事实】\n- "),
                        it.relationshipNotes.takeIf { notes -> notes.isNotEmpty() }?.joinToString(separator = "\n- ", prefix = "【关系变化】\n- ")
                    ).joinToString("\n")
                }.orEmpty()
                val turnMemory = response.messages.takeLast(3).joinToString("；") { "${it.speaker}：${it.text}" }
                val mergedMemory = if (memoryAddition.isBlank()) listOf(prepared.storyMemory, "【当前章节进展】\n$turnMemory").filter(String::isNotBlank).joinToString("\n").takeLast(6_000) else listOf(prepared.storyMemory, memoryAddition, "【当前章节进展】\n$turnMemory").filter(String::isNotBlank).joinToString("\n").takeLast(6_000)
                val safeProgress = progress?.let {
                    if (it.completion && !acceptedCompletion) it.copy(
                        completion = false,
                        chapterStatus = "ready",
                        statusDescription = (it.statusDescription + " 剧情已接近完成，但仍未满足本章事件条件。" ).trim(),
                        unmetConditions = prepared.unmetChapterRequirements()
                    ) else it
                }
                if (progress?.completion == true && !acceptedCompletion) error = prepared.unmetChapterRequirements().joinToString(prefix = "剧情接近完成，但仍需：")
                val safeMemory = if (result.isFailure) before.storyMemory else mergedMemory
                val safe = prepared.copy(pendingTransition = transitionFor(prepared, safeProgress), chapterProgress = safeProgress, storyMemory = safeMemory)
                state = safe
                GameStore.save(context, state); loading = false
            }
        }
    }

    MaterialTheme {
        Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF363254), Ink, Color(0xFF10111B))))) {
                if (appPage == "home") {
                    HomePage(context, {
                        GameStore.clearEditorDraft(context)
                        state = GameState(projectId = "game_${System.currentTimeMillis()}"); assets = ProjectAssets(); input = ""; library = GameStore.loadLibrary(context); appPage = "workspace"; screen = "editor"
                    }, { appPage = "library" }, { library = GameStore.loadLibrary(context); appPage = "library" })
                    } else if (appPage == "library") {
                    LibraryPage(context, library, state, assets, { appPage = "home" }, { entry -> GameStore.clearEditorDraft(context); GameStore.restore(context, entry); appPage = "workspace"; screen = "game"; playtestOrigin = "library"; state = GameStore.load(context); assets = GameStore.loadAssets(context); lastTurn = null }, { entry -> GameStore.clearEditorDraft(context); GameStore.restore(context, entry); appPage = "workspace"; screen = "editor"; state = GameStore.load(context); assets = GameStore.loadAssets(context); lastTurn = null }, { entry -> GameStore.deletePublished(context, entry.id); if (state.projectId == entry.id) { state = GameState(); assets = ProjectAssets(); appPage = "home"; screen = "editor"; lastTurn = null } ; library = GameStore.loadLibrary(context) })
                } else if (screen == "editor") {
                    CreationWizard(context, state, assets, { appPage = "home" }, { next -> state = next; GameStore.save(context, next) }, { next -> assets = next; GameStore.saveAssets(context, next) }, { next, publishedAssets ->
                        if (next.project.characters.isEmpty() || next.project.playerCharacterId.isBlank() || next.project.chapters.none { it.title.isNotBlank() && it.contentDescription.isNotBlank() }) showPlaytestGuide = true
                        else {
                            val id = next.projectId.ifBlank { "game_${System.currentTimeMillis()}" }
                            val published = next.copy(projectId = id)
                            state = published
                              runCatching {
                                  publishedAssets.copy(
                                      sprites = publishedAssets.sprites.map { asset -> asset.copy(uri = GameStore.copyAssetToPrivate(context, asset.uri, "projects/$id")) },
                                      backgrounds = publishedAssets.backgrounds.map { asset -> asset.copy(uri = GameStore.copyAssetToPrivate(context, asset.uri, "projects/$id")) }
                                  )
                              }.onSuccess { privateAssets ->
                                  assets = privateAssets
                                  // Persist permanent asset URIs before the draft directory is removed.
                                  GameStore.saveAssets(context, privateAssets)
                                  GameStore.save(context, published)
                                  GameStore.publish(context, GameStore.snapshot(context, id, published, privateAssets))
                                  GameStore.clearEditorDraft(context)
                                   library = GameStore.loadLibrary(context); screen = "game"; playtestOrigin = "editor"; lastTurn = null
                              }.onFailure { error ->
                                  android.widget.Toast.makeText(context, "保存图片失败，游戏尚未发布：${error.message ?: "无法复制立绘或场景"}", android.widget.Toast.LENGTH_LONG).show()
                              }
                        }
                    })
                } else {
                    GamePlayScreen(state, assets, input, loading, error, { input = it.take(500) }, { play(it) }, { overlay = "history" }, { overlay = "saves" }, { overlay = "debug" }, { overlay = "more" }, { screen = "editor" }, { next -> state = next; GameStore.save(context, state) }, {
                         val orderedChapters = state.project.chapters.sortedBy { it.id }
                         val nextConfig = orderedChapters.firstOrNull { it.id > state.chapter }
                         val currentConfig = state.chapterConfig()
                          state = if (state.pendingTransition?.type == "ending" || currentConfig?.isFinal == true || nextConfig == null) {
                              state.copy(endingShown = true, pendingTransition = null)
                          } else {
                              val opening = nextConfig.openingDescription.takeIf(String::isNotBlank) ?: nextConfig.contentDescription
                              state.copy(
                                  chapter = nextConfig.id,
                                  chapterTurns = 0,
                                  goal = nextConfig.goal,
                                  messages = state.messages + StoryMessage("旁白", opening),
                                  lines = emptyList(),
                                  lineIndex = 0,
                                  linePage = 0,
                                  choices = emptyList(),
                                  scene = SceneState(backgroundId = nextConfig.allowedBackgroundIds.firstOrNull().orEmpty()),
                                  storyMemory = (state.storyMemory + "\n上一章已完成：${currentConfig?.title.orEmpty()}").takeLast(6_000),
                                  pendingTransition = null
                              )
                          }
                         GameStore.save(context, state); appPage = "workspace"
                      }, { lastTurn?.let { state = it; lastTurn = null; GameStore.save(context, state) } }, { state = createInitialGameState(state.projectId, state.project); input = ""; lastTurn = null; GameStore.save(context, state) }, { state = state.copy(endingShown = false, endingContinued = true); GameStore.save(context, state) })
                }
                 when (overlay) {
                      "more" -> MoreOverlay({ overlay = "history" }, { overlay = "saves" }, { overlay = "debug" }, if (playtestOrigin == "library") "返回游戏库" else "返回编辑器", { if (playtestOrigin == "library") appPage = "library" else screen = "editor"; overlay = null }, { state = createInitialGameState(state.projectId, state.project); input = ""; lastTurn = null; GameStore.save(context, state); overlay = null }, { if (playtestOrigin == "library") appPage = "library" else screen = "editor"; overlay = null })
                     "history" -> HistoryOverlay(state.messages) { overlay = null }
                     "saves" -> SaveOverlay(saves, { slot -> GameStore.saveSlot(context, slot, state, assets); saves = GameStore.loadSaves(context) }, { slot -> if (GameStore.loadSlot(context, slot, state.projectId)) { state = GameStore.load(context); assets = GameStore.loadAssets(context); lastTurn = null; input = ""; overlay = null } else { error = "该存档不属于当前 Galgame 项目，无法读取。" } }) { overlay = null }
                    "debug" -> DebugOverlay { overlay = null }
                     "project" -> ProjectEditor(state.project, { config ->
                         val chapterStillExists = config.chapters.any { it.id == state.chapter }
                         val playerChanged = config.playerCharacterId != state.project.playerCharacterId
                         val resetRequired = !chapterStillExists || playerChanged || config.chapters.map { it.id } != state.project.chapters.map { it.id }
                         state = if (resetRequired) createInitialGameState(state.projectId, config) else state.copy(project = config, goal = config.chapters.find { it.id == state.chapter }?.goal ?: state.goal)
                         lastTurn = null
                         input = ""
                         GameStore.save(context, state)
                         GameStore.publish(context, GameStore.snapshot(context, state.projectId, state, assets))
                        library = GameStore.loadLibrary(context)
                    }) { overlay = null }
                    "validate" -> ValidationOverlay(state.project, assets) { overlay = null }
                     "assets" -> AssetEditor(context, state.projectId.ifBlank { "draft" }, state.project.playerCharacterId.ifBlank { state.project.characters.firstOrNull()?.id.orEmpty() }, assets, { updated ->
                        val added = updated.sprites.filter { candidate -> assets.sprites.none { it.id == candidate.id } }
                        if (added.isNotEmpty()) {
                            val addedByCharacter = added.groupBy { it.characterId }
                            val updatedCharacters = state.project.characters.map { character ->
                                val refs = addedByCharacter[character.id].orEmpty().map { SpriteRef(it.id, it.name, it.usageCondition) }
                                if (refs.isEmpty()) character else character.copy(sprites = (character.sprites + refs).distinctBy { it.assetId })
                            }
                            state = state.copy(project = state.project.copy(characters = updatedCharacters))
                            GameStore.save(context, state)
                        }
                        val validSpriteIds = updated.sprites.map { it.id }.toSet()
                        state = state.copy(project = state.project.copy(characters = state.project.characters.map { character -> character.copy(sprites = character.sprites.filter { it.assetId in validSpriteIds }) }))
                        assets = updated
                        GameStore.save(context, state)
                        GameStore.saveAssets(context, updated)
                        if (state.projectId.isNotBlank()) {
                            GameStore.publish(context, GameStore.snapshot(context, state.projectId, state, updated))
                            library = GameStore.loadLibrary(context)
                        }
                    }) { overlay = null }
                }
                if (showPlaytestGuide) EmptyPlaytestGuide { showPlaytestGuide = false }
                draftToRestore?.let { draft ->
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text("发现未完成草稿") },
                        text = { Text("上次自动保存：${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(draft.updatedAt))}\n恢复后可继续编辑未发布的 Galgame 草稿。") },
                        confirmButton = { Button({ state = GameStore.stateFromJson(draft.stateJson); assets = GameStore.assetsFromJson(draft.assetsJson); GameStore.clearEditorDraft(context); appPage = "workspace"; screen = "editor"; draftToRestore = null }) { Text("继续编辑草稿") } },
                        dismissButton = { OutlinedButton({ GameStore.clearEditorDraft(context); draftToRestore = null }) { Text("放弃草稿") } }
                    )
                }
            }
        }
    }
    BackHandler(enabled = overlay != null || showPlaytestGuide || appPage != "home" || screen != "editor") {
        when {
            showPlaytestGuide -> showPlaytestGuide = false
            overlay != null -> overlay = null
             screen == "game" -> if (playtestOrigin == "library") { appPage = "library"; screen = "editor" } else screen = "editor"
            appPage == "library" -> appPage = "home"
            appPage == "workspace" -> appPage = "home"
            appPage == "home" -> onExit()
        }
    }
}

@Composable
private fun EmptyPlaytestGuide(close: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xE9141522)).padding(24.dp)) {
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(22.dp)) {
            Text("还没有可试玩内容", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("请先在编辑器中添加至少一个角色和一个填写完整的章节。", color = Soft, modifier = Modifier.padding(top = 10.dp))
            Button(close, Modifier.fillMaxWidth().padding(top = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("返回编辑器") }
        }
    }
}

@Composable
private fun CreationWizard(context: Context, state: GameState, assets: ProjectAssets, exitToHome: () -> Unit, saveState: (GameState) -> Unit, saveAssets: (ProjectAssets) -> Unit, finish: (GameState, ProjectAssets) -> Unit) {
    val savedDraft = remember(state.projectId) { GameStore.loadEditorDraft(context) }
    var step by remember { mutableIntStateOf(savedDraft?.step ?: 1) }
    var title by remember { mutableStateOf(state.project.title) }
    var description by remember { mutableStateOf(state.project.description) }
    var projectStyle by remember { mutableStateOf(state.project.style) }
    var projectRestrictions by remember { mutableStateOf(state.project.restrictions) }
    var characters by remember { mutableStateOf(state.project.characters) }
    var playerCharacterId by remember { mutableStateOf(state.project.playerCharacterId) }
    var draftProject by remember { mutableStateOf(state.project) }
    var chapters by remember { mutableStateOf(state.project.chapters) }
    var selectedCharacter by remember { mutableStateOf(savedDraft?.selectedCharacterId?.takeIf { id -> characters.any { it.id == id } } ?: characters.firstOrNull()?.id) }
    var selectedChapter by remember { mutableIntStateOf(savedDraft?.selectedChapterId?.takeIf { id -> chapters.any { it.id == id } } ?: chapters.firstOrNull()?.id ?: 1) }
    var characterName by remember { mutableStateOf(characters.firstOrNull()?.name.orEmpty()) }
    var characterInfo by remember { mutableStateOf(characters.firstOrNull()?.personality.orEmpty()) }
    var characterGoal by remember { mutableStateOf(characters.firstOrNull()?.goal.orEmpty()) }
    var characterSpeech by remember { mutableStateOf(characters.firstOrNull()?.speechStyle.orEmpty()) }
    var characterRelationship by remember { mutableStateOf(characters.firstOrNull()?.relationshipToPlayer.orEmpty()) }
    var characterSecret by remember { mutableStateOf(characters.firstOrNull()?.secret.orEmpty()) }
    var characterTaboos by remember { mutableStateOf(characters.firstOrNull()?.taboos.orEmpty()) }
    var characterSprites by remember { mutableStateOf(characters.firstOrNull()?.sprites.orEmpty()) }
    var selectedSpriteId by remember { mutableStateOf(savedDraft?.selectedSpriteId?.takeIf(String::isNotBlank)) }
    var spriteName by remember { mutableStateOf(savedDraft?.pendingSpriteName.orEmpty()) }
    var spriteCondition by remember { mutableStateOf(savedDraft?.pendingSpriteCondition.orEmpty()) }
    var chapterTitle by remember { mutableStateOf(chapters.firstOrNull()?.title.orEmpty()) }
    var chapterContent by remember { mutableStateOf(chapters.firstOrNull()?.contentDescription.orEmpty()) }
    var chapterEnding by remember { mutableStateOf(chapters.firstOrNull()?.completionHint.orEmpty()) }
    var chapterRequiredFlag by remember { mutableStateOf(chapters.firstOrNull()?.requiredFlag.orEmpty()) }
    var chapterOpening by remember { mutableStateOf(chapters.firstOrNull()?.openingDescription.orEmpty()) }
    var chapterAtmosphere by remember { mutableStateOf(chapters.firstOrNull()?.atmosphere.orEmpty()) }
    var chapterNotes by remember { mutableStateOf(chapters.firstOrNull()?.notes.orEmpty()) }
    var chapterSpoilers by remember { mutableStateOf(chapters.firstOrNull()?.spoilers.orEmpty()) }
    var backgrounds by remember { mutableStateOf(assets.backgrounds) }
    var workingAssets by remember { mutableStateOf(assets) }
    var selectedBackgrounds by remember { mutableStateOf(emptyList<String>()) }
    var selectedBackgroundId by remember { mutableStateOf(savedDraft?.selectedBackgroundId?.takeIf(String::isNotBlank)) }
    var backgroundName by remember { mutableStateOf(savedDraft?.pendingBackgroundName.orEmpty()) }
    var backgroundCondition by remember { mutableStateOf(savedDraft?.pendingBackgroundCondition.orEmpty()) }
    var guide by remember { mutableStateOf<String?>(null) }
    var pendingSpriteUri by remember { mutableStateOf(savedDraft?.pendingSpriteUri?.takeIf(String::isNotBlank)?.let(Uri::parse)) }
    var pendingBackgroundUri by remember { mutableStateOf(savedDraft?.pendingBackgroundUri?.takeIf(String::isNotBlank)?.let(Uri::parse)) }
    var previewScale by remember { mutableFloatStateOf(1f) }
    var previewOffsetX by remember { mutableFloatStateOf(0f) }
    var previewOffsetY by remember { mutableFloatStateOf(0f) }
    var previewSpriteId by remember { mutableStateOf<String?>(null) }
    var previewBackgroundId by remember { mutableStateOf<String?>(null) }
    var playerPicker by remember { mutableStateOf(false) }
    var draftStatus by remember { mutableStateOf("草稿已保存") }
    var showExitConfirm by remember { mutableStateOf(false) }
    val spritePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        if (characterName.isBlank()) { guide = "请先填写角色名字，再上传立绘。"; return@rememberLauncherForActivityResult }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val bitmap = runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if (bitmap == null || !bitmap.hasAlpha()) { guide = "这张 PNG 没有透明通道，请在 APP 外处理后重新选择。"; return@rememberLauncherForActivityResult }
        pendingSpriteUri = runCatching { Uri.parse(GameStore.copyAssetToPrivate(context, uri.toString(), "drafts/${state.projectId.ifBlank { "draft" }}")) }.getOrElse { guide = "无法保存立绘到草稿：${it.message}"; return@rememberLauncherForActivityResult }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        pendingBackgroundUri = runCatching { Uri.parse(GameStore.copyAssetToPrivate(context, uri.toString(), "drafts/${state.projectId.ifBlank { "draft" }}")) }.getOrElse { guide = "无法保存场景到草稿：${it.message}"; return@rememberLauncherForActivityResult }
    }
    fun saveDraftNow() {
        val currentCharacter = selectedCharacter?.let { id -> CharacterConfig(id, characterName, characterInfo, characterGoal, characterSpeech, characterRelationship, characterSecret, characterTaboos, characterSprites) }
        val draftCharacters = if (currentCharacter != null && currentCharacter.name.isNotBlank()) characters.filterNot { it.id == currentCharacter.id } + currentCharacter else characters
        val chapter = chapters.find { it.id == selectedChapter }
        val currentChapter = chapter?.copy(title = chapterTitle, contentDescription = chapterContent, openingDescription = chapterOpening, completionHint = chapterEnding, requiredFlag = chapterRequiredFlag, atmosphere = chapterAtmosphere, notes = chapterNotes, spoilers = chapterSpoilers, allowedBackgroundIds = selectedBackgrounds)
        val draftChapters = if (currentChapter != null) chapters.filterNot { it.id == currentChapter.id } + currentChapter else chapters
        val project = state.project.copy(title = title, description = description, style = projectStyle, restrictions = projectRestrictions, playerCharacterId = playerCharacterId, characters = draftCharacters, chapters = draftChapters)
        GameStore.saveEditorDraft(context, step, state.copy(project = project), workingAssets.copy(backgrounds = backgrounds), pendingSpriteUri?.toString().orEmpty(), pendingBackgroundUri?.toString().orEmpty(), spriteName, spriteCondition, backgroundName, backgroundCondition, selectedCharacter.orEmpty(), selectedChapter, selectedSpriteId.orEmpty(), selectedBackgroundId.orEmpty())
        draftStatus = "草稿已保存"
    }
    fun commitCurrentCharacter(): Boolean {
        val id = selectedCharacter ?: return false
        if (characterName.isBlank()) return false
        val value = CharacterConfig(id, characterName.trim(), characterInfo.trim(), characterGoal.trim(), characterSpeech.trim(), characterRelationship.trim(), characterSecret.trim(), characterTaboos.trim(), characterSprites)
        characters = characters.filterNot { it.id == id } + value
        return true
    }
    fun commitCurrentChapter() {
        val existing = chapters.find { it.id == selectedChapter } ?: ChapterConfig(selectedChapter)
        val value = existing.copy(title = chapterTitle.trim(), contentDescription = chapterContent.trim(), openingDescription = chapterOpening.trim(), completionHint = chapterEnding.trim(), requiredFlag = chapterRequiredFlag.trim(), atmosphere = chapterAtmosphere.trim(), notes = chapterNotes.trim(), spoilers = chapterSpoilers.trim(), allowedBackgroundIds = selectedBackgrounds)
        chapters = chapters.filterNot { it.id == selectedChapter } + value
    }
    LaunchedEffect(selectedCharacter) {
        characters.firstOrNull { it.id == selectedCharacter }?.let { character ->
            characterName = character.name; characterInfo = character.personality; characterGoal = character.goal
            characterSpeech = character.speechStyle; characterRelationship = character.relationshipToPlayer
            characterSecret = character.secret; characterTaboos = character.taboos; characterSprites = character.sprites
        }
    }
    LaunchedEffect(selectedChapter) {
        chapters.firstOrNull { it.id == selectedChapter }?.let { chapter ->
            chapterTitle = chapter.title; chapterContent = chapter.contentDescription; chapterEnding = chapter.completionHint
            chapterRequiredFlag = chapter.requiredFlag; chapterOpening = chapter.openingDescription
            chapterAtmosphere = chapter.atmosphere; chapterNotes = chapter.notes; chapterSpoilers = chapter.spoilers
            selectedBackgrounds = chapter.allowedBackgroundIds
        }
    }
    fun requestExit() { showExitConfirm = true }
    BackHandler {
        if (step > 1) { saveDraftNow(); step-- }
        else requestExit()
    }
    LaunchedEffect(title, description, projectStyle, projectRestrictions, characters, chapters, playerCharacterId, characterName, characterInfo, characterGoal, characterSpeech, characterRelationship, characterSecret, characterTaboos, characterSprites, chapterTitle, chapterContent, chapterEnding, chapterOpening, chapterAtmosphere, chapterNotes, chapterSpoilers, backgrounds, workingAssets, selectedBackgrounds, pendingSpriteUri, pendingBackgroundUri, spriteName, spriteCondition, backgroundName, backgroundCondition, step) {
        draftStatus = "正在保存草稿…"
        delay(800)
        saveDraftNow()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("返回", color = Accent, modifier = Modifier.clickable { requestExit() }.padding(end = 16.dp, top = 8.dp, bottom = 8.dp))
            Column(Modifier.weight(1f)) {
                Text("创建新游戏", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("步骤 $step / 5 · $draftStatus", color = Accent, modifier = Modifier.padding(top = 5.dp, bottom = 16.dp))
            }
        }
        when (step) {
            1 -> {
                Text("1. 游戏剧情", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("先写下你的故事、世界观和想要的整体方向。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                EditorTextField(title, { title = it }, "游戏名称（必填）", Modifier.fillMaxWidth(), true, placeholder = "例如：雨夜的岛")
                EditorTextField(description, { description = it }, "游戏简介（必填）", Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 8, placeholder = "例如：玩家来到一座与外界隔绝的小岛，逐渐发现角色们隐藏的秘密。", suggestedLength = 2000)
                EditorTextField(projectStyle, { projectStyle = it }, "创作风格（非必填）", Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 3, placeholder = "例如：节奏偏慢，重点描写日常互动和角色关系。", suggestedLength = 300)
                EditorTextField(projectRestrictions, { projectRestrictions = it }, "创作禁区（非必填）", Modifier.fillMaxWidth().padding(top = 10.dp), minLines = 3, placeholder = "例如：不要角色突然死亡，不要血腥描写。", suggestedLength = 500)
            }
            2 -> {
                Text("2. 创建角色", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("添加角色并填写人设。每个角色至少一张立绘；立绘说明不仅可以写情绪，也可以写受伤、地点、行为、装扮和剧情条件。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Text(if (selectedCharacter == null) "正在新增角色" else "正在编辑角色：${characterName.ifBlank { "未命名角色" }}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                characters.forEach { character ->
                    val selected = character.id == selectedCharacter
                    val isPlayer = character.id == playerCharacterId
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { commitCurrentCharacter(); selectedCharacter = character.id; characterName = character.name; characterInfo = character.personality; characterGoal = character.goal; characterSpeech = character.speechStyle; characterRelationship = character.relationshipToPlayer; characterSecret = character.secret; characterTaboos = character.taboos; characterSprites = character.sprites; selectedSpriteId = null; pendingSpriteUri = null; spriteName = ""; spriteCondition = "" }.background(if (selected) Color(0x88504B75) else Panel, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val thumbnail = character.sprites.firstOrNull()?.let { ref -> workingAssets.sprites.find { it.id == ref.assetId } }
                        thumbnail?.let { LoadBitmap(it.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.size(52.dp), contentScale = ContentScale.Fit) } } ?: Box(Modifier.size(52.dp).background(Color(0x66504B75), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text("?", color = Soft) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(character.name.ifBlank { "未命名角色" }, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(if (isPlayer) "玩家角色 · 自动加入每一章" else "普通角色 · ${character.sprites.size} 张立绘", color = if (isPlayer) Accent else Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                        }
                        if (isPlayer) Text("玩家", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.background(Accent, RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
                        else Text("设为玩家", color = Accent, fontSize = 12.sp, modifier = Modifier.clickable {
                             val oldPlayerId = playerCharacterId
                             playerCharacterId = character.id
                             chapters = chapters.map { chapter -> chapter.copy(allowedCharacterIds = chapter.allowedCharacterIds.filterNot { it == oldPlayerId }.plus(character.id).distinct()) }
                            guide = "已将 ${character.name} 设为玩家角色，并自动加入所有章节。"
                        }.padding(8.dp))
                    }
                }
                OutlinedButton({ val id = "character_${characters.size + 1}"; selectedCharacter = id; characterName = ""; characterInfo = ""; characterGoal = ""; characterSpeech = ""; characterRelationship = ""; characterSecret = ""; characterTaboos = ""; characterSprites = emptyList(); selectedSpriteId = null; pendingSpriteUri = null; guide = "正在创建新角色，请填写信息并添加至少一张立绘。" }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("新增角色") }
                EditorTextField(characterName, { characterName = it }, "角色名称（必填）", Modifier.fillMaxWidth().padding(top = 16.dp), true, placeholder = "例如：绫")
                EditorTextField(characterInfo, { characterInfo = it }, "性格与背景（必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 4, placeholder = "例如：外表冷静，实际上很在意他人评价。", suggestedLength = 1500)
                EditorTextField(characterGoal, { characterGoal = it }, "角色目标（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：查清异常信号来源。", suggestedLength = 300)
                EditorTextField(characterSpeech, { characterSpeech = it }, "说话方式（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：语气克制，紧张时会转移话题。", suggestedLength = 300)
                EditorTextField(characterRelationship, { characterRelationship = it }, "与玩家的关系（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：刚认识，表面客气但有所防备。", suggestedLength = 500)
                EditorTextField(characterSecret, { characterSecret = it }, "角色秘密（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：知道信号来源，但暂时不愿透露。", suggestedLength = 500)
                EditorTextField(characterTaboos, { characterTaboos = it }, "角色禁区（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：不会主动提及童年经历。", suggestedLength = 300)
                if (characterSprites.isNotEmpty()) {
                    Text("当前角色的立绘", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    characterSprites.forEach { sprite ->
                        val asset = workingAssets.sprites.find { it.id == sprite.assetId }
                        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            asset?.let { LoadBitmap(it.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.size(56.dp), contentScale = ContentScale.Fit) } }
                            Text(sprite.name, color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            Text("编辑", color = Accent, modifier = Modifier.clickable { selectedSpriteId = sprite.assetId; spriteName = sprite.name; spriteCondition = sprite.usageCondition; pendingSpriteUri = asset?.uri?.let { Uri.parse(it) } }.padding(6.dp))
                            Text("删除", color = Accent, modifier = Modifier.clickable {
                                val nextSprites = characterSprites.filterNot { it.assetId == sprite.assetId }
                                characterSprites = nextSprites
                                workingAssets = workingAssets.copy(sprites = workingAssets.sprites.filterNot { it.id == sprite.assetId })
                                guide = "立绘已从角色草稿移除，请点击保存角色提交修改。"
                            }.padding(6.dp))
                        }
                    }
                }
                Button({ spritePicker.launch(arrayOf("image/png")) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("上传透明 PNG 立绘") }
                pendingSpriteUri?.let { uri ->
                    Text("待保存立绘预览", color = Accent, modifier = Modifier.padding(top = 10.dp))
                    LoadBitmap(uri.toString())?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxWidth().height(180.dp).padding(top = 8.dp), contentScale = ContentScale.Fit) }
                    EditorTextField(spriteName, { spriteName = it }, "立绘名称（必填）", Modifier.fillMaxWidth().padding(top = 8.dp), true, placeholder = "例如：受伤后的卧室便装")
                    EditorTextField(spriteCondition, { spriteCondition = it }, "使用条件（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3, placeholder = "例如：受伤后在医院接受治疗时使用。", suggestedLength = 100)
                    Button({
                        val assetId = selectedSpriteId ?: "sprite_${System.currentTimeMillis()}"
                        val characterId = selectedCharacter ?: "character_${characters.size + 1}"
                        val bitmap = runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return@Button
                         val asset = SpriteAsset(assetId, characterId, spriteName.ifBlank { "立绘" }, uri.toString(), emptyList(), spriteCondition, autoSpriteScale(bitmap))
                        val nextAssets = workingAssets.copy(sprites = (workingAssets.sprites.filterNot { it.id == assetId } + asset))
                        val nextSprites = characterSprites.filterNot { it.assetId == assetId } + SpriteRef(assetId, asset.name, spriteCondition)
                        workingAssets = nextAssets
                        characterSprites = nextSprites
                        pendingSpriteUri = null; selectedSpriteId = null; spriteName = ""; spriteCondition = ""; guide = "立绘已加入角色草稿，请点击保存角色提交。"
                    }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (selectedSpriteId == null) "确认添加立绘" else "确认立绘修改") }
                }
                Button({
                    val id = selectedCharacter ?: "character_${characters.size + 1}"
                    val value = CharacterConfig(id, characterName.trim(), characterInfo.trim(), characterGoal.trim(), characterSpeech.trim(), characterRelationship.trim(), characterSecret.trim(), characterTaboos.trim(), characterSprites)
                    if (value.name.isBlank()) { guide = "请填写角色名称。"; return@Button }
                    if (value.personality.isBlank()) { guide = "请填写角色设定。"; return@Button }
                    if (value.sprites.isEmpty()) { guide = "角色“${value.name}”至少需要一张立绘后才能保存。"; return@Button }
                    val draftAssetIds = value.sprites.map { it.assetId }.toSet()
                    val nextAssets = workingAssets.copy(sprites = workingAssets.sprites.filterNot { it.characterId == id && it.id !in draftAssetIds }.map { asset -> if (asset.id in draftAssetIds) asset.copy(characterId = id) else asset })
                    workingAssets = nextAssets
                    saveAssets(nextAssets)
                    characters = characters.filterNot { it.id == id } + value
                    selectedCharacter = id
                    guide = "角色已保存。"
                }, Modifier.fillMaxWidth().padding(top = 18.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("保存角色", fontWeight = FontWeight.Bold) }
            }
            3 -> {
                Text("3. 创建场景", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("每张背景图都可以代表同一地点的不同时段或氛围，例如教室白天、教室傍晚。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                Text("已创建场景", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                backgrounds.forEach { background ->
                    val selected = background.id == selectedBackgroundId
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selectedBackgroundId = background.id
                            backgroundName = background.name
                            backgroundCondition = background.usageCondition
                            pendingBackgroundUri = Uri.parse(background.uri)
                        }.padding(vertical = 4.dp).background(if (selected) Color(0x88504B75) else Panel, RoundedCornerShape(14.dp)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LoadBitmap(background.uri)?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp, 96.dp), contentScale = ContentScale.Crop) } ?: Box(Modifier.size(64.dp, 96.dp).background(Color(0x66504B75), RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(background.name.ifBlank { "未命名场景" }, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(background.usageCondition.ifBlank { "未填写展示说明" }, color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            if (selected) Text("当前编辑中", color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        Text("›", color = Accent, fontSize = 24.sp)
                    }
                }
                EditorTextField(backgroundName, { backgroundName = it }, "背景名称（必填）", Modifier.fillMaxWidth(), true, placeholder = "例如：雨天车站")
                EditorTextField(backgroundCondition, { backgroundCondition = it }, "背景使用条件（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3, placeholder = "例如：放学后的雨天车站，等待回家时使用。", suggestedLength = 100)
                Button({ backgroundPicker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("上传场景背景图") }
                pendingBackgroundUri?.let { uri ->
                    Text("待保存场景预览", color = Accent, modifier = Modifier.padding(top = 10.dp))
                    LoadBitmap(uri.toString())?.let { bitmap -> Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).padding(top = 8.dp).background(Color(0xFF303044))) { Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop); Text("9:16 试玩裁剪预览", color = Color.White, modifier = Modifier.padding(8.dp)) } }
                    Button({
                        val id = selectedBackgroundId ?: "background_${System.currentTimeMillis()}"
                         val asset = BackgroundAsset(id, backgroundName.ifBlank { "场景" }, uri.toString(), emptyList(), backgroundCondition)
                        backgrounds = (backgrounds.filterNot { it.id == id } + asset).sortedBy { it.id }
                        val nextAssets = workingAssets.copy(backgrounds = backgrounds)
                        workingAssets = nextAssets; saveAssets(nextAssets); pendingBackgroundUri = null; selectedBackgroundId = null; backgroundName = ""; backgroundCondition = ""; guide = "场景已保存。"
                    }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(if (selectedBackgroundId == null) "保存场景" else "保存场景修改") }
                    if (selectedBackgroundId != null) OutlinedButton({
                        val id = selectedBackgroundId.orEmpty()
                        backgrounds = backgrounds.filterNot { it.id == id }
                        val nextAssets = workingAssets.copy(backgrounds = backgrounds)
                        workingAssets = nextAssets; saveAssets(nextAssets); pendingBackgroundUri = null; selectedBackgroundId = null; backgroundName = ""; backgroundCondition = ""; guide = "场景已删除。"
                    }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("删除当前场景") }
                }
            }
            4 -> {
                Text("4. 创建章节", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("每一章只需要写内容、选择出场角色和描述结束条件。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                chapters.forEach { chapter -> Text("第${chapter.id}章  ${chapter.title}", color = if (chapter.id == selectedChapter) Accent else Soft, modifier = Modifier.clickable { commitCurrentChapter(); selectedChapter = chapter.id; chapterTitle = chapter.title; chapterContent = chapter.contentDescription; chapterOpening = chapter.openingDescription; chapterEnding = chapter.completionHint; chapterRequiredFlag = chapter.requiredFlag; chapterAtmosphere = chapter.atmosphere; chapterNotes = chapter.notes; chapterSpoilers = chapter.spoilers; selectedBackgrounds = chapter.allowedBackgroundIds }.padding(vertical = 6.dp)) }
                EditorTextField(chapterTitle, { chapterTitle = it }, "章节名称（必填）", Modifier.fillMaxWidth(), true, placeholder = "例如：雨中的约定")
                EditorTextField(chapterContent, { chapterContent = it }, "章节内容（必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 7, placeholder = "例如：玩家陪绫前往医院处理伤势，途中发现她隐瞒了异常信号的事。", suggestedLength = 2000)
                EditorTextField(chapterOpening, { chapterOpening = it }, "开场描述（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 4, placeholder = "例如：放学后的天空下起了雨，绫站在校门口。", suggestedLength = 1000)
                Text("本章出场角色", color = Accent, modifier = Modifier.padding(top = 12.dp))
                characters.forEach { character ->
                    val isPlayer = character.id == playerCharacterId
                    Row(Modifier.fillMaxWidth().clickable {
                        if (isPlayer) return@clickable
                        val current = chapters.find { it.id == selectedChapter }?.allowedCharacterIds.orEmpty()
                        val next = if (character.id in current) current - character.id else current + character.id
                        val existing = chapters.find { it.id == selectedChapter } ?: ChapterConfig(selectedChapter)
                        chapters = chapters.filterNot { it.id == selectedChapter } + existing.copy(allowedCharacterIds = (next + playerCharacterId).distinct())
                    }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        val checked = isPlayer || character.id in chapters.find { it.id == selectedChapter }?.allowedCharacterIds.orEmpty()
                        Checkbox(checked, null, enabled = !isPlayer)
                        Text(character.name, color = Color.White)
                        if (isPlayer) Text("  用户扮演角色 · 自动加入", color = Accent, fontSize = 12.sp)
                    }
                }
                Text("本章可用场景", color = Accent, modifier = Modifier.padding(top = 12.dp))
                backgrounds.forEach { background ->
                    Row(Modifier.fillMaxWidth().clickable { selectedBackgrounds = if (background.id in selectedBackgrounds) selectedBackgrounds - background.id else selectedBackgrounds + background.id }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(background.id in selectedBackgrounds, null); Text(background.name, color = Color.White); Text("  ${background.usageCondition}", color = Soft, fontSize = 12.sp)
                    }
                }
                EditorTextField(chapterEnding, { chapterEnding = it }, "本章完成目标（建议填写）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3, placeholder = "例如：当玩家和绫建立初步信任，并决定一起调查异常信号时，本章完成。", suggestedLength = 500)
                if (state.project.events.isNotEmpty()) EditorTextField(chapterRequiredFlag, { chapterRequiredFlag = it }, "完成本章必须发生的事件 ID（可选）", Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true, placeholder = "请填写已有事件 ID")
                EditorTextField(chapterAtmosphere, { chapterAtmosphere = it }, "章节氛围（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：安静、克制，带一点暧昧和不安。", suggestedLength = 300)
                EditorTextField(chapterNotes, { chapterNotes = it }, "本章注意事项（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：不要直接揭露信号来源，只能给出暗示。", suggestedLength = 500)
                EditorTextField(chapterSpoilers, { chapterSpoilers = it }, "本章不可提前揭露内容（非必填）", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 2, placeholder = "例如：不要说出绫已经知道信号来源。", suggestedLength = 500)
                Button({
                    val allowedIds = (chapters.find { it.id == selectedChapter }?.allowedCharacterIds.orEmpty() + playerCharacterId).filter(String::isNotBlank).distinct()
                    val value = ChapterConfig(selectedChapter, chapterTitle.trim(), chapterContent.trim(), allowedIds, chapterEnding.trim(), requiredFlag = chapterRequiredFlag.trim(), contentDescription = chapterContent.trim(), allowedBackgroundIds = selectedBackgrounds, openingDescription = chapterOpening.trim(), atmosphere = chapterAtmosphere.trim(), notes = chapterNotes.trim(), spoilers = chapterSpoilers.trim())
                    val finalChapters = (chapters.filterNot { it.id == selectedChapter } + value).sortedBy { it.id }
                    chapters = finalChapters
                    val project = ProjectConfig(title.trim(), description.trim(), projectStyle.trim(), projectRestrictions.trim(), playerCharacterId, characters, finalChapters, state.project.variables, state.project.items, state.project.events)
                    val issues = projectIssues(project, workingAssets)
                    if (issues.isNotEmpty()) { guide = issues.first(); return@Button }
                    val first = project.chapters.minByOrNull { it.id }
                    val opening = first?.openingDescription?.takeIf { it.isNotBlank() } ?: first?.contentDescription?.takeIf { it.isNotBlank() } ?: "故事开始了。"
                    draftProject = project
                    saveState(state.copy(project = project))
                    previewScale = workingAssets.globalSpriteScale
                    previewOffsetX = workingAssets.globalSpriteOffsetX
                    previewOffsetY = workingAssets.globalSpriteOffsetY
                    step = 5
                    guide = "项目已保存，请完成统一立绘适配。"
                }, Modifier.fillMaxWidth().padding(top = 14.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("保存项目并继续适配", fontWeight = FontWeight.Bold) }
                 OutlinedButton({ val id = (chapters.maxOfOrNull { it.id } ?: 0) + 1; chapters = chapters + ChapterConfig(id); selectedChapter = id; chapterTitle = ""; chapterContent = ""; chapterOpening = ""; chapterEnding = ""; chapterRequiredFlag = ""; chapterAtmosphere = ""; chapterNotes = ""; chapterSpoilers = ""; selectedBackgrounds = emptyList() }, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("新增章节") }
            }
            5 -> {
                val availableSprites = draftProject.characters.flatMap { it.sprites }.mapNotNull { ref -> workingAssets.sprites.find { it.id == ref.assetId } }
                val exampleSprite = availableSprites.find { it.id == previewSpriteId } ?: availableSprites.firstOrNull()
                val exampleBackground = workingAssets.backgrounds.find { it.id == previewBackgroundId } ?: workingAssets.backgrounds.firstOrNull()
                Text("5. 统一立绘适配", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("全局参数应用到所有立绘；切换立绘后可单独微调该图，修正半身图或透明边距。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                if (exampleSprite == null) {
                    Text("还没有可预览的立绘，请返回角色步骤添加至少一张立绘。", color = Accent)
                } else {
                    Text("预览场景", color = Accent, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        workingAssets.backgrounds.forEach { background ->
                            Column(Modifier.width(76.dp).clickable { previewBackgroundId = background.id }.background(if (background.id == exampleBackground?.id) Color(0x88504B75) else Panel, RoundedCornerShape(10.dp)).padding(6.dp)) {
                                LoadBitmap(background.uri)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(70.dp), contentScale = ContentScale.Crop) }
                                Text(background.name, color = if (background.id == exampleBackground?.id) Accent else Soft, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Text("预览立绘", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableSprites.forEach { sprite ->
                            Column(Modifier.width(76.dp).clickable { previewSpriteId = sprite.id }.background(if (sprite.id == exampleSprite.id) Color(0x88504B75) else Panel, RoundedCornerShape(10.dp)).padding(6.dp)) {
                                LoadBitmap(sprite.uri)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(70.dp), contentScale = ContentScale.Fit) }
                                Text(sprite.name, color = if (sprite.id == exampleSprite.id) Accent else Soft, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().aspectRatio(9f / 16f).clipToBounds().background(Color(0xFF303044)), contentAlignment = Alignment.BottomCenter) {
                        exampleBackground?.let { background -> LoadBitmap(background.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                        LoadBitmap(exampleSprite.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxHeight(.9f).graphicsLayer(scaleX = exampleSprite.scale * previewScale, scaleY = exampleSprite.scale * previewScale, translationX = (exampleSprite.offsetX + previewOffsetX) * 180f, translationY = (exampleSprite.offsetY + previewOffsetY) * 180f), contentScale = ContentScale.Fit) }
                        Text("示例：${exampleSprite.name}", color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
                    }
                    Text("全局大小 ${"%.2f".format(previewScale)}", color = Accent, modifier = Modifier.padding(top = 14.dp))
                    Slider(previewScale, { previewScale = it }, valueRange = .35f..2.5f)
                    Text("全局左右位置", color = Accent)
                    Slider(previewOffsetX, { previewOffsetX = it }, valueRange = -1f..1f)
                    Text("全局上下位置", color = Accent)
                    Slider(previewOffsetY, { previewOffsetY = it }, valueRange = -2f..2f)
                    Text("当前立绘微调：${exampleSprite.name}", color = Accent, modifier = Modifier.padding(top = 12.dp))
                    Text("当前大小 ${"%.2f".format(exampleSprite.scale)}", color = Soft)
                    Slider(exampleSprite.scale, { value -> workingAssets = workingAssets.copy(sprites = workingAssets.sprites.map { if (it.id == exampleSprite.id) it.copy(scale = value) else it }) }, valueRange = .05f..6f)
                    Text("当前左右位置", color = Soft)
                    Slider(exampleSprite.offsetX, { value -> workingAssets = workingAssets.copy(sprites = workingAssets.sprites.map { if (it.id == exampleSprite.id) it.copy(offsetX = value) else it }) }, valueRange = -4f..4f)
                    Text("当前上下位置", color = Soft)
                    Slider(exampleSprite.offsetY, { value -> workingAssets = workingAssets.copy(sprites = workingAssets.sprites.map { if (it.id == exampleSprite.id) it.copy(offsetY = value) else it }) }, valueRange = -4f..4f)
                    OutlinedButton({
                        val bitmap = runCatching { context.contentResolver.openInputStream(Uri.parse(exampleSprite.uri)).use { BitmapFactory.decodeStream(it) } }.getOrNull()
                        bitmap?.let { auto -> workingAssets = workingAssets.copy(sprites = workingAssets.sprites.map { if (it.id == exampleSprite.id) it.copy(scale = autoSpriteScale(auto), offsetX = 0f, offsetY = 0f) else it }) }
                    }, Modifier.fillMaxWidth()) { Text("恢复当前立绘自动适配") }
                    Button({
                         val adjustedAssets = workingAssets.copy(globalSpriteScale = previewScale, globalSpriteOffsetX = previewOffsetX, globalSpriteOffsetY = previewOffsetY)
                         workingAssets = adjustedAssets; saveAssets(adjustedAssets)
                          val nextState = createInitialGameState(state.projectId.ifBlank { draftProject.title.ifBlank { "game" } }, draftProject)
                         saveState(nextState)
                         finish(nextState, adjustedAssets)
                    }, Modifier.fillMaxWidth().padding(top = 10.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("确认适配并进入试玩", fontWeight = FontWeight.Bold) }
                }
            }
        }
        guide?.let { message -> Text(message, color = Accent, modifier = Modifier.padding(top = 10.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
             if (step > 1) OutlinedButton({ saveDraftNow(); step-- }) { Text("上一步") } else Spacer(Modifier.width(1.dp))
            if (step < 5) Button({
                when {
                    step == 1 -> step++
                    step == 2 -> {
                        if (!commitCurrentCharacter()) guide = "请先完整填写当前角色，并至少添加一张立绘。"
                        else if (playerCharacterId.isBlank() && characters.isNotEmpty()) playerPicker = true
                        else if (characters.isNotEmpty()) step++
                    }
                    step == 3 -> step++
                }
            }) { Text("下一步") }
        }
         if (playerPicker) AlertDialog(onDismissRequest = { playerPicker = false }, title = { Text("选择玩家角色") }, text = { Column { Text("请选择你要扮演的角色。该角色会自动加入每一章，AI 不会替他/她发言。"); characters.forEach { character -> Text(character.name.ifBlank { "未命名角色" }, color = Accent, modifier = Modifier.fillMaxWidth().clickable { val oldPlayerId = playerCharacterId; playerCharacterId = character.id; chapters = chapters.map { chapter -> chapter.copy(allowedCharacterIds = chapter.allowedCharacterIds.filterNot { it == oldPlayerId }.plus(character.id).distinct()) }; playerPicker = false; step++ }.padding(vertical = 10.dp)) } } }, confirmButton = {}, dismissButton = { OutlinedButton({ playerPicker = false }) { Text("取消") } })
        if (showExitConfirm) AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出创建新游戏？") },
            text = { Text("当前内容会自动保存为本地草稿。下次进入 Galgame 时可以继续编辑。") },
            confirmButton = {
                Button({ showExitConfirm = false; saveDraftNow(); exitToHome() }) { Text("保存并退出") }
            },
            dismissButton = {
                Row {
                    TextButton({ showExitConfirm = false }) { Text("继续编辑") }
                    TextButton({ showExitConfirm = false; GameStore.clearEditorDraft(context); exitToHome() }) { Text("放弃草稿", color = Accent) }
                }
            }
        )
    }
}

@Composable
private fun HomePage(context: Context, create: () -> Unit, openLibrary: () -> Unit, imported: (LibraryEntry) -> Unit) {
    var packageMessage by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        importing = true
        Thread {
            RdgPackage.import(context, uri).onSuccess { entry -> Handler(Looper.getMainLooper()).post {
                imported(entry)
                packageMessage = "已导入《${entry.title}》，已加入游戏库。"
                importing = false
            } }.onFailure { error -> Handler(Looper.getMainLooper()).post {
                packageMessage = "导入失败：${error.message ?: "无法读取游戏包"}"
                importing = false
            } }
        }.start()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("AI Galgame", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("创建、编辑并试玩属于你的互动视觉小说。", color = Soft, modifier = Modifier.padding(top = 8.dp, bottom = 28.dp))
        Button(create, Modifier.fillMaxWidth().height(58.dp), enabled = !importing, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("创建新游戏", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(openLibrary, Modifier.fillMaxWidth().height(54.dp).padding(top = 10.dp), enabled = !importing) { Text("游戏库", fontSize = 16.sp) }
        OutlinedButton({ importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = !importing) { if (importing) { CircularProgressIndicator(Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text("正在导入并校验……") } else Text("导入游戏包 (.rdg)") }
        Text("模型配置请前往主应用设置", color = Soft, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp))
        packageMessage?.let { Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun LibraryPage(context: Context, games: List<LibraryEntry>, currentState: GameState, currentAssets: ProjectAssets, back: () -> Unit, open: (LibraryEntry) -> Unit, edit: (LibraryEntry) -> Unit, delete: (LibraryEntry) -> Unit) {
    var selected by remember { mutableStateOf<LibraryEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<LibraryEntry?>(null) }
    var exportFormat by remember { mutableStateOf<LibraryEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun shareFile(file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "分享游戏文件"))
    }
    val rdgExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val game = exportFormat ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val exportState = runCatching { GameStore.stateFromJson(game.stateJson) }.getOrDefault(currentState)
            val exportAssets = runCatching { GameStore.assetsFromJson(game.assetsJson) }.getOrDefault(currentAssets)
            message = RdgPackage.exportEntry(context, game, exportState, exportAssets, uri).fold({ "已导出 RDG 游戏包。" }, { "导出失败：${it.message}" })
        }
        exportFormat = null
    }
    val htmlExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val game = exportFormat ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            val exportState = runCatching { GameStore.stateFromJson(game.stateJson) }.getOrDefault(currentState)
            val exportAssets = runCatching { GameStore.assetsFromJson(game.assetsJson) }.getOrDefault(currentAssets)
            message = RdgPackage.exportEntryHtml(context, game, exportState, exportAssets, uri).fold({ "已导出 H5 游玩版。" }, { "导出失败：${it.message}" })
        }
        exportFormat = null
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("游戏库", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("返回", color = Accent, modifier = Modifier.clickable(onClick = back).padding(8.dp)) }
        if (games.isEmpty()) Text("还没有完成的游戏。创建并保存一个游戏后，它会出现在这里。", color = Soft, modifier = Modifier.padding(top = 24.dp))
        message?.let { Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
        games.forEach { game ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text(game.title.ifBlank { "未命名游戏" }, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(game.description.ifBlank { "暂无简介" }, color = Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Button({ open(game) }, Modifier.padding(top = 10.dp)) { Text("试玩") }
                OutlinedButton({ selected = game }, Modifier.padding(top = 8.dp)) { Text("功能") }
            }
        }
        selected?.let { game -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(game.title.ifBlank { "未命名游戏" }) }, text = { Text("选择操作") }, confirmButton = { Button({ edit(game); selected = null }) { Text("编辑") } }, dismissButton = { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ exportFormat = game; selected = null }) { Text("导出") }; OutlinedButton({ confirmDelete = game; selected = null }) { Text("删除") } } }) }
        confirmDelete?.let { game -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("删除游戏？") }, text = { Text("确定删除《${game.title.ifBlank { "未命名游戏" }}》吗？这会删除游戏库条目和本地游戏资源，操作无法撤销。") }, confirmButton = { Button({ delete(game); confirmDelete = null }) { Text("确认删除") } }, dismissButton = { OutlinedButton({ confirmDelete = null }) { Text("取消") } }) }
        exportFormat?.let { game -> AlertDialog(onDismissRequest = { exportFormat = null }, title = { Text("导出 ${game.title.ifBlank { "游戏" }}") }, text = { Text("选择导出格式：RDG 可继续编辑；H5 可直接分享游玩。") }, confirmButton = { Button({ rdgExporter.launch("${game.title.ifBlank { "罗德岛嘎啦game" }}.rdg") }) { Text("游戏文件 (.rdg)") } }, dismissButton = { OutlinedButton({ htmlExporter.launch("${game.title.ifBlank { "罗德岛嘎啦game" }}.html") }) { Text("H5 游玩版 (.html)") } }) }
    }
}

@Composable
private fun Header(state: GameState, history: () -> Unit, assets: () -> Unit, project: () -> Unit, author: () -> Unit, validate: () -> Unit, progress: () -> Unit, reset: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(state.project.title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold); Text("第${state.chapter}章  ${state.chapterConfig()?.title?.ifBlank { "未命名章节" } ?: "未命名章节"}", color = Soft, fontSize = 13.sp) }
        Text("历史", color = Accent, modifier = Modifier.clickable(onClick = history).padding(6.dp))
        Text("资源", color = Accent, modifier = Modifier.clickable(onClick = assets).padding(6.dp))
        Text("项目", color = Accent, modifier = Modifier.clickable(onClick = project).padding(6.dp))
        Text("检查", color = Accent, modifier = Modifier.clickable(onClick = validate).padding(6.dp))
        Text("进度", color = Accent, modifier = Modifier.clickable(onClick = progress).padding(6.dp))
        Text("重置", color = Accent, modifier = Modifier.clickable(onClick = reset).padding(6.dp))
    }
    Row(Modifier.padding(top = 8.dp)) { state.project.variables.take(2).forEach { variable -> Chip("${variable.name} ${state.variables[variable.id] ?: variable.initial}"); Spacer(Modifier.width(8.dp)) }; Chip(if (state.hasMap) "已获得地图" else "尚未获得地图") }
    Text("当前剧情目标：${state.goal}", color = Color(0xFFF1D6DE), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable private fun Chip(text: String) = Text(text, color = Soft, fontSize = 12.sp, modifier = Modifier.background(Color(0x66504B75), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 5.dp))

@Composable
private fun Scene(state: GameState, assets: ProjectAssets) {
    val background = assets.backgrounds.find { it.id == state.scene.backgroundId } ?: assets.backgrounds.firstOrNull()
    val sprite = assets.sprites.find { it.id == state.scene.visibleSpriteId }
    Box(Modifier.fillMaxWidth().height(175.dp).background(Brush.verticalGradient(listOf(Color(0xFFCC8CA3), Color(0xFF423756))), RoundedCornerShape(18.dp))) {
        background?.let { asset -> LoadBitmap(asset.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = BiasAlignment(asset.focusX * 2 - 1, asset.focusY * 2 - 1)) } }
        Text(background?.name ?: "未设置场景", color = Color.White.copy(alpha = .75f), fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        sprite?.let { asset -> LoadBitmap(asset.uri)?.let { bitmap -> Image(bitmap.asImageBitmap(), null, Modifier.align(Alignment.BottomCenter).fillMaxHeight().offset { IntOffset(((state.scene.spriteOffsetX + asset.offsetX + assets.globalSpriteOffsetX) * 175).roundToInt(), ((state.scene.spriteOffsetY + asset.offsetY + assets.globalSpriteOffsetY) * 175).roundToInt()) }.graphicsLayer(scaleX = state.scene.spriteScale * asset.scale * assets.globalSpriteScale, scaleY = state.scene.spriteScale * asset.scale * assets.globalSpriteScale), contentScale = ContentScale.Fit) } }
        if (sprite == null) Column(Modifier.align(Alignment.CenterEnd).padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(state.project.characters.find { it.id == state.scene.visibleCharacterId }?.name ?: "", fontSize = 38.sp, color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun LinePlayer(state: GameState, assets: ProjectAssets, advance: (GameState) -> Unit) {
    val line = state.currentLine ?: return
    val visibleText = line.text.chunked(180).getOrElse(state.linePage) { line.text.chunked(180).lastOrNull().orEmpty() }
    val pageCount = line.text.chunked(180).size.coerceAtLeast(1)
    LaunchedEffect(state.lineIndex) { }
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Text(line.speaker, color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(visibleText, color = Color.White, fontSize = 18.sp, lineHeight = 27.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (pageCount > 1) Text("${state.linePage + 1} / $pageCount", color = Soft, fontSize = 12.sp)
        Button({
            if (state.linePage + 1 < pageCount) { advance(state.copy(linePage = state.linePage + 1)); return@Button }
            val nextScene = if (line.speakerId.isBlank()) {
                state.scene.copy(visibleCharacterId = null, visibleSpriteId = null)
            } else {
                state.scene.copy(visibleCharacterId = line.speakerId, visibleSpriteId = line.spriteId ?: assets.sprites.firstOrNull { it.characterId == line.speakerId }?.id)
            }
            val nextIndex = state.lineIndex + 1
            if (nextIndex < state.lines.size) {
                val nextLine = state.lines[nextIndex]
                val followingScene = if (nextLine.speakerId.isBlank()) {
                    nextScene.copy(visibleCharacterId = null, visibleSpriteId = null)
                } else {
                    nextScene.copy(visibleCharacterId = nextLine.speakerId, visibleSpriteId = nextLine.spriteId ?: assets.sprites.firstOrNull { it.characterId == nextLine.speakerId }?.id)
                }
                advance(state.copy(lineIndex = nextIndex, linePage = 0, messages = state.messages + StoryMessage(nextLine.speaker, nextLine.text), scene = followingScene))
            } else advance(state.copy(lines = emptyList(), lineIndex = 0, linePage = 0, scene = nextScene))
        }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text(if (state.linePage + 1 < pageCount) "继续阅读" else if (state.lineIndex + 1 < state.lines.size) "下一句" else "继续") }
    }
}

@Composable private fun LoadingRow() = Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(color = Accent, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text("正在生成剧情……", color = Soft) }
@Composable private fun TransitionCard(transition: StoryTransition, continueAction: () -> Unit) { Column(Modifier.fillMaxWidth().background(Color(0xE63A315B), RoundedCornerShape(18.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(transition.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text(transition.subtitle, color = Soft, modifier = Modifier.padding(vertical = 12.dp)); Button(continueAction, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("继续") } } }
@Composable private fun EndingCard(replay: () -> Unit, continueStory: () -> Unit) { Column(Modifier.fillMaxWidth().background(Color(0xE63A315B), RoundedCornerShape(18.dp)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("故事已完结", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("你可以重新体验，也可以继续停留在结局之后。", color = Soft, modifier = Modifier.padding(vertical = 12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(replay, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383651))) { Text("重新开始") }; Button(continueStory, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink)) { Text("继续游玩") } } } }
@Composable private fun MoreOverlay(history: () -> Unit, saves: () -> Unit, debug: () -> Unit, editorLabel: String, exit: () -> Unit, reset: () -> Unit, close: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xF9141522)).padding(24.dp)) {
        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("更多设置", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp)) }
            Button({ history() }, Modifier.fillMaxWidth()) { Text("对话历史") }
            Button({ saves() }, Modifier.fillMaxWidth()) { Text("存档与读档") }
            Button({ exit() }, Modifier.fillMaxWidth()) { Text(editorLabel) }
            OutlinedButton({ debug() }, Modifier.fillMaxWidth()) { Text("调试信息") }
            OutlinedButton({ confirmReset = true }, Modifier.fillMaxWidth()) { Text("重新开始") }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("重新开始游戏？") }, text = { Text("当前游玩进度、对话和剧情状态将被清除，但项目编辑内容不会丢失。") }, confirmButton = { Button({ confirmReset = false; reset() }) { Text("重新开始") } }, dismissButton = { OutlinedButton({ confirmReset = false }) { Text("取消") } })
}
@Composable private fun DialoguePanel(state: GameState) = Column(Modifier.fillMaxWidth().background(Color(0xD9141522), RoundedCornerShape(18.dp)).padding(horizontal = 18.dp, vertical = 14.dp)) { state.messages.takeLast(2).forEach { Text(it.speaker, color = Accent, fontWeight = FontWeight.Bold, fontSize = 14.sp); Text(it.text, color = Color.White, fontSize = 17.sp, lineHeight = 25.sp, modifier = Modifier.padding(bottom = 7.dp)) } }
@Composable private fun CompactDialoguePanel(state: GameState) = Column(Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()).background(Color(0xD9141522), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) { state.messages.takeLast(2).forEach { Text(it.speaker, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(it.text, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(bottom = 6.dp)) } }
@Composable private fun HistoryOverlay(messages: List<StoryMessage>, close: () -> Unit) = Box(Modifier.fillMaxSize().background(Color(0xE9141522)).clickable(onClick = close).padding(24.dp)) { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) { Text("对话历史", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold); Text("只显示文字记录，点击空白处关闭", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)); messages.forEach { Text(it.speaker, color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)); Text(it.text, color = Color.White, lineHeight = 21.sp) } } }

@Composable
private fun SaveOverlay(saves: List<SaveSlot>, save: (Int) -> Unit, load: (SaveSlot) -> Unit, close: () -> Unit) {
    var confirmSave by remember { mutableStateOf<SaveSlot?>(null) }
    var confirmLoad by remember { mutableStateOf<SaveSlot?>(null) }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(Color(0xF9141522)).padding(20.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("存档与读档", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp)) }
            Text("保存会覆盖对应槽位，并保存当前章节、对话、选择和资源状态。", color = Soft, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            saves.forEach { slot ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text("存档 ${slot.slot + 1}", color = Accent, fontWeight = FontWeight.Bold)
                    Text(if (slot.stateJson.isBlank()) "空槽位" else "${slot.title.ifBlank { "未命名游戏" }} · 第 ${slot.chapter} 章", color = Color.White, modifier = Modifier.padding(top = 5.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ if (slot.stateJson.isBlank()) save(slot.slot) else confirmSave = slot }) { Text(if (slot.stateJson.isBlank()) "保存" else "覆盖保存") }
                        if (slot.stateJson.isNotBlank()) OutlinedButton({ confirmLoad = slot }) { Text("读档") }
                    }
                }
            }
        }
        confirmSave?.let { slot -> AlertDialog(onDismissRequest = { confirmSave = null }, title = { Text("覆盖存档 ${slot.slot + 1}？") }, text = { Text("原存档“${slot.title.ifBlank { "未命名游戏" }} · 第${slot.chapter}章”将被永久替换。") }, confirmButton = { Button({ save(slot.slot); confirmSave = null }) { Text("确认覆盖") } }, dismissButton = { OutlinedButton({ confirmSave = null }) { Text("取消") } }) }
        confirmLoad?.let { slot -> AlertDialog(onDismissRequest = { confirmLoad = null }, title = { Text("读取存档 ${slot.slot + 1}？") }, text = { Text("将放弃当前未保存的试玩进度，并恢复“${slot.title.ifBlank { "未命名游戏" }} · 第${slot.chapter}章”。") }, confirmButton = { Button({ load(slot); confirmLoad = null }) { Text("确认读档") } }, dismissButton = { OutlinedButton({ confirmLoad = null }) { Text("取消") } }) }
    }
}

@Composable
private fun DebugOverlay(close: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val entries = DebugLog.all()
    fun copy(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("AI 调试日志", text))
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(Color(0xF9141522)).padding(18.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("AI 调试日志", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("复制全部", color = Accent, modifier = Modifier.clickable { copy(entries.joinToString("\n\n") { "[${it.agent}] ${it.durationMs}ms\n请求：\n${it.request}\n响应：\n${it.response}\n结果：${it.result}\n错误：${it.error.orEmpty()}" }) }.padding(8.dp))
                Text("清空", color = Accent, modifier = Modifier.clickable { DebugLog.clear(); revision++ }.padding(8.dp))
                Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp))
            }
            if (entries.isEmpty()) Text("还没有 AI 请求。进行一次剧情生成或章节判定后，日志会显示在这里。", color = Soft, modifier = Modifier.padding(top = 20.dp))
            entries.forEach { entry ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Panel, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Text("Agent：${entry.agent}", color = Accent, fontWeight = FontWeight.Bold)
                    Text(if (entry.error == null) "状态：成功 · 耗时 ${entry.durationMs} ms" else "状态：失败 · ${debugFailureSummary(entry.error)}", color = if (entry.error == null) Soft else Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Text("解析结果：${entry.result}", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("复制", color = Accent, modifier = Modifier.clickable { copy("[${entry.agent}]\n请求：\n${entry.request}\n\n响应：\n${entry.response}\n\n结果：${entry.result}\n错误：${entry.error.orEmpty()}") }.padding(top = 8.dp))
                    Text("请求\n${entry.request}", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Text("响应\n${entry.response.ifBlank { "无响应正文" }}", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ValidationOverlay(project: ProjectConfig, assets: ProjectAssets, close: () -> Unit) {
    val characterIds = project.characters.map { it.id }.toSet()
    val variableIds = project.variables.map { it.id }.toSet()
    val assetIds = assets.sprites.map { it.id }.toSet()
    val errors = buildList {
        addAll(projectIssues(project, assets))
        if (project.variables.any { it.id.isBlank() || it.minimum > it.maximum }) add("存在数值变量范围无效。")
        project.chapters.forEach { chapter ->
            if (chapter.allowedCharacterIds.any { it !in characterIds }) add("第${chapter.id}章引用了不存在的角色 ID。")
        }
        project.chapters.forEach { chapter ->
            if (chapter.requiredFlag.isNotBlank() && chapter.requiredFlag !in project.events.map { it.id }) add("第${chapter.id}章引用了不存在的完成事件：${chapter.requiredFlag}")
        }
        assets.sprites.forEach { sprite -> if (sprite.uri.isBlank()) add("立绘资源 ${sprite.id} 没有图片。") }
        if (assetIds.isEmpty()) add("尚未上传立绘，试玩将使用文字场景。")
    }
    Box(Modifier.fillMaxSize().background(Color(0xF9141522)).padding(24.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("项目检查", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp)) }
            Text(if (errors.isEmpty()) "未发现阻塞问题，可以开始试玩。" else "发现 ${errors.size} 个问题：", color = if (errors.isEmpty()) Color(0xFF9BE7B0) else Accent, modifier = Modifier.padding(vertical = 14.dp))
            errors.forEach { Text("• $it", color = Color.White, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}

@Composable
private fun ProjectEditor(config: ProjectConfig, save: (ProjectConfig) -> Unit, close: () -> Unit) {
    var title by remember(config) { mutableStateOf(config.title) }
    var description by remember(config) { mutableStateOf(config.description) }
    var characters by remember(config) { mutableStateOf(config.characters) }
    var chapters by remember(config) { mutableStateOf(config.chapters) }
    var selectedChapter by remember(config) { mutableStateOf(config.chapters.firstOrNull()?.id ?: 1) }
    var selectedCharacter by remember(config) { mutableStateOf(config.characters.firstOrNull()?.id ?: "character_1") }
    var characterName by remember(config) { mutableStateOf("") }
    var characterText by remember(config) { mutableStateOf("") }
    var chapterTitle by remember(config) { mutableStateOf("") }
    var chapterText by remember(config) { mutableStateOf("") }
    var completionText by remember(config) { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val selected = chapters.find { it.id == selectedChapter }
    val selectedChar = characters.find { it.id == selectedCharacter }
    LaunchedEffect(selectedChapter) {
        chapterTitle = selected?.title.orEmpty(); chapterText = selected?.contentDescription.orEmpty(); completionText = selected?.completionHint.orEmpty()
    }
    LaunchedEffect(selectedCharacter) { characterName = selectedChar?.name.orEmpty(); characterText = selectedChar?.personality.orEmpty() }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(Color(0xF9141522)).padding(18.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("编辑游戏", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp)) }
            Text("这里只填写故事内容，不需要理解变量、事件或 JSON。", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            EditorTextField(title, { title = it.take(60) }, "游戏名称", Modifier.fillMaxWidth(), singleLine = true)
            EditorTextField(description, { description = it.take(500) }, "游戏简介", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
            Text("角色", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            if (characters.isEmpty()) Text("还没有角色，请添加第一个角色。", color = Soft, fontSize = 13.sp)
            characters.forEach { character ->
                Text("${character.name.ifBlank { "未命名角色" }}  ·  ${character.id}", color = if (character.id == selectedCharacter) Accent else Soft, modifier = Modifier.clickable { selectedCharacter = character.id }.padding(vertical = 5.dp))
            }
            EditorTextField(characterName, { characterName = it.take(40) }, "角色名称", Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
            EditorTextField(characterText, { characterText = it.take(500) }, "这个角色是什么样的人？", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({
                    val id = selectedCharacter.ifBlank { "character_${characters.size + 1}" }
                    val old = characters.firstOrNull { it.id == id }
                    val value = (old ?: CharacterConfig(id)).copy(
                        name = characterName.trim(), personality = characterText.trim(),
                        goal = old?.goal ?: characterText.trim(),
                        speechStyle = old?.speechStyle ?: "按照上述人设自然说话"
                    )
                    characters = (characters.filterNot { it.id == id } + value).filter { it.name.isNotBlank() }
                    selectedCharacter = id; status = "角色已保存。"
                }) { Text("保存角色") }
                OutlinedButton({ val id = "character_${characters.size + 1}"; characters = characters + CharacterConfig(id); selectedCharacter = id; characterName = ""; characterText = "" }) { Text("添加角色") }
                OutlinedButton({ if (characters.size > 1) { characters = characters.filterNot { it.id == selectedCharacter }; selectedCharacter = characters.first().id; status = "角色已删除。" } else status = "至少保留一个角色。" }) { Text("删除") }
            }
            Text("章节", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 18.dp))
            chapters.forEach { chapter -> Text("第 ${chapter.id} 章  ${chapter.title.ifBlank { "未命名章节" }}", color = if (chapter.id == selectedChapter) Accent else Soft, modifier = Modifier.clickable { selectedChapter = chapter.id }.padding(vertical = 5.dp)) }
            EditorTextField(chapterTitle, { chapterTitle = it.take(60) }, "章节名称，例如：第一章", Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true)
            EditorTextField(chapterText, { chapterText = it.take(1000) }, "这一章想发生什么？", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 5)
            EditorTextField(completionText, { completionText = it.take(500) }, "这一章怎样算完成？用一句话描述", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({
                    val id = selectedChapter
                    val old = chapters.firstOrNull { it.id == id } ?: ChapterConfig(id)
                    val value = old.copy(title = chapterTitle.trim(), goal = old.goal, contentDescription = chapterText.trim(), allowedCharacterIds = characters.map { it.id }, completionHint = completionText.trim())
                    chapters = (chapters.filterNot { it.id == id } + value).sortedBy { it.id }; status = "章节已保存。"
                }) { Text("保存章节") }
                OutlinedButton({ val id = (chapters.maxOfOrNull { it.id } ?: 0) + 1; chapters = chapters + ChapterConfig(id); selectedChapter = id; chapterTitle = ""; chapterText = ""; completionText = "" }) { Text("添加章节") }
                OutlinedButton({ if (chapters.size > 1) { chapters = chapters.filterNot { it.id == selectedChapter }; selectedChapter = chapters.first().id; status = "章节已删除。" } else status = "至少保留一个章节。" }) { Text("删除") }
            }
            status?.let { Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Button({
                val finalCharacters = characters.filter { it.name.isNotBlank() }
                val finalChapters = chapters.filter { it.title.isNotBlank() || it.goal.isNotBlank() }
                val playerId = config.playerCharacterId.takeIf { id -> finalCharacters.any { it.id == id } } ?: finalCharacters.firstOrNull()?.id.orEmpty()
                save(config.copy(title = title.trim(), description = description.trim(), playerCharacterId = playerId, characters = finalCharacters, chapters = finalChapters)); close()
            }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("保存游戏内容") }
        }
    }
}

@Composable
private fun LegacyProjectEditor(config: ProjectConfig, save: (ProjectConfig) -> Unit, close: () -> Unit) {
    var title by remember(config) { mutableStateOf(config.title) }
    var description by remember(config) { mutableStateOf(config.description) }
    var character by remember(config) { mutableStateOf(config.characters.firstOrNull() ?: CharacterConfig()) }
    var chapter by remember(config) { mutableStateOf(config.chapters.firstOrNull() ?: ChapterConfig()) }
    var characters by remember(config) { mutableStateOf(config.characters) }
    var chapters by remember(config) { mutableStateOf(config.chapters) }
    var variables by remember(config) { mutableStateOf(config.variables) }
    var items by remember(config) { mutableStateOf(config.items) }
    var events by remember(config) { mutableStateOf(config.events) }
    var item by remember(config) { mutableStateOf(config.items.firstOrNull() ?: ItemConfig()) }
    var event by remember(config) { mutableStateOf(config.events.firstOrNull() ?: EventConfig()) }
    var allowedIdsText by remember(config) { mutableStateOf(chapter.allowedCharacterIds.joinToString(",")) }
    var message by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(Color(0xF9141522)).padding(18.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("项目编辑", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp)) }
            Text("这些配置会在生成游戏时拼接到叙事 Agent 的上下文中。", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedTextField(title, { title = it.take(60) }, Modifier.fillMaxWidth(), label = { Text("作品名称") }, singleLine = true)
            OutlinedTextField(description, { description = it.take(300) }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("作品简介") }, minLines = 2)
            Text("角色卡", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("已配置角色：${characters.size}", color = Soft, fontSize = 12.sp)
            OutlinedTextField(character.name, { character = character.copy(name = it.take(30)) }, Modifier.fillMaxWidth(), label = { Text("角色名称") }, singleLine = true)
            OutlinedTextField(character.personality, { character = character.copy(personality = it.take(200)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("性格") }, minLines = 2)
            OutlinedTextField(character.goal, { character = character.copy(goal = it.take(200)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("本章目标") }, minLines = 2)
            OutlinedTextField(character.speechStyle, { character = character.copy(speechStyle = it.take(120)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("说话方式") }, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({
                    val id = if (characters.any { it.id == character.id }) character.id else "character_${characters.size + 1}"
                    val item = character.copy(id = id)
                    characters = characters.filterNot { it.id == id } + item
                    character = CharacterConfig(id = "character_${characters.size + 1}")
                    message = "角色已加入项目。"
                }) { Text("保存当前角色") }
                OutlinedButton({ character = CharacterConfig(id = "character_${characters.size + 1}") }) { Text("新建角色") }
                OutlinedButton({ if (characters.size > 1) { characters = characters.filterNot { it.id == character.id }; character = characters.first(); message = "角色已删除。" } else message = "至少保留一个角色。" }) { Text("删除") }
            }
            characters.forEach { item ->
                Text("${item.id}  ${item.name}", color = Soft, fontSize = 12.sp, modifier = Modifier.clickable { character = item }.padding(top = 6.dp))
            }
            Text("章节卡", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("已配置章节：${chapters.size}", color = Soft, fontSize = 12.sp)
            OutlinedTextField(chapter.title, { chapter = chapter.copy(title = it.take(50)) }, Modifier.fillMaxWidth(), label = { Text("章节标题") }, singleLine = true)
            OutlinedTextField(chapter.goal, { chapter = chapter.copy(goal = it.take(150)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("当前剧情目标") }, minLines = 2)
            OutlinedTextField(chapter.completionHint, { chapter = chapter.copy(completionHint = it.take(150)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("完成提示（给作者看的规则）") }, minLines = 2)
            OutlinedTextField(allowedIdsText, { allowedIdsText = it.take(150); chapter = chapter.copy(allowedCharacterIds = it.split(",").map(String::trim).filter(String::isNotBlank)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("允许角色 ID，用逗号分隔") }, supportingText = { Text("例如：aya,character_2") }, singleLine = true)
            OutlinedTextField(chapter.requiredFlag, { chapter = chapter.copy(requiredFlag = it.take(50)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("需要的事件标记（可选）") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({
                    val item = chapter.copy(id = if (chapters.any { it.id == chapter.id }) chapter.id else (chapters.maxOfOrNull { it.id } ?: 0) + 1)
                    chapters = chapters.filterNot { it.id == item.id } + item
                    chapter = ChapterConfig(id = (chapters.maxOfOrNull { it.id } ?: 0) + 1)
                    message = "章节已加入项目。"
                }) { Text("保存当前章节") }
                OutlinedButton({ chapter = ChapterConfig(id = (chapters.maxOfOrNull { it.id } ?: 0) + 1); allowedIdsText = "aya" }) { Text("新建章节") }
                OutlinedButton({ if (chapters.size > 1) { chapters = chapters.filterNot { it.id == chapter.id }; chapter = chapters.first(); allowedIdsText = chapter.allowedCharacterIds.joinToString(","); message = "章节已删除。" } else message = "至少保留一个章节。" }) { Text("删除") }
            }
            chapters.forEach { item ->
                Text("第${item.id}章  ${item.title}", color = Soft, fontSize = 12.sp, modifier = Modifier.clickable { chapter = item; allowedIdsText = item.allowedCharacterIds.joinToString(",") }.padding(top = 6.dp))
            }
            Text("数值变量", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("用简单的数值表达关系、勇气、金钱、线索等状态。每轮变化会被系统限制在设定范围内。", color = Soft, fontSize = 12.sp)
            var variable by remember(config) { mutableStateOf(config.variables.firstOrNull() ?: NumberVariableConfig()) }
            OutlinedTextField(variable.id, { variable = variable.copy(id = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(30)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("变量 ID") }, singleLine = true)
            OutlinedTextField(variable.name, { variable = variable.copy(name = it.take(30)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("显示名称") }, singleLine = true)
            OutlinedTextField(variable.initial.toString(), { variable = variable.copy(initial = it.toIntOrNull() ?: variable.initial) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("初始值") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(variable.minimum.toString(), { variable = variable.copy(minimum = it.toIntOrNull() ?: variable.minimum) }, Modifier.weight(1f), label = { Text("最小值") }, singleLine = true); OutlinedTextField(variable.maximum.toString(), { variable = variable.copy(maximum = it.toIntOrNull() ?: variable.maximum) }, Modifier.weight(1f), label = { Text("最大值") }, singleLine = true) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(variable.perTurnMinimum.toString(), { variable = variable.copy(perTurnMinimum = it.toIntOrNull() ?: variable.perTurnMinimum) }, Modifier.weight(1f), label = { Text("每轮最小变化") }, singleLine = true); OutlinedTextField(variable.perTurnMaximum.toString(), { variable = variable.copy(perTurnMaximum = it.toIntOrNull() ?: variable.perTurnMaximum) }, Modifier.weight(1f), label = { Text("每轮最大变化") }, singleLine = true) }
            OutlinedTextField(variable.description, { variable = variable.copy(description = it.take(150)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("变化说明") }, minLines = 2)
            Text("当前变量：${config.variables.joinToString("、") { it.name }}", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({ variables = variables.filterNot { it.id == variable.id } + variable; message = "数值变量已保存。" }) { Text("保存变量") }
                OutlinedButton({ variable = NumberVariableConfig(id = "number_${variables.size + 1}", name = "新数值") }) { Text("新建变量") }
                OutlinedButton({ if (variables.size > 1) { variables = variables.filterNot { it.id == variable.id }; variable = variables.first(); message = "数值变量已删除。" } else message = "至少保留一个数值变量。" }) { Text("删除") }
            }
            variables.forEach { item -> Text("${item.id}  ${item.name}", color = Soft, fontSize = 12.sp, modifier = Modifier.clickable { variable = item }.padding(top = 5.dp)) }
            Text("道具", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            Text("条件只能引用这里定义的 ID。", color = Soft, fontSize = 12.sp)
            OutlinedTextField(item.id, { item = item.copy(id = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(30)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("道具 ID") }, singleLine = true)
            OutlinedTextField(item.name, { item = item.copy(name = it.take(30)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("道具名称") }, singleLine = true)
            OutlinedTextField(item.description, { item = item.copy(description = it.take(150)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("道具说明") }, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({ items = items.filterNot { it.id == item.id } + item; message = "道具已保存。" }) { Text("保存道具") }
                OutlinedButton({ item = ItemConfig(id = "item_${items.size + 1}", name = "新道具", description = "") }) { Text("新建") }
                OutlinedButton({ if (items.size > 1) { items = items.filterNot { it.id == item.id }; item = items.first(); message = "道具已删除。" } else message = "至少保留一个道具。" }) { Text("删除") }
            }
            items.forEach { itemValue -> Text("${itemValue.id}（${itemValue.name}）", color = Soft, fontSize = 12.sp, modifier = Modifier.clickable { item = itemValue }.padding(top = 5.dp)) }
            Text("事件", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            OutlinedTextField(event.id, { event = event.copy(id = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(30)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("事件 ID") }, singleLine = true)
            OutlinedTextField(event.name, { event = event.copy(name = it.take(30)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("事件名称") }, singleLine = true)
            OutlinedTextField(event.description, { event = event.copy(description = it.take(150)) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("事件说明") }, minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button({ events = events.filterNot { it.id == event.id } + event; message = "事件已保存。" }) { Text("保存事件") }
                OutlinedButton({ event = EventConfig(id = "event_${events.size + 1}", name = "新事件", description = "") }) { Text("新建") }
                OutlinedButton({ if (events.size > 1) { events = events.filterNot { it.id == event.id }; event = events.first(); message = "事件已删除。" } else message = "至少保留一个事件。" }) { Text("删除") }
            }
            events.forEach { eventValue -> Text("${eventValue.id}（${eventValue.name}）", color = Soft, fontSize = 12.sp, modifier = Modifier.clickable { event = eventValue }.padding(top = 5.dp)) }
            message?.let { Text(it, color = Accent, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            Button({
                val finalTitle = title.trim().ifBlank { "未命名作品" }
                val finalAllowed = chapter.allowedCharacterIds.filter { id -> characters.any { it.id == id } }.ifEmpty { listOf(character.id) }
                val finalChapter = chapter.copy(title = chapter.title.trim().ifBlank { "未命名章节" }, goal = chapter.goal.trim().ifBlank { "让角色自然推进当前剧情" }, allowedCharacterIds = finalAllowed)
                val finalCharacters = (characters + character.copy(name = character.name.trim().ifBlank { "角色" })).distinctBy { it.id }
                val finalChapters = (chapters + finalChapter).distinctBy { it.id }.sortedBy { it.id }
                val finalVariable = variable.copy(id = variable.id.ifBlank { "number_1" }, minimum = minOf(variable.minimum, variable.maximum), maximum = maxOf(variable.minimum, variable.maximum), initial = variable.initial.coerceIn(variable.minimum, variable.maximum), perTurnMinimum = minOf(variable.perTurnMinimum, variable.perTurnMaximum), perTurnMaximum = maxOf(variable.perTurnMinimum, variable.perTurnMaximum))
                val finalVariables = (variables + variable).distinctBy { it.id }
                val finalItems = (items + item.copy(id = item.id.ifBlank { "item_1" }, name = item.name.ifBlank { "道具" })).distinctBy { it.id }
                val finalEvents = (events + event.copy(id = event.id.ifBlank { "event_1" }, name = event.name.ifBlank { "事件" })).distinctBy { it.id }
                save(ProjectConfig(finalTitle, description.trim(), config.style, config.restrictions, config.playerCharacterId, finalCharacters, finalChapters, finalVariables, finalItems, finalEvents))
                close()
            }, Modifier.fillMaxWidth().padding(top = 14.dp)) { Text("保存并用于试玩") }
        }
    }
}

@Composable
private fun AssetEditor(context: Context, projectId: String, defaultCharacterId: String, assets: ProjectAssets, save: (ProjectAssets) -> Unit, close: () -> Unit) {
    var selected by remember { mutableStateOf(assets.sprites.firstOrNull()?.id) }
    var scale by remember { mutableFloatStateOf(assets.sprites.find { it.id == selected }?.let { 1f } ?: 1f) }
    var spriteOffsetX by remember { mutableFloatStateOf(0f) }
    var spriteOffsetY by remember { mutableFloatStateOf(0f) }
    var focusX by remember { mutableFloatStateOf(assets.backgrounds.firstOrNull()?.focusX ?: .5f) }
    var focusY by remember { mutableFloatStateOf(assets.backgrounds.firstOrNull()?.focusY ?: .5f) }
    var message by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf("list") }
    var previewUri by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }
    var editingCondition by remember { mutableStateOf("") }
    var replacingId by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val type = context.contentResolver.getType(uri)
        if (type != "image/png") { message = "请选择透明 PNG 立绘文件。"; return@rememberLauncherForActivityResult }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val bitmap = runCatching { context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        if (bitmap == null || !bitmap.hasAlpha()) { message = "这张图片不是带透明通道的 PNG，请在 APP 外处理后重新上传。"; return@rememberLauncherForActivityResult }
        val replaceId = replacingId
        if (replaceId != null) {
             val privateUri = runCatching { GameStore.copyAssetToPrivate(context, uri.toString(), "projects/$projectId") }.getOrElse { message = "无法保存立绘：${it.message}"; return@rememberLauncherForActivityResult }
             save(assets.copy(sprites = assets.sprites.map { if (it.id == replaceId) it.copy(uri = privateUri, scale = autoSpriteScale(bitmap)) else it }))
            replacingId = null; message = "立绘图片已替换。"
        } else {
             val privateUri = runCatching { GameStore.copyAssetToPrivate(context, uri.toString(), "projects/$projectId") }.getOrElse { message = "无法保存立绘：${it.message}"; return@rememberLauncherForActivityResult }
              val characterId = defaultCharacterId.ifBlank { assets.sprites.firstOrNull()?.characterId.orEmpty() }
              if (characterId.isBlank()) { message = "请先在项目中创建角色，再添加立绘。"; return@rememberLauncherForActivityResult }
              val asset = SpriteAsset("sprite_${System.currentTimeMillis()}", characterId, "新立绘", privateUri, listOf("未分类"), scale = autoSpriteScale(bitmap))
            save(assets.copy(sprites = assets.sprites + asset)); selected = asset.id; mode = "list"; message = "立绘已添加。"
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val replaceId = replacingId
        if (replaceId != null) {
             val privateUri = runCatching { GameStore.copyAssetToPrivate(context, uri.toString(), "projects/$projectId") }.getOrElse { message = "无法保存场景：${it.message}"; return@rememberLauncherForActivityResult }
             save(assets.copy(backgrounds = assets.backgrounds.map { if (it.id == replaceId) it.copy(uri = privateUri) else it }))
            replacingId = null; message = "场景图片已替换。"
        } else {
             val privateUri = runCatching { GameStore.copyAssetToPrivate(context, uri.toString(), "projects/$projectId") }.getOrElse { message = "无法保存场景：${it.message}"; return@rememberLauncherForActivityResult }
             val asset = BackgroundAsset("background_${System.currentTimeMillis()}", "新场景", privateUri, listOf("未分类"), "", focusX, focusY)
            save(assets.copy(backgrounds = assets.backgrounds + asset)); mode = "list"; message = "场景已添加。"
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().background(Color(0xF9141522)).padding(18.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("资源与适配", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("关闭", color = Accent, modifier = Modifier.clickable(onClick = close).padding(8.dp)) }
        Text("立绘仅接受透明 PNG。原图比例不会被拉伸；使用说明可以填写情绪、受伤状态、地点、行为、装扮或剧情条件。", color = Soft, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            message?.let { Text(it, color = Accent, fontSize = 12.sp) }
            if (mode == "list") {
                Button({ picker.launch(arrayOf("image/png")) }, Modifier.fillMaxWidth()) { Text("新增透明 PNG 立绘") }
                Text("立绘列表", color = Accent, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
                assets.sprites.forEach { asset ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(Panel, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadBitmap(asset.uri)?.let { Image(it.asImageBitmap(), null, Modifier.size(64.dp), contentScale = ContentScale.Fit) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(asset.name, color = Color.White); Text(asset.usageCondition.ifBlank { "未填写使用条件" }, color = Soft, fontSize = 12.sp) }
                        Text("预览", color = Accent, modifier = Modifier.clickable { previewUri = asset.uri }.padding(6.dp))
                        Text("编辑", color = Accent, modifier = Modifier.clickable { editingId = asset.id; editingName = asset.name; editingCondition = asset.usageCondition; scale = asset.scale; spriteOffsetX = asset.offsetX; spriteOffsetY = asset.offsetY }.padding(6.dp))
                        Text("删除", color = Accent, modifier = Modifier.clickable { deleteId = asset.id }.padding(6.dp))
                    }
                }
                Button({ mode = "backgrounds" }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("管理场景背景") }
            } else {
                Button({ backgroundPicker.launch(arrayOf("image/*")) }, Modifier.fillMaxWidth()) { Text("新增场景背景") }
                Text("场景列表", color = Accent, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
                assets.backgrounds.forEach { asset ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).background(Panel, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadBitmap(asset.uri)?.let { Image(it.asImageBitmap(), null, Modifier.size(80.dp, 56.dp), contentScale = ContentScale.Crop) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(asset.name, color = Color.White); Text(asset.usageCondition.ifBlank { "未填写展示条件" }, color = Soft, fontSize = 12.sp) }
                        Text("预览", color = Accent, modifier = Modifier.clickable { previewUri = asset.uri }.padding(6.dp))
                        Text("编辑", color = Accent, modifier = Modifier.clickable { editingId = asset.id; editingName = asset.name; editingCondition = asset.usageCondition }.padding(6.dp))
                        Text("删除", color = Accent, modifier = Modifier.clickable { deleteId = asset.id }.padding(6.dp))
                    }
                }
                OutlinedButton({ mode = "list" }, Modifier.fillMaxWidth().padding(top = 16.dp)) { Text("返回立绘列表") }
            }
        }
        editingId?.let { id ->
            val sprite = assets.sprites.find { it.id == id }
            val background = assets.backgrounds.find { it.id == id }
            val uri = sprite?.uri ?: background?.uri
            if (uri != null) AlertDialog(
                onDismissRequest = { editingId = null },
                title = { Text("编辑资源") },
                text = {
                    Column {
                        LoadBitmap(uri)?.let { bitmap ->
                            Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF303044)), contentAlignment = Alignment.BottomCenter) {
                                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxHeight(.9f).graphicsLayer(scaleX = if (sprite != null) scale else 1f, scaleY = if (sprite != null) scale else 1f, translationX = spriteOffsetX * 100f, translationY = spriteOffsetY * 100f), contentScale = ContentScale.Fit)
                            }
                        }
                        EditorTextField(editingName, { editingName = it.take(60) }, "资源名称", Modifier.fillMaxWidth().padding(top = 8.dp), true)
                        EditorTextField(editingCondition, { editingCondition = it.take(500) }, "使用说明", Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
                        if (sprite != null) {
                            Text("人物大小 ${"%.2f".format(scale)}", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                            Slider(scale, { scale = it }, valueRange = .05f..6f)
                            Text("水平位置", color = Accent, fontSize = 13.sp)
                            Slider(spriteOffsetX, { spriteOffsetX = it }, valueRange = -4f..4f)
                            Text("垂直位置", color = Accent, fontSize = 13.sp)
                            Slider(spriteOffsetY, { spriteOffsetY = it }, valueRange = -4f..4f)
                            Text("恢复自动适配", color = Accent, modifier = Modifier.clickable {
                                runCatching { GameStore.openAssetInput(context, uri)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()?.let { bitmap ->
                                    scale = autoSpriteScale(bitmap); spriteOffsetX = 0f; spriteOffsetY = 0f
                                }
                            }.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    Button({
                        val next = assets.copy(
                            sprites = assets.sprites.map { if (it.id == id) it.copy(name = editingName.trim().ifBlank { "立绘" }, usageCondition = editingCondition.trim(), scale = scale, offsetX = spriteOffsetX, offsetY = spriteOffsetY) else it },
                            backgrounds = assets.backgrounds.map { if (it.id == id) it.copy(name = editingName.trim().ifBlank { "场景" }, usageCondition = editingCondition.trim()) else it }
                        )
                        save(next); editingId = null; message = "资源已更新。"
                    }) { Text("保存修改") }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ replacingId = id; editingId = null; if (sprite != null) picker.launch(arrayOf("image/png")) else backgroundPicker.launch(arrayOf("image/*")) }) { Text("替换图片") }
                        OutlinedButton({ editingId = null }) { Text("取消") }
                    }
                }
            )
        }
        previewUri?.let { uri ->
            Box(Modifier.fillMaxSize().background(Color(0xDD141522)).clickable { previewUri = null }.padding(24.dp), contentAlignment = Alignment.Center) {
                LoadBitmap(uri)?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(420.dp), contentScale = ContentScale.Fit) }
            }
        }
        deleteId?.let { id ->
            AlertDialog(
                onDismissRequest = { deleteId = null },
                title = { Text("删除资源") },
                text = { Text("确定删除这个资源吗？已在章节中勾选的资源会在试玩时自动回退为默认画面。") },
                confirmButton = { Button({
                    save(assets.copy(sprites = assets.sprites.filterNot { it.id == id }, backgrounds = assets.backgrounds.filterNot { it.id == id }))
                    if (selected == id) selected = null
                    deleteId = null; message = "资源已删除。"
                }) { Text("确认删除") } },
                dismissButton = { OutlinedButton({ deleteId = null }) { Text("取消") } }
            )
        }
    }
}

@Composable private fun LoadBitmap(uri: String): android.graphics.Bitmap? { val context = androidx.compose.ui.platform.LocalContext.current; return remember(uri) { runCatching { val parsed = Uri.parse(uri); if (parsed.scheme == "file") File(parsed.path.orEmpty()).inputStream().use { BitmapFactory.decodeStream(it) } else context.contentResolver.openInputStream(parsed).use { BitmapFactory.decodeStream(it) } }.getOrNull() } }
