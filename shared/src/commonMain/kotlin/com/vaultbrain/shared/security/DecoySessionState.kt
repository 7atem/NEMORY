package com.vaultbrain.shared.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state manager for Decoy Mode across Android and iOS platforms.
 *
 * When [isDecoy] is true:
 * - Database and vector store reads return empty results.
 * - Mutation operations are completely suppressed.
 * - In-memory caches and sensitive view models cancel load jobs immediately.
 */
object DecoySessionState {
    private val _isDecoy = MutableStateFlow(false)
    val isDecoyState: StateFlow<Boolean> = _isDecoy.asStateFlow()

    val isDecoy: Boolean
        get() = _isDecoy.value

    fun enterDecoyMode() {
        _isDecoy.value = true
    }

    fun exitDecoyMode() {
        _isDecoy.value = false
    }

    /**
     * Executes [block] only if decoy mode is inactive.
     * Returns [onDecoy] immediately when in decoy mode.
     */
    inline fun <T> guardAccess(onDecoy: T, block: () -> T): T {
        return if (isDecoy) onDecoy else block()
    }
}
