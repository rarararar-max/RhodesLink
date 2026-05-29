package com.example.rhodesterminal.viewmodel

import com.example.rhodesterminal.shared.model.ChatSession
import com.example.rhodesterminal.shared.model.Operator
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.viewmodel.shared.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: ChatRepository,
    private val prefs: Prefs,
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
            val ids = appState.sessions.value.map { it.id }.toSet()
            prefs.hidden.edit().putStringSet("hidden_ids", ids).apply()
            appState.clearSessions()
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

    fun loadGroupData(groupId: String, callback: (String, List<Operator>, String) -> Unit) {
        scope.launch {
            val session = repository.getSession(groupId) ?: run { callback("", emptyList(), ""); return@launch }
            val memberNames = session.members.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val allOps = appState.operators.value
            val memberOps = memberNames.mapNotNull { name -> allOps.find { it.id == name || it.name == name } }
            callback(session.operatorName, memberOps, session.rules ?: "")
        }
    }

    fun saveGroup(groupId: String, name: String, memberNames: List<String>, rules: String, avatarUri: String = "", mutedMembers: List<String> = emptyList()) {
        scope.launch {
            val id = groupId.ifBlank { "group_${System.currentTimeMillis()}" }
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
                val parts = mutableListOf<String>()
                if (added.isNotEmpty()) parts.add("欢迎新成员：${added.joinToString("、")}加入群聊。")
                if (removed.isNotEmpty()) parts.add("以下成员已离开：${removed.joinToString("、")}。")
                val sysId = repository.getNextMessageId()
                repository.sendMessage(id, com.example.rhodesterminal.shared.model.ChatMessage(
                    id = sysId, sessionId = id,
                    senderName = "系统", content = parts.joinToString("\n"),
                    type = "system", mode = "online", isMe = false
                ))
            }
        }
    }
}
