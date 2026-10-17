package com.rhodes.privatechat.shared.modelgateway

import com.rhodes.privatechat.shared.network.ModelCapabilities
import com.rhodes.privatechat.shared.network.providers
import com.rhodes.privatechat.shared.settings.SettingsRepository

data class VisionConfiguration(val provider: String, val endpoint: String, val apiKey: String, val model: String) {
    val configured: Boolean get() = endpoint.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

fun resolveVisionConfiguration(
    mode: String, chatProvider: String, chatModel: String, chatKey: String,
    visionProvider: String, visionEndpoint: String, visionKey: String, visionModel: String,
): VisionConfiguration = if (mode == "auto") {
    if (ModelCapabilities.supportsImages(chatProvider, chatModel)) {
        VisionConfiguration(chatProvider, providers[chatProvider]?.baseUrl.orEmpty(), chatKey, chatModel)
    } else VisionConfiguration(chatProvider, "", "", "")
} else VisionConfiguration(visionProvider, visionEndpoint, visionKey.ifBlank { chatKey }, visionModel)

fun SettingsRepository.effectiveVisionConfiguration(): VisionConfiguration = resolveVisionConfiguration(
    visionMode, provider, modelName, apiKey, visionProvider, visionBaseUrl, visionApiKey, visionModelName
)

fun SettingsRepository.hasVisionConfiguration(): Boolean = effectiveVisionConfiguration().configured
