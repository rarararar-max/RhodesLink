package com.rhodes.privatechat.shared.knowledge

import com.rhodes.privatechat.shared.data.KnowledgeBaseRepository
import com.rhodes.privatechat.shared.model.KnowledgeBase
import com.rhodes.privatechat.shared.model.KnowledgeBaseChunk
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.datetime.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.random.Random

class KnowledgeBaseImportService(
    private val repository: KnowledgeBaseRepository,
    private val indexService: KnowledgeBaseIndexService,
    private val settings: SettingsRepository,
) {
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val processingJobs = mutableMapOf<String, Job>()
    suspend fun importFile(fileName: String, bytes: ByteArray, name: String = defaultName(fileName)): KnowledgeBase {
        val preview = KnowledgeBaseTextProcessor.prepare(fileName, bytes)
        val format = fileName.substringAfterLast('.', "").lowercase()
        return savePrepared(name, preview, fileName, format)
    }

    suspend fun saveText(
        name: String,
        content: String,
        sourceFormat: String = "txt",
        sourceType: String = "user",
    ): KnowledgeBase {
        val preview = KnowledgeBaseTextProcessor.prepareText(content, sourceFormat)
        return savePrepared(name, preview, "", sourceFormat, sourceType)
    }

    suspend fun saveTextInBackground(name: String, content: String, sourceFormat: String = "txt"): KnowledgeBase {
        require(name.trim().isNotBlank()) { "知识库名称不能为空" }
        val now = Clock.System.now().toEpochMilliseconds()
        val book = KnowledgeBase(
            id = newId("kb"), name = name.trim(), rawContent = content, sourceFormat = sourceFormat,
            indexStatus = "processing", createdAt = now, updatedAt = now,
        )
        repository.save(book, emptyList())
        startBackgroundProcessing(book, content, sourceFormat, settings.vectorProviderMode)
        return book
    }

    suspend fun cancelBackgroundProcessing(knowledgeBaseId: String) {
        processingJobs.remove(knowledgeBaseId)?.cancel()
    }

    suspend fun resumeBackgroundProcessing(knowledgeBase: KnowledgeBase) {
        if (knowledgeBase.indexStatus == "processing" && knowledgeBase.id !in processingJobs) {
            startBackgroundProcessing(knowledgeBase, knowledgeBase.rawContent, knowledgeBase.sourceFormat.ifBlank { "txt" }, settings.vectorProviderMode)
        }
    }

    private fun startBackgroundProcessing(book: KnowledgeBase, content: String, sourceFormat: String, vectorProviderMode: String) {
        processingJobs.remove(book.id)?.cancel()
        processingJobs[book.id] = backgroundScope.launch {
            try {
                val createdAt = book.createdAt
                val preview = KnowledgeBaseTextProcessor.prepareText(content, sourceFormat)
                val chunks = preview.chunks.map { draft ->
                    KnowledgeBaseChunk(
                        id = newId("kbc"), knowledgeBaseId = book.id, ordinal = draft.ordinal,
                        sourceHeading = draft.sourceHeading, content = draft.content, createdAt = createdAt, updatedAt = createdAt,
                    )
                }
                val current = repository.get(book.id)
                if (current?.indexStatus != "processing" || current.rawContent != content) return@launch
                repository.save(current.copy(rawContent = preview.normalizedContent, updatedAt = Clock.System.now().toEpochMilliseconds()), chunks)
                if (vectorProviderMode == "local") indexService.enqueueIndex(book.id, remoteConfirmed = false)
                else repository.updateIndexStatus(book.id, "pending_confirm")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                repository.get(book.id)?.takeIf { it.indexStatus == "processing" }?.let {
                    repository.updateIndexStatus(book.id, "failed")
                }
            } finally {
                processingJobs.remove(book.id)
            }
        }
    }

    suspend fun updateText(existing: KnowledgeBase, name: String, content: String): KnowledgeBase {
        val preview = KnowledgeBaseTextProcessor.prepareText(content, existing.sourceFormat.ifBlank { "txt" })
        require(name.trim().isNotBlank()) { "知识库名称不能为空" }
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = existing.copy(
            name = name.trim(),
            rawContent = preview.normalizedContent,
            indexStatus = "pending",
            indexedEmbeddingSignature = "",
            updatedAt = now,
        )
        val chunks = preview.chunks.map { draft ->
            KnowledgeBaseChunk(
                id = newId("kbc"), knowledgeBaseId = existing.id, ordinal = draft.ordinal,
                sourceHeading = draft.sourceHeading, content = draft.content, createdAt = now, updatedAt = now,
            )
        }
        indexService.invalidate(existing.id)
        repository.save(updated, chunks)
        if (settings.vectorProviderMode == "local") indexService.enqueueIndex(existing.id, remoteConfirmed = false)
        return updated
    }

    private suspend fun savePrepared(
        name: String,
        preview: KnowledgeBaseImportPreview,
        sourceFileName: String,
        sourceFormat: String,
        sourceType: String = "user",
    ): KnowledgeBase {
        require(name.trim().isNotBlank()) { "知识库名称不能为空" }
        val now = Clock.System.now().toEpochMilliseconds()
        val id = newId("kb")
        val knowledgeBase = KnowledgeBase(
            id = id,
            name = name.trim(),
            rawContent = preview.normalizedContent,
            sourceFileName = sourceFileName,
            sourceFormat = sourceFormat,
            sourceType = sourceType,
            createdAt = now,
            updatedAt = now,
        )
        val chunks = preview.chunks.map { draft ->
            KnowledgeBaseChunk(
                id = newId("kbc"),
                knowledgeBaseId = id,
                ordinal = draft.ordinal,
                sourceHeading = draft.sourceHeading,
                content = draft.content,
                createdAt = now,
                updatedAt = now,
            )
        }
        repository.save(knowledgeBase, chunks)
        // Local hashing is free and offline, so imports can index immediately. Remote indexing
        // stays pending until the UI shows its request-count confirmation in the next phase.
        if (settings.vectorProviderMode == "local") {
            indexService.enqueueIndex(knowledgeBase.id, remoteConfirmed = false)
        } else {
            repository.updateIndexStatus(knowledgeBase.id, "pending_confirm")
        }
        return knowledgeBase
    }

    private fun defaultName(fileName: String): String = fileName.substringBeforeLast('.').trim().ifBlank { "未命名知识库" }

    private fun newId(prefix: String): String = "$prefix-${randomPart()}-${randomPart()}"

    private fun randomPart(): String = Random.nextLong().toString().replace("-", "")
}
