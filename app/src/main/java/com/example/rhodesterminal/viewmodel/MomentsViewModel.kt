package com.example.rhodesterminal.viewmodel

import android.util.Log
import com.example.rhodesterminal.shared.model.MomentComment
import com.example.rhodesterminal.shared.model.MomentLike
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.Prefs
import com.example.rhodesterminal.viewmodel.shared.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers

class MomentsViewModel(
    private val repository: ChatRepository,
    private val prefs: Prefs,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val getUserProfile: () -> UserProfile
) {
    companion object {
        const val DEBUG = true
    }

    fun getLikes(momentId: Long): Flow<List<MomentLike>> = repository.getLikesFlow(momentId)
    fun getCommentsForMoment(momentId: Long): Flow<List<MomentComment>> = repository.getComments(momentId)

    fun likeMoment(momentId: Long, operatorId: String, operatorName: String) {
        scope.launch {
            val existing = repository.getLike(momentId, operatorId)
            if (existing == null) {
                repository.insertLike(MomentLike(momentId = momentId, operatorId = operatorId, operatorName = operatorName))
            }
            val count = repository.getLikeCount(momentId)
            repository.updateLikeCount(momentId, count)
        }
    }

    fun getLatestMomentId(): Long = appState.moments.value.firstOrNull()?.id ?: 0

    fun getMomentBadge(): Int {
        val mp = prefs.moment
        val lastSeenMoment = mp.getLong("last_seen_moment_id", 0)
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

    fun loadInboxComments(callback: (List<MomentComment>) -> Unit) {
        scope.launch {
            val profile = getUserProfile()
            val cutoff = System.currentTimeMillis() - 30L * 86400000L
            callback(repository.getInboxComments(cutoff, profile.nickname))
        }
    }

    fun markMomentsSeen() {
        val latest = appState.moments.value.firstOrNull()?.id ?: 0
        prefs.moment.edit().putLong("last_seen_moment_id", latest).apply()
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
                prefs.moment.edit().putLong("last_seen_comment_id", maxId).apply()
            }
        }
    }
}
