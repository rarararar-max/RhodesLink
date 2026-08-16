package com.rhodes.privatechat.viewmodel

/** Defaults for the user-editable prompt modules. Empty custom values remain intentionally empty. */
object PromptModuleDefaults {
    fun outputProtocol(type: String, mode: String): String = when (type) {
        "private" -> """【状态】角色当前的动作或互动状态
【心情】角色当前的主要情绪
【位置】角色当前所在地点或线上状态
【本轮简述】当前主线、已发生的进展和未完成事项
${if (mode == "online") "【台词】角色实际发送给用户的文字" else "【旁白】第三人称可见的动作或场景\n【台词】角色实际说出口的文字"}"""
        "group" -> """【本轮剧情简述】本轮已经明确发生的主线和未结束事项（可选）
【发言人: operator_id】
角色实际发言
${if (mode == "online") "" else "【旁白】第三人称共享场景描写（可选）"}"""
        else -> ""
    }

    fun runtime(type: String, mode: String): String = when (type) {
        "private" -> """【本轮背景资料】

【当前时间】
{{CURRENT_TIME}}

【用户资料】
姓名：{{USER_NAME}}
性别：{{USER_GENDER}}
身份设定：{{USER_BIO}}

【上一轮互动状态】
{{PRIVATE_CONTINUITY_STATE}}

【最近聊天进展】
{{SHORT_TERM_SUMMARY}}

【相关记忆】
{{MEMORY_V2_CONTEXT}}

【群聊背景】
{{GROUP_CONTEXT}}
"""
        "group" -> """【本轮背景资料】

【当前时间】
{{CURRENT_TIME}}

【当前群聊主线】
{{GROUP_PLOT_SUMMARY}}

【当前成员与发言标识】
{{MEMBER_PROFILES}}

【最近群聊进展】
{{SHORT_TERM_SUMMARY}}

【关系背景】
{{RELATION_HINTS}}

【相关记忆】
{{MEMORY_V2_CONTEXT}}

【近期公开动态】
{{RECENT_SOCIAL_CONTEXT}}
"""
        else -> ""
    }
}
