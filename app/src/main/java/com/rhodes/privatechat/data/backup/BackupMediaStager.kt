package com.rhodes.privatechat.data.backup

import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Extracts only manifest-declared media into an app-owned staging directory. */
class BackupMediaStager {
    fun extract(input: InputStream, payload: BackupPayload, stagingDirectory: File): Map<String, File> {
        val expected = payload.media.associateBy { it.archivePath }
        val restored = linkedMapOf<String, File>()
        val root = stagingDirectory.canonicalFile
        root.mkdirs()
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val item = expected[entry.name]
                if (item != null && !entry.isDirectory) {
                    val target = File(root, item.archivePath).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) throw BackupFormatException("媒体路径不安全")
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> zip.copyTo(output) }
                    restored[item.mediaId] = target
                }
                zip.closeEntry()
            }
        }
        if (restored.keys != payload.media.map { it.mediaId }.toSet()) {
            throw BackupFormatException("备份媒体文件不完整，已取消恢复")
        }
        return restored
    }
}
