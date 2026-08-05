package com.rhodes.privatechat

import com.rhodes.privatechat.viewmodel.shared.PlainGeneratedContentNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlainGeneratedContentNormalizerTest {
    @Test
    fun preservesNormalPlainText() {
        assertEquals("今天食堂的汤有点咸。", PlainGeneratedContentNormalizer.normalize("今天食堂的汤有点咸。", 5, 40))
    }

    @Test
    fun unwrapsFenceQuotesAndKnownJsonContent() {
        assertEquals("刚巡完逻，风有点凉。", PlainGeneratedContentNormalizer.normalize("```json\n{\"content\":\"刚巡完逻，风有点凉。\"}\n```", 5, 40))
        assertEquals("这句我记住了。", PlainGeneratedContentNormalizer.normalize("\"这句我记住了。\"", 5, 40))
    }

    @Test
    fun removesExplanationPrefixAndRejectsUnsafeWrappers() {
        assertEquals("走廊里安静得很。", PlainGeneratedContentNormalizer.normalize("下面是动态：走廊里安静得很。", 5, 40))
        assertNull(PlainGeneratedContentNormalizer.normalize("{\"analysis\":\"先分析\"}", 2, 40))
        assertNull(PlainGeneratedContentNormalizer.normalize("{\"content\":{\"text\":\"正文\"}}", 2, 40))
        assertNull(PlainGeneratedContentNormalizer.normalize("作为AI，我不能这样做。", 2, 40))
    }

    @Test
    fun preservesNaturalOpeningsAndMultilineBodyAfterKnownWrapper() {
        assertEquals("这是个好主意。", PlainGeneratedContentNormalizer.normalize("这是个好主意。", 2, 40))
        assertEquals(
            "今天下雨，心情有点闷。\n晚上又去甲板吹了会风。",
            PlainGeneratedContentNormalizer.normalize("下面是日记：今天下雨，心情有点闷。\n晚上又去甲板吹了会风。", 2, 80)
        )
    }

    @Test
    fun enforcesLengthAndUsesSentenceBoundaryWhenTruncating() {
        assertNull(PlainGeneratedContentNormalizer.normalize("太短", 3, 20))
        assertEquals("第一句已经结束。", PlainGeneratedContentNormalizer.normalize("第一句已经结束。第二句不应保留", 3, 8))
    }

    @Test
    fun usesCodePointsConsistentlyAndRejectsNonStringJsonValues() {
        assertEquals("😀😀", PlainGeneratedContentNormalizer.normalize("😀😀", 2, 2))
        assertNull(PlainGeneratedContentNormalizer.normalize("😀😀", 3, 4))
        assertNull(PlainGeneratedContentNormalizer.normalize("{\"content\":12345}", 2, 40))
        assertNull(PlainGeneratedContentNormalizer.normalize("{\"content\":true}", 2, 40))
    }
}
