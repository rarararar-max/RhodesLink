package com.rhodes.privatechat.shared.settings

import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.toFlowSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.map
import kotlin.math.max

@OptIn(com.russhwolf.settings.ExperimentalSettingsApi::class)
class SettingsRepository(private val settings: ObservableSettings) {

    private val flowSettings = settings.toFlowSettings()
    private val draftLock = Any()
    private var draftActive: Boolean = false
    private val draftValues = mutableMapOf<String, Any?>()

    fun beginDraft() = synchronized(draftLock) {
        draftActive = true
        draftValues.clear()
    }

    fun hasDraftChanges(): Boolean = synchronized(draftLock) { draftActive && draftValues.isNotEmpty() }

    fun saveDraft() = synchronized(draftLock) {
        if (!draftActive) return
        draftValues.forEach { (key, value) ->
            when (value) {
                is String -> settings.putString(key, value)
                is Int -> settings.putInt(key, value)
                is Boolean -> settings.putBoolean(key, value)
                is Long -> settings.putLong(key, value)
                null -> settings.remove(key)
            }
        }
        draftValues.clear()
        draftActive = false
    }

    fun discardDraft() = synchronized(draftLock) {
        draftValues.clear()
        draftActive = false
    }

    private fun draftString(key: String, default: String): String = synchronized(draftLock) {
        if (draftActive && draftValues.containsKey(key)) draftValues[key] as? String ?: default else safeGetString(key, default)
    }

    private fun draftInt(key: String, default: Int): Int = synchronized(draftLock) {
        if (draftActive && draftValues.containsKey(key)) draftValues[key] as? Int ?: default else safeGetInt(key, default)
    }

    private fun draftBoolean(key: String, default: Boolean): Boolean = synchronized(draftLock) {
        if (draftActive && draftValues.containsKey(key)) draftValues[key] as? Boolean ?: default else safeGetBoolean(key, default)
    }

    private fun draftLong(key: String, default: Long): Long = synchronized(draftLock) {
        if (draftActive && draftValues.containsKey(key)) draftValues[key] as? Long ?: default else safeGetLong(key, default)
    }

    private fun safeGetString(key: String, default: String): String = try {
        settings.getString(key, default)
    } catch (_: Exception) {
        default
    }

    private fun safeGetInt(key: String, default: Int): Int = try {
        settings.getInt(key, default)
    } catch (_: Exception) {
        val asString = try { settings.getString(key, default.toString()) } catch (_: Exception) { null }
        asString?.toIntOrNull() ?: default
    }

    private fun safeGetBoolean(key: String, default: Boolean): Boolean = try {
        settings.getBoolean(key, default)
    } catch (_: Exception) {
        val asString = try { settings.getString(key, default.toString()) } catch (_: Exception) { null }
        asString?.equals("true", true) ?: default
    }

    private fun safeGetLong(key: String, default: Long): Long = try {
        settings.getLong(key, default)
    } catch (_: Exception) {
        val asString = try { settings.getString(key, default.toString()) } catch (_: Exception) { null }
        asString?.toLongOrNull() ?: default
    }

    // === 模型设置 ===
    var provider: String
        get() = getString("provider", "deepseek")
        set(value) = putString("provider", value)

    var modelName: String
        get() = getString("model_name", "deepseek-chat")
        set(value) = putString("model_name", value)

    var customUrl: String
        get() = getString("custom_url", "")
        set(value) = putString("custom_url", value)

    // === API 设置 ===
    var apiKey: String
        get() = getString("api_key", "")
        set(value) = putString("api_key", value)

    val apiKeyFlow: Flow<String> = flowSettings.getStringFlow("api_key", "")

    var aiAvatarUri: String
        get() = getString("ai_avatar_uri", "")
        set(value) = putString("ai_avatar_uri", value)

    var bgUri: String
        get() = getString("bg_uri", "")
        set(value) = putString("bg_uri", value)

    // === 聊天设置 ===
    var dualModel: Boolean
        get() = getBoolean("dual_model", false)
        set(value) = putBoolean("dual_model", value)

    var messageCounter: Int
        get() = getInt("msg_counter", 0)
        set(value) = putInt("msg_counter", value)

    var impressionMsgCounter: Int
        get() = getInt("impression_msg_counter", 0)
        set(value) = putInt("impression_msg_counter", value)

    var summaryThreshold: Int
        get() = getInt("summary_threshold", 20).coerceIn(3, 200)
        set(value) = putInt("summary_threshold", value.coerceIn(3, 200))

    var summaryRetain: Int
        get() = getInt("summary_retain", 5).coerceIn(1, 50)
        set(value) = putInt("summary_retain", value.coerceIn(1, 50))

    var impressionThreshold: Int
        get() = getInt("impression_threshold", 50).coerceIn(5, 500)
        set(value) = putInt("impression_threshold", value.coerceIn(5, 500))

    var historyMessages: Int
        get() = getInt("history_messages", 20).coerceIn(0, 200)
        set(value) = putInt("history_messages", value.coerceIn(0, 200))

    var maxContextTokens: Int
        get() = getInt("max_context_tokens", 32000).coerceIn(1000, 200000)
        set(value) = putInt("max_context_tokens", value.coerceIn(1000, 200000))

    // === 聊天配置 ===
    var aiTemperature: Double
        get() = getInt("ai_temperature", 80).coerceIn(0, 200).toDouble() / 100.0
        set(value) = putInt("ai_temperature", (value.coerceIn(0.0, 2.0) * 100).toInt())

