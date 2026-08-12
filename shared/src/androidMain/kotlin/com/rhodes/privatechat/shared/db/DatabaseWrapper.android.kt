package com.rhodes.privatechat.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseWrapper(context: Context) {
    init {
        DatabaseCompatibility.prepareBeforeOpen(context)
    }

    actual val database: RhodesDatabase = RhodesDatabase(
        AndroidSqliteDriver(RhodesDatabase.Schema, context, "rhodes_terminal.db")
    ).also {
        DatabaseCompatibility.restoreMissingCoreDataFromPre113Backup(context)
        DatabaseCompatibility.markDerivedDataCleaned(context)
    }
}
