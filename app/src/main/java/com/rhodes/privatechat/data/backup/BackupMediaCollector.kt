package com.rhodes.privatechat.data.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Packages only durable files owned by this app; external URIs remain references, never copies. */
class BackupMediaCollector(private val context: Context) {
    data class Result(
        val items: List<BackupMediaItem>,
        val sources: List<BackupMediaSource>,
        val skippedUris: List<String>,
    )

    fun collect(payload: BackupPayload): Result {
        val accepted = linkedMapOf<String, File>()
        val skipped = linkedSetOf<String>()
        referencedUris(payload).filter { it.isNotBlank() }.forEach { uri ->
            val file = ownedImageFile(uri)
            if (file != null) accepted.putIfAbsent(file.canonicalPath, file) else skipped += uri
        }
        val items = accepted.values.map { file ->
            BackupMediaItem(
                mediaId = "local-image/${stableId(file)}",
                archivePath = "media/images/${stableId(file)}${file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()}",
                mimeType = mimeType(file.extension),
                originalUri = Uri.fromFile(file).toString(),
            )
        }
        val sources = items.zip(accepted.values).map { (item, file) ->
            BackupMediaSource(item) { FileInputStream(file) }
        }
        return Result(items, sources, skipped.toList())
    }

    fun attachCollectedMedia(payload: BackupPayload): Pair<BackupPayload, Result> {
        val result = collect(payload)
        return payload.copy(media = result.items) to result
    }

    private fun referencedUris(payload: BackupPayload): Sequence<String> = sequence {
        yield(decodePortableString(payload.content.settings?.get("user_avatar_uri").orEmpty()))
        for (operator in payload.content.operators.orEmpty()) yield(operator.avatarUri)
        for (session in payload.content.sessions.orEmpty()) yield(session.avatarUri)
        for (gift in payload.giftRecords) yield(gift.imageUri)
        for (message in payload.content.messages.orEmpty()) {
            for (match in imageUriPattern.findAll(message.content)) {
                yield(match.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\"))
            }
        }
        decodePortableString(payload.content.settings?.get("support_conversation").orEmpty())
            .replace("\\\"", "\"").let { conversation ->
                for (match in imageUriPattern.findAll(conversation)) yield(match.groupValues[1].replace("\\\\", "\\"))
            }
    }

    private fun ownedImageFile(value: String): File? {
        val uri = Uri.parse(value)
        if (uri.scheme != "file") return null
        val file = File(uri.path ?: return null)
        if (!file.isFile || !file.canRead()) return null
        val canonical = file.canonicalFile
        return canonical.takeIf { candidate -> ownedRoots.any { root -> candidate.path.startsWith(root.path + File.separator) } }
    }

    private val ownedRoots: List<File>
        get() = listOf("images", "chat_images", "restored_media").map { File(context.filesDir, it).canonicalFile }

    private fun stableId(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.canonicalPath.encodeToByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun decodePortableString(value: String): String =
        if (value.startsWith("s:")) value.removePrefix("s:") else value

    companion object {
        private val imageUriPattern = Regex("\\\"imageUri\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
    }
}
