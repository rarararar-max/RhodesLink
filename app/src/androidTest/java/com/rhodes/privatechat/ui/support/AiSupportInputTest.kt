package com.rhodes.privatechat.ui.support

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AiSupportInputTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun busyInputKeepsDraftAndDoesNotSubmit() {
        var submitted = 0
        val draft = mutableStateOf("第二个问题")

        composeRule.setContent {
            AiSupportInput(input = draft.value, busy = true, onInputChange = { draft.value = it }) { submitted++ }
        }

        composeRule.onNodeWithText("第二个问题").assertTextEquals("第二个问题")
        assertEquals(0, submitted)
    }
}