    var cleanDays: Int
        get() = getInt("clean_days", 30).coerceIn(0, 3650)
        set(value) = putInt("clean_days", value.coerceIn(0, 3650))

    // === 记忆注入设置 ===
    var memoryMode: String
        get() = getString("memory_mode", "standard")
        set(value) = putString("memory_mode", value)

    var sourceAwareMemoryEnabled: Boolean
        get() = getBoolean("source_aware_memory_enabled", true)
        set(value) = putBoolean("source_aware_memory_enabled", value)

    var distinguishPrivateMemory: Boolean
        get() = getBoolean("distinguish_private_memory", true)
        set(value) = putBoolean("distinguish_private_memory", value)

    var memorySourceStyle: String
        get() = getString("memory_source_style", "natural")
        set(value) = putString("memory_source_style", value)

    var unifiedMemoryEnabled: Boolean
        get() = getBoolean("unified_memory_enabled", true)
        set(value) = putBoolean("unified_memory_enabled", value)

    var summaryCursorEnabled: Boolean
        get() = getBoolean("summary_cursor_enabled", true)
        set(value) = putBoolean("summary_cursor_enabled", value)

    var memoryV2Enabled: Boolean
        get() = getBoolean("memory_v2_enabled", true)
        set(value) = putBoolean("memory_v2_enabled", value)

    /** fast, balanced, or deep; controls candidate work, not what the character knows. */
    var memoryRecallMode: String
        get() = getString("memory_recall_mode", "balanced").takeIf { it in setOf("fast", "balanced", "deep") } ?: "balanced"
        set(value) = putString("memory_recall_mode", value.takeIf { it in setOf("fast", "balanced", "deep") } ?: "balanced")

    /** restrained, natural, or proactive references to a character's own past conversations. */
    var personalMemoryReferenceStyle: String
        get() = getString("personal_memory_reference_style", "natural").takeIf { it in setOf("restrained", "natural", "proactive") } ?: "natural"
        set(value) = putString("personal_memory_reference_style", value.takeIf { it in setOf("restrained", "natural", "proactive") } ?: "natural")

    var memoryRecallCandidateLimit: Int
        get() = getInt("memory_recall_candidate_limit", 300).coerceIn(50, 1000)
        set(value) = putInt("memory_recall_candidate_limit", value.coerceIn(50, 1000))

    var momentMemoryV2Enabled: Boolean
        get() = getBoolean("moment_memory_v2_enabled", true)
        set(value) = putBoolean("moment_memory_v2_enabled", value)

    var globalPublicMemoryEnabled: Boolean
        get() = getBoolean("global_public_memory_enabled", true)
        set(value) = putBoolean("global_public_memory_enabled", value)

    var globalPublicMemoryCount: Int
        get() = getInt("global_public_memory_count", 5).coerceIn(0, 20)
        set(value) = putInt("global_public_memory_count", value.coerceIn(0, 20))

    var memoryV2PromoteL1Threshold: Int
        get() = getInt("memory_v2_promote_l1_threshold", 20).coerceIn(5, 200)
        set(value) = putInt("memory_v2_promote_l1_threshold", value.coerceIn(5, 200))

    var memoryV2PromoteL2Threshold: Int
        get() = getInt("memory_v2_promote_l2_threshold", 10).coerceIn(3, 100)
        set(value) = putInt("memory_v2_promote_l2_threshold", value.coerceIn(3, 100))

    var memoryPinMinImportance: Int
        get() = getInt("memory_pin_min_importance", 70).coerceIn(0, 100)
        set(value) = putInt("memory_pin_min_importance", value.coerceIn(0, 100))

    var autoImpressionUpdateEnabled: Boolean
        get() = getBoolean("auto_impression_update_enabled", true)
        set(value) = putBoolean("auto_impression_update_enabled", value)

    var momentPrivateMemoryUsage: String
        get() = getString("moment_private_memory_usage", "subtle")
        set(value) = putString("moment_private_memory_usage", value)

    var privateAnchorCount: Int
        get() = getInt("private_anchor_count", 5).coerceIn(0, 20)
        set(value) = putInt("private_anchor_count", value.coerceIn(0, 20))

    var privateSharedMemoryCount: Int
        get() = getInt("private_shared_memory_count", 3).coerceIn(0, 20)
        set(value) = putInt("private_shared_memory_count", value.coerceIn(0, 20))

    var privateGroupContextCount: Int
        get() = getInt("private_group_context_count", 2).coerceIn(0, 10)
        set(value) = putInt("private_group_context_count", value.coerceIn(0, 10))

    var groupMemberMemoryCount: Int
        get() = getInt("group_member_memory_count", 2).coerceIn(0, 10)
        set(value) = putInt("group_member_memory_count", value.coerceIn(0, 10))

    var groupUserEventCount: Int
        get() = getInt("group_user_event_count", 3).coerceIn(0, 10)
        set(value) = putInt("group_user_event_count", value.coerceIn(0, 10))

    var momentUserPostObserverCount: Int
        get() = getInt("moment_user_post_observer_count", groupUserEventCount).coerceIn(0, 10)
        set(value) = putInt("moment_user_post_observer_count", value.coerceIn(0, 10))

    var groupRelationshipHintCount: Int
        get() = settings.getInt("group_relationship_hint_count", 10).coerceIn(0, 30)
        set(value) = settings.putInt("group_relationship_hint_count", value.coerceIn(0, 30))

