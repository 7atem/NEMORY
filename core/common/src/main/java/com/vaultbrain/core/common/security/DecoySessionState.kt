package com.vaultbrain.core.common.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DecoySessionState {
    private val _isDecoy = MutableStateFlow(false)
    val isDecoy: StateFlow<Boolean> = _isDecoy.asStateFlow()

    fun setDecoyMode(active: Boolean) {
        _isDecoy.value = active
    }
}
