package com.vaultbrain.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.integrations.connector.ConnectorRegistry
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Connections management screen.
 *
 * In the foundation build it only observes stored connection rows; live connector
 * availability and sync controls are wired in later iterations.
 */
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    repository: ExternalContextRepository,
    private val registry: ConnectorRegistry
) : ViewModel() {

    val uiState: StateFlow<ConnectionsUiState> = repository.observeConnections()
        .map { ConnectionsUiState(connections = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionsUiState()
        )

    fun isRegistered(connectorId: String): Boolean = registry.get(connectorId) != null
}

data class ConnectionsUiState(
    val connections: List<ConnectionRecord> = emptyList(),
    val isLoading: Boolean = false
)
