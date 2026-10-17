package com.rhodes.privatechat

import com.rhodes.privatechat.shared.vector.*
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.russhwolf.settings.ObservableSettings
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class MemoryVectorReliabilityTest {
    private fun repository(initial: Map<String, Any> = emptyMap()): SettingsRepository {
        val data = initial.toMutableMap()
        val backend = Proxy.newProxyInstance(ObservableSettings::class.java.classLoader,
            arrayOf(ObservableSettings::class.java)) { proxy, method, args ->
            val a = args.orEmpty()
            when {
                method.name == "hasKey" -> data.containsKey(a[0])
                method.name == "getKeys" -> data.keys.toSet()
                method.name == "getSize" -> data.size
                method.name.startsWith("get") -> data[a[0]] ?: a.getOrNull(1)
                method.name.startsWith("put") -> { data[a[0] as String] = a[1]; null }
                method.name == "remove" -> { data.remove(a[0]); null }
                method.name == "clear" -> { data.clear(); null }
                method.name == "equals" -> proxy === a[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "TestSettings"
                else -> null
            }
        } as ObservableSettings
        return SettingsRepository(backend)
    }


    private class Store: VectorStoreGateway {
        val records = mutableMapOf<String, VectorMemory>()
        var searches = 0
        override suspend fun upsert(memory: VectorMemory) { records[memory.id] = memory }
        override suspend fun search(request: VectorSearchRequest): List<VectorMemory> { searches++; return records.values.toList() }
        override suspend fun listMemories(ownerType: String, ownerId: String) = records.values.filter { it.ownerType == ownerType && it.ownerId == ownerId }
        override suspend fun delete(memoryId: String) { records.remove(memoryId) }
        override suspend fun deleteBySource(ownerType: String, ownerId: String, sourceId: String) {}
        override suspend fun clearOwnerMemory(ownerType: String, ownerId: String) {}
        override suspend fun clearAllMemories() { records.clear() }
    }
    private fun memory(text: String = "原记忆") = VectorMemory("id", "operator", "op", "memory_v2_l1", "source", text, embedding = listOf(1.0, 0.0))

    @Test fun failedReplacementPreservesExistingVector() = runBlocking {
        val store = Store().apply { records["id"] = memory() }
        val service = MemoryVectorService(repository(), store) { object: EmbeddingGateway {
            override suspend fun embed(text: String): List<Double> = throw IllegalStateException("offline")
        } }
        try { service.saveMemory(memory("新记忆")); fail("must fail") } catch (_: IllegalStateException) {}
        assertEquals("原记忆", store.records["id"]?.content)
    }
    @Test fun emptyOrNonFiniteEmbeddingCannotReplaceWorkingIndex() = runBlocking {
        for (vector in listOf(emptyList(), listOf(Double.NaN))) {
            val store = Store().apply { records["id"] = memory() }
            val service = MemoryVectorService(repository(), store) { object: EmbeddingGateway {
                override suspend fun embed(text: String) = vector
            } }
            try { service.saveMemory(memory("新记忆")); fail("must reject") } catch (_: IllegalArgumentException) {}
            assertEquals("原记忆", store.records["id"]?.content)
        }
    }
    @Test fun changingModelDuringEmbeddingCannotPublishUnderWrongSignature() = runBlocking {
        val settings = repository()
        val store = Store().apply { records["id"] = memory() }
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val service = MemoryVectorService(settings, store) { object: EmbeddingGateway {
            override suspend fun embed(text: String): List<Double> { entered.complete(Unit); release.await(); return listOf(1.0, 0.0) }
        } }
        val job = async { runCatching { service.saveMemory(memory("新记忆")) } }
        entered.await(); settings.vectorModelName = "new-model"; release.complete(Unit)
        assertTrue(job.await().isFailure)
        assertEquals("原记忆", store.records["id"]?.content)
    }
    @Test fun cancellationDoesNotEraseOldIndex() = runBlocking {
        val store = Store().apply { records["id"] = memory() }
        val entered = CompletableDeferred<Unit>()
        val service = MemoryVectorService(repository(), store) { object: EmbeddingGateway {
            override suspend fun embed(text: String): List<Double> { entered.complete(Unit); awaitCancellation() }
        } }
        val job = launch { service.saveMemory(memory("新记忆")) }
        entered.await(); job.cancelAndJoin()
        assertEquals("原记忆", store.records["id"]?.content)
    }
    @Test fun precomputedQueryFromAnotherModelIsNeverSearched() = runBlocking {
        val serviceSettings = repository(); val store = Store()
        val service = MemoryVectorService(serviceSettings, store)
        val signature = service.currentEmbeddingSignature()
        serviceSettings.vectorModelName = "other"
        assertTrue(service.searchWithEmbedding(VectorSearchRequest("operator", "op", "query", embeddingSignature = signature), listOf(1.0,0.0)).isEmpty())
        assertEquals(0, store.searches)
    }
    @Test fun rebuildSignatureIsCheckedBeforeSpendingEmbeddingRequest() = runBlocking {
        val settings = repository(); val store = Store(); var calls = 0
        val service = MemoryVectorService(settings, store) { object: EmbeddingGateway {
            override suspend fun embed(text: String): List<Double> { calls++; return listOf(1.0, 0.0) }
        } }
        try { service.saveMemory(memory().copy(embeddingSignature = "old-model")); fail("must reject") } catch (_: IllegalStateException) {}
        assertEquals(0, calls)
    }
    @Test fun providerChangeRecreatesGatewayAndDoesNotReuseCachedVector() = runBlocking {
        val settings = repository(); val store = Store(); var calls = 0; var factories = 0
        val service = MemoryVectorService(settings, store) { factories++; object: EmbeddingGateway {
            override suspend fun embed(text: String): List<Double> { calls++; return listOf(1.0, 0.0) }
        } }
        service.saveMemory(memory()); service.saveMemory(memory())
        assertEquals(1, calls)
        settings.vectorProvider = "different-provider"
        service.saveMemory(memory())
        assertEquals(2, factories); assertEquals(2, calls)
    }
}
