package com.rhodes.privatechat

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.rhodes.privatechat.ui.theme.*
import com.rhodes.privatechat.ui.common.*
import com.rhodes.privatechat.ui.settings.*
import com.rhodes.privatechat.ui.contacts.ContactsScreen
import com.rhodes.privatechat.ui.sessions.SessionListScreen
import com.rhodes.privatechat.ui.moments.MomentsScreen
import com.rhodes.privatechat.ui.diary.DiaryScreen
import com.rhodes.privatechat.ui.chat.component.*
import com.rhodes.privatechat.ui.chat.util.MessageParser
import com.rhodes.privatechat.shared.model.ChatMessage
import com.rhodes.privatechat.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File
import kotlinx.coroutines.runBlocking
import com.rhodes.privatechat.shared.model.*
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.rhodes.privatechat.shared.db.RhodesDatabase

class LiquidGlassVisualTest {
    @Test fun textContrastSurvivesBrightBackground() {
        val prior = currentAppearanceSkin
        try {
            applyLiquidGlassTheme()
            val surfaces = listOf(Surface, Card, ElevatedSurface, InputBg, BubbleMine, BubbleMineEnd, BubbleOther, HeaderStart, HeaderEnd)
            for (surface in surfaces) {
                val background = ColorUtils.compositeColors(surface.toArgb(), android.graphics.Color.WHITE)
                val backing = ColorUtils.compositeColors(0x10E9F6FF, ColorUtils.compositeColors(0x0AFFFFFF, background))
                for (text in listOf(TextPrimary, TextSecondary, TextTertiary)) {
                    assertTrue("Unreadable palette contrast on $surface", ColorUtils.calculateContrast(text.toArgb(), backing) >= 4.5)
                }
            }
            assertTrue(ColorUtils.calculateContrast(OnPrimary.toArgb(), Primary.toArgb()) >= 4.5)
        } finally {
            when(prior) {
                AppearanceSkin.ClassicLight -> applyLightTheme()
                AppearanceSkin.ClassicDark -> applyDarkTheme()
                AppearanceSkin.RhodesDay -> applyRhodesDayTheme()
                AppearanceSkin.RhodesNight -> applyRhodesNightTheme()
                AppearanceSkin.FrostedGlass -> applyFrostedGlassTheme()
                AppearanceSkin.LiquidGlass -> applyLiquidGlassTheme()
            }
        }
    }
    @Test fun inspectMajorScreensAndInput() {
        val inst = InstrumentationRegistry.getInstrumentation()
        val prior = currentAppearanceSkin
        val koin = GlobalContext.get()
        val vm = MainViewModel(inst.targetContext.applicationContext as android.app.Application,
            koin.get(), koin.get(), koin.get(), koin.get(), koin.get(), startBackgroundWork = false)
        var page by mutableIntStateOf(0)
        var chatInput by mutableStateOf("")
        var fixtureMoment = 0L
        var fixtureDiary = 0L
        runBlocking {
            val op = vm.repository.getAllOperatorsSync().firstOrNull { it.name == "凯尔希" }
                ?: vm.repository.getAllOperatorsSync().first()
            val now = System.currentTimeMillis()
            fixtureMoment = vm.repository.insertMoment(Moment(operatorId=op.id, operatorName=op.name,
                content="医疗部今天收到了一大箱橙子。阿米娅说是后勤部送来的，结果清点时少了两个。\n如果某位博士的桌上恰好也有橙子，请记得先洗手。", createdAt=now))
            vm.repository.insertComment(MomentComment(momentId=fixtureMoment, operatorId="user", operatorName="博士",
                content="只拿了一个，另一个可能在可露希尔那里。", createdAt=now+1))
            fixtureDiary = vm.repository.insertDiary(Diary(operatorId=op.id, operatorName=op.name,
                content="今晚的走廊比往常安静。阿米娅把整理好的报告放在门口，留了张字条，提醒我记得吃晚饭。\n\n博士来得比预计早，手里却拿着一份被咖啡洇湿的申请表。我没有追问，只把桌上的茶推了过去。\n\n罗德岛依然有很多事要做。不过，至少今晚可以让灯早一点熄灭。", date="2026-11-07", createdAt=now)).id
        }

        try {
            ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
                scenario.onActivity { a -> a.setContent {
                    currentAppearanceSkin = AppearanceSkin.LiquidGlass
                    罗德岛终端Theme {
                        when(page) {
                            0 -> SettingsScreen()
                            1 -> ContactsScreen(vm, {})
                            2 -> MomentsScreen(vm, {})
                            3 -> DiaryScreen(vm, {})
                            4 -> AppearanceSettingsScreen({})
                            5 -> AppBackground {
                                Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
                                    GradientHeader("凯尔希", onBack = {})
                                    val messages = remember { MessageParser.parse(listOf(
                                        ChatMessage(id=1, sessionId="glass-preview", senderName="博士", content="今天的报告已经交给阿米娅了。", isMe=true),
                                        ChatMessage(id=2, sessionId="glass-preview", senderName="凯尔希", content="她放下手中的病历，抬眼看了看你。医疗部的灯光映在杯沿，走廊里传来干员们交班的脚步声。", isMe=false),
                                        ChatMessage(id=3, sessionId="glass-preview", senderName="凯尔希", content="那就坐一会儿。阿米娅刚送来的茶还热着，今晚暂时不需要你加班。", isMe=false)
                                    ), false, aiName="凯尔希") }
                                    MessageList("glass-preview", messages, rememberLazyListState(), { _,_ -> }, displayEventsLoaded=true, modifier=Modifier.weight(1f))
                                    ChatInputBar(chatInput, {chatInput=it}, {})
                                }
                            }
                            else -> SessionListScreen(vm, {})
                        }
                    }
                } }
                fun findNode(node: AccessibilityNodeInfo?, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
                    if(node == null) return null
                    if(predicate(node)) return node
                    for(n in 0 until node.childCount) findNode(node.getChild(n), predicate)?.let { return it }
                    return null
                }
                for (i in 0..6) {
                    inst.runOnMainSync { page = i }
                    SystemClock.sleep(if(i == 0) 4500 else 1500)
                    if (i == 3) {
                        val field = findNode(inst.uiAutomation.rootInActiveWindow) { it.isEditable }
                        val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "凯尔希") }
                        assertTrue(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args))
                        SystemClock.sleep(600)
                        var target = findNode(inst.uiAutomation.rootInActiveWindow) { it.text?.toString() == "凯尔希" && !it.isEditable }
                        while(target != null && !target.isClickable) target = target.parent
                        assertNotNull(target)
                        assertTrue(target!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                        SystemClock.sleep(900)
                    }
                    val image = inst.uiAutomation.takeScreenshot()
                    assertNotNull("Screenshot page $i", image)
                    File(inst.targetContext.getExternalFilesDir(null), "glass-page-$i.png").outputStream().use {
                        image!!.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    image!!.recycle()
                    if (i == 5) {
                        fun editable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                            if (node == null) return null
                            if (node.isEditable) return node
                            for (n in 0 until node.childCount) editable(node.getChild(n))?.let { return it }
                            return null
                        }
                        val field = editable(inst.uiAutomation.rootInActiveWindow)
                        assertNotNull("Chat input missing", field)
                        inst.runOnMainSync { chatInput = "那就一起喝杯茶吧。" }
                        SystemClock.sleep(300)
                        editable(inst.uiAutomation.rootInActiveWindow)!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        SystemClock.sleep(1000)
                        val keyboard = inst.uiAutomation.takeScreenshot()!!
                        File(inst.targetContext.getExternalFilesDir(null), "glass-keyboard.png").outputStream().use { keyboard.compress(Bitmap.CompressFormat.PNG,100,it) }
                        keyboard.recycle()
                    }

                }
                assertEquals(AppearanceSkin.LiquidGlass, AppearanceSkin.fromStorage("liquid_glass", false))
            }
        } finally {
            AndroidSqliteDriver(RhodesDatabase.Schema, inst.targetContext, "rhodes_terminal.db").use { driver ->
                driver.execute(null, "DELETE FROM moment_comments WHERE momentId = $fixtureMoment", 0)
                driver.execute(null, "DELETE FROM moments WHERE id = $fixtureMoment", 0)
                driver.execute(null, "DELETE FROM diaries WHERE id = $fixtureDiary", 0)
            }
            inst.runOnMainSync { currentAppearanceSkin = prior }
        }
    }
}
