package com.rhodes.privatechat.data.backup

import android.content.Context
import android.net.Uri
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.shared.db.DatabaseDispatcher
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class BackupExportStage {
    SNAPSHOT,
    MEDIA,
    WRITE_CACHE,
    VALIDATE_CACHE,
    COPY_DESTINATION,
}

data class BackupExportProgress(
    val stage: BackupExportStage,
    val detail: String,
    val completed: Long = 0L,
    val total: Long = 0L,
)

data class BackupExportResult(
    val manifest: BackupManifest,
    val payload: BackupPayload,
    val media: BackupMediaCollector.Result,
    val stagedFile: File,
)

/** Builds a verified cache artifact before touching a document-provider destination. */
class BackupExportCoordinator(
    private val context: Context,
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val writer: BackupFileWriter,
    private val reader: BackupFileReader,
) {
    suspend fun export(
        destination: Uri,
        selection: BackupContentSelection,
        includeMedia: Boolean,
        onProgress: (BackupExportProgress) -> Unit = {},
    ): BackupExportResult {
        var exportStage = "snapshot"
        onProgress(BackupExportProgress(BackupExportStage.SNAPSHOT, "正在读取备份数据"))
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        var snapshotPart = "initializing"
        val snapshotStarted = android.os.SystemClock.elapsedRealtime()
        DebugLogger.diagnostic("Backup/Export", "stage=snapshot,status=start,selection=$selection,includeMedia=$includeMedia")
        val snapshot = try {
            BackupContentFilter.apply(BackupSnapshotBuilder(repository, settings).build { name, elapsedMs ->
                snapshotPart = name
                DebugLogger.diagnostic("Backup/SnapshotRead", "part=$name,status=done,elapsedMs=$elapsedMs")
            }, selection)
        } catch (error: Throwable) {
            DebugLogger.diagnostic("Backup/Export", "stage=snapshot,status=failed,part=$snapshotPart,elapsedMs=${android.os.SystemClock.elapsedRealtime() - snapshotStarted},errorClass=${error.javaClass.simpleName},errorMessage=${error.message?.replace('\n', ' ')?.take(160)}")
            throw error
        }
        DebugLogger.diagnostic("Backup/Export", "stage=snapshot,status=done,elapsedMs=${android.os.SystemClock.elapsedRealtime() - snapshotStarted},lastPart=$snapshotPart")
        exportStage = "media"
        onProgress(BackupExportProgress(BackupExportStage.MEDIA, "正在整理本机图片"))
        val (payload, media) = if (includeMedia) BackupMediaCollector(context).attachCollectedMedia(snapshot)
        else snapshot to BackupMediaCollector.Result(emptyList(), emptyList(), emptyList())
        val dir = File(context.cacheDir, "backup-exports")
        check(dir.exists() || dir.mkdirs()) { "无法创建备份缓存目录" }
        val final = File(dir, "rhodes_${System.currentTimeMillis()}.rbackup")
        val partial = File(dir, "${final.name}.partial")
        try {
            exportStage = "write_cache"
            onProgress(BackupExportProgress(BackupExportStage.WRITE_CACHE, "正在写入本地暂存备份"))
            val manifest = FileOutputStream(partial).use { output ->
                writer.writeFullBackup(
                    output, payload, media.sources, media.sources.isNotEmpty(),
                    onMediaProgress = { completed, total, bytes ->
                        onProgress(BackupExportProgress(BackupExportStage.WRITE_CACHE, "正在写入图片 $completed/$total，已写入 $bytes 字节", completed.toLong(), total.toLong()))
                    },
                    ensureActive = { coroutineContext.ensureActive() },
                )
            }
            coroutineContext.ensureActive()
            check(partial.renameTo(final)) { "无法完成备份暂存" }
            exportStage = "validate_cache"
            onProgress(BackupExportProgress(BackupExportStage.VALIDATE_CACHE, "正在校验本地暂存备份"))
            val validation = FileInputStream(final).use(reader::validate)
            check(validation is BackupValidationResult.Valid) {
                (validation as? BackupValidationResult.Invalid)?.reason ?: "暂存备份校验失败"
            }
            exportStage = "copy_destination"
            onProgress(BackupExportProgress(BackupExportStage.COPY_DESTINATION, "正在保存到所选位置", 0L, final.length()))
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                FileInputStream(final).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(BackupExportProgress(BackupExportStage.COPY_DESTINATION, "正在保存到所选位置", copied, final.length()))
                    }
                    output.flush()
                }
            } ?: error("无法写入所选位置")
            DebugLogger.diagnostic("Backup/Export", "stage=copy_destination,status=done,bytes=${final.length()}")
            return BackupExportResult(manifest, payload, media, final)
        } catch (error: Throwable) {
            val db = DatabaseDispatcher.snapshot()
            DebugLogger.diagnostic("Backup/Export", "stage=$exportStage,status=failed,errorClass=${error.javaClass.simpleName},errorMessage=${error.message?.replace('\n', ' ')?.take(160)},dbTask=${db.runningTask},dbRunningMs=${db.runningForMs},dbQueued=${db.queuedTasks}")
            partial.delete()
            final.delete()
            throw error
        }
    }
}
