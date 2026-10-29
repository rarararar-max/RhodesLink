package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.network.ModelCapabilities
import com.rhodes.privatechat.shared.network.ModelErrorMessages
import com.rhodes.privatechat.shared.vector.EmbeddingConfigurationSignature
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class OctoberMigrationRegressionTest {
    private val json = Json { encodeDefaults = true }

    @Test fun normalChatOmitsUnsetOptionalParametersEvenWithEncodeDefaults() {
        val body = json.parseToJsonElement(json.encodeToString(ChatCompletionRequest(
            messages = listOf(AiMessage("user", "连接测试"))
        ))).jsonObject
        listOf("response_format", "max_tokens", "thinking", "enable_thinking", "reasoning_effort").forEach {
            assertFalse("Unexpected null parameter: $it", body.containsKey(it))
        }
    }

    @Test fun jsonModeIncludesTheRequestedResponseFormat() {
        val body = json.parseToJsonElement(json.encodeToString(ChatCompletionRequest(
            messages = emptyList(), response_format = ResponseFormat("json_object")
        ))).jsonObject
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }

    @Test fun miniMaxDoesNotReceiveDisableThinkingFlags() {
        listOf("MiniMax-M3.1-Flash", "MiniMax-M3").forEach {
            assertEquals("max", ModelCapabilities.reasoningEffort("minimax", it))
            assertNull(ModelCapabilities.thinkingParameter("minimax", it))
            assertNull(ModelCapabilities.enableThinkingParameter("minimax", it))
        }
        assertNull(ModelCapabilities.reasoningEffort("deepseek", "deepseek-chat"))
    }

    @Test fun oldIndexesKeepTheirSignatureAndNativeProtocolIsIsolated() {
        val old = "third_party|https://example.invalid/embeddings|embedding-model"
        assertEquals(old, EmbeddingConfigurationSignature.create("third_party", "ali", "https://example.invalid/embeddings", "embedding-model"))
        assertNotEquals(old, EmbeddingConfigurationSignature.create("third_party", "ali", "https://example.invalid/embeddings", "embedding-model", "aliyun_native"))
    }

    @Test fun diagnosticsKeepProviderDetailsWithoutLeakingCredentials() {
        val key = "example-secret-credential"
        val safe = ModelErrorMessages.redact("HTTP 400 code=InvalidParameter request_id=abc token=$key Bearer test-token", key)
        assertFalse(safe.contains(key))
        assertFalse(safe.contains("test-token"))
        assertTrue(safe.contains("request_id=abc"))
        assertFalse(ModelErrorMessages.forHttpStatus(401, "key=sk-test_secret12345").contains("sk-test_secret12345"))
    }
}
