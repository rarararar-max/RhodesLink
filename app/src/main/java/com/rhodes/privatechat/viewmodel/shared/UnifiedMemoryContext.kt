package com.rhodes.privatechat.viewmodel.shared

object UnifiedMemoryContext {
    fun mergeBlocks(maxChars: Int, vararg blocks: String): String {
        if (maxChars <= 0) return "无"
        val lines = blocks
            .flatMap { it.lines() }
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "无" && it != "暂无" }
        if (lines.isEmpty()) return "无"

        val seen = mutableSetOf<String>()
        val picked = mutableListOf<String>()
        var used = 0
        for (line in lines) {
            val normalized = normalize(line)
            if (normalized.isBlank() || normalized in seen) continue
            val clean = line.take(180)
            if (used + clean.length + 1 > maxChars) break
            seen += normalized
            picked += clean
            used += clean.length + 1
        }
        return picked.joinToString("\n").ifBlank { "无" }
    }

    fun shouldIncludeTimeSummary(userContent: String): Boolean {
        val text = userContent.trim()
        if (text.isBlank()) return false
        return listOf(
            "昨天", "前天", "今天", "刚才", "上次", "之前", "以前", "最近", "那天", "那次",
            "记得", "想起", "说过", "聊过", "提过", "发生", "总结", "回顾"
        ).any { text.contains(it) }
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("""^[-•*\s]+"""), "")
        .replace(Regex("""【[^】]+】"""), "")
        .replace(Regex("""\[[^]]+]"""), "")
        .replace(Regex("""[\s，。！？；,.!?;：:\"'“”‘’（）()]"""), "")
        .take(90)
}