    var momentAnchorCount: Int
        get() = getInt("moment_anchor_count", 3).coerceIn(0, 10)
        set(value) = putInt("moment_anchor_count", value.coerceIn(0, 10))

    var momentRecentPostCount: Int
        get() = getInt("moment_recent_post_count", 3).coerceIn(0, 10)
        set(value) = putInt("moment_recent_post_count", value.coerceIn(0, 10))

    var momentUserRelatedRate: Int
        get() = settings.getInt("moment_user_related_rate", 20).coerceIn(0, 100)
        set(value) = settings.putInt("moment_user_related_rate", value.coerceIn(0, 100))

    var commentContextCount: Int
        get() = getInt("comment_context_count", 5).coerceIn(0, 20)
        set(value) = putInt("comment_context_count", value.coerceIn(0, 20))

    var commentMemoryCount: Int
        get() = settings.getInt("comment_memory_count", 2).coerceIn(0, 10)
        set(value) = settings.putInt("comment_memory_count", value.coerceIn(0, 10))

    var commentBystanderMin: Int
        get() = settings.getInt("comment_bystander_min", 1).coerceIn(0, 10)
        set(value) = settings.putInt("comment_bystander_min", value.coerceIn(0, 10))

    var commentBystanderMax: Int
        get() = settings.getInt("comment_bystander_max", 3).coerceIn(0, 10)
        set(value) = settings.putInt("comment_bystander_max", value.coerceIn(0, 10))

    var diaryAnchorCount: Int
        get() = getInt("diary_anchor_count", 5).coerceIn(0, 20)
        set(value) = putInt("diary_anchor_count", value.coerceIn(0, 20))

    var diaryGroupSummaryCount: Int
        get() = settings.getInt("diary_group_summary_count", 3).coerceIn(0, 10)
        set(value) = settings.putInt("diary_group_summary_count", value.coerceIn(0, 10))

    var diaryRelationEventCount: Int
        get() = settings.getInt("diary_relation_event_count", 3).coerceIn(0, 10)
        set(value) = settings.putInt("diary_relation_event_count", value.coerceIn(0, 10))

    // === 世界运行设置 ===
    var autoAiEnabled: Boolean
        get() = getBoolean("auto_ai_enabled", true)
        set(value) = putBoolean("auto_ai_enabled", value)

    var worldSchedulerEnabled: Boolean
        get() = getBoolean("world_scheduler_enabled", false)
        set(value) = putBoolean("world_scheduler_enabled", value)

    var dailyAutoMomentEnabled: Boolean
        get() = getBoolean("daily_auto_moment_enabled", true)
        set(value) = putBoolean("daily_auto_moment_enabled", value)

    var idleProactiveChatEnabled: Boolean
        get() = getBoolean("idle_proactive_chat_enabled", false)
        set(value) = putBoolean("idle_proactive_chat_enabled", value)

    var autoMomentEnabled: Boolean
        get() = getBoolean("auto_moment_enabled", false)
        set(value) = putBoolean("auto_moment_enabled", value)

    var worldAutoGroupEnabled: Boolean
        get() = getBoolean("world_auto_group_enabled", false)
        set(value) = putBoolean("world_auto_group_enabled", value)

    var worldProactiveChatEnabled: Boolean
        get() = getBoolean("world_proactive_chat_enabled", false)
        set(value) = putBoolean("world_proactive_chat_enabled", value)

    var autoDiaryEnabled: Boolean
        get() = getBoolean("auto_diary_enabled", false)
        set(value) = putBoolean("auto_diary_enabled", value)

    var dailyWorldEventLimit: Int
        get() = settings.getInt("daily_world_event_limit", 30).coerceIn(0, 200)
        set(value) = settings.putInt("daily_world_event_limit", value.coerceIn(0, 200))

    var dailyWorldTriggerLimit: Int
        get() = settings.getInt("daily_world_trigger_limit", 20).coerceIn(0, 200)
        set(value) = settings.putInt("daily_world_trigger_limit", value.coerceIn(0, 200))

    var tickWorldTriggerLimit: Int
        get() = settings.getInt("tick_world_trigger_limit", 2).coerceIn(0, 20)
        set(value) = settings.putInt("tick_world_trigger_limit", value.coerceIn(0, 20))

    var dailyDiaryOperatorLimit: Int
        get() = settings.getInt("daily_diary_operator_limit", 3).coerceIn(0, 20)
        set(value) = settings.putInt("daily_diary_operator_limit", value.coerceIn(0, 20))

    var dailyProactiveLimit: Int
        get() = settings.getInt("daily_proactive_limit", 3).coerceIn(0, 20)
        set(value) = settings.putInt("daily_proactive_limit", value.coerceIn(0, 20))

    var proactiveGlobalCooldownMinutes: Int
        get() = settings.getInt("proactive_global_cooldown_minutes", 60).coerceIn(0, 1440)
        set(value) = settings.putInt("proactive_global_cooldown_minutes", value.coerceIn(0, 1440))

    var proactiveQuietAfterUserMinutes: Int
        get() = settings.getInt("proactive_quiet_after_user_minutes", 10).coerceIn(0, 1440)
        set(value) = settings.putInt("proactive_quiet_after_user_minutes", value.coerceIn(0, 1440))

    var proactiveOperatorCooldownMinutes: Int
        get() = settings.getInt("proactive_operator_cooldown_minutes", 120).coerceIn(0, 1440)
        set(value) = settings.putInt("proactive_operator_cooldown_minutes", value.coerceIn(0, 1440))

