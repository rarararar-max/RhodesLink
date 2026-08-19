package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.ui.support.AiSupportContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSupportContractTest {
    @Test
    fun localRetrievalNormalizesTraditionalChineseAndUsesIntentTerms() {
        val sections = listOf(
            "## 外观与皮肤\n\n在设置页面打开外观，选择皮肤或主题。",
            "## 常见错误\n\nAI 回复失败时，请检查模型配置和网络连接。",
        )

        assertTrue(AiSupportContract.localReference(sections, "怎麼換主題").contains("外观与皮肤"))
        assertTrue(AiSupportContract.localReference(sections, "闪退怎么办").contains("常见错误"))
    }

    @Test
    fun localRetrievalUsesStableTieBreakAndRelevantParagraphs() {
        val sections = listOf(
            "## 第一章\n\n无关段落。\n\n配置文本模型需要填写 API Key。",
            "## 第二章\n\n配置文本模型也需要选择模型。",
        )

        val result = AiSupportContract.localReference(sections, "配置文本模型")

        assertTrue(result.indexOf("第一章") < result.indexOf("第二章"))
        assertTrue(result.contains("填写 API Key"))
    }

    @Test
    fun historyHasCharacterBudgetAndErrorsAreRedacted() {
        val history = AiSupportContract.recentHistory(List(8) { AiMessage("user", "a".repeat(700)) })

        assertTrue(history.sumOf { it.content.length } <= 3_000)
        assertEquals("模型服务认证失败，请检查模型设置中的 API Key。", AiSupportContract.userError(Exception("API error 401: secret detail")))
        assertFalse(AiSupportContract.userError(Exception("API error 500: private body")).contains("private body"))
    }
}
