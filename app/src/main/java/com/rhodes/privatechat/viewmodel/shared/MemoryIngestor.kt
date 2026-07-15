package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorMemory
import com.rhodes.privatechat.util.DebugLogger

class MemoryIngestor(
    private val settings: SettingsRepository,
    private val memoryVectorService: MemoryVectorService?,
) {
    suspend fun ingestMoment(moment: Moment) {
        if (settings.globalPublicMemoryEnabled) {
            saveGlobalPublicVector(
                sourceType = "moment",
                sourceId = moment.id.toString(),
                content = "${moment.operatorName}在${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(java.util.Date(moment.createdAt))}发表公开动态：${moment.content.take(240)}",
                importance = 0.7,
                createdAt = moment.createdAt,
            )
        }
    }

    suspend fun ingestMomentComment(comment: MomentComment) {
        if (settings.globalPublicMemoryEnabled) {
            saveGlobalPublicVector(
                sourceType = "moment_comment",
                sourceId = comment.id.takeIf { it > 0 }?.toString() ?: "${comment.momentId}_${comment.createdAt}",
                content = "${comment.operatorName}在${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }.format(java.util.Date(comment.createdAt))}发表公开评论：${comment.content.take(200)}",
                importance = 0.55,
                createdAt = comment.createdAt,
            )
        }
    }

    suspend fun ingestOperatorMomentComment(comment: MomentComment) {
        val service = memoryVectorService ?: return
        val clean = "${comment.operatorName}评论动态：${comment.content.take(200)}".trim()
        if (clean.isBlank()) return
        try {
            service.saveMemory(VectorMemory(
                id = "operator_comment_${comment.operatorId}_${comment.momentId}_${comment.createdAt}",
                ownerType = "operator", ownerId = comment.operatorId,
                sourceType = "moment_comment", sourceId = comment.id.toString(),
                content = clean, importance = 0.45, tags = "COMMENT",
                visibility = "public", createdAt = comment.createdAt,
                expiresAt = MemoryPolicy.memoryExpiresAt(settings)
            ))
        } catch (e: Exception) {
            DebugLogger.log("Vector/Save", "干员评论向量写入失败: ${e.message?.take(80)}")
        }
    }

    suspend fun saveGlobalPublicVector(sourceType: String, sourceId: String, content: String, importance: Double, createdAt: Long = System.currentTimeMillis()) {
        val service = memoryVectorService ?: return
        val clean = content.trim()
        if (clean.isBlank()) return
        try {
            service.saveMemory(VectorMemory(
                id = "global_${sourceType}_${sourceId}_${clean.hashCode()}",
                ownerType = "global",
                ownerId = "public",
                sourceType = sourceType,
                sourceId = sourceId,
                content = clean.take(500),
                importance = importance,
                tags = sourceType.uppercase(),
                visibility = "public",
                createdAt = createdAt,
                expiresAt = MemoryPolicy.memoryExpiresAt(settings),
            ))
        } catch (e: Exception) {
            DebugLogger.log("Vector/Save", "全局公开向量写入失败: ${e.message?.take(80)}")
        }
    }
}
