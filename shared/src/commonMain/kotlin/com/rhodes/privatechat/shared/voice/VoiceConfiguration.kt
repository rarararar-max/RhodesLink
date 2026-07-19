package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun SettingsRepository.hasAsrConfiguration(): Boolean =
    asrBaseUrl.isNotBlank() && asrApiKey.ifBlank { apiKey }.isNotBlank()

fun SettingsRepository.hasTtsConfiguration(): Boolean =
    ttsBaseUrl.isNotBlank() && ttsApiKey.ifBlank { apiKey }.isNotBlank()

fun SettingsRepository.voiceCallSetupMessage(voiceId: String): String? = when {
    !hasAsrConfiguration() -> "请先在模型设置中填写语音识别模型和密钥。"
    !hasTtsConfiguration() -> "请先在模型设置中填写文字转语音模型和密钥。"
    ttsProvider == "vocu" && voiceId.isBlank() -> "Vocu 需要在角色编辑页填写音色 ID。"
    else -> null
}

fun SettingsRepository.effectiveVoiceId(operatorVoiceId: String): String =
    operatorVoiceId

fun defaultTtsVoiceId(provider: String): String =
    when (provider) {
        "xiaomi" -> "mimo_default"
        "vocu" -> ""
        else -> "male-qn-qingse"
    }

fun createTtsGateway(endpoint: String, apiKey: String, modelName: String, provider: String = ""): TtsGateway {
    return if (endpoint.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()) {
        when {
            provider == "vocu" || endpoint.contains("vocu.ai") -> VocuTtsGateway(endpoint, apiKey)
            provider == "xiaomi" || endpoint.contains("api.xiaomimimo.com") -> XiaomiMimoTtsGateway(endpoint, apiKey, modelName)
            else -> MinimaxTtsGateway(endpoint = endpoint, apiKey = apiKey, modelName = modelName)
        }
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
