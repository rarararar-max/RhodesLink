package com.rhodes.privatechat.viewmodel.shared

/**
 * 群聊一轮输出的 token 预算。
 *
 * 改前：群聊回复调用 chatResult 时不传 maxOutputTokens，OpenAI 兼容厂商走服务端默认值（DeepSeek 为 4096），
 * Anthropic 分支在 AIService 里硬编码 4096。每位成员多一行【心情】后输出变长约 10~15%，
 * 多成员轮次（成员数 × 发言段数）会贴着 4096 上限，容易 finish_reason=length 截断台词。
 *
 * 改后：对输出上限足够大的厂商显式上调到 [GROUP_MAX_OUTPUT_TOKENS]；
 * 已知输出上限仍在 4096 附近、传更大值可能被服务端直接拒绝的厂商保持默认（返回 null），
 * 避免为了留余量反而让整轮请求失败。
 */
object GroupOutputBudget {

    /** 群聊显式输出预算：4096 × 1.22，覆盖每位成员多一行【心情】的开销。 */
    const val GROUP_MAX_OUTPUT_TOKENS = 5_000

    /**
     * 输出上限接近 4096 或不确定的服务端：不显式传 max_tokens，继续用服务端默认值。
     * zhipu（glm-4 输出上限 4095）、minimax、siliconflow 与自填厂商的模型上限差异较大。
     */
    private val PROVIDERS_WITH_UNKNOWN_OUTPUT_CAP = setOf("zhipu", "minimax", "siliconflow", "custom")

    /** 返回本轮群聊请求要显式使用的 max_tokens；null 表示沿用服务端默认值。 */
    fun groupMaxOutputTokens(provider: String): Int? =
        if (provider.trim().lowercase() in PROVIDERS_WITH_UNKNOWN_OUTPUT_CAP) null else GROUP_MAX_OUTPUT_TOKENS
}
