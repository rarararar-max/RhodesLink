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

    fun createSettings(): ObservableSettings {
        val legacy = appContext.getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE)
        // Diagnostic A/B switch. When the plain store is selected, every settings read/write bypasses the
        // Keystore-encrypted store. Motivation: a reply turn was observed stalled at "prompt_build_start"
        // for 2115s while every database, storage and model probe stayed fast, and each encrypted commit
        // re-encrypts and rewrites the whole 2201-entry file through Keystore, which no coroutine timeout
        // can interrupt. If the app chats normally with this switch ON, the encrypted settings path is
        // proven to be the blocker - and the switch doubles as a workaround for the affected user.
        // The flag is read from the PLAIN store so it is always readable, whatever the encrypted store does.
        if (legacy.getBoolean("diagnostic_use_plain_settings", false)) return atomicSettings(legacy)
        return try {
            val masterKey = MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val encrypted = EncryptedSharedPreferences.create(
                appContext,
                "rhodes_settings_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            if (legacy.all.isNotEmpty()) {
                // Keep the legacy store intact until secure storage has a verified copy.
                val encryptedValues = encrypted.all
                val editor = encrypted.edit()
                legacy.all.forEach { (key, value) ->
                    val secureValue = encryptedValues[key]
                    val restoreValue = key !in encryptedValues ||
                        (secureValue is String && secureValue.isBlank() && value is String && value.isNotBlank())
                    if (restoreValue) {
                        when (value) {
                            is String -> editor.putString(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Float -> editor.putFloat(key, value)
                            is Boolean -> editor.putBoolean(key, value)
                            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                        }
                    }
                }
                if (!editor.commit()) throw IllegalStateException("无法提交加密设置迁移")
            }
            atomicSettings(encrypted).also { settings ->
                // A degraded Keystore can make every encrypted read/write block for seconds. Prompt
                // assembly reads settings first, so on such a device no reply can ever be produced - it
                // just times out. Time one real write; on a pathologically slow store fall back to the
                // plain store for this process and record the fact for the diagnostic report.
                val start = android.os.SystemClock.elapsedRealtime()
                val usable = runCatching {
                    settings.putLong("probe_keystore_latency_check", start)
                    settings.remove("probe_keystore_latency_check")
                    true
                }.getOrDefault(false)
                val elapsed = android.os.SystemClock.elapsedRealtime() - start
                if (!usable || elapsed > SLOW_KEYSTORE_MS) {
                    legacy.edit()
                        .putBoolean("settings_plain_fallback_used", true)
                        .putLong("settings_keystore_probe_ms", elapsed)
                        .commit()
                    throw IllegalStateException("encrypted settings too slow: ${elapsed}ms")
                }
            }
        } catch (_: Exception) {
            // Keystore failures must not make an upgraded user lose API configuration or UI state.
            atomicSettings(legacy)
        }
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
