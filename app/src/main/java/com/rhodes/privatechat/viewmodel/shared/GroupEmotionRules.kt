package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.GroupMsgResult

/**
 * 群聊成员心情的纯逻辑：清洗、归属、快照读写与“本轮缺心情就沿用上一轮”的回退判定。
 *
 * 设计要点（与提示词的【心情】/【成员心情】协议一一对应）：
 * - 本轮真实写出的心情只保存在该轮消息 JSON 的 emotion 字段里，回退值不写进 JSON；
 * - 群聊回合状态里的 memberEmotions 是“本轮展示用的心情”快照，回退来的值额外记录在 emotionFallbackKeys；
 * - 回退有效期是 6 小时时间窗，不是自然日；本轮没有发言的成员不参与回退；
 * - 归属优先用 speakerId，老数据 speakerId 为空时退回显示名匹配。
 *
 * 这些函数不依赖 Android 与数据库，方便单测直接调用。
 */
object GroupEmotionRules {

    /** 回退有效期：6 小时时间窗。 */
    const val FALLBACK_WINDOW_MS = 6L * 60 * 60 * 1000

    /** 心情短词最长 10 字（协议要求 2~6 字，这里只做兜底截断）。 */
    const val EMOTION_MAX_CHARS = 10

    /** 群聊回合状态里【成员心情】的字数上限，与提示词一致。 */
    const val SNAPSHOT_MAX_CHARS = 60

    /** 快照最多保留的成员数，避免异常输出把状态字段撑爆。 */
    const val MAX_SNAPSHOT_ENTRIES = 12

    private const val EMOTION_PUNCTUATION = "。！？，；、"

    /** 历史里出现过的一条成员心情，用于回退扫描。 */
    data class TrackedEmotion(
        val speakerId: String,
        val speakerName: String,
        val emotion: String,
        val spokenAtMs: Long
    )

    /** 一轮的成员心情快照结果。 */
    data class RoundEmotions(
        /** 标识 -> 心情（标识优先 speakerId，缺失时退回显示名）。 */
        val emotions: Map<String, String>,
        /** 属于回退（本轮没写、沿用旧值）的标识。 */
        val inheritedKeys: Set<String>
    )

    /** 注入提示词用的一条【上轮成员情绪】。 */
    data class PromptEmotion(val key: String, val emotion: String, val inherited: Boolean)

