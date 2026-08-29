package com.rhodes.privatechat.shared.knowledge

import com.rhodes.privatechat.shared.data.KnowledgeBaseRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorMemory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

data class KnowledgeBaseIndexResult(
    val knowledgeBaseId: String,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
)

data class KnowledgeBaseIndexPlan(
    val knowledgeBaseId: String,
    val chunkCount: Int,
    val usesRemoteEmbedding: Boolean,
    val requiresUserConfirmation: Boolean,
)

class KnowledgeBaseIndexService(
    private val repository: KnowledgeBaseRepository,
    private val vectorService: MemoryVectorService,
    private val settings: SettingsRepository,
) {
    // A slow remote embedding request for one book must not serialize all knowledge bases.
    private val bookMutexesMutex = Mutex()
    private val bookMutexes = mutableMapOf<String, Mutex>()
    private val stateMutex = Mutex()
    private val cancelledBooks = mutableSetOf<String>()
    private val activeJobs = mutableMapOf<String, MutableSet<Job>>()

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun planIndex(knowledgeBaseId: String): KnowledgeBaseIndexPlan {
        val chunks = repository.getChunks(knowledgeBaseId).count { it.enabled && it.content.isNotBlank() }
        val remote = settings.vectorProviderMode == "third_party"
        return KnowledgeBaseIndexPlan(knowledgeBaseId, chunks, remote, remote)
    }

    suspend fun planDirtyIndex(knowledgeBaseId: String): KnowledgeBaseIndexPlan {
        val chunks = repository.getChunks(knowledgeBaseId).count { it.enabled && it.content.isNotBlank() && (it.indexedAt <= 0L || it.indexError.isNotBlank()) }
        val remote = settings.vectorProviderMode == "third_party"
        return KnowledgeBaseIndexPlan(knowledgeBaseId, chunks, remote, remote)
    }

    /**
     * Starts work without tying it to a Compose screen. Remote indexing must be explicitly
     * confirmed by the UI because every chunk can make a paid API request.
     */
    suspend fun enqueueIndex(
        knowledgeBaseId: String,
        remoteConfirmed: Boolean,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onComplete: (KnowledgeBaseIndexResult) -> Unit = {},
    ): Job {
        val plan = planIndex(knowledgeBaseId)
        require(!plan.requiresUserConfirmation || remoteConfirmed) { "远程向量化需要用户确认预计请求次数" }
        return backgroundScope.launch {
            val result = try { indexBook(knowledgeBaseId, onProgress) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Throwable) {
                runCatching { repository.updateIndexStatus(knowledgeBaseId, "failed") }
                KnowledgeBaseIndexResult(knowledgeBaseId, 0, 0, 1, listOf(error.message ?: "索引任务失败"))
            }
            onComplete(result)
        }
    }

    suspend fun indexBook(
        knowledgeBaseId: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): KnowledgeBaseIndexResult {
        val job = currentCoroutineContext()[Job]!!
        stateMutex.withLock {
            cancelledBooks.remove(knowledgeBaseId)
            activeJobs.getOrPut(knowledgeBaseId) { mutableSetOf() } += job
        }
        return try {
            index(knowledgeBaseId, rebuildAll = true, onProgress)
        } finally {
            stateMutex.withLock {
                activeJobs[knowledgeBaseId]?.remove(job)
                if (activeJobs[knowledgeBaseId].isNullOrEmpty()) activeJobs.remove(knowledgeBaseId)
            }
        }
    }

    suspend fun retryFailedChunks(
        knowledgeBaseId: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): KnowledgeBaseIndexResult {
        val job = currentCoroutineContext()[Job]!!
        stateMutex.withLock {
            cancelledBooks.remove(knowledgeBaseId)
            activeJobs.getOrPut(knowledgeBaseId) { mutableSetOf() } += job
        }
        return try {
            index(knowledgeBaseId, rebuildAll = false, onProgress)
        } finally {
            stateMutex.withLock {
                activeJobs[knowledgeBaseId]?.remove(job)
                if (activeJobs[knowledgeBaseId].isNullOrEmpty()) activeJobs.remove(knowledgeBaseId)
            }
        }
    }

    suspend fun invalidate(knowledgeBaseId: String) {
        cancelActiveIndex(knowledgeBaseId)
        mutexFor(knowledgeBaseId).withLock {
        vectorService.clearOwnerMemory(OWNER_TYPE, knowledgeBaseId)
        repository.clearChunkIndexes(knowledgeBaseId)
        repository.updateIndexStatus(knowledgeBaseId, "pending")
        }
        stateMutex.withLock { cancelledBooks.remove(knowledgeBaseId) }
    }

    suspend fun cancelAndRemove(knowledgeBaseId: String) {
        cancelActiveIndex(knowledgeBaseId)
        vectorService.clearOwnerMemory(OWNER_TYPE, knowledgeBaseId)
    }

    suspend fun removeChunkVector(knowledgeBaseId: String, chunkId: String) {
        cancelActiveIndex(knowledgeBaseId)
        vectorService.clearSessionMemory(OWNER_TYPE, knowledgeBaseId, chunkId)
    }

    suspend fun markChunksPendingConfirmation(knowledgeBaseId: String) {
        val chunks = repository.getChunks(knowledgeBaseId)
        val hasIndexed = chunks.any { it.enabled && it.indexedAt > 0L && it.indexError.isBlank() }
        repository.updateIndexStatus(knowledgeBaseId, if (hasIndexed) "partial_pending_confirm" else "pending_confirm", vectorService.currentEmbeddingSignature())
    }

    suspend fun enqueueDirtyChunks(knowledgeBaseId: String, remoteConfirmed: Boolean): Job {
        val plan = planIndex(knowledgeBaseId)
        require(!plan.requiresUserConfirmation || remoteConfirmed) { "远程向量化需要用户确认预计请求次数" }
        return backgroundScope.launch { indexDirtyChunks(knowledgeBaseId) }
    }

    suspend fun indexDirtyChunks(knowledgeBaseId: String): KnowledgeBaseIndexResult {
        val job = currentCoroutineContext()[Job]!!
        stateMutex.withLock {
            cancelledBooks.remove(knowledgeBaseId)
            activeJobs.getOrPut(knowledgeBaseId) { mutableSetOf() } += job
        }
        return try {
            index(knowledgeBaseId, rebuildAll = false) { _, _ -> }
        } finally {
            stateMutex.withLock {
                activeJobs[knowledgeBaseId]?.remove(job)
                if (activeJobs[knowledgeBaseId].isNullOrEmpty()) activeJobs.remove(knowledgeBaseId)
            }
        }
    }

    private suspend fun index(
        knowledgeBaseId: String,
        rebuildAll: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): KnowledgeBaseIndexResult = mutexFor(knowledgeBaseId).withLock {
        try {
        if (isCancelled(knowledgeBaseId)) return@withLock KnowledgeBaseIndexResult(knowledgeBaseId, 0, 0, 0)
        val book = repository.get(knowledgeBaseId) ?: throw IllegalArgumentException("知识库不存在")
        val chunks = repository.getChunks(knowledgeBaseId).filter {
            it.enabled && it.content.isNotBlank() && (rebuildAll || it.indexedAt <= 0L || it.indexError.isNotBlank())
        }
        val signature = vectorService.currentEmbeddingSignature()
        val hasUsableExistingChunks = !rebuildAll && repository.getChunks(knowledgeBaseId).any {
            it.enabled && it.indexedAt > 0L && it.indexError.isBlank()
        }
        val indexingStatus = if (hasUsableExistingChunks) "partial_indexing:0/${chunks.size}" else "indexing:0/${chunks.size}"
        repository.updateIndexStatus(knowledgeBaseId, indexingStatus, signature)
        if (rebuildAll) {
            repository.clearChunkIndexes(knowledgeBaseId)
            vectorService.clearOwnerMemory(OWNER_TYPE, knowledgeBaseId)
        }

        if (chunks.isEmpty()) {
            if (rebuildAll) {
                repository.updateIndexStatus(knowledgeBaseId, "failed", signature)
                return@withLock KnowledgeBaseIndexResult(knowledgeBaseId, 0, 0, 0, listOf("知识库没有可索引的分段"))
            }
            return@withLock KnowledgeBaseIndexResult(knowledgeBaseId, 0, 0, 0)
        }

        var succeeded = 0
        var failed = 0
        val errors = mutableListOf<String>()
        chunks.forEachIndexed { index, chunk ->
            if (isCancelled(knowledgeBaseId) || repository.get(knowledgeBaseId) == null) return@withLock KnowledgeBaseIndexResult(knowledgeBaseId, chunks.size, succeeded, failed)
            try {
                val searchableContent = buildSearchableContent(book.name, chunk.sourceHeading, chunk.userKeywords, chunk.content)
                saveChunkVector(
                    VectorMemory(
                        id = vectorId(chunk.id), ownerType = OWNER_TYPE, ownerId = knowledgeBaseId,
                        sourceType = SOURCE_TYPE, sourceId = chunk.id, content = searchableContent,
                        importance = 0.5, tags = chunk.userKeywords, visibility = "public", createdAt = chunk.createdAt,
                    )
                )
                repository.updateChunkIndex(chunk.id, System.currentTimeMillis())
                succeeded++
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                failed++
                val message = error.message?.take(160).orEmpty().ifBlank { error::class.simpleName ?: "索引失败" }
                errors += "片段 ${chunk.ordinal}：$message"
                repository.updateChunkIndex(chunk.id, 0L, message)
            }
            onProgress(index + 1, chunks.size)
            repository.updateIndexStatus(knowledgeBaseId, if (hasUsableExistingChunks) "partial_indexing:${index + 1}/${chunks.size}" else "indexing:${index + 1}/${chunks.size}", signature)
        }
        val remainingDirty = repository.getChunks(knowledgeBaseId).any { it.enabled && (it.indexedAt <= 0L || it.indexError.isNotBlank()) }
        val status = when {
            remainingDirty && succeeded > 0 -> "partial_pending_confirm"
            failed == 0 -> "ready"
            succeeded == 0 -> "failed"
            else -> "partial_failed"
        }
        repository.updateIndexStatus(knowledgeBaseId, status, signature)
        KnowledgeBaseIndexResult(knowledgeBaseId, chunks.size, succeeded, failed, errors.distinct().take(5))
        } catch (cancelled: CancellationException) {
            repository.updateIndexStatus(knowledgeBaseId, "pending")
            throw cancelled
        } catch (error: Throwable) {
            repository.get(knowledgeBaseId)?.let { repository.updateIndexStatus(knowledgeBaseId, "failed") }
            throw error
        }
    }

    private fun buildSearchableContent(bookName: String, heading: String, keywords: String, content: String): String =
        buildString {
            append("知识库：").append(bookName)
            if (heading.isNotBlank()) append("\n章节：").append(heading)
            if (keywords.isNotBlank()) append("\n关键词：").append(keywords)
            append("\n正文：").append(content)
        }

    private fun vectorId(chunkId: String): String = "knowledge_base_chunk_$chunkId"

    private suspend fun mutexFor(knowledgeBaseId: String): Mutex = bookMutexesMutex.withLock {
        bookMutexes.getOrPut(knowledgeBaseId) { Mutex() }
    }

    private suspend fun saveChunkVector(memory: VectorMemory) {
        if (settings.vectorProviderMode != "third_party") {
            withTimeout(EMBEDDING_TIMEOUT_MS) { vectorService.saveMemory(memory) }
            return
        }
        var lastFailure: Throwable? = null
        repeat(MAX_REMOTE_ATTEMPTS) { attempt ->
            try {
                withTimeout(EMBEDDING_TIMEOUT_MS) {
                    remoteIndexSemaphore.withPermit {
                        vectorService.saveMemory(memory)
                    }
                }
                return
            } catch (timeout: TimeoutCancellationException) {
                if (!currentCoroutineContext().isActive) throw timeout
                lastFailure = timeout
                if (attempt + 1 < MAX_REMOTE_ATTEMPTS) delay(RETRY_DELAYS_MS[attempt]) else throw timeout
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                lastFailure = error
                if (attempt + 1 < MAX_REMOTE_ATTEMPTS && isRetryable(error)) {
                    delay(RETRY_DELAYS_MS[attempt])
                } else {
                    throw error
                }
            }
        }
        throw lastFailure ?: IllegalStateException("知识库向量化失败")
    }

    private fun isRetryable(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return "timeout" in message.lowercase() ||
            "connection" in message.lowercase() ||
            "network" in message.lowercase() ||
            "408" in message || "429" in message || Regex("\\b5\\d{2}\\b").containsMatchIn(message)
    }

    private suspend fun isCancelled(knowledgeBaseId: String): Boolean = stateMutex.withLock {
        knowledgeBaseId in cancelledBooks
    }

    private suspend fun cancelActiveIndex(knowledgeBaseId: String) {
        val jobs = stateMutex.withLock {
            cancelledBooks += knowledgeBaseId
            activeJobs[knowledgeBaseId]?.toList().orEmpty()
        }
        jobs.forEach(Job::cancel)
    }

    private companion object {
        const val OWNER_TYPE = "knowledge_base"
        const val SOURCE_TYPE = "knowledge_base_chunk"
        const val EMBEDDING_TIMEOUT_MS = 15_000L
        const val MAX_REMOTE_ATTEMPTS = 3
        val RETRY_DELAYS_MS = longArrayOf(750L, 2_000L)
        val remoteIndexSemaphore = Semaphore(1)
    }
}
