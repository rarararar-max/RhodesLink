package com.rhodes.privatechat.util

/**
 * 本轮上下文引用的"可解释"记录。
 *
 * 目的：玩家在调试记录里应该能一眼看出"我的记忆/知识库到底生效了没有、注入了哪些、没生效是因为什么"，
 * 而不是去搜日志、翻完整请求。这些状态在拼提示词时本来就已知，这里只是把它们记下来并翻译成人话，
 * 每条原因都带"下一步去哪开"。
 *
 * 纯数据 + 纯函数，便于单元测试；不产生任何模型调用。
 */
object ContextProvenance {

    /** 一条上下文章道（记忆/知识库/摘要…）在本轮的结局。 */
    enum class Outcome {
        /** 已进入本轮提示词 */
        INJECTED,

        /** 玩家没有开启这个来源（设置里关着） */
        DISABLED_BY_SWITCH,

        /** 生成侧没开，所以根本没有这类记忆 */
        GENERATION_OFF,

        /** 开关正常，但库里没有可用内容 */
        NO_DATA,

        /** 读到了内容，但被长度上限/条数上限裁掉 */
        TRIMMED,

        /** 向量索引未就绪（签名不匹配或尚未建立） */
        INDEX_NOT_READY,

        /** 角色没有关联任何知识库 */
        NOT_BOUND,

        /** 读取超时，本轮降级跳过 */
        TIMEOUT,

        /** 检索正常完成，但没有相关命中（正常现象） */
        NO_MATCH,
    }

    /** 卡片与详情共用的一行记录。channel 是给玩家看的名字，detail 是条数/片段等附加信息。 */
    data class Entry(
        val channel: String,
        val outcome: Outcome,
        val detail: String = "",
    )

    /** 已注入：`▶ 私聊记忆：3 条已注入` */
    fun line(entry: Entry): String {
        val detail = entry.detail.trim()
        val suffix = if (detail.isBlank()) "" else "：$detail"
        return when (entry.outcome) {
            Outcome.INJECTED -> "▶ ${entry.channel}$suffix"
            Outcome.NO_MATCH -> "· ${entry.channel}：检索完成，本轮没有相关命中"
            else -> "⚠ ${entry.channel}：${reason(entry.outcome)}$suffix"
        }
    }

    /** 原因 + 下一步。文案里直接给出设置路径，玩家不用猜。 */
    fun reason(outcome: Outcome): String = when (outcome) {
        Outcome.INJECTED -> "已注入"
        Outcome.DISABLED_BY_SWITCH -> "你尚未在设置中开启该来源 → 设置 → 记忆 → 注入"
        Outcome.GENERATION_OFF -> "该来源尚未生成记忆 → 设置 → 记忆 → 生成"
        Outcome.NO_DATA -> "该角色还没有可用的这类记忆（多聊几轮会自动生成）"
        Outcome.TRIMMED -> "读到了内容，但超出长度上限被裁掉 → 设置 → 记忆（条数上限/上下文模式）"
        Outcome.INDEX_NOT_READY -> "向量索引未就绪，本轮未使用向量召回 → 设置 → 记忆 → 管理"
        Outcome.NOT_BOUND -> "该角色未关联任何知识库 → 设置 → 知识库 / 角色编辑 → 关联知识库"
        Outcome.TIMEOUT -> "读取超时，本轮跳过（不影响正常回复）"
        Outcome.NO_MATCH -> "本轮没有相关命中"
    }

    /**
     * 卡片上用的 3~5 行摘要：先列已注入的通道，再列最多若干条"为什么没有"。
     * 全部正常时只给一行，避免卡片被撑爆。
     */
    fun summary(entries: List<Entry>, maxProblems: Int = 3): String {
        val injected = entries.filter { it.outcome == Outcome.INJECTED }
        val problems = entries.filter { it.outcome != Outcome.INJECTED && it.outcome != Outcome.NO_MATCH }
        if (entries.isEmpty()) return ""
        val builder = StringBuilder()
        if (injected.isEmpty()) {
            builder.append("本轮未引用记忆或知识库")
        } else {
            injected.forEach { builder.append(line(it)).append('\n') }
        }
        problems.take(maxProblems).forEach { builder.append(line(it)).append('\n') }
        if (problems.size > maxProblems) {
            builder.append("⚠ 另有 ${problems.size - maxProblems} 项未注入，详见本轮引用明细")
        }
        return builder.toString().trim().trimEnd('\n')
    }