    /** 心情短词清洗：去标记/引号，截到第一个标点前，再截断到最多 10 字；空串表示缺失。 */
    fun cleanEmotion(raw: String): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""
        text = text.trimStart('-', '*', '•', '·', '　', ' ')
        text = text.removePrefix("心情").removePrefix("情绪").trimStart('：', ':', ' ', '　')
        text = text.trim(
            '"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』',
            '【', '】', '（', '）', '(', ')', '《', '》'
        )
        if (text.isEmpty()) return ""
        val cut = text.indexOfFirst { it in EMOTION_PUNCTUATION }
        if (cut >= 0) text = text.substring(0, cut)
        text = text.trim().trim(
            '"', '\'', '“', '”', '‘', '’', '「', '」', '『', '』', '【', '】',
            '（', '）', '(', ')', '《', '》', '：', ':', '、', ' '
        )
        return text.take(EMOTION_MAX_CHARS)
    }

    /** 成员在群聊状态/快照里的键：优先发言标识，老数据没有标识时退回显示名。 */
    fun memberKey(speakerId: String, speakerName: String): String =
        speakerId.trim().ifBlank { speakerName.trim() }

    /** 时间窗判断：情绪产生时刻距 nowMs 不超过窗口时可用；未知时间（<=0）视为不可用。 */
    fun isFallbackWithinWindow(nowMs: Long, emotionAtMs: Long, windowMs: Long = FALLBACK_WINDOW_MS): Boolean {
        if (emotionAtMs <= 0L) return false
        return nowMs - emotionAtMs <= windowMs
    }

    /** 解析 `标识=心情;标识=心情` 形式的【成员心情】字段。空值、坏值直接丢弃。 */
    fun parseEmotionSnapshot(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        raw.split(';', '；', '\n', '|').forEach { part ->
            if (result.size >= MAX_SNAPSHOT_ENTRIES) return@forEach
            val separator = part.indexOfFirst { it == '=' || it == '：' || it == ':' }
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator).trim().trim('-', '*', '•', '·', ' ')
            val emotion = cleanEmotion(part.substring(separator + 1))
            if (key.isBlank() || emotion.isBlank()) return@forEach
            if (!result.containsKey(key)) result[key] = emotion
        }
        return result
    }

    /** 按 `标识=心情` 分号分隔输出，整体不超过 maxChars（超出的成员整条丢弃，不写半截）。 */
    fun formatEmotionSnapshot(emotions: Map<String, String>, maxChars: Int = SNAPSHOT_MAX_CHARS): String {
        val parts = mutableListOf<String>()
        var length = 0
        for ((rawKey, rawEmotion) in emotions) {
            if (parts.size >= MAX_SNAPSHOT_ENTRIES) break
            val key = rawKey.trim()
            val emotion = cleanEmotion(rawEmotion)
            if (key.isBlank() || emotion.isBlank()) continue
            val part = "$key=$emotion"
            val extra = if (parts.isEmpty()) part.length else part.length + 1
            if (length + extra > maxChars) break
            parts += part
            length += extra
        }
        return parts.joinToString(";")
    }

    /** 解析 emotionFallbackKeys：标识用分号/逗号分隔。 */
    fun parseKeyList(raw: String): Set<String> =
        raw.split(';', '；', ',', '，', '\n', '|', ' ')
            .map { it.trim().trim('-', '*', '•', '·') }
            .filter { it.isNotBlank() }
            .toSet()

    /** 输出 emotionFallbackKeys，整体不超过 maxChars。 */
    fun formatKeyList(keys: Collection<String>, maxChars: Int = SNAPSHOT_MAX_CHARS): String {
        val parts = mutableListOf<String>()
        var length = 0
        for (rawKey in keys) {
            val key = rawKey.trim()
            if (key.isBlank()) continue
            val extra = if (parts.isEmpty()) key.length else key.length + 1
            if (length + extra > maxChars) break
            parts += key
            length += extra
        }
        return parts.joinToString(";")
    }

    /**
     * 在历史心情里找该成员最近一次非空心情。
     * 归属优先 speakerId；只有一方缺少 speakerId（老数据）时才按显示名匹配。
     */
    fun findRecentEmotion(
        history: List<TrackedEmotion>,
        speakerId: String,
        speakerName: String,
        nowMs: Long,
        windowMs: Long = FALLBACK_WINDOW_MS
    ): TrackedEmotion? {
        val id = speakerId.trim()
        val name = speakerName.trim()
        return history.asSequence()
            .filter { it.emotion.isNotBlank() }
            .filter { candidate ->
                (id.isNotBlank() && candidate.speakerId.trim() == id) ||
                    (name.isNotBlank() && candidate.speakerName.trim() == name &&
                        (id.isBlank() || candidate.speakerId.isBlank()))
            }
            .filter { isFallbackWithinWindow(nowMs, it.spokenAtMs, windowMs) }
            .maxByOrNull { it.spokenAtMs }
    }

    /** 在历史里按快照的键（标识或显示名）找最近一次心情，用于判断“沿用”值是否还在有效窗口内。 */
    fun findRecentEmotionByKey(
        history: List<TrackedEmotion>,
        key: String,
        nowMs: Long,
        windowMs: Long = FALLBACK_WINDOW_MS
    ): TrackedEmotion? {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return null
        return history.asSequence()
            .filter { it.emotion.isNotBlank() }
            .filter { it.speakerId.trim() == trimmed || it.speakerName.trim() == trimmed }
            .filter { isFallbackWithinWindow(nowMs, it.spokenAtMs, windowMs) }
            .maxByOrNull { it.spokenAtMs }
    }

    /**
     * 逐成员独立判断本轮成员心情（只有本轮实际发言的成员参与）：
     * ① 本轮台词里解析到的心情；② 本轮【成员心情】字段声明的心情（本轮已发言成员）；
     * ③ 上一轮【成员心情】快照；④ 最近若干轮历史里该成员最近一次非空心情。
     * ③④ 属于回退，会记入 inheritedKeys；超出 6 小时窗口或本轮未发言的成员不参与回退。
     */
    fun resolveRoundEmotions(
        nowMs: Long,
        results: List<GroupMsgResult>,
        declaredSnapshot: String = "",
        previousSnapshot: String,
        previousSnapshotAtMs: Long,
        previousInheritedKeys: String,
        history: List<TrackedEmotion>,
        windowMs: Long = FALLBACK_WINDOW_MS
    ): RoundEmotions {
        val declared = parseEmotionSnapshot(declaredSnapshot)
        val snapshot = parseEmotionSnapshot(previousSnapshot)
        val previouslyInherited = parseKeyList(previousInheritedKeys)
        val emotions = LinkedHashMap<String, String>()
        val inherited = LinkedHashSet<String>()
        val order = LinkedHashMap<String, GroupMsgResult>()
        results.forEach { result ->
            if (result.type.equals("narration", true) || result.speaker == "旁白") return@forEach
            val key = memberKey(result.speakerId, result.speaker)
            if (key.isBlank()) return@forEach
            order[key] = result
        }
        order.forEach { (key, lastSegment) ->
            val parsed = cleanEmotion(lastSegment.emotion)
            if (parsed.isNotBlank()) {
                emotions[key] = parsed
                return@forEach
            }
            val declaredEmotion = declaredEmotionFor(declared, key, lastSegment.speakerId, lastSegment.speaker)
            if (declaredEmotion.isNotBlank()) {
                // 模型在【成员心情】里为本轮已发言成员声明的心情，算本轮值而不是回退值。
                emotions[key] = declaredEmotion
                return@forEach
            }
            val fallback = resolveFallbackEmotion(
                speakerId = lastSegment.speakerId,
                speakerName = lastSegment.speaker,
                nowMs = nowMs,
                snapshot = snapshot,
                snapshotAtMs = previousSnapshotAtMs,
                inheritedKeys = previouslyInherited,
                history = history,
                windowMs = windowMs
            )
            if (fallback.isNotBlank()) {
                emotions[key] = fallback
                inherited += key
            }
        }
        return RoundEmotions(emotions, inherited)
    }

    private fun declaredEmotionFor(
        declared: Map<String, String>,
        key: String,
        speakerId: String,
        speakerName: String
    ): String = listOf(key, speakerId.trim(), speakerName.trim())
        .filter { it.isNotBlank() }
        .distinct()
        .firstNotNullOfOrNull { declared[it]?.takeIf { emotion -> emotion.isNotBlank() } }
        .orEmpty()

    /**
     * 单个成员的回退：先取上一轮快照（快照值本身是“沿用”时用历史里的真实产生时间判窗口），
     * 再退回最近若干轮历史。取不到或超出窗口时返回空串（不回退、不显示）。
     */
    fun resolveFallbackEmotion(
        speakerId: String,
        speakerName: String,
        nowMs: Long,
        snapshot: Map<String, String>,
        snapshotAtMs: Long,
        inheritedKeys: Set<String>,
        history: List<TrackedEmotion>,
        windowMs: Long = FALLBACK_WINDOW_MS
    ): String {
        val aliases = listOf(memberKey(speakerId, speakerName), speakerName.trim(), speakerId.trim())
            .filter { it.isNotBlank() }
            .distinct()
        val snapshotKey = aliases.firstOrNull { snapshot[it]?.isNotBlank() == true }
        if (snapshotKey != null) {
            val emotion = cleanEmotion(snapshot[snapshotKey].orEmpty())
            if (emotion.isNotBlank()) {
                if (snapshotKey !in inheritedKeys) {
                    // 上一轮真实写出的心情：窗口从上一轮快照时间起算。
                    if (isFallbackWithinWindow(nowMs, snapshotAtMs, windowMs)) return emotion
                } else {
                    // 快照值本身就是“沿用”的：窗口必须按心情真正产生的时刻算。
                    // 历史里找不到（清理/超窗）就不回退，避免把陈旧情绪一直滚下去。
                    val origin = findRecentEmotionByKey(history, snapshotKey, nowMs, windowMs)
                        ?: findRecentEmotion(history, speakerId, speakerName, nowMs, windowMs)
                    return if (origin != null) emotion else ""
                }
            }
        }
        return findRecentEmotion(history, speakerId, speakerName, nowMs, windowMs)?.emotion.orEmpty()
    }

    /**
     * 下一轮注入提示词的【上轮成员情绪】。
     * 上一轮真实写出的值直接列出；属于“沿用”的值标注（沿用上一轮），
     * 并且必须能在历史里找到它真正产生的那一次、且仍在 6 小时窗口内，否则不再注入。
     * [snapshotAtMs] 只用于对齐调用方的快照时间口径，沿用值的有效性一律以历史里的真实产生时间为准。
     */
    fun promptEmotions(
        nowMs: Long,
        snapshot: String,
        snapshotAtMs: Long,
        inheritedKeys: String,
        history: List<TrackedEmotion> = emptyList(),
        windowMs: Long = FALLBACK_WINDOW_MS
    ): List<PromptEmotion> {
        val parsed = parseEmotionSnapshot(snapshot)
        if (parsed.isEmpty()) return emptyList()
        val inherited = parseKeyList(inheritedKeys)
        return parsed.mapNotNull { (key, emotion) ->
            if (key !in inherited) return@mapNotNull PromptEmotion(key, emotion, false)
            // 沿用值必须在历史里还能找到它真正产生的那一次，且仍在 6 小时窗口内。
            val origin = findRecentEmotionByKey(history, key, nowMs, windowMs) ?: return@mapNotNull null
            PromptEmotion(key, origin.emotion, true)
        }
    }
}
