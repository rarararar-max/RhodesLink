package com.example.rhodesterminal.shared.db

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseWrapper(context: Context) {
    actual val database: RhodesDatabase = run {
        // 一次性迁移：删除旧版 Room 数据库（版本14），避免与 SQLDelight schema（版本1）冲突
        val prefs = context.getSharedPreferences("db_migration", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("old_db_deleted", false)) {
            context.deleteDatabase("rhodes_terminal.db")
            prefs.edit().putBoolean("old_db_deleted", true).apply()
        }

        RhodesDatabase(
            AndroidSqliteDriver(RhodesDatabase.Schema, context, "rhodes_terminal.db")
        )
    }
}
