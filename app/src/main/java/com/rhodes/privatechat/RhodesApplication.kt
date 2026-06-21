package com.rhodes.privatechat

import android.app.Application
import com.rhodes.privatechat.di.appModule
import com.rhodes.privatechat.settings.SettingsMigration
import com.rhodes.privatechat.shared.db.DatabaseWrapper
import com.rhodes.privatechat.shared.di.sharedModule
import com.rhodes.privatechat.shared.settings.AndroidSettingsFactory
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class RhodesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidSettingsFactory.init(this)
        SettingsMigration.migrateIfNeeded(this)
        startKoin {
            androidLogger()
            androidContext(this@RhodesApplication)
            modules(sharedModule(DatabaseWrapper(this@RhodesApplication)), appModule)
        }
    }
}
