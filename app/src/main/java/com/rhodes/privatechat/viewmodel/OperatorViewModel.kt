package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.model.Relationship
import com.rhodes.privatechat.shared.data.BfsNode
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class OperatorViewModel(
    private val repository: ChatRepository,
    private val settings: SettingsRepository,
    private val appState: AppStateHolder,
    private val scope: CoroutineScope,
    private val onSessionDeleting: (String) -> Unit = {},
    private val onSelectedOperatorUpdated: ((Operator?) -> Unit)? = null
) {
    fun saveOperator(
        id: String, name: String, title: String = "", description: String,
        privatePrompt: String = "", groupPrompt: String = "",
        memoryInjection: String = "",
        userRelation: String = "", avatarUri: String = "",
        autoPost: Boolean = true, allowChat: Boolean = true,
        relationships: List<Relationship> = emptyList(),
        activityLevel: Float = 0.5f,
        gender: String = "",
        voiceName: String = "",
        voiceSpeed: String = "",
        voicePitch: String = "",
        onComplete: (String?) -> Unit = {}
    ) {
        scope.launch {
            try {
                val cleanName = name.trim()
                if (id.isBlank()) {
                    onComplete("干员内部编号无效，请重新进入新建页面")
                    return@launch
                }
                if (cleanName.isBlank()) {
                    onComplete("请输入干员名称")
                    return@launch
                }
                val duplicate = appState.getOperatorsSnapshot().firstOrNull {
                    it.id != id && it.name.trim().equals(cleanName, ignoreCase = true)
                }
                if (duplicate != null && !existingNameMatches(id, cleanName)) {
                    onComplete("已存在名为“$cleanName”的干员，请使用其他名称")
                    return@launch
                }
                val existing = repository.getOperator(id)
                val op = Operator(
                    id = id, name = cleanName, title = title,
                    description = description, gender = gender,
                    location = existing?.location ?: "宿舍",
                    activity = existing?.activity ?: "休息", emotion = existing?.emotion ?: "平静",
                    intimacy = existing?.intimacy ?: 0,
                    privatePrompt = if (privatePrompt.isNotBlank()) privatePrompt else existing?.privatePrompt ?: "",
                    groupPrompt = if (groupPrompt.isNotBlank()) groupPrompt else existing?.groupPrompt ?: "",
                    memoryInjection = memoryInjection,
                    userRelation = if (userRelation.isNotBlank()) userRelation else existing?.userRelation ?: "",
                    avatarUri = if (avatarUri.isNotBlank()) avatarUri else existing?.avatarUri ?: "",
                    lmb = existing?.lmb ?: 10000,
                    attack = existing?.attack ?: 0.5f,
                    defense = existing?.defense ?: 0.5f,
                    meldPref = existing?.meldPref ?: "medium",
                    activityLevel = activityLevel,
                    voiceName = voiceName.ifBlank { existing?.voiceName ?: "" },
                    voiceSpeed = voiceSpeed.ifBlank { existing?.voiceSpeed ?: "" },
                    voicePitch = voicePitch.ifBlank { existing?.voicePitch ?: "" }
                )
                repository.insertOperator(op)
                repository.syncOperatorAvatar(id, op.avatarUri)
                repository.syncOperatorName(id, cleanName)
                settings.putOperatorDynPermission(id, autoPost)
                settings.putOperatorMsgPermission(id, allowChat)
                repository.deleteRelationshipByOperator(id)
                for (rel in relationships) {
                    repository.insertRelationship(rel.copy(operatorId = id))
                }
                onSelectedOperatorUpdated?.invoke(repository.getOperator(id))
                onComplete(null)
            } catch (e: Exception) {
                onComplete("保存失败：${e.message?.take(80) ?: "请稍后重试"}")
            }
        }
    }

    private fun existingNameMatches(id: String, name: String): Boolean =
        appState.getOperatorsSnapshot().firstOrNull { it.id == id }?.name?.trim().equals(name, ignoreCase = true)

    fun deleteOperator(operatorId: String) {
        deleteOperators(listOf(operatorId))
    }

    fun deleteOperators(operatorIds: Collection<String>, onComplete: (String?) -> Unit = {}) {
        val ids = operatorIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) {
            onComplete("未选择可删除的干员")
            return
        }
        scope.launch {
            try {
                ids.forEach { operatorId ->
                    val session = repository.getSessionByOperator(operatorId)
                    if (session != null) {
                        onSessionDeleting(session.id)
                        repository.purgeSessionData(session.id)
                        repository.deleteSession(session.id)
                    }
                    repository.purgeOperatorData(operatorId)
                    repository.deleteOperator(operatorId)
                    settings.remove("dyn_$operatorId")
                    settings.remove("msg_$operatorId")
                    settings.putStringSet(
                        "deleted_preset_operator_ids",
                        settings.getStringSet("deleted_preset_operator_ids") + operatorId
                    )
                }
                onComplete(null)
            } catch (e: Exception) {
                onComplete("删除失败：${e.message?.take(80) ?: "请稍后重试"}")
            }
        }
    }

    fun loadRelationships(operatorId: String, callback: (List<Relationship>) -> Unit) {
        scope.launch { callback(repository.getRelationships(operatorId)) }
    }

    fun saveRelationship(rel: Relationship, reciprocal: Relationship? = null, onComplete: () -> Unit = {}) {
        scope.launch {
            repository.insertRelationship(rel)
            reciprocal?.let { repository.insertRelationship(it) }
            onComplete()
        }
    }

    fun loadRelationGraph(operatorId: String, callback: (List<BfsNode>) -> Unit) {
        scope.launch { callback(repository.bfsRelationGraph(operatorId)) }
    }

    fun loadSharedMemories(operatorId: String, callback: (String) -> Unit) {
        scope.launch { callback(repository.getSharedMemoriesForOperator(operatorId)) }
    }
}