    /**
     * 结果卡上用的**单行**摘要：只讲结论，细节留给详情模块。
     * 卡片高度有限，逐行展开会把整页撑得很长。
     */
    fun compactSummary(entries: List<Entry>): String {
        if (entries.isEmpty()) return ""
        val injected = entries.filter { it.outcome == Outcome.INJECTED }
        val problems = entries.filter { it.outcome != Outcome.INJECTED && it.outcome != Outcome.NO_MATCH }
        val parts = mutableListOf<String>()
        parts += if (injected.isEmpty()) "未引用记忆或知识库" else "已注入 " + injected.joinToString("、") { it.channel }
        if (problems.isNotEmpty()) {
            parts += "未注入 " + problems.take(2).joinToString("、") { "${it.channel}（${shortReason(it.outcome)}）" }
        }
        if (problems.size > 2) parts += "另有 ${problems.size - 2} 项见详情"
        return "上下文：" + parts.joinToString("；")
    }

    private fun shortReason(outcome: Outcome): String = when (outcome) {
        Outcome.DISABLED_BY_SWITCH -> "设置未开启"
        Outcome.GENERATION_OFF -> "尚未生成"
        Outcome.NO_DATA -> "暂无内容"
        Outcome.TRIMMED -> "被长度裁掉"
        Outcome.INDEX_NOT_READY -> "向量索引未就绪"
        Outcome.NOT_BOUND -> "未关联知识库"
        Outcome.TIMEOUT -> "读取超时"
        else -> reason(outcome)
    }

    /** 详情模块：逐条列出，含片段原文（原文由调用方按需截断）。 */
    fun detail(surface: String, entries: List<Entry>, snippets: List<String> = emptyList()): String = buildString {
        append("界面：").append(surface).append('\n')
        append("【本轮上下文引用】\n")
        if (entries.isEmpty()) append("（无记录）\n")
        entries.forEach { append(line(it)).append('\n') }
        if (snippets.isNotEmpty()) {
            append("\n【实际进入提示词的内容】\n")
            snippets.forEach { append(it).append('\n') }
        }
    }.trim()

    /**
     * 记忆条道的结局判定（纯函数，便于测试）。
     *
     * @param totalSwitchOn 记忆功能总开关
     * @param sourceAllowed 该来源是否允许注入
     * @param generationOn  该来源是否允许生成
     * @param hasData       库里是否有可用内容
     * @param injected      内容是否真的进入了本轮提示词
     */
    fun memoryOutcome(
        totalSwitchOn: Boolean,
        sourceAllowed: Boolean,
        generationOn: Boolean,
        hasData: Boolean,
        injected: Boolean,
    ): Outcome = when {
        !totalSwitchOn || !sourceAllowed -> Outcome.DISABLED_BY_SWITCH
        !generationOn && !hasData -> Outcome.GENERATION_OFF
        !hasData -> Outcome.NO_DATA
        injected -> Outcome.INJECTED
        else -> Outcome.TRIMMED
    }

    /** 知识库条道的结局判定：把检索器的原因码翻译成结局。 */
    fun knowledgeOutcome(reason: String, hitCount: Int, injectedCount: Int): Outcome = when {
        hitCount > 0 && injectedCount > 0 -> Outcome.INJECTED
        hitCount > 0 -> Outcome.TRIMMED
        reason == "no_assignment" -> Outcome.NOT_BOUND
        reason == "index_not_ready" -> Outcome.INDEX_NOT_READY
        reason == "embedding_unavailable" -> Outcome.INDEX_NOT_READY
        reason == "blank_query" -> Outcome.NO_DATA
        else -> Outcome.NO_MATCH
    }
}
