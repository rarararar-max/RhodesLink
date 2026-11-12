package com.rhodes.privatechat.shared.settings

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

object AndroidSettingsFactory {
    private lateinit var appContext: Context
    /** One encrypted write slower than this means the Keystore path is unusable for chat prompts. */
    private const val SLOW_KEYSTORE_MS = 1_500L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun atomicSettings(prefs: android.content.SharedPreferences): ObservableSettings {
        val delegate = SharedPreferencesSettings(prefs)
        return object : ObservableSettings by delegate, AtomicSettingsBatch {
            override fun commitSupportState(strings: Map<String, String>, ints: Map<String, Int>): Boolean {
                val editor = prefs.edit()
                strings.forEach { (key, value) -> editor.putString(key, value) }
                ints.forEach { (key, value) -> editor.putInt(key, value) }
                return editor.commit()
            }
        }
    }

    private var activePreferences: android.content.SharedPreferences? = null
    private var cachedSettings: ObservableSettings? = null

    private fun securePreferences(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(appContext, "rhodes_settings_secure", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    @Synchronized
    fun createSettings(): ObservableSettings {
        cachedSettings?.let { return it }
        val plain = appContext.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE)
        val migration = SettingsStorageMigration(plain, ::securePreferences)
        val selected = try {
            migration.select()
        } catch (error: Exception) {
            // An unreadable encrypted store cannot be recovered by fabricating an empty store.
            // Preserve both files and record only the error type (never credentials).
            plain.edit().putString("settings_storage_error", error.javaClass.simpleName).commit()
            val previous = plain.getString("settings_storage_active_v2", null)
            val retained = if (previous == "secure") runCatching { securePreferences() }.getOrNull() else null
            if (retained != null) {
                plain.edit().putBoolean(SettingsStorageMigration.REQUEST_PLAIN, false).commit()
                retained
            } else {
                // Keep recovery pending: do not label an empty/legacy view as migrated.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(appContext,
                        "设置迁移或读取未完成，原数据已保留。请勿清除数据，可在调试日志中查看设置存储提示。",
                        android.widget.Toast.LENGTH_LONG).show()
                }
                plain
            }
        }
        var chosen = selected
        if (selected !== plain) {
            val start = android.os.SystemClock.elapsedRealtime()
            val usable = runCatching {
                check(selected.edit().putLong("probe_keystore_latency_check", start).commit())
                check(selected.edit().remove("probe_keystore_latency_check").commit())
            }.isSuccess
            val elapsed = android.os.SystemClock.elapsedRealtime() - start
            if (!usable || elapsed > SLOW_KEYSTORE_MS) {
                // If migration fails, continue using the source rather than losing configuration.
                chosen = runCatching { migration.fallback(selected) }.getOrElse {
                    plain.edit().putString("settings_storage_error", it.javaClass.simpleName).commit()
                    selected
                }
                plain.edit().putLong("settings_keystore_probe_ms", elapsed).commit()
            }
        }
        activePreferences = chosen
        return atomicSettings(chosen).also { cachedSettings = it }
    }

    @Synchronized
    fun requestPlainSettings(usePlain: Boolean) {
        createSettings()
        val plain = appContext.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE)
        check(!plain.contains("settings_storage_error") || plain.contains("settings_storage_active_v2")) { "设置恢复尚未完成，请保留原数据后重试，暂不切换设置库" }
        SettingsStorageMigration(plain, ::securePreferences).request(usePlain, checkNotNull(activePreferences))
    }

}

actual fun createPlatformSettings(): ObservableSettings = AndroidSettingsFactory.createSettings()

/**
 * Times one real commit on the store the app is currently using (encrypted unless the diagnostic plain
 * switch is on). The chat pipeline writes its step markers through this store, and an encrypted commit
 * re-encrypts and rewrites the whole file via Keystore - which no coroutine timeout can interrupt. A
 * large value here is the profile of "the prompt-build stage burns exactly its 50s budget".
 */
fun measureSecureSettingsCommit(): Long {
    val store = AndroidSettingsFactory.createSettings()
    val start = android.os.SystemClock.elapsedRealtime()
    runCatching { store.putLong("probe_secure_commit_ms", start) }
    runCatching { store.remove("probe_secure_commit_ms") }
    return android.os.SystemClock.elapsedRealtime() - start
}
