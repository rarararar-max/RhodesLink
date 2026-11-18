package com.rhodes.privatechat

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rhodes.privatechat.ui.chat.component.*
import com.rhodes.privatechat.ui.chat.model.ChatUiMessage
import com.rhodes.privatechat.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ContinueMenuUiTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private fun find(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if(node == null) return null
        if(node.text?.toString() == text) return node
        for(i in 0 until node.childCount) find(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun await(text: String): AccessibilityNodeInfo {
        repeat(100) { find(inst.uiAutomation.rootInActiveWindow,text)?.let { return it }; SystemClock.sleep(100) }
        error("Missing $text")
    }
    private fun action(text: String, action: Int) {
        var node: AccessibilityNodeInfo? = await(text)
        while(node != null && if(action == AccessibilityNodeInfo.ACTION_LONG_CLICK) !node.isLongClickable else !node.isClickable) node=node.parent
        assertNotNull(node); assertTrue(node!!.performAction(action))
    }
    @Test fun continueClosesMenuKeepsHistoryAndHeaderPosition() {
        val prior = currentAppearanceSkin
        var loading by mutableStateOf(false)
        val calls=AtomicInteger()
        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { a -> a.setContent {
                    currentAppearanceSkin=AppearanceSkin.LiquidGlass
                    罗德岛终端Theme {
                        Column(Modifier.fillMaxSize()) {
                            ChatHeader("凯尔希", isLoading=loading, subtitleText="医疗部 · 平静", onBack={})
                            MessageList("continue-test", listOf(ChatUiMessage(1,"凯尔希",Color.Cyan,"先坐一会儿，茶还热着。",1)),
                                rememberLazyListState(), {_,_->}, onContinue={calls.incrementAndGet();loading=true},
                                displayEventsLoaded=true, modifier=Modifier.weight(1f))
                        }
                    }
                } }
                val before=Rect();await("凯尔希").getBoundsInScreen(before)
                await("先坐一会儿，茶还热着。")
                action("先坐一会儿，茶还热着。",AccessibilityNodeInfo.ACTION_LONG_CLICK)
                action("继续说",AccessibilityNodeInfo.ACTION_CLICK)
                repeat(30) { if(calls.get()==0) SystemClock.sleep(100) }
                assertEquals(1,calls.get())
                await("先坐一会儿，茶还热着。")
                SystemClock.sleep(300)
                assertNull(find(inst.uiAutomation.rootInActiveWindow,"继续说"))
                val after=Rect();await("凯尔希").getBoundsInScreen(after)
                assertEquals(before,after)
            }
        } finally { inst.runOnMainSync { currentAppearanceSkin=prior } }
    }
}
