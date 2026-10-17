package com.rhodes.privatechat.viewmodel.shared

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex

object ContextMaintenanceLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun forSession(id: String): Mutex = locks.computeIfAbsent(id) { Mutex() }
}
