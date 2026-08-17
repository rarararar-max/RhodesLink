package com.rhodes.privatechat.data.backup

import android.content.Context
import androidx.work.WorkManager
import com.rhodes.privatechat.automation.DailyContentScheduler
import com.rhodes.privatechat.automation.GroupAutoChatScheduler
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

enum class BackupRestoreStage {
    VALIDATING,
    CREATING_SAFETY_BACKUP,
    STOPPING_BACKGROUND_WORK,
    RESTORING_DATABASE,
    RESTORING_MEDIA,
    REBUILDING_RUNTIME,
    COMPLETED,
    FAILED,
}

data class BackupRestoreProgress(val stage: BackupRestoreStage, val detail: String)

data class BackupRestoreResult(
    val success: Boolean,
    val stage: BackupRestoreStage,
    val manifest: BackupManifest? = null,
    val safetyBackup: File? = null,
    val reason: String = "",
)

/**
 * Coordinates the destructive portion of a restore. The executor is intentionally supplied by
 * the persistence layer: it must restore into a single database transaction or staging database.
 */
class BackupRestoreCoordinator(
    private val context: Context,
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val snapshotBuilder: BackupSnapshotBuilder,
    private val fileWriter: BackupFileWriter,
    private val fileReader: BackupFileReader = BackupFileReader(),
    private val restoreExecutor: suspend (BackupPayload, (BackupRestoreProgress) -> Unit) -> Unit,
) {
    private val mutex = Mutex()

    suspend fun inspect(input: InputStream): BackupValidationResult = fileReader.validate(input)

    suspend fun restore(openInput: () -> InputStream, onProgress: (BackupRestoreProgress) -> Unit): BackupRestoreResult = mutex.withLock {
        var safetyBackup: File? = null
        var manifest: BackupManifest? = null
        var previousSettings: Map<String, String>? = null
        var restoreInput: File? = null
        try {
            onProgress(BackupRestoreProgress(BackupRestoreStage.VALIDATING, "正在校验备份文件"))
            restoreInput = copyRestoreInput(openInput)
            val verifiedInput = requireNotNull(restoreInput)
            val archive = try {
                verifiedInput.inputStream().use(fileReader::read)
            } catch (e: Exception) {
                verifiedInput.delete()
                throw e
            }
            manifest = archive.manifest

            onProgress(BackupRestoreProgress(BackupRestoreStage.STOPPING_BACKGROUND_WORK, "正在停止后台任务"))
            BackupRestoreMaintenance.begin()
            cancelApplicationWork()

            // Freeze app-owned writers before snapshotting the current world. Otherwise an
            // automatic reply could land between the safety backup and destructive restore.
            onProgress(BackupRestoreProgress(BackupRestoreStage.CREATING_SAFETY_BACKUP, "正在创建恢复前安全备份"))
            safetyBackup = createSafetyBackup()

            onProgress(BackupRestoreProgress(BackupRestoreStage.RESTORING_MEDIA, "正在准备图片文件"))
            val mediaDirectory = File(context.filesDir, "restored_media/${archive.manifest.backupId}")
            val stagedMedia = if (archive.payload.media.isEmpty()) emptyMap() else {
                verifiedInput.inputStream().use { BackupMediaStager().extract(it, archive.payload, mediaDirectory) }
            }
            val restoredPayload = BackupRestorePayloadRewriter.rewriteMediaUris(archive.payload, stagedMedia)

            onProgress(BackupRestoreProgress(BackupRestoreStage.RESTORING_DATABASE, "正在恢复结构化数据"))
            try {
                // Preferences are outside SQLite. Apply them before the transaction so a failed
                // database restore can restore the old preference snapshot in the same path.
                previousSettings = PortableSettings.snapshot(repository, settings)
                PortableSettings.clearForRestore(repository, settings)
                applySettings(restoredPayload.content.settings.orEmpty())
                restoreExecutor(restoredPayload, onProgress)
            } catch (e: Exception) {
                mediaDirectory.deleteRecursively()
                previousSettings?.let(::applySettings)
                throw e
            }

            onProgress(BackupRestoreProgress(BackupRestoreStage.REBUILDING_RUNTIME, "正在重新安排自动内容"))
            // Runtime schedules are derived state. A scheduler issue must not turn an already
            // committed database restore into a false failure or a destructive second restore.
            runCatching { DailyContentScheduler.schedulePlanner(context) }
            runCatching { GroupAutoChatScheduler.reconcile(context, repository, settings) }
            BackupRestoreMaintenance.finish()
            onProgress(BackupRestoreProgress(BackupRestoreStage.COMPLETED, "恢复完成"))
            BackupRestoreResult(true, BackupRestoreStage.COMPLETED, manifest, safetyBackup)
        } catch (e: CancellationException) {
            restoreInput?.delete()
            BackupRestoreMaintenance.finish()
            throw e
        } catch (e: Exception) {
            restoreInput?.delete()
            BackupRestoreMaintenance.finish()
            onProgress(BackupRestoreProgress(BackupRestoreStage.FAILED, e.message ?: "恢复失败"))
            BackupRestoreResult(false, BackupRestoreStage.FAILED, manifest, safetyBackup, e.message ?: "恢复失败")
        } finally {
            restoreInput?.delete()
        }
    }

    private fun copyRestoreInput(openInput: () -> InputStream): File {
        val directory = File(context.cacheDir, "restore-inputs").apply { mkdirs() }
        val target = File.createTempFile("restore_", ".rbackup", directory)
        try {
            openInput().use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
            return target
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    private suspend fun createSafetyBackup(): File {
        val snapshot = snapshotBuilder.build()
        val collector = BackupMediaCollector(context)
        val (payload, media) = collector.attachCollectedMedia(snapshot)
        val directory = File(context.filesDir, "restore-safety-backups").apply { mkdirs() }
        val file = File(directory, "before_restore_${System.currentTimeMillis()}.rbackup")
        file.outputStream().use { fileWriter.writeFullBackup(it, payload, media.sources, mediaIncluded = media.sources.isNotEmpty()) }
        file.inputStream().use { input ->
            if (fileReader.validate(input) !is BackupValidationResult.Valid) {
                file.delete()
                throw BackupFormatException("恢复前安全备份校验失败")
            }
        }
        directory.listFiles()
            ?.filter { it.isFile && it.extension == "rbackup" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }
        return file
    }

    private suspend fun cancelApplicationWork() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork("daily-content-plan")
        repository.getAllSessionsSync()
            .filter { it.operatorId.startsWith("group_") || it.operatorId.startsWith("group") }
            .forEach { GroupAutoChatScheduler.cancel(context, settings, it.id) }
    }

    private fun applySettings(values: Map<String, String>) {
        PortableSettings.apply(settings, values)
    }
}
