package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.data.OperatorExport
import com.rhodes.privatechat.data.RelationshipExport
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class OperatorPackagePayload(
    val operator: OperatorExport,
    val relationships: List<RelationshipExport>,
    val avatar: BackupMediaItem? = null,
    /** Editor-owned prompt slots live in preferences, so they travel separately from operator fields. */
    val promptSlots: OperatorPromptSlots? = null,
)

@Serializable
data class OperatorPromptSlots(
    val privateSlots: List<String> = emptyList(),
    val groupSlots: List<String> = emptyList(),
    val activePrivateSlot: Int = 1,
    val activeGroupSlot: Int = 1,
)

data class OperatorPackage(val manifest: BackupManifest, val payload: OperatorPackagePayload, val avatarBytes: ByteArray? = null)

class OperatorPackageWriter(private val appVersion: String, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { encodeDefaults = true }

    fun write(output: OutputStream, payload: OperatorPackagePayload, avatar: BackupMediaSource? = null): BackupManifest {
        require((payload.avatar == null) == (avatar == null)) { "角色头像清单与文件不一致" }
        val files = mutableListOf<BackupFileEntry>()
        ZipOutputStream(output.buffered()).use { zip ->
            val body = json.encodeToString(payload).encodeToByteArray()
            put(zip, "data/operator.json", body)
            files += BackupFileEntry("data/operator.json", body.size.toLong(), sha256(body))
            if (avatar != null) {
                val bytes = avatar.openStream().use { it.readBytes() }
                put(zip, avatar.item.archivePath, bytes)
                files += BackupFileEntry(avatar.item.archivePath, bytes.size.toLong(), sha256(bytes))
            }
            val manifest = BackupManifest(
                format = "rhodes-operator", formatVersion = 1, scope = "operator_card",
                appVersion = appVersion, schemaVersion = 1, createdAt = now(), backupId = UUID.randomUUID().toString(),
                mediaIncluded = avatar != null,
                recordCounts = mapOf("operators" to 1, "relationships" to payload.relationships.size, "mediaFiles" to if (avatar == null) 0 else 1),
                files = files, excludedCategories = listOf("chat_history", "memories", "diaries", "moments", "credentials"),
            )
            put(zip, "manifest.json", json.encodeToString(manifest).encodeToByteArray())
            return manifest
        }
    }

    private fun put(zip: ZipOutputStream, path: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}

class OperatorPackageReader {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(input: InputStream): OperatorPackage {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    if (!BackupFileReader.isSafeArchivePath(entry.name)) throw BackupFormatException("角色包路径不安全")
                    if (entries.size >= MAX_ENTRIES) throw BackupFormatException("角色包文件数量超过限制")
                    val bytes = readLimited(zip)
                    totalBytes += bytes.size
                    if (totalBytes > MAX_TOTAL_BYTES) throw BackupFormatException("角色包解压总大小超过限制")
                    if (entries.put(entry.name, bytes) != null) throw BackupFormatException("角色包存在重复路径")
                }
                zip.closeEntry()
            }
        }
        val manifest = json.decodeFromString<BackupManifest>(entries["manifest.json"]?.decodeToString() ?: throw BackupFormatException("缺少 manifest.json"))
        if (manifest.format != "rhodes-operator" || manifest.formatVersion != 1 || manifest.scope != "operator_card") throw BackupFormatException("不支持的角色包格式")
        if (manifest.files.map { it.path }.distinct().size != manifest.files.size) throw BackupFormatException("角色包清单存在重复文件")
        if (manifest.files.map { it.path }.toSet() != entries.keys - "manifest.json") throw BackupFormatException("角色包清单与文件不一致")
        manifest.files.forEach { file ->
            val bytes = entries[file.path] ?: throw BackupFormatException("缺少文件：${file.path}")
            if (bytes.size.toLong() != file.size || sha256(bytes) != file.sha256.lowercase()) throw BackupFormatException("角色包校验失败：${file.path}")
        }
        val payload = json.decodeFromString<OperatorPackagePayload>(entries["data/operator.json"]?.decodeToString() ?: throw BackupFormatException("缺少角色资料"))
        if ((payload.avatar == null) != !manifest.mediaIncluded) throw BackupFormatException("角色包头像清单不一致")
        payload.avatar?.let { avatar -> if (entries[avatar.archivePath] == null) throw BackupFormatException("角色包缺少头像文件") }
        return OperatorPackage(manifest, payload, payload.avatar?.let { entries[it.archivePath] })
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun readLimited(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_ENTRY_BYTES) throw BackupFormatException("角色包单个文件超过限制")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_ENTRIES = 16
        const val MAX_ENTRY_BYTES = 20 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 25L * 1024 * 1024
    }
}
