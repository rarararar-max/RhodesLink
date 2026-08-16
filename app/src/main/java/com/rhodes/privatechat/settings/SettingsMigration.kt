package com.rhodes.privatechat.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One-time migration from old Prefs SharedPreferences files to the unified rhodes_settings store.
 * Call from RhodesApplication.onCreate() before startKoin.
 */
object SettingsMigration {
    private const val TARGET_SP = "rhodes_settings"
    private const val MIGRATION_DONE_KEY = "prefs_migration_done_v1"
    private const val MIGRATION_FIX_V2_KEY = "prefs_migration_fix_v2"
    private const val CONTINUITY_OPTIMIZATIONS_INITIALIZED_KEY = "continuity_optimizations_initialized_v1"
    private const val FRESH_INSTALL_KEY = "fresh_install_v1"
    private val legacyPreferenceFiles = listOf(
        "chat_prefs", "model_prefs", "user_prefs", "session_hidden", "token_stats", "op_perms",
        "dispatch", "op_lmb", "moment_prefs", "mahjong_history", "prompt_templates"
    )

    private fun initializeContinuityOptimizations(context: Context, target: SharedPreferences) {
        if (safeBoolean(target, CONTINUITY_OPTIMIZATIONS_INITIALIZED_KEY, false)) return
        val editor = target.edit()
        // Defaults apply only when neither the unified store nor the legacy store has a value.
        // Never overwrite a choice made by an existing 1.11/1.12 user.
        val legacy = context.getSharedPreferences("chat_prefs", Context.MODE_PRIVATE)
        editor.putBoolean(CONTINUITY_OPTIMIZATIONS_INITIALIZED_KEY, true)
        editor.commit()
    }

    private val intFixKeys = listOf(
        "msg_counter", "impression_msg_counter", "summary_threshold",
        "ai_temperature", "clean_days", "summary_retain", "history_messages",
        "nar_seg_min", "nar_seg_max", "nar_min", "nar_max",
        "dia_seg_min", "dia_seg_max", "dia_min", "dia_max", "daily_moment_target",
        "moment_min_chars", "moment_max_chars", "diary_min_chars", "diary_max_chars",
        "online_min_chars", "online_max_chars", "online_min_segs", "online_max_segs",
        "dispatch_min_chars", "dispatch_max_chars", "group_chat_min_interval", "group_chat_max_interval",
        "group_auto_min", "group_auto_max", "group_msg_min", "group_msg_max",
        "group_speech_min", "group_speech_max", "group_nar_seg_min", "group_nar_seg_max",
        "group_nar_min", "group_nar_max", "comment_min_chars", "comment_max_chars",
        "clean_days_messages", "clean_days_anchors", "clean_days_diaries", "clean_days_moments",
        "clean_days_dispatches", "hypnosis_round", "max_context_tokens",
        "impression_threshold", "private_anchor_count", "private_shared_memory_count", "private_group_context_count",
        "group_member_memory_count", "group_user_event_count", "moment_user_post_observer_count",
        "group_relationship_hint_count", "moment_recent_post_count", "moment_user_related_rate",
        "comment_context_count", "comment_memory_count", "comment_bystander_min", "comment_bystander_max",
        "diary_group_summary_count", "diary_relation_event_count",
        "daily_diary_operator_limit", "daily_proactive_limit", "group_auto_max_rounds",
        "daily_intimacy_cap", "today_intimacy_count", "lmb", "daily_lmb_count", "lmb_daily_count",
        "last_seen_moment_id", "last_seen_comment_id"
    )

