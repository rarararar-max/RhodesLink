package com.rhodes.privatechat

import com.rhodes.privatechat.data.PromptPlaceholderRegistry
import com.rhodes.privatechat.viewmodel.shared.CachePromptLayering
import com.rhodes.privatechat.viewmodel.shared.GroupRuntimeContext
import com.rhodes.privatechat.viewmodel.shared.PromptTemplates
import org.junit.Assert.*
import org.junit.Test

class GroupRuntimeContextTest {
    private val profile = "名字：夕\n发言标识：dusk\n人设：完整卡片_UNIQUE_PROFILE"
    private val values = mapOf(
        "MEMBER_PROFILES" to profile, "MEMBER_NAMES" to "夕",
        "GROUP_SUMMARY" to "摘要_UNIQUE_SUMMARY", "SHORT_TERM_SUMMARY" to "摘要_UNIQUE_SUMMARY",
        "GROUP_CONTEXT" to "摘要_UNIQUE_SUMMARY",
        "MEMORY_V2_CONTEXT" to "记忆_UNIQUE_MEMORY", "MEMORY_ANCHORS" to "记忆_UNIQUE_MEMORY",
        "SHARED_MEMORIES" to "记忆_UNIQUE_MEMORY", "GROUP_RULES" to "独立规则_UNIQUE_RULE",
    )
    private fun render(template: String, data: Map<String, String> = values) =
        Regex("\\{\\{([A-Z0-9_]+)\\}\\}").replace(template) { data[it.groupValues[1]] ?: it.value }

    private fun build(
        custom: String? = null,
        template: String = "{{MEMBER_PROFILES}}\n{{GROUP_SUMMARY}}\n{{MEMORY_V2_CONTEXT}}\n{{GROUP_RULES}}",
        isCustomTemplate: Boolean = true,
        data: Map<String, String> = values,
        natural: String = "自然资料",
        continuity: String = "",
    ) = GroupRuntimeContext.build(
        continuity, custom, natural, data, { render(it, data) },
        templateRuntime = { excluded ->
            if (isCustomTemplate) CachePromptLayering.runtimeContext(
                template, data, PromptPlaceholderRegistry.runtimeKeys("group", "director"),
                excluded, deduplicateAliases = true,
            ) else ""
        },
        memberIdentifiers = "夕=dusk",
    )
    private fun occurrences(text: String, needle: String) = text.windowed(needle.length).count { it == needle }

    @Test fun defaultModesStillSupplyExactlyOneFullPersona() {
        for (mode in listOf("online", "offline", "director", "auto")) {
            val result = build(template = PromptTemplates.get("group", mode), isCustomTemplate = false)
            assertEquals(mode, 1, occurrences(result, profile))
            assertTrue(result.contains("发言标识：dusk"))
        }
    }

    @Test fun customRuntimeAndCustomMainTemplateDoNotTripleThePersona() {
        val result = build(custom = "用户自定义要求\n{{MEMBER_PROFILES}}")
        assertEquals(1, occurrences(result, profile))
        assertTrue(result.contains("用户自定义要求"))
        assertTrue(result.contains("夕=dusk"))
        assertTrue(result.contains("独立规则_UNIQUE_RULE"))
    }

    @Test fun removingRuntimePlaceholderStillKeepsOneAppProvidedPersona() {
        val result = build(custom = "只写当前场景")
        assertEquals(1, occurrences(result, profile))
        assertTrue(result.contains("只写当前场景"))
    }

    @Test fun customRuntimeWithDefaultMainTemplateAlsoSuppliesOnePersona() {
        assertEquals(1, occurrences(build(custom = "{{MEMBER_PROFILES}}", isCustomTemplate = false), profile))
    }

    @Test fun blankCustomModuleDoesNotRemoveRequiredMemberDataOrOtherTemplateFields() {
        val result = build(custom = "")
        assertEquals(1, occurrences(result, profile))
        assertTrue(result.contains("独立规则_UNIQUE_RULE"))
        assertFalse(result.contains("自然资料"))
    }

    @Test fun summaryAndMemoryAliasesKeepOneBodyAndPreserveUserInstructions() {
        val custom = "请遵守原有指令\n摘要={{GROUP_SUMMARY}}｜回顾={{GROUP_CONTEXT}}｜短期={{SHORT_TERM_SUMMARY}}\n" +
            "相关={{MEMORY_V2_CONTEXT}}｜共享={{SHARED_MEMORIES}}｜锚点={{MEMORY_ANCHORS}}"
        val result = build(custom = custom)
        assertEquals(1, occurrences(result, "摘要_UNIQUE_SUMMARY"))
        assertEquals(1, occurrences(result, "记忆_UNIQUE_MEMORY"))
        assertTrue(result.contains("请遵守原有指令"))
        assertTrue(result.contains("同本轮已提供的群聊摘要"))
        assertTrue(result.contains("同本轮已提供的相关记忆"))
    }

    @Test fun distinctAliasValuesAreNeverDiscarded() {
        val data = values + ("MEMORY_ANCHORS" to "不同锚点") + ("GROUP_CONTEXT" to "不同回顾")
        val result = build(custom = "{{MEMORY_V2_CONTEXT}}\n{{MEMORY_ANCHORS}}\n{{GROUP_SUMMARY}}\n{{GROUP_CONTEXT}}", data = data)
        assertTrue(result.contains("不同锚点"))
        assertTrue(result.contains("不同回顾"))
        assertTrue(result.contains("记忆_UNIQUE_MEMORY"))
    }

