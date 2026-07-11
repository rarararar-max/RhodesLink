package com.rhodes.privatechat.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
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

    Column(Modifier.fillMaxSize().background(BG).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(Surface).padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = TextPrimary) }
            Text("与${operator.name}通话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("音色ID：${operator.voiceName}", color = TextSecondary)
            Text("识别：${transcript.ifBlank { "等待你说话" }}", color = TextPrimary, lineHeight = 22.sp)
            Text("回复：${reply.ifBlank { "等待回复" }}", color = TextPrimary, lineHeight = 22.sp)
            Spacer(Modifier.height(8.dp))
            Button(enabled = !busy, onClick = {
                if (!recording) {
                    recording = audio.startRecording()
                    if (!recording) Toast.makeText(context, "无法开始录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
                } else {
                    recording = false
                    val recorded = audio.stopRecording()
                    if (recorded == null) {
                        Toast.makeText(context, "录音为空", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        busy = true
                        try {
                            val asr: AsrGateway = org.koin.core.context.GlobalContext.get().get()
                            val tts: TtsGateway = org.koin.core.context.GlobalContext.get().get()
                            val text = asr.transcribe(AsrRequest(audio.readPcmFromWav(recorded.path))).text
                            transcript = text
                            if (text.isBlank()) return@launch
                            val prompt = "你是${operator.name}。${operator.privatePrompt.ifBlank { operator.description }}\n当前是语音通话，请用1到3句自然口语回复用户。用户说：$text"
                            val ai = viewModel.chatViewModel.sharedChatForFeature(listOf(AiMessage("system", prompt)))
                            reply = ai.take(300)
                            val bytes = tts.synthesize(TtsRequest(text = reply, voiceId = operator.voiceName, speed = operator.voiceSpeed.toDoubleOrNull() ?: 1.0)).audioBytes
                            val audioFile = bytes?.let { audio.saveTtsAudio(it) }
                            if (audioFile != null) audio.play(audioFile.path)
                        } catch (e: Exception) {
                            Toast.makeText(context, "通话失败：${e.message?.take(40) ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        } finally {
                            busy = false
                        }
                    }
                }
            }) { Text(if (recording) "停止并发送" else "开始说话") }
            Text("这是一版语音通话 MVP：录音 -> ASR -> AI -> TTS 播放。", fontSize = 12.sp, color = TextSecondary)
        }
    }
}