    fun migrateIfNeeded(context: Context) {
        val target = context.getSharedPreferences(TARGET_SP, Context.MODE_PRIVATE)
        if (safeBoolean(target, MIGRATION_DONE_KEY, false)) {
            runFixV2IfNeeded(context, target)
            initializeContinuityOptimizations(context, target)
            return
        }

        // A clean install has neither a database nor any legacy preference values. Mark it once
        // so future launches never treat its newly created database as an upgrade candidate.
        val hasExistingDatabase = context.getDatabasePath("rhodes_terminal.db").exists()
        val hasLegacyPreferences = legacyPreferenceFiles.any { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.isNotEmpty()
        }
        if (target.all.isEmpty() && !hasExistingDatabase && !hasLegacyPreferences) {
            target.edit()
                .putBoolean(FRESH_INSTALL_KEY, true)
                .putBoolean(MIGRATION_DONE_KEY, true)
                .putBoolean(MIGRATION_FIX_V2_KEY, true)
                .commit()
            initializeContinuityOptimizations(context, target)
            return
        }

        val editor = target.edit()

        // 1. chat_prefs -> rhodes_settings (string keys only)
        migrateFile(context, "chat_prefs", editor, listOf(
            "api_key", "daily_summary_date", "hypnosis_cmd", "last_mode"
        ))
        // Migrate int keys
        migrateIntKeys(context, "chat_prefs", editor, listOf(
            "msg_counter", "impression_msg_counter", "summary_threshold",
            "ai_temperature", "clean_days", "summary_retain", "history_messages",
            "nar_seg_min", "nar_seg_max", "nar_min", "nar_max",
            "dia_seg_min", "dia_seg_max", "dia_min", "dia_max",
            "daily_moment_target",
            "moment_min_chars", "moment_max_chars", "diary_min_chars", "diary_max_chars",
            "online_min_chars", "online_max_chars", "online_min_segs", "online_max_segs",
            "dispatch_min_chars", "dispatch_max_chars",
            "group_chat_min_interval", "group_chat_max_interval",
            "group_auto_min", "group_auto_max",
            "group_msg_min", "group_msg_max",
            "group_speech_min", "group_speech_max",
            "group_nar_seg_min", "group_nar_seg_max",
            "group_nar_min", "group_nar_max",
            "comment_min_chars", "comment_max_chars",
            "clean_days_messages", "clean_days_anchors", "clean_days_diaries",
            "clean_days_moments", "clean_days_dispatches",
            "hypnosis_round"
        ))
        // Migrate boolean keys
        migrateBooleanKeys(context, "chat_prefs", editor, listOf(
            "dispatch_fast_mode", "dark_mode"
        ))
        // Migrate dynamic keys (bg_*, gbg_*, group_auto_*, group_mode_*, moment_count_*)
        migrateDynamicKeys(context, "chat_prefs", editor)

        // 2. model_prefs -> rhodes_settings
        migrateFile(context, "model_prefs", editor, listOf(
            "provider", "model_name", "custom_url"
        ))

        // 3. user_prefs -> rhodes_settings (with key remapping)
        val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        if (userPrefs.contains("nickname")) editor.putString("user_name", userPrefs.getString("nickname", "博士"))
        if (userPrefs.contains("gender")) editor.putString("user_gender", userPrefs.getString("gender", ""))
        if (userPrefs.contains("bio")) editor.putString("user_signature", userPrefs.getString("bio", ""))
        if (userPrefs.contains("avatar_uri")) editor.putString("user_avatar_uri", userPrefs.getString("avatar_uri", ""))

        // 4. session_hidden -> rhodes_settings
        migrateStringSetKeysAsJson(context, "session_hidden", editor, listOf("hidden_ids"))

        // 5. token_stats -> rhodes_settings
        migrateDynamicKeys(context, "token_stats", editor)

        // 6. op_perms -> rhodes_settings
        migrateDynamicKeys(context, "op_perms", editor)

        // 7. dispatch -> rhodes_settings
        migrateFile(context, "dispatch", editor, listOf(
            "lmb_refresh_date", "reward_date"
        ))
        migrateIntKeys(context, "dispatch", editor, listOf("lmb"))
        migrateIntKey(context, "dispatch", editor, "lmb_daily_count", "daily_lmb_count")

        // 8. op_lmb -> rhodes_settings
        migrateDynamicKeys(context, "op_lmb", editor)

        // 9. moment_prefs -> rhodes_settings
        migrateLongKeys(context, "moment_prefs", editor, listOf(
            "last_seen_moment_id", "last_seen_comment_id"
        ))

        // 10. mahjong_history -> rhodes_settings
        migrateStringKey(context, "mahjong_history", editor, "games", "mahjong_history_json")

        // 11. prompt_templates -> rhodes_settings
        migrateDynamicKeys(context, "prompt_templates", editor)

        // 修复已迁移用户：被旧版 migrateFile 存成 String 的 int/bool 转回正确类型
        fixupCorruptedInts(context, editor, intFixKeys)
        fixupCorruptedBooleans(context, editor, listOf("dispatch_fast_mode", "dark_mode", "permissions_initialized", "initial_hidden_done"))
        editor.putBoolean(MIGRATION_DONE_KEY, true)
        editor.putBoolean(MIGRATION_FIX_V2_KEY, true)
        editor.commit()
        initializeContinuityOptimizations(context, target)
    }

