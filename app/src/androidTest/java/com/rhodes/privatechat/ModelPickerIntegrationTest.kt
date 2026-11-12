package com.rhodes.privatechat

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rhodes.privatechat.ui.model.ModelPickerSheet
import com.rhodes.privatechat.ui.theme.罗德岛终端Theme
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** UiAutomation avoids Espresso's removed InputManager reflection on the Android 16 emulator. */
class ModelPickerIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) find(node.getChild(i), predicate)?.let { return it }
        return null
    }
    private fun awaitText(text: String): AccessibilityNodeInfo {
        repeat(120) {
            find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == text }?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("Expected visible text: $text")
    }
    private fun click(text: String) {
        var node: AccessibilityNodeInfo? = awaitText(text)
        while (node != null && !node.isClickable) node = node.parent
        assertNotNull(node)
        assertTrue(node!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(250)
    }
    private fun search(text: String) {
        val edit = find(instrumentation.uiAutomation.rootInActiveWindow) { it.isEditable }
        assertNotNull("Search field missing", edit)
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        assertTrue(edit!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
        SystemClock.sleep(250)
    }
    @Test fun searchableSheetSelectsExactIdAndCloses() {
        var shown by mutableStateOf(true)
        var selected by mutableStateOf("deepseek-chat")
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                罗德岛终端Theme {
                    Text("已选：$selected")
                    if (shown) ModelPickerSheet(
                        listOf("deepseek-chat", "deepseek-reasoner", "Qwen/Qwen3-235B-A22B-Instruct-2507"), selected,
                        onDismiss = { shown = false }, onSelect = { selected = it; shown = false }, onManual = { shown = false })
                }
            } }
            awaitText("选择模型")
            SystemClock.sleep(500)
            val image = instrumentation.uiAutomation.takeScreenshot()!!
            File(instrumentation.targetContext.getExternalFilesDir(null), "model-picker-review.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            image.recycle()
            search("Qwen")
            assertNull(find(instrumentation.uiAutomation.rootInActiveWindow) { it.text?.toString() == "deepseek-chat" })
            click("Qwen/Qwen3-235B-A22B-Instruct-2507")
            awaitText("已选：Qwen/Qwen3-235B-A22B-Instruct-2507")
            assertEquals("Qwen/Qwen3-235B-A22B-Instruct-2507", selected)
        }
    }
    @Test fun emptySearchCanReturnToManualEntryWithoutChangingSelection() {
        var shown by mutableStateOf(true)
        var manual = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                罗德岛终端Theme {
                    Text(if (manual) "已进入手动输入" else "模型设置")
                    if (shown) ModelPickerSheet(listOf("deepseek-chat"), "deepseek-chat",
                        onDismiss = { shown = false }, onSelect = {}, onManual = { manual = true; shown = false })
                }
            } }
            awaitText("选择模型")
            search("no-such-model")
            awaitText("没有匹配的模型，可改用手动输入。")
            click("手动输入模型名称")
            awaitText("已进入手动输入")
            assertTrue(manual)
            assertFalse(shown)
        }
    }
}
