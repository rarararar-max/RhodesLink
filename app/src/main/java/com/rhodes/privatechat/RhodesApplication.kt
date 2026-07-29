package com.rhodes.privatechat

import android.app.Application
import android.content.Context
import com.rhodes.privatechat.di.appModule
import com.rhodes.privatechat.settings.SettingsMigration
import com.rhodes.privatechat.automation.DailyContentScheduler
import com.rhodes.privatechat.automation.GroupAutoChatScheduler
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.di.sharedModule
import com.rhodes.privatechat.shared.settings.AndroidSettingsFactory
import com.rhodes.privatechat.shared.data.ChatRepository
import com.rhodes.privatechat.shared.settings.SettingsRepository
import com.rhodes.privatechat.util.DebugLogger
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class RhodesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidSettingsFactory.init(this)
        DebugLogger.enabled = getSharedPreferences("rhodes_settings", Context.MODE_PRIVATE).getBoolean("debug_log_enabled", false)
        SettingsMigration.migrateIfNeeded(this)
        
        // 版本升级后清除导航栈状态，防止旧版序列化数据导致新版崩溃
        clearNavigationStateIfNeeded()
        
        startKoin {
            androidLogger()
            androidContext(this@RhodesApplication)
            modules(sharedModule(DatabaseWrapper(this@RhodesApplication)), appModule)
            DailyContentScheduler.schedulePlanner(this@RhodesApplication)
        }
        GroupAutoChatScheduler.reconcile(
            this,
            org.koin.java.KoinJavaComponent.get(ChatRepository::class.java),
            org.koin.java.KoinJavaComponent.get(SettingsRepository::class.java)
        )
    }
    
    private fun clearNavigationStateIfNeeded() {
        try {
            val prefs = getSharedPreferences("voyager_state", Context.MODE_PRIVATE)
            val runtimePrefs = getSharedPreferences("rhodes_runtime", Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt("last_version", 0)
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode

            if (lastVersion < currentVersion) {
                prefs.edit().clear().apply()
                runtimePrefs.edit().putBoolean("drop_saved_state_once", true).apply()
            }
            
            prefs.edit().putInt("last_version", currentVersion).apply()
        } catch (_: Exception) {
        }
    }
}
