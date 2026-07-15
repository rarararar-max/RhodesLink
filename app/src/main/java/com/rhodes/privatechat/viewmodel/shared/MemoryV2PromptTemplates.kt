package com.rhodes.privatechat.viewmodel.shared

object MemoryV2PromptTemplates {
    fun getL1(sourceKind: String): String = when (sourceKind) {
        "PRIVATE_CHAT" -> PRIVATE_L1
        "GROUP_CHAT" -> GROUP_L1
        "MOMENT", "MOMENT_COMMENT", "WORLD_EVENT", "COMMENT" -> EVENT_L1
        "DIARY" -> DIARY_L1
        else -> PRIVATE_L1
    }

    val L2 = """
你是一个记忆合并器。你会收到若干条 L1 记忆碎片 JSON。

任务：把同一主题、同一事件、同一对象的碎片合并成中期记忆。只保留未来对互动有用的事实、约定、情绪状态、偏好、提醒。

输出要求：
- 只输出纯 JSON 数组，不要 Markdown，不要解释。
- 如果没有可合并内容，输出 []。
- 每个对象必须符合统一字段结构。
- content 必须包含时间范围或来源线索，例如“最近几次”“6月下旬”“昨天到今天”。
- 不要写画像式结论，不要把一次玩笑概括成稳定人格。
- 每个输出项必须提供 evidence_ids，列出实际支持这条结论的输入记忆 id；不得编造 id。

$SCHEMA
""".trimIndent()

    val L3 = """
你是一个长期记忆聚合器。你会收到多条 L2 中期记忆 JSON。

任务：将稳定、反复出现、长期有用的信息整理为长期记忆。只保留对后续互动有持续影响的内容。

输出要求：
- 只输出纯 JSON 数组，不要 Markdown，不要解释。
- 如果没有足够稳定的信息，输出 []。
- 每个对象必须符合统一字段结构。
- content 必须包含大时间跨度或稳定性来源，例如“长期以来”“最近多次”“一段时间内”。
- 不要编造用户画像，不要输出 relationship_statement。
- 每个输出项必须提供 evidence_ids，列出实际支持这条结论的输入记忆 id；不得编造 id。

$SCHEMA
""".trimIndent()

    private val PRIVATE_L1 = """
你是一个私聊记忆提取器。你会收到一段用户与角色的对话，每条消息带有时间戳。

任务：提取将来对这个角色与用户继续互动有用的记忆碎片。

提取边界：
- 提取用户明确表达的偏好、禁忌、计划、承诺、情绪、需求、事实、自我描述。
- 可以提取角色与用户之间发生的具体事件或约定。
- 寒暄、短测试、乱码、纯数字、单字回复、格式错误内容不要提取。
- 不要把角色人设、角色职业、角色习惯提取成用户偏好。
- 不要从一次玩笑、反话或暧昧语气推断长期人格。
- 如果新内容与旧印象冲突，以新内容为准描述事实，不要并列保留矛盾。

输出要求：
- 只输出纯 JSON 数组，不要 Markdown，不要解释。
- 没有值得记住的信息时输出 []。
- 每个对象必须符合统一字段结构。

$SCHEMA
""".trimIndent()

    private val GROUP_L1 = """
你是一个群聊记忆提取器。你会收到一段群聊内容，每条消息带有时间戳。

任务：提取公开群聊里将来可复用的事实、事件、约定、评价、状态变化和公开偏好。

提取边界：
- 只提取群成员公开说出的内容或群聊中发生的公开事件。
- 不要提取旁白里的夸张气氛为事实。
- 不要把普通接梗、附和、玩笑当成稳定关系或稳定偏好。
- 不要输出私聊隐私推断。
- 无价值闲聊输出 []。

输出要求：
- 只输出纯 JSON 数组，不要 Markdown，不要解释。
- 每个对象必须符合统一字段结构。

$SCHEMA
""".trimIndent()

    private val EVENT_L1 = """
你是一个事件记忆提取器。你会收到一段动态、评论或世界事件文本。

任务：只提取可复用的公开事件事实。

提取边界：
- 只允许 type = "event"。
- 不要编造事件之外的动机、关系和长期偏好。
- 如果文本只是普通寒暄或无法形成事实，输出 []。

输出要求：
- 只输出纯 JSON 数组，不要 Markdown，不要解释。
- 每个对象必须符合统一字段结构。

$SCHEMA
""".trimIndent()

    private val DIARY_L1 = """
你是一个日记记忆提取器。你会收到一段角色日记。

任务：提取日记中可用于后续世界连续性的事实、状态、事件、约定、提醒。

提取边界：
- 日记是角色主观视角；不确定信息要保留“不确定/听说/感觉”的语气。
- 不要把抒情句提取成事实。
- 不要从一次感受推断长期画像。
- 无明确事实时输出 []。

输出要求：
- 只输出纯 JSON 数组，不要 Markdown，不要解释。
- 每个对象必须符合统一字段结构。

$SCHEMA
""".trimIndent()

    private const val SCHEMA = """
统一字段结构：
[
  {
    "type": "emotion_state | behavior_state | physiological_state | event | agreement_commitment | intent_wish | preference_expression | evaluation_opinion | self_cognition_statement | external_knowledge | care_reminder",
    "content": "一句完整、具体、可复用的记忆，12到80字，不含系统词",
    "nickname": "用户昵称；不确定则填系统提供的当前昵称",
    "importance": 0,
    "privacy": "public | private | shared",
    "unmet_need": false,
    "location": "地点或空字符串",
    "emotion_valence": "positive | neutral | negative | mixed",
    "event_time": "原始事件时间或空字符串",
    "scheduled_time": "未来约定/提醒时间或空字符串",
    "action": "需要后续执行的动作或空字符串",
    "care_type": "comfort | remind | celebrate | accompany | none"
  }
]

字段规则：
- type 必须从上面枚举选择，不得输出 relationship_statement。
- importance 必须是 0 到 100 的整数：重大禁忌/承诺/安全需求 80-100，明确偏好/计划 50-79，普通事件 20-49，弱信息 1-19。
- privacy：公开群聊/动态事实用 public；私聊、负面情绪、个人隐私、亲密内容用 private；可被关系网自然听说但不敏感的信息用 shared。
- unmet_need 只在用户明确需要安慰、陪伴、提醒、帮助且尚未满足时为 true。
- content 禁止出现“系统记录”“记忆”“锚点”“摘要”“好感度”“affection”等机制词。
- content 必须具体到谁、什么事、什么偏好或什么约定；不要写“聊得很开心”这种空泛句。
- JSON 字段类型必须正确：importance 是数字，unmet_need 是布尔值，其他字段是字符串。
- L2/L3 合并时额外输出 evidence_ids：整数数组，必须只引用本次输入 JSON 中的 id。L1 提取时不要输出该字段。
"""
}
