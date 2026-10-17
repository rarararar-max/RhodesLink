package com.rhodes.privatechat.shared.network

import com.rhodes.privatechat.shared.model.ThinkingParam

/** Explicit allowlists: never assume that every model from a provider accepts images or thinking flags. */
object ModelCapabilities {
    fun supportsImages(provider: String, model: String): Boolean = when (provider) {
        "deepseek" -> model in setOf("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp", "deepseek-chat", "deepseek-reasoner")
        "ali" -> model == "qwen3.8-flash"
        else -> false
    }

    fun thinkingParameter(provider: String, model: String, enabled: Boolean = false): ThinkingParam? {
        val supported = provider == "deepseek" ||
            (provider == "xiaomi" && (model.startsWith("mimo-v2.5") || model.startsWith("mimo-v2.6"))) ||
            (provider == "zhipu" && model in setOf("glm-4.5", "glm-4.5-air", "glm-4.6", "glm-4.7", "glm-5", "glm-5.1", "glm-5.2"))
        return if (supported) ThinkingParam(if (enabled && provider == "deepseek") "enabled" else "disabled") else null
    }

    fun enableThinkingParameter(provider: String, model: String): Boolean? =
        if (provider == "ali" && (model.startsWith("qwen3") || model in setOf("qwen-plus", "qwen-flash"))) false else null
}
