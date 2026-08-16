package com.rhodes.privatechat.galgame_full

import com.rhodes.privatechat.shared.model.AiMessage
import com.rhodes.privatechat.viewmodel.shared.SharedUtils

object GalgameHostBridge {
    @Volatile
    var sharedUtils: SharedUtils? = null

    suspend fun chat(messages: List<AiMessage>, maxOutputTokens: Int, temperature: Double, logTag: String): String {
        val utils = sharedUtils ?: error("Galgame AI 宿主尚未初始化")
        return utils.chat(messages, logTag, maxOutputTokens, temperature)
    }
}
