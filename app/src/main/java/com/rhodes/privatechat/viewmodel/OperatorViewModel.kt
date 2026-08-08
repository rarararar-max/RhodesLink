package com.rhodes.privatechat.viewmodel

import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.model.Relationship
import com.rhodes.privatechat.shared.data.BfsNode
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.viewmodel.shared.AppStateHolder
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

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
        DebugLogger.diagnostic("Operator/SaveEnqueued", "operatorId=$id, nameLength=${name.trim().length}")
        scope.launch(Dispatchers.IO) {
            try {
                val cleanName = name.trim()
                if (id.isBlank()) {
                    DebugLogger.diagnostic("Operator/SaveBlocked", "reason=blank_id, nameLength=${cleanName.length}, existingOperators=${appState.getOperatorsSnapshot().size}")
                    finish(onComplete, "干员内部编号无效，请重新进入新建页面")
                    return@launch
                }
                if (cleanName.isBlank()) {
                    DebugLogger.diagnostic("Operator/SaveBlocked", "reason=blank_name, operatorId=$id")
                    finish(onComplete, "请输入干员名称")
                    return@launch
                }
                val duplicate = appState.getOperatorsSnapshot().firstOrNull {
                    it.id != id && it.name.trim().equals(cleanName, ignoreCase = true)
                }
                if (duplicate != null && !existingNameMatches(id, cleanName)) {
                    DebugLogger.diagnostic("Operator/SaveBlocked", "reason=duplicate_name, operatorId=$id, duplicateId=${duplicate.id}")
                    finish(onComplete, "已存在名为“$cleanName”的干员，请使用其他名称")
                    return@launch
                }
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=get_existing_start")
                val existing = withTimeout(8_000L) { repository.getOperator(id) }
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=get_existing_done, exists=${existing != null}")
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
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=insert_start")
                withTimeout(8_000L) { repository.insertOperator(op) }
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=insert_done")
                val saved = withTimeout(8_000L) { repository.getOperator(id) }
                if (saved?.id != id) throw IllegalStateException("角色写入后读取不到")
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=insert_readback_done")
                // A new operator has no session yet. These optional syncs must not turn a
                // successful operator insert into a visible save failure on an upgraded DB.
                if (existing != null) {
                    runCatching { withTimeout(8_000L) { repository.syncOperatorAvatar(id, op.avatarUri) } }
                        .onFailure { DebugLogger.diagnostic("Special/OperatorAvatarSyncFailed", "operatorId=$id, error=${it.javaClass.simpleName}:${it.message?.take(120)}") }
                    runCatching { withTimeout(8_000L) { repository.syncOperatorName(id, cleanName) } }
                        .onFailure { DebugLogger.diagnostic("Special/OperatorNameSyncFailed", "operatorId=$id, error=${it.javaClass.simpleName}:${it.message?.take(120)}") }
                }
                settings.putOperatorDynPermission(id, autoPost)
                settings.putOperatorMsgPermission(id, allowChat)
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=relationships_start, count=${relationships.size}")
                runCatching {
                    withTimeout(8_000L) { repository.deleteRelationshipByOperator(id) }
                    for (rel in relationships) {
                        withTimeout(8_000L) { repository.insertRelationship(rel.copy(operatorId = id)) }
                    }
                }.onFailure {
                    DebugLogger.diagnostic("Special/OperatorRelationshipsFailed", "operatorId=$id, error=${it.javaClass.simpleName}:${it.message?.take(160)}")
                }
                DebugLogger.diagnostic("Operator/SaveStep", "operatorId=$id, step=relationships_done")
                runCatching { onSelectedOperatorUpdated?.invoke(saved) }
                    .onFailure { DebugLogger.diagnostic("Special/OperatorSelectionRefreshFailed", "operatorId=$id, error=${it.javaClass.simpleName}:${it.message?.take(160)}") }
                runCatching { appState.refreshOperators(repository.getAllOperatorsSync()) }
                    .onFailure { DebugLogger.diagnostic("Special/OperatorStateRefreshFailed", "operatorId=$id, error=${it.javaClass.simpleName}:${it.message?.take(160)}") }
                DebugLogger.log("Operator/Save", "saved operatorId=$id, isNew=${existing == null}, operatorCount=${appState.getOperatorsSnapshot().size}")
                DebugLogger.diagnostic("Special/OperatorCreateSucceeded", "operatorId=$id, isNew=${existing == null}, operatorCount=${appState.getOperatorsSnapshot().size}")
                finish(onComplete, null)
            } catch (e: Exception) {
                DebugLogger.diagnostic("Operator/SaveFailed", "operatorId=$id, nameLength=${name.trim().length}, error=${e.javaClass.simpleName}:${e.message?.take(120)}")
                DebugLogger.diagnostic("Special/OperatorCreateFailed", "operatorId=$id, error=${e.javaClass.simpleName}:${e.message?.take(160)}")
                finish(onComplete, "保存失败：${e.message?.take(80) ?: "请稍后重试"}")
            }
        }
    }

    private suspend fun finish(onComplete: (String?) -> Unit, error: String?) {
        withContext(Dispatchers.Main.immediate) { onComplete(error) }
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
