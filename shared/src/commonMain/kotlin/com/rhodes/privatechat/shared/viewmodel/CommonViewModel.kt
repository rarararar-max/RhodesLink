package com.rhodes.privatechat.shared.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

abstract class CommonViewModel {
    protected val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    open fun onCleared() {
        viewModelScope.cancel()
    }
}
