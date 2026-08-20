package com.rhodes.privatechat.data.backup

import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository

/** Non-secret settings that define the user's experience and can safely move between devices. */
object PortableSettings {
    private val stringKeys = setOf(
        "user_name", "user_gender", "user_signature", "user_avatar_uri", "appearance_skin",
        "memory_recall_mode", "personal_memory_reference_style", "context_mode",
        "status_location_pool", "status_activity_pool", "status_emotion_pool",
        "sleep_fixed_wake_text", "sleep_wake_text_mode",
        "support_conversation",
    )
    private val booleanKeys = setOf(
        "dark_mode", "source_aware_memory_enabled", "distinguish_private_memory", "unified_memory_enabled",
        "summary_cursor_enabled", "memory_v2_enabled", "moment_memory_v2_enabled",
        "private_memory_generation_enabled", "group_memory_generation_enabled", "moment_memory_generation_enabled",
        "moment_comment_memory_generation_enabled", "diary_memory_generation_enabled",
        "private_summary_generation_enabled", "group_summary_generation_enabled",
        "private_daily_summary_generation_enabled", "group_daily_summary_generation_enabled",
        "private_memory_promotion_enabled", "group_memory_promotion_enabled", "moment_memory_promotion_enabled",
        "moment_comment_memory_promotion_enabled", "diary_memory_promotion_enabled", "group_memory_copy_to_members_enabled",
        "global_public_memory_enabled", "private_recall_private_chat_memory", "private_recall_group_chat_memory",
        "private_recall_moment_memory", "private_recall_moment_comment_memory", "private_recall_relationship_memory",
        "private_recall_diary_memory", "private_recall_manual_memory", "auto_ai_enabled",
        "daily_auto_moment_enabled", "idle_proactive_chat_enabled", "auto_moment_enabled", "auto_diary_enabled",
        "quiet_hours_enabled", "auto_status_refresh", "dispatch_fast_mode",
    )
    private val intKeys = setOf(
        "ai_temperature", "history_messages", "max_context_tokens", "clean_days",
        "memory_recall_candidate_limit", "memory_v2_promote_l1_threshold", "memory_v2_promote_l2_threshold",
        "memory_v2_important_promotion_threshold", "private_group_context_count", "group_member_memory_count",
        "private_memory_extraction_threshold", "group_memory_extraction_threshold", "group_user_event_count",
        "moment_user_post_observer_count", "group_relationship_hint_count", "moment_recent_post_count",
        "moment_user_related_rate", "comment_context_count", "comment_memory_count", "comment_bystander_min",
        "comment_bystander_max", "diary_group_summary_count", "diary_relation_event_count", "daily_diary_operator_limit",
        "daily_proactive_limit", "proactive_quiet_after_user_minutes", "nar_seg_min", "nar_seg_max", "nar_min",
        "nar_max", "dia_seg_min", "dia_seg_max", "dia_min", "dia_max", "dispatch_min_chars", "dispatch_max_chars",
        "moment_min_chars", "moment_max_chars", "diary_min_chars", "diary_max_chars", "group_chat_min_interval",
        "group_chat_max_interval", "group_auto_max_rounds", "daily_intimacy_cap", "lmb", "daily_lmb_count",
        "daily_moment_target", "daily_proactive_chance", "daily_proactive_max", "quiet_hours_start", "quiet_hours_end",
        "sleep_alarm_hour", "sleep_alarm_minute", "sleep_inactivity_minutes", "sleep_dim_after_seconds",
        "sleep_snooze_minutes", "clean_days_messages", "clean_days_anchors", "clean_days_diaries",
        "clean_days_moments", "clean_days_dispatches", "group_msg_min", "group_msg_max", "group_speech_min",
        "group_speech_max", "group_nar_seg_min", "group_nar_seg_max", "group_nar_min", "group_nar_max",
        "comment_min_chars", "comment_max_chars",
    )

    suspend fun snapshot(repository: ChatRepository, settings: SettingsRepository): Map<String, String> = buildMap {
        stringKeys.forEach { put(it, "s:${settings.getString(it)}") }
        booleanKeys.forEach { put(it, "b:${settings.getBoolean(it)}") }
        intKeys.forEach { put(it, "i:${settings.getInt(it)}") }
        put("hidden_ids", "s:${settings.hiddenIds.joinToString(",")}")
        repository.getAllOperatorsSync().forEach { operator ->
            val id = operator.id
            put("operator_prompt_slot_${id}_private", "i:${settings.getInt("operator_prompt_slot_${id}_private", 1)}")
            put("operator_prompt_slot_${id}_group", "i:${settings.getInt("operator_prompt_slot_${id}_group", 1)}")
            (1..3).forEach { slot ->
                listOf("private", "group").forEach { type ->
                    val key = "operator_prompt_slot_${id}_${type}_$slot"
                    put(key, "s:${settings.getString(key)}")
                }
            }
            put("msg_$id", "b:${settings.getOperatorMsgPermission(id)}")
            put("dyn_$id", "b:${settings.getOperatorDynPermission(id)}")
            put("voice_volume_$id", "s:${settings.getOperatorVoiceVolume(id)}")
            put("chat_tts_$id", "b:${settings.getBoolean("chat_tts_$id", false)}")
            put("bg_$id", "s:${settings.getString("bg_$id")}")
            put("diary_read_at_$id", "l:${settings.getDiaryReadAt(id)}")
        }
        repository.getAllSessionsSync().forEach { session ->
            put("last_mode_${session.operatorId}", "s:${settings.getLastMode(session.operatorId)}")
            if (session.id.startsWith("group_")) put("gbg_${session.id}", "s:${settings.getString("gbg_${session.id}")}")
        }
    }

    fun apply(settings: SettingsRepository, values: Map<String, String>) {
        values.forEach { (key, encoded) ->
            val separator = encoded.indexOf(':')
            if (separator <= 0) {
                // Version 1 backups stored a small untyped settings map.
                settings.putString(key, encoded)
                return@forEach
            }
            when (encoded.substring(0, separator)) {
                "s" -> settings.putString(key, encoded.substring(separator + 1))
                "b" -> encoded.substring(separator + 1).toBooleanStrictOrNull()?.let { settings.putBoolean(key, it) }
                "i" -> encoded.substring(separator + 1).toIntOrNull()?.let { settings.putInt(key, it) }
                "l" -> encoded.substring(separator + 1).toLongOrNull()?.let { settings.putLong(key, it) }
            }
        }
    }

    suspend fun clearForRestore(repository: ChatRepository, settings: SettingsRepository) {
        (stringKeys + booleanKeys + intKeys + "hidden_ids").forEach(settings::remove)
        repository.getAllOperatorsSync().forEach { operator ->
            val id = operator.id
            listOf(
                "operator_prompt_slot_${id}_private", "operator_prompt_slot_${id}_group", "msg_$id", "dyn_$id",
                "voice_volume_$id", "chat_tts_$id", "bg_$id", "diary_read_at_$id", "last_mode_$id",
            ).forEach(settings::remove)
            (1..3).forEach { slot ->
                settings.remove("operator_prompt_slot_${id}_private_$slot")
                settings.remove("operator_prompt_slot_${id}_group_$slot")
            }
        }
        repository.getAllSessionsSync().filter { it.id.startsWith("group_") }.forEach { settings.remove("gbg_${it.id}") }
    }
}
