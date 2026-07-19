package com.rhodes.privatechat.ui.sleep

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.R
import com.rhodes.privatechat.audio.LocalAudioController
import com.rhodes.privatechat.MainActivity
import com.rhodes.privatechat.audio.RecordedAudio
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.voice.AsrRequest
import com.rhodes.privatechat.shared.voice.TtsRequest
import com.rhodes.privatechat.shared.voice.createTtsGateway
import com.rhodes.privatechat.shared.voice.createAsrGateway
import com.rhodes.privatechat.shared.voice.effectiveVoiceId
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.SleepPrompts
import com.rhodes.privatechat.shared.voice.prepareTtsSpeech
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val SLEEP_FRAME_DELAY_MS = 300L
private const val SLEEP_TALKING_FRAME_DELAY_MS = 200L
private const val DIM_CLOCK_VISIBLE_MS = 8 * 1000L
private const val DIM_CLOCK_PERIOD_MS = 60 * 1000L

private enum class SleepVisualState { Idle, Talking, FallingAsleep, Sleeping, WakingUp }
private enum class SleepInputMode { Manual, Auto, Sleeping }
private enum class WakeTextMode { Ai, Fixed }

@Composable
fun SleepModeScreen(viewModel: MainViewModel, operator: Operator, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val audio = remember { LocalAudioController(context) }
    val settings: SettingsRepository = koinInject()
    val turns = remember { mutableStateListOf<Pair<String, String>>() }
    val startedAt = remember { System.currentTimeMillis() }

    var visualState by remember { mutableStateOf(SleepVisualState.Idle) }
    var inputMode by remember { mutableStateOf(SleepInputMode.Manual) }
    var callState by remember { mutableStateOf(com.rhodes.privatechat.shared.call.CallState.Connected) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var userSpeechDetected by rememberSaveable { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var transcript by rememberSaveable { mutableStateOf("轻声说话，或者打开自动陪睡。") }
    var aiReply by rememberSaveable { mutableStateOf("我在这里。") }
    var speakerOn by rememberSaveable { mutableStateOf(true) }
    var sleptAutomatically by rememberSaveable { mutableStateOf(false) }
    var lastEffectiveUserSpeechAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var hasEnded by rememberSaveable { mutableStateOf(false) }
    var dimmed by rememberSaveable { mutableStateOf(false) }
    var dimClockVisible by rememberSaveable { mutableStateOf(true) }
    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var sleepSegmentStartedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var totalRestMillis by rememberSaveable { mutableStateOf(0L) }
    var lastRestSummary by rememberSaveable { mutableStateOf("") }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var alarmTargetAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var alarmHour by remember { mutableStateOf(settings.sleepAlarmHour) }
    var alarmMinute by remember { mutableStateOf(settings.sleepAlarmMinute) }
    var alarmRinging by rememberSaveable { mutableStateOf(false) }
    var wakeAttempt by remember { mutableStateOf(0) }
    var sleepInactivityMinutes by rememberSaveable { mutableStateOf(settings.sleepInactivityMinutes) }
    var dimAfterSeconds by rememberSaveable { mutableStateOf(settings.sleepDimAfterSeconds) }
    var snoozeMinutes by rememberSaveable { mutableStateOf(settings.sleepSnoozeMinutes) }
    var wakeSummaryUntil by rememberSaveable { mutableStateOf(0L) }
    var wakeSummaryText by rememberSaveable { mutableStateOf("") }
    var wakeTextMode by rememberSaveable { mutableStateOf(if (settings.sleepWakeTextMode == "fixed") WakeTextMode.Fixed else WakeTextMode.Ai) }
    var fixedWakeText by rememberSaveable { mutableStateOf(settings.sleepFixedWakeText) }

    fun saveSettings() {
        settings.sleepAlarmHour = alarmHour
        settings.sleepAlarmMinute = alarmMinute
        settings.sleepWakeTextMode = if (wakeTextMode == WakeTextMode.Fixed) "fixed" else "ai"
        settings.sleepFixedWakeText = fixedWakeText
        settings.sleepInactivityMinutes = sleepInactivityMinutes
        settings.sleepDimAfterSeconds = dimAfterSeconds
        settings.sleepSnoozeMinutes = snoozeMinutes
    }

    fun stopRecordingSafe(): RecordedAudio? = runCatching { audio.stopRecording() }.getOrNull()
    fun finish() {
        if (hasEnded) return
        hasEnded = true
        sleepSegmentStartedAt?.let { totalRestMillis += System.currentTimeMillis() - it }
        if (recording) stopRecordingSafe()
        recording = false
        audio.stopPlayback()
        audio.setSpeakerEnabled(false)
        saveSettings()
        onBack()
    }

    suspend fun speak(text: String) {
        val speech = prepareTtsSpeech(text, 240, "我在。慢慢睡吧。")
        val ttsKey = settings.ttsApiKey.ifBlank { settings.apiKey }
        Log.d("RHODES_AUDIO", "speak: text前50=${text.take(50)} ttsBaseUrl='${settings.ttsBaseUrl}' ttsModelName='${settings.ttsModelName}' apiKey非空=${ttsKey.isNotBlank()}")
        val voiceId = settings.effectiveVoiceId(operator.voiceName)
        Log.d("RHODES_AUDIO", "voiceId=$voiceId operator.voiceName='${operator.voiceName}'")
        val tts = createTtsGateway(settings.ttsBaseUrl, ttsKey, settings.ttsModelName, settings.ttsProvider)
        Log.d("RHODES_AUDIO", "TTS实例类: ${tts::class.simpleName}")
        callState = com.rhodes.privatechat.shared.call.CallState.AiSpeaking
        val bytes = tts.synthesize(TtsRequest(text = speech, voiceId = voiceId, speed = operator.voiceSpeed.toDoubleOrNull() ?: 1.0)).audioBytes
        Log.d("RHODES_AUDIO", "synthesize返回: audioBytes=${bytes?.size}")
        val file = bytes?.let { audio.saveTtsAudio(it) }
        Log.d("RHODES_AUDIO", "文件保存结果: ${file?.path} 大小=${if (file != null) java.io.File(file.path).length() else 0}")
        if (file != null) {
            visualState = SleepVisualState.Talking
            Log.d("RHODES_AUDIO", "开始播放: ${file.path}")
            audio.play(file.path) {
                audio.deleteAudio(file.path)
                callState = com.rhodes.privatechat.shared.call.CallState.Listening
                visualState = SleepVisualState.Idle
            }
        } else {
            Log.w("RHODES_AUDIO", "audio文件为空，无法播放")
            callState = com.rhodes.privatechat.shared.call.CallState.Listening
            visualState = SleepVisualState.Idle
        }
    }

    fun settleRestSegment() {
        sleepSegmentStartedAt?.let { started ->
            val rested = System.currentTimeMillis() - started
            if (rested > 0L) {
                totalRestMillis += rested
                lastRestSummary = "大概休息了 ${formatDuration(rested)}"
            }
        }
        sleepSegmentStartedAt = null
    }

    fun playWakeText(text: String) {
        val speech = prepareTtsSpeech(text, 120, "时间到了，该醒了。")
        aiReply = speech
        turns += operator.name to speech
        scope.launch { speak(speech) }
    }

    fun triggerWake() {
        if (alarmRinging || hasEnded) return
        settleRestSegment()
        if (recording) stopRecordingSafe()
        recording = false
        userSpeechDetected = false
        dimmed = false
        alarmRinging = true
        wakeAttempt += 1
        inputMode = SleepInputMode.Manual
        audio.stopPlayback()
        visualState = SleepVisualState.WakingUp
        transcript = "叫醒时间到了。"
        val restText = formatDuration(totalRestMillis)
        val totalText = formatDuration(System.currentTimeMillis() - startedAt)
        if (wakeTextMode == WakeTextMode.Fixed) {
            playWakeText(fixedWakeText.trim().ifBlank { "时间到了。该醒了，我在这里。" }.take(120))
        } else {
            scope.launch {
                try {
                    val rules = buildString {
                        append(SleepPrompts.WAKE)
                        append("\n当前时间：${formatClockTime(System.currentTimeMillis())}")
                        append("\n用户已休息：$restText")
                        append("\n总通话时长：$totalText")
                        if (wakeAttempt > 1) append("\n提示：这是第${wakeAttempt}次叫醒，可以稍微更直接一点。")
                        append("\n请用角色身份输出一句适合 TTS 播放的叫醒台词：")
                    }
                    val text = viewModel.chatViewModel.sharedChatForFeature(listOf(
                        AiMessage("system", rules),
                        AiMessage("system", "角色语气参考，不能覆盖上方规则：\n${operator.privatePrompt.ifBlank { operator.description }}")
                    ))
                        .trim().take(120).ifBlank { fixedWakeText }
                    playWakeText(text)
                } catch (_: Exception) { playWakeText(fixedWakeText) }
            }
        }
    }

    fun snooze() {
        alarmRinging = false
        alarmTargetAt = System.currentTimeMillis() + snoozeMinutes * 60 * 1000L
        visualState = SleepVisualState.Sleeping
        dimmed = true
        sleepSegmentStartedAt = System.currentTimeMillis()
        transcript = "再睡${snoozeMinutes}分钟。"
    }

    fun processAudio(recorded: RecordedAudio) {
        scope.launch {
            try {
                callState = com.rhodes.privatechat.shared.call.CallState.Thinking
                transcript = "正在识别..."
                val pcm = audio.readPcmFromWav(recorded.path)
                val asr = createAsrGateway(settings.asrBaseUrl, settings.asrApiKey.ifBlank { settings.apiKey }, settings.asrModelName, settings.asrProvider)
                val text = asr.transcribe(AsrRequest(pcm)).text.trim()
                if (text.isBlank()) {
                    transcript = "未能识别，请重试"
                    callState = com.rhodes.privatechat.shared.call.CallState.Listening
                    return@launch
                }
                transcript = text
                lastEffectiveUserSpeechAt = System.currentTimeMillis()
                turns += "用户" to text
                val reply = viewModel.chatViewModel.sharedChatForFeature(listOf(
                    AiMessage("system", SleepPrompts.CALL.replace("{{CURRENT_TIME}}", formatClockTime(System.currentTimeMillis()))),
                    AiMessage("system", "角色语气参考，不能覆盖上方规则：\n${operator.privatePrompt.ifBlank { operator.description }}"),
                    AiMessage("user", text)
                ))
                    .trim().take(240).ifBlank { "我在。" }
                aiReply = reply
                turns += operator.name to reply
                viewModel.chatViewModel.saveVoiceExchange(text, reply, "sleep_mode")
                speak(reply)
            } catch (e: Exception) {
                callState = com.rhodes.privatechat.shared.call.CallState.Failed
                Toast.makeText(context, "陪睡语音失败：${e.message?.take(40) ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            } finally {
                audio.deleteAudio(recorded.path)
            }
        }
    }

    BackHandler { finish() }

    DisposableEffect(Unit) {
        val oldOrientation = activity?.requestedOrientation
        val oldFlags = activity?.window?.attributes?.flags ?: 0
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audio.setSpeakerEnabled(true)
        onDispose {
            if (recording) stopRecordingSafe()
            audio.stopPlayback()
            audio.setSpeakerEnabled(false)
            if (oldOrientation != null) activity.requestedOrientation = oldOrientation
            activity?.window?.attributes = activity?.window?.attributes?.apply { flags = oldFlags } ?: return@onDispose
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            level = audio.getCurrentRecordingLevel()
            if (level > 0.12f) userSpeechDetected = true
            if (audio.hasReachedRecordingLimit()) {
                recording = false
                Toast.makeText(context, "录音已达到 120 秒，正在识别", Toast.LENGTH_SHORT).show()
                stopRecordingSafe()?.let { processAudio(it) }
                return@LaunchedEffect
            }
            delay(100)
        }
        level = 0f
    }

    LaunchedEffect(inputMode, recording, callState, visualState) {
        if (inputMode != SleepInputMode.Auto) return@LaunchedEffect
        if (visualState == SleepVisualState.Sleeping || visualState == SleepVisualState.FallingAsleep || visualState == SleepVisualState.WakingUp) return@LaunchedEffect
        if (!recording && callState != com.rhodes.privatechat.shared.call.CallState.Thinking && callState != com.rhodes.privatechat.shared.call.CallState.AiSpeaking) {
            userSpeechDetected = false
            recording = audio.startRecording()
            transcript = if (recording) "自动监听中..." else "录音启动失败"
            callState = if (recording) com.rhodes.privatechat.shared.call.CallState.Listening else com.rhodes.privatechat.shared.call.CallState.Failed
        }
    }

    LaunchedEffect(inputMode, recording, userSpeechDetected) {
        while (inputMode == SleepInputMode.Auto && recording && userSpeechDetected) {
            if (audio.hasRecordingBeenSilent(1200L)) {
                recording = false
                stopRecordingSafe()?.let { processAudio(it) }
                return@LaunchedEffect
            }
            delay(180)
        }
    }

    LaunchedEffect(inputMode, lastEffectiveUserSpeechAt, callState) {
        while (inputMode == SleepInputMode.Auto) {
            val idleLongEnough = System.currentTimeMillis() - lastEffectiveUserSpeechAt >= sleepInactivityMinutes * 60 * 1000L
            if (idleLongEnough && callState != com.rhodes.privatechat.shared.call.CallState.Thinking && callState != com.rhodes.privatechat.shared.call.CallState.AiSpeaking) {
                if (recording) stopRecordingSafe()
                recording = false
                userSpeechDetected = false
                inputMode = SleepInputMode.Sleeping
                sleptAutomatically = true
                transcript = "你应该已经睡着了。麦克风已关闭。"
                visualState = SleepVisualState.FallingAsleep
                return@LaunchedEffect
            }
            delay(1000L)
        }
    }

    LaunchedEffect(visualState) {
        when (visualState) {
            SleepVisualState.FallingAsleep -> {
                delay(19 * SLEEP_FRAME_DELAY_MS)
                visualState = SleepVisualState.Sleeping
                inputMode = SleepInputMode.Sleeping
                if (sleepSegmentStartedAt == null) sleepSegmentStartedAt = System.currentTimeMillis()
                delay(dimAfterSeconds * 1000L)
                if (visualState == SleepVisualState.Sleeping) { dimmed = true; dimClockVisible = true }
            }
            SleepVisualState.WakingUp -> {
                delay(1800L)
                visualState = SleepVisualState.Idle
            }
            else -> {}
        }
    }

    LaunchedEffect(dimmed) {
        while (dimmed) {
            dimClockVisible = true
            delay(DIM_CLOCK_VISIBLE_MS)
            dimClockVisible = false
            delay((DIM_CLOCK_PERIOD_MS - DIM_CLOCK_VISIBLE_MS).coerceAtLeast(1000L))
        }
    }

    LaunchedEffect(alarmTargetAt) {
        while (alarmTargetAt != null && !hasEnded) {
            val target = alarmTargetAt ?: return@LaunchedEffect
            if (System.currentTimeMillis() >= target && !alarmRinging) {
                triggerWake()
                return@LaunchedEffect
            }
            delay(1000L)
        }
    }

    LaunchedEffect(alarmRinging, wakeAttempt) {
        if (alarmRinging && wakeAttempt in 1..2) {
            delay(if (wakeAttempt == 1) 45_000L else 60_000L)
            if (alarmRinging) {
                alarmRinging = false
                triggerWake()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = visualState == SleepVisualState.Sleeping) {
        if (dimmed) {
            settleRestSegment()
            dimmed = false
            visualState = SleepVisualState.WakingUp
            inputMode = SleepInputMode.Manual
            transcript = "已唤醒。需要的话，可以重新打开自动陪睡。"
        } else {
            visualState = SleepVisualState.WakingUp
            inputMode = SleepInputMode.Manual
            transcript = "已唤醒。需要的话，可以重新打开自动陪睡。"
        }
    }) {
        if (!dimmed) {
            val frames = when (visualState) {
                SleepVisualState.Talking -> sleepTalkingFrames()
                SleepVisualState.FallingAsleep -> sleepToSleepingFrames()
                SleepVisualState.Sleeping -> sleepingIdleFrames()
                SleepVisualState.WakingUp -> sleepToSleepingFrames().asReversed()
                else -> sleepIdleFrames()
            }
            val loop = visualState == SleepVisualState.Idle || visualState == SleepVisualState.Talking || visualState == SleepVisualState.Sleeping
            val frameDelayMs = if (visualState == SleepVisualState.Talking) SLEEP_TALKING_FRAME_DELAY_MS else SLEEP_FRAME_DELAY_MS
            var frameIndex by remember(visualState) { mutableStateOf(0) }
            LaunchedEffect(visualState) {
                frameIndex = 0
                while (true) {
                    delay(frameDelayMs)
                    if (loop) frameIndex = (frameIndex + 1) % frames.size
                    else if (frameIndex < frames.lastIndex) frameIndex++
                    else { visualState = if (visualState == SleepVisualState.FallingAsleep) SleepVisualState.Sleeping else SleepVisualState.Idle; return@LaunchedEffect }
                }
            }
            Image(painter = painterResource(frames[frameIndex.coerceIn(frames.indices)]), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.58f)))))
        if (visualState == SleepVisualState.Sleeping) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))

        if (!dimmed) {
            Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Text(formatClockTime(currentTimeMillis), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                Text("${operator.name} · ${statusText(visualState, inputMode, callState, recording)}", color = Color(0xFFD8E2EE), fontSize = 13.sp)
                alarmTargetAt?.let { Text(formatAlarmCompact(it), color = Color(0xFFFFD28A), fontSize = 12.sp) }
                val now = System.currentTimeMillis()
                when {
                    wakeSummaryUntil > now && wakeSummaryText.isNotBlank() -> Text(wakeSummaryText, color = Color(0xFFD8E2EE), fontSize = 12.sp)
                    alarmRinging -> Text("休息约 ${formatDuration(totalRestMillis)}", color = Color(0xFFD8E2EE), fontSize = 12.sp)
                    visualState == SleepVisualState.Sleeping && sleepSegmentStartedAt != null -> Text("休息约 ${formatDuration(totalRestMillis + (now - sleepSegmentStartedAt!!))}", color = Color(0xFFD8E2EE), fontSize = 12.sp)
                }
            }
        }

        if (visualState == SleepVisualState.Sleeping && !dimmed) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("已入睡", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(lastRestSummary.ifBlank { "麦克风已关闭 · 轻触屏幕唤醒" }, color = Color(0xFFD8E2EE), fontSize = 14.sp)
            }
            SleepButton("退出", Modifier.align(Alignment.BottomEnd).padding(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0x553A4554))) { finish() }
        }

        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            if (dimClockVisible) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(formatClockTime(currentTimeMillis), color = Color.White.copy(alpha = 0.13f), fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
                    alarmTargetAt?.let { Text(formatAlarmCompact(it), color = Color.White.copy(alpha = 0.10f), fontSize = 13.sp) }
                }
            }
        }

        if (alarmRinging && !dimmed) {
            Column(Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatClockTime(currentTimeMillis), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("该醒了", color = Color(0xFFFFD28A), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("休息约 ${formatDuration(totalRestMillis)}", color = Color(0xFFD8E2EE), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SleepButton("我醒了") {
                        alarmRinging = false
                        alarmTargetAt = null
                        wakeSummaryText = "已醒来 · 本次大概休息 ${formatDuration(totalRestMillis)}"
                        wakeSummaryUntil = System.currentTimeMillis() + 8_000L
                        visualState = SleepVisualState.Idle
                        callState = com.rhodes.privatechat.shared.call.CallState.Listening
                    }
                    SleepButton("再睡${snoozeMinutes}分钟") { snooze() }
                }
            }
        }

        if (visualState != SleepVisualState.Sleeping && !dimmed && !alarmRinging) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.40f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("你：$transcript", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
                    Text("${operator.name}：$aiReply", color = Color(0xFFD8E2EE), fontSize = 13.sp, lineHeight = 18.sp)
                    if (recording) {
                        Spacer(Modifier.height(8.dp))
                        AudioLevelMeter(level)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SleepButton(primaryButtonText(visualState, recording, callState), Modifier.weight(1.6f),
                        enabled = visualState != SleepVisualState.FallingAsleep && visualState != SleepVisualState.WakingUp) {
                        when {
                            recording -> { recording = false; stopRecordingSafe()?.let { processAudio(it) } }
                            callState == com.rhodes.privatechat.shared.call.CallState.AiSpeaking -> audio.stopPlayback()
                            else -> MainActivity.requestMicrophonePermission { granted ->
                                if (!granted) Toast.makeText(context, "需要允许麦克风权限才能使用陪睡语音。", Toast.LENGTH_LONG).show()
                                else recording = audio.startRecording().also { if (!it) Toast.makeText(context, "无法开始录音", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }
                    SleepButton(if (inputMode == SleepInputMode.Auto) "关闭自动" else "自动陪睡", Modifier.weight(1f),
                        enabled = visualState != SleepVisualState.Sleeping && visualState != SleepVisualState.FallingAsleep && visualState != SleepVisualState.WakingUp) {
                        if (inputMode == SleepInputMode.Auto) {
                            inputMode = SleepInputMode.Manual
                            if (recording) { stopRecordingSafe(); recording = false }
                        } else { inputMode = SleepInputMode.Auto; lastEffectiveUserSpeechAt = System.currentTimeMillis() }
                    }
                    SleepButton(alarmTargetAt?.let { "闹钟 ${formatClockTime(it)}" } ?: "闹钟", Modifier.weight(1f), enabled = !alarmRinging) { showAlarmDialog = true }
                    SleepButton("设置", Modifier.weight(0.8f)) { showSettingsDialog = true }
                    SleepButton(if (speakerOn) "切听筒" else "扬声器", Modifier.weight(0.9f)) { speakerOn = !speakerOn; audio.setSpeakerEnabled(speakerOn) }
                    SleepButton("退出", Modifier.weight(0.8f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA7D2D2D))) { finish() }
                }
            }
        }
    }

    if (showAlarmDialog) {
        val previewTargetAt = buildNextAlarmMillis(alarmHour, alarmMinute)
        Dialog(
            onDismissRequest = { showAlarmDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.84f).widthIn(min = 300.dp, max = 400.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E24),
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("陪睡闹钟", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(formatAlarmSummary(previewTargetAt), color = Color(0xFFD8E2EE), fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        WheelPicker(label = "小时", values = (0..23).toList(), selected = alarmHour, formatter = { it.toString().padStart(2, '0') }, onSelected = { alarmHour = it }, modifier = Modifier.weight(1f))
                        WheelPicker(label = "分钟", values = (0..59).toList(), selected = alarmMinute, formatter = { it.toString().padStart(2, '0') }, onSelected = { alarmMinute = it }, modifier = Modifier.weight(1f))
                    }
                    Text("陪睡闹钟仅在本次陪睡模式保持打开时生效。", color = Color.Gray, fontSize = 12.sp)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (alarmTargetAt != null) TextButton(onClick = { alarmTargetAt = null; showAlarmDialog = false }) { Text("取消闹钟", color = Color(0xFFFFD28A)) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showAlarmDialog = false }) { Text("关闭", color = Color.Gray) }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { alarmTargetAt = buildNextAlarmMillis(alarmHour, alarmMinute); settings.sleepAlarmHour = alarmHour; settings.sleepAlarmMinute = alarmMinute; wakeAttempt = 0; showAlarmDialog = false }) { Text("确认", color = Color(0xFF5B7CFA)) }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        Dialog(
            onDismissRequest = { showSettingsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.88f).widthIn(min = 300.dp, max = 420.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1E1E24),
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text("陪睡设置", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingChoiceRow("入睡判定", listOf(3, 5, 8, 10), sleepInactivityMinutes, { "${it}分钟" }, onSelect = { sleepInactivityMinutes = it })
                        SettingChoiceRow("入睡后黑屏", listOf(30, 60, 180, 300), dimAfterSeconds, { if (it < 60) "${it}秒" else "${it / 60}分钟" }, onSelect = { dimAfterSeconds = it })
                        SettingChoiceRow("再睡时长", listOf(5, 10, 15), snoozeMinutes, { "${it}分钟" }, onSelect = { snoozeMinutes = it })
                        SettingChoiceRow("叫醒文案", listOf(WakeTextMode.Ai, WakeTextMode.Fixed), wakeTextMode, { if (it == WakeTextMode.Ai) "AI自动" else "固定文案" }, onSelect = { wakeTextMode = it })
                        if (wakeTextMode == WakeTextMode.Fixed) {
                            OutlinedTextField(value = fixedWakeText, onValueChange = { fixedWakeText = it.take(120) }, label = { Text("固定叫醒词") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                            Text("固定文案不会调用 LLM，会直接用于 TTS 叫醒。", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            Text("AI 自动生成会参考角色、当前时间和休息时长生成叫醒词。", color = Color.Gray, fontSize = 12.sp)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("叫醒时使用扬声器", fontWeight = FontWeight.SemiBold)
                                Text("请确认手机媒体音量合适", color = Color.Gray, fontSize = 12.sp)
                            }
                            TextButton(onClick = { speakerOn = !speakerOn; audio.setSpeakerEnabled(speakerOn) }) { Text(if (speakerOn) "开启" else "关闭") }
                        }
                        Text("这些设置会保存为下次陪睡的默认设置。", color = Color.Gray, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { saveSettings(); showSettingsDialog = false }) { Text("完成", color = Color(0xFF5B7CFA)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(containerColor = Color(0xAA253A52)), onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.height(46.dp), colors = colors, shape = RoundedCornerShape(18.dp)) {
        Text(text, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun <T> SettingChoiceRow(title: String, values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEach { value ->
                val active = value == selected
                Button(onClick = { onSelect(value) }, colors = ButtonDefaults.buttonColors(containerColor = if (active) Color(0xFF245C8F) else Color(0xFF2A2A30)), modifier = Modifier.height(36.dp)) {
                    Text(label(value), fontSize = 12.sp, color = if (active) Color(0xFFFFFFFF) else Color(0xFFD8E2EE))
                }
            }
        }
    }
}

@Composable
private fun AudioLevelMeter(level: Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(18) { index ->
            val active = index / 18f <= level
            Box(Modifier.width(5.dp).height((8 + index % 6 * 4).dp).clip(RoundedCornerShape(99.dp)).background(if (active) Color(0xFF66D19E) else Color(0x335D6B7A)))
        }
    }
}

private fun statusText(visualState: SleepVisualState, inputMode: SleepInputMode, callState: com.rhodes.privatechat.shared.call.CallState, recording: Boolean): String = when {
    visualState == SleepVisualState.Sleeping -> "已入睡 · 麦克风关闭"
    visualState == SleepVisualState.FallingAsleep -> "正在入睡"
    visualState == SleepVisualState.WakingUp -> "正在唤醒"
    callState == com.rhodes.privatechat.shared.call.CallState.AiSpeaking -> "正在说话"
    recording -> if (inputMode == SleepInputMode.Auto) "自动监听中" else "录音中"
    inputMode == SleepInputMode.Auto -> "自动陪睡中"
    else -> "按钮发言模式"
}

private fun primaryButtonText(visualState: SleepVisualState, recording: Boolean, callState: com.rhodes.privatechat.shared.call.CallState): String = when {
    recording -> "结束并发送"
    callState == com.rhodes.privatechat.shared.call.CallState.AiSpeaking -> "打断"
    else -> "开始说话"
}

private fun formatClockTime(timeMillis: Long): String {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).apply { timeInMillis = timeMillis }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val time = SimpleDateFormat("HH:mm", Locale.CHINA).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(timeMillis))
    val period = when (hour) {
        in 5..7 -> "清晨"; in 8..11 -> "上午"; in 12..13 -> "中午"; in 14..17 -> "下午"; in 18..21 -> "晚上"; in 22..23 -> "深夜"; else -> "凌晨"
    }
    return "$time（$period）"
}
private fun formatAlarmCompact(targetAtMillis: Long): String = "闹钟 ${formatClockTime(targetAtMillis)} · ${formatRemainingTime(targetAtMillis)}"
private fun formatAlarmSummary(targetAtMillis: Long): String {
    val day = if (isSameDay(targetAtMillis, System.currentTimeMillis())) "今天" else "明天"
    return "$day ${formatClockTime(targetAtMillis)} 叫醒 · ${formatRemainingTime(targetAtMillis)}"
}
private fun formatRemainingTime(targetAtMillis: Long): String {
    val minutes = ((targetAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "还有${hours}小时${restMinutes}分钟"
        hours > 0 -> "还有${hours}小时"
        else -> "还有${restMinutes}分钟"
    }
}
private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
private fun buildNextAlarmMillis(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        // A just-selected future minute should still mean today, not silently jump a day.
        if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
    }.timeInMillis
}
private fun formatDuration(durationMillis: Long): String {
    val minutes = (durationMillis / 60_000L).coerceAtLeast(0L)
    if (minutes == 0L) return "不到1分钟"
    val hours = minutes / 60
    val restMinutes = minutes % 60
    return when {
        hours > 0 && restMinutes > 0 -> "${hours}小时${restMinutes}分钟"
        hours > 0 -> "${hours}小时"
        else -> "${restMinutes}分钟"
    }
}

@Composable
private fun WheelPicker(
    label: String,
    values: List<Int>,
    selected: Int,
    formatter: (Int) -> String,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loopCount = 1000
    val centerLoop = loopCount / 2
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    // The list shows exactly three rows; the second visible row is the selected value.
    val initialIndex = centerLoop * values.size + selectedIndex - 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceAtLeast(0))
    var centeredValue by remember { mutableIntStateOf(selected) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = (listState.firstVisibleItemIndex + 1 + if (listState.firstVisibleItemScrollOffset >= 22) 1 else 0)
                .coerceIn(1, loopCount * values.size - 2)
            listState.animateScrollToItem(centerIndex - 1)
            centeredValue = values[centerIndex % values.size]
            onSelected(centeredValue)
        }
    }
    LaunchedEffect(selected) {
        val index = values.indexOf(selected)
        if (index >= 0 && !listState.isScrollInProgress) {
            val target = centerLoop * values.size + index - 1
            if (kotlin.math.abs(target - listState.firstVisibleItemIndex) > values.size) listState.scrollToItem(target)
        }
        centeredValue = selected
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color(0xFF5D6875))
        Box(modifier = Modifier.height(132.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // The centered row is the actual selected time, so it owns the visual selection state.
            Box(modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xAA245C8F)))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                items(loopCount * values.size) { rawIndex ->
                    val value = values[rawIndex % values.size]
                    Box(modifier = Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val active = rawIndex == listState.firstVisibleItemIndex + 1 && value == centeredValue
                        Text(formatter(value), fontSize = if (active) 26.sp else 18.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, color = if (active) Color.White else Color(0xFF6A6A70))
                    }
                }
            }
        }
    }
}

