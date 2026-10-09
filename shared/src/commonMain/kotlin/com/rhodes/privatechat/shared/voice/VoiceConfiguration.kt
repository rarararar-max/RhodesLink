package com.rhodes.privatechat.shared.voice

import com.rhodes.privatechat.shared.settings.SettingsRepository

fun SettingsRepository.hasAsrConfiguration(): Boolean =
    asrBaseUrl.isNotBlank() && asrApiKey.ifBlank { apiKey }.isNotBlank()

fun SettingsRepository.hasTtsConfiguration(): Boolean =
    ttsBaseUrl.isNotBlank() &&
        ttsApiKey.ifBlank { apiKey }.isNotBlank() &&
        (ttsProvider == "vocu" || ttsBaseUrl.contains("vocu.ai") || ttsModelName.isNotBlank())

fun SettingsRepository.voiceCallSetupMessage(voiceId: String): String? = when {
    !hasAsrConfiguration() -> "请先在模型设置中填写语音识别模型和密钥。"
    !hasTtsConfiguration() -> "请先在模型设置中填写文字转语音模型和密钥。"
    ttsProvider == "vocu" && voiceId.isBlank() -> "Vocu 需要在角色编辑页填写音色 ID。"
    ttsProvider == "volcano" && voiceId.isBlank() -> "火山引擎需要在角色编辑页填写音色 ID（控制台音色库里的 Speaker ID，S_ 开头）。"
    else -> null
}

fun SettingsRepository.effectiveVoiceId(operatorVoiceId: String): String =
    operatorVoiceId

fun defaultTtsVoiceId(provider: String): String =
    when (provider) {
        "xiaomi" -> "mimo_default"
        "vocu" -> ""
        // 火山没有公共默认音色：必须用玩家自己复刻得到的 Speaker ID。
        "volcano" -> ""
        else -> "male-qn-qingse"
    }

/**
 * 只用于在调试日志里把“应用实际用的 Key”和火山控制台里的 Key 对上号。
 * 日志永远不写完整密钥：只留前 6 位和长度，足够在控制台的 Key 列表里认出来。
 */
fun maskedApiKey(apiKey: String): String {
    val trimmed = apiKey.trim()
    return when {
        trimmed.isEmpty() -> "未填写"
        trimmed.length <= 8 -> "长度 ${trimmed.length}（过短，可能填错）"
        else -> "${trimmed.take(6)}…（长度 ${trimmed.length}）"
    }
}

fun createTtsGateway(endpoint: String, apiKey: String, modelName: String, provider: String = ""): TtsGateway {
    val isVocu = provider == "vocu" || endpoint.contains("vocu.ai")
    val isVolcano = provider == "volcano" || endpoint.contains("openspeech.bytedance.com")
    return if (endpoint.isNotBlank() && apiKey.isNotBlank() && (isVocu || modelName.isNotBlank())) {
        when {
            isVocu -> VocuTtsGateway(endpoint, apiKey)
            isVolcano -> VolcanoTtsGateway(endpoint, apiKey, resourceId = modelName)
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

/**
 * Explains the most common voice-setup failure. When no dedicated ASR key is set the app silently
 * reuses the chat key, so any non-Aliyun chat provider (DeepSeek, OpenAI, ...) makes 语音识别 fail
 * with a 401 that users cannot interpret. Returns null when there is nothing to warn about.
 */
fun asrKeyFallbackHint(
    endpoint: String,
    asrApiKey: String,
    chatApiKey: String,
    chatProvider: String = "",
    asrProvider: String = "",
): String? {
    if (asrProvider == "xiaomi" || endpoint.contains("api.xiaomimimo.com")) return null
    if (asrApiKey.isNotBlank() || chatApiKey.isBlank()) return null
    if (!endpoint.contains("dashscope.aliyuncs.com")) return "未单独填写语音识别密钥，将复用聊天密钥。"
    return if (chatProvider == "ali") {
        "未单独填写语音识别密钥，将复用聊天密钥（当前聊天服务商为阿里云，通常可用）。"
    } else {
        "未单独填写语音识别密钥，会复用聊天密钥；当前聊天服务商不是阿里云百炼，语音识别会返回 401，请在此单独填写百炼 Key。"
    }
}
