package com.rhodes.privatechat

import com.rhodes.privatechat.shared.network.ModelErrorMessages
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The private/group chains used to hand the provider's raw JSON to the user, so "为什么聊不了"
 * was unanswerable from the screen. These assertions pin the actionable text.
 */
class ModelErrorMessagesTest {
    // Captured shapes: DeepSeek/DashScope auth failure, context overflow, and a provider 5xx.
    private val authFailure = """{"error":{"message":"Authentication Fails, Your api key is invalid","type":"authentication_error"}}"""
    private val contextOverflow = """{"error":{"message":"This model's maximum context length is 65536 tokens","type":"invalid_request_error"}}"""

    @Test
    fun authFailureTellsTheUserToCheckTheKey() {
        val message = ModelErrorMessages.forHttpStatus(401, authFailure)
        assertTrue("必须说清是鉴权/Key 问题: $message", message.contains("鉴权失败"))
        assertTrue("必须提到换服务商要换 Key: $message", message.contains("服务商"))
        assertTrue("原始信息仍要保留给客服: $message", message.contains("Authentication Fails"))
    }

    @Test
    fun contextOverflowSaysWhichLeverToPull() {
        val message = ModelErrorMessages.forHttpStatus(400, contextOverflow)
        assertTrue("必须指出是上下文超限: $message", message.contains("上下文超出模型上限"))
        assertTrue("必须给出可操作建议: $message", message.contains("历史条数"))
    }

    @Test
    fun otherStatusesAreActionableToo() {
        assertTrue(ModelErrorMessages.forHttpStatus(429, "").contains("过于频繁"))
        assertTrue(ModelErrorMessages.forHttpStatus(503, "").contains("稍后重试"))
        assertTrue(ModelErrorMessages.forHttpStatus(404, "").contains("模型"))
        assertTrue(ModelErrorMessages.forHttpStatus(418, "teapot").contains("请求失败"))
    }

    @Test
    fun rawPayloadIsTruncatedButNeverDroppedWhenPresent() {
        val long = "x".repeat(2000)
        val message = ModelErrorMessages.forHttpStatus(500, long)
        assertTrue("原始信息要保留", message.contains("原始信息"))
        assertTrue("保留足够的诊断信息", message.contains("x".repeat(600)))
        assertTrue("仍限制错误长度: ${message.length}", message.length < 800)
    }

    @Test
    fun retryClassifierPrefixIsPreserved() {
        val message = ModelErrorMessages.forHttpStatus(401, authFailure)
        assertFalse("正文里不能再出现裸的 JSON 起始引号", message.startsWith("{"))
        assertTrue("分类器依赖的旧前缀由调用方拼装，正文只负责提示部分", message.contains("鉴权失败"))
    }
}