    var momentTriggerStrength: Int
        get() = settings.getInt("moment_trigger_strength", 50).coerceIn(0, 100)
        set(value) = settings.putInt("moment_trigger_strength", value.coerceIn(0, 100))

    var groupTriggerStrength: Int
        get() = settings.getInt("group_trigger_strength", 50).coerceIn(0, 100)
        set(value) = settings.putInt("group_trigger_strength", value.coerceIn(0, 100))

    var eventGroupRounds: Int
        get() = settings.getInt("event_group_rounds", 2).coerceIn(1, 10)
        set(value) = settings.putInt("event_group_rounds", value.coerceIn(1, 10))

    var eventGroupCooldownMinutes: Int
        get() = settings.getInt("event_group_cooldown_minutes", 45).coerceIn(1, 720)
        set(value) = settings.putInt("event_group_cooldown_minutes", value.coerceIn(1, 720))

    var eventMaxGroupsPerTrigger: Int
        get() = settings.getInt("event_max_groups_per_trigger", 1).coerceIn(1, 10)
        set(value) = settings.putInt("event_max_groups_per_trigger", value.coerceIn(1, 10))

    var eventContextCount: Int
        get() = settings.getInt("event_context_count", 5).coerceIn(0, 20)
        set(value) = settings.putInt("event_context_count", value.coerceIn(0, 20))

    var contextMode: String
        get() = getString("context_mode", "custom")
        set(value) = putString("context_mode", value)

    var dailyAutoAiLimit: Int
        get() = getInt("daily_auto_ai_limit", 40).coerceIn(0, 500)
        set(value) = putInt("daily_auto_ai_limit", value.coerceIn(0, 500))

    var tickAutoAiLimit: Int
        get() = settings.getInt("tick_auto_ai_limit", 3).coerceIn(0, 50)
        set(value) = settings.putInt("tick_auto_ai_limit", value.coerceIn(0, 50))

    var commentToPrivateTriggerRate: Int
        get() = settings.getInt("comment_to_private_trigger_rate", 30).coerceIn(0, 100)
        set(value) = settings.putInt("comment_to_private_trigger_rate", value.coerceIn(0, 100))

    var momentToGroupTriggerRate: Int
        get() = settings.getInt("moment_to_group_trigger_rate", 40).coerceIn(0, 100)
        set(value) = settings.putInt("moment_to_group_trigger_rate", value.coerceIn(0, 100))

    fun getLastMode(operatorId: String): String =
        getString("last_mode_$operatorId", "online")

    fun putLastMode(operatorId: String, value: String) =
        putString("last_mode_$operatorId", value)

    // === 旁白设置 ===
    var narSegMin: Int
        get() = getInt("nar_seg_min", 1).coerceIn(0, getInt("nar_seg_max", 3).coerceIn(0, 20))
        set(value) = putInt("nar_seg_min", value.coerceIn(0, getInt("nar_seg_max", 3).coerceIn(0, 20)))

    var narSegMax: Int
        get() = getInt("nar_seg_max", 3).coerceIn(getInt("nar_seg_min", 1).coerceIn(0, 20), 20)
        set(value) = putInt("nar_seg_max", value.coerceIn(getInt("nar_seg_min", 1).coerceIn(0, 20), 20))

    var narMin: Int
        get() = settings.getInt("nar_min", 50).coerceIn(0, settings.getInt("nar_max", 300).coerceIn(0, 2000))
        set(value) = settings.putInt("nar_min", value.coerceIn(0, settings.getInt("nar_max", 300).coerceIn(0, 2000)))

    var narMax: Int
        get() = settings.getInt("nar_max", 300).coerceIn(settings.getInt("nar_min", 50).coerceIn(0, 2000), 2000)
        set(value) = settings.putInt("nar_max", value.coerceIn(settings.getInt("nar_min", 50).coerceIn(0, 2000), 2000))

    var diaSegMin: Int
        get() = settings.getInt("dia_seg_min", 1).coerceIn(0, settings.getInt("dia_seg_max", 3).coerceIn(0, 20))
        set(value) = settings.putInt("dia_seg_min", value.coerceIn(0, settings.getInt("dia_seg_max", 3).coerceIn(0, 20)))

    var diaSegMax: Int
        get() = settings.getInt("dia_seg_max", 3).coerceIn(settings.getInt("dia_seg_min", 1).coerceIn(0, 20), 20)
        set(value) = settings.putInt("dia_seg_max", value.coerceIn(settings.getInt("dia_seg_min", 1).coerceIn(0, 20), 20))

    var diaMin: Int
        get() = settings.getInt("dia_min", 10).coerceIn(0, settings.getInt("dia_max", 300).coerceIn(0, 2000))
        set(value) = settings.putInt("dia_min", value.coerceIn(0, settings.getInt("dia_max", 300).coerceIn(0, 2000)))

    var diaMax: Int
        get() = settings.getInt("dia_max", 300).coerceIn(settings.getInt("dia_min", 10).coerceIn(0, 2000), 2000)
        set(value) = settings.putInt("dia_max", value.coerceIn(settings.getInt("dia_min", 10).coerceIn(0, 2000), 2000))

    // === 派遣设置 ===
    var dispatchFastMode: Boolean
        get() = getBoolean("dispatch_fast_mode", false)
        set(value) = putBoolean("dispatch_fast_mode", value)

