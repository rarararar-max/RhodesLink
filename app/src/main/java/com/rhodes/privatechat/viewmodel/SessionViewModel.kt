package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope
) {
    fun findOperatorByName(name: String): Operator? = appState.findOperatorByName(name)

    fun deleteSession(sessionId: String) {
        scope.launch {
            repository.deleteSessionMessages(sessionId)
            repository.deleteSession(sessionId)
        }
    }

    fun clearAllMessages() {
        scope.launch {
            val sessions = appState.sessions.value.toList()
            val hidden = settings.hiddenIds.toMutableSet()
            for (session in sessions) {
                hidden.add(session.id)
            }
            settings.hiddenIds = hidden
            appState.clearChatListOnly()
        }
    }

    fun markAllRead() {
        scope.launch { repository.markAllRead() }
    }

    fun pinSession(sessionId: String) {
        scope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            repository.insertSession(session.copy(isPinned = !session.isPinned))
        }
    }

    fun markSessionRead(sessionId: String) {
        scope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            repository.insertSession(session.copy(unreadCount = 0))
        }
    }

    fun loadGroupData(groupId: String, callback: (String, List<Operator>, String, Set<String>) -> Unit) {
        scope.launch {
            val session = repository.getSession(groupId) ?: run { callback("", emptyList(), "", emptySet()); return@launch }
            val memberNames = session.members.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val allOps = appState.operators.value
            val memberOps = memberNames.mapNotNull { name -> allOps.find { it.id == name || it.name == name } }
            val mutedSet = session.mutedMembers.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
            callback(session.operatorName, memberOps, session.rules ?: "", mutedSet)
        }
    }

    fun saveGroup(groupId: String, name: String, memberNames: List<String>, rules: String, avatarUri: String = "", mutedMembers: List<String> = emptyList(), onComplete: () -> Unit = {}) {
        scope.launch {
            val id = groupId.ifBlank { "group_${java.util.UUID.randomUUID()}" }
            val existing = if (groupId.isNotBlank()) repository.getSession(groupId) else null
            val oldMembers = existing?.members?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val newMembers = memberNames.toSet()
            val added = newMembers - oldMembers
            val removed = oldMembers - newMembers
            repository.insertSession(ChatSession(
                id = id, operatorId = id, operatorName = name,
                rules = rules, lastTime = System.currentTimeMillis(),
                members = memberNames.joinToString(","),
                avatarUri = if (avatarUri.isNotBlank()) avatarUri else existing?.avatarUri ?: "",
                mutedMembers = mutedMembers.joinToString(",")
            ))
            if (added.isNotEmpty() || removed.isNotEmpty()) {
                val allOps = appState.operators.value
                fun resolveName(id: String) = allOps.find { it.id == id || it.name == id }?.name ?: id
                val parts = mutableListOf<String>()
                if (added.isNotEmpty()) parts.add("欢迎新成员：${added.joinToString("、") { resolveName(it) }}加入群聊。")
                if (removed.isNotEmpty()) parts.add("以下成员已离开：${removed.joinToString("、") { resolveName(it) }}。")
                val sysId = repository.getNextMessageId()
                repository.sendMessage(id, com.rhodes.privatechat.shared.model.ChatMessage(
                    id = sysId, sessionId = id,
                    senderName = "系统", content = parts.joinToString("\n"),
                    type = "system", mode = "online", isMe = false
                ))
            }
            val allSessions = repository.getAllSessionsSync()
            appState.refreshAllSessions(allSessions, settings.hiddenIds)
            onComplete()
        }
    }
}
