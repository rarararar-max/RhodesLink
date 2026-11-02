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

/** All current recorders produce bounded, mono signed-16-bit PCM. Validate before encoding. */
fun validateAsrAudio(request: AsrRequest) {
    require(request.sampleRate in 8000..48000 && request.inputAudioFormat == "pcm" && request.pcm16kMonoAudio.size % 2 == 0) {
        "录音格式无效：需要单声道16位PCM"
    }
    require(request.pcm16kMonoAudio.size <= 4_000_000) { "录音过长，请缩短后重试" }
}
