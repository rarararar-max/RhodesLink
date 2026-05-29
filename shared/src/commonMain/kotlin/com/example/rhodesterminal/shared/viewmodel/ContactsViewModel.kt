package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

class ContactsViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    private val _operators = MutableStateFlow<List<Operator>>(emptyList())
    val operators: StateFlow<List<Operator>> = _operators.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    fun saveOperator(
        id: String, name: String, title: String, description: String,
        avatarUri: String, privatePrompt: String, groupPrompt: String,
        userRelation: String, attack: Float, defense: Float, meldPref: String
    ) {
        // TODO: 实现保存干员
    }

    fun deleteOperator(operatorId: String) {
        // TODO: 实现删除干员
    }

    fun findOperatorByName(name: String): Operator? {
        return _operators.value.find { it.name == name }
    }

    fun loadRelationships(operatorId: String): Flow<List<Relationship>> {
        return emptyFlow()
    }

    fun saveGroup(groupId: String, name: String, members: String, rules: String, avatarUri: String) {
        // TODO: 实现保存群组
    }

    fun deleteGroup(groupSessionId: String) {
        // TODO: 实现删除群组
    }

    fun updateOperatorIntimacy(operatorId: String, delta: Int) {
        // TODO: 实现更新好感度
    }

    fun updateOperatorStatus(operatorId: String, location: String, activity: String, emotion: String) {
        // TODO: 实现更新状态
    }

    fun getMessageRanking(): List<Triple<String, String, Int>> {
        return emptyList()
    }

    fun getAllImpressions(): List<Pair<String, String>> {
        return emptyList()
    }

    fun deleteAllImpressions() {
        // TODO: 实现删除印象
    }
}
