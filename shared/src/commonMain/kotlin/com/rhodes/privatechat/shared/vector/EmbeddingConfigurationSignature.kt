package com.rhodes.privatechat.shared.vector

/**
 * A stable identity for vectors that can be compared safely across all feature entry points.
 *
 * Keep the legacy OpenAI signature stable; native protocols need a separate vector space.
 */
object EmbeddingConfigurationSignature {
    fun create(
        mode: String,
        @Suppress("UNUSED_PARAMETER") provider: String,
        baseUrl: String,
        modelName: String,
        protocol: String = "openai_compat",
    ): String {
        val legacy = listOf(mode, baseUrl.trim(), modelName.trim()).joinToString("|")
        return if (protocol == "openai_compat") legacy else "$legacy|protocol=$protocol"
    }
}
