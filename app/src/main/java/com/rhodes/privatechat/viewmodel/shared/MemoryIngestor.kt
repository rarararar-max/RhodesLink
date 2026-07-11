package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.MomentComment
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.shared.vector.MemoryVectorService
import com.rhodes.privatechat.shared.vector.VectorMemory
import com.rhodes.privatechat.util.DebugLogger

class MemoryIngestor(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val memoryV2Pipeline: MemoryV2Pipeline,
    private val memoryVectorService: MemoryVectorService?,
) {
    suspend fun ingestMoment(moment: Moment) {
        if (settings.globalPublicMemoryEnabled) {
            saveGlobalPublicVector(
                sourceType = "moment",
                sourceId = moment.id.toString(),
                content = "${moment.operatorName}发布动态：${moment.content.take(240)}",
                importance = 0.7,
                createdAt = moment.createdAt,
            )
        }
        if (settings.memoryV2Enabled && settings.momentMemoryV2Enabled) {
            try {
                memoryV2Pipeline.ingestMoment(moment)
            } catch (e: Exception) {
                DebugLogger.log("MemoryV2", "动态L1写入失败: ${e.message?.take(80)}")
            }
        }
    }

    suspend fun ingestMomentComment(comment: MomentComment) {
        if (settings.globalPublicMemoryEnabled) {
            saveGlobalPublicVector(
                sourceType = "moment_comment",
                sourceId = comment.id.takeIf { it > 0 }?.toString() ?: "${comment.momentId}_${comment.createdAt}",
                content = "${comment.operatorName}评论动态：${comment.content.take(200)}",
                importance = 0.55,
                createdAt = comment.createdAt,
            )
        }
        if (settings.memoryV2Enabled && settings.momentMemoryV2Enabled) {
            try {
                memoryV2Pipeline.ingestMomentComment(comment, comment.momentId)
            } catch (e: Exception) {
                DebugLogger.log("MemoryV2", "评论L1写入失败: ${e.message?.take(80)}")
            }
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
