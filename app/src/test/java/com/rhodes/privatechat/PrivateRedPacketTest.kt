package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.settings.AtomicSettingsBatch
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.russhwolf.settings.ObservableSettings
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class PrivateRedPacketTest {
    private fun repository(balance: Int = 100, commitOk: Boolean = true, atomic: Boolean = true): SettingsRepository {
        val data = mutableMapOf<String, Any>("lmb" to balance)
        val interfaces = if (atomic) arrayOf(ObservableSettings::class.java, AtomicSettingsBatch::class.java)
            else arrayOf(ObservableSettings::class.java)
        val settings = Proxy.newProxyInstance(ObservableSettings::class.java.classLoader, interfaces) { proxy, method, args ->
            val a = args.orEmpty()
            when {
                method.name == "commitSupportState" -> {
                    if (commitOk) {
                        @Suppress("UNCHECKED_CAST") val strings = a[0] as Map<String, String>
                        @Suppress("UNCHECKED_CAST") val ints = a[1] as Map<String, Int>
                        data.putAll(strings); data.putAll(ints)
                    }
                    commitOk
                }
                method.name == "hasKey" -> data.containsKey(a[0])
                method.name == "getKeys" -> data.keys.toSet()
                method.name == "getSize" -> data.size
                method.name.startsWith("get") -> data[a[0]] ?: a.getOrNull(1)
                method.name.startsWith("put") -> { data[a[0] as String] = a[1]; null }
                method.name == "remove" -> { data.remove(a[0]); null }
                method.name == "equals" -> proxy === a[0]
                method.name == "hashCode" -> System.identityHashCode(proxy)
                method.name == "toString" -> "TestNewspaperSettings"
                else -> null
            }
        } as ObservableSettings
        return SettingsRepository(settings)
    }

    private fun packet(amount: Int = 66) = PrivateRedPacket(
        "12345678-1234-1234-1234-123456789abc", "private_kaltsit", "kaltsit", "凯尔希", amount,
        "请大家喝点热饮。", messageId = 100, createdAt = 1000)

    @Test fun sendAndClaimAreIdempotentAndClaimDoesNotDebitAgain() {
        val store = repository()
        repeat(3) { assertTrue(store.reservePrivateRedPacket(packet())) }
        assertEquals(34, store.lmb)
        assertEquals(listOf(packet().id), store.pendingPrivateRedPackets())
        val first = store.settlePrivateRedPacket(packet().id, true, 2000)!!
        repeat(3) { assertEquals(first, store.settlePrivateRedPacket(packet().id, true, 3000)) }
        assertEquals(34, store.lmb)
        assertEquals(2000, first.claimedAt)
        assertTrue(store.acknowledgePrivateRedPacket(first.id))
        assertTrue(store.pendingPrivateRedPackets()!!.isEmpty())
        assertEquals(first, store.privateRedPacket(first.id))
    }
    @Test fun claimedPacketCannotBeRefundedAndResendCannotChargeAgain() {
        val store = repository()
        assertTrue(store.reservePrivateRedPacket(packet()))
        store.settlePrivateRedPacket(packet().id, true, 2000)
        assertNull(store.settlePrivateRedPacket(packet().id, false, 3000))
        assertFalse(store.reservePrivateRedPacket(packet()))
        assertEquals(34, store.lmb)
    }
    @Test fun unwrittenMessageRefundsExactlyOnce() {
        val store = repository()
        assertTrue(store.reservePrivateRedPacket(packet()))
        repeat(4) { assertEquals("refunded", store.settlePrivateRedPacket(packet().id, false, 2000)?.status) }
        assertEquals(100, store.lmb)
        assertNull(store.settlePrivateRedPacket(packet().id, true, 3000))
    }
    @Test fun insufficientBalanceAndInvalidAmountsNeverCreateTransactions() {
        val store = repository(65)
        listOf(-1, 0, 66, 20001, Int.MAX_VALUE).forEach { assertFalse(store.reservePrivateRedPacket(packet(it))) }
        assertEquals(65, store.lmb)
        assertNull(store.privateRedPacket(packet().id))
        assertTrue(store.pendingPrivateRedPackets()!!.isEmpty())
    }
    @Test fun brokenAtomicStorageCannotDebitOrPublishTransaction() {
        listOf(repository(commitOk = false), repository(atomic = false)).forEach { store ->
            assertFalse(store.reservePrivateRedPacket(packet()))
            assertEquals(100, store.lmb)
            assertNull(store.privateRedPacket(packet().id))
        }
    }
    @Test fun duplicateIdWithDifferentPayloadIsRejected() {
        val store = repository()
        assertTrue(store.reservePrivateRedPacket(packet()))
        assertFalse(store.reservePrivateRedPacket(packet(50)))
        assertFalse(store.reservePrivateRedPacket(packet().copy(recipientId = "amiya")))
        assertEquals(34, store.lmb)
    }
    @Test fun malformedRecordsAndUnboundedWishesAreRejected() {
        assertNull(PrivateRedPacket.parse("{}"))
        assertNull(PrivateRedPacket.parse("not json"))
        assertFalse(packet().copy(wish = "长".repeat(61)).valid())
        assertFalse(packet().copy(wish = "a\nb").valid())
        assertFalse(packet().copy(status = "claimed").valid())
        assertFalse(packet().copy(id = "-".repeat(36)).valid())
    }
    @Test fun contextPreservesAmountAndStateButDoesNotTurnWishIntoSystemInstruction() {
        val claimed = packet().copy(status = "claimed", claimedAt = 2000)
        assertEquals(claimed, PrivateRedPacket.parse(claimed.encoded()))
        val context = claimed.contextText(true)
        assertTrue(context.contains("66龙门币"))
        assertTrue(context.contains("状态：已领取"))
        assertTrue(context.contains("不代表亲密度"))
        assertTrue(context.contains("请大家喝点热饮"))
        assertFalse(context.contains("【系统说明】"))
        assertTrue(packet().contextText().contains("尚未领取"))
    }
    @Test fun committedClaimRemainsRecoverableUntilProjectionAcknowledged() {
        val store = repository()
        assertTrue(store.reservePrivateRedPacket(packet()))
        val claimed = store.settlePrivateRedPacket(packet().id, true, 2000)!!
        assertEquals(listOf(claimed.id), store.pendingPrivateRedPackets())
        assertEquals(claimed, store.privateRedPacket(claimed.id))
        assertEquals(34, store.lmb)
        assertTrue(store.acknowledgePrivateRedPacket(claimed.id))
        assertTrue(store.acknowledgePrivateRedPacket(claimed.id))
        assertEquals(34, store.lmb)
    }
    @Test fun concurrentDuplicateSendsDebitOnlyOnce() {
        val store = repository()
        val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
        try {
            val results = (1..30).map { pool.submit<Boolean> { store.reservePrivateRedPacket(packet()) } }
            assertTrue(results.all { it.get(5, java.util.concurrent.TimeUnit.SECONDS) })
            assertEquals(34, store.lmb)
            assertEquals(listOf(packet().id), store.pendingPrivateRedPackets())
        } finally { pool.shutdownNow() }
    }
    @Test fun competingClaimAndRefundHaveOnlyOneTerminalOutcome() {
        val store = repository()
        assertTrue(store.reservePrivateRedPacket(packet()))
        val gate = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val claim = pool.submit<PrivateRedPacket?> { gate.await(); store.settlePrivateRedPacket(packet().id, true, 2000) }
            val refund = pool.submit<PrivateRedPacket?> { gate.await(); store.settlePrivateRedPacket(packet().id, false, 2000) }
            gate.countDown()
            val claimed = claim.get(5, java.util.concurrent.TimeUnit.SECONDS)
            val refunded = refund.get(5, java.util.concurrent.TimeUnit.SECONDS)
            assertTrue((claimed == null) != (refunded == null))
            assertEquals(if (claimed != null) 34 else 100, store.lmb)
            assertEquals(if (claimed != null) "claimed" else "refunded", store.privateRedPacket(packet().id)?.status)
        } finally { pool.shutdownNow() }
    }

}
