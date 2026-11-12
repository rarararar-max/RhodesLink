package com.rhodes.privatechat.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseWrapper private constructor(context: Context, private val suppliedDatabase: RhodesDatabase?) {
    constructor(context: Context) : this(context, null)
    private val appContext = context.applicationContext
    // Construction only registers the handle. Application warms it on IO before navigation.
    actual val database: RhodesDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        suppliedDatabase ?: openDatabase()
    }

    private fun openDatabase(): RhodesDatabase {
        DatabaseCompatibility.prepareBeforeOpen(appContext)
        return RhodesDatabase(AndroidSqliteDriver(RhodesDatabase.Schema, appContext, "rhodes_terminal.db")).also {
            DatabaseCompatibility.restoreMissingCoreDataFromPre113Backup(appContext)
            DatabaseCompatibility.markDerivedDataCleaned(appContext)
        }
    }
    actual suspend fun <T> withBackupReader(block: suspend (DatabaseWrapper) -> T): T {
        // Ensure compatibility preparation has completed before opening a second handle.
        database
        val driver = AndroidSqliteDriver(RhodesDatabase.Schema, appContext, "rhodes_terminal.db")
        return try {
            block(DatabaseWrapper(appContext, RhodesDatabase(ChunkedBackupDriver(driver))))
        } finally {
            driver.close()
        }
    }
}
