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

    @Test
    fun proactiveTemplateUsesTheActiveTagProtocolInsteadOfJson() {
        val template = PromptTemplates.get("private", "proactive")

        assertTrue(template.contains("【状态】"))
        assertTrue(template.contains("【台词】"))
        assertTrue(template.contains("不要 JSON"))
        assertTrue(!template.contains("\"segments\""))
    }

    @Test
    fun privateProtocolKeepsIntentAnalysisInternal() {
        val protocol = com.rhodes.privatechat.viewmodel.PromptModuleDefaults.outputProtocol("private", "online")
        assertTrue(protocol.contains("【用户发言意图分析】"))
        assertTrue(protocol.contains("补全后的完整语义"))
        assertTrue(protocol.contains("深层真实的意图"))
        assertTrue(protocol.contains("期望回应"))
        assertTrue(protocol.contains("不得在【旁白】、【台词】"))
        assertTrue(protocol.contains("【输出格式示例"))
        assertTrue(protocol.contains("【本轮简述】用户提出想去角色房间"))
    }

    @Test
    fun privateBehaviorDoesNotTreatIntentGuessAsUserFact() {
        val behavior = com.rhodes.privatechat.viewmodel.PromptModuleDefaults.behavior("private", "online")
        assertTrue(behavior.contains("不得把推测伪装成用户明确说过的话"))
        assertTrue(behavior.contains("用户本轮明确原话始终优先"))
        assertTrue(behavior.contains("【本轮增量】"))
        assertTrue(behavior.contains("连续性不等于重复"))
        assertTrue(behavior.contains("【期望回应】只写本轮需要完成的新回应目标"))
    }

    @Test
    fun privateHypnosisRulesRequireVisibleAlteredBehavior() {
        val behavior = com.rhodes.privatechat.viewmodel.PromptModuleDefaults.behavior("private", "offline")
        assertTrue(behavior.contains("【本轮强制角色状态】"))
        assertTrue(behavior.contains("不能抵消正在生效的状态"))
    }

    @Test
    fun offlineNarrationProtocolKeepsUserActionsAndNarrationBoundaries() {
        val protocol = com.rhodes.privatechat.viewmodel.PromptModuleDefaults.narrationProtocol("offline")

        assertTrue(protocol.contains("不得使用“用户”"))
        assertTrue(protocol.contains("用户昵称"))
        assertTrue(protocol.contains("第一人称"))
        assertTrue(protocol.contains("用户发言意图分析只是内部推断"))
        assertTrue(protocol.contains("旁白】与紧接的【台词】不得表达同一信息"))
        assertTrue(protocol.contains("同义重复"))
        assertEquals("", com.rhodes.privatechat.viewmodel.PromptModuleDefaults.narrationProtocol("online"))
    }

    @Test
    fun offlineTemplateKeepsTheUserReferenceInRuntimeContext() {
        val template = PromptTemplates.get("private", "offline")

        assertTrue(template.contains("与本轮资料中的用户面对面互动"))
        assertFalse(template.contains("与{{USER_NAME}}面对面互动"))
    }
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
        assertTrue(empty.system.contains("见本轮资料"))
        assertEquals("【本轮资料】\n暂无与本轮相关的补充资料。", empty.runtimeContext)
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
        assertTrue(result.system.contains("见本轮资料"))
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
            "【当前时间】\n09:30\n【可能相关的过往经历】\n相关记忆",
            layer.runtimeContext
        )
        assertFalse(layer.runtimeContext.contains("不能进入运行时资料的本轮输入"))
        assertFalse(layer.system.contains("不能进入运行时资料的本轮输入"))
    }

    @Test
    fun runtimeContextUsesNaturalLabelsInsteadOfInternalPlaceholderNames() {
        val template = """
            进展：{{SHORT_TERM_SUMMARY}}
            群聊：{{GROUP_CONTEXT}}
            动态：{{RECENT_SOCIAL_CONTEXT}}
        """.trimIndent()
        val layer = CachePromptLayering.build(
            template = template,
            replacements = mapOf(
                "SHORT_TERM_SUMMARY" to "刚刚约好晚些再聊",
                "GROUP_CONTEXT" to "群里在讨论晚餐",
                "RECENT_SOCIAL_CONTEXT" to "阿米娅发了一条动态"
            ),
            dynamicKeys = setOf("SHORT_TERM_SUMMARY", "GROUP_CONTEXT", "RECENT_SOCIAL_CONTEXT")
        ) { values ->
            values.entries.fold(template) { rendered, (key, value) -> rendered.replace("{{$key}}", value) }.trim()
        }

        assertTrue(layer.runtimeContext.contains("【最近聊天进展】"))
        assertTrue(layer.runtimeContext.contains("【从群聊得知的近况】"))
        assertTrue(layer.runtimeContext.contains("【近期公开动态与评论】"))
        assertFalse(layer.runtimeContext.contains("SHORT_TERM_SUMMARY"))
        assertFalse(layer.runtimeContext.contains("GROUP_CONTEXT"))
        assertFalse(layer.runtimeContext.contains("RECENT_SOCIAL_CONTEXT"))
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

        assertEquals("【当前时间】\n09:30", layer.runtimeContext)
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
    fun shippedPrivateTemplatesDoNotContainRetiredAnalysisPlaceholder() {
        listOf("online", "offline", "director", "proactive").forEach { mode ->
            assertFalse("private/$mode", PromptTemplates.get("private", mode).contains("{{AI_ANALYSIS}}"))
        }
    }

    @Test
    fun promptTemplateVersionAdvancesForTheCurrentPromptRevision() {
        assertEquals(29, PromptTemplates.VERSION)
    }

    @Test
    fun shippedGroupTemplatesUseTheRuntimeTagProtocolInsteadOfJson() {
        listOf("online", "offline", "director", "auto").forEach { mode ->
            val template = PromptTemplates.get("group", mode)
            assertFalse("group/$mode must not prescribe JSON", template.contains("JSON", ignoreCase = true))
            assertFalse("group/$mode must not prescribe a JSON schema", template.contains("speaker="))
            assertFalse("group/$mode must not include retired planner guidance", template.contains("{{GROUP_TURN_GUIDANCE}}"))
        }
    }

    @Test
    fun privateIdentityStaysInTheCacheablePromptPrefix() {
        val runtime = PromptPlaceholderRegistry.runtimeKeys("private", "online")

        assertFalse("operator name must stay cacheable", "OPERATOR_NAME" in runtime)
        assertFalse("operator title must stay cacheable", "OPERATOR_TITLE" in runtime)
        assertFalse("operator persona must stay cacheable", "OPERATOR_PERSONA" in runtime)
        assertFalse("operator gender must stay cacheable", "OPERATOR_GENDER" in runtime)
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
    fun unknownPlaceholderIsDetectedBeforeTemplateDispatch() {
        val template = "角色：{{OPERATOR_NAME}}，保留：{{FUTURE_COMPATIBILITY_TOKEN}}"
        val rendered = template.replace("{{OPERATOR_NAME}}", "阿米娅")

        assertEquals("角色：阿米娅，保留：{{FUTURE_COMPATIBILITY_TOKEN}}", rendered)
        assertTrue(Regex("\\{\\{([A-Z0-9_]+)\\}\\}").containsMatchIn(rendered))
    }

    @Test
    fun v18LegacyTemplateIsPreservedInsteadOfOverwritten() {
        assertTrue(PromptTemplateMigration.preserveLegacyTemplate("用户历史模板", false, 18, 19))
        assertFalse(PromptTemplateMigration.preserveLegacyTemplate("", false, 18, 19))
        assertFalse(PromptTemplateMigration.preserveLegacyTemplate("用户模板", true, 18, 19))
        assertFalse(PromptTemplateMigration.preserveLegacyTemplate("当前模板", false, 19, 19))
    }

}
