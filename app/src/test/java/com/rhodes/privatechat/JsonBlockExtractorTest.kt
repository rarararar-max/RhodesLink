package com.rhodes.privatechat

import com.rhodes.privatechat.shared.network.JsonBlockExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonBlockExtractorTest {
    @Test
    fun preservesBracesInsideStringsAndNestedObjects() {
        val raw = "prefix {\"speaker\":\"阿米娅\",\"message\":\"这是 } 一个 { 测试\",\"meta\":{\"tag\":\"x\"}} suffix"

        assertEquals(
            listOf("{\"speaker\":\"阿米娅\",\"message\":\"这是 } 一个 { 测试\",\"meta\":{\"tag\":\"x\"}}"),
            JsonBlockExtractor.extract(raw),
        )
    }

    @Test
    fun returnsEachTopLevelBlockWithoutSplittingEscapedQuotes() {
        val raw = "{\"message\":\"她说\\\"好\\\"\"} text [\"not a group reply\"]"

        assertEquals(
            listOf("{\"message\":\"她说\\\"好\\\"\"}", "[\"not a group reply\"]"),
            JsonBlockExtractor.extract(raw),
        )
    }
}
