package com.rhodes.privatechat.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseWrapper(context: Context) {
    private val appContext = context.applicationContext
    // Construction only registers the handle. Application warms it on IO before navigation.
    actual val database: RhodesDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DatabaseCompatibility.prepareBeforeOpen(appContext)
        RhodesDatabase(AndroidSqliteDriver(RhodesDatabase.Schema, appContext, "rhodes_terminal.db")).also {
            DatabaseCompatibility.restoreMissingCoreDataFromPre113Backup(appContext)
            DatabaseCompatibility.markDerivedDataCleaned(appContext)
        }
    }
}
