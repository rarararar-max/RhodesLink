package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

class MomentsViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    private val _moments = MutableStateFlow<List<Moment>>(emptyList())
    val moments: StateFlow<List<Moment>> = _moments.asStateFlow()

    private val _comments = MutableStateFlow<List<MomentComment>>(emptyList())
    val comments: StateFlow<List<MomentComment>> = _comments.asStateFlow()

    fun loadComments(momentId: Long) {
        // TODO: 实现加载评论
    }

    fun getLikes(momentId: Long): Flow<List<MomentLike>> {
        return emptyFlow()
    }

    fun getCommentsForMoment(momentId: Long): Flow<List<MomentComment>> {
        return emptyFlow()
    }

    fun likeMoment(momentId: Long, operatorId: String, operatorName: String) {
        // TODO: 实现点赞
    }

    fun commentOnMoment(
        momentId: Long, operatorId: String, operatorName: String,
        content: String, parentCommentId: Long = 0, replyToName: String = ""
    ) {
        // TODO: 实现评论
    }

    fun postUserMoment(content: String, mentionedOperatorIds: String = "") {
        // TODO: 实现发布动态
    }

    fun getLatestMomentId(): Long? {
        return _moments.value.maxByOrNull { it.createdAt }?.id
    }

    fun getUnreadCommentCount(): Int {
        return 0
    }

    fun markAllCommentsRead() {
        // TODO: 实现标记已读
    }
}
