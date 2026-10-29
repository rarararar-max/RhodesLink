package com.rhodes.privatechat

import com.rhodes.privatechat.ui.startup.*
import org.junit.Assert.*
import org.junit.Test

class StartupPolicyTest {
    @Test fun dailyPlaysFirstLaunchAndNextDayOnly() {
        assertTrue(shouldPlayStartup(StartupMode.DAILY, false, false, "", "2026-10-20"))
        assertFalse(shouldPlayStartup(StartupMode.DAILY, false, false, "2026-10-20", "2026-10-20"))
        assertTrue(shouldPlayStartup(StartupMode.DAILY, false, false, "2026-10-20", "2026-10-21"))
    }
    @Test fun alwaysIgnoresDailyRecordAndNeverAlwaysSkips() {
        assertTrue(shouldPlayStartup(StartupMode.ALWAYS, false, false, "2026-10-20", "2026-10-20"))
        assertFalse(shouldPlayStartup(StartupMode.NEVER, false, false, "", "2026-10-20"))
    }
    @Test fun restorationAndDirectEntryBypassEveryMode() {
        StartupMode.entries.forEach {
            assertFalse(shouldPlayStartup(it, true, false, "", "2026-10-20"))
            assertFalse(shouldPlayStartup(it, false, true, "", "2026-10-20"))
        }
    }
    @Test fun oldToggleMigratesWithoutOverwritingExplicitMode() {
        assertEquals(StartupMode.DAILY, resolveStartupMode(null, true))
        assertEquals(StartupMode.NEVER, resolveStartupMode(null, false))
        assertEquals(StartupMode.ALWAYS, resolveStartupMode("always", false))
        assertEquals(StartupMode.NEVER, resolveStartupMode("never", true))
    }
    @Test fun sensingRegionFollowsDrawingAcrossAspectRatios() {
        listOf(1080f to 2400f, 720f to 1280f, 2400f to 1080f).forEach { (w, h) ->
            val r = startupTouchRegion(w, h)
            assertTrue(r.contains(w * .5f, h * .41f))
            assertTrue(r.contains(r.centerX + r.radius * .85f, r.centerY))
            assertFalse(r.contains(r.centerX + r.radius, r.centerY + r.radius))
            assertFalse(r.contains(0f, 0f))
        }
    }
}
