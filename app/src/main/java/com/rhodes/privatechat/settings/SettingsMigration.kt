package com.rhodes.privatechat.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * One-time migration from old Prefs SharedPreferences files to the unified rhodes_settings store.
 * Call from RhodesApplication.onCreate() before startKoin.
 */
object SettingsMigration {
    private const val TARGET_SP = "rhodes_settings"
    private const val MIGRATION_DONE_KEY = "prefs_migration_done_v1"

    fun migrateIfNeeded(context: Context) {
        val target = context.getSharedPreferences(TARGET_SP, Context.MODE_PRIVATE)
        if (target.getBoolean(MIGRATION_DONE_KEY, false)) return

        val editor = target.edit()

        // 1. chat_prefs -> rhodes_settings
        migrateFile(context, "chat_prefs", editor, listOf(
            "dual_model", "msg_counter", "impression_msg_counter", "summary_threshold",
            "api_key", "ai_temperature", "clean_days", "summary_retain", "history_messages",
            "nar_seg_min", "nar_seg_max", "nar_min", "nar_max",
            "dia_seg_min", "dia_seg_max", "dia_min", "dia_max",
            "daily_moment_target", "dispatch_fast_mode",
            "moment_min_chars", "moment_max_chars", "diary_min_chars", "diary_max_chars",
            "online_min_chars", "online_max_chars", "online_min_segs", "online_max_segs",
            "dispatch_min_chars", "dispatch_max_chars",
            "daily_summary_date", "hypnosis_cmd", "last_mode",
            "group_chat_min_interval", "group_chat_max_interval",
            "group_auto_min", "group_auto_max",
            "group_msg_min", "group_msg_max",
            "group_speech_min", "group_speech_max",
            "group_nar_seg_min", "group_nar_seg_max",
            "group_nar_min", "group_nar_max",
            "comment_min_chars", "comment_max_chars",
            "clean_days_messages", "clean_days_anchors", "clean_days_diaries",
            "clean_days_moments", "clean_days_dispatches"
        ))
        // Migrate int keys that are stored as int in source
        migrateIntKeys(context, "chat_prefs", editor, listOf(
            "hypnosis_rounds"
        ))
        // Migrate boolean keys
        migrateBooleanKeys(context, "chat_prefs", editor, listOf(
            "dark_mode"
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
        migrateStringSetKeys(context, "session_hidden", editor, listOf("hidden_ids"))

        // 5. token_stats -> rhodes_settings
        migrateDynamicKeys(context, "token_stats", editor)

        // 6. op_perms -> rhodes_settings
        migrateDynamicKeys(context, "op_perms", editor)

        // 7. dispatch -> rhodes_settings
        migrateFile(context, "dispatch", editor, listOf(
            "lmb_refresh_date", "reward_date"
        ))
        migrateIntKeys(context, "dispatch", editor, listOf(
            "lmb", "lmb_daily_count"
        ))

        // 8. op_lmb -> rhodes_settings
        migrateDynamicKeys(context, "op_lmb", editor)

        // 9. moment_prefs -> rhodes_settings
        migrateLongKeys(context, "moment_prefs", editor, listOf(
            "last_seen_moment_id", "last_seen_comment_id"
        ))

        // 10. mahjong_history -> rhodes_settings
        migrateFile(context, "mahjong_history", editor, listOf("games"))

        // 11. prompt_templates -> rhodes_settings
        migrateDynamicKeys(context, "prompt_templates", editor)

        editor.putBoolean(MIGRATION_DONE_KEY, true)
        editor.apply()
    }

    private fun migrateFile(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                val value = source.getString(key, null)
                if (value != null) editor.putString(key, value)
            }
        }
    }

    private fun migrateIntKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                editor.putInt(key, source.getInt(key, 0))
            }
        }
    }

    private fun migrateBooleanKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                editor.putBoolean(key, source.getBoolean(key, false))
            }
        }
    }

    private fun migrateLongKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                editor.putLong(key, source.getLong(key, 0))
            }
        }
    }

    private fun migrateStringSetKeys(context: Context, spName: String, editor: SharedPreferences.Editor, keys: List<String>) {
        val source = context.getSharedPreferences(spName, Context.MODE_PRIVATE)
        for (key in keys) {
            if (source.contains(key)) {
                val set = source.getStringSet(key, null)
                if (set != null) editor.putStringSet(key, set)
            }
        }
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