    var dispatchMinChars: Int
        get() = settings.getInt("dispatch_min_chars", 50).coerceIn(20, settings.getInt("dispatch_max_chars", 300).coerceIn(20, 2000))
        set(value) = settings.putInt("dispatch_min_chars", value.coerceIn(20, settings.getInt("dispatch_max_chars", 300).coerceIn(20, 2000)))

    var dispatchMaxChars: Int
        get() = settings.getInt("dispatch_max_chars", 300).coerceIn(settings.getInt("dispatch_min_chars", 50).coerceIn(20, 2000), 2000)
        set(value) = settings.putInt("dispatch_max_chars", value.coerceIn(settings.getInt("dispatch_min_chars", 50).coerceIn(20, 2000), 2000))

    // === 动态/日记设置 ===
    var momentMinChars: Int
        get() = getInt("moment_min_chars", 50).coerceIn(5, getInt("moment_max_chars", 200).coerceIn(5, 2000))
        set(value) = putInt("moment_min_chars", value.coerceIn(5, getInt("moment_max_chars", 200).coerceIn(5, 2000)))

    var momentMaxChars: Int
        get() = getInt("moment_max_chars", 200).coerceIn(getInt("moment_min_chars", 50).coerceIn(5, 2000), 2000)
        set(value) = putInt("moment_max_chars", value.coerceIn(getInt("moment_min_chars", 50).coerceIn(5, 2000), 2000))

    var diaryMinChars: Int
        get() = settings.getInt("diary_min_chars", 50).coerceIn(20, settings.getInt("diary_max_chars", 300).coerceIn(20, 3000))
        set(value) = settings.putInt("diary_min_chars", value.coerceIn(20, settings.getInt("diary_max_chars", 300).coerceIn(20, 3000)))

    var diaryMaxChars: Int
        get() = settings.getInt("diary_max_chars", 300).coerceIn(settings.getInt("diary_min_chars", 50).coerceIn(20, 3000), 3000)
        set(value) = settings.putInt("diary_max_chars", value.coerceIn(settings.getInt("diary_min_chars", 50).coerceIn(20, 3000), 3000))

    // === 群聊设置 ===
    var groupChatMinInterval: Int
        get() = settings.getInt("group_chat_min_interval", 60).coerceIn(5, settings.getInt("group_chat_max_interval", 180).coerceIn(5, 86400))
        set(value) = settings.putInt("group_chat_min_interval", value.coerceIn(5, settings.getInt("group_chat_max_interval", 180).coerceIn(5, 86400)))

    var groupChatMaxInterval: Int
        get() = settings.getInt("group_chat_max_interval", 180).coerceIn(settings.getInt("group_chat_min_interval", 60).coerceIn(5, 86400), 86400)
        set(value) = settings.putInt("group_chat_max_interval", value.coerceIn(settings.getInt("group_chat_min_interval", 60).coerceIn(5, 86400), 86400))

    var groupAutoMaxRounds: Int
        get() = settings.getInt("group_auto_max_rounds", 20).coerceIn(1, 500)
        set(value) = settings.putInt("group_auto_max_rounds", value.coerceIn(1, 500))

    var autoStatusRefresh: Boolean
        get() = settings.getBoolean("auto_status_refresh", true)
        set(value) = settings.putBoolean("auto_status_refresh", value)

    val defaultStatusLocations: String
        get() = "宿舍\n训练室\n食堂\n医疗部\n甲板\n办公室\n花园\n走廊\n图书室"

    val defaultStatusActivities: String
        get() = "休息\n训练\n吃饭\n工作\n散步\n聊天\n发呆\n阅读\n整理装备"

    val defaultStatusEmotions: String
        get() = "平静\n开心\n疲惫\n专注\n放松\n兴奋\n低落"

    var statusLocationPool: String
        get() = getString("status_location_pool", defaultStatusLocations)
        set(value) = putString("status_location_pool", value)

    var statusActivityPool: String
        get() = getString("status_activity_pool", defaultStatusActivities)
        set(value) = putString("status_activity_pool", value)

    var statusEmotionPool: String
        get() = getString("status_emotion_pool", defaultStatusEmotions)
        set(value) = putString("status_emotion_pool", value)

    fun parseStatusPool(value: String, defaultValue: String): List<String> =
        value.lines().map { it.trim() }.filter { it.isNotBlank() }.ifEmpty {
            defaultValue.lines().map { it.trim() }.filter { it.isNotBlank() }
        }

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

    @Synchronized
    fun addLmb(delta: Int): Int {
        val next = max(0, settings.getInt("lmb", 1000) + delta)
        settings.putInt("lmb", next)
        return next
    }

    @Synchronized
    fun trySpendLmb(amount: Int): Boolean {
        val current = settings.getInt("lmb", 1000)
        if (current < amount) return false
        settings.putInt("lmb", current - amount)
        return true
    }

    @Synchronized
    fun grantDailyLmb(date: String, amount: Int, dailyLimit: Int = 5000): Boolean {
        val safeAmount = amount.coerceAtLeast(0)
        if (safeAmount == 0) return false
        val safeDailyLimit = dailyLimit.coerceAtLeast(0)
        if (settings.getString("reward_date", "") != date) {
            settings.putString("reward_date", date)
            settings.putInt("daily_lmb_count", 0)
        }
        val count = settings.getInt("daily_lmb_count", 0)
        if (count + safeAmount > safeDailyLimit) return false
        settings.putInt("lmb", settings.getInt("lmb", 1000) + safeAmount)
        settings.putInt("daily_lmb_count", count + safeAmount)
        return true
    }

