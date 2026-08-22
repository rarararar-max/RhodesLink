package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.data.ExportPayload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Splits the v2 container without changing the portable record serializers. */
object BackupPagePlanner {
    const val PAGE_SIZE = 500
    private const val MAX_PAGE_BYTES = 15 * 1024 * 1024
    private val json = Json { encodeDefaults = true }

    fun pages(payload: BackupPayload): List<BackupPayloadPage> = buildList {
        chunkByEncodedBytes(payload.content.operators.orEmpty(), BackupCategory.ROLES) { records ->
            BackupPayload(ExportPayload(type = "full_backup", operators = records), media = payload.media)
        }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.ROLES, index, BackupPayload(ExportPayload(type = "full_backup", operators = records), media = if (index == 0) payload.media else emptyList())))
        }
        chunkByEncodedBytes(payload.content.relationships.orEmpty(), BackupCategory.ROLES) { records -> BackupPayload(ExportPayload(type = "full_backup", relationships = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.ROLES, 10_000 + index, BackupPayload(ExportPayload(type = "full_backup", relationships = records))))
        }
        if (payload.media.isNotEmpty() && none { it.category == BackupCategory.ROLES }) {
            add(BackupPayloadPage(BackupCategory.ROLES, 90_000, BackupPayload(ExportPayload(type = "full_backup"), media = payload.media)))
        }
        chunkByEncodedBytes(payload.content.sessions.orEmpty(), BackupCategory.CHATS) { records -> BackupPayload(ExportPayload(type = "full_backup", sessions = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.CHATS, index, BackupPayload(ExportPayload(type = "full_backup", sessions = records))))
        }
        chunkByEncodedBytes(payload.content.messages.orEmpty(), BackupCategory.CHATS) { records -> BackupPayload(ExportPayload(type = "full_backup", messages = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.CHATS, 10_000 + index, BackupPayload(ExportPayload(type = "full_backup", messages = records))))
        }
        chunkByEncodedBytes(payload.displayEvents, BackupCategory.CHATS) { records -> BackupPayload(ExportPayload(type = "full_backup"), displayEvents = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.CHATS, 20_000 + index, BackupPayload(ExportPayload(type = "full_backup"), displayEvents = records)))
        }
        chunkByEncodedBytes(payload.chatArchives, BackupCategory.CHATS) { records -> BackupPayload(ExportPayload(type = "full_backup"), chatArchives = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.CHATS, 30_000 + index, BackupPayload(ExportPayload(type = "full_backup"), chatArchives = records)))
        }
        chunkByEncodedBytes(payload.chatHistorySegments, BackupCategory.CHATS) { records -> BackupPayload(ExportPayload(type = "full_backup"), chatHistorySegments = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.CHATS, 40_000 + index, BackupPayload(ExportPayload(type = "full_backup"), chatHistorySegments = records)))
        }
        chunkByEncodedBytes(payload.content.memories.orEmpty(), BackupCategory.MEMORIES) { records -> BackupPayload(ExportPayload(type = "full_backup", memories = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.MEMORIES, index, BackupPayload(ExportPayload(type = "full_backup", memories = records))))
        }
        chunkByEncodedBytes(payload.content.anchors.orEmpty(), BackupCategory.MEMORIES) { records -> BackupPayload(ExportPayload(type = "full_backup", anchors = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.MEMORIES, 10_000 + index, BackupPayload(ExportPayload(type = "full_backup", anchors = records))))
        }
        chunkByEncodedBytes(payload.content.memoryItems.orEmpty(), BackupCategory.MEMORIES) { records -> BackupPayload(ExportPayload(type = "full_backup", memoryItems = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.MEMORIES, 20_000 + index, BackupPayload(ExportPayload(type = "full_backup", memoryItems = records))))
        }
        chunkByEncodedBytes(payload.content.moments.orEmpty(), BackupCategory.SOCIAL) { records -> BackupPayload(ExportPayload(type = "full_backup", moments = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.SOCIAL, index, BackupPayload(ExportPayload(type = "full_backup", moments = records))))
        }
        chunkByEncodedBytes(payload.content.momentComments.orEmpty(), BackupCategory.SOCIAL) { records -> BackupPayload(ExportPayload(type = "full_backup", momentComments = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.SOCIAL, 10_000 + index, BackupPayload(ExportPayload(type = "full_backup", momentComments = records))))
        }
        chunkByEncodedBytes(payload.content.momentLikes.orEmpty(), BackupCategory.SOCIAL) { records -> BackupPayload(ExportPayload(type = "full_backup", momentLikes = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.SOCIAL, 20_000 + index, BackupPayload(ExportPayload(type = "full_backup", momentLikes = records))))
        }
        chunkByEncodedBytes(payload.content.diaries.orEmpty(), BackupCategory.SOCIAL) { records -> BackupPayload(ExportPayload(type = "full_backup", diaries = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.SOCIAL, 30_000 + index, BackupPayload(ExportPayload(type = "full_backup", diaries = records))))
        }
        chunkByEncodedBytes(payload.sharedExperiences, BackupCategory.SOCIAL) { records -> BackupPayload(ExportPayload(type = "full_backup"), sharedExperiences = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.SOCIAL, 40_000 + index, BackupPayload(ExportPayload(type = "full_backup"), sharedExperiences = records)))
        }
        chunkByEncodedBytes(payload.sharedExperienceParticipants, BackupCategory.SOCIAL) { records -> BackupPayload(ExportPayload(type = "full_backup"), sharedExperienceParticipants = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.SOCIAL, 50_000 + index, BackupPayload(ExportPayload(type = "full_backup"), sharedExperienceParticipants = records)))
        }
        chunkByEncodedBytes(payload.content.knowledgeBases.orEmpty(), BackupCategory.KNOWLEDGE_BASES) { records -> BackupPayload(ExportPayload(type = "full_backup", knowledgeBases = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.KNOWLEDGE_BASES, index, BackupPayload(ExportPayload(type = "full_backup", knowledgeBases = records))))
        }
        chunkByEncodedBytes(payload.content.knowledgeBaseChunks.orEmpty(), BackupCategory.KNOWLEDGE_BASES) { records -> BackupPayload(ExportPayload(type = "full_backup", knowledgeBaseChunks = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.KNOWLEDGE_BASES, 10_000 + index, BackupPayload(ExportPayload(type = "full_backup", knowledgeBaseChunks = records))))
        }
        chunkByEncodedBytes(payload.content.operatorKnowledgeBaseAssignments.orEmpty(), BackupCategory.KNOWLEDGE_BASES) { records -> BackupPayload(ExportPayload(type = "full_backup", operatorKnowledgeBaseAssignments = records)) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.KNOWLEDGE_BASES, 20_000 + index, BackupPayload(ExportPayload(type = "full_backup", operatorKnowledgeBaseAssignments = records))))
        }
        chunkByEncodedBytes(payload.giftRecords, BackupCategory.EXTRAS) { records -> BackupPayload(ExportPayload(type = "full_backup"), giftRecords = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.EXTRAS, index, BackupPayload(ExportPayload(type = "full_backup"), giftRecords = records)))
        }
        chunkByEncodedBytes(payload.dispatchRecords, BackupCategory.EXTRAS) { records -> BackupPayload(ExportPayload(type = "full_backup"), dispatchRecords = records) }.forEachIndexed { index, records ->
            add(BackupPayloadPage(BackupCategory.EXTRAS, 10_000 + index, BackupPayload(ExportPayload(type = "full_backup"), dispatchRecords = records)))
        }
        payload.mahjongSave?.let { save -> add(BackupPayloadPage(BackupCategory.EXTRAS, 20_000, BackupPayload(ExportPayload(type = "full_backup"), mahjongSave = save))) }
        payload.content.settings?.let { settings -> add(BackupPayloadPage(BackupCategory.SETTINGS, 0, BackupPayload(ExportPayload(type = "full_backup", settings = settings)))) }
    }

    private fun <T> chunkByEncodedBytes(records: List<T>, category: BackupCategory, payload: (List<T>) -> BackupPayload): List<List<T>> {
        if (records.isEmpty()) return emptyList()
        val result = mutableListOf<MutableList<T>>()
        var current = mutableListOf<T>()
        records.forEach { record ->
            val candidate = current + record
            val probe = BackupPayloadPage(category, 0, payload(candidate))
            if (current.isNotEmpty() && (candidate.size > PAGE_SIZE || json.encodeToString(probe).encodeToByteArray().size > MAX_PAGE_BYTES)) {
                result += current
                current = mutableListOf(record)
            } else current += record
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    fun merge(pages: List<BackupPayloadPage>): BackupPayload {
        val content = ExportPayload(
            version = 5, type = "full_backup",
            operators = pages.flatMap { it.payload.content.operators.orEmpty() }.ifEmpty { null },
            relationships = pages.flatMap { it.payload.content.relationships.orEmpty() }.ifEmpty { null },
            sessions = pages.flatMap { it.payload.content.sessions.orEmpty() }.ifEmpty { null },
            messages = pages.flatMap { it.payload.content.messages.orEmpty() }.ifEmpty { null },
            memories = pages.flatMap { it.payload.content.memories.orEmpty() }.ifEmpty { null },
            anchors = pages.flatMap { it.payload.content.anchors.orEmpty() }.ifEmpty { null },
            moments = pages.flatMap { it.payload.content.moments.orEmpty() }.ifEmpty { null },
            momentLikes = pages.flatMap { it.payload.content.momentLikes.orEmpty() }.ifEmpty { null },
            momentComments = pages.flatMap { it.payload.content.momentComments.orEmpty() }.ifEmpty { null },
            diaries = pages.flatMap { it.payload.content.diaries.orEmpty() }.ifEmpty { null },
            memoryItems = pages.flatMap { it.payload.content.memoryItems.orEmpty() }.ifEmpty { null },
            knowledgeBases = pages.flatMap { it.payload.content.knowledgeBases.orEmpty() }.ifEmpty { null },
            knowledgeBaseChunks = pages.flatMap { it.payload.content.knowledgeBaseChunks.orEmpty() }.ifEmpty { null },
            operatorKnowledgeBaseAssignments = pages.flatMap { it.payload.content.operatorKnowledgeBaseAssignments.orEmpty() }.ifEmpty { null },
            settings = pages.lastOrNull { it.payload.content.settings != null }?.payload?.content?.settings,
        )
        return BackupPayload(content, pages.flatMap { it.payload.displayEvents }, pages.flatMap { it.payload.chatArchives }, pages.flatMap { it.payload.chatHistorySegments }, pages.flatMap { it.payload.giftRecords }, pages.flatMap { it.payload.dispatchRecords }, pages.mapNotNull { it.payload.mahjongSave }.lastOrNull(), pages.flatMap { it.payload.sharedExperiences }, pages.flatMap { it.payload.sharedExperienceParticipants }, pages.flatMap { it.payload.media })
    }
}
