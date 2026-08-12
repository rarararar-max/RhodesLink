package com.rhodes.privatechat.shared.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        DatabaseCompatibility.prepareBeforeOpen(context)
        return AndroidSqliteDriver(RhodesDatabase.Schema, context, "rhodes_terminal.db")
            .also {
                DatabaseCompatibility.restoreMissingCoreDataFromPre113Backup(context)
                DatabaseCompatibility.markDerivedDataCleaned(context)
            }
    }
}
