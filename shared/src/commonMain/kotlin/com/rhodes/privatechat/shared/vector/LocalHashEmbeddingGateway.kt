package com.rhodes.privatechat.shared.vector

import kotlin.math.sqrt

class LocalHashEmbeddingGateway(
    private val dimensions: Int = 384,
) : EmbeddingGateway {
    override suspend fun embed(text: String): List<Double> {
        val vector = DoubleArray(dimensions)
        val normalized = text.lowercase().filterNot { it.isWhitespace() }
        if (normalized.isBlank()) return vector.toList()
        val grams = buildList {
            normalized.forEach { add(it.toString()) }
            normalized.windowed(2).forEach { add(it) }
            normalized.windowed(3).forEach { add(it) }
        }
        grams.forEach { gram ->
            val hash = stableHash(gram)
            val index = (hash and Int.MAX_VALUE) % dimensions
            val sign = if ((hash ushr 31) == 0) 1.0 else -1.0
            vector[index] += sign
        }
        var norm = 0.0
        vector.forEach { norm += it * it }
        val scale = sqrt(norm).takeIf { it > 0.0 } ?: 1.0
        return vector.map { it / scale }
    }

    private fun stableHash(value: String): Int {
        var hash = -0x7ee3623b
        value.forEach { char ->
            hash = hash xor char.code
            hash *= 16777619
        }
        return hash
    }
}
