package com.rhodes.privatechat.automation

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.data.backup.BackupRestoreMaintenance
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.java.KoinJavaComponent.get
import kotlin.coroutines.resume

class ManualReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            if (BackupRestoreMaintenance.active) return Result.success()
            val sessionId = inputData.getString("sessionId") ?: return Result.success()
            val messageId = inputData.getLong("messageId", 0L)
            if (messageId <= 0L) return Result.success()
            val isGroup = inputData.getBoolean("isGroup", false)
            val repository = get<ChatRepository>(ChatRepository::class.java)
            val message = repository.getMessagesSync(sessionId).firstOrNull { it.id == messageId && it.isMe }
                ?: return Result.success()
            // A later AI row is the reply for this queued turn (including a merged user-message batch).
            if (repository.getMessagesSync(sessionId).any { !it.isMe && it.type == "ai_json" && it.timestamp >= message.timestamp }) {
                return Result.success()
            }
            val settings = get<SettingsRepository>(SettingsRepository::class.java)
            val viewModel = MainViewModel(
                applicationContext as Application, repository, settings,
                get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
                get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false
            )
            val completed = suspendCancellableCoroutine { continuation ->
                if (isGroup) viewModel.resumeGroupReply(sessionId, messageId) { if (continuation.isActive) continuation.resume(it) }
                else viewModel.resumePrivateReply(sessionId, messageId) { if (continuation.isActive) continuation.resume(it) }
            }
            if (BackupRestoreMaintenance.active) return Result.success()
            if (completed) Result.success() else Result.retry()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
