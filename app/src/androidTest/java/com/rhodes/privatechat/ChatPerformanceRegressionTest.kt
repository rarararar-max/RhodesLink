package com.rhodes.privatechat

import android.os.SystemClock
import android.view.Choreographer
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.ui.chat.component.MessageList
import com.rhodes.privatechat.ui.chat.util.MessageParser
import com.rhodes.privatechat.ui.theme.罗德岛终端Theme
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ChatPerformanceRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun reply(id: Long, segments: Int, recalled: Int = -1): ChatMessage {
        val rows = (0 until segments).joinToString(",") { i ->
            """{"type":"dialogue","content":"第 $i 段：医疗部的灯还亮着，阿米娅刚把今晚的报告送过来。" ,"recalled":${i == recalled}}"""
        }
        return ChatMessage(id = id, sessionId = "performance_fixture", senderName = "凯尔希",
            content = """{"emotion":"平静","segments":[$rows]}""", type = "ai_json", mode = "offline", isMe = false)
    }
    @Test fun manySegmentsPreserveRecallAndIndices() {
        val started = SystemClock.elapsedRealtimeNanos()
        val parsed = MessageParser.parse(listOf(reply(77, 120, 62)), false, aiName = "凯尔希")
        assertEquals(119, parsed.size)
        assertFalse(parsed.any { it.segmentIndex == 62 })
        assertEquals(119, parsed.last().segmentIndex)
        assertTrue(parsed.all { it.originalMessageId == 77L && it.senderName == "凯尔希" })
        android.util.Log.i("RhodesPerformance", "parse_120_segments_ms=${(SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0}")
    }
    @Test fun historyWindowIsBoundedAndHandlesEqualTimestamps() {
        val context = instrumentation.targetContext
        val driver = app.cash.sqldelight.driver.android.AndroidSqliteDriver(com.rhodes.privatechat.shared.db.RhodesDatabase.Schema, context, null)
        try {
            val db = com.rhodes.privatechat.shared.db.RhodesDatabase(driver)
            db.transaction {
                (1L..1000L).forEach { id ->
                    db.chatMessagesQueries.insertMessage(id, "window", "", "凯尔希", "reply", "text", "offline", "", "", "", "", "", 0L, 1000L + id / 3, 0L)
                }
                db.chatMessagesQueries.insertMessage(1001L, "other", "", "凯尔希", "other", "text", "offline", "", "", "", "", "", 0L, 1166L, 0L)
            }
            val target = db.chatMessagesQueries.getMessageById(500L).executeAsOne()
            val before = db.chatMessagesQueries.getMessagesBefore("window", target.timestamp, target.timestamp, 500L, 25L).executeAsList()
            val after = db.chatMessagesQueries.getMessagesAfterPosition("window", target.timestamp, target.timestamp, 500L, 25L).executeAsList()
            assertEquals((475L..499L).toList(), before.map { it.id }.reversed())
            assertEquals((501L..525L).toList(), after.map { it.id })
            assertEquals(51, before.size + after.size + 1)
        } finally { driver.close() }
    }

    @Test fun keyboardRemainsUsableDuringProgressiveLongReply() {
        val old = MessageParser.parse((1L..30L).map { reply(it, 12) }, false, aiName = "凯尔希")
        val now = System.currentTimeMillis()
        val fresh = MessageParser.parse(listOf(reply(31, 18).copy(timestamp = now)), false, aiName = "凯尔希")
        val reveals = AtomicInteger()
        val typed = java.util.concurrent.atomic.AtomicReference("")
        val imeWasVisible = java.util.concurrent.atomic.AtomicBoolean(false)
        val intervals = mutableListOf<Long>()
        var lastFrame = 0L
        var running = true
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(time: Long) {
                if (lastFrame != 0L) intervals += (time - lastFrame) / 1_000_000
                lastFrame = time
                if (running) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.enableEdgeToEdge()
                activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                activity.setContent {
                    罗德岛终端Theme {
                        val focus = remember { FocusRequester() }
                        val keyboard = LocalSoftwareKeyboardController.current
                        val imeHeight = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current)
                        SideEffect { if (imeHeight > 0) imeWasVisible.set(true) }
                        var text by remember { mutableStateOf("") }
                        Column(Modifier.fillMaxSize().imePadding()) {
                            MessageList("performance_fixture", old + fresh, rememberLazyListState(), { _, _ -> },
                                progressiveDisplay = true, displayEventsLoaded = true, legacyMessageCutoff = now,
                                onReveal = { _, at -> reveals.incrementAndGet(); at }, modifier = Modifier.weight(1f))
                            BasicTextField(text, { text = it; typed.set(it) }, Modifier.fillMaxWidth().focusRequester(focus)
                                .semantics { contentDescription = "performance-input" })
                        }
                        LaunchedEffect(Unit) { delay(1500); focus.requestFocus(); delay(150); keyboard?.show() }
                    }
                }
            }
            SystemClock.sleep(1200)
            scenario.onActivity { Choreographer.getInstance().postFrameCallback(callback) }
            SystemClock.sleep(4500)
            val automation = instrumentation.uiAutomation
            val screenshot = automation.takeScreenshot()!!
            val keyX = screenshot.width * .05f
            val keyY = screenshot.height * .715f
            screenshot.recycle()
            repeat(3) {
                val down = SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, keyX, keyY, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try { automation.injectInputEvent(event, true) } finally { event.recycle() }
                }
                SystemClock.sleep(100)
            }
            SystemClock.sleep(300)
            scenario.onActivity { running = false; Choreographer.getInstance().removeFrameCallback(callback) }
            val sorted = intervals.sorted()
            val report = "frames=${sorted.size},p95_ms=${sorted.getOrNull((sorted.size * .95).toInt())},max_ms=${sorted.maxOrNull()},reveals=${reveals.get()}"
            android.util.Log.i("RhodesPerformance", report)
            val context = instrumentation.targetContext
            File(context.getExternalFilesDir(null), "chat-performance.txt").writeText(report)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.getExternalFilesDir(null), "chat-performance.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            assertTrue("Software keyboard resized the viewport", imeWasVisible.get())
            assertTrue("Keyboard input reaches the field during long reply", typed.get().isNotBlank())
            assertTrue("Progressive reveal must continue while keyboard opens", reveals.get() >= 2)

        }
    }
}
