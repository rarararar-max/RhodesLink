package com.rhodes.privatechat.shared.knowledge

import com.rhodes.privatechat.shared.data.KnowledgeBaseRepository
import com.rhodes.privatechat.shared.model.KnowledgeBase
import com.rhodes.privatechat.shared.model.KnowledgeBaseChunk
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.datetime.Clock
import kotlin.random.Random

class KnowledgeBaseImportService(
    private val repository: KnowledgeBaseRepository,
    private val indexService: KnowledgeBaseIndexService,
    private val settings: SettingsRepository,
) {
    suspend fun importFile(fileName: String, bytes: ByteArray, name: String = defaultName(fileName)): KnowledgeBase {
        val preview = KnowledgeBaseTextProcessor.prepare(fileName, bytes)
        val format = fileName.substringAfterLast('.', "").lowercase()
        return savePrepared(name, preview, fileName, format)
    }

    suspend fun saveText(name: String, content: String, sourceFormat: String = "txt"): KnowledgeBase {
        val preview = KnowledgeBaseTextProcessor.prepareText(content, sourceFormat)
        return savePrepared(name, preview, "", sourceFormat)
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
        }
        return knowledgeBase
    }

    private fun defaultName(fileName: String): String = fileName.substringBeforeLast('.').trim().ifBlank { "未命名知识库" }

    private fun newId(prefix: String): String = "$prefix-${randomPart()}-${randomPart()}"

    private fun randomPart(): String = Random.nextLong().toString().replace("-", "")
}
