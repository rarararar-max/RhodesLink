package com.rhodes.privatechat.galgame_full

import org.json.JSONArray
import org.json.JSONObject

object ProgressJudgeClient {
    suspend fun judge(state: GameState, assets: ProjectAssets): Result<ProgressReport> {
        val startedAt = System.currentTimeMillis()
        var requestLog = ""
        var rawResponse = ""
        return runCatching {
        val chapter = state.project.chapters.find { it.id == state.chapter } ?: error("chapter not found")
        val system = """
            你是互动视觉小说的章节进度判定 Agent。只根据给定事实和对话判断，不创造事实，不修改状态，不替剧情 Agent 续写剧情。仅在本章目标、完成提示和证据均明确满足时，才将 completion 设为 true；不确定时必须为 false。requiredFlag 或最低好感度未满足时，completion 必须为 false。
            只返回 JSON，不要 Markdown：{"status_description":"","chapter_status":"in_progress|ready|completed|blocked","completion":false,"unmet_conditions":[],"next_direction":"","evidence":[],"chapter_summary":"","key_facts":[],"relationship_notes":[]}。
        """.trimIndent()
        val user = """
            【章节】
            标题：${chapter.title}
            内容：${chapter.contentDescription}
            目标：${chapter.goal}
            完成提示：${chapter.completionHint}
            注意事项：${chapter.notes}
            必需事件：${chapter.requiredFlag.ifBlank { "无" }}
            最低好感度：${chapter.minAffection}
            【已确认状态】
            变量：${state.variables}
            道具：${state.items}
            事件：${state.events}
            当前好感度：${state.affection}
            长期记忆：${state.storyMemory.ifBlank { "无" }}
            最近对话：${state.messages.takeLast(12).joinToString("\n") { "${it.speaker}：${it.text}" }}
        """.trimIndent()
        requestLog = "$system\n\n$user"
        val raw = GalgameHostBridge.chat(listOf(com.rhodes.privatechat.shared.model.AiMessage("system", system), com.rhodes.privatechat.shared.model.AiMessage("user", user)), 800, 0.1, "GalgameProgress")
        rawResponse = raw
        parse(raw).also { DebugLog.add(DebugLogEntry("章节判定", startedAt, System.currentTimeMillis() - startedAt, requestLog, rawResponse, "解析成功：${it.chapterStatus} / completion=${it.completion}")) }
        }.onFailure { error ->
            val detail = if ("\"finish_reason\":\"length\"" in rawResponse) "模型输出被 max_tokens 截断（finish_reason=length）" else error.message ?: error.javaClass.simpleName
            DebugLog.add(DebugLogEntry("章节判定", startedAt, System.currentTimeMillis() - startedAt, requestLog, rawResponse, "判定未完成", detail))
        }
    }

    private fun parse(raw: String): ProgressReport {
        val trimmed = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val clean = trimmed.substringAfter('{', trimmed).substringBeforeLast('}', trimmed).let { if (it == trimmed) it else "{$it}" }
        val j = JSONObject(clean)
        return ProgressReport(j.optString("status_description"), j.optString("chapter_status", "in_progress"), j.optBoolean("completion"), j.optJSONArray("unmet_conditions").strings(), j.optString("next_direction"), j.optJSONArray("evidence").strings(), j.optString("chapter_summary").take(600), j.optJSONArray("key_facts").strings(), j.optJSONArray("relationship_notes").strings())
    }

    private fun JSONArray?.strings(): List<String> = this?.let { List(it.length()) { index -> it.optString(index).take(200) }.filter(String::isNotBlank) }.orEmpty()
}
