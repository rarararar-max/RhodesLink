package com.rhodes.privatechat.ui.sleep

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.rhodes.privatechat.audio.RecordedAudio
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.shared.call.CallState
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.voice.AsrGateway
import com.rhodes.privatechat.shared.voice.AsrRequest
import com.rhodes.privatechat.shared.voice.TtsGateway
import com.rhodes.privatechat.shared.voice.TtsRequest
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.SleepPrompts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val SLEEP_FRAME_DELAY_MS = 300L
private const val SLEEP_TALKING_FRAME_DELAY_MS = 200L

private enum class SleepVisualState { Idle, Talking, FallingAsleep, Sleeping, WakingUp }
private enum class SleepInputMode { Manual, Auto, Sleeping }

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
    var callState by remember { mutableStateOf(CallState.Connected) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("轻声说话，或者打开自动陪睡。") }
    var aiReply by rememberSaveable { mutableStateOf("我在这里。") }
    var currentTimeText by remember { mutableStateOf(formatClockTime(System.currentTimeMillis())) }
    var dimmed by rememberSaveable { mutableStateOf(false) }
    var sleepStartedAt by rememberSaveable { mutableStateOf(0L) }
    var totalRestMillis by rememberSaveable { mutableStateOf(0L) }
    var alarmTargetAt by rememberSaveable { mutableStateOf(0L) }
    var alarmHour by rememberSaveable { mutableStateOf(settings.sleepAlarmHour) }
    var alarmMinute by rememberSaveable { mutableStateOf(settings.sleepAlarmMinute) }
    var alarmRinging by rememberSaveable { mutableStateOf(false) }
    var showAlarmDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var fixedWakeText by rememberSaveable { mutableStateOf(settings.sleepFixedWakeText) }
    var inactivityMinutes by rememberSaveable { mutableStateOf(settings.sleepInactivityMinutes) }
    var dimAfterSeconds by rememberSaveable { mutableStateOf(settings.sleepDimAfterSeconds) }
    var snoozeMinutes by rememberSaveable { mutableStateOf(settings.sleepSnoozeMinutes) }
    var lastEffectiveUserSpeechAt by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var wakeAttempt by remember { mutableStateOf(0) }
    var hasEnded by rememberSaveable { mutableStateOf(false) }
    var userSpeechDetected by rememberSaveable { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }

    DisposableEffect(audio) {
        onDispose { audio.release() }
    }

    fun saveSettings() {
        settings.sleepAlarmHour = alarmHour
        settings.sleepAlarmMinute = alarmMinute
        settings.sleepFixedWakeText = fixedWakeText
        settings.sleepInactivityMinutes = inactivityMinutes
        settings.sleepDimAfterSeconds = dimAfterSeconds
        settings.sleepSnoozeMinutes = snoozeMinutes
    }

    fun stopRecordingSafe(): RecordedAudio? = runCatching { audio.stopRecording() }.getOrNull()
    fun finish() {
        if (hasEnded) return
        hasEnded = true
        if (recording) stopRecordingSafe()
        recording = false
        audio.stopPlayback()
        audio.setSpeakerEnabled(false)
        saveSettings()
        onBack()
    }

    suspend fun speak(text: String) {
        val tts: TtsGateway = org.koin.core.context.GlobalContext.get().get()
        callState = CallState.AiSpeaking
        visualState = SleepVisualState.Talking
        val bytes = tts.synthesize(TtsRequest(text = text, voiceId = operator.voiceName, speed = operator.voiceSpeed.toDoubleOrNull() ?: 1.0)).audioBytes
        val file = bytes?.let { audio.saveTtsAudio(it) }
        if (file != null) audio.play(file.path) {
            callState = CallState.Listening
            visualState = SleepVisualState.Idle
        } else {
            callState = CallState.Listening
            visualState = SleepVisualState.Idle
        }
    }

    fun processAudio(recorded: RecordedAudio) {
        scope.launch {
            try {
                callState = CallState.Thinking
                transcript = "正在识别..."
                val asr: AsrGateway = org.koin.core.context.GlobalContext.get().get()
                val text = asr.transcribe(AsrRequest(audio.readPcmFromWav(recorded.path))).text.trim()
                if (text.isBlank()) {
                    transcript = "未能识别，请重试"
                    callState = CallState.Listening
                    return@launch
                }
                transcript = text
                lastEffectiveUserSpeechAt = System.currentTimeMillis()
                turns += "用户" to text
                val prompt = buildString {
                    append(operator.privatePrompt.ifBlank { operator.description })
                    append("\n\n")
                    append(SleepPrompts.CALL)
                    append("\n用户刚刚轻声说：")
                    append(text)
                }
                val reply = viewModel.chatViewModel.sharedChatForFeature(listOf(AiMessage("system", prompt))).trim().take(240).ifBlank { "我在。" }
                aiReply = reply
                turns += operator.name to reply
                speak(reply)
            } catch (e: Exception) {
                callState = CallState.Failed
                Toast.makeText(context, "陪睡语音失败：${e.message?.take(40) ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun triggerWake() {
        if (alarmRinging || hasEnded) return
        if (sleepStartedAt > 0L) totalRestMillis += System.currentTimeMillis() - sleepStartedAt
        sleepStartedAt = 0L
        dimmed = false
        alarmRinging = true
        wakeAttempt += 1
        visualState = SleepVisualState.WakingUp
        inputMode = SleepInputMode.Manual
        transcript = "叫醒时间到了。"
        scope.launch {
            val wakeText = if (wakeAttempt <= 2) {
                try {
                    val prompt = buildString {
                        append(operator.privatePrompt.ifBlank { operator.description })
                        append("\n\n")
                        append(SleepPrompts.WAKE)
                        append("\n当前时间：${formatClockTime(System.currentTimeMillis())}")
                        append("\n用户已休息：${formatDuration(totalRestMillis)}")
                        if (wakeAttempt > 1) append("\n提示：这是第${wakeAttempt}次叫醒，可以稍微更直接一点。")
                        append("\n请用角色身份输出一句适合 TTS 播放的叫醒台词：")
                    }
                    viewModel.chatViewModel.sharedChatForFeature(listOf(AiMessage("system", prompt))).trim().take(120)
                        .ifBlank { fixedWakeText }
                } catch (_: Exception) { fixedWakeText }
            } else fixedWakeText
            val finalWake = wakeText.take(120)
            aiReply = finalWake
            turns += operator.name to finalWake
            speak(finalWake)
        }
    }

    fun snooze() {
        alarmRinging = false
        sleepStartedAt = System.currentTimeMillis()
        alarmTargetAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        visualState = SleepVisualState.Sleeping
        inputMode = SleepInputMode.Sleeping
        transcript = "再睡${snoozeMinutes}分钟。"
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
            currentTimeText = formatClockTime(System.currentTimeMillis())
            val target = alarmTargetAt
            if (target > 0L && System.currentTimeMillis() >= target && !alarmRinging) triggerWake()
            delay(1000L)
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            level = audio.getCurrentRecordingLevel()
            if (level > 0.12f) userSpeechDetected = true
            delay(100)
        }
        level = 0f
    }

    LaunchedEffect(inputMode, recording, callState, userSpeechDetected) {
        while (inputMode == SleepInputMode.Auto) {
            if (!recording && callState != CallState.Thinking && callState != CallState.AiSpeaking) {
                userSpeechDetected = false
                recording = audio.startRecording()
                transcript = if (recording) "自动监听中..." else "录音启动失败"
            }
            if (recording && userSpeechDetected && audio.hasRecordingBeenSilent(1200L)) {
                recording = false
                stopRecordingSafe()?.let { processAudio(it) }
            }
            delay(180)
        }
    }

    LaunchedEffect(inputMode, lastEffectiveUserSpeechAt, callState) {
        while (inputMode == SleepInputMode.Auto) {
            val idleLongEnough = System.currentTimeMillis() - lastEffectiveUserSpeechAt >= inactivityMinutes * 60 * 1000L
            if (idleLongEnough && callState != CallState.Thinking && callState != CallState.AiSpeaking) {
                if (recording) stopRecordingSafe()
                recording = false
                userSpeechDetected = false
                inputMode = SleepInputMode.Sleeping
                transcript = "你应该已经睡着了。"
                visualState = SleepVisualState.FallingAsleep
            }
            delay(1000L)
        }
    }

    LaunchedEffect(visualState) {
        if (visualState == SleepVisualState.FallingAsleep) {
            delay(19 * SLEEP_FRAME_DELAY_MS)
            visualState = SleepVisualState.Sleeping
            inputMode = SleepInputMode.Sleeping
            if (sleepStartedAt == 0L) sleepStartedAt = System.currentTimeMillis()
            delay(dimAfterSeconds * 1000L)
            if (visualState == SleepVisualState.Sleeping) dimmed = true
        } else if (visualState == SleepVisualState.WakingUp) {
            delay(1800L)
            visualState = SleepVisualState.Idle
        }
    }

    LaunchedEffect(alarmRinging, wakeAttempt) {
        if (alarmRinging && !hasEnded) {
            delay(300_000L)
            if (alarmRinging && wakeAttempt < 3 && !hasEnded) {
                alarmRinging = false
                triggerWake()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable(enabled = dimmed || visualState == SleepVisualState.Sleeping) {
        dimmed = false
        if (visualState == SleepVisualState.Sleeping) {
            visualState = SleepVisualState.WakingUp
            inputMode = SleepInputMode.Manual
        }
    }) {
        if (!dimmed) SleepFrameAnimation(visualState, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent, Color.Black.copy(alpha = 0.62f)))))

        if (dimmed) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Text(currentTimeText, color = Color.White.copy(alpha = 0.14f), fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            }
            return@Box
        }

        Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(24.dp)) {
            Text(currentTimeText, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("${operator.name} · ${statusText(visualState, inputMode, callState, recording)}", color = Color(0xFFD8E2EE), fontSize = 13.sp)
            if (alarmTargetAt > 0L) Text("闹钟 ${formatClockTime(alarmTargetAt)}", color = Color(0xFFFFD28A), fontSize = 12.sp)
        }

        if (visualState == SleepVisualState.Sleeping) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已入睡", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("麦克风已关闭 · 轻触屏幕唤醒", color = Color(0xFFD8E2EE), fontSize = 14.sp)
            }
        } else {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(aiReply, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(transcript, color = Color(0xFFD8E2EE), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SleepButton(primaryButtonText(visualState, recording, callState), Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA2563EB))) {
                        when {
                            visualState == SleepVisualState.Sleeping -> visualState = SleepVisualState.WakingUp
                            recording -> { recording = false; stopRecordingSafe()?.let { processAudio(it) } }
                            callState == CallState.AiSpeaking -> audio.stopPlayback()
                            else -> recording = audio.startRecording().also { if (!it) Toast.makeText(context, "无法开始录音", Toast.LENGTH_SHORT).show() }
                        }
                    }
                    SleepButton(if (inputMode == SleepInputMode.Auto) "关闭自动" else "自动陪睡", Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA334155))) {
                        inputMode = if (inputMode == SleepInputMode.Auto) SleepInputMode.Manual else SleepInputMode.Auto
                    }
                    SleepButton("入睡", Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA4C1D95))) {
                        if (recording) stopRecordingSafe()
                        recording = false
                        visualState = SleepVisualState.FallingAsleep
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SleepButton("闹钟", Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA92400E))) { showAlarmDialog = true }
                    SleepButton("设置", Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA334155))) { showSettingsDialog = true }
                    SleepButton("退出", Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xAA7F1D1D))) { finish() }
                }
            }
        }
    }

    if (showAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showAlarmDialog = false },
            title = { Text("陪睡闹钟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(alarmHour.toString(), { alarmHour = it.toIntOrNull()?.coerceIn(0, 23) ?: alarmHour }, label = { Text("小时") })
                    OutlinedTextField(alarmMinute.toString(), { alarmMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: alarmMinute }, label = { Text("分钟") })
                }
            },
            confirmButton = { TextButton(onClick = { alarmTargetAt = buildNextAlarmMillis(alarmHour, alarmMinute); settings.sleepAlarmHour = alarmHour; settings.sleepAlarmMinute = alarmMinute; showAlarmDialog = false }) { Text("设置") } },
            dismissButton = { TextButton(onClick = { alarmTargetAt = 0L; showAlarmDialog = false }) { Text("取消闹钟") } }
        )
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("陪睡设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("叫醒词", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(fixedWakeText, { fixedWakeText = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Text("自动入睡（分钟）", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(inactivityMinutes.toString(), { inactivityMinutes = it.toIntOrNull()?.coerceIn(1, 60) ?: inactivityMinutes }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("黑屏延迟（秒）", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(dimAfterSeconds.toString(), { dimAfterSeconds = it.toIntOrNull()?.coerceIn(10, 600) ?: dimAfterSeconds }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("再睡时长（分钟）", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(snoozeMinutes.toString(), { snoozeMinutes = it.toIntOrNull()?.coerceIn(1, 30) ?: snoozeMinutes }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { showSettingsDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun SleepFrameAnimation(visualState: SleepVisualState, modifier: Modifier = Modifier) {
    val frames = when (visualState) {
        SleepVisualState.Talking -> sleepTalkingFrames()
        SleepVisualState.FallingAsleep -> sleepToSleepingFrames()
        SleepVisualState.Sleeping -> sleepingIdleFrames()
        else -> sleepIdleFrames()
    }
    var index by remember(visualState) { mutableStateOf(0) }
    LaunchedEffect(visualState, frames.size) {
        index = 0
        while (true) {
            delay(if (visualState == SleepVisualState.Talking) SLEEP_TALKING_FRAME_DELAY_MS else SLEEP_FRAME_DELAY_MS)
            index = if (visualState == SleepVisualState.FallingAsleep && index >= frames.lastIndex) index else (index + 1) % frames.size
        }
    }
    Image(painter = painterResource(frames[index.coerceIn(frames.indices)]), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
}

@Composable
private fun SleepButton(text: String, modifier: Modifier = Modifier, colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(containerColor = Color(0xAA2563EB)), onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(48.dp), colors = colors, shape = RoundedCornerShape(18.dp)) { Text(text, color = Color.White) }
}

