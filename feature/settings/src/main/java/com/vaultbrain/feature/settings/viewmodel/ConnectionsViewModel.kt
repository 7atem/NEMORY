package com.vaultbrain.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.integrations.calendar.CalendarConnector
import com.vaultbrain.core.integrations.calendar.CalendarInfo

import com.vaultbrain.core.integrations.connector.ConnectorRegistry
import com.vaultbrain.core.integrations.health.HealthConnectConnector
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Connections management screen.
 *
 * Observes stored connections and manages the read-only device Calendar connector.
 */
@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val repository: ExternalContextRepository,
    private val registry: ConnectorRegistry,
    private val calendarConnector: CalendarConnector,
    private val healthConnectConnector: HealthConnectConnector
) : ViewModel() {

    private val calendarState = MutableStateFlow(CalendarConnectionUiState())
    private val healthState = MutableStateFlow(HealthConnectionUiState())

    /** Permissions to pass to the Health Connect permission contract. */
    val healthPermissions: Set<String> = HealthConnectConnector.REQUIRED_PERMISSIONS

    val uiState: StateFlow<ConnectionsUiState> = combine(
        repository.observeConnections(),
        calendarState,
        healthState
    ) { connections, calendar, health -> ConnectionsUiState(connections, calendar, health) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionsUiState()
        )

    init {
        registry.register(calendarConnector)
        registry.register(healthConnectConnector)

        refreshCalendar()
        refreshHealth()
    }

    fun refreshCalendar() {
        viewModelScope.launch {
            val availability = calendarConnector.availability()
            val granted = availability.state == ConnectionState.CONNECTED
            calendarState.value = CalendarConnectionUiState(
                isAvailable = availability.isAvailable,
                hasPermission = granted,
                calendars = if (granted) calendarConnector.calendars() else emptyList(),
                selectedIds = calendarConnector.selectedCalendarIds()
            )
        }
    }

    fun onCalendarPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            if (granted) calendarConnector.connect().getOrNull()
            refreshCalendar()
        }
    }

    fun toggleCalendar(calendarId: Long) {
        val selected = calendarState.value.selectedIds.toMutableSet().apply {
            if (!add(calendarId)) remove(calendarId)
        }
        calendarConnector.setSelectedCalendarIds(selected)
        calendarState.update { it.copy(selectedIds = selected) }
    }

    fun syncCalendar() {
        viewModelScope.launch {
            calendarState.update { it.copy(isSyncing = true, error = null) }
            val connection = repository.getConnection(
                CalendarConnector.CONNECTOR_ID,
                CalendarConnector.ACCOUNT_ID
            ) ?: calendarConnector.connect().getOrNull()
            val result = connection?.let { calendarConnector.sync(it, SyncRequest(force = true)) }
            calendarState.update {
                it.copy(
                    isSyncing = false,
                    error = result?.exceptionOrNull()?.message,
                    lastSyncAt = if (result?.isSuccess == true) System.currentTimeMillis() else it.lastSyncAt
                )
            }
        }
    }

    fun disconnectCalendar() {
        viewModelScope.launch {
            repository.getConnection(CalendarConnector.CONNECTOR_ID, CalendarConnector.ACCOUNT_ID)
                ?.let { calendarConnector.disconnect(it) }
            refreshCalendar()
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            val availability = healthConnectConnector.availability()
            healthState.value = HealthConnectionUiState(
                isAvailable = availability.isAvailable,
                hasPermission = availability.state == ConnectionState.CONNECTED
            )
        }
    }

    fun onHealthPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            if (granted.containsAll(HealthConnectConnector.REQUIRED_PERMISSIONS)) {
                healthConnectConnector.connect().getOrNull()
                syncHealth()
            }
            refreshHealth()
        }
    }

    fun syncHealth() {
        viewModelScope.launch {
            healthState.update { it.copy(isSyncing = true, error = null) }
            val connection = repository.getConnection(
                HealthConnectConnector.CONNECTOR_ID,
                HealthConnectConnector.ACCOUNT_ID
            ) ?: healthConnectConnector.connect().getOrNull()
            val result = connection?.let { healthConnectConnector.sync(it, SyncRequest(force = true)) }
            healthState.update {
                it.copy(
                    isSyncing = false,
                    error = result?.exceptionOrNull()?.message,
                    lastSyncAt = if (result?.isSuccess == true) System.currentTimeMillis() else it.lastSyncAt
                )
            }
        }
    }

    fun disconnectHealth() {
        viewModelScope.launch {
            repository.getConnection(HealthConnectConnector.CONNECTOR_ID, HealthConnectConnector.ACCOUNT_ID)
                ?.let { healthConnectConnector.disconnect(it) }
            refreshHealth()
        }
    }

    fun isRegistered(connectorId: String): Boolean = registry.get(connectorId) != null
}

data class ConnectionsUiState(
    val connections: List<ConnectionRecord> = emptyList(),
    val calendar: CalendarConnectionUiState = CalendarConnectionUiState(),
    val health: HealthConnectionUiState = HealthConnectionUiState(),
    val isLoading: Boolean = false
)

data class HealthConnectionUiState(
    val isAvailable: Boolean = true,
    val hasPermission: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val error: String? = null
)

data class CalendarConnectionUiState(
    val isAvailable: Boolean = true,
    val hasPermission: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val error: String? = null
)
