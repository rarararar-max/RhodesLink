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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private val indexMutex = Mutex()

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun planIndex(knowledgeBaseId: String): KnowledgeBaseIndexPlan {
        val chunks = repository.getChunks(knowledgeBaseId).count { it.enabled && it.content.isNotBlank() }
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
    ): KnowledgeBaseIndexResult = index(knowledgeBaseId, rebuildAll = true, onProgress)

    suspend fun retryFailedChunks(
        knowledgeBaseId: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): KnowledgeBaseIndexResult = index(knowledgeBaseId, rebuildAll = false, onProgress)

    suspend fun invalidate(knowledgeBaseId: String) = indexMutex.withLock {
        vectorService.clearOwnerMemory(OWNER_TYPE, knowledgeBaseId)
        repository.clearChunkIndexes(knowledgeBaseId)
        repository.updateIndexStatus(knowledgeBaseId, "pending")
    }

    private suspend fun index(
        knowledgeBaseId: String,
        rebuildAll: Boolean,
        onProgress: (done: Int, total: Int) -> Unit,
    ): KnowledgeBaseIndexResult = indexMutex.withLock {
        try {
        val book = repository.get(knowledgeBaseId) ?: throw IllegalArgumentException("知识库不存在")
        val chunks = repository.getChunks(knowledgeBaseId).filter {
            it.enabled && it.content.isNotBlank() && (rebuildAll || it.indexError.isNotBlank())
        }
        val signature = vectorService.currentEmbeddingSignature()
        repository.updateIndexStatus(knowledgeBaseId, "indexing", signature)
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
            try {
                val searchableContent = buildSearchableContent(book.name, chunk.sourceHeading, chunk.userKeywords, chunk.content)
                vectorService.saveMemory(
                    VectorMemory(
                        id = vectorId(chunk.id),
                        ownerType = OWNER_TYPE,
                        ownerId = knowledgeBaseId,
                        sourceType = SOURCE_TYPE,
                        sourceId = chunk.id,
                        content = searchableContent,
                        importance = 0.5,
                        tags = chunk.userKeywords,
                        visibility = "public",
                        createdAt = chunk.createdAt,
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
        }
        val status = when {
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

    private companion object {
        const val OWNER_TYPE = "knowledge_base"
        const val SOURCE_TYPE = "knowledge_base_chunk"
    }
}
