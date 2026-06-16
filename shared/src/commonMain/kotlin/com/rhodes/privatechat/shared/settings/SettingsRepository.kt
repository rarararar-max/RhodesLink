package com.rhodes.privatechat.shared.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.map

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class SettingsRepository(private val settings: ObservableSettings) {

    private val flowSettings = settings.toFlowSettings()

    // === 模型设置 ===
    var provider: String
        get() = settings.getString("provider", "deepseek")
        set(value) = settings.putString("provider", value)

    var modelName: String
        get() = settings.getString("model_name", "deepseek-chat")
        set(value) = settings.putString("model_name", value)

    var customUrl: String
        get() = settings.getString("custom_url", "")
        set(value) = settings.putString("custom_url", value)

    // === API 设置 ===
    var apiKey: String
        get() = settings.getString("api_key", "")
        set(value) = settings.putString("api_key", value)

    val apiKeyFlow: Flow<String> = flowSettings.getStringFlow("api_key", "")

    var aiAvatarUri: String
        get() = settings.getString("ai_avatar_uri", "")
        set(value) = settings.putString("ai_avatar_uri", value)

    var bgUri: String
        get() = settings.getString("bg_uri", "")
        set(value) = settings.putString("bg_uri", value)

    // === 聊天设置 ===
    var dualModel: Boolean
        get() = settings.getBoolean("dual_model", false)
        set(value) = settings.putBoolean("dual_model", value)

    var messageCounter: Int
        get() = settings.getInt("msg_counter", 0)
        set(value) = settings.putInt("msg_counter", value)

    var impressionMsgCounter: Int
        get() = settings.getInt("impression_msg_counter", 0)
        set(value) = settings.putInt("impression_msg_counter", value)

    var summaryThreshold: Int
        get() = settings.getInt("summary_threshold", 20).coerceAtLeast(3)
        set(value) = settings.putInt("summary_threshold", value)

    var summaryRetain: Int
        get() = settings.getInt("summary_retain", 5)
        set(value) = settings.putInt("summary_retain", value)

    var impressionThreshold: Int
        get() = settings.getInt("impression_threshold", 50)
        set(value) = settings.putInt("impression_threshold", value)

    var historyMessages: Int
        get() = settings.getInt("history_messages", 20)
        set(value) = settings.putInt("history_messages", value)

    var maxContextTokens: Int
        get() = settings.getInt("max_context_tokens", 32000)
        set(value) = settings.putInt("max_context_tokens", value)

    // === 聊天配置 ===
    var aiTemperature: Double
        get() = settings.getInt("ai_temperature", 80).toDouble() / 100.0
        set(value) = settings.putInt("ai_temperature", (value * 100).toInt())

    var cleanDays: Int
        get() = settings.getInt("clean_days", 30)
        set(value) = settings.putInt("clean_days", value)

    fun getLastMode(operatorId: String): String =
        getString("last_mode_$operatorId", "online")

    fun putLastMode(operatorId: String, value: String) =
        putString("last_mode_$operatorId", value)

    // === 旁白设置 ===
    var narSegMin: Int
        get() = settings.getInt("nar_seg_min", 1)
        set(value) = settings.putInt("nar_seg_min", value)

    var narSegMax: Int
        get() = settings.getInt("nar_seg_max", 3)
        set(value) = settings.putInt("nar_seg_max", value)

    var narMin: Int
        get() = settings.getInt("nar_min", 50)
        set(value) = settings.putInt("nar_min", value)

    var narMax: Int
        get() = settings.getInt("nar_max", 300)
        set(value) = settings.putInt("nar_max", value)

    var diaSegMin: Int
        get() = settings.getInt("dia_seg_min", 1)
        set(value) = settings.putInt("dia_seg_min", value)

    var diaSegMax: Int
        get() = settings.getInt("dia_seg_max", 3)
        set(value) = settings.putInt("dia_seg_max", value)

    var diaMin: Int
        get() = settings.getInt("dia_min", 10)
        set(value) = settings.putInt("dia_min", value)

    var diaMax: Int
        get() = settings.getInt("dia_max", 300)
        set(value) = settings.putInt("dia_max", value)

    // === 派遣设置 ===
    var dispatchFastMode: Boolean
        get() = settings.getBoolean("dispatch_fast_mode", false)
        set(value) = settings.putBoolean("dispatch_fast_mode", value)

    var dispatchMinChars: Int
        get() = settings.getInt("dispatch_min_chars", 50)
        set(value) = settings.putInt("dispatch_min_chars", value)

    var dispatchMaxChars: Int
        get() = settings.getInt("dispatch_max_chars", 300)
        set(value) = settings.putInt("dispatch_max_chars", value)

    // === 动态/日记设置 ===
    var momentMinChars: Int
        get() = settings.getInt("moment_min_chars", 50)
        set(value) = settings.putInt("moment_min_chars", value)

    var momentMaxChars: Int
        get() = settings.getInt("moment_max_chars", 200)
        set(value) = settings.putInt("moment_max_chars", value)

    var diaryMinChars: Int
        get() = settings.getInt("diary_min_chars", 50)
        set(value) = settings.putInt("diary_min_chars", value)

    var diaryMaxChars: Int
        get() = settings.getInt("diary_max_chars", 300)
        set(value) = settings.putInt("diary_max_chars", value)

    // === 群聊设置 ===
    var groupChatMinInterval: Int
        get() = settings.getInt("group_chat_min_interval", 60)
        set(value) = settings.putInt("group_chat_min_interval", value)

    var groupChatMaxInterval: Int
        get() = settings.getInt("group_chat_max_interval", 180)
        set(value) = settings.putInt("group_chat_max_interval", value)

    // === 好感度设置 ===
    var dailyIntimacyCap: Int
        get() = settings.getInt("daily_intimacy_cap", 5)
        set(value) = settings.putInt("daily_intimacy_cap", value)

    var todayDate: String
        get() = settings.getString("today_date", "")
        set(value) = settings.putString("today_date", value)

    var todayIntimacyCount: Int
        get() = settings.getInt("today_intimacy_count", 0)
        set(value) = settings.putInt("today_intimacy_count", value)

    // === 龙门币 ===
    var lmb: Int
        get() = settings.getInt("lmb", 1000)
        set(value) = settings.putInt("lmb", value)

    val lmbFlow: Flow<Int> = flowSettings.getIntFlow("lmb", 1000)

    var lmbRefreshDate: String
        get() = settings.getString("lmb_refresh_date", "")
        set(value) = settings.putString("lmb_refresh_date", value)

    var dailyLmbCount: Int
        get() = settings.getInt("daily_lmb_count", 0)
        set(value) = settings.putInt("daily_lmb_count", value)

    var rewardDate: String
        get() = settings.getString("reward_date", "")
        set(value) = settings.putString("reward_date", value)

    // === 动态设置 ===
    var dailyMomentTarget: Int
        get() = settings.getInt("daily_moment_target", 2)
        set(value) = settings.putInt("daily_moment_target", value)

    // === 用户资料 ===
    var userName: String
        get() = settings.getString("user_name", "博士")
        set(value) = settings.putString("user_name", value)

    var userGender: String
        get() = settings.getString("user_gender", "男")
        set(value) = settings.putString("user_gender", value)

    var userSignature: String
        get() = settings.getString("user_signature", "")
        set(value) = settings.putString("user_signature", value)

    var userAvatarUri: String
        get() = settings.getString("user_avatar_uri", "")
        set(value) = settings.putString("user_avatar_uri", value)

    // === 深色模式 ===
    var darkMode: Boolean
        get() = settings.getBoolean("dark_mode", true)
        set(value) = settings.putBoolean("dark_mode", value)

    // === 清理设置 ===
    var cleanDaysMessages: Int
        get() = settings.getInt("clean_days_messages", 30)
        set(value) = settings.putInt("clean_days_messages", value)

    var cleanDaysAnchors: Int
        get() = settings.getInt("clean_days_anchors", 7)
        set(value) = settings.putInt("clean_days_anchors", value)

    var cleanDaysDiaries: Int
        get() = settings.getInt("clean_days_diaries", 30)
        set(value) = settings.putInt("clean_days_diaries", value)

    var cleanDaysMoments: Int
        get() = settings.getInt("clean_days_moments", 7)
        set(value) = settings.putInt("clean_days_moments", value)

    var cleanDaysDispatches: Int
        get() = settings.getInt("clean_days_dispatches", 30)
        set(value) = settings.putInt("clean_days_dispatches", value)

    // === 催眠设置 ===
    var hypnosisCmd: String
        get() = settings.getString("hypnosis_cmd", "")
        set(value) = settings.putString("hypnosis_cmd", value)

    var hypnosisRound: Int
        get() = settings.getInt("hypnosis_round", 0)
        set(value) = settings.putInt("hypnosis_round", value)

    // === 每日总结 ===
    var dailySummaryDate: String
        get() = settings.getString("daily_summary_date", "")
        set(value) = settings.putString("daily_summary_date", value)

    // === 隐藏会话 ===
    var hiddenIds: Set<String>
        get() = getStringSet("hidden_ids")
        set(value) = putStringSet("hidden_ids", value)

    // === 动态 ID 计数 ===
    var lastSeenMomentId: Long
        get() = getLong("last_seen_moment_id", 0)
        set(value) = putLong("last_seen_moment_id", value)

    var lastSeenCommentId: Long
        get() = getLong("last_seen_comment_id", 0)
        set(value) = putLong("last_seen_comment_id", value)

    // === 麻将历史 ===
    var mahjongHistoryJson: String
        get() = getString("mahjong_history_json", "")
        set(value) = putString("mahjong_history_json", value)

    var groupMsgMin: Int
        get() = getInt("group_msg_min", 10)
        set(value) = putInt("group_msg_min", value)

    var groupMsgMax: Int
        get() = getInt("group_msg_max", 100)
        set(value) = putInt("group_msg_max", value)

    var groupSpeechMin: Int
        get() = getInt("group_speech_min", 1)
        set(value) = putInt("group_speech_min", value)

    var groupSpeechMax: Int
        get() = getInt("group_speech_max", 2)
        set(value) = putInt("group_speech_max", value)

    var groupNarSegMin: Int
        get() = getInt("group_nar_seg_min", 1)
        set(value) = putInt("group_nar_seg_min", value)

    var groupNarSegMax: Int
        get() = getInt("group_nar_seg_max", 3)
        set(value) = putInt("group_nar_seg_max", value)

    var groupNarMin: Int
        get() = getInt("group_nar_min", 20)
        set(value) = putInt("group_nar_min", value)

    var groupNarMax: Int
        get() = getInt("group_nar_max", 100)
        set(value) = putInt("group_nar_max", value)

    var commentMinChars: Int
        get() = getInt("comment_min_chars", 10)
        set(value) = putInt("comment_min_chars", value)

    var commentMaxChars: Int
        get() = getInt("comment_max_chars", 40)
        set(value) = putInt("comment_max_chars", value)

    // === 动态键方法（per-operator, per-group）===

    fun getOperatorMsgPermission(operatorId: String): Boolean =
        getBoolean("msg_$operatorId", true)

    fun putOperatorMsgPermission(operatorId: String, value: Boolean) =
        putBoolean("msg_$operatorId", value)

    fun getOperatorDynPermission(operatorId: String): Boolean =
        getBoolean("dyn_$operatorId", true)

    fun putOperatorDynPermission(operatorId: String, value: Boolean) =
        putBoolean("dyn_$operatorId", value)

    fun getGroupAuto(groupId: String): Boolean =
        getBoolean("group_auto_$groupId", false)

    fun putGroupAuto(groupId: String, value: Boolean) =
        putBoolean("group_auto_$groupId", value)

    fun getGroupMode(groupId: String): String =
        getString("group_mode_$groupId", "online")

    fun putGroupMode(groupId: String, value: String) =
        putString("group_mode_$groupId", value)

    fun getPromptTemplate(type: String, mode: String = ""): String {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        return getString(key, "")
    }

    fun putPromptTemplate(type: String, mode: String, value: String) {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        putString(key, value)
    }

    fun removePromptTemplate(type: String, mode: String = "") {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        remove(key)
    }

    fun getMomentCount(operatorId: String, date: String): Int =
        getInt("moment_count_${operatorId}_$date", 0)

    fun putMomentCount(operatorId: String, date: String, value: Int) =
        putInt("moment_count_${operatorId}_$date", value)

    fun removeMomentCount(operatorId: String, date: String) =
        remove("moment_count_${operatorId}_$date")

    // === Token 追踪（旧字段：总计，兼容旧数据）===

    fun getTokenCount(category: String): Int =
        getInt("token_$category", 0)

    fun putTokenCount(category: String, value: Int) =
        putInt("token_$category", value)

    fun getDailyTokenCount(category: String, date: String): Int =
        getInt("daily_${category}_$date", 0)

    fun putDailyTokenCount(category: String, date: String, value: Int) =
        putInt("daily_${category}_$date", value)

    // === Token 追踪（新字段：输入/输出分离）===

    fun getInputTokenCount(category: String): Int =
        getInt("token_in_$category", 0)

    fun getOutputTokenCount(category: String): Int =
        getInt("token_out_$category", 0)

    fun addInputTokenCount(category: String, delta: Int) =
        putInt("token_in_$category", getInputTokenCount(category) + delta)

    fun addOutputTokenCount(category: String, delta: Int) =
        putInt("token_out_$category", getOutputTokenCount(category) + delta)

    fun getDailyInputTokenCount(category: String, date: String): Int =
        getInt("daily_in_${category}_$date", 0)

    fun getDailyOutputTokenCount(category: String, date: String): Int =
        getInt("daily_out_${category}_$date", 0)

    fun addDailyInputTokenCount(category: String, date: String, delta: Int) =
        putInt("daily_in_${category}_$date", getDailyInputTokenCount(category, date) + delta)

    fun addDailyOutputTokenCount(category: String, date: String, delta: Int) =
        putInt("daily_out_${category}_$date", getDailyOutputTokenCount(category, date) + delta)

    // === 通用方法 ===
    fun getString(key: String, default: String = ""): String =
        settings.getString(key, default)

    fun putString(key: String, value: String) =
        settings.putString(key, value)

    fun getInt(key: String, default: Int = 0): Int =
        settings.getInt(key, default)

    fun putInt(key: String, value: Int) =
        settings.putInt(key, value)

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        settings.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) =
        settings.putBoolean(key, value)

    fun getLong(key: String, default: Long = 0L): Long =
        settings.getLong(key, default)

    fun putLong(key: String, value: Long) =
        settings.putLong(key, value)

    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> {
        val json = settings.getString(key, "")
        return if (json.isBlank()) default
        else try { Json.decodeFromString<Set<String>>(json) } catch (_: Exception) { default }
    }

    fun putStringSet(key: String, value: Set<String>) =
        settings.putString(key, Json.encodeToString(value))

    fun remove(key: String) =
        settings.remove(key)

    fun clear() =
        settings.clear()
}