    val lmbFlow: Flow<Int> = flowSettings.getIntFlow("lmb", 1000)

    var lmbRefreshDate: String
        get() = settings.getString("lmb_refresh_date", "")
        set(value) = settings.putString("lmb_refresh_date", value)

    var dailyLmbCount: Int
        get() = settings.getInt("daily_lmb_count", 0).coerceAtLeast(0)
        set(value) = settings.putInt("daily_lmb_count", value.coerceAtLeast(0))

    var rewardDate: String
        get() = settings.getString("reward_date", "")
        set(value) = settings.putString("reward_date", value)

    // === 动态设置 ===
    var dailyMomentTarget: Int
        get() = settings.getInt("daily_moment_target", 2).coerceIn(0, 3)
        set(value) = settings.putInt("daily_moment_target", value.coerceIn(0, 3))

    var dailyProactiveChance: Int
        get() = settings.getInt("daily_proactive_chance", 80).coerceIn(0, 100)
        set(value) = settings.putInt("daily_proactive_chance", value.coerceIn(0, 100))

    var dailyProactiveMax: Int
        get() = settings.getInt("daily_proactive_max", 5).coerceIn(0, 20)
        set(value) = settings.putInt("daily_proactive_max", value.coerceIn(0, 20))

    var quietHoursEnabled: Boolean
        get() = getBoolean("quiet_hours_enabled", false)
        set(value) = putBoolean("quiet_hours_enabled", value)

    var quietHoursStart: Int
        get() = settings.getInt("quiet_hours_start", 1).coerceIn(0, 23)
        set(value) = settings.putInt("quiet_hours_start", value.coerceIn(0, 23))

    var quietHoursEnd: Int
        get() = settings.getInt("quiet_hours_end", 9).coerceIn(0, 23)
        set(value) = settings.putInt("quiet_hours_end", value.coerceIn(0, 23))

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
        get() = getBoolean("dark_mode", true)
        set(value) = settings.putBoolean("dark_mode", value)

    var vectorProviderMode: String
        get() = getString("vector_provider_mode", "local")
        set(value) = putString("vector_provider_mode", value)

    var vectorProvider: String
        get() = getString("vector_provider", "ali")
        set(value) = putString("vector_provider", value)

    var vectorModelName: String
        get() = getString("vector_model_name", "text-embedding-v4")
        set(value) = putString("vector_model_name", value)

    var vectorBaseUrl: String
        get() = getString("vector_base_url", "")
        set(value) = putString("vector_base_url", value)

    var vectorApiKey: String
        get() = getString("vector_api_key", "")
        set(value) = putString("vector_api_key", value)

    var vectorIndexSignature: String
        get() = getString("vector_index_signature", "")
        set(value) = putString("vector_index_signature", value)

    var visionProvider: String
        get() = getString("vision_provider", "ali")
        set(value) = putString("vision_provider", value)

    var visionModelName: String
        get() = getString("vision_model_name", "qwen3-vl-plus")
        set(value) = putString("vision_model_name", value)

    var visionBaseUrl: String
        get() = getString("vision_base_url", "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation")
        set(value) = putString("vision_base_url", value)

    var visionApiKey: String
        get() = getString("vision_api_key", "")
        set(value) = putString("vision_api_key", value)

    var asrModelName: String
        get() = getString("asr_model_name", "qwen3.5-omni-flash-realtime|qwen3-asr-flash-realtime")
        set(value) = putString("asr_model_name", value)

