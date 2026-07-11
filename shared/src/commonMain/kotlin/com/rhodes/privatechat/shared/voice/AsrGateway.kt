package com.rhodes.privatechat.shared.voice

data class AsrRequest(
    val pcm16kMonoAudio: ByteArray,
    val language: String = "zh",
    val inputAudioFormat: String = "pcm",
    val sampleRate: Int = 16000,
)

data class AsrResult(
    val text: String,
    val emotion: String? = null,
)

interface AsrGateway {
    suspend fun transcribe(request: AsrRequest): AsrResult
}
