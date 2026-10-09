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
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatSpeech(val text: String, val voiceId: String, val speed: Double = 1.0, val volume: Float = 1f, val messageKey: String = "")

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
    private val _speakingMessageKey = MutableStateFlow("")
    val speakingMessageKey: StateFlow<String> = _speakingMessageKey.asStateFlow()

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
        _speakingMessageKey.value = ""
    }

    private fun startWorkerLocked() {
        if (worker?.isActive == true) return
        val workerGeneration = generation
        worker = scope.launch {
            val gateway = createTtsGateway(settings.ttsBaseUrl, settings.ttsApiKey.ifBlank { settings.apiKey }, settings.ttsModelName, settings.ttsProvider)
            try {
                supervisorScope {
                    var prefetched = emptyList<kotlinx.coroutines.Deferred<PreparedSpeech>>()
                    while (true) {
                        if (prefetched.isEmpty()) {
                            val next = synchronized(lock) { if (workerGeneration != generation) emptyList() else takePendingLocked(1) }
                            prefetched = next.map { async { prepareSpeech(gateway, it) } }
                        }
                        val nextPrepared = prefetched.firstOrNull() ?: break
                        prefetched = prefetched.drop(1)
                        val fill = synchronized(lock) { if (workerGeneration != generation) emptyList() else takePendingLocked(1 - prefetched.size) }
                        prefetched = prefetched + fill.map { async { prepareSpeech(gateway, it) } }
                        try {
                            val prepared = nextPrepared.await()
                            _speakingMessageKey.value = prepared.speech.messageKey
                            playPreparedSpeech(gateway, prepared)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // 聊天里的提示只是一闪而过的 Toast（且必须很短），完整原因写进调试日志便于排障：
                            // 火山这类网关会把真实错误放在 header.code/message 里，只看 Toast 会以为是“无法解析”。
                            // 音色 ID、服务端 LogID、请求 ID 都已经包含在网关抛出的错误详情里。
                            val dedicatedKey = settings.ttsApiKey.trim()
                            val usedKey = dedicatedKey.ifBlank { settings.apiKey }
                            com.rhodes.privatechat.util.DebugLogger.diagnostic(
                                "Voice/TTS/播放失败",
                                "provider=${settings.ttsProvider}, baseUrl=${settings.ttsBaseUrl}, model=${settings.ttsModelName}, " +
                                    "key来源=${if (dedicatedKey.isBlank()) "回退聊天密钥（TTS 密钥为空）" else "专用 TTS 密钥"}, " +
                                    "key指纹=${com.rhodes.privatechat.shared.voice.maskedApiKey(usedKey)}, " +
                                    "error=${e.message?.take(1_200) ?: e.javaClass.simpleName}"
                            )
                            onError("语音播放失败：${e.message?.take(60) ?: "未知错误"}")
                        }
                    }
                }
            } finally {
                _speakingMessageKey.value = ""
                synchronized(lock) {
                    if (workerGeneration == generation) {
                        worker = null
                        if (pending.isNotEmpty()) startWorkerLocked()
                    }
                }
            }
        }
    }

    private fun takePendingLocked(count: Int): List<ChatSpeech> = buildList {
        repeat(count) { pending.removeFirstOrNull()?.let(::add) }
    }

    private data class PreparedSpeech(val speech: ChatSpeech, val parts: List<String>, val firstAudio: ByteArray)

    private suspend fun prepareSpeech(gateway: com.rhodes.privatechat.shared.voice.TtsGateway, speech: ChatSpeech): PreparedSpeech {
        val parts = splitSentences(speech.text)
        val firstAudio = withTimeout(45_000) {
            gateway.synthesize(TtsRequest(text = parts.first(), voiceId = speech.voiceId, speed = speech.speed))
        }.audioBytes ?: error("TTS 未返回音频")
        return PreparedSpeech(speech, parts, firstAudio)
    }

    private suspend fun playPreparedSpeech(gateway: com.rhodes.privatechat.shared.voice.TtsGateway, prepared: PreparedSpeech) = coroutineScope {
        var currentAudio = prepared.firstAudio
        for (index in prepared.parts.indices) {
            val nextAudio = if (index + 1 < prepared.parts.size) async {
                withTimeout(45_000) {
                    gateway.synthesize(TtsRequest(text = prepared.parts[index + 1], voiceId = prepared.speech.voiceId, speed = prepared.speech.speed))
                }.audioBytes ?: error("TTS 未返回音频")
            } else null
            val bytes = currentAudio
            val file = audio.saveTtsAudio(bytes) ?: error("无法保存 TTS 音频")
            var complete = false
            var success = false
            audio.play(file.path, prepared.speech.volume) { played -> complete = true; success = played }
            var waited = 0L
            while (!complete && waited < 90_000L) { delay(100); waited += 100 }
            audio.deleteAudio(file.path)
            if (!success) error("音频播放失败")
            if (nextAudio != null) currentAudio = nextAudio.await()
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
