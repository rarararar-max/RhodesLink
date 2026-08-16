package com.rhodes.privatechat.galgame_full

data class DebugLogEntry(
    val agent: String,
    val timestamp: Long,
    val durationMs: Long,
    val request: String,
    val response: String,
    val result: String,
    val error: String? = null
)

object DebugLog {
    private val entries = mutableListOf<DebugLogEntry>()

    @Synchronized fun add(entry: DebugLogEntry) {
        entries.add(entry)
        if (entries.size > 20) entries.removeAt(0)
    }

    @Synchronized fun all(): List<DebugLogEntry> = entries.toList().asReversed()

    @Synchronized fun clear() = entries.clear()
}

fun debugFailureSummary(error: String?): String {
    val message = error.orEmpty()
    return when {
        "请先填写 API Key" in message -> "请先在首页的 API Key 设置中填写有效的 Key"
        "HTTP 401" in message -> "鉴权失败：API Key 无效或已失效"
        "HTTP 402" in message -> "额度不足：API 账户余额或额度不可用"
        "HTTP 404" in message -> "模型或接口不存在"
        "HTTP 429" in message -> "请求过于频繁：触发限流"
        "HTTP 5" in message -> "模型服务端异常"
        "finish_reason=length" in message -> "模型输出被截断：达到 max_tokens 上限"
        "timeout" in message.lowercase() -> "网络超时：未在等待时间内收到响应"
        "AI attempted to speak for the player" in message -> "规则解析失败：AI 代替玩家角色发言"
        "sprite does not belong" in message -> "规则解析失败：AI 使用了不属于该角色的立绘"
        "character is not allowed" in message -> "规则解析失败：AI 使用了本章禁止出场的角色"
        "background is not allowed" in message -> "规则解析失败：AI 使用了本章禁止的场景"
        "narration cannot" in message -> "规则解析失败：旁白错误携带立绘"
        "JSONException" in message || "JSONObject" in message || "JSONArray" in message -> "JSON 解析失败：模型没有返回符合格式的内容"
        message.isBlank() -> "未知错误：没有可用错误详情"
        else -> "请求或解析失败：$message"
    }
}