    var asrBaseUrl: String
        get() = getString("asr_base_url", "wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
        set(value) = putString("asr_base_url", value)

    var asrApiKey: String
        get() = getString("asr_api_key", "")
        set(value) = putString("asr_api_key", value)

    var asrProvider: String
        get() = getString("asr_provider", "ali")
        set(value) = putString("asr_provider", value)

    var ttsModelName: String
        get() = getString("tts_model_name", "speech-2.8-hd")
        set(value) = putString("tts_model_name", value)

    var ttsBaseUrl: String
        get() = getString("tts_base_url", "")
        set(value) = putString("tts_base_url", value)

    var ttsApiKey: String
        get() = getString("tts_api_key", "")
        set(value) = putString("tts_api_key", value)

    var ttsProvider: String
        get() = getString("tts_provider", "minimax")
        set(value) = putString("tts_provider", value)

    var ttsDefaultVoiceId: String
        get() = getString("tts_default_voice_id", "")
        set(value) = putString("tts_default_voice_id", value)

    // === 陪睡设置 ===
    var sleepAlarmHour: Int
        get() = getInt("sleep_alarm_hour", 7)
        set(value) = putInt("sleep_alarm_hour", value.coerceIn(0, 23))

    var sleepAlarmMinute: Int
        get() = getInt("sleep_alarm_minute", 30)
        set(value) = putInt("sleep_alarm_minute", value.coerceIn(0, 59))

    var sleepFixedWakeText: String
        get() = getString("sleep_fixed_wake_text", "时间到了。该醒了，我在这里。")
        set(value) = putString("sleep_fixed_wake_text", value)

    var sleepWakeTextMode: String
        get() = getString("sleep_wake_text_mode", "ai")
        set(value) = putString("sleep_wake_text_mode", if (value == "fixed") "fixed" else "ai")

    var sleepInactivityMinutes: Int
        get() = getInt("sleep_inactivity_minutes", 5).coerceIn(1, 60)
        set(value) = putInt("sleep_inactivity_minutes", value.coerceIn(1, 60))

    var sleepDimAfterSeconds: Int
        get() = getInt("sleep_dim_after_seconds", 60).coerceIn(10, 600)
        set(value) = putInt("sleep_dim_after_seconds", value.coerceIn(10, 600))

    var sleepSnoozeMinutes: Int
        get() = getInt("sleep_snooze_minutes", 5).coerceIn(1, 30)
        set(value) = putInt("sleep_snooze_minutes", value.coerceIn(1, 30))

    // === 清理设置 ===
    var cleanDaysMessages: Int
        get() = settings.getInt("clean_days_messages", 30).coerceIn(0, 3650)
        set(value) = settings.putInt("clean_days_messages", value.coerceIn(0, 3650))

    var cleanDaysAnchors: Int
        get() = settings.getInt("clean_days_anchors", 7).coerceIn(0, 3650)
        set(value) = settings.putInt("clean_days_anchors", value.coerceIn(0, 3650))

    var cleanDaysDiaries: Int
        get() = settings.getInt("clean_days_diaries", 30).coerceIn(0, 3650)
        set(value) = settings.putInt("clean_days_diaries", value.coerceIn(0, 3650))

    var cleanDaysMoments: Int
        get() = settings.getInt("clean_days_moments", 7).coerceIn(0, 3650)
        set(value) = settings.putInt("clean_days_moments", value.coerceIn(0, 3650))

    var cleanDaysDispatches: Int
        get() = settings.getInt("clean_days_dispatches", 30).coerceIn(0, 3650)
        set(value) = settings.putInt("clean_days_dispatches", value.coerceIn(0, 3650))

    var cleanDaysWorldEvents: Int
        get() = settings.getInt("clean_days_world_events", 7).coerceIn(0, 3650)
        set(value) = settings.putInt("clean_days_world_events", value.coerceIn(0, 3650))

    // === 催眠设置 ===
    var hypnosisCmd: String
        get() = settings.getString("hypnosis_cmd", "")
        set(value) = settings.putString("hypnosis_cmd", value)

    var hypnosisRound: Int
        get() = settings.getInt("hypnosis_round", 0).coerceIn(0, 100)
        set(value) = settings.putInt("hypnosis_round", value.coerceIn(0, 100))

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

    fun getDiaryReadAt(operatorId: String): Long =
        getLong("diary_read_at_$operatorId", 0L)

    fun putDiaryReadAt(operatorId: String, value: Long) =
        putLong("diary_read_at_$operatorId", value)

    // === 麻将历史 ===
    var mahjongHistoryJson: String
        get() = getString("mahjong_history_json", "")
        set(value) = putString("mahjong_history_json", value)

    var worldLogJson: String
        get() = getString("world_log_json", "[]")
        set(value) = putString("world_log_json", value)

    var groupMsgMin: Int
        get() = getInt("group_msg_min", 10).coerceIn(1, getInt("group_msg_max", 100).coerceIn(1, 2000))
        set(value) = putInt("group_msg_min", value.coerceIn(1, getInt("group_msg_max", 100).coerceIn(1, 2000)))

    var groupMsgMax: Int
        get() = getInt("group_msg_max", 100).coerceIn(getInt("group_msg_min", 10).coerceIn(1, 2000), 2000)
        set(value) = putInt("group_msg_max", value.coerceIn(getInt("group_msg_min", 10).coerceIn(1, 2000), 2000))

    var groupSpeechMin: Int
        get() = getInt("group_speech_min", 1).coerceIn(1, getInt("group_speech_max", 2).coerceIn(1, 20))
        set(value) = putInt("group_speech_min", value.coerceIn(1, getInt("group_speech_max", 2).coerceIn(1, 20)))

    var groupSpeechMax: Int
        get() = getInt("group_speech_max", 2).coerceIn(getInt("group_speech_min", 1).coerceIn(1, 20), 20)
        set(value) = putInt("group_speech_max", value.coerceIn(getInt("group_speech_min", 1).coerceIn(1, 20), 20))

    var groupNarSegMin: Int
        get() = getInt("group_nar_seg_min", 0).coerceIn(0, getInt("group_nar_seg_max", 3).coerceIn(0, 20))
        set(value) = putInt("group_nar_seg_min", value.coerceIn(0, getInt("group_nar_seg_max", 3).coerceIn(0, 20)))

    var groupNarSegMax: Int
        get() = getInt("group_nar_seg_max", 3).coerceIn(getInt("group_nar_seg_min", 0).coerceIn(0, 20), 20)
        set(value) = putInt("group_nar_seg_max", value.coerceIn(getInt("group_nar_seg_min", 0).coerceIn(0, 20), 20))

    var groupNarMin: Int
        get() = getInt("group_nar_min", 20).coerceIn(0, getInt("group_nar_max", 100).coerceIn(0, 2000))
        set(value) = putInt("group_nar_min", value.coerceIn(0, getInt("group_nar_max", 100).coerceIn(0, 2000)))

    var groupNarMax: Int
        get() = getInt("group_nar_max", 100).coerceIn(getInt("group_nar_min", 20).coerceIn(0, 2000), 2000)
        set(value) = putInt("group_nar_max", value.coerceIn(getInt("group_nar_min", 20).coerceIn(0, 2000), 2000))

    var commentMinChars: Int
        get() = getInt("comment_min_chars", 10).coerceIn(1, getInt("comment_max_chars", 40).coerceIn(1, 1000))
        set(value) = putInt("comment_min_chars", value.coerceIn(1, getInt("comment_max_chars", 40).coerceIn(1, 1000)))

    var commentMaxChars: Int
        get() = getInt("comment_max_chars", 40).coerceIn(getInt("comment_min_chars", 10).coerceIn(1, 1000), 1000)
        set(value) = putInt("comment_max_chars", value.coerceIn(getInt("comment_min_chars", 10).coerceIn(1, 1000), 1000))

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

    fun getGroupEventAuto(groupId: String): Boolean =
        getBoolean("group_event_auto_$groupId", false)

    fun putGroupEventAuto(groupId: String, value: Boolean) =
        putBoolean("group_event_auto_$groupId", value)

    fun getGroupMode(groupId: String): String =
        getString("group_mode_$groupId", "online")

    fun putGroupMode(groupId: String, value: String) =
        putString("group_mode_$groupId", value)

    fun getSessionMessageCounter(sessionId: String): Int =
        getInt("msg_counter_$sessionId", 0)

    fun putSessionMessageCounter(sessionId: String, value: Int) =
        putInt("msg_counter_$sessionId", value)

    fun getPromptTemplate(type: String, mode: String = ""): String {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        return getString(key, "")
    }

    fun putPromptTemplate(type: String, mode: String, value: String) {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        putString(key, value)
    }

    fun getPromptTemplateVersion(type: String, mode: String = ""): Int {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}_version" else "prompt_${type}_version"
        return getInt(key, 0)
    }

