package com.rhodes.privatechat.ui.startup

import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.rhodes.privatechat.ui.settings.StartupSettingsScreen
import com.rhodes.privatechat.ui.theme.罗德岛终端Theme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

/** Inject platform touch events directly; avoids Espresso's removed InputManager API on API 36. */
class StartupInteractionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var downAt = 0L
    private fun touch(action: Int, x: Float, y: Float) {
        if (action == MotionEvent.ACTION_DOWN) downAt = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
    }
    private fun capture(name: String) {
        val context = instrumentation.targetContext
        val file = File(context.getExternalFilesDir(null), "$name.png")
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        assertNotNull(bitmap)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) findText(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun clickText(text: String) {
        instrumentation.waitForIdleSync()
        var node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        val deadline = SystemClock.uptimeMillis() + 2500
        while (node == null && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(100)
            node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        }
        assertNotNull("Missing control: $text", node)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull("Not clickable: $text", node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(180)
    }
    private fun withOpening(test: (StartupTouchRegion, AtomicInteger) -> Unit) {
        val finishes = AtomicInteger()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            var region = startupTouchRegion(1080f, 2400f)
            scenario.onActivity { activity -> activity.setContent { TerminalStartup(false) { finishes.incrementAndGet() } } }
            SystemClock.sleep(600)
            scenario.onActivity { activity ->
                val content = activity.findViewById<View>(android.R.id.content)
                val location = IntArray(2); content.getLocationOnScreen(location)
                val local = startupTouchRegion(content.width.toFloat(), content.height.toFloat())
                region = local.copy(centerX = local.centerX + location[0], centerY = local.centerY + location[1])
            }
            test(region, finishes)
        }
    }
    @Test fun offCenterLongPressReleasesAndFinishesOnce() = withOpening { r, finishes ->
        capture("startup-idle")
        val x = r.centerX + r.radius * .65f
        touch(MotionEvent.ACTION_DOWN, x, r.centerY)
        SystemClock.sleep(1600)
        assertEquals(0, finishes.get())
        capture("startup-held-off-center")
        touch(MotionEvent.ACTION_UP, x, r.centerY)
        SystemClock.sleep(700)
        capture("startup-assembling")
        SystemClock.sleep(1400)
        capture("startup-crystal")
        SystemClock.sleep(1600)
        assertEquals(1, finishes.get())
    }
    @Test fun slideOutAndReturnCancelsButNextTapWorks() = withOpening { r, finishes ->
        touch(MotionEvent.ACTION_DOWN, r.centerX, r.centerY)
        touch(MotionEvent.ACTION_MOVE, 10f, r.centerY)
        touch(MotionEvent.ACTION_MOVE, r.centerX, r.centerY)
        touch(MotionEvent.ACTION_UP, r.centerX, r.centerY)
        SystemClock.sleep(3600)
        assertEquals(0, finishes.get())
        touch(MotionEvent.ACTION_DOWN, r.centerX, r.centerY)
        touch(MotionEvent.ACTION_UP, r.centerX, r.centerY)
        SystemClock.sleep(3600)
        assertEquals(1, finishes.get())
    }
    @Test fun outsideDoesNotStartAndPlatformCancellationDoesNotTrigger() = withOpening { r, finishes ->
        touch(MotionEvent.ACTION_DOWN, 10f, r.centerY)
        touch(MotionEvent.ACTION_UP, 10f, r.centerY)
        touch(MotionEvent.ACTION_DOWN, r.centerX, r.centerY)
        touch(MotionEvent.ACTION_CANCEL, r.centerX, r.centerY)
        SystemClock.sleep(3600)
        assertEquals(0, finishes.get())
    }
    @Test fun skipStillWorksWithParentGestureHandler() = withOpening { _, finishes ->
        clickText("跳过")
        assertEquals(1, finishes.get())
    }
    @Test fun settingModesPersistAndPreviewDoesNotConsumeDate() {
        val context = instrumentation.targetContext
        val raw = context.getSharedPreferences("rhodes_startup", android.content.Context.MODE_PRIVATE)
        val original = raw.all.toMap()
        try {
            raw.edit().clear().putBoolean("enabled", true).commit()
            val prefs = StartupPreferences(context)
            assertEquals(StartupMode.DAILY, prefs.mode)
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { 罗德岛终端Theme { StartupSettingsScreen {} } } }
                SystemClock.sleep(600)
                capture("startup-settings")
                clickText("每次启动时显示")
                assertEquals(StartupMode.ALWAYS, StartupPreferences(context).mode)
                assertTrue(prefs.claim(false, false))
                assertTrue(prefs.claim(false, false))
                assertFalse(raw.contains("last_date"))
                clickText("不显示开屏")
                assertEquals(StartupMode.NEVER, StartupPreferences(context).mode)
                assertFalse(prefs.claim(false, false))
                clickText("每日首次显示")
                assertEquals(StartupMode.DAILY, StartupPreferences(context).mode)
                clickText("重播启动演出")
                assertFalse(raw.contains("last_date"))
                clickText("跳过")
                assertTrue(prefs.claim(false, false))
                assertFalse(prefs.claim(false, false))
            }
        } finally {
            raw.edit().clear().apply {
                original.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                } }
            }.commit()
        }
    }

}
