package com.rhodes.privatechat

import com.rhodes.privatechat.shared.modelgateway.normalizeOpenAiChatEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class VisionEndpointTest {
    @Test
    fun acceptsOpenAiCompatibleBaseUrl() {
        assertEquals(
            "https://api.siliconflow.cn/v1/chat/completions",
            normalizeOpenAiChatEndpoint("https://api.siliconflow.cn/v1"),
        )
    }

    @Test
    fun keepsCompleteChatCompletionsEndpoint() {
        assertEquals(
            "https://api.siliconflow.cn/v1/chat/completions",
            normalizeOpenAiChatEndpoint("https://api.siliconflow.cn/v1/chat/completions/"),
        )
    }

    @Test
    fun acceptsDeepSeekBaseUrl() {
        assertEquals(
            "https://api.deepseek.com/chat/completions",
            normalizeOpenAiChatEndpoint("https://api.deepseek.com/"),
        )
    }
}
