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
)

sealed interface BackupValidationResult {
    data class Valid(val manifest: BackupManifest) : BackupValidationResult
    data class Invalid(val reason: String) : BackupValidationResult
}

class BackupFormatException(message: String) : IllegalArgumentException(message)
