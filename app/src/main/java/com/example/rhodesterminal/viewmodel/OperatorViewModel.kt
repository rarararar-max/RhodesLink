package com.example.rhodesterminal.viewmodel

import com.example.rhodesterminal.shared.model.Operator
import com.example.rhodesterminal.shared.model.Relationship
import com.example.rhodesterminal.shared.data.BfsNode
import com.example.rhodesterminal.shared.data.ChatRepository
import com.example.rhodesterminal.viewmodel.shared.AppStateHolder
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OperatorViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val onSelectedOperatorUpdated: ((Operator?) -> Unit)? = null
) {
    fun saveOperator(
        id: String, name: String, title: String = "", description: String,
        privatePrompt: String = "", groupPrompt: String = "",
        userRelation: String = "", avatarUri: String = "",
        autoPost: Boolean = true, allowChat: Boolean = true,
        relationships: List<Relationship> = emptyList()
    ) {
        scope.launch {
            val existing = repository.getOperator(id)
            val op = Operator(
                id = id, name = name, title = title,
                description = description, location = existing?.location ?: "宿舍",
                activity = existing?.activity ?: "休息", emotion = existing?.emotion ?: "平静",
                intimacy = existing?.intimacy ?: 0,
                privatePrompt = if (privatePrompt.isNotBlank()) privatePrompt else existing?.privatePrompt ?: "",
                groupPrompt = if (groupPrompt.isNotBlank()) groupPrompt else existing?.groupPrompt ?: "",
                userRelation = if (userRelation.isNotBlank()) userRelation else existing?.userRelation ?: "",
                avatarUri = if (avatarUri.isNotBlank()) avatarUri else existing?.avatarUri ?: "",
                lmb = existing?.lmb ?: 10000,
                attack = existing?.attack ?: 0.5f,
                defense = existing?.defense ?: 0.5f,
                meldPref = existing?.meldPref ?: "medium"
            )
            repository.insertOperator(op)
            settings.putOperatorDynPermission(id, autoPost)
            settings.putOperatorMsgPermission(id, allowChat)
            repository.deleteRelationshipByOperator(id)
            for (rel in relationships) {
                repository.insertRelationship(rel.copy(operatorId = id))
            }
            onSelectedOperatorUpdated?.invoke(repository.getOperator(id))
        }
    }

    fun deleteOperator(operatorId: String) {
        scope.launch {
            repository.deleteOperator(operatorId)
            val session = repository.getSessionByOperator(operatorId)
            if (session != null) {
                repository.deleteSessionMessages(session.id)
                repository.deleteSession(session.id)
            }
        }
    }

    fun loadRelationships(operatorId: String, callback: (List<Relationship>) -> Unit) {
        scope.launch { callback(repository.getRelationships(operatorId)) }
    }

    fun loadRelationGraph(operatorId: String, callback: (List<BfsNode>) -> Unit) {
        scope.launch { callback(repository.bfsRelationGraph(operatorId)) }
    }

    fun loadSharedMemories(operatorId: String, callback: (String) -> Unit) {
        scope.launch { callback(repository.getSharedMemoriesForOperator(operatorId)) }
    }
}
