package com.rhodes.privatechat.shared.model

/** Removes only the declared historical range. The system and this turn's tail are immutable. */
fun trimPromptHistory(
    messages: MutableList<AiMessage>, historyCount: Int, maxTokens: Int,
    estimate: (String) -> Int,
): Int {
    require(historyCount >= 0 && historyCount < messages.size)
    fun cost(message: AiMessage) = estimate(message.content) + 10
    val required = messages.take(1) + messages.drop(1 + historyCount)
    require(required.sumOf(::cost) <= maxTokens) {
        "本轮必要内容超过手动上下文上限，请提高上下文上限或开启自动上下文；本轮输入未被裁剪"
    }
    var total = messages.sumOf(::cost)
    var removed = 0
    while (removed < historyCount && total > maxTokens) {
        total -= cost(messages.removeAt(1))
        removed++
    }
    return removed
}

fun privateHistoryRoundCount(messages: List<ChatMessage>): Int =
    messages.count { it.isMe } + if (messages.firstOrNull()?.isMe == false) 1 else 0

data class PrivateHistoryWindow(val messages: List<ChatMessage>, val readCount: Int, val capped: Boolean)

/**
 * Fetches newest-first pages, then counts eligible rounds, not database rows. No database migration.
 * A hard row cap bounds pathological sessions; callers must report when it is reached.
 */
suspend fun readPrivateHistoryWindow(
    roundLimit: Int,
    restartAt: Long = 0L,
    excludedIds: Set<Long> = emptySet(),
    beforeMessageId: Long? = null,
    maxRows: Int = 20_000,
    fetchPage: suspend (ChatMessage?, Int) -> List<ChatMessage>,
): PrivateHistoryWindow {
    val newestFirst = mutableListOf<ChatMessage>()
    val wanted = roundLimit.coerceAtLeast(1)
    var capped = false
    while (newestFirst.size < maxRows) {
        val pageSize = minOf(200, maxRows - newestFirst.size)
        val page = fetchPage(newestFirst.lastOrNull(), pageSize)
        if (page.isEmpty()) break
        newestFirst.addAll(page)
        val chronological = newestFirst.asReversed()
        val beforeIndex = beforeMessageId?.let { id -> chronological.indexOfFirst { it.id == id } }
        val beforeFound = beforeIndex == null || beforeIndex >= 0
        val scoped = if (beforeIndex != null && beforeIndex >= 0) chronological.take(beforeIndex) else chronological
        val eligible = scoped.filter {
            it.timestamp >= restartAt && it.id !in excludedIds &&
                it.type !in setOf("system", "send_failed", "gift_reply_failed")
        }
        val reachedStart = page.size < pageSize || page.last().timestamp < restartAt
        if (reachedStart || (beforeFound && eligible.count { it.isMe } >= wanted)) break
        capped = newestFirst.size >= maxRows
    }
    return PrivateHistoryWindow(newestFirst.asReversed().toList(), newestFirst.size, capped)
}
