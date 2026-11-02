package com.rhodes.privatechat.shared.voice

data class TtsRequest(
    val text: String,
    val voiceId: String,
    val format: String = "mp3",
    val speed: Double = 1.0,
)

data class TtsResult(
    val audioBytes: ByteArray? = null,
    val durationMs: Long = 0L,
)

interface TtsGateway {
    suspend fun synthesize(request: TtsRequest): TtsResult
}

/** A deadline owned by this operation must not swallow cancellation of its caller. */
suspend fun <T : Any> withVoiceDeadline(timeoutMs: Long, label: String, block: suspend () -> T): T =
    kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { block() }
        ?: throw IllegalStateException("$label 超时，请检查网络后重试")

class ReliableTtsGateway(private val delegate: TtsGateway, private val apiKey: String) : TtsGateway {
    override suspend fun synthesize(request: TtsRequest): TtsResult {
        require(request.speed.isFinite() && request.speed > 0) { "语速设置无效" }
        try {
            val result = withVoiceDeadline(90_000L, "语音合成") { delegate.synthesize(request) }
            check(result.audioBytes?.isNotEmpty() == true) { "语音服务未返回可播放的音频" }
            return result
        } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (e: Exception) {
            throw IllegalStateException(com.rhodes.privatechat.shared.network.ModelErrorMessages.redact(e.message ?: "语音合成失败", apiKey), e)
        }
    }
}
