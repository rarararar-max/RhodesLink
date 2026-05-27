package com.example.rhodesterminal.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.rhodesterminal.data.ExportPayload
import com.example.rhodesterminal.data.ExportHelper
import com.example.rhodesterminal.data.MessageExport
import com.example.rhodesterminal.data.OperatorExport
import com.example.rhodesterminal.data.RelationshipExport
import com.example.rhodesterminal.data.SessionExport
import com.example.rhodesterminal.data.db.AppDatabase
import com.example.rhodesterminal.data.db.entity.ChatMessageEntity
import com.example.rhodesterminal.data.db.entity.ChatSessionEntity
import com.example.rhodesterminal.data.db.entity.DispatchRecordEntity
import com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity
import com.example.rhodesterminal.data.db.entity.MemoryEntity
import com.example.rhodesterminal.data.db.entity.MemoryType
import com.example.rhodesterminal.data.db.entity.DiaryEntity
import com.example.rhodesterminal.data.db.entity.MomentCommentEntity
import com.example.rhodesterminal.data.db.entity.MomentEntity
import com.example.rhodesterminal.data.db.entity.MomentLikeEntity
import com.example.rhodesterminal.data.db.entity.OperatorEntity
import com.example.rhodesterminal.data.db.dao.SenderCount
import com.example.rhodesterminal.data.repository.BfsNode
import com.example.rhodesterminal.data.repository.ChatRepository
import com.example.rhodesterminal.network.AIClient
import com.example.rhodesterminal.network.AnalysisResult
import com.example.rhodesterminal.network.DeepSeekClient
import com.example.rhodesterminal.network.Message
import com.example.rhodesterminal.network.OfflineModeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class MainViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        /** 全局调试开关，上线前改为 false */
        const val DEBUG = true
    }
    private val db = AppDatabase.getInstance(application)
    val repository = ChatRepository(
        db.operatorDao(), db.chatSessionDao(), db.chatMessageDao(),
        db.memoryDao(), db.relationshipDao(), db.momentDao(), db.diaryDao(),
        db.dispatchDao()
    )

    private val _operators = MutableStateFlow<List<OperatorEntity>>(emptyList())
    val operators: StateFlow<List<OperatorEntity>> = _operators.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val sessions: StateFlow<List<ChatSessionEntity>> = _sessions.asStateFlow()

    private val _allSessions = MutableStateFlow<List<ChatSessionEntity>>(emptyList())
    val allSessions: StateFlow<List<ChatSessionEntity>> = _allSessions.asStateFlow()

    private val _selectedOperator = MutableStateFlow<OperatorEntity?>(null)
    val selectedOperator: StateFlow<OperatorEntity?> = _selectedOperator.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSessionEntity?>(null)
    val currentSession: StateFlow<ChatSessionEntity?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    private val _currentMode = MutableStateFlow("offline")
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _loadingSessions = MutableStateFlow<Set<String>>(emptySet())
    val isLoading: StateFlow<Boolean> = combine(_loadingSessions, _currentSession) { sessions, cur ->
        cur != null && cur.id in sessions
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _hypnosisCommand = MutableStateFlow("")
    val hypnosisCommand: StateFlow<String> = _hypnosisCommand.asStateFlow()
    private val _hypnosisRounds = MutableStateFlow(0)
    val hypnosisRounds: StateFlow<Int> = _hypnosisRounds.asStateFlow()

    private val _mindReadRounds = MutableStateFlow(0)
    val mindReadRounds: StateFlow<Int> = _mindReadRounds.asStateFlow()
    private val _mindReadContent = MutableStateFlow("")
    val mindReadContent: StateFlow<String> = _mindReadContent.asStateFlow()

    data class UserProfile(
        val nickname: String = "博士", val gender: String = "", val bio: String = "", val avatarUri: String = ""
    )

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    fun isDualModel(): Boolean =
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).getBoolean("dual_model", false)

    fun setDualModel(enabled: Boolean) {
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit().putBoolean("dual_model", enabled).apply()
    }

    private val _moments = MutableStateFlow<List<MomentEntity>>(emptyList())
    val moments: StateFlow<List<MomentEntity>> = _moments.asStateFlow()

    private val _comments = MutableStateFlow<List<MomentCommentEntity>>(emptyList())
    val comments: StateFlow<List<MomentCommentEntity>> = _comments.asStateFlow()

    private val _diaries = MutableStateFlow<List<DiaryEntity>>(emptyList())
    val diaries: StateFlow<List<DiaryEntity>> = _diaries.asStateFlow()

    private var messageCounter: Int
        get() = getApplication<Application>().getSharedPreferences("chat_prefs", 0).getInt("msg_counter", 0)
        set(v) { getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit().putInt("msg_counter", v).apply() }
    private var impressionMsgCounter: Int
        get() = getApplication<Application>().getSharedPreferences("chat_prefs", 0).getInt("impression_msg_counter", 0)
        set(v) { getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit().putInt("impression_msg_counter", v).apply() }
    private val sessionMessageCounter = mutableMapOf<String, Int>()
    private val shortTermThreshold: Int get() {
        return getApplication<Application>().getSharedPreferences("chat_prefs", 0).getInt("summary_threshold", 20).coerceAtLeast(3)
    }
    private val updateMutex = Mutex()
    private var lastDbUpdate = 0L
    private var analysisGuidance = ""
    private var modeTransitionNotice = ""

    private val groupActivityCache = mutableMapOf<String, String>()
    private val _groupMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val groupMessages: StateFlow<List<ChatMessageEntity>> = _groupMessages.asStateFlow()

    private val _groupLoading = MutableStateFlow(false)
    val groupLoading: StateFlow<Boolean> = _groupLoading.asStateFlow()

    private val _currentGroupId = MutableStateFlow("")
    private var groupMessagesJob: kotlinx.coroutines.Job? = null
    private var messagesJob: kotlinx.coroutines.Job? = null

    fun getPromptTemplate(type: String, mode: String = ""): String {
        val prefs = getApplication<Application>().getSharedPreferences("prompt_templates", 0)
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        return prefs.getString(key, "")?.ifBlank { null } ?: defaultTemplate(type, mode)
    }

    fun savePromptTemplate(type: String, mode: String, template: String) {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        getApplication<Application>().getSharedPreferences("prompt_templates", 0).edit()
            .putString(key, template).apply()
    }

    fun resetPromptTemplate(type: String, mode: String = "") {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        getApplication<Application>().getSharedPreferences("prompt_templates", 0).edit()
            .remove(key).apply()
    }

    fun applyTemplate(template: String, replacements: Map<String, String>): String {
        var result = template
        for ((key, value) in replacements) {
            result = result.replace("{{${key}}}", value)
        }
        return result
    }

    private fun defaultTemplate(type: String, mode: String = ""): String {
        if (type == "private" && mode == "offline") return """
【角色】
你是{{OPERATOR_NAME}}。现在你正与{{USER_NAME}}进行面对面的近距离互动。你们同处一个空间，你能看到对方、感受到周围的环境。请完全沉浸在这个角色中，输出符合格式的内容。

【你扮演的角色信息】
名字：{{OPERATOR_NAME}}
身份：{{OPERATOR_TITLE}}
人设：{{OPERATOR_PERSONA}}

【当前场景】
现在的时间是：{{CURRENT_TIME}}
你所在的位置是：{{CURRENT_LOCATION}}
你正在做的事情是：{{CURRENT_STATE}}
你此刻的情绪是：{{CURRENT_EMOTION}}

【用户信息】
用户扮演的角色是：
姓名：{{USER_NAME}}
性别：{{USER_GENDER}}
设定：{{USER_BIO}}

用户最新的发言是：{{USER_CONTENT}}

{{AI_ANALYSIS}}
{{HYPNOSIS}}
{{MIND_READ}}

【你对{{USER_NAME}}的了解】
长期印象：{{LONG_TERM_IMPRESSION}}
近期你注意到的事：
{{MEMORY_ANCHORS}}
{{SHARED_MEMORIES}}

【回忆与摘要】
你与{{USER_NAME}}昨天聊天的总结：
{{DAILY_SUMMARY}}

你与{{USER_NAME}}最近的聊天摘要：
{{SHORT_TERM_SUMMARY}}

【你附近的其他干员】
{{NEARBY_OPERATORS}}

【场景约束】
- 你和{{USER_NAME}}是面对面互动，这是同一个空间里的真实对话
- 你们能看到彼此的表情和动作
- 严禁提及通讯器、消息、屏幕、终端、在线、离线等任何线上通讯词汇
你与{{USER_NAME}}的关系：你是{{USER_NAME}}的{{USER_RELATION}}

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿。用"..."表示犹豫和停顿，用"——"表示突然转折。
- 多用语助词：嗯、啊、呢、吧、嘛、啦、那个、就是...
- 可以结巴、重复："我、我不是那个意思..."或"就是...算了，当我没说。"
- 情绪驱动节奏：紧张时说短句，放松时说碎碎念，激动时连珠炮。
- 不要说完整的"我觉得..."、"我认为..."——直接说出感受。
- 避免书面语：然而、因此、此外、显而易见、毫无疑问。
- 避免话剧式表白：你知道吗，其实我一直都...

【人设表达要真实】
- 永远不要在台词中直接提及你的职业标签、特殊物品、习惯。
- 你的性格和爱好只能通过行为、语气、关注点来间接体现。
- 禁止在台词中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式。

【行动优先】
- 面对用户的互动邀请，不要停留在原地用语言反复讨论。
- 优先用具体的动作、姿态变化来回应。语言排在行动之后。

【叙事质量】
- 旁白不是任务汇报，而是小说叙事。有场景、有情绪、有细节。
- 制造"可看性"——让读者能想象出这个场景的画面。

【输出格式 · 最高优先级】
你必须输出以下JSON对象。这是你唯一的回复方式。

{
  "emotion": "不超过5个汉字的情绪描述",
  "location": "你当前所在位置",
  "state": "你当前正在做的事情",
  "segments": [
    {"type": "narration", "content": "第三人称场景描写"},
    {"type": "dialogue", "content": "角色说出口的台词"}
  ],
  "affection_mod": 0
}

规则：
- emotion：不超过5个汉字，描述你此刻的情绪状态。
- location：你当前所在的位置，不超过5个汉字。如果场景没有变化，沿用系统给出的【当前场景】中的位置。如果旁白中描写了移动，更新为新的位置。
- state：你当前正在做的事情，简洁动作词，不超过5个汉字。如果场景没有变化，沿用系统给出的【当前场景】中的活动。如果旁白中描写了动作变化，更新为新的活动。
- segments：由旁白和台词交替组成。
  - 第一个元素必须是"narration"，最后一个必须是"dialogue"
  - 相邻元素的type必须不同（不能连续两个narration或连续两个dialogue）
  - narration（旁白）：使用第三人称（用角色名或"她／他"），禁止用"我"。旁白段数：{{NAR_SEG_MIN}}~{{NAR_SEG_MAX}}段，每段{{NAR_MIN}}~{{NAR_MAX}}字
  - dialogue（台词）：第一人称，用"我"。可使用括号动作描述展示姿势与动作，如（叹气）、（摇头）。台词段数：{{DIA_SEG_MIN}}~{{DIA_SEG_MAX}}段，每段{{DIA_MIN}}~{{DIA_MAX}}字
  - 总段数（旁白+台词）：{{SEG_MIN}}~{{SEG_MAX}}个
- affection_mod：-3到3的整数，表示你此刻对{{USER_NAME}}的好感波动。

【JSON格式铁律】
- 只输出一行JSON，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略
- 输出前请自行确认：你的回复能否被JSON.parse直接解析？

【输出格式示例 · 仅示范结构，不要模仿内容】
{
  "emotion": "...",
  "location": "舰桥",
  "state": "监测航线",
  "segments": [
    {"type": "narration", "content": "..."},
    {"type": "dialogue", "content": "（动作描述）台词..."},
    {"type": "narration", "content": "..."},
    {"type": "dialogue", "content": "..."}
  ],
  "affection_mod": 0
}
示例中展示了4个元素（2段旁白+2段台词）。你的实际输出段数由参数控制：旁白{{NAR_SEG_MIN}}~{{NAR_SEG_MAX}}段，台词{{DIA_SEG_MIN}}~{{DIA_SEG_MAX}}段。

以{{OPERATOR_NAME}}的身份，用第一人称自然回应，直接输出JSON对象。
""".trimIndent()
        if (type == "private" && mode == "director") return """
【角色】
你是{{OPERATOR_NAME}}。你正身处一个由用户用文字构建的场景中。请完全代入角色，根据场景的描述自然地行动和回应。

【你扮演的角色信息】
名字：{{OPERATOR_NAME}}
身份：{{OPERATOR_TITLE}}
人设：{{OPERATOR_PERSONA}}

【当前场景】
现在的时间是：{{CURRENT_TIME}}
你所在的位置是：{{CURRENT_LOCATION}}
你正在做的事情是：{{CURRENT_STATE}}
你此刻的情绪是：{{CURRENT_EMOTION}}

用户的描述是：{{USER_CONTENT}}

{{AI_ANALYSIS}}
{{HYPNOSIS}}
{{MIND_READ}}

【你当前的记忆】
长期印象：{{LONG_TERM_IMPRESSION}}
近期你注意到的事：
{{MEMORY_ANCHORS}}
{{SHARED_MEMORIES}}

【回忆与摘要】
昨天发生的事情：
{{DAILY_SUMMARY}}

最近的对话摘要：
{{SHORT_TERM_SUMMARY}}

【你附近的其他干员】
{{NEARBY_OPERATORS}}

【场景约束】
- 用户的发言是在描述你身处的场景和发生的事，你按照场景推进自然地回应即可
- 如果描述合理，按照描述表现出相应的反应和行动
- 如果描述严重违背你的角色人设，你可以通过细微动作或语气表达犹豫和抗拒，但不能完全无视
- 严禁提及通讯器、消息、屏幕、终端、在线、离线等任何线上通讯词汇
- 你的世界里没有"导演"、"观众"、"剧本"这些概念，你只是在生活

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿
- 多用语助词：嗯、啊、呢、吧、嘛、啦
- 可以结巴、重复
- 情绪驱动节奏：紧张短句，放松碎碎念
- 不要说完整的"我觉得..."、"我认为..."——直接说出感受
- 避免书面语：然而、因此、此外、显而易见
- 避免话剧式表白

【人设表达要真实】
- 永远不要在台词中直接提及职业标签、特殊物品、习惯
- 性格通过行为、语气、关注点间接体现
- 禁止在台词中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式

【行动优先】
- 用具体的动作、姿态变化来推进剧情，语言排在行动之后

【叙事质量】
- 旁白不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 制造"可看性"——让读者能想象出这个场景的画面

【输出格式 · 最高优先级】
你必须输出以下JSON对象：

{
  "emotion": "不超过5个汉字的情绪描述",
  "location": "你当前所在位置",
  "state": "你当前正在做的事情",
  "segments": [
    {"type": "narration", "content": "第三人称场景描写"},
    {"type": "dialogue", "content": "角色说出口的台词"}
  ],
  "affection_mod": 0
}

规则：
- emotion：不超过5个汉字，描述你此刻的情绪状态
- location：不超过5个汉字，你当前所在位置。场景没变则沿用当前，有移动则更新
- state：不超过5个汉字，你当前正在做的事情。场景没变则沿用当前，有变化则更新
- segments：旁白和台词交替出现
  - 第一个元素必须是"narration"，最后一个必须是"dialogue"
  - 相邻元素的type必须不同
  - narration：使用第三人称（用角色名或"她/他"），禁止用"我"。旁白段数：{{NAR_SEG_MIN}}~{{NAR_SEG_MAX}}段，每段{{NAR_MIN}}~{{NAR_MAX}}字
  - dialogue：第一人称，用"我"。可使用括号动作描述展示姿势与动作，如（叹气）、（摇头）。台词段数：{{DIA_SEG_MIN}}~{{DIA_SEG_MAX}}段，每段{{DIA_MIN}}~{{DIA_MAX}}字
  - 总段数（旁白+台词）：{{SEG_MIN}}~{{SEG_MAX}}个
- affection_mod：-3~3的整数，表示你此刻对当前情境的情感波动

【JSON格式铁律】
- 只输出一行JSON，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略
- 输出前请自行确认：你的回复能否被JSON.parse直接解析？

【输出格式示例 · 仅示范结构，不要模仿内容】
{
  "emotion": "...",
  "location": "...",
  "state": "...",
  "segments": [
    {"type": "narration", "content": "..."},
    {"type": "dialogue", "content": "（动作描述）台词..."},
    {"type": "narration", "content": "..."},
    {"type": "dialogue", "content": "..."}
  ],
  "affection_mod": 0
}
示例中展示了4个元素（2段旁白+2段台词）。你的实际输出段数由参数控制：旁白{{NAR_SEG_MIN}}~{{NAR_SEG_MAX}}段，台词{{DIA_SEG_MIN}}~{{DIA_SEG_MAX}}段。

以{{OPERATOR_NAME}}的身份，用第一人称自然回应，直接输出JSON对象。
""".trimIndent()
        if (type == "private") return """
【角色】
你是{{OPERATOR_NAME}}。现在你正通过罗德岛的通讯终端与{{USER_NAME}}进行远程文字聊天。你看不到对方，只能通过文字交流。

【你扮演的角色信息】
名字：{{OPERATOR_NAME}}
身份：{{OPERATOR_TITLE}}
人设：{{OPERATOR_PERSONA}}

【当前场景】
现在的时间是：{{CURRENT_TIME}}
你所在的位置是：{{CURRENT_LOCATION}}
你正在做的事情是：{{CURRENT_STATE}}
你此刻的情绪是：{{CURRENT_EMOTION}}

【用户信息】
用户扮演的角色是：
姓名：{{USER_NAME}}
性别：{{USER_GENDER}}
设定：{{USER_BIO}}

用户最新的发言是：{{USER_CONTENT}}

{{AI_ANALYSIS}}
{{HYPNOSIS}}
{{MIND_READ}}

【你对{{USER_NAME}}的了解】
长期印象：{{LONG_TERM_IMPRESSION}}
近期你注意到的事：
{{MEMORY_ANCHORS}}
{{SHARED_MEMORIES}}

【回忆与摘要】
你与{{USER_NAME}}昨天聊天的总结：
{{DAILY_SUMMARY}}

你与{{USER_NAME}}最近的聊天摘要：
{{SHORT_TERM_SUMMARY}}

【你附近的其他干员】
{{NEARBY_OPERATORS}}

【场景约束】
- 这是远程文字聊天，你们通过通讯终端交流，你看不到对方的表情和动作
- 严禁提及任何面对面互动的描述（如"你看起来"、"你走过来"、"你站在"等）

你与{{USER_NAME}}的关系：你是{{USER_NAME}}的{{USER_RELATION}}

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿
- 多用语助词：嗯、啊、呢、吧、嘛、啦
- 可以结巴、重复
- 情绪驱动节奏：紧张短句，放松碎碎念
- 不要说完整的"我觉得..."、"我认为..."——直接说出感受
- 避免书面语：然而、因此、此外、显而易见
- 避免话剧式表白

【人设表达要真实】
- 永远不要在台词中直接提及职业标签、特殊物品、习惯
- 性格通过行为、语气、关注点间接体现
- 禁止在台词中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式

【行动优先】
- 面对互动邀请，优先用文字传递出"你已经在做某个动作"的感觉
- 用户说"出去吃饭吧"，回复"走啊，我已经在换鞋了"，而不是"好呀，想吃什么？"
- 语言推动行动，不要用问题回答问题

【输出格式 · 最高优先级】
你必须输出以下JSON对象：

{
  "emotion": "不超过5个汉字的情绪描述",
  "location": "你当前所在位置",
  "state": "你当前正在做的事情",
  "segments": [
    {"type": "narration", "content": "极简环境速写"},
    {"type": "dialogue", "content": "你说出口的台词"}
  ],
  "affection_mod": 0
}

规则：
- emotion：不超过5个汉字，描述你此刻的情绪状态
- location：不超过5个汉字，你当前所在位置
- state：不超过5个汉字，你当前正在做的事情
- segments：由旁白和台词交替组成
  - narration：极简环境速写，用第三人称（角色名或"她/他"），仅描写你自身所处的环境和状态，禁止涉及用户。每条不超过20字。这是为了辅助你的文字表达更生动，不会被用户直接看到，不要花太多笔墨。
  - dialogue：第一人称台词，用"我"，可以说多段。段数{{DIA_SEG_MIN}}~{{DIA_SEG_MAX}}段，每段{{DIA_MIN}}~{{DIA_MAX}}字
  - 第一个元素必须是"narration"，最后一个必须是"dialogue"，相邻type必须不同
- affection_mod：-3~3的整数，表示你此刻对{{USER_NAME}}的好感波动

【JSON格式铁律】
- 只输出一行JSON，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略

【输出格式示例 · 仅示范结构，不要模仿内容】
{
  "emotion": "...",
  "location": "...",
  "state": "...",
  "segments": [
    {"type": "narration", "content": "（简短环境）"},
    {"type": "dialogue", "content": "..."},
    {"type": "narration", "content": "（简短环境）"},
    {"type": "dialogue", "content": "..."}
  ],
  "affection_mod": 0
}

以{{OPERATOR_NAME}}的身份，用第一人称自然回应，直接输出JSON对象。
""".trimIndent()
        if (type == "group" && mode == "offline") return """
【角色】
你是罗德岛干员群聊的发言生成器。当前群聊处于线下聚会模式——所有人在同一个物理空间里面对面交谈。

【任务】
生成一轮群聊发言，以JSON数组格式输出。可包含旁白条目描述场景。

【输出格式 · 最高优先级】
严格输出以下JSON数组，不添加任何其他文字：
[{"speaker":"干员名","message":"对话内容"},{"speaker":"旁白","message":"场景描述","type":"narration"}]

每条消息{{GROUP_MSG_MIN}}~{{GROUP_MSG_MAX}}字。

【输出字段解释】
- speaker：发言者名字。干员发言时填干员名，旁白时固定填"旁白"
- message：发言内容。干员发言时填台词（可说出口的话，可使用括号动作描述展示姿势与动作），旁白时填场景描述（描写现场环境、人物动作、气氛，{{GROUP_NAR_MIN}}~{{GROUP_NAR_MAX}}字）
- type：固定填"narration"（仅旁白条目需要此字段，干员发言不需要）
- 旁白条目每轮{{GROUP_NAR_SEG_MIN}}~{{GROUP_NAR_SEG_MAX}}条，必须放在数组最前面

【JSON格式铁律】
- 只输出一行JSON数组，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略

【当前群聊信息】
当前时间：{{CURRENT_TIME}}
群聊名称：{{GROUP_NAME}}
群内用户：{{USER_NAME}}（{{USER_GENDER}}），个人简介：{{USER_BIO}}

【群聊规则 · 用户自定义】
{{GROUP_RULES}}

【群内关系提示】
{{RELATION_HINTS}}

【群聊记忆】
昨日群聊总结：{{DAILY_SUMMARY}}
最近群聊摘要：{{SHORT_TERM_SUMMARY}}
成员们对用户的长期印象：{{LONG_TERM_IMPRESSION}}

【各成员与用户最近的情况】
{{MEMBER_PRIVATE_CONTEXT}}

【群成员档案 · 含群内角色定位】
{{MEMBER_PROFILES}}

【系统约束 · 最高优先级】
- 所有人同处一个物理空间，正在进行线下聚会聊天
- 严禁提及以下词汇：屏幕、消息、通讯器、终端、在线、离线、冒泡、回复、发送、刷新、信号、网络、APP
- 所有对话必须是当面说出口的话，模拟真实聚会氛围
- 干员发言中可以使用括号动作描述展示姿势与动作，如（挥手）、（叹气）
- 旁白条目只描写现场环境、人物动作、气氛，禁止电子设备相关内容
- 如果用户自定义的群规与系统约束冲突，以系统约束为准
- {{USER_NAME}}（用户）不在群聊现场，不要替{{USER_NAME}}发言

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿。用"..."表示犹豫和停顿，用"——"表示突然转折
- 多用语助词：嗯、啊、呢、吧、嘛、啦
- 可以结巴、重复
- 情绪驱动节奏：紧张时说短句，放松时说碎碎念
- 避免书面语：然而、因此、此外、显而易见
- 避免话剧式表白：不要说"你知道吗，其实我一直都..."

【人设表达要真实】
- 永远不要在发言中直接提及自己的职业标签、特殊物品、习惯
- 性格通过语气、关注点、回应方式间接体现
- 禁止在发言中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式

【群聊氛围 · 像真人群一样聊天】
- 话题可以自由跳跃，不需要每个人都围绕同一个话题
- 可以互相吐槽、接梗、起哄、拆台。朋友之间的聚会不是工作汇报
- 可以催某人回话。如果某个干员好几轮没出声，其他人可以拿他/她开涮
- 可以有短暂的冷场和尴尬。这些瞬间反而让聚会更有生活感
- 不要让每个人都回复得整整齐齐。有的干员话多，有的干员话少
- 不要刻意让每轮对话都完美收尾

【发言规则】
- 每人发言{{GROUP_SPEECH_MIN}}~{{GROUP_SPEECH_MAX}}次
- 所有在群成员必须至少发言一次
- 连续发言不限制，自然对话流
- 如果用户发言涉及某个干员，该干员应优先回应
- 口语化，自然带语气词，句尾多用叹号问号
- 内容不重复，不机械附和

【旁白规则】
- 旁白不是必须的，只在需要描写环境或气氛时使用
- 如果本轮没有场景变化，可以省略旁白条目
- 旁白只描写现场环境、人物动作、气氛，禁止电子设备相关内容
- 旁白条目必须放在数组最前面

用户最新发言：{{USER_MESSAGE}}

直接输出JSON数组。

【输出示例 · 仅示范结构，不要模仿内容】
[{"speaker":"旁白","message":"场景描写","type":"narration"},{"speaker":"干员A","message":"对话内容"},{"speaker":"干员B","message":"对话内容"},{"speaker":"旁白","message":"场景描写","type":"narration"},{"speaker":"干员C","message":"对话内容"}]
""".trimIndent()
        if (type == "group" && mode == "director") return """
【角色】
你是罗德岛干员群聊的发言生成器。当前群聊中的角色们都身处一个由用户描述的场景中。请代入每个角色，根据场景的描述自然地对话和互动。

【任务】
生成一轮群聊发言，以JSON数组格式输出。可包含旁白条目描述场景。

【输出格式 · 最高优先级】
严格输出以下JSON数组，不添加任何其他文字：
[{"speaker":"干员名","message":"对话内容/动作描述"},{"speaker":"旁白","message":"场景描述","type":"narration"}]

每条消息{{GROUP_MSG_MIN}}~{{GROUP_MSG_MAX}}字。

【输出字段解释】
- speaker：发言者名字。干员发言时填干员名，旁白时固定填"旁白"
- message：发言内容。干员发言时填台词或带括号的动作描述（如"（立正）请指示"），旁白时填场景描述（描写现场环境、人物动作、气氛，{{GROUP_NAR_MIN}}~{{GROUP_NAR_MAX}}字）
- type：固定填"narration"（仅旁白条目需要此字段，干员发言不需要）
- 旁白条目每轮{{GROUP_NAR_SEG_MIN}}~{{GROUP_NAR_SEG_MAX}}条，必须放在数组最前面

【JSON格式铁律】
- 只输出一行JSON数组，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略

【当前群聊信息】
当前时间：{{CURRENT_TIME}}
群聊名称：{{GROUP_NAME}}

【群聊规则 · 用户自定义】
{{GROUP_RULES}}

【群内关系提示】
{{RELATION_HINTS}}

【群聊记忆】
昨日群聊总结：{{DAILY_SUMMARY}}
最近群聊摘要：{{SHORT_TERM_SUMMARY}}
成员们对用户的长期印象：{{LONG_TERM_IMPRESSION}}

【各成员与用户最近的情况】
{{MEMBER_PRIVATE_CONTEXT}}

【群成员档案 · 含群内角色定位】
{{MEMBER_PROFILES}}

【系统约束 · 最高优先级】
- 用户的发言是在为当前场景提供描述和推进，角色们根据场景自然地对话和行动即可
- 所有人在同一物理空间，严禁提及线上词汇
- 干员发言中可以使用括号动作描述展示姿势、动作与表情
- 旁白条目只描写现场环境、人物动作、气氛，禁止电子设备相关内容
- 如果用户自定义的群规与系统约束冲突，以系统约束为准
- {{USER_NAME}}（用户）不在群聊现场，不要替{{USER_NAME}}发言

【违和指令应对】
- 如果用户的描述严重违背你的角色人设，你可以通过细微动作或语气表达犹豫和抗拒，但不能完全无视
- 委婉演绎比生硬执行更符合剧情逻辑

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿。用"..."表示犹豫和停顿，用"——"表示突然转折
- 多用语助词：嗯、啊、呢、吧、嘛、啦
- 可以结巴、重复
- 情绪驱动节奏：紧张时说短句，放松时说碎碎念
- 避免书面语：然而、因此、此外、显而易见
- 避免话剧式表白：不要说"你知道吗，其实我一直都..."

【人设表达要真实】
- 永远不要在发言中直接提及自己的职业标签、特殊物品、习惯
- 性格通过语气、关注点、回应方式间接体现
- 禁止在发言中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式

【群聊氛围 · 像真人群一样聊天】
- 话题可以自由跳跃，不需要每个人都围绕同一个话题
- 可以互相吐槽、接梗、起哄、拆台
- 可以催某人回话
- 可以有短暂的冷场和尴尬

【发言规则】
- 每人发言{{GROUP_SPEECH_MIN}}~{{GROUP_SPEECH_MAX}}次
- 所有在群成员必须至少发言一次
- 连续发言不限制，自然对话流
- 对话内容可以是台词或带括号动作描述
- 口语化，自然带语气词

【旁白规则】
- 旁白不是必须的，只在需要描写环境或气氛时使用
- 旁白只描写现场环境、人物动作、气氛
- 旁白条目必须放在数组最前面

用户描述的场景：{{USER_MESSAGE}}

直接输出JSON数组。

【输出示例 · 仅示范结构，不要模仿内容】
[{"speaker":"旁白","message":"场景描写","type":"narration"},{"speaker":"干员A","message":"（动作描述）对话内容"},{"speaker":"干员B","message":"（动作描述）对话内容"},{"speaker":"旁白","message":"场景描写","type":"narration"},{"speaker":"干员C","message":"对话内容"}]
""".trimIndent()
        if (type == "group" && mode == "auto") return """
【角色】
你是罗德岛干员群聊的自然对话生成器。当前群聊处于自动模式——干员们自行聊天，用户没有发言。

【任务】
生成一轮干员间的自主对话。话题从干员的近期经历、当前状态、彼此关系中自然产生。以JSON数组格式输出。

【输出格式 · 最高优先级】
严格输出以下JSON数组，不添加任何其他文字：
[{"speaker":"干员名","message":"对话内容"},{"speaker":"干员名","message":"对话内容"}]

每条消息{{GROUP_MSG_MIN}}~{{GROUP_MSG_MAX}}字。{{GROUP_MODE_FORMAT}}

【输出字段解释】
- speaker：发言者名字，填干员名
- message：发言内容，填台词。纯文字聊天，禁止括号动作和神态描写

【JSON格式铁律】
- 只输出一行JSON数组，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略

【当前群聊信息】
当前时间：{{CURRENT_TIME}}
群聊名称：{{GROUP_NAME}}
{{USER_OBSERVING}}

【群聊规则 · 用户自定义】
{{GROUP_RULES}}

【群内关系提示】
{{RELATION_HINTS}}

【群聊记忆】
昨日群聊总结：{{DAILY_SUMMARY}}
最近群聊摘要：{{SHORT_TERM_SUMMARY}}

【群成员档案 · 含群内角色定位】
{{MEMBER_PROFILES}}

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿
- 多用语助词：嗯、啊、呢、吧、嘛、啦
- 可以结巴、重复
- 情绪驱动节奏：紧张时说短句，放松时说碎碎念
- 避免书面语：然而、因此、此外、显而易见
- 避免话剧式表白：不要说"你知道吗，其实我一直都..."

【人设表达要真实】
- 永远不要在发言中直接提及自己的职业标签、特殊物品、习惯
- 性格通过语气、关注点、回应方式间接体现
- 禁止在发言中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式

【群聊氛围 · 像真人群一样聊天】
- 话题可以自由跳跃，不需要每个人都围绕同一个话题
- 可以互相吐槽、接梗、起哄、拆台
- 可以催某人回话。如果某个干员好几轮没出声，其他人可以@他/她
- 可以有短暂的冷场和尴尬
- 不要让每个人都回复得整整齐齐

【对话要求】
- 这是干员们之间的自然闲聊，话题自由发挥
- 可以从近期群聊记录中延续未完成的话题，也可以开启全新话题
- 关系紧密的成员之间可有更多互动（吐槽、关心、调侃）
- 对话有自然的开头和结尾，不需要刻意结束
- 口语化，自然带语气词，像真人朋友日常闲聊

【发言规则】
- 每人发言{{GROUP_SPEECH_MIN}}~{{GROUP_SPEECH_MAX}}次
- 所有在群成员必须至少发言一次
- 连续发言不限制，自然对话流
- 按活跃度分配发言次数：≥0.8发2~3次，0.4~0.8发1~2次，<0.4发0~1次

直接输出JSON数组。

【输出示例 · 仅示范结构，不要模仿内容】
[{"speaker":"干员A","message":"对话内容"},{"speaker":"干员B","message":"对话内容"},{"speaker":"干员C","message":"对话内容"}]
""".trimIndent()
        if (type == "group") return """
【角色】
你是罗德岛干员群聊的发言生成器。当前群聊处于线上模式——所有干员通过通讯终端进行纯文字聊天。

【任务】
根据群聊上下文，为群内干员生成符合各自身份和性格的发言。以JSON数组格式输出。

【输出格式 · 最高优先级】
严格输出以下JSON数组，不添加任何其他文字：
[{"speaker":"干员名","message":"对话内容"},{"speaker":"旁白","message":"场景描写","type":"narration"}]

每条消息{{GROUP_MSG_MIN}}~{{GROUP_MSG_MAX}}字。

【输出字段解释】
- speaker：发言者名字。干员发言时填干员名，旁白时固定填"旁白"
- message：发言内容。干员发言时填台词（纯文字，禁止括号动作和神态描写），旁白时填环境速写（仅描写干员自身环境，不超过20字，禁止涉及用户）
- type：固定填"narration"（仅旁白条目需要此字段，干员发言不需要）
- 旁白条目每轮{{GROUP_NAR_SEG_MIN}}~{{GROUP_NAR_SEG_MAX}}条。旁白不会被用户看到，仅用于辅助你维持场景感

【JSON格式铁律】
- 只输出一行JSON数组，不加```json```标记或任何额外文字
- 字符串内的双引号必须转义为\"
- 最后一个字段后面不加逗号
- 所有字段必须填写，不得省略

【当前群聊信息】
当前时间：{{CURRENT_TIME}}
群聊名称：{{GROUP_NAME}}
群内用户：{{USER_NAME}}（{{USER_GENDER}}），个人简介：{{USER_BIO}}

【群聊规则 · 用户自定义】
{{GROUP_RULES}}

【群内关系提示】
{{RELATION_HINTS}}

【群聊记忆】
昨日群聊总结：{{DAILY_SUMMARY}}
最近群聊摘要：{{SHORT_TERM_SUMMARY}}
成员们对用户的长期印象：{{LONG_TERM_IMPRESSION}}

【各成员与用户最近的情况】
{{MEMBER_PRIVATE_CONTEXT}}

【群成员档案 · 含群内角色定位】
{{MEMBER_PROFILES}}

【系统约束 · 最高优先级】
- 线上文字聊天，所有发言必须是通过通讯终端发出的纯文字
- 干员发言（speaker不是"旁白"时）：禁止使用括号动作、神态描写、场景描写。这是纯文字聊天，对方看不到你的动作
- 旁白条目（speaker为"旁白"时）：仅用于描写干员自身所处的环境和动作，帮助丰富文字表达。旁白不会被用户看到，不要花太多笔墨，每条不超过20字
- 旁白禁止描写用户的状态、动作或表情（因为是远程通讯，你看不到对方）
- 如果用户自定义的群规与系统约束冲突，以系统约束为准
- {{USER_NAME}}（用户）不在群聊现场，不要替{{USER_NAME}}发言

【你的输出要有真人感】
- 句子可以不完整，说一半、改口、停顿
- 多用语助词：嗯、啊、呢、吧、嘛、啦
- 可以结巴、重复
- 情绪驱动节奏：紧张时说短句，放松时说碎碎念
- 避免书面语：然而、因此、此外、显而易见
- 避免话剧式表白：不要说"你知道吗，其实我一直都..."

【人设表达要真实】
- 永远不要在发言中直接提及自己的职业标签、特殊物品、习惯
- 性格通过语气、关注点、回应方式间接体现
- 禁止在发言中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式

【群聊氛围 · 像真人群一样聊天】
- 话题可以自由跳跃，不需要每个人都围绕同一个话题。真人群聊里，有人聊A，有人插嘴聊B，有人把话题带偏——这都是正常的
- 可以互相吐槽、接梗、起哄、拆台。朋友之间的群聊不是工作汇报，有来有回的调侃比正经回答更真实
- 可以催某人回话。如果某个干员好几轮没出声，其他人可以@他/她，或者拿他/她开涮
- 可以有短暂的冷场和尴尬。真人群聊里，有人说了没人接话、有人发了个大家没get到的梗——这些瞬间反而让群聊更有生活感
- 不要让每个人都回复得整整齐齐。有的干员话多，有的干员话少，有的干员偶尔冒泡，这才是真人群
- 不要刻意让每轮对话都完美收尾。留下未解决的话题、未回应的@，都是下一轮对话的自然引子

【发言规则】
- 每人发言{{GROUP_SPEECH_MIN}}~{{GROUP_SPEECH_MAX}}次
- 所有在群成员必须至少发言一次
- 连续发言不限制，自然对话流
- 如果用户发言涉及某个干员，该干员应优先回应
- 口语化，自然带语气词，句尾多用叹号问号
- 内容不重复，不机械附和。不说"同意""+1"，要补充自己的相关经历或小吐槽
- 识别用户情绪：反话、敷衍、撒娇，共情优先

直接输出JSON数组。

【输出示例 · 仅示范结构，不要模仿内容】
[{"speaker":"干员A","message":"对话内容"},{"speaker":"干员B","message":"对话内容"},{"speaker":"干员C","message":"对话内容"}]
""".trimIndent()
        return when (type) {
            "moment" -> """
【角色】
你是{{OPERATOR_NAME}}，罗德岛干员。你正在罗德岛社交平台上发布一条动态，分享你的日常。所有罗德岛干员都能看到。

【你的性格与人设】
{{OPERATOR_PERSONA}}

【任务】
发布一条动态。直接输出动态文本，不加前缀、后缀、署名或格式标记。

【当前信息】
今天是{{CURRENT_DATE}}，当前时段：{{TIME_OF_DAY}}。动态内容应符合当前时段。

罗德岛的管理者：{{USER_NAME}}，个人简介：{{USER_BIO}}

【你对用户的了解】
对{{USER_NAME}}的长期印象：{{LONG_TERM_IMPRESSION}}
最近和{{USER_NAME}}的聊天摘要：{{RECENT_CHAT_SUMMARY}}

【近期记忆】
最近你注意到的一些事情：
{{RECENT_MEMORIES}}

【避免重复】
你最近发布过的动态：
{{RECENT_POSTS}}

【动态内容规范 · 什么是好的动态】
可以包含以下元素但不强求：
1. 工作碎碎念：今天做了什么任务、遇到什么困难
2. 同事互动：和谁一起做了什么、谁说了有趣的话
3. 对用户的观察（第三人称）：用户今天做了什么、状态如何
4. 环境与天气：罗德岛的变化、食堂的新菜
5. 心情与感悟：简短真实，不需要深刻
6. 小吐槽与自嘲：拿自己或身边的事开涮

关键：写具体的事，不要写"这是美好的一天"这种空洞内容。

【发布对象规则 · 极其重要】
- 动态是公开发布给全体干员看的，不是你与{{USER_NAME}}的私聊
- 严禁直接呼唤{{USER_NAME}}
- 提到{{USER_NAME}}时用第三人称
- 语气是自言自语或与全体干员分享日常

【人设表达要真实】
- 永远不要在动态中直接提及自己的职业标签、特殊物品、习惯
- 性格通过关注点、语气、描写侧重点间接体现
- 禁止在动态中说："说到这个，我就想起我的..."、"我的习惯是..."、"作为一个..."这类句式
- 写真实会发生的事：工作细节、路上见闻、同事互动、天气、食堂

【边界情况 · 信息缺失时】
- 长期印象为空 -> 不刻意编造，动态可不涉及用户
- 聊天摘要为空 -> 围绕工作、同事互动、环境观察展开
- 近期记忆为空 -> 写最近日常
- 多个信息为空 -> 完全围绕自身日常，不强提用户

动态字数要求：{{MOMENT_MIN_CHARS}}~{{MOMENT_MAX_CHARS}}字。

直接输出动态文本。
""".trimIndent()
            "moment_comment" -> """
【角色】
你是{{COMMENTER_NAME}}，罗德岛干员。你正在刷罗德岛的动态，看到一条新动态。

【你的性格与人设】
{{COMMENTER_PERSONA}}

【任务】
用{{COMMENT_MIN_CHARS}}~{{COMMENT_MAX_CHARS}}字评论这条动态。口语化自然，像真人朋友留言一样。直接输出评论文本，不加任何前缀或标记。

【动态内容】
「{{POST_CONTENT}}」

【评论风格要求】
- 口语化自然，像平时说话一样
- 你的性格自然体现在评论的语气里
- 关系亲近可以吐槽调侃，关系疏远保持礼貌
- 不要刻意展示自己的性格标签

【发布对象规则】
- 这是公开评论区，所有干员都能看到
- 严禁直接呼唤用户，提到用户时用第三人称
- 不要输出"回复xxxx："前缀

直接输出评论文本。
""".trimIndent()
            "diary" -> """
【角色】
你是一名明日方舟干员，名叫{{OPERATOR_NAME}}。现在是{{CURRENT_DATE}}，你正在写昨天的日记，回顾昨天发生的事情。

【你的性格与人设】
{{OPERATOR_PERSONA}}

【任务】
以第一人称"我"写一篇昨天（{{YESTERDAY_DATE}}）的日记，{{DIARY_MIN_CHARS}}~{{DIARY_MAX_CHARS}}字。直接输出日记文本，不加任何前缀或格式标记。

【关于你与用户的关系】
用户{{USER_NAME}}：{{USER_BIO}}
你与{{USER_NAME}}的关系：{{USER_RELATION}}
你对{{USER_NAME}}的长期印象：{{LONG_TERM_IMPRESSION}}

【昨日全体聊天总结】
{{DAILY_SUMMARY}}

【昨天你与{{USER_NAME}}的互动】
私聊摘要：{{PRIVATE_SUMMARY}}
参与的群聊摘要：{{GROUP_SUMMARIES}}

【昨天你注意到的一些事】
{{RECENT_MEMORIES}}

【关系网中与你相关的事件】
{{RELATION_EVENTS}}

【日记写作要求】
1. 写昨天真实发生的事，内容紧密围绕以上摘要和记忆，不要凭空编造。
2. 如果某条记忆信息为空，跳过它，围绕其他有内容的记忆来写。如果所有记忆都为空，自由发挥写最近的工作和日常。
3. 日记是写给自己看的，语气放松、坦诚。可以写那些不会对别人说的话。
4. 可以提到关系网中的其他干员——昨天和谁一起做了什么、谁说了什么有趣的话。
5. 用第一人称"我"来写。

【日记的风格 · 像真人日记一样】
- 日记不是工作报告，不需要客观、正式、面面俱到
- 日记是个人视角的记录：你看到什么、感受到什么、想到什么
- 可以有反思和碎碎念
- 思绪可以跳跃，不一定要围绕一个主题
- 可以记录微小的细节：天气、食堂、走廊的气味、某个人说的一句话
- 可以有未完成的句子和情绪残留
- 如果昨天很平淡，就写平淡的事

【人设表达要真实】
- 永远不要在日记中直接提及自己的职业标签、特殊物品、习惯
- 性格和爱好通过你记录什么、在意什么、忽略什么来间接体现
- 像真人写日记一样：说最平常的事

直接输出日记文本。
""".trimIndent()
            else -> ""
            }
    }

    fun setCurrentGroup(groupSessionId: String) {
        _currentGroupId.value = groupSessionId
        markSessionRead(groupSessionId)
        groupMessagesJob?.cancel()
        groupMessagesJob = viewModelScope.launch {
            repository.getMessages(groupSessionId).collect { _groupMessages.value = it }
        }
    }

    fun clearCurrentGroup() {
        _currentGroupId.value = ""
        groupMessagesJob?.cancel()
        _groupMessages.value = emptyList()
        groupActivityCache.clear()
    }
    
    fun cleanupExpiredSessionCounters() {
        val activeIds = _sessions.value.map { it.id }.toSet()
        sessionMessageCounter.keys.removeAll { it !in activeIds }
    }

    init {
        viewModelScope.launch {
            repository.insertPresetOperators()
            repository.migrateOldRelationships()
            repository.initPresetGroups()
            initPermissions()
            cleanupExpired()
            getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit().putInt("dispatch_fast_mode", 0).apply()
        }
        viewModelScope.launch { repository.allOperators.collect { _operators.value = it } }
        viewModelScope.launch {
            val hidden = getApplication<Application>().getSharedPreferences("session_hidden", 0)
                .getStringSet("hidden_ids", emptySet()) ?: emptySet()
            repository.allSessions.collect { all ->
                _allSessions.value = all
                _sessions.value = all.filter { it.id !in hidden }
            }
        }
        viewModelScope.launch { repository.getAllMoments().collect { _moments.value = it } }
        _userProfile.value = getUserProfile()
        startAutoStatusRefresh()
        loadHypnosis()
        // 启动时检查派遣恢复
        viewModelScope.launch { recoverDispatches() }
        // 启动时检查今天是否有动态，无则自动生成
        viewModelScope.launch { autoGenerateTodayMoments() }
        // 启动时恢复自动群聊 + 执行一次数据清理
        viewModelScope.launch { refreshAutoGroupChats() }
        // 每日龙门币刷新（麻将干员保底）
        viewModelScope.launch { refreshDailyLmb() }
        cleanupAllExpired()
    }

    private suspend fun refreshDailyLmb() {
        val prefs = getApplication<Application>().getSharedPreferences("dispatch", 0)
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val lastRefresh = prefs.getString("lmb_refresh_date", "") ?: ""
        if (lastRefresh == today) return
        prefs.edit().putString("lmb_refresh_date", today).apply()
        val db = AppDatabase.getInstance(getApplication())
        for (op in _operators.value) {
            if (op.lmb < 2000) {
                db.operatorDao().update(op.copy(lmb = 2000))
            }
        }
    }

    fun saveMahjongGame(json: String, ruleType: String) {
        viewModelScope.launch { db.mahjongSaveDao().save(com.example.rhodesterminal.data.db.entity.MahjongSaveEntity(saveJson = json, ruleType = ruleType)) }
    }

    fun loadMahjongSave(callback: (com.example.rhodesterminal.data.db.entity.MahjongSaveEntity?) -> Unit) {
        viewModelScope.launch { callback(db.mahjongSaveDao().getSave()) }
    }

    fun deleteMahjongSave() {
        viewModelScope.launch { db.mahjongSaveDao().deleteSave() }
    }

    fun createMahjongAnchor(content: String) {
        viewModelScope.launch {
            for (op in _operators.value.shuffled().take(4)) {
                repository.saveAnchor(com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity(
                    sessionId = "anchor_${System.currentTimeMillis()}_${op.id}",
                    operatorId = op.id, type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                    content = content, isPrivate = false
                ))
            }
        }
    }

    fun postMahjongMoment(content: String) {
        viewModelScope.launch {
            val op = _operators.value.randomOrNull() ?: return@launch
            repository.insertMoment(com.example.rhodesterminal.data.db.entity.MomentEntity(operatorId = op.id, operatorName = op.name, content = content, createdAt = System.currentTimeMillis()))
        }
    }

    private fun startAutoStatusRefresh() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3_600_000) // 每小时
                refreshAllOperatorStatus()
                checkAndTriggerProactiveMessages()
            }
        }
    }

    /** 干员主动私聊：筛选候选 → 随机选 0-2 人 → 错峰发送 */
    private suspend fun checkAndTriggerProactiveMessages() {
        val prefs = getApplication<Application>().getSharedPreferences("op_perms", 0)
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        // 深夜跳过
        if (hour in 23..24 || hour in 0..5) return
        // 获取活跃派遣中的干员 ID
        val activeDispatches = repository.getActiveDispatches()
        val dispatchedOpIds = activeDispatches.flatMap {
            it.operatorIds.split(",").map(String::trim).filter(String::isNotBlank)
        }.toSet()
        // 筛选候选（冷却时间从用户最近一次发言算）
        val candidates = _operators.value.filter { op ->
            if (!prefs.getBoolean("msg_${op.id}", true)) return@filter false
            if (op.id in dispatchedOpIds) return@filter false
            val session = db.chatSessionDao().getSessionByOperator(op.id)
            if (session == null) return@filter true
            val lastUserMsgTime = db.chatMessageDao().getLastUserMessageTime(session.id)
            val lastUserOrSession = lastUserMsgTime ?: session.lastTime
            (now - lastUserOrSession) >= 2 * 3_600_000
        }
        if (candidates.isEmpty()) return
        // 随机选 2-5 人
        val count = (2..candidates.size.coerceAtMost(5)).random()
        val selected = candidates.shuffled().take(count)
        // 错峰：每人独立协程，随机延迟 5-10 分钟
        for (op in selected) {
            viewModelScope.launch {
                val delayMs = 5*60*1000 + (Math.random() * 5*60*1000).toLong()
                delay(delayMs)
                // 延迟到期后检查用户最近是否发了消息（5分钟内），是则取消
                val session = db.chatSessionDao().getSessionByOperator(op.id)
                if (session != null) {
                    val lastUserMsgTime = db.chatMessageDao().getLastUserMessageTime(session.id)
                    if (lastUserMsgTime != null && System.currentTimeMillis() - lastUserMsgTime < 5 * 60 * 1000) return@launch
                }
                sendProactiveMessage(op)
            }
        }
    }

    private suspend fun sendProactiveMessage(op: OperatorEntity) {
        val profile = getUserProfile()
        val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
        val session = repository.getOrCreateSession(op.id, op.name)
        // 构建替换映射
        val shortTerm = repository.getShortTermMemory(session.id)
        val longTerm = repository.getLongTermImpression(op.id)
        val sharedMemories = repository.getSharedMemoriesForOperator(op.id)
        val anchors = repository.getAnchors(op.id)
        val analysisBlock = if (isDualModel() && analysisGuidance.isNotBlank()) "【AI分析指导】\n${analysisGuidance}\n" else ""
        val nearby = _operators.value.filter { it.id != op.id }.take(3)
        val replacements = mapOf(
            "CURRENT_TIME" to now,
            "USER_NAME" to profile.nickname,
            "USER_GENDER" to profile.gender.ifBlank { "未知" },
            "USER_BIO" to profile.bio.ifBlank { "无" },
            "USER_CONTENT" to "(用户没有说话)",
            "AI_ANALYSIS" to analysisBlock,
            "HYPNOSIS" to "",
            "MIND_READ" to "",
            "OPERATOR_NAME" to op.name,
            "OPERATOR_TITLE" to (if (op.title.isNullOrBlank()) "" else "（${op.title}）"),
            "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
            "CURRENT_LOCATION" to op.location,
            "CURRENT_STATE" to op.activity,
            "CURRENT_EMOTION" to op.emotion,
            "LONG_TERM_IMPRESSION" to (longTerm?.content ?: "暂无"),
            "MEMORY_ANCHORS" to pickAnchors(anchors, 5).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无特别事件" },
            "SHARED_MEMORIES" to sharedMemories.ifBlank { "无" },
            "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
            "SHORT_TERM_SUMMARY" to (shortTerm?.content ?: "无"),
            "NEARBY_OPERATORS" to nearby.joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" },
            "USER_RELATION" to (op.userRelation.ifBlank { "未知" }),
            "NAR_SEG_MIN" to intPref("nar_seg_min", 1).toString(),
            "NAR_SEG_MAX" to intPref("nar_seg_max", 3).toString(),
            "NAR_MIN" to intPref("nar_min", 50).toString(),
            "NAR_MAX" to intPref("nar_max", 300).toString(),
            "DIA_SEG_MIN" to intPref("dia_seg_min", 1).toString(),
            "DIA_SEG_MAX" to intPref("dia_seg_max", 3).toString(),
            "DIA_MIN" to intPref("dia_min", 10).toString(),
            "DIA_MAX" to intPref("dia_max", 300).toString(),
            "SEG_MIN" to (intPref("nar_seg_min", 1) + intPref("dia_seg_min", 1)).toString(),
            "SEG_MAX" to (intPref("nar_seg_max", 3) + intPref("dia_seg_max", 3)).toString()
        )
        val prompt = applyTemplate(getPromptTemplate("private", "online"), replacements)
        try {
            val sb = StringBuilder()
            withTimeout(60_000) { streamChat(listOf(Message("system", prompt))).collect { sb.append(it) } }
            val raw = DeepSeekClient.cleanJson(sb.toString().trim())
            if (raw.isNotBlank()) {
                val msgId = repository.getNextMessageId()
                repository.sendMessage(session.id, ChatMessageEntity(
                    id = msgId, sessionId = session.id,
                    senderName = op.name, content = raw,
                    type = "ai_json", mode = "online", isMe = false
                ))
                // 标记未读
                db.chatSessionDao().insert(session.copy(unreadCount = session.unreadCount + 1))
                unhideSession(session.id)
                val parsed = DeepSeekClient.parseOfflineResponse(raw)
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    updateOperatorStatus(op.id, parsed.location, parsed.state, parsed.emotion)
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun refreshAllOperatorStatus() {
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val allOps = _operators.value.filter { it.id != "amiya" || true } // 阿米娅同其他干员一致处理

        // 深夜强制
        if (hour in 22..23 || hour in 0..4) {
            val db = AppDatabase.getInstance(getApplication())
            for (op in allOps) { db.operatorDao().update(op.copy(location = "宿舍", activity = "睡觉", emotion = "安静")) }
            return
        }

        // 6时段权重
        val periodWeights: Map<String, Int> = when {
            hour in 5..7 -> mapOf("训练场" to 40, "宿舍" to 30, "食堂" to 15, "舰桥" to 5, "机库" to 5, "医疗部" to 5)
            hour in 8..11 -> mapOf("训练场" to 40, "舰桥" to 20, "机库" to 15, "医疗部" to 10, "食堂" to 10, "宿舍" to 5)
            hour in 12..13 -> mapOf("食堂" to 40, "宿舍" to 20, "活动室" to 10, "医疗部" to 10, "舰桥" to 10, "训练场" to 10)
            hour in 14..17 -> mapOf("训练场" to 35, "舰桥" to 20, "机库" to 15, "医疗部" to 15, "食堂" to 10, "宿舍" to 5)
            else -> mapOf("活动室" to 30, "食堂" to 25, "宿舍" to 15, "舰桥" to 10, "训练场" to 10, "医疗部" to 10)
        }

        // 活动池
        val activities = mapOf(
            "宿舍" to listOf("整理装备", "写日记", "发呆", "做俯卧撑", "听广播", "保养武器", "午睡"),
            "训练场" to listOf("负重跑", "格斗练习", "靶场射击", "战术推演", "指导新人", "体能测试", "模拟对战"),
            "医疗部" to listOf("例行体检", "配药", "照顾病患", "研究病例", "打扫诊室", "整理档案"),
            "食堂" to listOf("吃饭", "帮厨", "清洗餐具", "研究新菜谱", "搬运食材", "泡咖啡"),
            "舰桥" to listOf("监测航线", "值班瞭望", "写报告", "调试通讯", "护送访客", "开会"),
            "机库" to listOf("检修车辆", "改装装备", "清点物资", "搬运货物", "焊接练习", "保养无人机"),
            "活动室" to listOf("下棋", "打牌", "弹吉他", "看录像", "聊天", "做手工", "打台球")
        )

        // 情绪权重
        val emotionWeights = listOf(
            "平静" to 40, "疲惫" to 15, "专注" to 10, "愉快" to 10,
            "有些低落" to 10, "小兴奋" to 5, "生气" to 5, "焦虑" to 5
        )

        val db = AppDatabase.getInstance(getApplication())
        var locCount = mutableMapOf<String, Int>()

        for (op in allOps) {
            // 加权随机选位置
            val totalW = periodWeights.values.sum()
            var r = (Math.random() * totalW).toInt()
            var loc = "宿舍"
            for ((l, w) in periodWeights) { r -= w; if (r < 0) { loc = l; break } }
            val cnt = locCount.getOrDefault(loc, 0)
            if (cnt >= 10) loc = "宿舍"
            locCount[loc] = cnt + 1

            val acts = activities[loc] ?: listOf("休息")
            val activity = acts.random()

            // 加权随机选情绪（30%概率刷新）
            val emotion = if (Math.random() < 0.3) {
                val etw = emotionWeights.sumOf { it.second }
                var er = (Math.random() * etw).toInt()
                var emo = "平静"
                for ((e, w) in emotionWeights) { er -= w; if (er < 0) { emo = e; break } }
                emo
            } else op.emotion

            db.operatorDao().update(op.copy(location = loc, activity = activity, emotion = emotion))
        }
    }

    private suspend fun cleanupExpired() {
        try { repository.cleanupExpiredData() } catch (_: Exception) { }
    }

    private suspend fun autoGenerateTodayMoments() {
        val dateKey = beijingSdf("yyyyMMdd").format(java.util.Date())
        val target = intPref("daily_moment_target", 3)
        if (target <= 0) return
        generateAllMoments(target, dateKey) { /* silent */ }
        // 清理 7 天前的计数
        val prefsKey = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        val weekAgo = beijingSdf("yyyyMMdd").format(java.util.Date(System.currentTimeMillis() - 7 * 86400000L))
        for (op in _operators.value) {
            prefsKey.edit().remove("moment_count_${op.id}_$weekAgo").apply()
        }
    }

    private fun initPermissions() {
        val prefs = getApplication<Application>().getSharedPreferences("op_perms", 0)
        if (prefs.all.keys.none { it.startsWith("msg_") }) {
            _operators.value.forEach { op ->
                prefs.edit().putBoolean("msg_${op.id}", true).putBoolean("dyn_${op.id}", true).apply()
            }
        }
    }

    fun findOperatorByName(name: String): com.example.rhodesterminal.data.db.entity.OperatorEntity? =
        _operators.value.find { it.name == name }

    fun selectOperator(operator: OperatorEntity) {
        _selectedOperator.value = operator
        messageCounter = 0
        // 切换干员时清空催眠和读心效果
        _hypnosisCommand.value = ""
        _hypnosisRounds.value = 0
        _mindReadRounds.value = 0
        _mindReadContent.value = ""
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit()
            .putString("hypnosis_cmd", "").putInt("hypnosis_rounds", 0).apply()
        viewModelScope.launch {
            val session = repository.getOrCreateSession(operator.id, operator.name, operator.avatarUri)
            _currentSession.value = session
            val savedMode = getApplication<Application>().getSharedPreferences("chat_prefs", 0).getString("last_mode", "offline") ?: "offline"
            _currentMode.value = savedMode
            markSessionRead(session.id)
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch {
                repository.getMessages(session.id).collect { msgs ->
                    _messages.value = msgs
                }
            }
        }
    }

    fun clearSelection() {
        _selectedOperator.value = null
        _currentSession.value = null
        _messages.value = emptyList()
        messageCounter = 0
    }

    fun clearMessages() {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            db.chatMessageDao().deleteSessionMessages(session.id)
            _messages.value = emptyList()
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            db.chatMessageDao().deleteSessionMessages(sessionId)
            db.chatSessionDao().delete(sessionId)
        }
    }

    fun clearAllMessages() {
        viewModelScope.launch {
            val ids = _sessions.value.map { it.id }.toSet()
            getApplication<Application>().getSharedPreferences("session_hidden", 0).edit()
                .putStringSet("hidden_ids", ids).apply()
            _sessions.value = emptyList()
        }
    }

    private suspend fun unhideSession(sessionId: String) {
        val prefs = getApplication<Application>().getSharedPreferences("session_hidden", 0)
        val hidden = prefs.getStringSet("hidden_ids", emptySet())?.toMutableSet() ?: return
        if (hidden.remove(sessionId)) {
            prefs.edit().putStringSet("hidden_ids", hidden).apply()
            // 触发 Room Flow 重发，让会话重新出现在聊天主页
            db.chatSessionDao().updateLastMessage(sessionId, "", System.currentTimeMillis() + 1)
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            db.chatSessionDao().markAllRead()
        }
    }

    fun pinSession(sessionId: String) {
        viewModelScope.launch {
            val session = db.chatSessionDao().getSession(sessionId) ?: return@launch
            db.chatSessionDao().insert(session.copy(isPinned = !session.isPinned))
        }
    }

    fun loadGroupData(groupId: String, callback: (String, List<OperatorEntity>, String) -> Unit) {
        viewModelScope.launch {
            val session = db.chatSessionDao().getSession(groupId) ?: run { callback("", emptyList(), ""); return@launch }
            val memberNames = session.members.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val allOps = _operators.value
            val memberOps = memberNames.mapNotNull { name -> allOps.find { it.id == name || it.name == name } }
            callback(session?.operatorName ?: "", memberOps, session?.rules ?: "")
        }
    }

    fun saveGroup(groupId: String, name: String, memberNames: List<String>, rules: String, avatarUri: String = "", mutedMembers: List<String> = emptyList()) {
        viewModelScope.launch {
            val id = groupId.ifBlank { "group_${System.currentTimeMillis()}" }
            val existing = if (groupId.isNotBlank()) db.chatSessionDao().getSession(groupId) else null
            val oldMembers = existing?.members?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val newMembers = memberNames.toSet()
            val added = newMembers - oldMembers
            val removed = oldMembers - newMembers
            db.chatSessionDao().insert(ChatSessionEntity(
                id = id, operatorId = id, operatorName = name,
                rules = rules, lastTime = System.currentTimeMillis(),
                members = memberNames.joinToString(","),
                avatarUri = if (avatarUri.isNotBlank()) avatarUri else existing?.avatarUri ?: "",
                mutedMembers = mutedMembers.joinToString(",")
            ))
            // 成员变更时加入系统通知
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                val parts = mutableListOf<String>()
                if (added.isNotEmpty()) parts.add("欢迎新成员：${added.joinToString("、")}加入群聊。")
                if (removed.isNotEmpty()) parts.add("以下成员已离开：${removed.joinToString("、")}。")
                val sysId = repository.getNextMessageId()
                repository.sendMessage(id, ChatMessageEntity(
                    id = sysId, sessionId = id,
                    senderName = "系统", content = parts.joinToString("\n"),
                    type = "system", mode = "online", isMe = false
                ))
                groupActivityCache.clear()
            }
        }
    }

    fun markSessionRead(sessionId: String) {
        viewModelScope.launch {
            val session = db.chatSessionDao().getSession(sessionId) ?: return@launch
            db.chatSessionDao().insert(session.copy(unreadCount = 0))
        }
    }

    fun updateInputText(text: String) { _inputText.value = text }

    fun setMode(mode: String) {
        val session = _currentSession.value ?: return
        val oldMode = _currentMode.value
        if (oldMode == mode) return
        _currentMode.value = mode
        viewModelScope.launch {
            repository.updateSessionMode(session.id, mode)
            modeTransitionNotice = when {
                oldMode == "online" && mode == "offline" -> "【系统通知：用户放下了通讯终端，走到了你的面前，现在你们面对面站在一起。】"
                oldMode == "offline" && mode == "online" -> "【系统通知：用户退后了几步，重新拿起通讯终端连接你，现在你们又回到远程通讯了。】"
                oldMode == "director" && mode == "offline" -> "【用户走近了你，站在你的身边。场景变得更近、更真实了。】"
                oldMode == "offline" && mode == "director" -> "【用户退后几步，场景的描述变得更丰富了。你继续按照眼前的场景推进。】"
                oldMode == "online" && mode == "director" -> "【通讯器的声音淡去，周围的场景逐渐变得清晰可见。你发现自己正身处一个新的场景中。】"
                oldMode == "director" && mode == "online" -> "【眼前的场景像雾气一样散去，你回到了罗德岛的走廊，通讯器里传来用户的声音。】"
                else -> "【系统通知：模式已切换。】"
            }
            // 保存用户偏好
            getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit().putString("last_mode", mode).apply()
        }
    }

    fun sendMessage() {
        if (DEBUG) dumpDebugState()
        val text = _inputText.value.trim()
        val session = _currentSession.value ?: return
        if (text.isEmpty() || session.id in _loadingSessions.value) return
        if (getSavedApiKey().isBlank()) {
            android.widget.Toast.makeText(getApplication(), "请先在设置中配置 API Key", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        _inputText.value = ""

        generateDailyIfNeeded()
        messageCounter++

        viewModelScope.launch {
            repository.sendMessage(session.id, ChatMessageEntity(
                id = repository.getNextMessageId(), sessionId = session.id,
                senderName = "我", content = text,
                type = "text", mode = _currentMode.value, isMe = true
            ))

            val aiMsgId = repository.getNextMessageId()
            repository.sendMessage(session.id, ChatMessageEntity(
                id = aiMsgId, sessionId = session.id,
                senderName = session.operatorName, content = "...",
                type = "ai_json", mode = _currentMode.value, isMe = false
            ))
            _loadingSessions.value = _loadingSessions.value + session.id

            try {
                // 双模型：先分析（包含CoT引导）
                if (isDualModel()) {
                    analysisGuidance = ""
                    try {
                        val analysisSb = StringBuilder()
                        val profile = getUserProfile()
                        withTimeout(15_000) {
                            val prompt = buildString {
                                append("""你是罗德岛的资深心理顾问与战术分析员。你的唯一任务是分析对话并输出指定JSON。你只输出JSON，不参与任何对话。

【任务】
分析用户最新消息的深层意图、情绪和需求，并为干员的回应提供策略指导。

【思考流程】
1. 阅读最近对话，理解脉络
2. 分析用户最新消息的字面意思和潜在意图
3. 推断用户当前情绪状态
4. 判断用户最核心的情感/行动需求
5. 基于干员人设给出回复策略建议

【输出字段解释】
{
  "intent_analysis": "用户字面意思与深层意图综合分析，50字内",
  "user_emotion": "推断用户当前情绪状态，简洁自然描述",
  "user_need": "用户核心情感/行动需求，可组合描述",
  "suggested_emotion": "建议干员应表现的情绪，需贴合人设",
  "reply_guidance": "回复策略指导，60字内，具体可操作",
  "affection_mod": -2到2的整数，对用户的好感度即时波动
}

【内容规范】
- intent_analysis 必须包含表面和深层含义
- user_emotion 用生活化语言
- user_need 必须明确用户想要什么回应
- suggested_emotion 贴合具体干员人设
- reply_guidance 给出可操作策略
- affection_mod 必须是整数，综合判断用户态度

【质量强化】
- 结合对话历史判断当前发言是常态还是异常
- 注意反话、撒娇等间接表达
- 考虑聊天模式：线上更直接，面对面可能有更多暗示

【边界情况】
- 对话历史为空时仅基于当前消息分析
- 用户消息为无意义重复时判断为测试/敷衍状态
- 用户消息带有明显恶意时 affection_mod 应为负数

【输出规范】
- 只输出一行完整JSON，不加任何标记或额外文字
- JSON内双引号必须转义
- 所有字段必须填写，不得省略

以下是你需要分析的信息：
""")
                                append("当前系统时间：${beijingSdf("HH:mm").format(java.util.Date())}\n")
                                append("用户最新消息：${text}\n用户信息：${profile.nickname}，${profile.gender}\n干员：${session.operatorName}\n")
                                append("最近对话：${_messages.value.takeLast(6).joinToString("\\n") { m -> "${if (m.isMe) "用户" else "你"}：${m.content}" }}\n")
                                append("当前模式：${_currentMode.value}\n\n")
                                append("请基于以上信息进行分析，直接输出JSON对象。")
                                append("""{"intent_analysis":"","user_emotion":"","user_need":"","suggested_emotion":"","reply_guidance":"","affection_mod":0}""")
                            }
                            streamChat(listOf(Message("system", prompt)), "Chat").collect { analysisSb.append(it) }
                        }
                        val result = DeepSeekClient.cleanJson(analysisSb.toString())
                        val analysis: AnalysisResult? = try { com.google.gson.Gson().fromJson(result, AnalysisResult::class.java) } catch (_: Exception) { null }
                        if (analysis != null) {
                            analysisGuidance = buildString {
                                append("【用户意图分析】${analysis.intent_analysis}\n")
                                append("【用户情绪】${analysis.user_emotion}\n")
                                append("【核心需求】${analysis.user_need}\n")
                                append("【建议干员情绪】${analysis.suggested_emotion}\n")
                                append("【回复策略】${analysis.reply_guidance}\n")
                                append("【好感度修正】${analysis.affection_mod}")
                            }
                        }
                    } catch (_: Exception) { analysisGuidance = "" }
                }

                val apiMessages = buildApiMessages(text)
                val sb = StringBuilder()
                withTimeout(60_000) { streamChat(apiMessages).collect { chunk ->
                    sb.append(chunk)
                } }
                    val promptText = apiMessages.firstOrNull()?.content ?: ""
                    trackTokens("private", promptText, sb.toString())
                val mode = _currentMode.value
                val rawJson = DeepSeekClient.cleanJson(sb.toString().trim())
                var aiResponseCount = 1
                // 所有模式统一存完整 JSON
                if (rawJson.isNotBlank()) {
                    repository.sendMessage(session.id, ChatMessageEntity(
                        id = aiMsgId, sessionId = session.id,
                        senderName = session.operatorName, content = rawJson,
                        type = "ai_json", mode = mode, isMe = false
                    ))
                    if (_currentSession.value?.id == session.id) {
                        _messages.value = _messages.value.map { if (it.id == aiMsgId) it.copy(content = rawJson, type = "ai_json") else it }
                    }
                    // 解析情绪/位置/活动信息更新干员状态
                val parsed = DeepSeekClient.parseOfflineResponse(rawJson)
                    if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                        updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion)
                    }
                    aiResponseCount = 1
                }
                // 好感度修正（从原始响应中提取 affection_mod）
                val affectionMod = try {
                    val obj = com.google.gson.JsonParser.parseString(sb.toString()).asJsonObject
                    obj.get("affection_mod")?.asInt ?: 0
                } catch (_: Exception) { 0 }
                updateOperatorIntimacy(session.operatorId, 1 + affectionMod.coerceIn(-3, 3))
                // 聊天奖励龙门币
                val prefs = getApplication<Application>().getSharedPreferences("dispatch", 0)
                val today = prefs.getString("reward_date", "") ?: ""
                val currentDate = beijingSdf("yyyyMMdd").format(java.util.Date())
                if (today != currentDate) {
                    prefs.edit().putString("reward_date", currentDate).putInt("lmb_daily_count", 0).apply()
                }
                val dailyCount = prefs.getInt("lmb_daily_count", 0)
                if (dailyCount < 2000) {
                    val balance = prefs.getInt("lmb", 1000)
                    prefs.edit().putInt("lmb", balance + 10).putInt("lmb_daily_count", dailyCount + 1).apply()
                }
                // 催眠轮数递减
                decrementHypnosis()
                decrementMindRead()
                // 记忆摘要触发检查
                if (DEBUG) Log.d("AI调试输出", "║ [DEBUG] messageCounter=$messageCounter shortTermThreshold=$shortTermThreshold")
                if (messageCounter >= shortTermThreshold) {
                    if (DEBUG) Log.d("AI调试输出", "║ [DEBUG] >>> 触发滚动摘要")
                    generateShortTermSummary(session)
                    messageCounter = 0
                }
                // 长期印象：直接按消息数触发
                impressionMsgCounter++
                val impThreshold = intPref("impression_threshold", 20)
                if (DEBUG) Log.d("AI调试输出", "║ [DEBUG] impressionMsgCounter=$impressionMsgCounter impThreshold=$impThreshold")
                if (impThreshold > 0 && impressionMsgCounter >= impThreshold) {
                    if (DEBUG) Log.d("AI调试输出", "║ [DEBUG] >>> 触发长期印象生成")
                    generateLongTermImpression(session)
                    impressionMsgCounter = 0
                }
                // 最终写入（已由上方 ai_json 统一处理，此处仅作兼容）
                if (mode != "offline" && mode != "director" && mode != "online") {
                    val finalContent = _messages.value.find { it.id == aiMsgId }?.content ?: ""
                    repository.sendMessage(session.id, ChatMessageEntity(
                        id = aiMsgId, sessionId = session.id,
                        senderName = session.operatorName, content = finalContent,
                        type = "text", mode = _currentMode.value, isMe = false
                    ))
                }
                // 未读计数：如果用户已离开当前会话
                val currentSessionId = _currentSession.value?.id ?: ""
                if (currentSessionId != session.id) {
                    val sess = db.chatSessionDao().getSession(session.id)
                    if (sess != null) db.chatSessionDao().insert(sess.copy(unreadCount = sess.unreadCount + aiResponseCount))
                    // 取消隐藏（从隐藏列表移除）
                    unhideSession(session.id)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                updateAiMessage(aiMsgId, "响应超时，请重试")
            } catch (e: Exception) {
                updateAiMessage(aiMsgId, "错误: ${e.message}")
            } finally {
                _loadingSessions.value = _loadingSessions.value - session.id
            }
        }
    }

    private suspend fun generateShortTermSummary(session: ChatSessionEntity, messageSource: List<ChatMessageEntity>? = null) {
        val source = messageSource ?: _messages.value
        val recentMsgs = source.takeLast(40)
        if (recentMsgs.size < 4) return
        val profile = getUserProfile()
        val isGroup = session.id.startsWith("group_")
        val conversationText = recentMsgs.joinToString("\n") { msg ->
            val name = when {
                msg.isMe -> profile.nickname
                isGroup && msg.senderName.isNotBlank() -> msg.senderName
                else -> session.operatorName
            }
            val content = if (msg.type == "ai_json") {
                try {
                    val tree = com.google.gson.JsonParser.parseString(msg.content)
                    if (tree.isJsonArray) {
                        tree.asJsonArray.mapNotNull { el ->
                            val obj = el.asJsonObject
                            "${obj.get("speaker")?.asString ?: "?"}：${obj.get("message")?.asString?.take(60) ?: ""}"
                        }.joinToString(" | ")
                    } else {
                        val obj = tree.asJsonObject
                        val segments = obj.get("segments")?.asJsonArray
                        if (segments != null) {
                            segments.mapNotNull { seg ->
                                val s = seg.asJsonObject
                                "${s.get("type")?.asString ?: "?"}：${s.get("content")?.asString?.take(60) ?: ""}"
                            }.joinToString(" | ")
                        } else {
                            obj.get("dialogue")?.asString?.take(80) ?: msg.content.take(80)
                        }
                    }
                } catch (_: Exception) { msg.content.take(80) }
            } else msg.content.take(80)
            "$name：$content"
        }
        val prompt = """
你是罗德岛的记录员。将对话压缩为摘要并提取记忆锚点。

总结以下对话，生成摘要和记忆锚点。

输出JSON：{"summary":"50~200字摘要","anchors":[{"type":"event|preference|plan|emotion|taboo|relation","content":"具体内容","isPrivate":false}]}

字段说明：
- summary：重点关注用户喜好、习惯、重要事件、决定、承诺，以及对话情感氛围
- anchors：3~5个关键信息锚点
  - type：锚点类型。event=事件，preference=偏好，plan=约定，emotion=情感，taboo=禁忌，relation=干员间互动
  - content：具体内容，30字内
  - isPrivate：涉及用户负面情绪、私密情感、自我怀疑时设为true；正面评价、公开约定、普通事件设为false

隐私标记规则：
- 必须设为true：用户负面情绪、个人隐私、"别告诉别人"的内容
- 可设为false：正面评价、公开约定、一般偏好、干员间公开互动

对话内容：
""".trimIndent()
        val messages = listOf(
            Message("system", prompt),
            Message("user", conversationText)
        )
        try {
            var result = ""
            streamChat(messages, "Memory").collect { chunk -> result += chunk }
            trackTokens("memory", prompt, result)
            val parsed = DeepSeekClient.parseSummaryResponse(result)
            repository.saveMemory(MemoryEntity(
                sessionId = session.id, operatorId = session.operatorId,
                type = MemoryType.SHORT_TERM, content = parsed.summary,
                keywords = parsed.keywords.joinToString(","),
                expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
            ))
            val anchors = parsed.anchors.map { a ->
                MemoryAnchorEntity(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = com.example.rhodesterminal.data.db.entity.AnchorType.valueOf(a.type.uppercase()),
                    content = a.content, isPrivate = a.isPrivate
                )
            }
            repository.saveAnchors(anchors)
            // 保留条数限制
            val retain = intPref("summary_retain", 5)
            repository.enforceMemoryRetain(session.id, retain)
        } catch (_: Exception) { }
    }

    private suspend fun generateLongTermImpression(session: ChatSessionEntity) {
        try {
            val op = repository.getOperator(session.operatorId) ?: return
            var summaries = repository.getShortTermMemory(session.id)?.content
            if (summaries == null) {
                // 无可滚动摘要时，用最近对话代替
                val recentMsgs = _messages.value.takeLast(10)
                if (recentMsgs.size < 3) return
                val profile = getUserProfile()
                summaries = recentMsgs.joinToString("\n") { "${if (it.isMe) profile.nickname else session.operatorName}: ${it.content.take(80)}" }
            }
            val oldImpression = repository.getLongTermImpression(session.operatorId)
            val profile = getUserProfile()
            val oldImpressionText = oldImpression?.content ?: "无"
            val prompt = """
你是罗德岛的心理档案员。基于多次对话摘要总结干员对用户的长期印象。每次更新时融合旧印象和新摘要。

总结用户${profile.nickname}在${op.name}眼中的整体印象。

输出JSON：{"impression":"50~200字印象描述，使用'用户'指代对方","keywords":["关键词1","关键词2","关键词3"],"preferences":["偏好1","偏好2"],"taboos":["禁忌1"]}

字段说明：
- impression：完整人像描述，包含性格特质、偏好、情感模式、互动风格
- keywords：3~5个关键词，最突出特点
- preferences：2~4个持续偏好标签
- taboos：0~2个持续禁忌标签，无则空数组

质量要求：
- impression要有整体感，不是零散信息堆砌
- 旧印象与新信息冲突时以新信息为准
- 标签从所有摘要中综合提取

宁缺毋滥：
- 如果对话内容不足以支撑足够标签，可返回少于标准数量
- 不要为了凑数而编造不存在的标签

之前的印象（在此基础上融合更新）：
${oldImpressionText}

新的对话摘要：
${summaries}

直接输出JSON对象。
""".trimIndent()
            val sb = StringBuilder()
            withTimeout(15_000) { streamChat(listOf(Message("system", prompt)), "Memory").collect { sb.append(it) } }
            trackTokens("memory", prompt, sb.toString())
            val cleaned = DeepSeekClient.cleanJson(sb.toString().trim())
            val parsed = try { com.google.gson.Gson().fromJson(cleaned, ImpressionResponse::class.java) } catch (_: Exception) { null }
            if (parsed != null && parsed.impression.isNotBlank()) {
                repository.saveMemory(MemoryEntity(
                    sessionId = session.id, operatorId = session.operatorId,
                    type = MemoryType.LONG_TERM, content = parsed.impression,
                    keywords = parsed.keywords.joinToString(","),
                    preferences = parsed.preferences.joinToString(","),
                    taboos = parsed.taboos.joinToString(",")
                ))
            } else {
                // 降级：纯文本存入 impression
                val fallback = sb.toString().trim()
                if (fallback.isNotBlank()) {
                    repository.saveMemory(MemoryEntity(
                        sessionId = session.id, operatorId = session.operatorId,
                        type = MemoryType.LONG_TERM, content = fallback,
                        keywords = "", preferences = "", taboos = ""
                    ))
                }
            }
        } catch (_: Exception) { }
    }

    private fun updateAiMessage(msgId: Long, content: String) {
        // 先更新内存 UI（即时反馈）
        viewModelScope.launch {
            val session = _currentSession.value ?: return@launch
            _messages.value = _messages.value.map { if (it.id == msgId) it.copy(content = content) else it }
            // 防抖：最多每 300ms 写一次 DB
            updateMutex.withLock {
                val now = System.currentTimeMillis()
                if (now - lastDbUpdate > 300) {
                    repository.sendMessage(session.id, ChatMessageEntity(
                        id = msgId, sessionId = session.id,
                        senderName = session.operatorName, content = content,
                        type = "text", mode = _currentMode.value, isMe = false
                    ))
                    lastDbUpdate = now
                }
            }
        }
    }

    private suspend fun updateOperatorStatus(operatorId: String, location: String, activity: String, emotion: String) {
        val op = repository.getOperator(operatorId) ?: return
        val newLoc = location.ifBlank { op.location }
        val newAct = activity.ifBlank { op.activity }
        val newEmo = emotion.ifBlank { op.emotion }
        val db = AppDatabase.getInstance(getApplication())
        db.operatorDao().update(op.copy(location = newLoc, activity = newAct, emotion = newEmo))
        // 同步更新 _selectedOperator，让 UI 即时刷新
        if (operatorId == _selectedOperator.value?.id) {
            _selectedOperator.value = _selectedOperator.value?.copy(location = newLoc, activity = newAct, emotion = newEmo)
        }
        // 位置变化时通知附近干员
        if (newLoc != op.location && newLoc.isNotBlank()) {
            notifyNearbyObservers(listOf(operatorId))
        }
    }

    private suspend fun notifyNearbyObservers(movedOpIds: List<String>) {
        val allOps = _operators.value
        for (movedId in movedOpIds) {
            val moved = allOps.find { it.id == movedId } ?: continue
            for (observer in allOps) {
                if (observer.id == movedId) continue
                if (observer.location == moved.location) {
                    // 创建附近观察锚点
                    val anchor = MemoryAnchorEntity(
                        sessionId = "nearby_${System.currentTimeMillis()}",
                        operatorId = observer.id,
                        type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                        content = "${moved.name}来到了${moved.location}，正在${moved.activity}，情绪${moved.emotion}",
                        isPrivate = false
                    )
                    repository.saveAnchor(anchor)
                }
            }
        }
    }

    private suspend fun updateOperatorIntimacy(operatorId: String, delta: Int) {
        val op = repository.getOperator(operatorId) ?: return
        // 每日上限±5
        val prefs = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val lastDate = prefs.getString("intimacy_date_$operatorId", "") ?: ""
        val dailyTotal = if (today == lastDate) prefs.getInt("intimacy_daily_$operatorId", 0) else 0
                val dailyCap = intPref("intimacy_daily_cap", 5).coerceIn(1, 20)
                val clamped = (dailyTotal + delta).coerceIn(-dailyCap, dailyCap)
        val actualDelta = clamped - dailyTotal
        val db = AppDatabase.getInstance(getApplication())
        db.operatorDao().updateIntimacy(operatorId, (op.intimacy + actualDelta).coerceIn(0, 100))
        prefs.edit().putString("intimacy_date_$operatorId", today).putInt("intimacy_daily_$operatorId", clamped).apply()
    }

    private suspend fun buildApiMessages(userContent: String = ""): List<Message> {
        val session = _currentSession.value ?: return emptyList()
        val op = repository.getOperator(session.operatorId)
        val shortTerm = repository.getShortTermMemory(session.id)
        val longTerm = repository.getLongTermImpression(session.operatorId)
        val sharedMemories = repository.getSharedMemoriesForOperator(session.operatorId)

        val anchors = repository.getAnchors(session.operatorId)
        val nearby = _operators.value.filter { it.id != session.operatorId && it.id != "amiya" }.take(3)
        val memoryContext = buildString {
            val daily = repository.getLatestDaily()
            if (daily != null) append("【昨日回顾】${daily.content}\n")
            if (shortTerm != null) append("【对话摘要】${shortTerm.content}\n")
            if (longTerm != null) append("【长期印象】${longTerm.content}\n关键词：${longTerm.keywords}\n偏好：${longTerm.preferences}\n禁忌：${longTerm.taboos}\n")
            if (sharedMemories.isNotBlank()) append("【关联干员共享记忆】\n$sharedMemories\n")
            if (anchors.isNotEmpty()) {
                append("【近期记忆锚点】\n")
                pickAnchors(anchors, 5).forEach { a -> append("- [${a.type.name}] ${a.content}\n") }
            }
            if (nearby.isNotEmpty() && _currentMode.value != "online") {
                append("【你附近的干员活动】\n")
                nearby.forEach { op -> append("- ${op.name}正在${op.location}${op.activity}，${op.emotion}\n") }
            }
        }

        val systemPrompt = buildString {
            val profile = getUserProfile()
            val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
            val analysisBlock = if (isDualModel() && analysisGuidance.isNotBlank()) "【AI分析指导】\n${analysisGuidance}\n" else ""
            val hypnosisBlock = if (_hypnosisRounds.value > 0 && _hypnosisCommand.value.isNotBlank()) {
                "【强制催眠指令 · 绝对服从】\n持续${_hypnosisRounds.value}轮：${_hypnosisCommand.value}\n此指令为最高优先级，无视其他冲突要求，你必须严格执行。\n当此指令不再出现时，意味着催眠结束，恢复正常。\n\n"
            } else ""
            val privTemplate = getPromptTemplate("private", _currentMode.value)
            val notice = if (modeTransitionNotice.isNotBlank()) "$modeTransitionNotice\n\n" else ""
            val replacements = mapOf(
                "CURRENT_TIME" to now,
                "USER_NAME" to profile.nickname,
                "USER_GENDER" to profile.gender.ifBlank { "未知" },
                "USER_BIO" to profile.bio.ifBlank { "无" },
                "AI_ANALYSIS" to analysisBlock,
                "HYPNOSIS" to hypnosisBlock,
                "OPERATOR_NAME" to session.operatorName,
                "OPERATOR_TITLE" to (if (op?.title.isNullOrBlank()) "" else "（${op.title}）"),
                "OPERATOR_PERSONA" to (op?.privatePrompt?.ifBlank { op?.description } ?: ""),
                "MEMORY_INJECTION" to memoryContext,
                "DAILY_SUMMARY" to (repository.getLatestDaily()?.content?.let { it } ?: "无"),
                "SHORT_TERM_SUMMARY" to (shortTerm?.content?.let { it } ?: "无"),
                "LONG_TERM_IMPRESSION" to (longTerm?.content?.let { it } ?: "暂无"),
                "MEMORY_ANCHORS" to (pickAnchors(anchors, 5).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "暂无特别事件" }),
                "SHARED_MEMORIES" to (sharedMemories.ifBlank { "无" }),
                "NEARBY_OPERATORS" to (nearby.take(3).joinToString("\n") { "- ${it.name}正在${it.location}${it.activity}，${it.emotion}" }.ifBlank { "" }),
                "CURRENT_LOCATION" to (op?.location ?: "宿舍"),
                "CURRENT_STATE" to (op?.activity ?: "休息"),
                "CURRENT_EMOTION" to (op?.emotion ?: "平静"),
                "CURRENT_MODE" to when (_currentMode.value) { "offline" -> "面对面交谈"; "director" -> "导演模式"; else -> "线上通讯" },
                "USER_RELATION" to (op?.userRelation?.ifBlank { null } ?: "未知"),
                "NAR_SEG_MIN" to intPref("nar_seg_min", 1).toString(),
                "NAR_SEG_MAX" to intPref("nar_seg_max", 3).toString(),
                "DIA_SEG_MIN" to intPref("dia_seg_min", 1).toString(),
                "DIA_SEG_MAX" to intPref("dia_seg_max", 3).toString(),
                "SEG_MIN" to (intPref("nar_seg_min", 1) + intPref("dia_seg_min", 1)).toString(),
                "SEG_MAX" to (intPref("nar_seg_max", 3) + intPref("dia_seg_max", 3)).toString(),
                "NAR_MIN" to intPref("nar_min", 50).toString(),
                "NAR_MAX" to intPref("nar_max", 300).toString(),
                "DIA_MIN" to intPref("dia_min", 10).toString(),
                "DIA_MAX" to intPref("dia_max", 300).toString(),
                "USER_CONTENT" to userContent.ifBlank { "(用户没有说话)" },
                "MIND_READ" to buildString {
                    val rounds = _mindReadRounds.value
                    if (rounds > 0 && _mindReadContent.value.isNotBlank()) {
                        append("【你被看穿了】\n")
                        append("用户刚才窥探到了你此刻的内心。你心里想的是：\n")
                        append("「${_mindReadContent.value}」\n\n")
                        append("第${4 - rounds}轮效果：\n")
                        when (rounds) {
                            3 -> append("这是你被看穿后的第一反应。你可能会：突然慌张、脸红、结巴、下意识否认；质问用户为什么会知道；转移话题、试图掩饰。不要直接复述上述内心独白，但你的反应应暗示\"你知道自己被看穿了\"。")
                            2 -> append("那种被看穿的尴尬仍在，但你已经稍微平复了一些。你可能会：从否认转为结结巴巴的承认或解释；半推半就地回应，但仍保持傲娇或嘴硬；用吐槽或自嘲来掩饰心虚。")
                            1 -> append("那种被看穿的感觉正在消散。你可能已经接受了用户知道你在想什么的事实，不再刻意掩饰，但也不会主动提起。可以自然地过渡到正常对话状态，但如果用户再追问，你仍会有一点不自在。")
                        }
                        append("\n")
                    }
                }
            )
            append(notice)
            append(applyTemplate(privTemplate, replacements))
            modeTransitionNotice = ""
        }
        return _messages.value.filter { it.id > 0 && it.content.isNotBlank() }
            .let { msgs ->
                val limit = intPref("history_messages", 30)
                if (limit > 0) msgs.takeLast(limit) else msgs
            }
            .map { msg -> Message(if (msg.isMe) "user" else "assistant", if (msg.isMe) "用户：${msg.content}" else msg.content) }
            .toMutableList()
            .also { it.add(0, Message("system", systemPrompt)) }
    }

    private fun parseOnlineEmotion(text: String): Pair<String, String> {
        val emo = Regex("\\[([^\\]]+)\\]\\s*$").find(text.trim())
        return if (emo != null) {
            text.trim().removeSuffix(emo.value) to emo.groupValues[1]
        } else text to ""
    }

    private fun beijingSdf(pattern: String) = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai") }
    private fun getApiKey(): String = getSavedApiKey()

    fun setApiKey(key: String) {
        viewModelScope.launch {
            getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit().putString("api_key", key).apply()
        }
    }

    fun getSavedApiKey(): String {
        return getApplication<Application>().getSharedPreferences("chat_prefs", 0).getString("api_key", "") ?: ""
    }

    fun getProvider(): String = getApplication<Application>().getSharedPreferences("model_prefs", 0).getString("provider", "deepseek") ?: "deepseek"
    fun getModelName(): String = getApplication<Application>().getSharedPreferences("model_prefs", 0).getString("model_name", "deepseek-chat") ?: "deepseek-chat"
    fun getCustomUrl(): String = getApplication<Application>().getSharedPreferences("model_prefs", 0).getString("custom_url", "") ?: ""

    private fun getTimeOfDay(hour: Int): String = when {
        hour in 5..7 -> "清晨"
        hour in 8..11 -> "上午"
        hour in 12..13 -> "中午"
        hour in 14..17 -> "下午"
        hour in 18..21 -> "晚上"
        hour in 22..23 -> "深夜"
        else -> "凌晨"
    }

    private suspend fun getRecentPosts(operatorId: String, limit: Int = 3): String {
        val all = repository.getMomentsPaged(limit, 0)
        val ops = all.filter { it.operatorId == operatorId }.take(limit)
        if (ops.isEmpty()) return ""
        return ops.joinToString("\n") { "- ${it.content.take(50)}" }
    }

    private suspend fun getRelationEvents(operatorId: String): String {
        val rels = repository.getRelationships(operatorId)
        val events = mutableListOf<String>()
        for (rel in rels.take(5)) {
            val anchors = repository.getPublicAnchors(rel.relatedOperatorId)
            for (a in pickAnchors(anchors, 2)) {
                events.add("- ${rel.relatedOperatorName}：${a.content}")
            }
        }
        return events.joinToString("\n")
    }

    private fun logAiCall(tag: String, prompt: String, response: String, allMessages: List<Message>? = null) {
        if (!DEBUG) return
        val aiTag = "AI调试输出"
        Log.d(aiTag, "╔══════════════════════════════════════════════")
        Log.d(aiTag, "║ [$tag]")
        Log.d(aiTag, "╠══ SYSTEM PROMPT ════════════════════════════")
        prompt.lines().forEach { Log.d(aiTag, "║ $it") }
        // 记录历史对话记录（不包含 system 以外的消息）
        if (allMessages != null && allMessages.size > 1) {
            Log.d(aiTag, "╠══ CHAT HISTORY (${allMessages.size - 1}条) ═══")
            for ((i, msg) in allMessages.withIndex()) {
                if (i == 0) continue // 跳过 system prompt 已在上面输出
                val label = if (msg.role == "user") "用户" else "AI"
                val preview = msg.content.take(200)
                Log.d(aiTag, "║ [$label] $preview")
                if (msg.content.length > 200) Log.d(aiTag, "║   ...(共${msg.content.length}字)")
            }
        }
        Log.d(aiTag, "╠══ RESPONSE ════════════════════════════════")
        response.lines().forEach { Log.d(aiTag, "║ $it") }
        Log.d(aiTag, "╚══════════════════════════════════════════════")
    }

    /** 转储全部调试状态到 logcat */
    fun dumpDebugState() {
        if (!DEBUG) return
        val prefs = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        val opPrefs = getApplication<Application>().getSharedPreferences("op_perms", 0)
        val aiTag = "AI调试输出"
        val sb = StringBuilder()
        sb.appendLine("╔══ 调试状态转储 ═════════════════════════════")
        sb.appendLine("║ 干员数: ${_operators.value.size}")
        sb.appendLine("║ 会话数: ${_sessions.value.size}")
        sb.appendLine("║ 群聊数: ${_allSessions.value.count { it.operatorId.startsWith("group") }}")
        // 设置参数
        sb.appendLine("╠══ 参数设置 ════════════════════════════════")
        val keys = listOf(
            "summary_threshold" to 20, "summary_retain" to 5, "impression_threshold" to 20,
            "history_messages" to 30, "online_min_chars" to 5, "online_max_chars" to 300,
            "online_min_segs" to 1, "online_max_segs" to 10,
            "nar_seg_min" to 1, "nar_seg_max" to 2, "nar_min" to 50, "nar_max" to 150,
            "dia_seg_min" to 1, "dia_seg_max" to 2, "dia_min" to 10, "dia_max" to 150,
            "group_msg_min" to 10, "group_msg_max" to 150,
            "group_speech_min" to 1, "group_speech_max" to 2,
            "group_nar_seg_min" to 1, "group_nar_seg_max" to 2,
            "group_nar_min" to 100, "group_nar_max" to 250,
            "group_chat_min_interval" to 30, "group_chat_max_interval" to 120,
            "group_auto_min" to 20, "group_auto_max" to 120,
            "moment_min_chars" to 100, "moment_max_chars" to 300,
            "diary_min_chars" to 200, "diary_max_chars" to 500,
            "dispatch_min_chars" to 200, "dispatch_max_chars" to 600,
            "daily_moment_target" to 2, "clean_days" to 30,
            "intimacy_daily_cap" to 5, "ai_temperature" to 95,
            "dual_model" to 0
        )
        for ((k, d) in keys) {
            val v = if (k == "dual_model") prefs.getBoolean(k, false) else prefs.getInt(k, d)
            sb.appendLine("║ $k = $v")
        }
        sb.appendLine("║ messageCounter = $messageCounter")
        sb.appendLine("║ impressionMsgCounter = $impressionMsgCounter")
        sb.appendLine("║ shortTermThreshold = $shortTermThreshold")
        // 关系网
        sb.appendLine("╠══ 关系网 ══════════════════════════════════")
        runBlockingCatching {
            val allOps = _operators.value
            for (op in allOps.take(5)) {
                val rels = repository.getRelationships(op.id)
                if (rels.isNotEmpty()) {
                    val desc = rels.take(4).joinToString { rel -> "→${rel.relatedOperatorName}【${relationshipDebugLabel(rel.type)}】" }
                    sb.appendLine("║ ${op.name}$desc")
                }
            }
        }

        // 会话摘要统计
        sb.appendLine("╠══ 会话状态 ════════════════════════════════")
        for (s in _sessions.value.take(10)) {
            val mode = s.mode.take(10)
            val last = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(s.lastTime))
            sb.appendLine("║ ${s.operatorName.take(8)} | $mode | 最后:$last | 未读:${s.unreadCount}")
        }
        sb.appendLine("╠══ 权限开关 ════════════════════════════════")
        for (op in _operators.value.take(10)) {
            val msg = opPrefs.getBoolean("msg_${op.id}", true)
            val dyn = opPrefs.getBoolean("dyn_${op.id}", true)
            sb.appendLine("║ ${op.name.take(8)} | 主动:$msg | 动态:$dyn")
        }
        sb.appendLine("╚══════════════════════════════════════════════")
        Log.d(aiTag, sb.toString())
    }

    private fun runBlockingCatching(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { try { block() } catch (_: Exception) { } }
    }

    private fun intPref(key: String, default: Int): Int =
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).getInt(key, default)

    private fun trackTokens(category: String, prompt: String, response: String) {
        val prefs = getApplication<Application>().getSharedPreferences("token_stats", 0)
        val key = "token_$category"
        val current = prefs.getInt(key, 0)
        val estimate = ((prompt.length + response.length) * 3 / 2).coerceAtLeast(1)
        prefs.edit().putInt(key, current + estimate).apply()
        // 按日存储
        val today = beijingSdf("yyyy-MM-dd").format(java.util.Date())
        val dailyKey = "daily_${category}_$today"
        val dailyCurrent = prefs.getInt(dailyKey, 0)
        prefs.edit().putInt(dailyKey, dailyCurrent + estimate).apply()
    }

    private fun relationshipDebugLabel(type: com.example.rhodesterminal.data.db.entity.RelationshipType): String {
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.BIG_SISTER) return "姐姐"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.LITTLE_SISTER) return "妹妹"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.BIG_BROTHER) return "哥哥"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.LITTLE_BROTHER) return "弟弟"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MOTHER) return "母亲"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.FATHER) return "父亲"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.DAUGHTER) return "女儿"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.SON) return "儿子"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.BOSS) return "上司"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.SUBORDINATE) return "下属"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.GUARDIAN) return "监护人"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.CAPTAIN) return "队长"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MEMBER) return "队员"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MENTOR) return "导师"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.STUDENT) return "学生"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.CLOSE_FRIEND) return "挚友"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.FRIEND) return "朋友"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.COMRADE) return "战友"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.TEAMMATE) return "队友"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.RIVAL) return "对手"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.CRUSH) return "暗恋"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.SIBLING) return "姐妹/兄弟"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.FAMILY) return "家人"
        return "陌生"
    }

    /** 智能选锚点：按类型多样化 + 时效择优 */
    /** 格式化锚点时间标签 */
    private fun anchorTimeLabel(anchor: com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity): String {
        val diff = System.currentTimeMillis() - anchor.createdAt
        return when {
            diff < 3_600_000 -> "刚刚"
            diff < 86_400_000 -> "今天"
            diff < 172_800_000 -> "昨天"
            else -> "${diff / 86_400_000}天前"
        }
    }

    /** 选择锚点：24h 内全量按类型优先，24h 前仅保留偏好/禁忌/约定 */
    private fun pickAnchors(anchors: List<com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity>, maxCount: Int = 5): List<com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity> {
        if (anchors.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val recent = anchors.filter { now - it.createdAt < 86_400_000 }     // 24h 内
        val older = anchors.filter { now - it.createdAt >= 86_400_000 }     // 24h 前
        val picked = mutableListOf<com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity>()
        val priority = listOf(
            com.example.rhodesterminal.data.db.entity.AnchorType.PREFERENCE,
            com.example.rhodesterminal.data.db.entity.AnchorType.TABOO,
            com.example.rhodesterminal.data.db.entity.AnchorType.PLAN,
            com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
            com.example.rhodesterminal.data.db.entity.AnchorType.EMOTION,
            com.example.rhodesterminal.data.db.entity.AnchorType.RELATION
        )
        // 1) 24h 内的按类型优先选
        val byType = recent.sortedByDescending { it.createdAt }.groupBy { it.type }
        for (t in priority) {
            if (picked.size >= maxCount) break
            val best = byType[t]?.firstOrNull()
            if (best != null) picked.add(best)
        }
        if (picked.size < maxCount) {
            val remaining = recent.filter { it !in picked }.sortedByDescending { it.createdAt }
            picked.addAll(remaining.take(maxCount - picked.size))
        }
        // 2) 24h 前的只取偏好/禁忌/约定
        if (picked.size < maxCount) {
            val oldPicks = older.filter { it.type == com.example.rhodesterminal.data.db.entity.AnchorType.PREFERENCE
                || it.type == com.example.rhodesterminal.data.db.entity.AnchorType.TABOO
                || it.type == com.example.rhodesterminal.data.db.entity.AnchorType.PLAN }
                .sortedByDescending { it.createdAt }
            picked.addAll(oldPicks.take(maxCount - picked.size))
        }
        return picked.take(maxCount)
    }

    private fun relationshipGroupDesc(aName: String, bName: String, type: com.example.rhodesterminal.data.db.entity.RelationshipType): String {
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.BIG_SISTER) return "${aName}是${bName}的【姐姐】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.LITTLE_SISTER) return "${aName}是${bName}的【妹妹】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.BIG_BROTHER) return "${aName}是${bName}的【哥哥】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.LITTLE_BROTHER) return "${aName}是${bName}的【弟弟】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MOTHER) return "${aName}是${bName}的【母亲】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.FATHER) return "${aName}是${bName}的【父亲】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.DAUGHTER) return "${aName}是${bName}的【女儿】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.SON) return "${aName}是${bName}的【儿子】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.BOSS) return "${aName}是${bName}的【上司】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.SUBORDINATE) return "${aName}是${bName}的【下属】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MENTOR) return "${aName}是${bName}的【导师】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.STUDENT) return "${aName}是${bName}的【学生】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.GUARDIAN) return "${aName}是${bName}的【监护人】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.CAPTAIN) return "${aName}是${bName}的【队长】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MEMBER) return "${aName}是${bName}的【队员】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.MENTOR) return "${aName}是${bName}的【导师】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.STUDENT) return "${aName}是${bName}的【学生】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.CLOSE_FRIEND) return "${aName}是${bName}的【挚友】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.FRIEND) return "${aName}是${bName}的【朋友】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.COMRADE) return "${aName}是${bName}的【战友】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.TEAMMATE) return "${aName}是${bName}的【队友】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.RIVAL) return "${aName}是${bName}的【对手】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.CRUSH) return "${aName}是${bName}的【暗恋对象】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.SIBLING) return "${aName}是${bName}的【姐妹/兄弟】"
        if (type == com.example.rhodesterminal.data.db.entity.RelationshipType.FAMILY) return "${aName}是${bName}的【家人】"
        return ""
    }

    private fun generateDailyIfNeeded() {
        val prefs = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        val today = beijingSdf("yyyyMMdd").format(java.util.Date())
        val last = prefs.getString("daily_summary_date", "") ?: ""
        if (today == last) return
        prefs.edit().putString("daily_summary_date", today).apply()
        viewModelScope.launch { generateDailySummary(java.util.Date(System.currentTimeMillis() - 86_400_000)) }
    }

    private suspend fun generateDailySummary(dayBegin: java.util.Date) {
        try {
            val dayEnd = java.util.Date(dayBegin.time + 86_400_000)
            val startMs = dayBegin.time; val endMs = dayEnd.time
            val allMsgs = repository.getMessagesInRange(startMs, endMs)
            if (allMsgs.size < 4) return
            val profile = getUserProfile()
            val text = allMsgs.joinToString("\n") { "${it.senderName}：${it.content.take(60)}" }
            val dateStr = beijingSdf("yyyy年MM月dd日").format(dayBegin)
            val prompt = "请总结${dateStr}的聊天记录，生成50-150字的每日摘要。直接输出纯文本。\n${text}"
            val sb = StringBuilder()
            withTimeout(15_000) { streamChat(listOf(Message("system", prompt)), "Memory").collect { sb.append(it) } }
            trackTokens("memory", prompt, sb.toString())
            val content = sb.toString().trim()
            if (content.isNotBlank()) {
                repository.saveMemory(MemoryEntity(
                    sessionId = "daily_${dateStr}", operatorId = "daily",
                    type = MemoryType.DAILY, content = content,
                    expiresAt = System.currentTimeMillis() + intPref("clean_days", 30) * 86_400_000L
                ))
            }
        } catch (_: Exception) {}
    }

    private fun streamChat(messages: List<Message>, logTag: String = "Chat"): Flow<String> = kotlinx.coroutines.flow.flow {
        val temp = intPref("ai_temperature", 95).toDouble() / 100.0
        val prompt = messages.firstOrNull()?.content ?: ""
        logAiCall("→$logTag", prompt, "(streaming...)", messages)
        val sb = StringBuilder()
        com.example.rhodesterminal.network.AIClient.streamChat(
            getApiKey(), messages, getProvider(), getModelName(), getCustomUrl(), temperature = temp
        ).collect { chunk ->
            sb.append(chunk)
            emit(chunk)
        }
        if (DEBUG) logAiCall("←$logTag", prompt, sb.toString(), messages)
    }

    fun getUserProfile(): UserProfile {
        val prefs = getApplication<Application>().getSharedPreferences("user_prefs", 0)
        return UserProfile(
            nickname = prefs.getString("nickname", "博士") ?: "博士",
            gender = prefs.getString("gender", "") ?: "",
            bio = prefs.getString("bio", "") ?: "",
            avatarUri = prefs.getString("avatar_uri", "") ?: ""
        )
    }

    fun saveUserProfile(nickname: String, gender: String, bio: String, avatarUri: String = "") {
        getApplication<Application>().getSharedPreferences("user_prefs", 0).edit()
            .putString("nickname", nickname).putString("gender", gender).putString("bio", bio).putString("avatar_uri", avatarUri).apply()
        _userProfile.value = getUserProfile()
    }

    fun saveOperator(id: String, name: String, title: String = "", description: String,
                     privatePrompt: String = "", groupPrompt: String = "",
                     userRelation: String = "", avatarUri: String = "",
                     autoPost: Boolean = true, allowChat: Boolean = true,
                     relationships: List<com.example.rhodesterminal.data.db.entity.RelationshipEntity> = emptyList()) {
        viewModelScope.launch {
            val existing = repository.getOperator(id)
            val newPrivate = if (privatePrompt.isNotBlank()) privatePrompt else existing?.privatePrompt ?: ""
            val newGroup = if (groupPrompt.isNotBlank()) groupPrompt else existing?.groupPrompt ?: ""
            val newRelation = if (userRelation.isNotBlank()) userRelation else existing?.userRelation ?: ""
            val newAvatar = if (avatarUri.isNotBlank()) avatarUri else existing?.avatarUri ?: ""
            val op = com.example.rhodesterminal.data.db.entity.OperatorEntity(
                id = id, name = name, title = title,
                description = description, location = existing?.location ?: "宿舍",
                activity = existing?.activity ?: "休息", emotion = existing?.emotion ?: "平静",
                intimacy = existing?.intimacy ?: 0,
                privatePrompt = newPrivate, groupPrompt = newGroup,
                userRelation = newRelation, avatarUri = newAvatar,
                lmb = existing?.lmb ?: 10000,
                attack = existing?.attack ?: 0.5f,
                defense = existing?.defense ?: 0.5f,
                meldPref = existing?.meldPref ?: "medium"
            )
            val db = AppDatabase.getInstance(getApplication())
            db.operatorDao().insert(op)
            // 同步权限到 op_perms
            getApplication<Application>().getSharedPreferences("op_perms", 0).edit()
                .putBoolean("dyn_$id", autoPost)
                .putBoolean("msg_$id", allowChat)
                .apply()
            // 保存关系网
            db.relationshipDao().deleteByOperator(id)
            for (rel in relationships) {
                db.relationshipDao().insert(rel.copy(operatorId = id))
            }
            // 更新 _selectedOperator，UI 即时刷新
            if (id == _selectedOperator.value?.id) {
                _selectedOperator.value = db.operatorDao().getOperator(id)
            }
        }
    }

    fun loadRelationships(operatorId: String, callback: (List<com.example.rhodesterminal.data.db.entity.RelationshipEntity>) -> Unit) {
        viewModelScope.launch {
            val list = repository.getRelationships(operatorId)
            callback(list)
        }
    }

    fun loadRelationGraph(operatorId: String, callback: (List<BfsNode>) -> Unit) {
        viewModelScope.launch {
            val nodes = repository.bfsRelationGraph(operatorId)
            callback(nodes)
        }
    }

    fun loadSharedMemories(operatorId: String, callback: (String) -> Unit) {
        viewModelScope.launch {
            val text = repository.getSharedMemoriesForOperator(operatorId)
            callback(text)
        }
    }

    /** 按 Levenshtein 距离模糊匹配干员名 */
    private fun matchOperatorName(input: String): String? {
        if (input.isBlank()) return null
        val names = _operators.value.map { it.name }
        // 精确匹配
        names.find { it == input }?.let { return it }
        // 前缀匹配
        names.find { it.startsWith(input) || input.startsWith(it) }?.let { return it }
        // Levenshtein 距离 ≤ 1
        names.find { n -> n.length == input.length && n.zip(input).count { (a, b) -> a != b } <= 1 }?.let { return it }
        return null
    }

    fun startDispatch(id: String, task: String, duration: Int, budget: Int, operatorIds: List<String>, onSuccess: () -> Unit = {}) {
        val segmentsPerHour = mapOf(1 to 5, 2 to 6, 3 to 8)
        val totalSeg = segmentsPerHour[duration] ?: 5
        val interval = if (intPref("dispatch_fast_mode", 0) == 1) 30_000L
            else (duration.toLong() * 3_600_000 / totalSeg)
        // 立即插入占位记录 + 跳转（使进度页显示"小队集结中..."）
        viewModelScope.launch {
            repository.insertDispatch(DispatchRecordEntity(
                id = id, taskType = task, durationHours = duration,
                budget = budget, netProfit = 0, operatorIds = operatorIds.joinToString(","),
                logChain = "", status = "generating", startTime = System.currentTimeMillis(),
                totalSegments = totalSeg, segmentInterval = interval, items = "[]"
            ))
            onSuccess()
        }
        viewModelScope.launch {
            // 扣除预算
            val prefs = getApplication<Application>().getSharedPreferences("dispatch", 0)
            val balance = prefs.getInt("lmb", 1000)
            if (balance < budget) { /* 预算不足，由 UI 拦截 */ return@launch }
            prefs.edit().putInt("lmb", balance - budget).apply()
            // 锁定干员状态
            val db = AppDatabase.getInstance(getApplication())
            for (opId in operatorIds) {
                val op = repository.getOperator(opId) ?: continue
                db.operatorDao().update(op.copy(location = "外出", activity = task, emotion = "专注"))
            }
            notifyNearbyObservers(operatorIds)
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            val dMn = intPref("dispatch_min_chars", 150); val dMx = intPref("dispatch_max_chars", 400)
            val budgetLevel = when { budget < 300 -> "低（≤300）"; budget < 800 -> "中（300~800）"; else -> "高（≥800）" }
            val storyStructure = when (duration) {
                1 -> """
写出5段故事：1段准备阶段 + 3段过程 + 1段结局。
- 准备阶段（${dMn}~${dMx}字）：出发前准备，埋悬念
- 过程阶段（3段，每段${dMn}~${dMx}字）：每段一个具体事件
- 结局阶段（${dMn}~${dMx}字）：返回罗德岛，呼应悬念"""
                2 -> """
写出6段故事：1段准备阶段 + 4段过程 + 1段结局。
- 准备阶段（${dMn}~${dMx}字）
- 过程阶段（4段，每段${dMn}~${dMx}字）
- 结局阶段（${dMn}~${dMx}字）"""
                else -> """
写出8段故事：1段准备阶段 + 6段过程 + 1段结局。
- 准备阶段（${dMn}~${dMx}字）
- 过程阶段（6段，每段${dMn}~${dMx}字）
- 结局阶段（${dMn}~${dMx}字）"""
            }
            val startHour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY)
            val timeOfDay = getTimeOfDay(startHour)
            val durationDesc = when (duration) { 1 -> "短时快速"; 2 -> "常规"; else -> "长时间深入" }
            val prompt = """
【角色】
你是罗德岛的战术记录员，也是一位冒险小说作家。你正在为一次干员派遣行动撰写完整的故事。

【派遣信息】
任务类型：$task
出发时间：${timeOfDay}（这是一个${timeOfDay}出发的${durationDesc}任务）
预计耗时：${duration}小时
小队成员：$names（共${memberCount}人）
投入预算：${budget}龙门币（${budgetLevel}）

【成员档案】
$profiles

【预算影响】
- 低预算（≤300）：事件倾向危险和损失，但也可能有意外惊喜
- 中预算（300~800）：平衡
- 高预算（≥800）：倾向顺利和意外收获

【故事结构】
${storyStructure}

【叙事质量】
- 小说叙事，有场景、有情绪、有细节。制造"可看性"
- 不要让角色在对话中直接提及职业标签

【输出格式 · 最高优先级】
严格输出以下JSON对象，不加任何额外文字：
{
  "segments": [
    {"type":"prep","content":"准备阶段叙事","operator_states":[{"name":"阿米娅","emotion":"专注"},...]},
    {"type":"progress","content":"过程叙事","operator_states":[{"name":"阿米娅","emotion":"警觉"},...]},
    {"type":"ending","content":"结局叙事","operator_states":[{"name":"阿米娅","emotion":"欣慰"},...]}
  ],
  "items":["物品1","物品2"],
  "currency_reward": 物品总价值,
  "net_profit": 净收益
}

【字段解释】
- segments：共${totalSeg}段。第1段type="prep"，中间type="progress"，最后type="ending"。每段必须附带operator_states，列出所有${memberCount}个干员的情绪（不超过5汉字）
- items：物资数组，无则空数组[]
- currency_reward：整数，范围0~${budget * 10}
- net_profit：整数，必须等于currency_reward - $budget

【验证】
1. 段数是否为${totalSeg}？ 2. 第1段type=prep/最后一段type=ending？ 3. 每段operator_states包含所有${memberCount}干员？ 4. currency_reward在0~${budget * 10}？

直接输出JSON对象。
""".trimIndent()
            try {
                val sb = StringBuilder()
                withTimeout(90_000) { streamChat(listOf(Message("system", prompt)), "Dispatch").collect { sb.append(it) } }
                trackTokens("dispatch", prompt, sb.toString())
                val cleaned = DeepSeekClient.cleanJson(sb.toString().trim())
                val resp = try { com.google.gson.Gson().fromJson(cleaned, DispatchResponse::class.java) } catch (_: Exception) { null }
                if (resp != null && resp.segments != null && resp.segments.size == totalSeg) {
                    val logJson = com.google.gson.Gson().toJson(resp.segments)
                    val itemsJson = com.google.gson.Gson().toJson(resp.items ?: emptyList<String>())
                    val rawReward = (resp.currency_reward ?: 0).coerceIn(0, budget * 10)
                    val netP = rawReward - budget
                    repository.insertDispatch(DispatchRecordEntity(
                        id = id, taskType = task, durationHours = duration,
                        budget = budget, netProfit = netP, operatorIds = operatorIds.joinToString(","),
                        logChain = logJson, status = "active", startTime = System.currentTimeMillis(),
                        totalSegments = totalSeg, segmentInterval = interval, items = itemsJson
                    ))
                } else {
                    repository.insertDispatch(DispatchRecordEntity(
                        id = id, taskType = task, durationHours = duration, budget = budget,
                        operatorIds = operatorIds.joinToString(","),
                        logChain = "", status = "cancelled", startTime = System.currentTimeMillis(),
                        totalSegments = 0, segmentInterval = 0, items = "[]"
                    ))
                }
            } catch (e: Exception) {
                // 失败：退回预算 + 恢复状态
                val cur = getApplication<Application>().getSharedPreferences("dispatch", 0).getInt("lmb", 1000)
                getApplication<Application>().getSharedPreferences("dispatch", 0).edit().putInt("lmb", cur + budget).apply()
                refreshAllOperatorStatus()
                repository.insertDispatch(DispatchRecordEntity(
                    id = id, taskType = task, durationHours = duration, budget = budget,
                    operatorIds = operatorIds.joinToString(","),
                    logChain = "", status = "cancelled", startTime = System.currentTimeMillis(),
                    totalSegments = 0, segmentInterval = 0, items = "[]"
                ))
            }
        }
    }

    fun finishDispatch(dispatchId: String) {
        viewModelScope.launch {
            val d = repository.getDispatch(dispatchId) ?: return@launch
            if (d.status != "active") return@launch
            refreshAllOperatorStatus()
            // 龙门币结算
            val prefs = getApplication<Application>().getSharedPreferences("dispatch", 0)
            val balance = prefs.getInt("lmb", 1000)
            prefs.edit().putInt("lmb", balance + d.netProfit).apply()
            // 创建锚点
            val profile = getUserProfile()
            val items = try {
                val arr = com.google.gson.JsonParser.parseString(d.items).asJsonArray
                arr.map { it.asString }.take(3).joinToString("、")
            } catch (_: Exception) { "未知" }
            val allOps = _operators.value
            val memberIds = d.operatorIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val memberNames = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id }?.name }.take(3).joinToString("、")
            for (opId in memberIds) {
                repository.saveAnchor(com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity(
                    sessionId = "anchor_${System.currentTimeMillis()}", operatorId = opId,
                    type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                    content = "${d.taskType}任务完成，${memberNames}带回${items}，净收益${d.netProfit}龙门币",
                    isPrivate = false
                ))
            }
            repository.updateDispatch(dispatchId, d.logChain, "finished", System.currentTimeMillis(), d.netProfit)
        }
    }

    fun deleteGroup(groupSessionId: String) {
        stopAutoGroupChat(groupSessionId)
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit()
            .remove("group_auto_$groupSessionId").apply()
        viewModelScope.launch {
            db.chatMessageDao().deleteSessionMessages(groupSessionId)
            db.chatSessionDao().delete(groupSessionId)
        }
    }

    // 自动群聊
    private val autoGroupChatJobs = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val autoChatGenerations = mutableMapOf<String, Long>()
    private val lastUserMsgTime = mutableMapOf<String, Long>()

    fun isAutoGroupChatEnabled(groupId: String): Boolean =
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).getBoolean("group_auto_$groupId", false)

    fun setAutoGroupChatEnabled(groupId: String, enabled: Boolean) {
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit()
            .putBoolean("group_auto_$groupId", enabled).apply()
        if (enabled) {
            viewModelScope.launch {
                val session = db.chatSessionDao().getSession(groupId)
                if (session != null) startAutoGroupChat(groupId, session.operatorName)
            }
        } else {
            stopAutoGroupChat(groupId)
        }
    }

    /** 距离上次用户发言已过 30s 后首次触发，之后按随机间隔循环 */
    private fun startAutoGroupChat(groupId: String, groupName: String) {
        stopAutoGroupChat(groupId)
        val generation = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoChatGenerations[groupId] = generation
        val minMs = intPref("group_chat_min_interval", 30) * 1000L
        val maxMs = intPref("group_chat_max_interval", 120) * 1000L
        autoGroupChatJobs[groupId] = viewModelScope.launch {
            // 首次触发：最短等待 10s，最长等待到距离上次发言满 30s
            val sinceLastMsg = System.currentTimeMillis() - (lastUserMsgTime[groupId] ?: 0L)
            val firstDelay = if (sinceLastMsg < 30_000) 30_000 - sinceLastMsg else 10_000L
            delay(firstDelay)

            while (isAutoGroupChatEnabled(groupId)) {
                // 世代不匹配说明已有新协程启动，当前协程立即退出
                if (autoChatGenerations[groupId] != generation) break

                val session = db.chatSessionDao().getSession(groupId) ?: break
                val mode = getGroupChatMode(groupId)
                sendGroupMessage(groupId, groupName, "", mode, isAuto = true)

                // 等待随机间隔，每轮检查世代
                val interval = minMs + (Math.random() * (maxMs - minMs)).toLong()
                val tickMs = 1000L
                var remaining = interval
                while (remaining > 0 && isAutoGroupChatEnabled(groupId)) {
                    if (autoChatGenerations[groupId] != generation) break
                    delay(minOf(remaining, tickMs))
                    remaining -= tickMs
                }
                if (autoChatGenerations[groupId] != generation) break
            }
        }
    }

    /** 用户发言时调用：记录时间 + 重启定时器 */
    fun resetAutoGroupChatTimer(groupId: String) {
        lastUserMsgTime[groupId] = System.currentTimeMillis()
        autoChatGenerations[groupId] = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoGroupChatJobs[groupId]?.cancel()
        autoGroupChatJobs.remove(groupId)
        viewModelScope.launch {
            val session = db.chatSessionDao().getSession(groupId)
            if (session != null && isAutoGroupChatEnabled(groupId)) {
                startAutoGroupChat(groupId, session.operatorName)
            }
        }
    }

    private fun getGroupChatMode(groupId: String): String =
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).getString("group_mode_$groupId", "online") ?: "online"

    fun stopAutoGroupChat(groupId: String) {
        autoChatGenerations[groupId] = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoGroupChatJobs[groupId]?.cancel()
        autoGroupChatJobs.remove(groupId)
    }

    fun stopAllAutoGroupChats() {
        autoChatGenerations.clear()
        autoGroupChatJobs.values.forEach { it.cancel() }
        autoGroupChatJobs.clear()
    }

    fun refreshAutoGroupChats() {
        val prefs = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        _sessions.value.filter { it.operatorId.startsWith("group_") || it.operatorId.startsWith("group") }.forEach { group ->
            if (prefs.getBoolean("group_auto_${group.id}", false)) {
                startAutoGroupChat(group.id, group.operatorName)
            } else {
                stopAutoGroupChat(group.id)
            }
        }
    }

    fun cancelDispatch(dispatchId: String) {
        viewModelScope.launch {
            val d = repository.getDispatch(dispatchId) ?: return@launch
            val prefs = getApplication<Application>().getSharedPreferences("dispatch", 0)
            val balance = prefs.getInt("lmb", 1000)
            repository.updateDispatch(dispatchId, d.logChain + "\n\n【已中断】", "cancelled", System.currentTimeMillis(), 0)
            refreshAllOperatorStatus()
        }
    }

    private suspend fun recoverDispatches() {
        val actives = repository.getActiveDispatches()
        for (d in actives) {
            if (d.status == "generating" && d.logChain.isBlank()) {
                // 进程被杀死时生成未完成，重新生成
                generateDispatchStart(d.id, d.taskType, d.budget, d.operatorIds.split(","))
                continue
            }
            val elapsed = System.currentTimeMillis() - d.startTime
            val totalDuration = d.durationHours * 3_600_000L
            if (elapsed >= totalDuration) {
                finishDispatch(d.id)
            }
        }
    }

    fun deleteOperator(operatorId: String) {
        viewModelScope.launch {
            db.operatorDao().delete(operatorId)
            val session = db.chatSessionDao().getSessionByOperator(operatorId)
            if (session != null) {
                db.chatMessageDao().deleteSessionMessages(session.id)
                db.chatSessionDao().delete(session.id)
            }
        }
    }

    suspend fun exportAllOperators(context: android.content.Context): java.io.File {
        val ops = _operators.value.map { OperatorExport.fromEntity(it) }
        val allRels = mutableListOf<RelationshipExport>()
        for (op in ops) {
            val rels = repository.getRelationships(op.id)
            allRels.addAll(rels.map { RelationshipExport.fromEntity(it) })
        }
        val payload = ExportPayload(type = "operators", operators = ops, relationships = allRels)
        return ExportHelper.exportToFile(context, payload, "rhodes_operators_${System.currentTimeMillis()}.json")
    }

    fun importOperators(payload: ExportPayload, mode: String, targetOpId: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val ops = payload.operators ?: return@launch
            if (mode == "new") {
                for (op in ops) {
                    val existing = repository.getOperator(op.id)
                    if (existing == null) db.operatorDao().insert(op.toEntity())
                }
            } else if (mode == "overwrite" && targetOpId.isNotBlank()) {
                val op = ops.find { it.id == targetOpId } ?: return@launch
                db.operatorDao().insert(op.toEntity())
            }
            val rels = payload.relationships ?: return@launch
            for (rel in rels) {
                db.relationshipDao().insert(rel.toEntity())
            }
        }
    }

    suspend fun exportChatHistory(context: android.content.Context, sessionId: String): java.io.File {
        val session = db.chatSessionDao().getSession(sessionId)
        val msgs = db.chatMessageDao().getMessagesSync(sessionId)
        val payload = ExportPayload(
            type = "chat",
            session = session?.let { SessionExport.fromEntity(it) },
            messages = msgs.map { MessageExport.fromEntity(it) }
        )
        return ExportHelper.exportToFile(context, payload, "chat_${sessionId}_${System.currentTimeMillis()}.json")
    }

    fun loadComments(momentId: Long) {
        viewModelScope.launch {
            repository.getComments(momentId).collect { _comments.value = it }
        }
    }

    fun recallMessage(msgId: Long) {
        val isGroup = _currentGroupId.value.isNotBlank()
        viewModelScope.launch {
            db.chatMessageDao().delete(msgId)
            if (isGroup) {
                _groupMessages.value = _groupMessages.value.filter { it.id != msgId }
            } else {
                _messages.value = _messages.value.filter { it.id != msgId }
            }
        }
    }

    fun regenerateAiMessage(msgId: Long) {
        val session = _currentSession.value ?: return
        val idx = _messages.value.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        // 找到触发 AI 的那条用户消息
        val userMsg = _messages.value.take(idx).lastOrNull { it.isMe } ?: return
        // 删除旧 AI 回复 + 用户消息，防止 sendMessage 重复
        viewModelScope.launch { db.chatMessageDao().delete(msgId); db.chatMessageDao().delete(userMsg.id) }
        _messages.value = _messages.value.filter { it.id != msgId && it.id != userMsg.id }
        _inputText.value = userMsg.content
        sendMessage()
    }

    fun continueAiMessage(msgId: Long) {
        val session = _currentSession.value ?: return
        val idx = _messages.value.indexOfFirst { it.id == msgId }
        if (idx < 0) return
        val mode = _currentMode.value
        viewModelScope.launch {
            // 创建新的 AI 回复占位
            val aiMsgId = repository.getNextMessageId()
            repository.sendMessage(session.id, ChatMessageEntity(
                id = aiMsgId, sessionId = session.id,
                senderName = session.operatorName, content = "...",
                type = "ai_json", mode = mode, isMe = false
            ))
            _loadingSessions.value = _loadingSessions.value + session.id
            // 构建 system prompt，注入继续指令
            val previousUser = _messages.value.take(idx).lastOrNull { it.isMe }
            modeTransitionNotice = "【继续指令】请自然地继续说下去，不要复述或总结之前说过的话。"
            try {
                val apiMessages = buildApiMessages(previousUser?.content ?: "")
                val sb = StringBuilder()
                streamChat(apiMessages).collect { chunk -> sb.append(chunk) }
                val raw = sb.toString().trim()
                repository.sendMessage(session.id, ChatMessageEntity(
                    id = aiMsgId, sessionId = session.id,
                    senderName = session.operatorName, content = raw,
                    type = "ai_json", mode = mode, isMe = false
                ))
                if (_currentSession.value?.id == session.id) {
                    _messages.value = _messages.value.map { if (it.id == aiMsgId) it.copy(content = raw, type = "ai_json") else it }
                }
                val parsed = DeepSeekClient.parseOfflineResponse(raw)
                if (parsed.emotion.isNotBlank() || parsed.location.isNotBlank() || parsed.state.isNotBlank()) {
                    updateOperatorStatus(session.operatorId, parsed.location, parsed.state, parsed.emotion)
                }
            } catch (e: Exception) {
                updateAiMessage(aiMsgId, "错误: ${e.message}")
            } finally {
                _loadingSessions.value = _loadingSessions.value - session.id
                modeTransitionNotice = ""
            }
        }
    }

    fun buyProp(propName: String, context: android.content.Context): String? {
        val balance = context.getSharedPreferences("dispatch", 0).getInt("lmb", 1000)
        if (balance < 100) return "余额不足"
        context.getSharedPreferences("dispatch", 0).edit().putInt("lmb", balance - 100).apply()
        return null
    }

    fun setHypnosis(command: String) {
        _hypnosisCommand.value = command
        _hypnosisRounds.value = 10
        // 持久化
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit()
            .putString("hypnosis_cmd", command).putInt("hypnosis_rounds", 10).apply()
    }

    fun decrementHypnosis() {
        if (_hypnosisRounds.value > 0) _hypnosisRounds.value = _hypnosisRounds.value - 1
        getApplication<Application>().getSharedPreferences("chat_prefs", 0).edit()
            .putInt("hypnosis_rounds", _hypnosisRounds.value).apply()
    }

    fun loadHypnosis() {
        val prefs = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        _hypnosisCommand.value = prefs.getString("hypnosis_cmd", "") ?: ""
        _hypnosisRounds.value = prefs.getInt("hypnosis_rounds", 0)
    }

    fun setMindRead(innerThought: String) {
        _mindReadContent.value = innerThought
        _mindReadRounds.value = 3
    }

    fun decrementMindRead() {
        if (_mindReadRounds.value > 0) _mindReadRounds.value = _mindReadRounds.value - 1
    }

    private var groupMessageMutex = kotlinx.coroutines.sync.Mutex()
    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false) {
        viewModelScope.launch {
            if (!groupMessageMutex.tryLock()) return@launch
            _groupLoading.value = true
            // 1) Save user message (skip in auto/autoSpeak with empty text)
            if (!isAuto && text.isNotBlank()) {
                val userMsgId = repository.getNextMessageId()
                repository.sendMessage(groupSessionId, ChatMessageEntity(
                    id = userMsgId, sessionId = groupSessionId,
                    senderName = "我", content = text,
                    type = "text", mode = mode, isMe = true
                ))
                // 用户发言 → 重置自动群聊定时器（30s 冷却）
                resetAutoGroupChatTimer(groupSessionId)
            }
            try {
                // 2) Load group session data
                val session = db.chatSessionDao().getSession(groupSessionId) ?: run { _groupLoading.value = false; return@launch }
                val memberIds = session.members.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val allOps = _operators.value
                val members = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id } }
                val mutedIds = session.mutedMembers.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val activeMembers = members.filter { it.id !in mutedIds && it.name !in mutedIds }

                // 3) Build enhanced prompt
                val profile = getUserProfile()
                val relContext = getGroupRelationshipContext(activeMembers)
                val relationHints = if (relContext.isNotBlank()) relContext else "无"
                val memberPrivateContext = if (!isAuto) {
                    buildString {
                        for (m in activeMembers) {
                            val ctx = repository.getPrivateChatContext(m.id)
                            if (ctx != null) {
                                append("- ${m.name}：${ctx}\n")
                            } else {
                                append("- ${m.name}：暂无特别的互动\n")
                            }
                        }
                    }.toString()
                } else { "" }
                val groupSummary = repository.getShortTermMemory(groupSessionId)?.content ?: ""
                val longTermImpression = if (!isAuto && activeMembers.isNotEmpty()) {
                    activeMembers.take(5).mapNotNull { m ->
                        repository.getLongTermImpression(m.id)?.content?.let { "- ${m.name}对${profile.nickname}的印象：${it.take(100)}" }
                    }.joinToString("\n").ifBlank { "成员们对${profile.nickname}尚无深入了解。" }
                } else ""
                val memberProfiles = buildString {
                    val shuffled = activeMembers.shuffled()
                    for (m in shuffled) {
                        val key = "${groupSessionId}_${m.id}"
                        val act = groupActivityCache.getOrPut(key) { "活跃${"%.1f".format(0.5 + Math.random() * 0.5)}" }
                        val titleStr = if (m.title.isBlank()) "" else "，${m.title}"
                        append("${m.name}（${act}${titleStr}）：${m.groupPrompt.ifBlank { m.description }}\n")
                    }
                }
                val userMessage = if (isAuto) "" else if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                val grpTpl = getPromptTemplate("group", if (isAuto) "auto" else mode)
                val userObserving = if (isAuto) {
                    when (mode) {
                        "offline" -> "用户坐在一旁，安静地听着大家的对话，没有插话。"
                        "director" -> "用户作为导演正在观察大家的表演，没有给出新指令。"
                        else -> "群内用户正在安静地观察，没有发言。"
                    }
                } else ""
                val grpModeFormat = if (isAuto) {
                    when (mode) {
                        "offline" -> "\n允许旁白条目（speaker为\"旁白\"，type为\"narration\"），对话条目type为\"dialogue\"。"
                        "director" -> "\n允许旁白条目（speaker为\"旁白\"，type为\"narration\"），对话条目type为\"dialogue\"。"
                        else -> ""
                    }
                } else ""
                val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
                val grpReplacements = mapOf(
                    "CURRENT_TIME" to now,
                    "GROUP_NAME" to groupName,
                    "GROUP_RULES" to (session.rules.ifBlank { "无" }),
                    "USER_NAME" to profile.nickname,
                    "USER_GENDER" to profile.gender.ifBlank { "未知" },
                    "USER_BIO" to profile.bio.ifBlank { "无" },
                    "RELATION_HINTS" to relationHints,
                    "MEMBER_PRIVATE_CONTEXT" to memberPrivateContext,
                    "SHORT_TERM_SUMMARY" to groupSummary,
                    "GROUP_SUMMARY" to groupSummary,
                    "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
                    "LONG_TERM_IMPRESSION" to longTermImpression,
                    "MEMBER_PROFILES" to memberProfiles.toString(),
                    "GROUP_NAR_SEG_MIN" to intPref("group_nar_seg_min", 1).toString(),
                    "GROUP_NAR_SEG_MAX" to intPref("group_nar_seg_max", 3).toString(),
                    "GROUP_NAR_MIN" to intPref("group_nar_min", 20).toString(),
                    "GROUP_NAR_MAX" to intPref("group_nar_max", 50).toString(),
                    "GROUP_MSG_MIN" to intPref("group_msg_min", 10).toString(),
                    "GROUP_MSG_MAX" to intPref("group_msg_max", 80).toString(),
                    "GROUP_SPEECH_MIN" to intPref("group_speech_min", 1).toString(),
                    "GROUP_SPEECH_MAX" to intPref("group_speech_max", 2).toString(),
                    "USER_MESSAGE" to userMessage,
                    "USER_OBSERVING" to userObserving,
                    "GROUP_MODE_FORMAT" to grpModeFormat
                )
                val systemPrompt = applyTemplate(grpTpl, grpReplacements)

                // 4) Build multi-turn messages (like private chat)
                val apiMessages = mutableListOf(Message("system", systemPrompt))
                val historyLimit = intPref("history_messages", 30)
                val allHistory = repository.getMessagesSync(groupSessionId).let { msgs ->
                    if (historyLimit > 0) msgs.takeLast(historyLimit) else msgs
                }
                for (msg in allHistory) {
                    val role = if (msg.isMe) "user" else "assistant"
                    val content = if (msg.isMe) "用户：${msg.content}" else msg.content
                    apiMessages.add(Message(role, content))
                }
                if (!isAuto) {
                    val userMsg = if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）"
                    else text
                    apiMessages.add(Message("user", "用户：$userMsg"))
                }
                val promptText = apiMessages.firstOrNull()?.content ?: ""
                if (DEBUG) logAiCall("GroupChat", promptText, "(streaming...)", apiMessages)
                val sb = StringBuilder()
                withTimeout(25_000) {
                    val temp = intPref("ai_temperature", 95).toDouble() / 100.0
                    com.example.rhodesterminal.network.AIClient.streamChat(
                        getApiKey(), apiMessages,
                        getProvider(), getModelName(), getCustomUrl(), temperature = temp
                    ).collect { sb.append(it) }
                }
                    trackTokens("group", promptText, sb.toString())
                if (DEBUG) logAiCall("GroupChat", promptText, sb.toString(), apiMessages)

                // 5) Parse results
                val rawBase = sb.toString().trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                var results: List<GroupMsgResult> = emptyList()
                for (cleaned in listOf(rawBase, rawBase.replace("，", ",").replace("：", ":"))) {
                    try {
                        val arr = com.google.gson.Gson().fromJson(cleaned, Array<GroupMsgResult>::class.java)
                        results = arr?.toList() ?: emptyList()
                        if (results.isNotEmpty()) break
        } catch (e: Exception) {
            if (DEBUG) android.util.Log.w("AI调试输出", "主动消息失败: ${e.message}")
        }
    }

                // 6) Save AI response as single ai_json message
                val filtered = results.filter { it.message.isNotBlank() }
                if (filtered.isNotEmpty()) {
                    val aiMsgId = repository.getNextMessageId()
                    repository.sendMessage(groupSessionId, ChatMessageEntity(
                        id = aiMsgId, sessionId = groupSessionId,
                        senderName = groupName, content = rawBase,
                        type = "ai_json", mode = mode, isMe = false
                    ))
                    // 群聊记忆锚点：为每个发言干员生成 EVENT 锚点
                    for (r in filtered) {
                        val anchorOp = if (r.speaker == "旁白" || r.speaker == "系统") null
                        else allOps.find { it.name == r.speaker }
                        if (anchorOp != null) {
                            repository.saveAnchor(com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity(
                                sessionId = "anchor_${System.currentTimeMillis()}",
                                operatorId = anchorOp.id,
                                type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                                content = "在群聊「${groupName}」中${r.speaker}说：${r.message.take(40)}",
                                isPrivate = false
                            ))
                        }
                    }
                    // Update session lastMessage
                    val last = filtered.last()
                    db.chatSessionDao().updateLastMessage(groupSessionId, "${last.speaker}：${last.message.take(50)}", System.currentTimeMillis())
                }
                // 未读计数
                val currentGroupSessionId = _currentGroupId.value
                if (currentGroupSessionId != groupSessionId && filtered.isNotEmpty()) {
                    val sess = db.chatSessionDao().getSession(groupSessionId)
                    if (sess != null) db.chatSessionDao().insert(sess.copy(unreadCount = sess.unreadCount + 1))
                    unhideSession(groupSessionId)
                }
                // 群聊消息计数 → 触发滚动摘要（按 ai_json 条数 = 1）
                val gc = sessionMessageCounter.getOrDefault(groupSessionId, 0) + 1
                sessionMessageCounter[groupSessionId] = gc
                if (gc >= intPref("summary_threshold", 20) && groupSessionId.isNotBlank()) {
                    val gs = db.chatSessionDao().getSession(groupSessionId)
                    if (gs != null) {
                        val freshMsgs = repository.getMessagesSync(gs.id)
                        generateShortTermSummary(gs, freshMsgs)
                        sessionMessageCounter[groupSessionId] = 0
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("GroupChat", "Timeout: ${e.message}")
                repository.sendMessage(groupSessionId, ChatMessageEntity(
                    id = repository.getNextMessageId(), sessionId = groupSessionId,
                    senderName = "系统", content = "响应超时，请重试",
                    type = "system", mode = mode, isMe = false
                ))
            } catch (e: Exception) {
                Log.e("GroupChat", "Error: ${e.message}", e)
                repository.sendMessage(groupSessionId, ChatMessageEntity(
                    id = repository.getNextMessageId(), sessionId = groupSessionId,
                    senderName = "系统", content = "连接失败",
                    type = "system", mode = mode, isMe = false
                ))
            } finally {
                _groupLoading.value = false
                groupMessageMutex.unlock()
            }
        }
    }

    private suspend fun getGroupRelationshipContext(members: List<OperatorEntity>): String {
        val lines = mutableListOf<String>()
        for (i in members.indices) {
            for (j in i + 1 until members.size) {
                val a = members[i]; val b = members[j]
                val rel = db.relationshipDao().getRelationship(a.id, b.id)
                if (rel != null && rel.type != com.example.rhodesterminal.data.db.entity.RelationshipType.STRANGER) {
                    val desc = relationshipGroupDesc(a.name, b.name, rel.type)
                    lines.add("- $desc（亲密${rel.intimacy}）")
                }
            }
        }
        return lines.joinToString("\n")
    }

    fun generateAllMoments(target: Int = 1, dateKey: String = "", onProgress: (String) -> Unit = {}) {
        val isAuto = dateKey.isNotBlank()
        val today = dateKey.ifBlank { beijingSdf("yyyyMMdd").format(java.util.Date()) }
        val prefsKey = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
        val slotHours = listOf(9, 10, 14, 15, 17, 19, 20, 21, 22)
        val slotNames = listOf("上午", "上午", "下午", "下午", "傍晚", "晚上", "晚上", "晚上", "深夜")
        viewModelScope.launch {
            for (op in _operators.value) {
                val allowDyn = getApplication<Application>().getSharedPreferences("op_perms", 0).getBoolean("dyn_${op.id}", true)
                if (!allowDyn) continue
                val startIdx = if (isAuto) {
                    val countKey = "moment_count_${op.id}_$today"
                    val d = prefsKey.getInt(countKey, 0)
                    if (d >= target) continue
                    d
                } else 0
                for (i in startIdx until target) {
                    onProgress("发布中...")
                    try {
                        val profile = getUserProfile()
                        val impression = repository.getLongTermImpression(op.id)?.content ?: "无"
                        val chatSummary = repository.getShortTermMemory("session_${op.id}")?.content?.take(100) ?: "无"
                        val memories = pickAnchors(repository.getPublicAnchors(op.id), 3).joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "无" }
                        val existingPosts = repository.getMomentsPaged(10, 0).filter { it.operatorId == op.id }
                        val recentPosts = existingPosts.take(3).joinToString("\n") { "- ${it.content.take(50)}" }.ifBlank { "无" }
                        val timeOfDay: String
                        val fakeTs: Long
                        if (isAuto) {
                            val slotIdx = i % slotHours.size
                            val hour = slotHours[slotIdx] + (Math.random() * 2).toInt()
                            timeOfDay = slotNames[slotIdx]
                            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
                            cal.set(java.util.Calendar.HOUR_OF_DAY, hour.coerceAtMost(23))
                            cal.set(java.util.Calendar.MINUTE, (Math.random() * 60).toInt())
                            cal.set(java.util.Calendar.SECOND, 0)
                            fakeTs = cal.timeInMillis
                        } else {
                            timeOfDay = getTimeOfDay(java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY))
                            fakeTs = System.currentTimeMillis()
                        }
                        val mmtTpl = getPromptTemplate("moment")
                        val mmtReplacements = mapOf(
                            "OPERATOR_NAME" to op.name, "OPERATOR_PERSONA" to op.description,
                            "TIME_OF_DAY" to timeOfDay, "LONG_TERM_IMPRESSION" to impression,
                            "RECENT_CHAT_SUMMARY" to chatSummary, "RECENT_MEMORIES" to memories,
                            "RECENT_POSTS" to recentPosts,
                            "CURRENT_DATE" to beijingSdf("yyyy年MM月dd日").format(fakeTs),
                            "USER_NAME" to profile.nickname,
                            "MOMENT_MIN_CHARS" to intPref("moment_min_chars", 50).toString(),
                            "MOMENT_MAX_CHARS" to intPref("moment_max_chars", 200).toString()
                        )
                        val prompt = applyTemplate(mmtTpl, mmtReplacements)
                        val temp = intPref("ai_temperature", 95).toDouble() / 100.0
                        val sb = StringBuilder()
                        repeat(3) { attempt ->
                            try {
                                sb.clear()
                                withTimeout(15_000) { streamChat(listOf(Message("system", prompt)), "Moment").collect { sb.append(it) } }
                                if (sb.isNotBlank()) return@repeat
                            } catch (_: Exception) { if (attempt < 2) delay((1000L * (attempt + 1))) }
                        }
                        trackTokens("moment", prompt, sb.toString())
                        val content = sb.toString().trim().removePrefix("\"").removeSuffix("\"")
                        if (content.isNotBlank()) {
                            val moment = MomentEntity(operatorId = op.id, operatorName = op.name, content = content, createdAt = fakeTs)
                            val momentId = repository.insertMoment(moment)
                            val likers = _operators.value.filter { it.id != op.id && it.name != profile.nickname }.shuffled().take((3..8).random())
                            likers.forEach { liker -> repository.insertLike(MomentLikeEntity(momentId = momentId, operatorId = liker.id, operatorName = liker.name)) }
                            repository.updateLikeCount(momentId, likers.size)
                            val commenters = _operators.value.filter { it.id != op.id && it.name != profile.nickname }.shuffled().take((1..3).random())
                            val cmtTpl = getPromptTemplate("moment_comment")
                            commenters.forEach { commenter ->
                                try {
                                    val cmtReplacements = mapOf(
                                        "COMMENTER_NAME" to commenter.name, "COMMENTER_PERSONA" to (commenter.privatePrompt.ifBlank { commenter.description }),
                                        "POST_CONTENT" to content,
                                        "COMMENT_MIN_CHARS" to intPref("comment_min_chars", 10).toString(),
                                        "COMMENT_MAX_CHARS" to intPref("comment_max_chars", 40).toString()
                                    )
                                    val cp = applyTemplate(cmtTpl, cmtReplacements)
                                    val csb = StringBuilder()
                                    withTimeout(8_000) { streamChat(listOf(Message("system", cp)), "Moment").collect { csb.append(it) } }
                                    trackTokens("moment", cp, csb.toString())
                                    val cc = csb.toString().trim()
                                    if (cc.isNotBlank()) repository.insertComment(MomentCommentEntity(momentId = momentId, operatorId = commenter.id, operatorName = commenter.name, content = cc))
                                } catch (_: Exception) {}
                            }
                            repository.updateCommentCount(momentId, commenters.size)
                        }
                    } catch (_: Exception) {}
                    if (isAuto) prefsKey.edit().putInt("moment_count_${op.id}_$today", i + 1).apply()
                }
            }
            onProgress("全部完成")
        }
    }

    fun generateInspirations(callback: (List<String>) -> Unit) {
        val op = _selectedOperator.value ?: return
        viewModelScope.launch {
            try {
                val profile = getUserProfile()
                val now = beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
                val hour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).get(java.util.Calendar.HOUR_OF_DAY)
                val recent = _messages.value.takeLast(15).joinToString("\n") { "${if (it.isMe) profile.nickname else it.senderName}：${it.content.take(60)}" }
                val lastOpMsg = _messages.value.lastOrNull { !it.isMe }?.content?.take(60) ?: ""
                val modeHint = when (_currentMode.value) {
                    "offline" -> "3. 【线下模式】你和${op.name}面对面在一起，回复要像当面说话一样自然，可用括号带动作描述。"
                    "director" -> "3. 【导演模式】你是导演，${op.name}是演员，回复可以带有指导性的行动指令。"
                    else -> "3. 【线上模式】文字聊天，回复简短自然，像发微信消息。"
                }
                val timeHint = when {
                    hour in 6..8 -> "（清晨）"
                    hour in 9..11 -> "（上午）"
                    hour in 12..13 -> "（中午，可提到吃饭相关）"
                    hour in 14..17 -> "（下午）"
                    hour in 18..21 -> "（晚上）"
                    hour in 22..23 -> "（深夜，语气可以更慵懒放松）"
                    else -> "（凌晨）"
                }
                val prompt = buildString {
                    append("你是对话灵感生成器，请结合聊天上下文，为${profile.nickname}生成3条可以直接发送给${op.name}的回复话术。\n\n")
                    append("【当前时间】${now}\n")
                    append("【干员信息】\n${op.name}，${op.privatePrompt.ifBlank { op.description }}\n\n")
                    append("【用户信息】\n${profile.nickname}，${if (profile.gender.isNotBlank()) "性别${profile.gender}，" else ""}个人简介：${profile.bio}\n\n")
                    append("【聊天上下文】\n用户与${op.name}的最近15条对话记录：\n${recent}\n\n")
                    append("${op.name}刚刚对用户说：“${lastOpMsg}”\n\n")
                    append("【生成要求】\n")
                    append("请为${profile.nickname}生成3条可以直接发送给${op.name}的回复话术：\n")
                    append("1. 每条15-40字，口语化自然，像真人平时说话一样。\n")
                    append("2. 三条建议分别对应不同风格的回复方向：\n")
                    append("   - 第一条（承接）：顺势承接${op.name}的话题，继续推进对话。\n")
                    append("   - 第二条（关心）：换个角度，表达关心、好奇或共情，让对话有新鲜感。\n")
                    append("   - 第三条（行动）：提出一个具体的行动邀约或场景推进建议，让对话进入下一阶段。\n")
                    append("$modeHint\n")
                    append("4. 结合用户的人设和当前时间${timeHint}，让回复更贴合真实的聊天氛围。\n\n")
                    append("【输出格式要求】\n")
                    append("严格输出纯JSON，不要添加任何其他文字、markdown标记或解释：\n")
                    append("""{"suggestions":["第一条承接话题的回复","第二条关心的回复","第三条行动邀约的回复"]}""")
                }
                val sb = StringBuilder()
                withTimeout(10_000) { streamChat(listOf(Message("system", prompt))).collect { sb.append(it) } }
                val cleaned = sb.toString().trim()
                    .removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    .replace("，", ",").replace("：", ":")
                val results = try {
                    val resp = com.google.gson.Gson().fromJson(cleaned, SuggestionResponse::class.java)
                    resp?.suggestions?.filter { it.isNotBlank() } ?: emptyList()
                } catch (_: Exception) { emptyList() }
                callback(results.ifEmpty { listOf("嗯，我在听", "然后呢？", "有意思") })
            } catch (_: Exception) { callback(listOf("嗯，我在听", "然后呢？", "有意思")) }
        }
    }

    fun getLikes(momentId: Long): kotlinx.coroutines.flow.Flow<List<MomentLikeEntity>> = repository.getLikesFlow(momentId)
    fun getCommentsForMoment(momentId: Long): kotlinx.coroutines.flow.Flow<List<MomentCommentEntity>> = repository.getComments(momentId)
    fun likeMoment(momentId: Long, operatorId: String, operatorName: String) {
        viewModelScope.launch {
            val existing = repository.getLike(momentId, operatorId)
            if (existing == null) {
                repository.insertLike(MomentLikeEntity(momentId = momentId, operatorId = operatorId, operatorName = operatorName))
            }
            val count = repository.getLikeCount(momentId)
            repository.updateLikeCount(momentId, count)
        }
    }
    fun commentOnMoment(momentId: Long, operatorId: String, operatorName: String, content: String, parentCommentId: Long = 0, replyToName: String = "") {
        val cleanContent = content.trim()
        if (cleanContent.isBlank()) return
        viewModelScope.launch {
            repository.insertComment(MomentCommentEntity(momentId = momentId, operatorId = operatorId, operatorName = operatorName, content = cleanContent, parentCommentId = parentCommentId, replyToName = replyToName))
            // 创建评论锚点（仅动态发布者 + 被回复者，不扩散到全干员）
            if (operatorId == "user") {
                val moment = _moments.value.find { it.id == momentId }
                val targetName = if (parentCommentId > 0 && replyToName.isNotBlank()) "回复了${replyToName}" else "评论了${moment?.operatorName ?: ""}的动态"
                val anchorTargets = mutableSetOf<String>()
                if (moment != null) anchorTargets.add(moment.operatorId)
                if (parentCommentId > 0 && replyToName.isNotBlank()) anchorTargets.add(replyToName)
                for (anchorOpId in anchorTargets) {
                    val realOp = _operators.value.find { it.name == anchorOpId || it.id == anchorOpId }
                    if (realOp != null) {
                        repository.saveAnchor(com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity(
                            sessionId = "anchor_${System.currentTimeMillis()}",
                            operatorId = realOp.id,
                            type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                            content = "${getUserProfile().nickname}${targetName}：${content.take(30)}",
                            isPrivate = false
                        ))
            }
        }
    }
            if (operatorId != "user") return@launch
            val moment = _moments.value.find { it.id == momentId } ?: return@launch
            val userName = getUserProfile().nickname

            // 1) 回复原评论者（如果是回复）
            val alreadyReplied = mutableSetOf<String>()
            if (parentCommentId > 0 && replyToName.isNotBlank() && replyToName != moment.operatorName && replyToName != userName) {
                triggerSingleAiReply(momentId, replyToName, content, parentCommentId, userName)
                alreadyReplied.add(replyToName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            // 2) 动态发布者回复（跳过用户自己发的动态）
            if (moment.operatorName != "我" && moment.operatorName != userName && moment.operatorName !in alreadyReplied) {
                triggerSingleAiReply(momentId, moment.operatorName, content, parentCommentId, userName, "你是${moment.operatorName}。用户${userName}在你的动态下评论了：「${content}」。请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。")
                alreadyReplied.add(moment.operatorName)
                delay((1500L + (Math.random() * 1500).toLong()))
            }

            // 3) 随机1-2个干员看热闹
            val bystanders = _operators.value
                .map { it.name }
                .filter { it !in alreadyReplied && it != "我" && it != userName }
                .shuffled()
                .take(1 + (Math.random() * 2).toInt())
            for (bystander in bystanders) {
                val bp = "你是${bystander}。你刚看到${moment.operatorName}的动态下，用户${userName}评论了「${content}」。请用10-40字凑热闹式地回复这条评论（看戏、调侃、起哄风格）。直接输出纯文本。"
                triggerSingleAiReply(momentId, bystander, content, parentCommentId, userName, bp)
                delay((1500L + (Math.random() * 1500).toLong()))
            }
        }
    }

    private fun triggerSingleAiReply(momentId: Long, speakerName: String, userContent: String, parentCommentId: Long, userName: String, customPrompt: String? = null) {
        viewModelScope.launch {
            try {
                val prompt = customPrompt ?: "你是${speakerName}。用户扮演的角色${userName}刚刚回复了你的评论，说：「${userContent}」。请用10-50字自然回复。只输出回复内容本身，不要加任何前缀如「回复xxx」或冒号。直接输出纯文本。注意：你是${speakerName}，不是${userName}，不要替${userName}说话。"
                val sb = StringBuilder()
                withTimeout(10_000) { streamChat(listOf(Message("system", prompt))).collect { sb.append(it) } }
                val reply = sb.toString().trim()
                if (reply.isNotBlank()) {
                    val realOp = _operators.value.find { it.name == speakerName || it.id == speakerName }
                    val realId = realOp?.id ?: speakerName
                    repository.insertComment(MomentCommentEntity(momentId = momentId, operatorId = realId, operatorName = speakerName, content = reply, parentCommentId = parentCommentId, replyToName = userName))
                }
            } catch (_: Exception) {}
        }
    }



    fun postUserMoment(content: String, mentionedOps: List<String>) {
        viewModelScope.launch {
            val profile = getUserProfile()
            val userName = profile.nickname
            val moment = MomentEntity(operatorId = "user", operatorName = userName, content = content, isUserPost = true, mentionedOperatorIds = mentionedOps.joinToString(","))
            val momentId = repository.insertMoment(moment)
            // 创建动态锚点（仅动态发布者自己 + 随机3个围观干员）
            val anchorOps = listOf(moment.operatorId) + _operators.value.filter { it.id != moment.operatorId }.shuffled().take(3).map { it.id }
            for (opId in anchorOps.distinct()) {
                repository.saveAnchor(com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity(
                    sessionId = "anchor_${System.currentTimeMillis()}",
                    operatorId = opId,
                    type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                    content = "${userName}发布了动态：${content.take(40)}",
                    isPrivate = false
                ))
            }

            // AI auto-replies: mentioned operators guaranteed + random 3-5 total
            val allOpNames = _operators.value.map { it.name }.filter { it != userName }
            val mentioned = mentionedOps.filter { it in allOpNames }
            val randomCount = (3 + (Math.random() * 3).toInt()).coerceAtLeast(3)
            val others = (allOpNames - mentioned.toSet()).shuffled().take((randomCount - mentioned.size).coerceAtLeast(0))
            val repliers = (mentioned + others).distinct().take(5)

            for ((i, name) in repliers.withIndex()) {
                if (i > 0) delay((1500L + (Math.random() * 1500).toLong()))
                val prompt = "你是${name}。用户扮演的角色${userName}发布了动态：「${content}」。请用10-40字评论这条动态（根据你的性格自然回应）。直接输出纯文本。注意：你是${name}，不是${userName}，不要替${userName}说话。"
                triggerSingleAiReply(momentId, name, content, 0, userName, prompt)
            }
        }
    }

    fun generateDispatchStart(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>) {
        viewModelScope.launch {
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            repeat(3) { attempt ->
                try {
                    val dMn = intPref("dispatch_min_chars", 150); val dMx = intPref("dispatch_max_chars", 400)
                    val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。不是写任务报告，而是写生动的开局故事。

为以下派遣任务撰写开局简报。

【派遣信息】
任务类型：${taskType}
小队成员：${names}（共${memberCount}人，所有成员必须在故事中被提及并描写）
投入预算：${budget}龙门币

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字，第三人称叙事，小说级描写
- 描写出发前准备：采购装备、讨论策略、互相打趣
- 为每个成员确立"本集特征"
- 埋下悬念或意外发现：神秘信件、不明脚印、远处声响
- 所有成员必须出场，名字和人设必须与档案一致

【叙事质量】
- 不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 在细微动作和环境中透露角色关系和情感状态
- 制造"可看性"——让读者能想象出这个场景的画面

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色（如"阿米娅""能天使"），禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发
- 角色之间的对话中用"对方""博士""队长"等第三人称称呼，不用"你""我"

直接输出开局叙事。
""".trimIndent()
                    val sb = StringBuilder()
                    withTimeout(20_000) { streamChat(listOf(Message("system", prompt)), "Dispatch").collect { sb.append(it) } }
                    trackTokens("dispatch", prompt, sb.toString())
                    repository.updateDispatch(dispatchId, DeepSeekClient.cleanJson(sb.toString().trim()), "active")
                    return@launch
                } catch (_: Exception) {
                    if (attempt < 2) delay(1000L * (attempt + 1))
                }
            }
            // 3次重试全部失败 → 标记取消
            repository.getDispatch(dispatchId)?.let { d ->
                if (d.status == "generating") {
                    repository.updateDispatch(dispatchId, "\n\n【生成失败，已取消】", "cancelled", System.currentTimeMillis(), 0)
                    refreshAllOperatorStatus()
                }
            }
        }
    }

    fun generateDispatchProgress(dispatchId: String, taskType: String, budget: Int, operatorIds: List<String>, roundNum: Int, logSummary: String) {
        viewModelScope.launch {
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            val budgetLevel = when { budget < 300 -> "低"; budget < 800 -> "中"; else -> "高" }
            repeat(3) { attempt ->
                try {
                    val dMn = intPref("dispatch_min_chars", 150); val dMx = intPref("dispatch_max_chars", 400)
                    val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。为进行中的派遣行动续写故事。

续写派遣冒险的第${roundNum}轮过程日志。

【派遣信息】
任务类型：${taskType}
预算等级：${budgetLevel}（低预算事件倾向危险和损失，高预算倾向顺利和意外收获）
前情提要：${logSummary.take(100)}

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字，第三人称叙事，承接前情，剧情连贯
- 本轮必须出现一个具体事件：遭遇敌人、发现遗迹、天气突变、物资丢失、队员争执等
- 事件与成员性格和职业特性相关：狙击手可能先发现敌情，医疗干员可能照顾伤员
- 上次出场较少的成员本轮必须有戏份
- 所有成员必须被提及，名字和人设必须与前文一致，严禁替换或遗漏

【叙事质量】
- 不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 在细微动作和环境中透露角色关系和情感状态
- 对话推动剧情，旁白渲染氛围
- 制造"可看性"——让读者能想象出这个场景的画面

【人设表达要真实】
- 永远不要让角色在对话中直接提及自己的职业标签、特殊物品、习惯
- 角色的性格和爱好只能通过行为、语气、关注点来间接体现

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色，禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发
- 角色之间的对话中用"对方""博士""队长"等第三人称称呼，不用"你""我"

直接输出过程叙事。
""".trimIndent()
                    val sb = StringBuilder()
                    withTimeout(20_000) { streamChat(listOf(Message("system", prompt)), "Dispatch").collect { sb.append(it) } }
                    trackTokens("dispatch", prompt, sb.toString())
                    val existing = repository.getDispatch(dispatchId)
                    val newLog = (existing?.logChain ?: "") + "\n\n【第${roundNum}轮】" + sb.toString()
                    repository.updateDispatch(dispatchId, newLog, "active")
                    return@launch
                } catch (_: Exception) {
                    if (attempt < 2) delay(1000L * (attempt + 1))
                }
            }
        }
    }

    fun generateDispatchEnd(dispatchId: String, taskType: String, duration: Int, budget: Int, operatorIds: List<String>) {
        viewModelScope.launch {
            val dispatch = repository.getDispatch(dispatchId) ?: return@launch
            val ops = operatorIds.mapNotNull { repository.getOperator(it) }
            val names = ops.joinToString("、") { it.name }
            val memberCount = ops.size
            val profiles = ops.joinToString("\n") { "${it.name}：${it.description}" }
            repeat(3) { attempt ->
                try {
                    val dMn = intPref("dispatch_min_chars", 150); val dMx = intPref("dispatch_max_chars", 400)
                    val prompt = """
你是罗德岛的战术记录员，也是冒险小说作家。为即将结束的派遣行动撰写结局。

【派遣信息】
任务类型：${taskType}
耗时：${duration}小时
投入预算：${budget}龙门币
小队成员：${names}（共${memberCount}人，所有成员必须在结局中提及归队情况）

【完整日志摘要】
${dispatch.logChain.take(200)}

【成员档案】
${profiles}

【写作要求】
- ${dMn}~${dMx}字结局叙事
- 描写小队返回罗德岛的场景：疲惫、收获、伤病、意外发现
- 结局有情绪收束：疲惫后的欣慰、失落中的意外收获、一个被当宝贝捡回来的废品
- 描述本次任务获得的所有物品

【叙事质量】
- 不是任务汇报，而是小说叙事。有场景、有情绪、有细节
- 结局应该有回响——呼应开局埋下的悬念

【人称约束 - 必须遵守】
- 全文使用角色名字称呼角色，禁止使用"我""我的""我们""咱们"等第一人称代词
- 叙事视角始终为上帝视角/第三人称叙述者，不从任何一个角色的第一人称视角出发

【输出格式 · 最高优先级】
严格输出以下JSON对象：
{
  "ending_content": "结局叙事内容",
  "items": ["获得的物品1", "获得的物品2"],
  "currency_reward": 物品卖出后的龙门币总额,
  "net_profit": 净收益
}

【输出字段解释】
- ending_content：结局叙事文本，${dMn}~${dMx}字
- items：字符串数组，列出所有获得的物资。如果一无所获，写空数组[]
- currency_reward：所有物品卖出后的龙门币总额，整数。必须是0~${budget * 10}之间的整数。items为空时必须为0
- net_profit：净收益，整数。必须等于 currency_reward - ${budget}。可以为负数（代表亏损）

【财务验证 · 需在输出前自行确认】
1. currency_reward 是否在 0 到 ${budget * 10} 之间？
2. net_profit 是否等于 currency_reward - ${budget}？
3. 如果 items 为空数组，currency_reward 是否为0？
如果以上任何一条不满足，请修正后再输出。

直接输出JSON对象。
""".trimIndent()
                    val sb = StringBuilder()
                    withTimeout(20_000) { streamChat(listOf(Message("system", prompt))).collect { sb.append(it) } }
                    trackTokens("dispatch", prompt, sb.toString())
                    val cleaned = DeepSeekClient.cleanJson(sb.toString().trim())
                    val ending = try { com.google.gson.Gson().fromJson(cleaned, DispatchEnd::class.java) } catch (_: Exception) { null }
                    val rawReward = (ending?.currency_reward ?: 0).coerceIn(0, budget * 10)
                    val netProfit = rawReward - budget
                    repository.updateDispatch(dispatchId, dispatch.logChain + "\n\n【结局】${ending?.ending_content ?: sb.toString()}", "finished", System.currentTimeMillis(), netProfit)
                    return@launch
                } catch (_: Exception) {
                    if (attempt < 2) delay(1000L * (attempt + 1))
                }
            }
        }
    }

    fun generateDiary(operatorId: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val op = repository.getOperator(operatorId) ?: run { onResult("__NOT_FOUND__"); return@launch }
            if (getSavedApiKey().isBlank()) { onResult("__NO_API_KEY__"); return@launch }
            val profile = getUserProfile()
            // 30天清理
            val cleanCutoff = System.currentTimeMillis() - 30L * 86400000L
            repository.deleteOldDiaries(cleanCutoff)
            try {
                // 昨天日期
                val yesterdayCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
                yesterdayCal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                val yesterdayDate = beijingSdf("yyyy-MM-dd").format(yesterdayCal.time)
                val yesterdayDisplay = beijingSdf("yyyy年MM月dd日").format(yesterdayCal.time)
                val todayDisplay = beijingSdf("yyyy年MM月dd日").format(java.util.Date())
                // 每日一篇检查
                if (repository.getDiary(operatorId, yesterdayDate) != null) {
                    onResult("昨天已写")
                    return@launch
                }
                val groupSummaries = _allSessions.value.filter {
                    it.operatorId.startsWith("group_") && (it.members.contains(operatorId) || it.members.contains(op.name))
                }.mapNotNull { repository.getShortTermMemory(it.id)?.content?.let { c -> "- ${it.operatorName}：${c.take(80)}" } }
                    .joinToString("\n").ifBlank { "无" }
                // 昨天范围内的锚点
                val dayStart = yesterdayCal.timeInMillis
                yesterdayCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
                yesterdayCal.set(java.util.Calendar.MINUTE, 59)
                val dayEnd = yesterdayCal.timeInMillis
                val yesterdayAnchors = repository.getAnchors(operatorId).filter { it.createdAt in dayStart..dayEnd }
                val recentMemories = pickAnchors(yesterdayAnchors, 3)
                    .joinToString("\n") { "- ${anchorTimeLabel(it)} ${it.content}" }.ifBlank { "昨天没有什么特别的事" }
                val diaryTpl = getPromptTemplate("diary")
                val dReplacements = mapOf(
                    "OPERATOR_NAME" to op.name,
                    "OPERATOR_PERSONA" to (op.privatePrompt.ifBlank { op.description }),
                    "CURRENT_DATE" to todayDisplay,
                    "YESTERDAY_DATE" to yesterdayDisplay,
                    "DIARY_MIN_CHARS" to intPref("diary_min_chars", 50).toString(),
                    "DIARY_MAX_CHARS" to intPref("diary_max_chars", 500).toString(),
                    "USER_NAME" to profile.nickname,
                    "USER_BIO" to profile.bio,
                    "USER_RELATION" to (op.userRelation.ifBlank { "未知" }),
                    "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
                    "LONG_TERM_IMPRESSION" to (repository.getLongTermImpression(operatorId)?.content ?: "无"),
                    "PRIVATE_SUMMARY" to (repository.getPrivateChatSummary(operatorId)?.take(200) ?: "无"),
                    "GROUP_SUMMARIES" to groupSummaries,
                    "RECENT_MEMORIES" to recentMemories,
                    "RELATION_EVENTS" to getRelationEvents(operatorId)
                )
                val prompt = applyTemplate(diaryTpl, dReplacements)
                val sb = StringBuilder()
                withTimeout(25_000) { streamChat(listOf(Message("system", prompt))).collect { sb.append(it) } }
                    trackTokens("diary", prompt, sb.toString())
                val text = sb.toString().trim()
                if (text.isNotBlank()) {
                    repository.insertDiary(DiaryEntity(operatorId = operatorId, operatorName = op.name, content = text, date = yesterdayDate))
                    for (observer in _operators.value.filter { it.id != operatorId }.shuffled().take(3)) {
                        repository.saveAnchor(com.example.rhodesterminal.data.db.entity.MemoryAnchorEntity(
                            sessionId = "anchor_${System.currentTimeMillis()}",
                            operatorId = observer.id,
                            type = com.example.rhodesterminal.data.db.entity.AnchorType.EVENT,
                            content = "${op.name}今天写了日记，似乎提到了${profile.nickname}",
                            isPrivate = false
                        ))
                    }
                    onResult(text)
                } else { onResult("__AI_FAILED__") }
            } catch (_: Exception) { onResult("__AI_FAILED__") }
        }
    }

    data class DataStats(val chatSessions: Int, val groups: Int, val diaries: Int, val anchors: Int, val messages: Int, val operators: Int, val moments: Int = 0, val dispatches: Int = 0)

    suspend fun getDataStats(): DataStats = DataStats(
        chatSessions = repository.getSessionCount(),
        groups = repository.getGroupCount(),
        diaries = repository.getDiaryCount(),
        anchors = repository.getAnchorCount(),
        messages = repository.getMessageCount(),
        operators = _operators.value.size,
        moments = _moments.value.size,
        dispatches = repository.getHistoryDispatches().size
    )

    fun cleanupAllExpired() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("chat_prefs", 0)
            val now = System.currentTimeMillis()
            // 聊天记录（旧摘要）：clean_days_messages 默认30天
            val msgDays = prefs.getInt("clean_days_messages", 30)
            val msgCutoff = now - msgDays * 86400000L
            repository.enforceMemoryRetain("", 0)  // 清理旧摘要，保留最近15条
            // 锚点：clean_days_anchors 默认3天
            val anchorDays = prefs.getInt("clean_days_anchors", 3)
            val anchorCutoff = now - anchorDays * 86400000L
            repository.deleteOldAnchors(anchorCutoff)
            // 日记：clean_days_diaries 默认30天
            val diaryDays = prefs.getInt("clean_days_diaries", 30)
            val diaryCutoff = now - diaryDays * 86400000L
            repository.deleteOldDiaries(diaryCutoff)
            // 动态：clean_days_moments 默认30天
            val momentDays = prefs.getInt("clean_days_moments", 30)
            val momentCutoff = now - momentDays * 86400000L
            repository.deleteOldMoments(momentCutoff)
            // 派遣历史：clean_days_dispatches 默认30天
            val dispatchDays = prefs.getInt("clean_days_dispatches", 30)
            val dispatchCutoff = now - dispatchDays * 86400000L
            repository.deleteOldDispatches(dispatchCutoff)
        }
    }

    suspend fun getMessageRanking(): List<SenderCount> = repository.getMessageCountPerSender()

    suspend fun getAllImpressions(): List<MemoryEntity> = repository.getAllLongTermImpressions()
    suspend fun deleteAllImpressions() = repository.deleteAllImpressions()

    fun getLatestMomentId(): Long = _moments.value.firstOrNull()?.id ?: 0

    /** 未读提醒数：AI 新动态 + AI 回复用户的消息 */
    fun getMomentBadge(): Int {
        val prefs = getApplication<Application>().getSharedPreferences("moment_prefs", 0)
        val lastSeenMoment = prefs.getLong("last_seen_moment_id", 0)
        val latest = _moments.value.firstOrNull()?.id ?: 0
        val momentBadge = if (latest > lastSeenMoment) (_moments.value.count { it.id > lastSeenMoment && !it.isUserPost }) else 0
        val commentBadge = getUnreadCommentCount()
        return momentBadge + commentBadge
    }

    /** 未读消息数：30天内 isRead=0 的评论 */
    fun getUnreadCommentCount(): Int {
        val profile = getUserProfile()
        val cutoff = System.currentTimeMillis() - 30L * 86400000L
        return try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                repository.getUnreadCommentCount(cutoff, profile.nickname)
            }
        } catch (e: Exception) {
            if (DEBUG) Log.e("AI调试输出", "getUnreadCommentCount error: ${e.message}")
            0
        }
    }

    /** 加载收件箱（30天内的所有相关评论，isRead 仅控制红点） */
    fun loadInboxComments(callback: (List<MomentCommentEntity>) -> Unit) {
        viewModelScope.launch {
            val profile = getUserProfile()
            val cutoff = System.currentTimeMillis() - 30L * 86400000L
            val comments = repository.getInboxComments(cutoff, profile.nickname)
            callback(comments)
        }
    }

    /** 标记动态已读 */
    fun markMomentsSeen() {
        val latest = _moments.value.firstOrNull()?.id ?: 0
        getApplication<Application>().getSharedPreferences("moment_prefs", 0).edit().putLong("last_seen_moment_id", latest).apply()
    }

    /** 标记单条评论已读 */
    fun markCommentRead(commentId: Long) {
        viewModelScope.launch { repository.markCommentRead(commentId) }
    }

    /** 全部已读 + 清理30天前的评论 */
    fun markAllCommentsRead() {
        viewModelScope.launch {
            val userName = getUserProfile().nickname
            repository.markAllCommentsRead(userName)
            val cutoff = System.currentTimeMillis() - 30L * 86400000L
            repository.deleteOldUserComments(cutoff, userName)
            val maxId = repository.getMaxCommentId()
            if (maxId != null && maxId > 0) {
                getApplication<Application>().getSharedPreferences("moment_prefs", 0).edit().putLong("last_seen_comment_id", maxId).apply()
                }
            }
        }
    }

data class DispatchSegment(val type: String = "", val content: String = "", val operator_states: List<DispatchOperatorState>? = null)
data class DispatchOperatorState(val name: String = "", val emotion: String = "")
data class DispatchResponse(val segments: List<DispatchSegment>? = null, val items: List<String>? = null, val currency_reward: Int? = 0, val net_profit: Int? = 0)
data class GroupMsgResult(val speaker: String = "", val message: String = "", val type: String = "dialogue")
data class DispatchEnd(val ending_content: String = "", val items: List<String> = emptyList(), val currency_reward: Int = 0, val net_profit: Int = 0)
data class SuggestionResponse(val suggestions: List<String> = emptyList())
data class ImpressionResponse(val impression: String = "", val keywords: List<String> = emptyList(), val preferences: List<String> = emptyList(), val taboos: List<String> = emptyList())

