package com.rhodes.privatechat

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rhodes.privatechat.shared.model.*
import com.rhodes.privatechat.shared.settings.*
import com.rhodes.privatechat.ui.chat.PrivateRedPacketDialog
import com.rhodes.privatechat.ui.chat.component.MessageList
import com.rhodes.privatechat.ui.chat.util.MessageParser
import com.rhodes.privatechat.ui.theme.*
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import java.io.File
import org.junit.Assert.*
import org.junit.Test

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class PrivateRedPacketIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun packet() = PrivateRedPacket("12345678-1234-1234-1234-123456789abc", "packet_fixture", "kaltsit", "凯尔希", 188,
        "夜班辛苦了，请医疗部的大家喝点热饮。", 100, 1000)
    private fun settings(name: String): SettingsRepository {
        val prefs = instrumentation.targetContext.getSharedPreferences(name, Context.MODE_PRIVATE)
        val delegate = SharedPreferencesSettings(prefs)
        return SettingsRepository(object : ObservableSettings by delegate, AtomicSettingsBatch {
            override fun commitSupportState(strings: Map<String, String>, ints: Map<String, Int>): Boolean =
                prefs.edit().also { edit -> strings.forEach { (k, v) -> edit.putString(k, v) }; ints.forEach { (k, v) -> edit.putInt(k, v) } }.commit()
        })
    }
    private fun findText(node: android.view.accessibility.AccessibilityNodeInfo?, text: String): android.view.accessibility.AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(text) == true) return node
        for (i in 0 until node.childCount) findText(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun visible(text: String) {
        instrumentation.waitForIdleSync()
        assertNotNull("Expected visible text: $text", findText(instrumentation.uiAutomation.rootInActiveWindow, text))
    }
    private fun click(text: String) {
        var node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("Expected clickable control: $text", node)
        assertTrue(node!!.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(150)
    }
    private fun capture(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun walletAndPendingClaimSurviveReopeningRealAndroidStore() {
        val name = "packet_test_${System.nanoTime()}"
        try {
            val first = settings(name)
            first.lmb = 1000
            assertTrue(first.reservePrivateRedPacket(packet()))
            val reopened = settings(name)
            assertEquals(812, reopened.lmb)
            assertEquals(listOf(packet().id), reopened.pendingPrivateRedPackets())
            val claimed = reopened.settlePrivateRedPacket(packet().id, true, 2000)!!
            val afterClaim = settings(name)
            assertEquals(claimed, afterClaim.privateRedPacket(packet().id))
            assertEquals(812, afterClaim.lmb)
            assertNull(afterClaim.settlePrivateRedPacket(packet().id, false, 3000))
            assertTrue(afterClaim.acknowledgePrivateRedPacket(packet().id))
            assertTrue(settings(name).pendingPrivateRedPackets()!!.isEmpty())
        } finally { instrumentation.targetContext.deleteSharedPreferences(name) }
    }

    @Test fun packetParserKeepsStructuredCardAndFailedReplyCanBeRetried() {
        val claimed = packet().copy(status = "claimed", claimedAt = 2000)
        val message = ChatMessage(100, "packet_fixture", senderName = "我", content = claimed.encoded(), type = "red_packet_reply_failed", isMe = true)
        val parsed = MessageParser.parse(listOf(message), false, aiName = "凯尔希").single()
        assertEquals(claimed, parsed.redPacket)
        assertTrue(parsed.isSendFailed)
        assertFalse(parsed.content.contains("recipientId"))
        assertEquals(100, parsed.originalMessageId)
    }

    @Test fun cardsAndComposerRenderWithoutDependingOnRealConversations() {
        val name = "packet_ui_test_${System.nanoTime()}"
        try {
            val testSettings = settings(name).apply { lmb = 1000 }
            var showDialog by mutableStateOf(false)
            val claimed = packet().copy(status = "claimed", claimedAt = 2000)
            val rows = listOf(
                ChatMessage(99, "packet_fixture", senderName = "凯尔希", content = "阿米娅还在整理今晚的报告。你也该去吃点东西了。", isMe = false),
                ChatMessage(100, "packet_fixture", senderName = "我", content = claimed.encoded(), type = "red_packet", isMe = true),
                ChatMessage(101, "packet_fixture", senderName = "凯尔希", content = "……我替值班的同事们收下。至于你，别拿这个当作今晚继续熬夜的理由。", isMe = false))
            val parsed = MessageParser.parse(rows, false, aiName = "凯尔希")
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent {
                    罗德岛终端Theme {
                        Column(Modifier.fillMaxSize().background(BG).padding(top = 38.dp)) {
                            Text("凯尔希 · 私聊", color = TextPrimary, modifier = Modifier.padding(16.dp))
                            MessageList("packet_fixture", parsed, rememberLazyListState(), { _, _ -> }, displayEventsLoaded = true, legacyMessageCutoff = Long.MAX_VALUE, modifier = Modifier.weight(1f))
                            Text("消息…                                      ＋", color = TextSecondary, modifier = Modifier.padding(20.dp))
                        }
                        if (showDialog) PrivateRedPacketDialog("凯尔希", testSettings, { showDialog = false }, { _, _ -> })
                    }
                } }
                SystemClock.sleep(900)
                visible("188 龙门币")
                visible("凯尔希领取了你的红包")
                capture("private-packet-card")
                instrumentation.runOnMainSync { showDialog = true }
                SystemClock.sleep(500)
                visible("给凯尔希发红包")
                click("188")
                visible("确认发送")
                capture("private-packet-dialog")
            }
        } finally { instrumentation.targetContext.deleteSharedPreferences(name) }
    }
}
