package com.rhodes.privatechat.viewmodel.shared
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
internal object MemoryPromotionLocks { val locks = ConcurrentHashMap<String, Mutex>() }
