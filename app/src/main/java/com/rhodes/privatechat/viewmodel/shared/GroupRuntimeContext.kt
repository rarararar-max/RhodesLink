package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.data.PromptPlaceholderRegistry

/** Only known aliases share ownership. Unrelated fields with equal text stay independent. */
internal object GroupRuntimeContext {
    private val token = Regex("\\{\\{([A-Z0-9_]+)\\}\\}")
    private val families = listOf(
        setOf("MEMBER_PROFILES"),
        setOf("MEMBER_NAMES"),
        setOf("SHORT_TERM_SUMMARY", "GROUP_SUMMARY", "GROUP_CONTEXT"),
        setOf("MEMORY_V2_CONTEXT", "SHARED_MEMORIES", "MEMORY_ANCHORS"),
        setOf("RELATION_HINTS", "GROUP_RELATION_HINTS"),
    )
    private val runtimeKeys = PromptPlaceholderRegistry.runtimeKeys("group") - setOf("USER_MESSAGE", "USER_OBSERVING")
    private fun family(key: String) = families.firstOrNull { key in it }
        ?: key.takeIf { it in runtimeKeys }?.let { setOf(it) }
    private fun meaningful(value: String) = value.isNotBlank() && value.trim() !in setOf("无", "暂无", "null")

    fun claimAlias(seen: MutableSet<Pair<String, String>>, key: String, value: String): Boolean {
        val keys = family(key) ?: return true
        if (!meaningful(value)) return true
        return seen.add(keys.sorted().joinToString("|") to value)
    }

    fun build(
        continuity: String,
        customRuntime: String?,
        naturalRuntime: String,
        replacements: Map<String, String>,
        render: (String) -> String,
        templateRuntime: (excludedKeys: Set<String>) -> String,
        memberIdentifiers: String = "",
    ): String {
        val provided = mutableMapOf<Pair<Set<String>, String>, String>()
        val runtime = if (customRuntime == null) {
            // Natural context is app-owned. Detect only exact known alias values it supplied.
            families.forEach { keys ->
                keys.forEach { key ->
                    val value = replacements[key].orEmpty()
                    if (key in setOf("SHORT_TERM_SUMMARY", "MEMORY_V2_CONTEXT", "RELATION_HINTS") &&
                        meaningful(value) && naturalRuntime.contains(value)) {
                        provided.putIfAbsent(keys to value, key)
                    }
                }
            }
            naturalRuntime
        } else {
            // Scan the original template, never persona/recall text inserted by rendering.
            val ownedTemplate = token.replace(customRuntime) { match ->
                val key = match.groupValues[1]
                val keys = family(key)
                val value = replacements[key].orEmpty()
                if (keys == null || !meaningful(value)) match.value else {
                    val previous = provided.putIfAbsent(keys to value, key)
                    if (previous == null) match.value else "（同本轮已提供的${label(previous)}）"
                }
            }
            render(ownedTemplate)
        }
        val profiles = replacements["MEMBER_PROFILES"].orEmpty()
        val profileFamily = family("MEMBER_PROFILES")!!
        val roster = if (profiles.isNotBlank() && (profileFamily to profiles) !in provided) {
            provided[profileFamily to profiles] = "MEMBER_PROFILES"
            "【当前成员与发言标识】\n$profiles"
        } else if (memberIdentifiers.isNotBlank()) {
            "【当前成员与发言标识】\n$memberIdentifiers\n完整人设见本轮已提供的成员资料。"
        } else ""
        val excluded = replacements.keys.filter { key ->
            family(key)?.let { (it to replacements[key].orEmpty()) in provided } == true
        }.toSet()
        return listOf(continuity, runtime, roster, templateRuntime(excluded))
            .filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun label(key: String): String = when (key) {
        "MEMBER_PROFILES" -> "成员人设"
        "MEMBER_NAMES" -> "成员名单"
        "SHORT_TERM_SUMMARY", "GROUP_SUMMARY", "GROUP_CONTEXT" -> "群聊摘要"
        "MEMORY_V2_CONTEXT", "SHARED_MEMORIES", "MEMORY_ANCHORS" -> "相关记忆"
        "RELATION_HINTS", "GROUP_RELATION_HINTS" -> "成员关系背景"
        else -> "资料（$key）"
    }
}
