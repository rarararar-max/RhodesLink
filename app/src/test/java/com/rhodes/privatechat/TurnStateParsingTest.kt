package com.rhodes.privatechat

import com.rhodes.privatechat.shared.model.PrivateTurnState
import org.junit.Assert.assertEquals
import org.junit.Test

class TurnStateModelTest {
    @Test
    fun privateTurnStateKeepsCompactContinuityFields() {
        val state = PrivateTurnState(
            currentTopic = "讨论晚餐选择。",
            userTurnType = "确认",
            currentAnchor = "用户同意第二个方案。",
            turnAdvance = "角色说明会按第二个方案安排。",
            threadStatus = "等待用户",
            unresolvedThread = "用户是否现在出发。"
        )

        assertEquals("讨论晚餐选择。", state.currentTopic)
        assertEquals("确认", state.userTurnType)
        assertEquals("用户同意第二个方案。", state.currentAnchor)
        assertEquals("角色说明会按第二个方案安排。", state.turnAdvance)
        assertEquals("等待用户", state.threadStatus)
        assertEquals("用户是否现在出发。", state.unresolvedThread)
    }
}
