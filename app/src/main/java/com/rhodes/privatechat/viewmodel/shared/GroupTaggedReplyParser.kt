package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.GroupMsgResult

/**
 * 群聊模型输出的标签解析：【发言人: 发言标识】【心情】【情绪】【旁白】与【群聊回合状态】内部字段。
 *
 * 归属规则：
 * - 【心情】属于最近的前一个【发言人】；
 * - 出现在【旁白】块里的心情一律丢弃（旁白块里心情标签后面的正文仍算旁白）；
 * - 给玩家（“我”）写的、或没有【发言人】归属的心情一律丢弃；
 * - 心情与发言人同行、心情后紧跟台词、心情写在台词之后、【情绪】与【心情】并存（取第一个非空）、
 *   标签重复（取第一个非空）都能识别；
 * - 心情写成句子或超长时截到第一个标点前，再截断到最多 10 字。
 *
 * 这里不依赖 ViewModel 成员表，成员归属通过 resolveSpeaker 回调注入，便于单测。
 */
object GroupTaggedReplyParser {

    data class SpeakerIdentity(val name: String, val id: String)

    /** 群聊回合状态的内部字段标签：只做索引，不进入台词与旁白。 */
    val TURN_STATE_LABELS = setOf(
        "群聊回合状态", "当前主线", "用户本轮作用", "本轮承接", "本轮新增推进",
        "主线状态", "下轮焦点", "本轮剧情简述", "成员心情"
    )

    val EMOTION_LABELS = setOf("心情", "情绪")

    /** 所有已定义标签，供标签正则和“下一个标签”定位共用。 */
    val LABEL_PATTERN: String = buildList {
        addAll(TURN_STATE_LABELS)
        add("旁白")
        add("发言人")
        addAll(EMOTION_LABELS)
    }.joinToString("|")

    private val tag = Regex(
        """[【\[［]\s*($LABEL_PATTERN)\s*(?:[：:]\s*([^】\]］]*))?[】\]］]"""
    )

    val NEXT_TAG_PATTERN: String = """[【\[［]\s*(?:$LABEL_PATTERN)"""

    /**
     * 解析整段输出。resolveSpeaker 返回 null 表示该标签不属于当前成员（含玩家“我”和名单外成员），
     * 该发言块与其中的心情都会被丢弃。
     */
    fun extractTagged(raw: String, resolveSpeaker: (String) -> SpeakerIdentity?): List<GroupMsgResult> {
        val matches = tag.findAll(raw).toList()
        if (matches.isEmpty()) return emptyList()
        val results = mutableListOf<GroupMsgResult>()
        var dialogueIndex = -1
        var narrationIndex = -1
        matches.forEachIndexed { index, match ->
            val label = match.groupValues[1]
            val inlineValue = match.groupValues[2].trim()
            val content = raw.substring(
                match.range.last + 1,
                matches.getOrNull(index + 1)?.range?.first ?: raw.length
            )
            when {
                label in TURN_STATE_LABELS -> Unit
                label == "旁白" -> {
                    dialogueIndex = -1
                    narrationIndex = -1
                    val text = content.trim()
                    if (text.isNotBlank()) {
                        results += GroupMsgResult(speaker = "旁白", message = text, type = "narration")
                        narrationIndex = results.lastIndex
                    }
                }
                label == "发言人" -> {
                    narrationIndex = -1
                    dialogueIndex = -1
                    val identity = resolveSpeaker(inlineValue) ?: return@forEachIndexed
                    results += GroupMsgResult(
                        speaker = identity.name,
                        message = content.trim(),
                        type = "dialogue",
                        speakerId = identity.id
                    )
                    dialogueIndex = results.lastIndex
                }
                label in EMOTION_LABELS -> {
                    val (emotion, remainder) = splitEmotion(content, inlineValue)
                    if (dialogueIndex >= 0) {
                        val slot = results[dialogueIndex]
                        val nextEmotion = if (slot.emotion.isBlank()) emotion else slot.emotion
                        results[dialogueIndex] = slot.copy(
                            message = joinDialogue(slot.message, remainder),
                            emotion = nextEmotion
                        )
                    } else if (narrationIndex >= 0) {
                        // 旁白里的心情一律丢弃，但标签后面的正文仍属于旁白。
                        val slot = results[narrationIndex]
                        results[narrationIndex] = slot.copy(message = joinDialogue(slot.message, remainder))
                    }
                }
            }
        }
        return results.filter { it.type.equals("narration", true) || it.message.isNotBlank() }
    }

    /** 从心情标签块里切出心情值与仍然属于台词的剩余正文。 */
    fun splitEmotion(content: String, inlineValue: String): Pair<String, String> {
        val inline = inlineValue.trim()
        if (inline.isNotBlank()) {
            return GroupEmotionRules.cleanEmotion(inline) to content.trim()
        }
        val lines = content.split('\n')
        val firstIndex = lines.indexOfFirst { it.isNotBlank() }
        if (firstIndex < 0) return "" to ""
        val firstLine = lines[firstIndex].trim()
        // 心情后紧跟台词时（【心情】不悦 台词），心情取第一个空白前的短词，其余仍算台词。
        val separator = firstLine.indexOfFirst { it == ' ' || it == '\t' || it == '　' }
        val head = if (separator > 0) firstLine.substring(0, separator) else firstLine
        val headTail = if (separator > 0) firstLine.substring(separator + 1).trim() else ""
        val rest = lines.drop(firstIndex + 1).joinToString("\n").trim()
        val remainder = listOf(headTail, rest).filter { it.isNotBlank() }.joinToString("\n")
        return GroupEmotionRules.cleanEmotion(head) to remainder
    }

    private fun joinDialogue(current: String, extra: String): String {
        if (extra.isBlank()) return current
        if (current.isBlank()) return extra
        return "$current\n$extra"
    }
}
