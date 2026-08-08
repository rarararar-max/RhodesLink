package com.rhodes.privatechat.viewmodel.shared

import com.rhodes.privatechat.shared.model.Moment
import com.rhodes.privatechat.shared.model.Operator
import com.rhodes.privatechat.shared.model.ChatSession
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import com.rhodes.privatechat.util.DebugLogger

data class UserProfile(
    val nickname: String = "博士", val gender: String = "", val bio: String = "", val avatarUri: String = ""
)

class AppStateHolder(
    scope: CoroutineScope,
    private val repository: ChatRepository,
    private val settings: SettingsRepository
) {
    private val _operators = MutableStateFlow<List<Operator>>(emptyList())
    val operators: StateFlow<List<Operator>> = _operators.asStateFlow()

    private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()

    private val _allSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val allSessions: StateFlow<List<ChatSession>> = _allSessions.asStateFlow()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _moments = MutableStateFlow<List<Moment>>(emptyList())
    val moments: StateFlow<List<Moment>> = _moments.asStateFlow()

    init {
        // Do not wait for SQLDelight's invalidation flow to emit its first value. Some upgraded
        // installations can read the database directly while their UI state remains empty.
        scope.launch {
            hydrateChatState("initial_sync", 1)
            if (_operators.value.isEmpty() || _allSessions.value.isEmpty()) {
                delay(750)
                hydrateChatState("initial_retry", 2)
            }
        }
        scope.launch {
            while (true) {
                try {
                    repository.allOperators.collect { operators ->
                        DebugLogger.diagnostic("AppState/OperatorsUpdated", "source=flow, operators=${operators.size}")
                        if (operators.isEmpty()) {
                            DebugLogger.diagnostic("Special/OperatorsEmpty", "source=flow")
                            // A stale SQLDelight invalidation flow must not erase contacts that
                            // are still readable from the same database connection.
                            val direct = repository.getAllOperatorsSync()
                            if (direct.isNotEmpty()) {
                                _operators.value = direct
                                DebugLogger.diagnostic("AppState/OperatorsEmptyIgnored", "flow=0, database=${direct.size}")
                            } else {
                                _operators.value = operators
                            }
                        } else {
                            _operators.value = operators
                        }
                    }
                } catch (e: Exception) {
                    DebugLogger.diagnostic("Special/OperatorsFlowFailed", "error=${e.javaClass.simpleName}:${e.message?.take(160)}")
                    runCatching { _operators.value = repository.getAllOperatorsSync() }
                    delay(500)
                }
            }
        }
        scope.launch {
            while (true) {
                try {
                    repository.allSessions.collect { all ->
                        val hidden = settings.hiddenIds
                        DebugLogger.diagnostic("AppState/SessionsUpdated", "source=flow, allSessions=${all.size}, visibleSessions=${_sessions.value.size}, hiddenSessions=${hidden.size}")
                        if (all.isEmpty()) {
                            DebugLogger.diagnostic("Special/SessionsEmpty", "source=flow")
                            val direct = repository.getAllSessionsSync()
                            if (direct.isNotEmpty()) {
                                refreshAllSessions(direct, hidden)
                                DebugLogger.diagnostic("AppState/SessionsEmptyIgnored", "flow=0, database=${direct.size}")
                            } else {
                                refreshAllSessions(all, hidden)
                            }
                        } else {
                            refreshAllSessions(all, hidden)
                        }
                    }
                } catch (e: Exception) {
                    DebugLogger.diagnostic("Special/SessionsFlowFailed", "error=${e.javaClass.simpleName}:${e.message?.take(160)}")
                    runCatching { refreshAllSessions(repository.getAllSessionsSync(), settings.hiddenIds) }
                    delay(500)
                }
            }
        }
        _userProfile.value = loadUserProfile()
    }

    private suspend fun hydrateChatState(source: String, attempt: Int) {
        try {
            DebugLogger.diagnostic("AppState/HydrationStart", "source=$source, attempt=$attempt")
            val operators = repository.getAllOperatorsSync()
            val allSessions = repository.getAllSessionsSync()
            val hidden = settings.hiddenIds
            refreshOperators(operators)
            refreshAllSessions(allSessions, hidden)
            DebugLogger.diagnostic(
                "AppState/HydrationDone",
                "source=$source, attempt=$attempt, operators=${operators.size}, allSessions=${allSessions.size}, visibleSessions=${_sessions.value.size}, hiddenSessions=${hidden.size}"
            )
        } catch (e: Exception) {
            DebugLogger.diagnostic("AppState/HydrationFailed", "source=$source, attempt=$attempt, error=${e.javaClass.simpleName}:${e.message?.take(160)}")
        }
    }

    fun refreshUserProfile() {
        _userProfile.value = loadUserProfile()
    }

    fun getOperatorsSnapshot(): List<Operator> = _operators.value

    fun refreshOperators(operators: List<Operator>) {
        _operators.value = operators
    }

    suspend fun reloadFromDatabase(source: String): Boolean {
        return try {
            val operators = repository.getAllOperatorsSync()
            val sessions = repository.getAllSessionsSync()
            refreshOperators(operators)
            refreshAllSessions(sessions, settings.hiddenIds)
            DebugLogger.diagnostic("AppState/ReloadDone", "source=$source, operators=${operators.size}, allSessions=${sessions.size}, visibleSessions=${_sessions.value.size}")
            true
        } catch (e: Exception) {
            DebugLogger.diagnostic("AppState/ReloadFailed", "source=$source, error=${e.javaClass.simpleName}:${e.message?.take(160)}")
            false
        }
    }

    fun findOperatorByName(name: String): Operator? =
        _operators.value.find { it.name == name }

    fun findOperatorById(id: String): Operator? =
        _operators.value.find { it.id == id }

    fun clearSessions() {
        _sessions.value = emptyList()
        _allSessions.value = emptyList()
    }

    fun clearChatListOnly() {
        _sessions.value = emptyList()
    }

    fun refreshAllSessions(all: List<ChatSession>, hiddenIds: Set<String>) {
        _allSessions.value = all
        _sessions.value = all.filter { it.id !in hiddenIds }
    }

    fun refreshMoments(moments: List<Moment>) {
        _moments.value = moments
    }

    private fun loadUserProfile(): UserProfile {
        return UserProfile(
            nickname = settings.userName.ifBlank { "博士" },
            gender = settings.userGender,
            bio = settings.userSignature,
            avatarUri = settings.userAvatarUri
        )
    }
}
