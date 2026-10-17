package com.rhodes.privatechat

import com.rhodes.privatechat.ui.support.*
import com.rhodes.privatechat.viewmodel.*
import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.russhwolf.settings.ObservableSettings
import java.lang.reflect.Proxy
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class SupportReliabilityTest {
    private fun repository(initial: Map<String, Any> = emptyMap()): SettingsRepository {
        val data = initial.toMutableMap()
        val backend = Proxy.newProxyInstance(ObservableSettings::class.java.classLoader,
            arrayOf(ObservableSettings::class.java, com.rhodes.privatechat.shared.settings.AtomicSettingsBatch::class.java)) { proxy, method, args ->
            val a = args.orEmpty()
            when {
                method.name == "commitSupportState" -> {
                    @Suppress("UNCHECKED_CAST") val strings = a[0] as Map<String, String>
                    @Suppress("UNCHECKED_CAST") val ints = a[1] as Map<String, Int>
                    data.putAll(strings); data.putAll(ints); true
                }
                method.name == "hasKey" -> data.containsKey(a[0])
                method.name == "getKeys" -> data.keys.toSet()
                method.name == "getSize" -> data.size
                method.name.startsWith("get") -> data[a[0]] ?: a.getOrNull(1)
                method.name.startsWith("put") -> { data[a[0] as String] = a[1]; null }
                method.name == "remove" -> { data.remove(a[0]); null }
                method.name == "clear" -> { data.clear(); null }
                method.name == "equals" -> proxy === a[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "TestSettings"
                else -> null
            }
        } as ObservableSettings
        return SettingsRepository(backend)
    }


    @Test fun invalidatedRequestCannotWriteIntoNewConversation() {
        val gate = SupportRequestGate()
        val old = gate.begin()
        gate.invalidate()
        assertFalse(gate.isCurrent(old))
        val next = gate.begin()
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(next))
    }
    @Test fun oldConversationPreservesUnclaimedPacketsAndAllHistory() {
        val messages = (1L..250L).map { AiSupportMessage(it, "assistant", "记录", redPacketAmount = if (it == 1L) 88 else 0) }
        assertEquals(250, sanitizeSupportConversation(messages).size)
        val old = Json.decodeFromString<AiSupportMessage>("""{"id":1,"role":"assistant","text":"旧记录","redPacketAmount":88}""")
        assertFalse(old.isError)
        assertFalse(old.redPacketClaimed)
        assertEquals("", old.manualVersion)
    }
    @Test fun claimIsAtomicAndStaleTranscriptCannotClaimAgain() {
        val s = repository(mapOf("support_conversation" to "before", "lmb" to 1000))
        assertTrue(s.claimSupportPacket("before", "claimed", 88))
        assertEquals(1088, s.lmb)
        assertEquals("claimed", s.supportConversation)
        assertFalse(s.claimSupportPacket("before", "claimed", 88))
        assertEquals(1088, s.lmb)
    }
    @Test fun publicationCommitsTranscriptAndDailyBudgetTogether() {
        val s = repository(mapOf("support_conversation" to "before"))
        assertTrue(s.saveSupportReply("before", "packet", "nuan", "2026-10-16", 200))
        assertEquals(300, s.supportLmbRemaining("nuan", "2026-10-16"))
        assertFalse(s.saveSupportReply("before", "duplicate", "nuan", "2026-10-16", 200))
        assertEquals("packet", s.supportConversation)
        assertEquals(300, s.supportLmbRemaining("nuan", "2026-10-16"))
    }
    @Test fun invalidAmountNeverMutatesWallet() {
        val s = repository(mapOf("support_conversation" to "before", "lmb" to 1000))
        assertFalse(s.claimSupportPacket("before", "claimed", 999))
        assertFalse(s.claimSupportPacket("before", "claimed", -1))
        assertEquals(1000, s.lmb)
        assertEquals("before", s.supportConversation)
    }
    @Test fun shortFollowupRetrievesPreviousQuestionTopic() {
        assertTrue(AiSupportContract.retrievalQuery("那怎么设置？", listOf("识图配置失败")).contains("识图配置失败"))
        assertEquals("我要换皮肤", AiSupportContract.retrievalQuery("我要换皮肤", listOf("识图配置失败")))
    }
    @Test fun failedTechnicalMessageIsNotSentAsAssistantAdvice() {
        val result = AiSupportContract.historyAfter(listOf(AiSupportMessage(1,"user","问题"), AiSupportMessage(2,"assistant","网络失败",isError=true)),0)
        assertEquals(listOf(AiMessage("user","问题")), result)
    }
    @Test fun languagePromiseNeverImpliesAnActualPacket() {
        assertTrue(AiSupportContract.visibleReply("给你个红包【红包金额:88】",0).contains("本条未发放红包"))
        assertFalse(AiSupportContract.visibleReply("给你个红包【红包金额:88】",88).contains("本条未发放红包"))
    }
    @Test fun imageFailureShowsOnlySafeMessage() {
        assertEquals("图片无法读取", AiSupportContract.userError(SupportImageException("图片无法读取")))
        assertFalse(AiSupportContract.userError(Exception("private response body")).contains("private response body"))
    }
}
