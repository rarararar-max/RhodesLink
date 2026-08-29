package com.rhodes.privatechat.viewmodel

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.TimeSource

internal class ChatStageTimeoutException(
    val surface: String,
    val stage: String,
    val budgetMs: Long,
    val elapsedMs: Long,
    cause: Throwable? = null,
) : Exception("$surface/$stage timed out after ${elapsedMs}ms (budget=${budgetMs}ms)", cause)

internal suspend fun <T> withChatStageTimeout(
    surface: String,
    stage: String,
    budgetMs: Long,
    block: suspend () -> T,
): T {
    val startedAt = TimeSource.Monotonic.markNow()
    return try {
        withTimeout(budgetMs) { block() }
    } catch (error: TimeoutCancellationException) {
        throw ChatStageTimeoutException(
            surface = surface,
            stage = stage,
            budgetMs = budgetMs,
            elapsedMs = startedAt.elapsedNow().inWholeMilliseconds,
            cause = error,
        )
    }
}

internal fun ChatStageTimeoutException.userMessage(label: String): String =
    "$label${stage.chatStageLabel()}超时（预算${budgetMs / 1000}秒），本轮未生成或保存AI回复，请重试"

internal fun String.chatStageLabel(): String = when (this) {
    "prompt_build" -> "提示词构建"
    "operator_read" -> "角色资料读取"
    "short_term_memory_read" -> "滚动摘要读取"
    "history_read" -> "最近对话读取"
    "group_context_read" -> "群聊背景读取"
    "stable_impression_read" -> "稳定印象读取"
    "private_memory_read" -> "私聊记忆读取"
    "relationship_memory_read" -> "关系记忆读取"
    "public_memory_read" -> "公开记忆读取"
    "knowledge_bindings_read" -> "知识库绑定读取"
    "knowledge_recall" -> "知识库召回"
    "model_primary_request" -> "模型请求"
    "model_content_retry" -> "模型内容重试"
    "ai_reply_id" -> "AI消息编号读取"
    "ai_reply_write" -> "AI回复保存"
    "turn_total" -> "整轮处理"
    "group_turn_state_read" -> "群聊连续性读取"
    "group_summary_read" -> "群聊滚动摘要读取"
    "group_relationship_read" -> "群聊关系读取"
    "member_private_memory_read" -> "成员私聊记忆读取"
    "group_memory_read" -> "群聊向量记忆读取"
    "group_public_memory_read" -> "群聊公开记忆读取"
    else -> "阶段[$this]"
}
