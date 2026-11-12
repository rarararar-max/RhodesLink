package com.rhodes.privatechat

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.platform.app.InstrumentationRegistry
import com.rhodes.privatechat.shared.settings.SettingsStorageMigration
import org.junit.Assert.*
import org.junit.Test

class SettingsStorageMigrationIntegrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block: (SharedPreferences, SharedPreferences) -> Unit) {
        val base = "settings_migration_test_${System.nanoTime()}"
        val plain = context.getSharedPreferences(base, Context.MODE_PRIVATE)
        val secure = context.getSharedPreferences("${base}_secure", Context.MODE_PRIVATE)
        try { block(plain, secure) } finally {
            context.deleteSharedPreferences(base)
            context.deleteSharedPreferences("${base}_secure")
        }
    }
    private fun fill(store: SharedPreferences) {
        check(store.edit().putString("api_key", "fixture-key")
            .putString("user_name", "博士测试").putString("user_avatar_uri", "file:///fixture.png")
            .putString("user_bio", "医疗部夜班").putString("user_signature", "今天也要好好休息")
            .putInt("lmb", 1234).putLong("last_at", 999L).putFloat("volume", 0.6f)
            .putBoolean("enabled", false).putStringSet("tags", setOf("a", "b")).commit())
    }

    @Test fun bothDirectionsAndRestartIncludeEditsAfterToggleAndIntentionalClears() = fixture { plain, secure ->
        fill(secure)
        val manager = SettingsStorageMigration(plain) { secure }
        assertSame(secure, manager.select())
        manager.request(true, secure)
        assertEquals("fixture-key", plain.getString("api_key", ""))
        secure.edit().putString("user_name", "退出前新昵称").commit()
        assertSame(plain, SettingsStorageMigration(plain) { secure }.select())
        assertEquals("退出前新昵称", plain.getString("user_name", ""))
        plain.edit().putString("api_key", "new-key").putString("user_signature", "").remove("user_bio").commit()
        manager.request(false, plain)
        plain.edit().putInt("lmb", 4321).commit()
        assertSame(secure, SettingsStorageMigration(plain) { secure }.select())
        assertEquals("new-key", secure.getString("api_key", ""))
        assertEquals("", secure.getString("user_signature", "nonempty"))
        assertFalse(secure.contains("user_bio"))
        assertEquals(4321, secure.getInt("lmb", 0))
        assertEquals(setOf("a", "b"), secure.getStringSet("tags", emptySet()))
        assertEquals(999L, secure.getLong("last_at", 0))
        assertFalse(secure.getBoolean("enabled", true))
        assertEquals(0.6f, secure.getFloat("volume", 0f), 0f)
    }

    @Test fun oldBrokenPlainModeRecoversMissingFieldsButKeepsNewNonblankEdits() = fixture { plain, secure ->
        fill(secure)
        plain.edit().putBoolean(SettingsStorageMigration.REQUEST_PLAIN, true)
            .putString("api_key", "").putString("user_name", "新昵称").commit()
        assertSame(plain, SettingsStorageMigration(plain) { secure }.select())
        assertEquals("fixture-key", plain.getString("api_key", ""))
        assertEquals("新昵称", plain.getString("user_name", ""))
        assertEquals("医疗部夜班", plain.getString("user_bio", ""))
        plain.edit().putString("api_key", "").commit()
        SettingsStorageMigration(plain) { error("Should not open secure once plain is authoritative") }.select()
        assertEquals("", plain.getString("api_key", "x"))
        assertEquals("fixture-key", secure.getString("api_key", ""))
    }

    @Test fun failedCommitDoesNotSwitchOrDeleteSource() = fixture { plain, secure ->
        fill(secure)
        SettingsStorageMigration(plain) { secure }.select()
        val failing = object : SharedPreferences by plain {
            override fun edit(): SharedPreferences.Editor {
                val delegate = plain.edit()
                return object : SharedPreferences.Editor by delegate {
                    override fun commit() = false
                }
            }
        }
        assertThrows(IllegalStateException::class.java) {
            SettingsStorageMigration(failing) { secure }.request(true, secure)
        }
        assertFalse(plain.getBoolean(SettingsStorageMigration.REQUEST_PLAIN, false))
        assertEquals("fixture-key", secure.getString("api_key", ""))
        assertEquals("博士测试", secure.getString("user_name", ""))
    }

    @Test fun failedFallbackNeverMarksPlainActiveOrDestroysSecureValues() = fixture { plain, secure ->
        fill(secure)
        SettingsStorageMigration(plain) { secure }.select()
        val failing = object : SharedPreferences by plain {
            override fun edit(): SharedPreferences.Editor {
                val delegate = plain.edit()
                return object : SharedPreferences.Editor by delegate { override fun commit() = false }
            }
        }
        assertThrows(IllegalStateException::class.java) {
            SettingsStorageMigration(failing) { secure }.fallback(secure)
        }
        assertEquals("secure", plain.getString("settings_storage_active_v2", ""))
        assertFalse(plain.getBoolean(SettingsStorageMigration.REQUEST_PLAIN, false))
        assertEquals("fixture-key", secure.getString("api_key", ""))
    }

    @Test fun unavailableSecureTargetKeepsPlainAndDoesNotChangeRequestedMode() = fixture { plain, secure ->
        fill(plain)
        plain.edit().putBoolean(SettingsStorageMigration.REQUEST_PLAIN, true).commit()
        SettingsStorageMigration(plain) { secure }.select()
        assertThrows(IllegalStateException::class.java) {
            SettingsStorageMigration(plain) { error("fixture unavailable") }.request(false, plain)
        }
        assertTrue(plain.getBoolean(SettingsStorageMigration.REQUEST_PLAIN, false))
        assertEquals("fixture-key", plain.getString("api_key", ""))
    }

    @Test fun automaticFallbackCopiesAndVerifiesAllValuesBeforeChangingMode() = fixture { plain, secure ->
        fill(secure)
        val manager = SettingsStorageMigration(plain) { secure }
        manager.select()
        assertSame(plain, manager.fallback(secure))
        assertTrue(plain.getBoolean(SettingsStorageMigration.REQUEST_PLAIN, false))
        assertEquals("fixture-key", plain.getString("api_key", ""))
        assertEquals("file:///fixture.png", plain.getString("user_avatar_uri", ""))
        assertSame(plain, SettingsStorageMigration(plain) { error("Must bypass secure") }.select())
    }

    @Test fun migratesThroughRealAndroidEncryptedPreferences() {
        val base = "encrypted_migration_test_${System.nanoTime()}"
        val plain = context.getSharedPreferences(base, Context.MODE_PRIVATE)
        val secure = EncryptedSharedPreferences.create(context, "${base}_secure",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        try {
            fill(secure)
            val manager = SettingsStorageMigration(plain) { secure }
            manager.select()
            manager.request(true, secure)
            assertSame(plain, SettingsStorageMigration(plain) { secure }.select())
            assertEquals("fixture-key", plain.getString("api_key", ""))
            plain.edit().putString("api_key", "replacement-key").commit()
            manager.request(false, plain)
            SettingsStorageMigration(plain) { secure }.select()
            assertEquals("replacement-key", secure.getString("api_key", ""))
            assertEquals("博士测试", secure.getString("user_name", ""))
        } finally {
            context.deleteSharedPreferences(base)
            context.deleteSharedPreferences("${base}_secure")
        }
    }
}
