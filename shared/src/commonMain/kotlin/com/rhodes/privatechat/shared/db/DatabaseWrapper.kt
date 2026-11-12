package com.rhodes.privatechat.shared.db

expect class DatabaseWrapper {
    val database: RhodesDatabase
    suspend fun <T> withBackupReader(block: suspend (DatabaseWrapper) -> T): T
}
