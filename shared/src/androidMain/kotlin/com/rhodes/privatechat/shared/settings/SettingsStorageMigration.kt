package com.rhodes.privatechat.shared.settings

import android.content.SharedPreferences

/** Control metadata is never copied as user settings. Never deletes the source store. */
class SettingsStorageMigration(
    private val plain: SharedPreferences,
    private val openSecure: () -> SharedPreferences,
) {
    companion object {
        const val REQUEST_PLAIN = "diagnostic_use_plain_settings"
        private const val ACTIVE = "settings_storage_active_v2"
        private const val RECOVERED = "settings_storage_recovered_v2"
        private fun control(key: String) = key == REQUEST_PLAIN || key.startsWith("settings_storage_") ||
            key.startsWith("settings_plain_fallback_") || key == "settings_keystore_probe_ms" || key.startsWith("probe_")
    }

    private fun values(store: SharedPreferences) = store.all.filterKeys { !control(it) }

    private fun write(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> { check(value.all { it is String }); editor.putStringSet(key, value.filterIsInstance<String>().toSet()) }
            else -> error("设置包含不支持的数据类型，未切换存储方式")
        }
    }

    private fun copy(source: SharedPreferences, target: SharedPreferences, exact: Boolean) {
        val sourceValues = values(source)
        val old = values(target)
        val desired = if (exact) sourceValues else old.toMutableMap().apply {
            sourceValues.forEach { (key, value) ->
                if (key !in old || (old[key] is String && (old[key] as String).isBlank())) put(key, value)
            }
        }
        if (old == desired) return
        val editor = target.edit()
        if (exact) (old.keys - desired.keys).forEach(editor::remove)
        desired.forEach { (key, value) -> write(editor, key, value) }
        check(editor.commit()) { "设置迁移写入失败，已保留原设置，请勿清除数据" }
        check(values(target) == desired) { "设置迁移校验失败，已保留原设置" }
    }

    private fun mark(mode: String) {
        check(plain.edit().putString(ACTIVE, mode).putBoolean(RECOVERED, true).remove("settings_storage_error").commit()) {
            "无法保存设置迁移状态，原设置仍保留"
        }
    }

    /** Called once at startup; later calls use the factory's cached live store. */
    fun select(): SharedPreferences {
        val requested = if (plain.getBoolean(REQUEST_PLAIN, false)) "plain" else "secure"
        val previous = plain.getString(ACTIVE, null)
        if (previous == "plain" && requested == "plain") return plain
        val secure = openSecure()
        if (previous == null) {
            if (requested == "plain") {
                // Repair installs affected by the old switch. Keep values subsequently entered
                // in plain storage; recover absent/blank fields from the untouched secure store.
                copy(secure, plain, exact = false)
            } else {
                copy(plain, secure, exact = false)
            }
        } else if (previous != requested) {
            // Re-read the SOURCE on restart, so edits made after toggling are included.
            copy(if (previous == "plain") plain else secure, if (requested == "plain") plain else secure, exact = true)
        }
        mark(requested)
        return if (requested == "plain") plain else secure
    }

    /** Preflight migration while keeping the current process on the original store. */
    fun request(usePlain: Boolean, current: SharedPreferences) {
        val target = if (usePlain) plain else openSecure()
        if (current !== target) copy(current, target, exact = true)
        check(plain.edit().putBoolean(REQUEST_PLAIN, usePlain).commit()) {
            "无法保存切换请求，当前设置保持不变"
        }
    }

    /** Degrade only after a complete verified copy; a failed copy keeps secure storage active. */
    fun fallback(current: SharedPreferences): SharedPreferences {
        copy(current, plain, exact = true)
        check(plain.edit().putString(ACTIVE, "plain").putBoolean(RECOVERED, true)
            .putBoolean(REQUEST_PLAIN, true).putBoolean("settings_plain_fallback_used", true).remove("settings_storage_error").commit()) {
            "无法保存降级状态，保留原设置"
        }
        return plain
    }
}
