package com.rhodes.privatechat.shared.voice

class DisabledAsrGateway : AsrGateway {
    override suspend fun transcribe(request: AsrRequest): AsrResult {
        error("语音识别模型未配置")
    }
}

class DisabledTtsGateway : TtsGateway {
    override suspend fun synthesize(request: TtsRequest): TtsResult {
        error("文字转语音模型未配置")
    }
}
