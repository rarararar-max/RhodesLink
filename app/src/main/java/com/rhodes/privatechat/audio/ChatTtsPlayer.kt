package com.rhodes.privatechat.audio

import android.content.Context
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.voice.TtsRequest
import com.rhodes.privatechat.shared.voice.createTtsGateway
import com.rhodes.privatechat.shared.voice.prepareTtsSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

data class ChatSpeech(val text: String, val voiceId: String, val speed: Double = 1.0)

/** Plays chat dialogue in order; automatic replies append while manual replay replaces the queue. */
class ChatTtsPlayer(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val onError: (String) -> Unit
) {
    private val audio = LocalAudioController(context)
    private val lock = Any()
    private val pending = ArrayDeque<ChatSpeech>()
    private var worker: Job? = null
    private var generation = 0L

    /** Interrupts any active playback and starts with the selected message. */
    fun play(items: List<ChatSpeech>) {
        synchronized(lock) {
            generation++
            pending.clear()
            worker?.cancel()
            worker = null
        }
        audio.stopPlayback()
        enqueue(items)
    }

    /** Adds newly received messages after the current sentence/queue. */
    fun enqueue(items: List<ChatSpeech>) {
        val queue = items.mapNotNull { item ->
            prepareTtsSpeech(item.text, 10_000, "").takeIf { it.isNotBlank() }?.let { item.copy(text = it) }
        }
        if (queue.isEmpty()) return
        synchronized(lock) {
            pending.addAll(queue)
            startWorkerLocked()
        }
    }

    fun stop() {
        synchronized(lock) {
            generation++
            pending.clear()
            worker?.cancel()
            worker = null
        }
        audio.stopPlayback()
    }

    private fun startWorkerLocked() {
        if (worker?.isActive == true) return
        val workerGeneration = generation
        worker = scope.launch {
            val gateway = createTtsGateway(settings.ttsBaseUrl, settings.ttsApiKey.ifBlank { settings.apiKey }, settings.ttsModelName, settings.ttsProvider)
            try {
                while (true) {
                    val speech = synchronized(lock) {
                        if (workerGeneration != generation || pending.isEmpty()) null else pending.removeFirst()
                    } ?: break
                    try {
                        playSpeech(gateway, speech)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        onError("语音播放失败：${e.message?.take(40) ?: "未知错误"}")
                    }
                }
            } finally {
                synchronized(lock) {
                    if (workerGeneration == generation) {
                        worker = null
                        if (pending.isNotEmpty()) startWorkerLocked()
                    }
                }
            }
        }
    }

    private suspend fun playSpeech(gateway: com.rhodes.privatechat.shared.voice.TtsGateway, speech: ChatSpeech) = coroutineScope {
        val parts = splitSentences(speech.text)
        var current = withTimeout(45_000) {
            gateway.synthesize(TtsRequest(text = parts.first(), voiceId = speech.voiceId, speed = speech.speed))
        }
        for (index in parts.indices) {
            val next = if (index + 1 < parts.size) async {
                withTimeout(45_000) {
                    gateway.synthesize(TtsRequest(text = parts[index + 1], voiceId = speech.voiceId, speed = speech.speed))
                }
            } else null
            val bytes = current.audioBytes ?: error("TTS 未返回音频")
            val file = audio.saveTtsAudio(bytes) ?: error("无法保存 TTS 音频")
            var complete = false
            var success = false
            audio.play(file.path) { played -> complete = true; success = played }
            var waited = 0L
            while (!complete && waited < 90_000L) { delay(100); waited += 100 }
            audio.deleteAudio(file.path)
            if (!success) error("音频播放失败")
            if (next == null) break
            current = next.await()
        }
    }

    fun release() {
        stop()
        audio.release()
    }
}

private fun splitSentences(text: String): List<String> {
    val result = mutableListOf<String>()
    val buffer = StringBuilder()
    text.forEach { ch ->
        buffer.append(ch)
        if (ch in "。！？!?；;\n" || buffer.length >= 120) {
            buffer.toString().trim().takeIf { it.isNotBlank() }?.let(result::add)
            buffer.clear()
        }
    }
    buffer.toString().trim().takeIf { it.isNotBlank() }?.let(result::add)
    return result.ifEmpty { listOf(text) }
}
