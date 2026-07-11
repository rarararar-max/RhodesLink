package com.rhodes.privatechat.ui.call

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rhodes.privatechat.audio.LocalAudioController
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.voice.AsrGateway
import com.rhodes.privatechat.shared.voice.AsrRequest
import com.rhodes.privatechat.shared.voice.TtsGateway
import com.rhodes.privatechat.shared.voice.TtsRequest
import com.rhodes.privatechat.ui.theme.BG
import com.rhodes.privatechat.ui.theme.Primary
import com.rhodes.privatechat.ui.theme.Surface
import com.rhodes.privatechat.ui.theme.TextPrimary
import com.rhodes.privatechat.ui.theme.TextSecondary
import com.rhodes.privatechat.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VoiceCallScreen(viewModel: MainViewModel, operator: Operator, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audio = remember { LocalAudioController(context) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var transcript by rememberSaveable { mutableStateOf("") }
    var reply by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var autoMode by rememberSaveable { mutableStateOf(false) }
    var userSpeechDetected by rememberSaveable { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var turns by rememberSaveable { mutableStateOf(listOf<Pair<String, String>>()) }

    suspend fun synthesizeAndPlay(tts: TtsGateway, text: String) {
        val parts = splitTtsSentences(text)
        for (part in parts) {
            val bytes = tts.synthesize(TtsRequest(text = part, voiceId = operator.voiceName, speed = operator.voiceSpeed.toDoubleOrNull() ?: 1.0)).audioBytes
            val audioFile = bytes?.let { audio.saveTtsAudio(it) }
            if (audioFile != null) {
                var done = false
                audio.play(audioFile.path) { done = true }
                while (!done) delay(120)
            }
        }
    }

    fun processRecording(path: String) {
        scope.launch {
            busy = true
            try {
                val asr: AsrGateway = org.koin.core.context.GlobalContext.get().get()
                val tts: TtsGateway = org.koin.core.context.GlobalContext.get().get()
                val text = asr.transcribe(AsrRequest(audio.readPcmFromWav(path))).text.trim()
                transcript = text
                if (text.isBlank()) return@launch
                turns = turns + ("用户" to text)
                val recent = turns.takeLast(8).joinToString("\n") { "${it.first}：${it.second}" }
                val prompt = "你是${operator.name}。${operator.privatePrompt.ifBlank { operator.description }}\n当前是语音通话，请用1到3句自然口语回复用户。\n最近通话：\n$recent\n用户刚说：$text"
                val ai = viewModel.chatViewModel.sharedChatForFeature(listOf(AiMessage("system", prompt))).trim().take(500)
                reply = ai
                turns = turns + (operator.name to ai)
                synthesizeAndPlay(tts, ai)
            } catch (e: Exception) {
                Toast.makeText(context, "通话失败：${e.message?.take(40) ?: "未知错误"}", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
                userSpeechDetected = false
            }
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

    LaunchedEffect(autoMode) {
        while (autoMode) {
            if (!recording && !busy) {
                userSpeechDetected = false
                recording = audio.startRecording()
                if (!recording) {
                    Toast.makeText(context, "无法开始录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
                    autoMode = false
                }
            }
            if (recording && userSpeechDetected && audio.hasRecordingBeenSilent(1200L)) {
                recording = false
                audio.stopRecording()?.path?.let { processRecording(it) }
            }
            delay(180)
        }
    }

    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(8.dp)) {
            IconButton(onClick = {
                if (recording) { recording = false; audio.stopRecording() }; audio.stopPlayback(); onBack()
            }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("与${operator.name}通话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("音色ID：${operator.voiceName}", color = TextSecondary, fontSize = 13.sp)
            AudioLevelMeter(level)
            Text("识别：${transcript.ifBlank { "等待你说话" }}", color = TextPrimary, lineHeight = 20.sp, fontSize = 14.sp)
            Text("回复：${reply.ifBlank { "等待回复" }}", color = Primary, lineHeight = 20.sp, fontSize = 14.sp)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(turns) { turn ->
                    Text("${turn.first}：${turn.second}", color = if (turn.first == "用户") TextPrimary else Primary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!autoMode) {
                    Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                        if (!recording) {
                            recording = audio.startRecording()
                            if (!recording) Toast.makeText(context, "无法开始录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
                        } else {
                            recording = false
                            audio.stopRecording()?.path?.let { processRecording(it) }
                        }
                    }) { Text(if (recording) "停止并发送" else "开始说话") }
                }
                Button(enabled = !busy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (autoMode) Color(0xAA334155) else Primary), onClick = {
                    autoMode = !autoMode
                    if (!autoMode && recording) { recording = false; audio.stopRecording() }
                }) { Text(if (autoMode) "关闭自动" else "自动通话") }
            }
        }
    }
}

@Composable
private fun AudioLevelMeter(level: Float) {
    val count = 6
    val active = (level * count).toInt().coerceIn(0, count)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (i in 0 until count) {
            Box(Modifier.size(width = 6.dp, height = if (i <= active) (8 + i * 4).dp else 8.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= active) Primary else Color.Gray.copy(alpha = 0.3f)))
        }
    }
}

private fun splitTtsSentences(text: String): List<String> {
    val segments = text.split(Regex("(?<=[。！？.!?\\n])")).map { it.trim() }.filter { it.isNotBlank() }
    return if (segments.isEmpty()) listOf(text) else segments
}
