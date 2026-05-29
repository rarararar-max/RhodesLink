package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ChatViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    private val _selectedOperator = MutableStateFlow<Operator?>(null)
    val selectedOperator: StateFlow<Operator?> = _selectedOperator.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _currentMode = MutableStateFlow(settings.lastMode)
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun selectOperator(operator: Operator) {
        _selectedOperator.value = operator
        _currentSession.value = ChatSession(
            id = "private_${operator.id}",
            operatorId = operator.id,
            operatorName = operator.name,
            mode = _currentMode.value
        )
        _messages.value = emptyList()
    }

    fun clearSelection() {
        _selectedOperator.value = null
        _currentSession.value = null
        _messages.value = emptyList()
    }

    fun updateInputText(text: String) {
        _inputText.value = text
    }

    fun setMode(mode: String) {
        _currentMode.value = mode
        settings.lastMode = mode
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        _isLoading.value = true
        _inputText.value = ""
        // TODO: 实现消息发送逻辑
        _isLoading.value = false
    }

    fun deleteSession(sessionId: String) {
        if (_currentSession.value?.id == sessionId) {
            clearSelection()
        }
    }

    fun markAllRead() {
        // TODO: 实现标记已读
    }
}
