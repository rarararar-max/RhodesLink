package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun SettingsRepository.hasAsrConfiguration(): Boolean =
    asrBaseUrl.isNotBlank() && asrApiKey.ifBlank { apiKey }.isNotBlank()

fun SettingsRepository.hasTtsConfiguration(): Boolean =
    ttsBaseUrl.isNotBlank() && ttsApiKey.ifBlank { apiKey }.isNotBlank()

fun SettingsRepository.voiceCallSetupMessage(voiceId: String): String? = when {
    !hasAsrConfiguration() -> "请先在模型设置中填写语音识别模型和密钥。"
    !hasTtsConfiguration() -> "请先在模型设置中填写文字转语音模型和密钥。"
    effectiveVoiceId(voiceId).isBlank() -> "请先在角色编辑页面填写音色 ID，或在模型设置中填写默认音色 ID。"
    else -> null
}

fun SettingsRepository.effectiveVoiceId(operatorVoiceId: String): String =
    operatorVoiceId.ifBlank { ttsDefaultVoiceId }

fun createTtsGateway(endpoint: String, apiKey: String, modelName: String, provider: String = ""): TtsGateway {
    return if (endpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
        if (provider == "xiaomi" || endpoint.contains("api.xiaomimimo.com")) XiaomiMimoTtsGateway(endpoint, apiKey, modelName)
        else MinimaxTtsGateway(endpoint = endpoint, apiKey = apiKey, modelName = modelName)
    } else {
        DisabledTtsGateway()
    }
}

fun createAsrGateway(endpoint: String, apiKey: String, modelName: String, provider: String = ""): AsrGateway {
    return if (endpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
        if (provider == "xiaomi" || endpoint.contains("api.xiaomimimo.com")) XiaomiMimoAsrGateway(endpoint, apiKey, modelName)
        else AliyunDashScopeAsrGateway(endpoint = endpoint, apiKey = apiKey, modelName = modelName)
    } else {
        DisabledAsrGateway()
    }
}