    fun putPromptTemplateVersion(type: String, mode: String, version: Int) {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}_version" else "prompt_${type}_version"
        putInt(key, version)
    }

    fun removePromptTemplate(type: String, mode: String = "") {
        val key = if (mode.isNotBlank()) "prompt_${type}_${mode}" else "prompt_$type"
        remove(key)
        remove(if (mode.isNotBlank()) "prompt_${type}_${mode}_version" else "prompt_${type}_version")
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
        draftString(key, default)

    fun putString(key: String, value: String) = synchronized(draftLock) {
        if (draftActive) draftValues[key] = value else settings.putString(key, value)
    }

    fun getInt(key: String, default: Int = 0): Int =
        draftInt(key, default)

    fun putInt(key: String, value: Int) = synchronized(draftLock) {
        if (draftActive) draftValues[key] = value else settings.putInt(key, value)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        draftBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) = synchronized(draftLock) {
        if (draftActive) draftValues[key] = value else settings.putBoolean(key, value)
    }

    fun getLong(key: String, default: Long = 0L): Long =
        draftLong(key, default)

    fun putLong(key: String, value: Long) = synchronized(draftLock) {
        if (draftActive) draftValues[key] = value else settings.putLong(key, value)
    }

    fun getSessionRestartAt(sessionId: String): Long = getLong("session_restart_at_$sessionId", 0L)

    fun putSessionRestartAt(sessionId: String, value: Long) = putLong("session_restart_at_$sessionId", value)

    fun getSummaryCursor(sessionId: String): Long = getLong("summary_cursor_session_$sessionId", 0L)

    fun putSummaryCursor(sessionId: String, value: Long) = putLong("summary_cursor_session_$sessionId", value)

    fun getStringSet(key: String, default: Set<String> = emptySet()): Set<String> {
        val json = safeGetString(key, "")
        return if (json.isBlank()) default
        else try { Json.decodeFromString<Set<String>>(json) } catch (_: Exception) { default }
    }

    fun putStringSet(key: String, value: Set<String>) =
        putString(key, Json.encodeToString(value))

    fun remove(key: String) = synchronized(draftLock) {
        if (draftActive) draftValues[key] = null else settings.remove(key)
    }

    fun applyContextMode(mode: String) {
        contextMode = mode
        when (mode) {
            "economy" -> {
                dualModel = false
                historyMessages = 12
                putInt("private_anchor_count", 3)
                putInt("private_shared_memory_count", 1)
                putInt("private_group_context_count", 1)
                putInt("group_member_memory_count", 1)
                putInt("event_context_count", 2)
                putInt("daily_moment_target", 1)
                autoDiaryEnabled = false
                putInt("comment_bystander_max", 1)
                putInt("daily_auto_ai_limit", 20)
                putInt("tick_auto_ai_limit", 2)
            }
            "standard" -> {
                historyMessages = 20
                putInt("private_anchor_count", 5)
                putInt("private_shared_memory_count", 3)
                putInt("private_group_context_count", 2)
                putInt("group_member_memory_count", 2)
                putInt("event_context_count", 5)
                putInt("daily_moment_target", 2)
                autoDiaryEnabled = true
                putInt("comment_bystander_max", 3)
                putInt("daily_auto_ai_limit", 40)
                putInt("tick_auto_ai_limit", 3)
            }
            "full" -> {
                historyMessages = 40
                putInt("private_anchor_count", 8)
                putInt("private_shared_memory_count", 5)
                putInt("private_group_context_count", 4)
                putInt("group_member_memory_count", 4)
                putInt("event_context_count", 8)
                putInt("daily_moment_target", 3)
                autoDiaryEnabled = true
                putInt("comment_bystander_max", 4)
                putInt("daily_auto_ai_limit", 80)
                putInt("tick_auto_ai_limit", 5)
            }
        }
    }

    fun clear() =
        settings.clear()
}
