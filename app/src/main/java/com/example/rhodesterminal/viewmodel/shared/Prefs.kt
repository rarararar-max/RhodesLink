package com.example.rhodesterminal.viewmodel.shared

import android.app.Application
import android.content.SharedPreferences

class Prefs(private val application: Application) {

    val chat: SharedPreferences get() = application.getSharedPreferences("chat_prefs", 0)
    val model: SharedPreferences get() = application.getSharedPreferences("model_prefs", 0)
    val user: SharedPreferences get() = application.getSharedPreferences("user_prefs", 0)
    val hidden: SharedPreferences get() = application.getSharedPreferences("session_hidden", 0)
    val tokens: SharedPreferences get() = application.getSharedPreferences("token_stats", 0)
    val opPerms: SharedPreferences get() = application.getSharedPreferences("op_perms", 0)
    val dispatch: SharedPreferences get() = application.getSharedPreferences("dispatch", 0)
    val opLmb: SharedPreferences get() = application.getSharedPreferences("op_lmb", 0)
    val moment: SharedPreferences get() = application.getSharedPreferences("moment_prefs", 0)
    val mahjong: SharedPreferences get() = application.getSharedPreferences("mahjong_history", 0)
    val promptTemplates: SharedPreferences get() = application.getSharedPreferences("prompt_templates", 0)

    // chat_prefs convenience
    fun intPref(key: String, default: Int): Int = chat.getInt(key, default)

    fun isDualModel(): Boolean = chat.getBoolean("dual_model", false)

    fun setDualModel(enabled: Boolean) {
        chat.edit().putBoolean("dual_model", enabled).apply()
    }

    var messageCounter: Int
        get() = chat.getInt("msg_counter", 0)
        set(v) { chat.edit().putInt("msg_counter", v).apply() }

    var impressionMsgCounter: Int
        get() = chat.getInt("impression_msg_counter", 0)
        set(v) { chat.edit().putInt("impression_msg_counter", v).apply() }

    val shortTermThreshold: Int get() = chat.getInt("summary_threshold", 20).coerceAtLeast(3)

    // model_prefs convenience
    fun apiKey(): String = chat.getString("api_key", "") ?: ""
    fun provider(): String = model.getString("provider", "deepseek") ?: "deepseek"
    fun modelName(): String = model.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
    fun customUrl(): String = model.getString("custom_url", "") ?: ""
}