    private fun runFixV2IfNeeded(context: Context, target: SharedPreferences) {
        val editor = target.edit()

        val hidden = target.all["hidden_ids"]
        if (hidden is Set<*>) {
            @Suppress("UNCHECKED_CAST")
            editor.putString("hidden_ids", Json.encodeToString(hidden.filterIsInstance<String>().toSet()))
        } else {
            migrateStringSetKeysAsJson(context, "session_hidden", editor, listOf("hidden_ids"))
        }

        if (!target.contains("daily_lmb_count")) {
            if (target.contains("lmb_daily_count")) editor.putInt("daily_lmb_count", safeInt(target, "lmb_daily_count", 0))
            else migrateIntKey(context, "dispatch", editor, "lmb_daily_count", "daily_lmb_count")
        }

        if (!target.contains("mahjong_history_json")) {
            if (target.contains("games")) safeString(target, "games", null)?.let { editor.putString("mahjong_history_json", it) }
            else migrateStringKey(context, "mahjong_history", editor, "games", "mahjong_history_json")
        }

        fixupCorruptedInts(context, editor, intFixKeys)
        fixupCorruptedBooleans(context, editor, listOf("dispatch_fast_mode", "dark_mode", "permissions_initialized", "initial_hidden_done"))

        editor.putBoolean(MIGRATION_FIX_V2_KEY, true)
        editor.commit()
    }

    private fun fixupCorruptedInts(context: Context, editor: SharedPreferences.Editor, keys: List<String>) {
        val target = context.getSharedPreferences(TARGET_SP, Context.MODE_PRIVATE)
        for (key in keys) {
            val v = target.all[key]
            if (v is String) {
                val intVal = v.toIntOrNull()
                if (intVal != null) editor.putInt(key, intVal)
            }
        }
    }

    private fun fixupCorruptedBooleans(context: Context, editor: SharedPreferences.Editor, keys: List<String>) {
        val target = context.getSharedPreferences(TARGET_SP, Context.MODE_PRIVATE)
        for (key in keys) {
            val v = target.all[key]
            if (v is String) {
                editor.putBoolean(key, v == "true")
            }
        }
    }

    private fun migrateFile(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                val value = safeString(source, key, null)
                if (value != null) editor.putString(key, value)
            }
        }
    }

    private fun migrateIntKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                editor.putInt(key, safeInt(source, key, 0))
            }
        }
    }

    private fun migrateIntKey(context: Context, spName: String, editor: SharedPreferences.Editor, sourceKey: String, targetKey: String) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        if (source.contains(sourceKey)) editor.putInt(targetKey, safeInt(source, sourceKey, 0))
    }

    private fun migrateStringKey(context: Context, spName: String, editor: SharedPreferences.Editor, sourceKey: String, targetKey: String) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        if (source.contains(sourceKey)) safeString(source, sourceKey, null)?.let { editor.putString(targetKey, it) }
    }

    private fun migrateBooleanKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                editor.putBoolean(key, safeBoolean(source, key, false))
            }
        }
    }

    private fun migrateLongKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                editor.putLong(key, safeLong(source, key, 0L))
            }
        }
    }

    private fun migrateStringSetKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                val set = safeStringSet(source, key)
                if (set != null) editor.putStringSet(key, set)
            }
        }
    }

    private fun migrateStringSetKeysAsJson(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                val set = safeStringSet(source, key)
                if (set != null) editor.putString(key, Json.encodeToString(set))
            }
        }
    }

    private fun safeString(prefs: SharedPreferences, key: String, default: String?): String? = when (val value = prefs.all[key]) {
        is String -> value
        null -> default
        else -> value.toString()
    }

    private fun safeInt(prefs: SharedPreferences, key: String, default: Int): Int = when (val value = prefs.all[key]) {
        is Int -> value
        is Long -> value.toInt()
        is Float -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        is Boolean -> if (value) 1 else 0
        else -> default
    }

    private fun safeLong(prefs: SharedPreferences, key: String, default: Long): Long = when (val value = prefs.all[key]) {
        is Long -> value
        is Int -> value.toLong()
        is Float -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        is Boolean -> if (value) 1L else 0L
        else -> default
    }

    private fun safeBoolean(prefs: SharedPreferences, key: String, default: Boolean): Boolean = when (val value = prefs.all[key]) {
        is Boolean -> value
        is String -> value.equals("true", true) || value == "1"
        is Int -> value != 0
        is Long -> value != 0L
        else -> default
    }

    private fun safeStringSet(prefs: SharedPreferences, key: String): Set<String>? = when (val value = prefs.all[key]) {
        is Set<*> -> value.filterIsInstance<String>().toSet()
        is String -> try { Json.decodeFromString<Set<String>>(value) } catch (_: Exception) { null }
        else -> null
    }

    private fun migrateDynamicKeys(context: Context, spName: String, editor: SharedPreferences.Editor) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        val target = context.getSharedPreferences(TARGET_SP, Context.MODE_PRIVATE)
        val all = source.all
        for ((key, value) in all) {
            if (target.contains(key)) continue // don't overwrite already-migrated
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as? Set<String>)
            }
        }
    }
}
