package com.rhodes.privatechat.shared.vector

/**
 * A stable identity for vectors that can be compared safely across all feature entry points.
 *
 * Keep the persisted format compatible with existing indexes. Provider-specific protocol is not
 * part of the current OpenAI-compatible gateway contract, so endpoint and model are sufficient.
 */
object EmbeddingConfigurationSignature {
    fun create(mode: String, @Suppress("UNUSED_PARAMETER") provider: String, baseUrl: String, modelName: String): String =
        listOf(mode, baseUrl.trim(), modelName.trim()).joinToString("|")
}
