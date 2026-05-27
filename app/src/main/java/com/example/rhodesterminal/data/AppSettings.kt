package com.example.rhodesterminal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_settings")

class AppSettings(private val context: Context) {
    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val AI_AVATAR_URI = stringPreferencesKey("ai_avatar_uri")
        private val BG_URI = stringPreferencesKey("bg_uri")
    }

    val apiKey: Flow<String> = context.dataStore.data.map { it[API_KEY] ?: "" }
    val aiAvatarUri: Flow<String> = context.dataStore.data.map { it[AI_AVATAR_URI] ?: "" }
    val bgUri: Flow<String> = context.dataStore.data.map { it[BG_URI] ?: "" }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[API_KEY] = key }
    }

    suspend fun saveAiAvatarUri(uri: String) {
        context.dataStore.edit { it[AI_AVATAR_URI] = uri }
    }

    suspend fun saveBgUri(uri: String) {
        context.dataStore.edit { it[BG_URI] = uri }
    }
}
