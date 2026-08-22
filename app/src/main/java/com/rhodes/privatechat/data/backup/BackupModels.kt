package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.data.ExportPayload
import com.rhodes.privatechat.shared.data.BackupChatDisplayEvent
import com.rhodes.privatechat.shared.data.SharedExperienceParticipant
import com.rhodes.privatechat.shared.model.ChatArchive
import com.rhodes.privatechat.shared.model.ChatHistorySegment
import com.rhodes.privatechat.shared.model.DispatchRecord
import com.rhodes.privatechat.shared.model.GiftRecord
import com.rhodes.privatechat.shared.model.MahjongSave
import com.rhodes.privatechat.shared.model.SharedExperience
import kotlinx.serialization.Serializable
import java.io.InputStream

/** User-facing backup categories. Dependent categories are normalized before use. */
data class BackupContentSelection(
    val roles: Boolean = true,
    val chats: Boolean = true,
    val memories: Boolean = true,
    val social: Boolean = true,
    val knowledgeBases: Boolean = true,
    val extras: Boolean = true,
    val settings: Boolean = true,
    val media: Boolean = true,
) {
    fun normalized(): BackupContentSelection = copy(
        roles = roles || chats || memories || social || knowledgeBases || extras,
        chats = chats || memories,
    )

    fun selectedCategoryCount(): Int = listOf(roles, chats, memories, social, knowledgeBases, extras, settings, media).count { it }

    companion object { val All = BackupContentSelection() }
}

@Serializable
data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val scope: String,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val backupId: String,
    val mediaIncluded: Boolean,
    val recordCounts: Map<String, Int>,
    val files: List<BackupFileEntry>,
    val excludedCategories: List<String>,
    val layout: String = "single_payload",
    val pages: List<BackupPageEntry> = emptyList(),
)

@Serializable
enum class BackupCategory { ROLES, CHATS, MEMORIES, SOCIAL, KNOWLEDGE_BASES, EXTRAS, SETTINGS }

@Serializable
data class BackupPageEntry(
    val category: BackupCategory,
    val pageIndex: Int,
    val path: String,
    val recordCount: Int,
)

@Serializable
data class BackupPayloadPage(
    val category: BackupCategory,
    val pageIndex: Int,
    val payload: BackupPayload,
)

@Serializable
data class BackupFileEntry(
    val path: String,
    val size: Long,
    val sha256: String,
)

@Serializable
data class BackupMediaItem(
    val mediaId: String,
    val archivePath: String,
    val mimeType: String = "application/octet-stream",
    val originalUri: String = "",
)

@Serializable
data class BackupPayload(
    val content: ExportPayload,
    val displayEvents: List<BackupChatDisplayEvent> = emptyList(),
    val chatArchives: List<ChatArchive> = emptyList(),
    val chatHistorySegments: List<ChatHistorySegment> = emptyList(),
    val giftRecords: List<GiftRecord> = emptyList(),
    val dispatchRecords: List<DispatchRecord> = emptyList(),
    val mahjongSave: MahjongSave? = null,
    val sharedExperiences: List<SharedExperience> = emptyList(),
    val sharedExperienceParticipants: List<SharedExperienceParticipant> = emptyList(),
    val media: List<BackupMediaItem> = emptyList(),
)

data class BackupMediaSource(
    val item: BackupMediaItem,
    val openStream: () -> InputStream,
)

data class BackupArchive(
    val manifest: BackupManifest,
    val payload: BackupPayload,
    val issues: List<BackupIssue> = emptyList(),
)

data class BackupRestoreOptions(
    val skipIssueCodes: Set<String> = emptySet(),
    val contentSelection: BackupContentSelection = BackupContentSelection.All,
)

data class BackupIssue(
    val code: String,
    val title: String,
    val detail: String,
    val count: Int,
    val skippable: Boolean = true,
)

sealed interface BackupValidationResult {
    data class Valid(val manifest: BackupManifest, val issues: List<BackupIssue> = emptyList()) : BackupValidationResult
    data class Invalid(val reason: String) : BackupValidationResult
}

class BackupFormatException(message: String) : IllegalArgumentException(message)
