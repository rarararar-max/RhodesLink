package com.rhodes.privatechat.viewmodel

import android.util.Log
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.model.MomentLike
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.util.DebugLogger
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.viewmodel.shared.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MomentsViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val getUserProfile: () -> UserProfile
) {
    companion object {
        const val DEBUG = true
    }

    private val likeMutex = Mutex()

    fun getLikes(momentId: Long): Flow<List<MomentLike>> = repository.getLikesFlow(momentId)
    fun getCommentsForMoment(momentId: Long): Flow<List<MomentComment>> = repository.getComments(momentId)

    fun likeMoment(momentId: Long, operatorId: String, operatorName: String) {
        scope.launch {
            try {
                likeMutex.withLock {
                    val existing = repository.getLike(momentId, operatorId)
                    if (existing == null) {
                        repository.insertLike(MomentLike(momentId = momentId, operatorId = operatorId, operatorName = operatorName, createdAt = System.currentTimeMillis()))
                        DebugLogger.log("Moment", "点赞: momentId=$momentId, user=$operatorName")
                    } else {
                        repository.deleteLike(momentId, operatorId)
                        DebugLogger.log("Moment", "取消点赞: momentId=$momentId, user=$operatorName")
                    }
                    val count = repository.getLikeCount(momentId)
                    repository.updateLikeCount(momentId, count)
                }
                val fresh = withContext(Dispatchers.Default) { repository.getAllMomentsSync() }
                appState.refreshMoments(fresh)
            } catch (e: Exception) {
                DebugLogger.log("Moment/ERROR", "likeMoment: ${e.message}")
                if (DEBUG) Log.e("MomentsVM", "likeMoment error: ${e.message}", e)
            }
        }
    }

    fun getLatestMomentId(): Long = appState.moments.value.firstOrNull()?.id ?: 0

    fun getMomentBadge(): Int {
        val lastSeenMoment = settings.lastSeenMomentId
        val latest = appState.moments.value.firstOrNull()?.id ?: 0
        val momentBadge = if (latest > lastSeenMoment) (appState.moments.value.count { it.id > lastSeenMoment && !it.isUserPost }) else 0
        val commentBadge = getUnreadCommentCount()
        return momentBadge + commentBadge
    }

    fun getUnreadCommentCount(): Int {
        val profile = getUserProfile()
        val cutoff = System.currentTimeMillis() - 30L * 86400000L
        return try {
            runBlocking(Dispatchers.IO) {
                repository.getUnreadCommentCount(cutoff, profile.nickname)
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e("AI调试输出", "getUnreadCommentCount error: ${e.message}")
            0
        }
    }

    suspend fun getUnreadCommentCountSuspend(): Int {
        val profile = getUserProfile()
        val cutoff = System.currentTimeMillis() - 30L * 86400000L
        return try {
            withContext(Dispatchers.IO) {
                repository.getUnreadCommentCount(cutoff, profile.nickname)
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e("AI调试输出", "getUnreadCommentCount error: ${e.message}")
            0
        }
    }

    fun loadInboxComments(callback: (List<MomentComment>) -> Unit) {
        scope.launch {
            val profile = getUserProfile()
            val cutoff = System.currentTimeMillis() - 30L * 86400000L
            callback(repository.getInboxComments(cutoff, profile.nickname))
        }
    }

    fun markMomentsSeen() {
        val latest = appState.moments.value.firstOrNull()?.id ?: 0
        settings.lastSeenMomentId = latest
    }

    fun markCommentRead(commentId: Long) {
        scope.launch { repository.markCommentRead(commentId) }
    }

    fun markAllCommentsRead() {
        scope.launch {
            val userName = getUserProfile().nickname
            repository.markAllCommentsRead(userName)
            val cutoff = System.currentTimeMillis() - 30L * 86400000L
            repository.deleteOldUserComments(cutoff, userName)
            val maxId = repository.getMaxCommentId()
            if (maxId != null && maxId > 0) {
                settings.lastSeenCommentId = maxId
            }
        }
    }
}
