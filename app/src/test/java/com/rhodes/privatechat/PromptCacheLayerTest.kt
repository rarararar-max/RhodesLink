package com.rhodes.privatechat

import com.rhodes.privatechat.viewmodel.shared.CachePromptLayering
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import com.rhodes.privatechat.viewmodel.shared.SharedUtils
import com.rhodes.privatechat.data.PromptPlaceholderRegistry
import com.rhodes.privatechat.shared.settings.PromptTemplateMigration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptCacheLayerTest {
    private fun layers(replacements: Map<String, String>): SharedUtils.CachePromptLayers {
        val template = """
            【规则】你是{{OPERATOR_NAME}}。
            时间：{{CURRENT_TIME}}
            记忆：{{MEMORY_V2_CONTEXT}}
            用户：{{USER_CONTENT}}
            输出：JSON。
        """.trimIndent()
        return CachePromptLayering.build(
            template,
            replacements,
            setOf("CURRENT_TIME", "MEMORY_V2_CONTEXT", "USER_CONTENT")
        ) { values ->
            values.entries.fold(template) { rendered, (key, value) -> rendered.replace("{{$key}}", value) }.trim()
        }
    }

    @Test
    fun cacheSystemIsStableWhenRuntimeValuesChange() {
        val first = layers(mapOf(
            "OPERATOR_NAME" to "阿米娅",
            "CURRENT_TIME" to "08:00",
            "MEMORY_V2_CONTEXT" to "昨天一起吃饭",
            "USER_CONTENT" to "早上好"
        ))
        val second = layers(mapOf(
            "OPERATOR_NAME" to "阿米娅",
            "CURRENT_TIME" to "22:00",
            "MEMORY_V2_CONTEXT" to "刚刚完成任务",
            "USER_CONTENT" to "晚安"
        ))

        assertEquals(first.system, second.system)
        assertFalse(first.system.contains("08:00"))
        assertFalse(first.system.contains("昨天一起吃饭"))
        assertFalse(first.system.contains("早上好"))
        assertTrue(first.runtimeContext.contains("08:00"))
        assertTrue(first.runtimeContext.contains("昨天一起吃饭"))
        assertFalse(first.runtimeContext.contains("早上好"))
    }

    @Test
    fun cacheSystemIsStableWhenRuntimeValuesAppearOrDisappear() {
        val empty = layers(mapOf(
            "OPERATOR_NAME" to "阿米娅",
            "CURRENT_TIME" to "",
            "MEMORY_V2_CONTEXT" to "",
            "USER_CONTENT" to ""
        ))
        val populated = layers(mapOf(
            "OPERATOR_NAME" to "阿米娅",
            "CURRENT_TIME" to "08:00",
            "MEMORY_V2_CONTEXT" to "昨天一起吃饭",
            "USER_CONTENT" to "你好"
        ))

        assertEquals(empty.system, populated.system)
        assertTrue(empty.system.contains("见本轮运行时上下文"))
        assertEquals("【本轮运行时上下文】\n无", empty.runtimeContext)
    }

    @Test
    fun staticValuesRemainInCacheSystem() {
        val result = layers(mapOf(
            "OPERATOR_NAME" to "阿米娅",
            "CURRENT_TIME" to "08:00",
            "MEMORY_V2_CONTEXT" to "无",
            "USER_CONTENT" to "你好"
        ))

        assertTrue(result.system.contains("阿米娅"))
        assertTrue(result.system.contains("见本轮运行时上下文"))
    }

    @Test
    fun allShippedChatTemplatesKeepTheirSystemStableAcrossRuntimeChanges() {
        val modes = listOf(
            "private" to "online", "private" to "offline", "private" to "director",
            "private" to "proactive",
            "group" to "online", "group" to "offline", "group" to "director", "group" to "auto",
            "moment" to "", "moment_comment" to "", "diary" to ""
        )
        modes.forEach { (type, mode) ->
            val template = PromptTemplates.get(type, mode)
            val runtime = PromptPlaceholderRegistry.runtimeKeys(type, mode)
            val first = PromptPlaceholderRegistry.allowed(type, mode).associateWith { key -> if (key in runtime) "first-$key" else "fixed-$key" }
            val second = PromptPlaceholderRegistry.allowed(type, mode).associateWith { key -> if (key in runtime) "second-$key" else "fixed-$key" }
            val render: (Map<String, String>) -> String = { values ->
                values.entries.fold(template) { rendered, (key, value) -> rendered.replace("{{$key}}", value) }.trim()
            }
            val firstLayer = CachePromptLayering.build(template, first, runtime, render)
            val secondLayer = CachePromptLayering.build(template, second, runtime, render)
            assertEquals("$type/$mode", firstLayer.system, secondLayer.system)
        }
    }

    @Test
    fun runtimeContextUsesStableKeyOrderingAndNeverIncludesUserContent() {
        val template = """
            角色：{{OPERATOR_NAME}}
            时间：{{CURRENT_TIME}}
            记忆：{{MEMORY_V2_CONTEXT}}
            用户：{{USER_CONTENT}}
        """.trimIndent()
        val replacements = linkedMapOf(
            "USER_CONTENT" to "不能进入运行时资料的本轮输入",
            "MEMORY_V2_CONTEXT" to "相关记忆",
            "CURRENT_TIME" to "09:30",
            "OPERATOR_NAME" to "阿米娅"
        )
        val layer = CachePromptLayering.build(
            template = template,
            replacements = replacements,
            dynamicKeys = setOf("USER_CONTENT", "MEMORY_V2_CONTEXT", "CURRENT_TIME")
        ) { values ->
            values.entries.fold(template) { rendered, (key, value) -> rendered.replace("{{$key}}", value) }.trim()
        }

        assertEquals(
            "【CURRENT_TIME】\n09:30\n【MEMORY_V2_CONTEXT】\n相关记忆",
            layer.runtimeContext
        )
        assertFalse(layer.runtimeContext.contains("不能进入运行时资料的本轮输入"))
        assertFalse(layer.system.contains("不能进入运行时资料的本轮输入"))
    }

    @Test
    fun runtimeContextExcludesDynamicKeysNotReferencedByTemplate() {
        val template = """
            角色：{{OPERATOR_NAME}}
            时间：{{CURRENT_TIME}}
        """.trimIndent()
        val layer = CachePromptLayering.build(
            template = template,
            replacements = mapOf(
                "OPERATOR_NAME" to "阿米娅",
                "CURRENT_TIME" to "09:30",
                "MEMORY_V2_CONTEXT" to "不应发送的无关记忆",
                "RECENT_SOCIAL_CONTEXT" to "不应发送的无关动态"
            ),
            dynamicKeys = setOf("CURRENT_TIME", "MEMORY_V2_CONTEXT", "RECENT_SOCIAL_CONTEXT")
        ) { values ->
            values.entries.fold(template) { rendered, (key, value) -> rendered.replace("{{$key}}", value) }.trim()
        }

        assertEquals("【CURRENT_TIME】\n09:30", layer.runtimeContext)
        assertFalse(layer.runtimeContext.contains("无关记忆"))
        assertFalse(layer.runtimeContext.contains("无关动态"))
    }

    @Test
    fun shippedNonChatTemplatesKeepTheirDeclaredPlaceholdersAvailable() {
        val expected = mapOf(
            "moment" to setOf("OPERATOR_NAME", "CURRENT_TIME", "MOMENT_TRIGGER_TYPE", "MOMENT_MIN_CHARS"),
            "moment_comment" to setOf("COMMENTER_NAME", "POST_CONTENT", "COMMENT_TASK", "COMMENT_MIN_CHARS"),
            "diary" to setOf("OPERATOR_NAME", "YESTERDAY_DATE", "PRIVATE_DAILY_SUMMARY", "DIARY_MIN_CHARS"),
            "dispatch" to setOf("TASK_TYPE", "MEMBER_PROFILES", "DISPATCH_MIN_CHARS")
        )

        expected.forEach { (type, placeholders) ->
            val allowed = PromptPlaceholderRegistry.allowed(type)
            assertTrue("$type should expose all compatibility placeholders", allowed.containsAll(placeholders))
        }
    }

    @Test
    fun allShippedTemplatePlaceholdersAreAllowedForTheirSurface() {
        val templates = listOf(
            "private" to "online", "private" to "offline", "private" to "director", "private" to "proactive",
            "group" to "online", "group" to "offline", "group" to "director", "group" to "auto",
            "moment" to "", "moment_comment" to "", "diary" to "",
            "dispatch" to "start", "dispatch" to "progress", "dispatch" to "ending"
        )
        val tokenPattern = Regex("\\{\\{([A-Z0-9_]+)\\}\\}")

        templates.forEach { (type, mode) ->
            val tokens = tokenPattern.findAll(PromptTemplates.get(type, mode)).map { it.groupValues[1] }.toSet()
            assertTrue(
                "$type/$mode contains unsupported placeholders: ${tokens - PromptPlaceholderRegistry.allowed(type, mode)}",
                PromptPlaceholderRegistry.allowed(type, mode).containsAll(tokens)
            )
        }
    }

    @Test
    fun nonChatRuntimeKeysKeepVolatileContentOutOfTheSystemPrefix() {
        val expectedRuntimeKeys = mapOf(
            "moment" to setOf("CURRENT_TIME", "RECENT_MEMORIES", "RECENT_POSTS", "USER_NAME"),
            "moment_comment" to setOf("CURRENT_TIME", "POST_CONTENT", "COMMENT_CONTEXT", "COMMENT_TASK"),
            "diary" to setOf("CURRENT_DATE", "YESTERDAY_DATE", "PRIVATE_SUMMARY", "RECENT_MEMORIES")
        )

        expectedRuntimeKeys.forEach { (type, keys) ->
            val runtime = PromptPlaceholderRegistry.runtimeKeys(type)
            assertTrue("$type runtime keys", runtime.containsAll(keys))
            assertFalse("$type operator persona must stay cacheable", "OPERATOR_PERSONA" in runtime)
        }
    }

    @Test
    fun unknownPlaceholderRemainsVisibleInRenderedTemplate() {
        val template = "角色：{{OPERATOR_NAME}}，保留：{{FUTURE_COMPATIBILITY_TOKEN}}"
        val rendered = template.replace("{{OPERATOR_NAME}}", "阿米娅")

        assertEquals("角色：阿米娅，保留：{{FUTURE_COMPATIBILITY_TOKEN}}", rendered)
    }

    @Test
    fun v18LegacyTemplateIsPreservedInsteadOfOverwritten() {
        assertTrue(PromptTemplateMigration.preserveLegacyTemplate("用户历史模板", false, 18, 19))
        assertFalse(PromptTemplateMigration.preserveLegacyTemplate("", false, 18, 19))
        assertFalse(PromptTemplateMigration.preserveLegacyTemplate("用户模板", true, 18, 19))
        assertFalse(PromptTemplateMigration.preserveLegacyTemplate("当前模板", false, 19, 19))
    }
}
