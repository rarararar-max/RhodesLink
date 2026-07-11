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
