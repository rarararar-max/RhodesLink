package com.rhodes.privatechat.shared.vector

interface EmbeddingGateway {
    suspend fun embed(text: String): List<Double>
}

class DisabledEmbeddingGateway : EmbeddingGateway {
    override suspend fun embed(text: String): List<Double> = emptyList()
}
