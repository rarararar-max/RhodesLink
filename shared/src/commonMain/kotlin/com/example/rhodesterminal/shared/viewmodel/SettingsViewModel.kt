package com.example.rhodesterminal.shared.viewmodel

import com.example.rhodesterminal.shared.model.*
import com.example.rhodesterminal.shared.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserProfile(
    val nickname: String = "博士",
    val gender: String = "",
    val bio: String = "",
    val avatarUri: String = ""
)

class SettingsViewModel(private val locator: ServiceLocator) : CommonViewModel() {

    private val settings = locator.settingsRepository

    private val _userProfile = MutableStateFlow(loadUserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _hypnosisCommand = MutableStateFlow(settings.getString("hypnosis_cmd", ""))
    val hypnosisCommand: StateFlow<String> = _hypnosisCommand.asStateFlow()

    private val _hypnosisRounds = MutableStateFlow(settings.getInt("hypnosis_rounds", 0))
    val hypnosisRounds: StateFlow<Int> = _hypnosisRounds.asStateFlow()

    private val _mindReadRounds = MutableStateFlow(settings.getInt("mind_read_rounds", 0))
    val mindReadRounds: StateFlow<Int> = _mindReadRounds.asStateFlow()

    private val _mindReadContent = MutableStateFlow(settings.getString("mind_read_content", ""))
    val mindReadContent: StateFlow<String> = _mindReadContent.asStateFlow()

    fun isDualModel(): Boolean = settings.dualModel

    fun setDualModel(enabled: Boolean) {
        settings.dualModel = enabled
    }

    fun getApiKey(): String = settings.apiKey

    fun setApiKey(key: String) {
        settings.apiKey = key
    }

    fun getProvider(): String = settings.getString("provider", "deepseek")

    fun getModelName(): String = settings.getString("model_name", "deepseek-chat")

    fun getCustomUrl(): String = settings.getString("custom_url", "")

    fun getTemperature(): Double {
        val raw = settings.getString("temperature", "0.95")
        return raw.toDoubleOrNull() ?: 0.95
    }

    fun getUserProfile(): UserProfile = _userProfile.value

    fun saveUserProfile(nickname: String, gender: String, bio: String, avatarUri: String) {
        settings.userName = nickname
        settings.userGender = gender
        settings.userSignature = bio
        settings.userAvatarUri = avatarUri
        _userProfile.value = UserProfile(nickname, gender, bio, avatarUri)
    }

    fun getPromptTemplate(type: String, mode: String): String {
        val key = "prompt_${type}_${mode}"
        return settings.getString(key, "")
    }

    fun savePromptTemplate(type: String, mode: String, template: String) {
        val key = "prompt_${type}_${mode}"
        settings.putString(key, template)
    }

    fun resetPromptTemplate(type: String, mode: String) {
        val key = "prompt_${type}_${mode}"
        settings.remove(key)
    }

    fun setHypnosis(command: String) {
        _hypnosisCommand.value = command
        _hypnosisRounds.value = 3
        settings.putString("hypnosis_cmd", command)
        settings.putInt("hypnosis_rounds", 3)
    }

    fun decrementHypnosis() {
        val current = _hypnosisRounds.value
        if (current > 0) {
            _hypnosisRounds.value = current - 1
            settings.putInt("hypnosis_rounds", current - 1)
            if (current - 1 == 0) {
                _hypnosisCommand.value = ""
                settings.putString("hypnosis_cmd", "")
            }
        }
    }

    fun loadHypnosis() {
        _hypnosisCommand.value = settings.getString("hypnosis_cmd", "")
        _hypnosisRounds.value = settings.getInt("hypnosis_rounds", 0)
    }

    fun setMindRead(innerThought: String) {
        _mindReadContent.value = innerThought
        _mindReadRounds.value = 3
        settings.putString("mind_read_content", innerThought)
        settings.putInt("mind_read_rounds", 3)
    }

    fun decrementMindRead() {
        val current = _mindReadRounds.value
        if (current > 0) {
            _mindReadRounds.value = current - 1
            settings.putInt("mind_read_rounds", current - 1)
            if (current - 1 == 0) {
                _mindReadContent.value = ""
                settings.putString("mind_read_content", "")
            }
        }
    }

    private fun loadUserProfile(): UserProfile {
        return UserProfile(
            nickname = settings.userName,
            gender = settings.userGender,
            bio = settings.userSignature,
            avatarUri = settings.userAvatarUri
        )
    }
}
