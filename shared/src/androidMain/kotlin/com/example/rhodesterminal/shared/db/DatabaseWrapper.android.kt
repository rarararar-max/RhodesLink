package com.example.rhodesterminal.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseWrapper(context: Context) {
    actual val database: RhodesDatabase = RhodesDatabase(
        AndroidSqliteDriver(RhodesDatabase.Schema, context, "rhodes_terminal.db")
    )
}
