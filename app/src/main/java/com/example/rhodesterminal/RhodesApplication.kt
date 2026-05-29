package com.example.rhodesterminal

import android.app.Application
import com.example.rhodesterminal.di.appModule
import com.example.rhodesterminal.shared.db.DatabaseWrapper
import com.example.rhodesterminal.shared.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class RhodesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@RhodesApplication)
            modules(sharedModule(DatabaseWrapper(this@RhodesApplication)), appModule)
        }
    }
}
