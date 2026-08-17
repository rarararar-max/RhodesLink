package com.rhodes.privatechat.data.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

class BackupFileReader(
    private val maxEntries: Int = 1_000,
    private val maxEntryBytes: Long = 100L * 1024 * 1024,
    private val maxTotalBytes: Long = 500L * 1024 * 1024,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(input: InputStream): BackupValidationResult = runCatching {
        read(input).manifest
    }.fold(
        onSuccess = { BackupValidationResult.Valid(it) },
        onFailure = { BackupValidationResult.Invalid(it.message ?: "备份文件无效") },
    )

    fun read(input: InputStream): BackupArchive {
        var entryCount = 0
        var totalBytes = 0L
        val entries = linkedMapOf<String, EntryDigest>()
        var manifestBytes: ByteArray? = null
        var payloadBytes: ByteArray? = null
        val paths = mutableSetOf<String>()

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > maxEntries) throw BackupFormatException("ZIP 条目数量超过限制")
                val path = entry.name.replace('\\', '/')
                if (!isSafeArchivePath(path)) throw BackupFormatException("ZIP 路径不安全：$path")
                if (!paths.add(path)) throw BackupFormatException("ZIP 存在重复路径：$path")
                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val capture = path == BackupFileWriter.MANIFEST_PATH || path == BackupFileWriter.PAYLOAD_PATH
                val digest = readLimited(zip, maxEntryBytes, capture) { read ->
                    totalBytes += read
                    if (totalBytes > maxTotalBytes) throw BackupFormatException("ZIP 解压总大小超过限制")
                }
                entries[path] = EntryDigest(digest.size, digest.sha256)
                if (path == BackupFileWriter.MANIFEST_PATH) manifestBytes = digest.captured
                if (path == BackupFileWriter.PAYLOAD_PATH) payloadBytes = digest.captured
                zip.closeEntry()
            }
        }

        val manifestContent = manifestBytes
            ?: throw BackupFormatException("缺少 manifest.json")
        val manifest = runCatching { json.decodeFromString<BackupManifest>(manifestContent.decodeToString()) }
            .getOrElse { throw BackupFormatException("manifest.json 无法解析") }
        if (manifest.format != BackupFileWriter.FULL_FORMAT) throw BackupFormatException("不支持的备份类型：${manifest.format}")
        if (manifest.formatVersion != BackupFileWriter.FORMAT_VERSION) throw BackupFormatException("不支持的备份格式版本：${manifest.formatVersion}")
        if (manifest.scope != "full") throw BackupFormatException("这不是完整备份文件")
        if (manifest.files.map { it.path }.distinct().size != manifest.files.size) {
            throw BackupFormatException("manifest 存在重复文件条目")
        }
        if (manifest.files.map { it.path }.toSet() != entries.keys - BackupFileWriter.MANIFEST_PATH) {
            throw BackupFormatException("manifest 文件清单与 ZIP 内容不一致")
        }
        manifest.files.forEach { file ->
            val actual = entries[file.path] ?: throw BackupFormatException("缺少文件：${file.path}")
            if (actual.size != file.size) throw BackupFormatException("文件大小不一致：${file.path}")
            if (actual.sha256 != file.sha256.lowercase()) throw BackupFormatException("文件校验失败：${file.path}")
        }

        val payloadContent = payloadBytes
            ?: throw BackupFormatException("缺少 data/backup.json")
        val payload = runCatching { json.decodeFromString<BackupPayload>(payloadContent.decodeToString()) }
            .getOrElse { throw BackupFormatException("data/backup.json 无法解析") }
        if (payload.content.type != "full_backup") throw BackupFormatException("备份内容不是完整备份")
        if (manifest.mediaIncluded != payload.media.isNotEmpty()) throw BackupFormatException("备份媒体清单与标记不一致")
        validateReferences(payload)
        if (payload.media.map { it.archivePath }.distinct().size != payload.media.size) {
            throw BackupFormatException("媒体清单存在重复路径")
        }
        if (payload.media.map { it.archivePath }.toSet() != entries.keys.filter { it.startsWith("media/") }.toSet()) {
            throw BackupFormatException("媒体清单与 ZIP 内容不一致")
        }
        return BackupArchive(manifest, payload)
    }

    private fun validateReferences(payload: BackupPayload) {
        val operatorIds = payload.content.operators.orEmpty().map { it.id }.toSet()
        val sessionIds = payload.content.sessions.orEmpty().map { it.id }.toSet()
        val momentIds = payload.content.moments.orEmpty().map { it.id }.toSet()
        val commentIds = payload.content.momentComments.orEmpty().map { it.id }.toSet()
        val knowledgeBases = payload.content.knowledgeBases.orEmpty()
        val knowledgeBaseIds = knowledgeBases.map { it.id }.toSet()
        val chunks = payload.content.knowledgeBaseChunks.orEmpty()
        val assignments = payload.content.operatorKnowledgeBaseAssignments.orEmpty()
        if (operatorIds.size != payload.content.operators.orEmpty().size) throw BackupFormatException("备份包含重复角色")
        if (sessionIds.size != payload.content.sessions.orEmpty().size) throw BackupFormatException("备份包含重复会话")
        if (knowledgeBaseIds.size != knowledgeBases.size) throw BackupFormatException("备份包含重复知识库")
        if (chunks.map { it.id }.distinct().size != chunks.size) throw BackupFormatException("备份包含重复知识库分段")
        if (chunks.map { it.knowledgeBaseId to it.ordinal }.distinct().size != chunks.size) throw BackupFormatException("备份包含重复知识库分段序号")
        if (assignments.map { it.operatorId to it.knowledgeBaseId }.distinct().size != assignments.size) throw BackupFormatException("备份包含重复角色知识库关联")
        chunks.forEach { if (it.knowledgeBaseId !in knowledgeBaseIds) throw BackupFormatException("知识库分段引用不存在的知识库") }
        assignments.forEach {
            if (it.operatorId !in operatorIds || it.knowledgeBaseId !in knowledgeBaseIds) throw BackupFormatException("角色知识库关联引用不完整")
        }
        payload.content.relationships.orEmpty().forEach { relationship ->
            if (relationship.operatorId !in operatorIds || relationship.relatedOperatorId !in operatorIds) throw BackupFormatException("角色关系引用了不存在的角色")
        }
        payload.content.sessions.orEmpty().forEach { session ->
            if (session.operatorId !in operatorIds) throw BackupFormatException("会话引用了不存在的角色")
        }
        payload.content.messages.orEmpty().forEach { message ->
            if (message.sessionId !in sessionIds) throw BackupFormatException("聊天消息引用了不存在的会话")
        }
        payload.chatArchives.forEach { archive ->
            if (archive.sessionId !in sessionIds || archive.operatorId !in operatorIds) {
                throw BackupFormatException("聊天存档引用不完整")
            }
        }
        payload.chatHistorySegments.forEach { segment ->
            if (segment.sessionId !in sessionIds) throw BackupFormatException("聊天历史片段引用了不存在的会话")
        }
        payload.giftRecords.forEach { gift ->
            if (gift.operatorId !in operatorIds) throw BackupFormatException("礼物记录引用了不存在的角色")
        }
        payload.content.memories.orEmpty().forEach { memory ->
            if (memory.operatorId !in operatorIds || memory.sessionId !in sessionIds) {
                throw BackupFormatException("记忆引用不完整")
            }
        }
        payload.content.anchors.orEmpty().forEach { anchor ->
            if (anchor.operatorId !in operatorIds || anchor.sessionId !in sessionIds) {
                throw BackupFormatException("记忆锚点引用不完整")
            }
        }
        payload.content.momentComments.orEmpty().forEach { comment ->
            if (comment.momentId !in momentIds || (comment.parentCommentId != 0L && comment.parentCommentId !in commentIds)) throw BackupFormatException("动态评论引用不完整")
        }
        payload.content.momentLikes.orEmpty().forEach { like ->
            if (like.momentId !in momentIds) throw BackupFormatException("动态点赞引用不完整")
        }
        val experienceIds = payload.sharedExperiences.map { it.id }.toSet()
        payload.sharedExperienceParticipants.forEach { participant ->
            if (participant.experienceId !in experienceIds || participant.operatorId !in operatorIds) {
                throw BackupFormatException("共同经历参与者引用不完整")
            }
        }
    }

    private fun readLimited(input: InputStream, limit: Long, capture: Boolean, onRead: (Long) -> Unit): EntryDigest {
        val output = if (capture) java.io.ByteArrayOutputStream() else null
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var size = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            size += read
            if (size > limit) throw BackupFormatException("ZIP 单个文件超过限制")
            onRead(read.toLong())
            digest.update(buffer, 0, read)
            output?.write(buffer, 0, read)
        }
        return EntryDigest(size, digest.digest().toHex(), output?.toByteArray())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private data class EntryDigest(val size: Long, val sha256: String, val captured: ByteArray? = null)

    companion object {
        fun isSafeArchivePath(path: String): Boolean {
            val normalized = path.replace('\\', '/')
            return normalized.isNotBlank() &&
                !normalized.startsWith('/') &&
                !normalized.contains(":/") &&
                normalized.split('/').none { it.isBlank() || it == "." || it == ".." }
        }
    }
}
