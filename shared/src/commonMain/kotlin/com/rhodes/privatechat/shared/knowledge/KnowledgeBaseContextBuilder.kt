package com.rhodes.privatechat.shared.knowledge

import com.rhodes.privatechat.shared.data.KnowledgeBaseRepository
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorSearchRequest
import kotlinx.coroutines.withTimeoutOrNull

class KnowledgeBaseContextBuilder(
    private val repository: KnowledgeBaseRepository,
    private val vectorService: MemoryVectorService,
) {
    suspend fun forKnowledgeBase(knowledgeBaseId: String, query: String, maxChars: Int): String {
        val activeSignature = vectorService.currentEmbeddingSignature()
        val book = repository.get(knowledgeBaseId)?.takeIf { KnowledgeBaseRecallPolicy.isUsableIndex(it.indexStatus, it.indexedEmbeddingSignature, activeSignature) } ?: return "无"
        val results = runCatching { vectorService.recall("knowledge_base", book.id, query, limit = 5, minScore = 0.18) }.getOrDefault(emptyList())
        var remaining = maxChars
        val blocks = results.mapNotNull { result ->
            if (remaining <= 0) return@mapNotNull null
            val heading = result.content.substringAfter("章节：", "").substringBefore("\n正文：").trim().escapeReservedMarkers()
            val content = result.content.substringAfter("正文：", result.content).trim().escapeReservedMarkers().take(remaining).trim()
            if (content.isBlank()) return@mapNotNull null
            remaining -= content.length
            "- [知识库：${book.name.escapeReservedMarkers()}]${heading.takeIf { it.isNotBlank() }?.let { "\n章节：$it" }.orEmpty()}\n$content"
        }
        return KnowledgeBaseRecallPolicy.wrapReference(blocks)
    }

    suspend fun forOperator(
        operatorId: String,
        query: String,
        maxEntries: Int,
        maxChars: Int,
        allowedBookIds: Set<String>? = null,
        perBookResults: Int = 1,
        candidateLimit: Int = 160,
        queryEmbeddingTimeoutMs: Long = KnowledgeBaseRecallPolicy.PRIVATE_QUERY_EMBEDDING_TIMEOUT_MS,
        perBookTimeoutMs: Long = KnowledgeBaseRecallPolicy.PRIVATE_PER_BOOK_TIMEOUT_MS,
        workBudgetMs: Long = KnowledgeBaseRecallPolicy.PRIVATE_WORK_BUDGET_MS,
    ): String {
        if (operatorId.isBlank() || query.isBlank()) return "无"
        val assignments = repository.getAssignments(operatorId).filter { it.enabled && (allowedBookIds == null || it.knowledgeBaseId in allowedBookIds) }
        if (assignments.isEmpty()) return "无"
        val activeSignature = vectorService.currentEmbeddingSignature()
        val books = repository.getAll().associateBy { it.id }
        val candidates = mutableListOf<KnowledgeBaseRecallPolicy.Candidate>()
        val deadline = System.currentTimeMillis() + workBudgetMs
        val queryEmbedding = withTimeoutOrNull(queryEmbeddingTimeoutMs) {
            try {
                vectorService.embedForDiagnostics(query)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
        }.orEmpty()
        if (queryEmbedding.isEmpty()) return "无"
        assignments.forEachIndexed assignmentLoop@{ order, assignment ->
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return@assignmentLoop
            val book = books[assignment.knowledgeBaseId]?.takeIf { KnowledgeBaseRecallPolicy.isUsableIndex(it.indexStatus, it.indexedEmbeddingSignature, activeSignature) } ?: return@assignmentLoop
            val results = withTimeoutOrNull(minOf(perBookTimeoutMs, remaining)) {
                try {
                    vectorService.searchWithEmbedding(VectorSearchRequest(
                    ownerType = "knowledge_base", ownerId = book.id, query = query,
                    limit = perBookResults,
                    minScore = 0.12, candidateLimit = candidateLimit,
                ), queryEmbedding)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    emptyList()
                }
            }.orEmpty()
            results.forEach { result ->
                val text = result.content.substringAfter("正文：", result.content).trim().escapeReservedMarkers()
                if (text.isNotBlank()) candidates += KnowledgeBaseRecallPolicy.Candidate(book.name.escapeReservedMarkers(), text, result.similarity, order)
            }
        }
        return renderCandidates(KnowledgeBaseRecallPolicy.selectTop(candidates, maxEntries), maxChars)
    }

    suspend fun forOperators(operatorIds: Collection<String>, query: String, maxEntries: Int, maxChars: Int, allowedBookIds: Set<String>): String {
        if (operatorIds.isEmpty() || allowedBookIds.isEmpty() || query.isBlank()) return "无"
        val books = repository.getAll().associateBy { it.id }
        val activeSignature = vectorService.currentEmbeddingSignature()
        val candidates = mutableListOf<KnowledgeBaseRecallPolicy.Candidate>()
        val seen = mutableSetOf<String>()
        val queryEmbedding = withTimeoutOrNull(GROUP_QUERY_EMBEDDING_TIMEOUT_MS) {
            try {
                vectorService.embedForDiagnostics(query)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                emptyList()
            }
        }.orEmpty()
        if (queryEmbedding.isEmpty()) return "无"
        var order = 0
        val visitedBookIds = mutableSetOf<String>()
        operatorIds.distinct().forEach { operatorId ->
            repository.getAssignments(operatorId).filter { it.enabled && it.knowledgeBaseId in allowedBookIds }.forEach { assignment ->
                if (!visitedBookIds.add(assignment.knowledgeBaseId)) return@forEach
                val book = books[assignment.knowledgeBaseId]?.takeIf { KnowledgeBaseRecallPolicy.isUsableIndex(it.indexStatus, it.indexedEmbeddingSignature, activeSignature) } ?: return@forEach
                val result = withTimeoutOrNull(GROUP_PER_BOOK_TIMEOUT_MS) {
                    try {
                        vectorService.searchWithEmbedding(VectorSearchRequest(
                            ownerType = "knowledge_base", ownerId = book.id, query = query,
                            limit = 1, minScore = GROUP_MIN_SCORE, candidateLimit = GROUP_CANDIDATE_LIMIT,
                        ), queryEmbedding).firstOrNull()
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        null
                    }
                } ?: return@forEach
                val text = result.content.substringAfter("正文：", result.content).trim().escapeReservedMarkers()
                if (text.isBlank() || !seen.add(text)) return@forEach
                candidates += KnowledgeBaseRecallPolicy.Candidate(book.name.escapeReservedMarkers(), text, result.similarity, order++)
            }
        }
        return renderCandidates(KnowledgeBaseRecallPolicy.selectTop(candidates, maxEntries), maxChars)
    }

    private fun renderCandidates(selected: List<KnowledgeBaseRecallPolicy.Candidate>, maxChars: Int): String {
        var usedChars = KnowledgeBaseRecallPolicy.referenceOverhead()
        val blocks = selected.mapIndexedNotNull { index, candidate ->
            val separatorChars = if (index == 0) 0 else 1
            val remaining = maxChars - usedChars - separatorChars
            val header = "- [知识库：${candidate.bookName.take(80)}]\n"
            if (remaining <= header.length) return@mapIndexedNotNull null
            val excerpt = candidate.text.take(remaining - header.length).trim()
            if (excerpt.isBlank()) return@mapIndexedNotNull null
            val block = header + excerpt
            usedChars += separatorChars + block.length
            block
        }
        return KnowledgeBaseRecallPolicy.wrapReference(blocks)
    }

    private fun String.escapeReservedMarkers(): String =
        KnowledgeBaseRecallPolicy.escapeReferenceText(this)

    private companion object {
        const val GROUP_QUERY_EMBEDDING_TIMEOUT_MS = 1_000L
        const val GROUP_PER_BOOK_TIMEOUT_MS = 600L
        const val GROUP_CANDIDATE_LIMIT = 60
        const val GROUP_MIN_SCORE = 0.16
    }

}
