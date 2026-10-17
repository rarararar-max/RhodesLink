package com.rhodes.privatechat.util

/**
 * Older display events contain small sequence numbers. New events contain their reveal time.
 * Keep legacy bubbles at their original timestamp; never rewrite stored message timestamps.
 */
internal fun bubbleDisplayTime(messageTime: Long, revealOrder: Long?): Long =
    revealOrder?.takeIf { it >= 1_000_000_000_000L } ?: messageTime

internal fun preserveEditedDraft(current: String, submitted: String): String =
    if (current == submitted) "" else current

/** A fixed header and footer surround the bubbles, even while an older page is loading. */
internal fun chatBottomItemIndex(bubbleCount: Int): Int = bubbleCount.coerceAtLeast(0) + 1

internal fun <T> orderChatBubbles(
    bubbles: List<T>,
    timestamp: (T) -> Long,
    messageId: (T) -> Long,
    segmentIndex: (T) -> Int,
    revealOrder: (T) -> Long?,
): List<T> = bubbles.sortedWith(
    compareBy<T> { bubbleDisplayTime(timestamp(it), revealOrder(it)) }
        .thenBy(timestamp).thenBy(messageId).thenBy(segmentIndex)
)

/** Separate the once-only save result from a reply which may finish much later. */
internal class MessagePersistenceReceipt(private val onResult: (Boolean) -> Unit) {
    private var delivered = false

    fun complete(success: Boolean) {
        if (delivered) return
        delivered = true
        onResult(success)
    }
}