    @Test fun unrelatedFieldsWithIdenticalTextAreNotTreatedAsAliases() {
        val data = values + ("GROUP_RULES" to "相同文字") + ("USER_BIO" to "相同文字")
        val result = build(custom = "{{GROUP_RULES}}\n{{USER_BIO}}", template = "{{MEMBER_PROFILES}}", data = data)
        assertEquals(2, occurrences(result, "相同文字"))
    }

    @Test fun naturalContextPreventsRepeatingItsSameSummaryInCustomTemplateSupplement() {
        val result = build(natural = "【回顾】\n摘要_UNIQUE_SUMMARY\n【记忆】\n记忆_UNIQUE_MEMORY")
        assertEquals(1, occurrences(result, "摘要_UNIQUE_SUMMARY"))
        assertEquals(1, occurrences(result, "记忆_UNIQUE_MEMORY"))
    }

    @Test fun duplicateKeysInTheSameCustomModuleDoNotRepeatThePersona() {
        assertEquals(1, occurrences(build(custom = "{{MEMBER_PROFILES}}\n{{MEMBER_PROFILES}}"), profile))
    }

    @Test fun sameFieldInRuntimeAndMainTemplateKeepsOneValueWithoutLosingOtherFields() {
        val result = build(custom = "{{GROUP_RULES}}")
        assertEquals(1, occurrences(result, "独立规则_UNIQUE_RULE"))
        assertTrue(result.contains("摘要_UNIQUE_SUMMARY"))
        assertTrue(result.contains("记忆_UNIQUE_MEMORY"))
    }

    @Test fun multiMemberRosterRetainsBothFullCardsAndTheirSpeakerIds() {
        val cards = profile + "\n\n名字：阿米娅\n发言标识：amiya\n人设：第二张完整人设"
        val result = build(custom = "{{MEMBER_PROFILES}}", data = values + ("MEMBER_PROFILES" to cards))
        assertEquals(1, occurrences(result, cards))
        assertTrue(result.contains("发言标识：dusk"))
        assertTrue(result.contains("发言标识：amiya"))
    }

    @Test fun userLiteralsAndUnsupportedPlaceholdersSurviveRendering() {
        val result = build(custom = "这是用户的原始指令。\n{{MY_UNKNOWN_KEY}}\n{{MEMBER_PROFILES}}")
        assertTrue(result.contains("这是用户的原始指令。"))
        assertTrue(result.contains("{{MY_UNKNOWN_KEY}}"))
    }

    @Test fun privateLayeringKeepsItsExistingBehavior() {
        val template = "{{MEMORY_V2_CONTEXT}}\n{{MEMORY_ANCHORS}}"
        val result = CachePromptLayering.build(template, values, PromptPlaceholderRegistry.runtimeKeys("private")) {
            render(template, it)
        }
        assertEquals(2, occurrences(result.runtimeContext, "记忆_UNIQUE_MEMORY"))
        assertTrue(result.runtimeContext.contains("【最近记住的事】"))
    }

    @Test fun personaContentIsNotRescannedAsTemplateSyntax() {
        val literalProfile = profile + "\n用户原文{{MEMORY_V2_CONTEXT}}"
        val result = build(custom = "{{MEMBER_PROFILES}}", data = values + ("MEMBER_PROFILES" to literalProfile))
        assertTrue(result.contains(literalProfile))
        assertEquals(1, occurrences(result, literalProfile))
    }

    @Test fun templateOnlyAliasesDeduplicateWithoutTouchingTheirCachedSystem() {
        val template = "{{MEMORY_V2_CONTEXT}}\n{{MEMORY_ANCHORS}}"
        val dynamic = PromptPlaceholderRegistry.runtimeKeys("group", "director")
        val before = CachePromptLayering.build(template, values, dynamic) { render(template, it) }
        val runtime = CachePromptLayering.runtimeContext(template, values, dynamic, deduplicateAliases = true)
        assertEquals(1, occurrences(runtime, "记忆_UNIQUE_MEMORY"))
        assertFalse(before.system.contains("记忆_UNIQUE_MEMORY"))
        assertEquals("见本轮资料\n见本轮资料", before.system)
    }

    @Test fun continuityAndNoMemberScenariosRemainIntact() {
        val result = build(custom = "", data = emptyMap(), continuity = "已验证连续性")
        assertTrue(result.contains("已验证连续性"))
        assertFalse(result.contains("人设："))
    }

    @Test fun legacyFieldWarningDoesNotClaimItWillRemainLiteral() {
        assertFalse("GROUP_SUMMARIES" in PromptPlaceholderRegistry.allowed("group"))
        val warning = PromptPlaceholderRegistry.unsupportedWarning("GROUP_SUMMARIES")
        assertTrue(warning.contains("旧版兼容"))
        assertTrue(warning.contains("默认值"))
        assertFalse(warning.contains("会保留原样"))
        assertTrue(PromptPlaceholderRegistry.unsupportedWarning("MY_UNKNOWN_KEY").contains("保留原样"))
    }
}
