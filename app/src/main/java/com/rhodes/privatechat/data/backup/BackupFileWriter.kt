package com.rhodes.privatechat.data.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupFileWriter(
    private val appVersion: String,
    private val schemaVersion: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    companion object {
        const val FULL_FORMAT = "rhodes-backup"
        const val FORMAT_VERSION = 1
        const val MANIFEST_PATH = "manifest.json"
        const val PAYLOAD_PATH = "data/backup.json"
    }

    private val json = Json { encodeDefaults = true }

    fun writeFullBackup(
        output: OutputStream,
        payload: BackupPayload,
        mediaSources: List<BackupMediaSource> = emptyList(),
        mediaIncluded: Boolean = mediaSources.isNotEmpty(),
    ): BackupManifest {
        require(mediaSources.map { it.item.archivePath }.distinct().size == mediaSources.size) { "媒体 ZIP 路径重复" }
        require(payload.media.map { it.archivePath }.distinct().size == payload.media.size) { "媒体清单路径重复" }
        require(mediaSources.all { BackupFileReader.isSafeArchivePath(it.item.archivePath) && it.item.archivePath.startsWith("media/") }) {
            "媒体路径必须位于 media/ 目录内"
        }
        require(payload.media.map { it.archivePath }.toSet() == mediaSources.map { it.item.archivePath }.toSet()) {
            "媒体清单与媒体数据不一致"
        }

        val entries = mutableListOf<BackupFileEntry>()
        ZipOutputStream(output.buffered()).use { zip ->
            val payloadBytes = json.encodeToString(payload).encodeToByteArray()
            writeBytes(zip, PAYLOAD_PATH, payloadBytes)
            entries += BackupFileEntry(PAYLOAD_PATH, payloadBytes.size.toLong(), sha256(payloadBytes))

            mediaSources.forEach { source ->
                zip.putNextEntry(ZipEntry(source.item.archivePath))
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                BufferedInputStream(source.openStream()).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        zip.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        size += read
                    }
                }
                zip.closeEntry()
                entries += BackupFileEntry(source.item.archivePath, size, digest.digest().toHex())
            }

            val manifest = BackupManifest(
                format = FULL_FORMAT,
                formatVersion = FORMAT_VERSION,
                scope = "full",
                appVersion = appVersion,
                schemaVersion = schemaVersion,
                createdAt = now(),
                backupId = UUID.randomUUID().toString(),
                mediaIncluded = mediaIncluded,
                recordCounts = recordCounts(payload),
                files = entries,
                excludedCategories = listOf("credentials", "vector_index", "worker_state", "temporary_files"),
            )
            writeBytes(zip, MANIFEST_PATH, json.encodeToString(manifest).encodeToByteArray())
            return manifest
        }
    }

    private fun recordCounts(payload: BackupPayload): Map<String, Int> = with(payload.content) {
        mapOf(
            "operators" to operators.orEmpty().size,
            "relationships" to relationships.orEmpty().size,
            "sessions" to sessions.orEmpty().size,
            "messages" to messages.orEmpty().size,
            "diaries" to diaries.orEmpty().size,
            "moments" to moments.orEmpty().size,
            "comments" to momentComments.orEmpty().size,
            "displayEvents" to payload.displayEvents.size,
            "archives" to payload.chatArchives.size,
            "historySegments" to payload.chatHistorySegments.size,
            "gifts" to payload.giftRecords.size,
            "dispatches" to payload.dispatchRecords.size,
            "sharedExperiences" to payload.sharedExperiences.size,
            "knowledgeBases" to knowledgeBases.orEmpty().size,
            "knowledgeBaseChunks" to knowledgeBaseChunks.orEmpty().size,
            "knowledgeBaseAssignments" to operatorKnowledgeBaseAssignments.orEmpty().size,
            "mediaFiles" to payload.media.size,
        )
    }

    private fun writeBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