private fun sleepIdleFrames() = listOf(R.drawable.sleep_idle_001, R.drawable.sleep_idle_002, R.drawable.sleep_idle_003, R.drawable.sleep_idle_004, R.drawable.sleep_idle_005, R.drawable.sleep_idle_006, R.drawable.sleep_idle_007, R.drawable.sleep_idle_008, R.drawable.sleep_idle_009, R.drawable.sleep_idle_010, R.drawable.sleep_idle_011, R.drawable.sleep_idle_012, R.drawable.sleep_idle_013, R.drawable.sleep_idle_014)
private fun sleepTalkingFrames() = listOf(R.drawable.sleep_talking_001, R.drawable.sleep_talking_002, R.drawable.sleep_talking_003, R.drawable.sleep_talking_004, R.drawable.sleep_talking_005, R.drawable.sleep_talking_006, R.drawable.sleep_talking_007, R.drawable.sleep_talking_008, R.drawable.sleep_talking_009, R.drawable.sleep_talking_010, R.drawable.sleep_talking_011, R.drawable.sleep_talking_012, R.drawable.sleep_talking_013, R.drawable.sleep_talking_014, R.drawable.sleep_talking_015, R.drawable.sleep_talking_016, R.drawable.sleep_talking_017, R.drawable.sleep_talking_018, R.drawable.sleep_talking_019, R.drawable.sleep_talking_020)
private fun sleepingIdleFrames() = listOf(R.drawable.sleeping_idle_001, R.drawable.sleeping_idle_002, R.drawable.sleeping_idle_003, R.drawable.sleeping_idle_004, R.drawable.sleeping_idle_005, R.drawable.sleeping_idle_006, R.drawable.sleeping_idle_007, R.drawable.sleeping_idle_008, R.drawable.sleeping_idle_009, R.drawable.sleeping_idle_010, R.drawable.sleeping_idle_011, R.drawable.sleeping_idle_012, R.drawable.sleeping_idle_013, R.drawable.sleeping_idle_014, R.drawable.sleeping_idle_015, R.drawable.sleeping_idle_016, R.drawable.sleeping_idle_017)
private fun sleepToSleepingFrames() = listOf(R.drawable.sleep_to_sleeping_001, R.drawable.sleep_to_sleeping_002, R.drawable.sleep_to_sleeping_003, R.drawable.sleep_to_sleeping_004, R.drawable.sleep_to_sleeping_005, R.drawable.sleep_to_sleeping_006, R.drawable.sleep_to_sleeping_007, R.drawable.sleep_to_sleeping_008, R.drawable.sleep_to_sleeping_009, R.drawable.sleep_to_sleeping_010, R.drawable.sleep_to_sleeping_011, R.drawable.sleep_to_sleeping_012, R.drawable.sleep_to_sleeping_013, R.drawable.sleep_to_sleeping_014, R.drawable.sleep_to_sleeping_015, R.drawable.sleep_to_sleeping_016, R.drawable.sleep_to_sleeping_017, R.drawable.sleep_to_sleeping_018, R.drawable.sleep_to_sleeping_019)
