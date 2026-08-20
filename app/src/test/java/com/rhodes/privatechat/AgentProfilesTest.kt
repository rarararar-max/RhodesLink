package com.rhodes.privatechat

import com.rhodes.privatechat.shared.settings.AgentProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProfilesTest {
    @Test
    fun supportAgentsHaveDistinctFixedRoutines() {
        assertEquals("商业街", AgentProfiles.routineAt("nuan", 18, 2).location)
        assertEquals("资料室", AgentProfiles.routineAt("yu", 10, 2).location)
        assertEquals("健身房", AgentProfiles.routineAt("chuan", 6, 2).location)
        assertEquals("房间", AgentProfiles.routineAt("tuan", 2, 2).location)
    }

    @Test
    fun weekendRoutineCanDifferFromWeekdayRoutine() {
        val weekday = AgentProfiles.routineAt("tuan", 19, 2)
        val weekend = AgentProfiles.routineAt("tuan", 19, 7)

        assertTrue(weekday.location != weekend.location)
    }
}
