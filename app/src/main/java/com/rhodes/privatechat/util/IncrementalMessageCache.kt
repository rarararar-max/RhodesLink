package com.rhodes.privatechat.util

/** Reuses unchanged rows; removed rows and old contexts never remain in this cache. */
internal class IncrementalMessageCache<K, S, V> {
    private var entries = emptyMap<K, Pair<S, V>>()

    @Synchronized
    fun map(sources: List<S>, key: (S) -> K, transform: (S) -> V): List<V> {
        val next = LinkedHashMap<K, Pair<S, V>>(sources.size)
        val result = sources.map { source ->
            val id = key(source)
            val old = entries[id]
            val value = if (old != null && old.first == source) old.second else transform(source)
            next[id] = source to value
            value
        }
        entries = next
        return result
    }
}
