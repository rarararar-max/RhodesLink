package com.rhodes.privatechat.automation

import android.app.Application
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.ReplyTurnStatus
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.MainViewModel
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.viewmodel.shared.OperatorStateUpdater
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.data.backup.BackupRestoreMaintenance
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.java.KoinJavaComponent.get
import kotlin.coroutines.resume
import java.util.UUID

class ManualReplyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        var ownedTurnId = ""
        var ownedToken = ""
        return try {
            if (BackupRestoreMaintenance.active) return Result.success()
            val turnId = inputData.getString("turnId")
            val repository = get<ChatRepository>(ChatRepository::class.java)
            if (turnId != null) {
                val current = repository.replyTurns.get(turnId) ?: return Result.success()
                if (current.status == ReplyTurnStatus.SUCCEEDED) return Result.success()
                val token = UUID.randomUUID().toString()
                val claimed = repository.replyTurns.claim(turnId, token, System.currentTimeMillis(), System.currentTimeMillis() + 180_000L)
                    ?: return Result.retry()
                ownedTurnId = turnId
                ownedToken = token
                val messageId = claimed.sourceMessageId ?: return Result.success()
                val settings = get<SettingsRepository>(SettingsRepository::class.java)
                val viewModel = MainViewModel(applicationContext as Application, repository, settings,
                    get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
                    get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false)
                val completed = suspendCancellableCoroutine { continuation ->
                    if (claimed.triggerKind == "image") {
                        if (claimed.surface == "group") viewModel.resumeGroupImageReply(claimed.sessionId, messageId, { if (continuation.isActive) continuation.resume(it) }, token)
                        else viewModel.resumePrivateImageReply(claimed.sessionId, messageId, { if (continuation.isActive) continuation.resume(it) }, token)
                    } else if (claimed.surface == "group") viewModel.resumeGroupReply(claimed.sessionId, messageId, onComplete = { if (continuation.isActive) continuation.resume(it) }, replyTurnId = turnId, replyLeaseToken = token)
                    else viewModel.resumePrivateReply(claimed.sessionId, messageId, onComplete = { if (continuation.isActive) continuation.resume(it) }, replyTurnId = turnId, replyLeaseToken = token)
                }
                if (completed) repository.replyTurns.complete(turnId, token, System.currentTimeMillis())
                else repository.replyTurns.release(turnId, token, System.currentTimeMillis() + 60_000L, System.currentTimeMillis(), "worker_reply_incomplete")
                ownedTurnId = ""
                ownedToken = ""
                return if (completed) Result.success() else Result.retry()
            }
            val sessionId = inputData.getString("sessionId") ?: return Result.success()
            val messageId = inputData.getLong("messageId", 0L)
            if (messageId <= 0L) return Result.success()
            val isGroup = inputData.getBoolean("isGroup", false)
            val message = repository.getMessagesSync(sessionId).firstOrNull { it.id == messageId && it.isMe }
                ?: return Result.success()
            // Upgrades can still have old WorkManager input. Convert it to the durable turn
            // protocol instead of guessing completion from the timestamp of another AI row.
            val legacyTurnId = "${if (isGroup) "group" else "private"}:$sessionId:$messageId"
            val now = System.currentTimeMillis()
            repository.createReplyTurn(com.rhodes.privatechat.shared.model.ReplyTurn(
                legacyTurnId, sessionId, if (isGroup) "group" else "private",
                if (message.type == "image") "image" else if (message.type == "gift_hidden" || message.type == "gift_reply_failed") "gift" else "manual",
                messageId, "", message.mode, "pending", 0, now, "", 0, null, "", now, now, 0,
            ))
            val token = UUID.randomUUID().toString()
            val claimed = repository.replyTurns.claim(legacyTurnId, token, now, now + 180_000L) ?: return Result.success()
            ownedTurnId = legacyTurnId
            ownedToken = token
            val settings = get<SettingsRepository>(SettingsRepository::class.java)
            val viewModel = MainViewModel(
                applicationContext as Application, repository, settings,
                get<AppStateHolder>(AppStateHolder::class.java), get<SharedUtils>(SharedUtils::class.java),
                get<OperatorStateUpdater>(OperatorStateUpdater::class.java), startBackgroundWork = false
            )
            val completed = suspendCancellableCoroutine { continuation ->
                if (isGroup) viewModel.resumeGroupReply(sessionId, messageId, onComplete = { if (continuation.isActive) continuation.resume(it) }, replyTurnId = legacyTurnId, replyLeaseToken = token)
                else viewModel.resumePrivateReply(sessionId, messageId, onComplete = { if (continuation.isActive) continuation.resume(it) }, replyTurnId = legacyTurnId, replyLeaseToken = token)
            }
            if (BackupRestoreMaintenance.active) return Result.success()
            if (completed) repository.replyTurns.complete(legacyTurnId, token, System.currentTimeMillis())
            else repository.replyTurns.release(legacyTurnId, token, System.currentTimeMillis() + 60_000L, System.currentTimeMillis(), "legacy_worker_reply_incomplete")
            ownedTurnId = ""
            ownedToken = ""
            if (completed) Result.success() else Result.retry()
        } catch (_: Exception) {
            if (ownedTurnId.isNotBlank() && ownedToken.isNotBlank()) {
                runCatching { get<ChatRepository>(ChatRepository::class.java).replyTurns.release(ownedTurnId, ownedToken, System.currentTimeMillis() + 60_000L, System.currentTimeMillis(), "worker_exception") }
            }
            Result.retry()
        }
    }
}