private fun statusText(visualState: SleepVisualState, inputMode: SleepInputMode, callState: CallState, recording: Boolean): String = when {
    visualState == SleepVisualState.Sleeping -> "已入睡 · 麦克风关闭"
    visualState == SleepVisualState.FallingAsleep -> "正在入睡"
    visualState == SleepVisualState.WakingUp -> "正在唤醒"
    callState == CallState.AiSpeaking -> "正在说话"
    recording -> if (inputMode == SleepInputMode.Auto) "自动监听中" else "录音中"
    inputMode == SleepInputMode.Auto -> "自动陪睡中"
    else -> "按钮发言模式"
}

private fun primaryButtonText(visualState: SleepVisualState, recording: Boolean, callState: CallState): String = when {
    visualState == SleepVisualState.Sleeping -> "唤醒"
    recording -> "结束并发送"
    callState == CallState.AiSpeaking -> "打断"
    else -> "开始说话"
}

private fun formatClockTime(timeMillis: Long): String = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timeMillis))

private fun buildNextAlarmMillis(hour: Int, minute: Int): Long {
    val now = Calendar.getInstance()
    return Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now.timeInMillis + 60_000L) add(Calendar.DAY_OF_YEAR, 1)
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

private fun sleepIdleFrames() = listOf(R.drawable.sleep_idle_001, R.drawable.sleep_idle_002, R.drawable.sleep_idle_003, R.drawable.sleep_idle_004, R.drawable.sleep_idle_005, R.drawable.sleep_idle_006, R.drawable.sleep_idle_007, R.drawable.sleep_idle_008, R.drawable.sleep_idle_009, R.drawable.sleep_idle_010, R.drawable.sleep_idle_011, R.drawable.sleep_idle_012, R.drawable.sleep_idle_013, R.drawable.sleep_idle_014)
private fun sleepTalkingFrames() = listOf(R.drawable.sleep_talking_001, R.drawable.sleep_talking_002, R.drawable.sleep_talking_003, R.drawable.sleep_talking_004, R.drawable.sleep_talking_005, R.drawable.sleep_talking_006)
private fun sleepingIdleFrames() = listOf(R.drawable.sleeping_idle_001, R.drawable.sleeping_idle_002, R.drawable.sleeping_idle_003, R.drawable.sleeping_idle_004, R.drawable.sleeping_idle_005, R.drawable.sleeping_idle_006, R.drawable.sleeping_idle_007, R.drawable.sleeping_idle_008, R.drawable.sleeping_idle_009, R.drawable.sleeping_idle_010, R.drawable.sleeping_idle_011, R.drawable.sleeping_idle_012, R.drawable.sleeping_idle_013, R.drawable.sleeping_idle_014, R.drawable.sleeping_idle_015, R.drawable.sleeping_idle_016, R.drawable.sleeping_idle_017)
private fun sleepToSleepingFrames() = listOf(R.drawable.sleep_to_sleeping_001, R.drawable.sleep_to_sleeping_002, R.drawable.sleep_to_sleeping_003, R.drawable.sleep_to_sleeping_004, R.drawable.sleep_to_sleeping_005, R.drawable.sleep_to_sleeping_006, R.drawable.sleep_to_sleeping_007, R.drawable.sleep_to_sleeping_008, R.drawable.sleep_to_sleeping_009, R.drawable.sleep_to_sleeping_010, R.drawable.sleep_to_sleeping_011, R.drawable.sleep_to_sleeping_012, R.drawable.sleep_to_sleeping_013, R.drawable.sleep_to_sleeping_014, R.drawable.sleep_to_sleeping_015, R.drawable.sleep_to_sleeping_016, R.drawable.sleep_to_sleeping_017, R.drawable.sleep_to_sleeping_018, R.drawable.sleep_to_sleeping_019)
