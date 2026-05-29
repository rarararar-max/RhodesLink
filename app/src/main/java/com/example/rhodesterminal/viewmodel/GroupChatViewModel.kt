package com.example.rhodesterminal.viewmodel

import android.util.Log
import com.example.rhodesterminal.shared.model.AnchorType
import com.example.rhodesterminal.shared.model.ChatMessage
import com.example.rhodesterminal.shared.model.ChatSession
import com.example.rhodesterminal.shared.model.MemoryAnchor
import com.example.rhodesterminal.shared.model.Operator
import com.example.rhodesterminal.shared.model.RelationshipType
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.shared.network.AIService
import com.example.rhodesterminal.shared.model.AiMessage
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.Prefs
import com.example.rhodesterminal.viewmodel.shared.SharedUtils
import com.example.rhodesterminal.viewmodel.shared.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

class GroupChatViewModel(
    private val repository: ChatRepository,
    private val prefs: Prefs,
    private val sharedUtils: SharedUtils,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val markSessionRead: (String) -> Unit,
    private val unhideSession: suspend (String) -> Unit,
    private val getUserProfile: () -> UserProfile,
    private val getPromptTemplate: (String, String) -> String,
    private val generateShortTermSummary: suspend (ChatSession, List<ChatMessage>?) -> Unit,
    private val sessionMessageCounter: MutableMap<String, Int>
) {
    companion object {
        const val DEBUG = true
    }

    private val groupActivityCache = mutableMapOf<String, String>()
    private val _groupMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val groupMessages: StateFlow<List<ChatMessage>> = _groupMessages.asStateFlow()

    private val _groupLoading = MutableStateFlow(false)
    val groupLoading: StateFlow<Boolean> = _groupLoading.asStateFlow()

    private val _currentGroupId = MutableStateFlow("")
    val currentGroupId: StateFlow<String> = _currentGroupId.asStateFlow()

    private var groupMessagesJob: Job? = null
    private var groupMessageMutex = Mutex()

    // 自动群聊
    private val autoGroupChatJobs = mutableMapOf<String, Job>()
    private val autoChatGenerations = mutableMapOf<String, Long>()
    private val lastUserMsgTime = mutableMapOf<String, Long>()

    fun setCurrentGroup(groupSessionId: String) {
        _currentGroupId.value = groupSessionId
        markSessionRead(groupSessionId)
        groupMessagesJob?.cancel()
        groupMessagesJob = scope.launch {
            repository.getMessages(groupSessionId).collect { _groupMessages.value = it }
        }
    }

    fun clearCurrentGroup() {
        _currentGroupId.value = ""
        groupMessagesJob?.cancel()
        _groupMessages.value = emptyList()
        groupActivityCache.clear()
    }

    fun removeMessage(msgId: Long) {
        _groupMessages.value = _groupMessages.value.filter { it.id != msgId }
    }

    fun deleteGroup(groupSessionId: String) {
        stopAutoGroupChat(groupSessionId)
        prefs.chat.edit().remove("group_auto_$groupSessionId").apply()
        scope.launch {
            repository.deleteSessionMessages(groupSessionId)
            repository.deleteSession(groupSessionId)
        }
    }

    fun isAutoGroupChatEnabled(groupId: String): Boolean =
        prefs.chat.getBoolean("group_auto_$groupId", false)

    fun setAutoGroupChatEnabled(groupId: String, enabled: Boolean) {
        prefs.chat.edit().putBoolean("group_auto_$groupId", enabled).apply()
        if (enabled) {
            scope.launch {
                val session = repository.getSession(groupId)
                if (session != null) startAutoGroupChat(groupId, session.operatorName)
            }
        } else {
            stopAutoGroupChat(groupId)
        }
    }

    private fun startAutoGroupChat(groupId: String, groupName: String) {
        stopAutoGroupChat(groupId)
        val generation = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoChatGenerations[groupId] = generation
        val minMs = prefs.intPref("group_chat_min_interval", 30) * 1000L
        val maxMs = prefs.intPref("group_chat_max_interval", 120) * 1000L
        autoGroupChatJobs[groupId] = scope.launch {
            val sinceLastMsg = System.currentTimeMillis() - (lastUserMsgTime[groupId] ?: 0L)
            val firstDelay = if (sinceLastMsg < 30_000) 30_000 - sinceLastMsg else 10_000L
            delay(firstDelay)
            while (isAutoGroupChatEnabled(groupId)) {
                if (autoChatGenerations[groupId] != generation) break
                val session = repository.getSession(groupId) ?: break
                val mode = getGroupChatMode(groupId)
                sendGroupMessage(groupId, groupName, "", mode, isAuto = true)
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

    fun resetAutoGroupChatTimer(groupId: String) {
        lastUserMsgTime[groupId] = System.currentTimeMillis()
        autoChatGenerations[groupId] = (autoChatGenerations[groupId] ?: 0L) + 1L
        autoGroupChatJobs[groupId]?.cancel()
        autoGroupChatJobs.remove(groupId)
        scope.launch {
            val session = repository.getSession(groupId)
            if (session != null && isAutoGroupChatEnabled(groupId)) {
                startAutoGroupChat(groupId, session.operatorName)
            }
        }
    }

    private fun getGroupChatMode(groupId: String): String =
        prefs.chat.getString("group_mode_$groupId", "online") ?: "online"

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
        val cp = prefs.chat
        appState.sessions.value.filter { it.operatorId.startsWith("group_") || it.operatorId.startsWith("group") }.forEach { group ->
            if (cp.getBoolean("group_auto_${group.id}", false)) {
                startAutoGroupChat(group.id, group.operatorName)
            } else {
                stopAutoGroupChat(group.id)
            }
        }
    }

    fun sendGroupMessage(groupSessionId: String, groupName: String, text: String, mode: String = "online", autoSpeak: Boolean = false, isAuto: Boolean = false) {
        scope.launch {
            if (!groupMessageMutex.tryLock()) return@launch
            _groupLoading.value = true
            if (!isAuto && text.isNotBlank()) {
                val userMsgId = repository.getNextMessageId()
                repository.sendMessage(groupSessionId, ChatMessage(
                    id = userMsgId, sessionId = groupSessionId,
                    senderName = "我", content = text, type = "text", mode = mode, isMe = true
                ))
                resetAutoGroupChatTimer(groupSessionId)
            }
            try {
                val session = repository.getSession(groupSessionId) ?: run { _groupLoading.value = false; return@launch }
                val memberIds = session.members.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val allOps = appState.operators.value
                val members = memberIds.mapNotNull { id -> allOps.find { it.id == id || it.name == id } }
                val mutedIds = session.mutedMembers.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                val activeMembers = members.filter { it.id !in mutedIds && it.name !in mutedIds }

                val profile = getUserProfile()
                val relContext = getGroupRelationshipContext(activeMembers)
                val relationHints = if (relContext.isNotBlank()) relContext else "无"
                val memberPrivateContext = if (!isAuto) {
                    buildString {
                        for (m in activeMembers) {
                            val ctx = repository.getPrivateChatContext(m.id)
                            if (ctx != null) append("- ${m.name}：${ctx}\n")
                            else append("- ${m.name}：暂无特别的互动\n")
                        }
                    }.toString()
                } else ""
                val groupSummary = repository.getShortTermMemory(groupSessionId)?.content ?: ""
                val longTermImpression = if (!isAuto && activeMembers.isNotEmpty()) {
                    activeMembers.take(5).mapNotNull { m ->
                        repository.getLongTermImpression(m.id)?.content?.let { "- ${m.name}对${profile.nickname}的印象：${it.take(100)}" }
                    }.joinToString("\n").ifBlank { "成员们对${profile.nickname}尚无深入了解。" }
                } else ""
                val memberProfiles = buildString {
                    for (m in activeMembers.shuffled()) {
                        val key = "${groupSessionId}_${m.id}"
                        val act = groupActivityCache.getOrPut(key) { "活跃${"%.1f".format(0.5 + Math.random() * 0.5)}" }
                        val titleStr = if (m.title.isBlank()) "" else "，${m.title}"
                        append("${m.name}（${act}${titleStr}）：${m.groupPrompt.ifBlank { m.description }}\n")
                    }
                }
                val userMessage = if (isAuto) "" else if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                val grpTpl = getPromptTemplate("group", if (isAuto) "auto" else mode)
                val userObserving = if (isAuto) when (mode) {
                    "offline" -> "用户坐在一旁，安静地听着大家的对话，没有插话。"
                    "director" -> "用户作为导演正在观察大家的表演，没有给出新指令。"
                    else -> "群内用户正在安静地观察，没有发言。"
                } else ""
                val grpModeFormat = if (isAuto) when (mode) {
                    "offline", "director" -> "\n允许旁白条目（speaker为\"旁白\"，type为\"narration\"），对话条目type为\"dialogue\"。"
                    else -> ""
                } else ""
                val now = sharedUtils.beijingSdf("yyyy-MM-dd HH:mm").format(java.util.Date())
                val grpReplacements = mapOf(
                    "CURRENT_TIME" to now, "GROUP_NAME" to groupName,
                    "GROUP_RULES" to (session.rules.ifBlank { "无" }),
                    "USER_NAME" to profile.nickname, "USER_GENDER" to profile.gender.ifBlank { "未知" },
                    "USER_BIO" to profile.bio.ifBlank { "无" }, "RELATION_HINTS" to relationHints,
                    "MEMBER_PRIVATE_CONTEXT" to memberPrivateContext,
                    "SHORT_TERM_SUMMARY" to groupSummary, "GROUP_SUMMARY" to groupSummary,
                    "DAILY_SUMMARY" to (repository.getLatestDaily()?.content ?: "无"),
                    "LONG_TERM_IMPRESSION" to longTermImpression,
                    "MEMBER_PROFILES" to memberProfiles.toString(),
                    "GROUP_NAR_SEG_MIN" to prefs.intPref("group_nar_seg_min", 1).toString(),
                    "GROUP_NAR_SEG_MAX" to prefs.intPref("group_nar_seg_max", 3).toString(),
                    "GROUP_NAR_MIN" to prefs.intPref("group_nar_min", 20).toString(),
                    "GROUP_NAR_MAX" to prefs.intPref("group_nar_max", 50).toString(),
                    "GROUP_MSG_MIN" to prefs.intPref("group_msg_min", 10).toString(),
                    "GROUP_MSG_MAX" to prefs.intPref("group_msg_max", 80).toString(),
                    "GROUP_SPEECH_MIN" to prefs.intPref("group_speech_min", 1).toString(),
                    "GROUP_SPEECH_MAX" to prefs.intPref("group_speech_max", 2).toString(),
                    "USER_MESSAGE" to userMessage, "USER_OBSERVING" to userObserving,
                    "GROUP_MODE_FORMAT" to grpModeFormat
                )
                val systemPrompt = sharedUtils.applyTemplate(grpTpl, grpReplacements)
                val apiMessages = mutableListOf(AiMessage("system", systemPrompt))
                val historyLimit = prefs.intPref("history_messages", 30)
                val allHistory = repository.getMessagesSync(groupSessionId).let { msgs ->
                    if (historyLimit > 0) msgs.takeLast(historyLimit) else msgs
                }
                for (msg in allHistory) {
                    val role = if (msg.isMe) "user" else "assistant"
                    val content = if (msg.isMe) "用户：${msg.content}" else msg.content
                    apiMessages.add(AiMessage(role, content))
                }
                if (!isAuto) {
                    val userMsg = if (autoSpeak) "（群聊已空闲一段时间，干员们自然地闲聊起来，无需等待用户发言。）" else text
                    apiMessages.add(AiMessage("user", "用户：$userMsg"))
                }
                val promptText = apiMessages.firstOrNull()?.content ?: ""
                if (DEBUG) sharedUtils.logAiCall("GroupChat", promptText, "(streaming...)", apiMessages)
                val sb = StringBuilder()
                withTimeout(25_000) {
                    val temp = prefs.intPref("ai_temperature", 95).toDouble() / 100.0
                    sharedUtils.streamChat(apiMessages, "GroupChat").collect { sb.append(it) }
                }
                sharedUtils.trackTokens("group", promptText, sb.toString())
                if (DEBUG) sharedUtils.logAiCall("GroupChat", promptText, sb.toString(), apiMessages)

                val rawBase = sb.toString().trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                var results: List<GroupMsgResult> = emptyList()
                for (cleaned in listOf(rawBase, rawBase.replace("，", ",").replace("：", ":"))) {
                    try {
                        val arr = com.google.gson.Gson().fromJson(cleaned, Array<GroupMsgResult>::class.java)
                        results = arr?.toList() ?: emptyList()
                        if (results.isNotEmpty()) break
                    } catch (_: Exception) {}
                }
                val filtered = results.filter { it.message.isNotBlank() }
                if (filtered.isNotEmpty()) {
                    val aiMsgId = repository.getNextMessageId()
                    repository.sendMessage(groupSessionId, ChatMessage(
                        id = aiMsgId, sessionId = groupSessionId,
                        senderName = groupName, content = rawBase,
                        type = "ai_json", mode = mode, isMe = false
                    ))
                    for (r in filtered) {
                        val anchorOp = if (r.speaker == "旁白" || r.speaker == "系统") null
                        else allOps.find { it.name == r.speaker }
                        if (anchorOp != null) {
                            repository.saveAnchor(MemoryAnchor(
                                sessionId = "anchor_${System.currentTimeMillis()}",
                                operatorId = anchorOp.id, type = AnchorType.EVENT,
                                content = "在群聊「${groupName}」中${r.speaker}说：${r.message.take(40)}",
                                isPrivate = false
                            ))
                        }
                    }
                    val last = filtered.last()
                    repository.updateLastMessage(groupSessionId, "${last.speaker}：${last.message.take(50)}", System.currentTimeMillis())
                }
                if (_currentGroupId.value != groupSessionId && filtered.isNotEmpty()) {
                    val sess = repository.getSession(groupSessionId)
                    if (sess != null) repository.insertSession(sess.copy(unreadCount = sess.unreadCount + 1))
                    unhideSession(groupSessionId)
                }
                val gc = sessionMessageCounter.getOrDefault(groupSessionId, 0) + 1
                sessionMessageCounter[groupSessionId] = gc
                if (gc >= prefs.intPref("summary_threshold", 20) && groupSessionId.isNotBlank()) {
                    val gs = repository.getSession(groupSessionId)
                    if (gs != null) {
                        val freshMsgs = repository.getMessagesSync(gs.id)
                        generateShortTermSummary(gs, freshMsgs)
                        sessionMessageCounter[groupSessionId] = 0
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("GroupChat", "Timeout: ${e.message}")
                repository.sendMessage(groupSessionId, ChatMessage(id = repository.getNextMessageId(), sessionId = groupSessionId, senderName = "系统", content = "响应超时，请重试", type = "system", mode = mode, isMe = false))
            } catch (e: Exception) {
                Log.e("GroupChat", "Error: ${e.message}", e)
                repository.sendMessage(groupSessionId, ChatMessage(id = repository.getNextMessageId(), sessionId = groupSessionId, senderName = "系统", content = "连接失败", type = "system", mode = mode, isMe = false))
            } finally {
                _groupLoading.value = false
                groupMessageMutex.unlock()
            }
        }
    }

    private suspend fun getGroupRelationshipContext(members: List<Operator>): String {
        val lines = mutableListOf<String>()
        for (i in members.indices) {
            for (j in i + 1 until members.size) {
                val a = members[i]; val b = members[j]
                val rel = repository.getRelationship(a.id, b.id)
                if (rel != null && rel.type != RelationshipType.STRANGER) {
                    val desc = sharedUtils.relationshipGroupDesc(a.name, b.name, rel.type)
                    lines.add("- $desc（亲密${rel.intimacy}）")
                }
            }
        }
        return lines.joinToString("\n")
    }
}
