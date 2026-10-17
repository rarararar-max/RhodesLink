package com.rhodes.privatechat.shared.settings

/** Optional platform capability. Never emulate an atomic wallet update with separate writes. */
interface AtomicSettingsBatch {
    fun commitSupportState(strings: Map<String, String>, ints: Map<String, Int>): Boolean
}
