package com.rhodes.privatechat.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Keeps a user-initiated reply recoverable when Android kills the app process. */
object ManualReplyScheduler {
    private const val SESSION_ID = "sessionId"
    private const val MESSAGE_ID = "messageId"
    private const val IS_GROUP = "isGroup"
    private const val TURN_ID = "turnId"

    fun schedule(context: Context, sessionId: String, messageId: Long, isGroup: Boolean) {
        val request = OneTimeWorkRequestBuilder<ManualReplyWorker>()
            // Give the foreground request time to finish before attempting recovery.
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(SESSION_ID to sessionId, MESSAGE_ID to messageId, IS_GROUP to isGroup))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "manual-reply-$messageId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleTurn(context: Context, turnId: String) {
        val request = OneTimeWorkRequestBuilder<ManualReplyWorker>()
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(TURN_ID to turnId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reply-turn-$turnId", ExistingWorkPolicy.KEEP, request)
    }

    fun complete(context: Context, messageId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("manual-reply-$messageId")
    }

    fun completeTurn(context: Context, turnId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reply-turn-$turnId")
    }
}
