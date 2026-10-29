package com.rhodes.privatechat.shared.network

/**
 * Turns a provider HTTP error into something the user can act on. The raw payload is kept (truncated)
 * because support needs it, but it must not be the first thing the user reads: the app used to surface
 * "API error 401: {"error":{"message":"Authentication Fails..."}}" verbatim.
 *
 * The message still starts with the historical "API error <code>" prefix because AIService classifies
 * retry behaviour from that prefix.
 */
object ModelErrorMessages {
    private const val RAW_LIMIT = 600

    fun redact(message: String, vararg secrets: String): String {
        var safe = message
        secrets.filter { it.isNotBlank() }.sortedByDescending { it.length }.forEach {
            safe = safe.replace(it, "[REDACTED]")
        }
        return safe.replace(Regex("(?i)Bearer\\s+[^\\s\"',}]+"), "Bearer [REDACTED]")
            .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "[REDACTED]")
    }

    fun forHttpStatus(status: Int, rawBody: String): String {
        val raw = redact(rawBody).trim().replace(Regex("\\s+"), " ")
        val hint = when {
            status == 401 || status == 403 ->
                "鉴权失败：请检查 API Key 是否正确、是否与当前服务商匹配（更换服务商后必须更换 Key）"
            status == 404 -> "接口或模型不存在：请检查 API 地址与模型名"
            status == 413 -> "请求内容过大：请减少上下文（历史条数、角色卡或知识库）"
            status == 429 -> "请求过于频繁：稍后重试即可"
            status == 400 || status == 422 ->
                if (looksLikeContextOverflow(raw)) "上下文超出模型上限：请减少历史条数或缩小角色卡/知识库"
                else "请求被拒绝：请检查模型名与参数"
            status in 500..599 -> "服务商暂时故障：请稍后重试"
            else -> "请求失败"
        }
        return if (raw.isBlank()) hint else "$hint（原始信息：${raw.take(RAW_LIMIT)}）"
    }

    private fun looksLikeContextOverflow(raw: String): Boolean {
        val lower = raw.lowercase()
        return listOf(
            "context", "token", "length", "too long", "maximum", "max_tokens",
            "上下文", "过长", "超出",
        ).any { it in lower }
    }
}
