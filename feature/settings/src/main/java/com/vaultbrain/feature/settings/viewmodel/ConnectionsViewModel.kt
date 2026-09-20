package com.vaultbrain.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.core.integrations.calendar.CalendarConnector
import com.vaultbrain.core.integrations.calendar.CalendarInfo

import com.vaultbrain.core.integrations.connector.ConnectorRegistry
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
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val googleAuthorization: com.vaultbrain.core.integrations.google.GoogleDeviceAuthorization,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val calendarState = MutableStateFlow(CalendarConnectionUiState())
    private val googleState = MutableStateFlow(GoogleConnectionUiState())
    private val authorizationIntents = kotlinx.coroutines.channels.Channel<android.app.PendingIntent>(1)
    val googleAuthorizationIntents = authorizationIntents.receiveAsFlow()
    private var pendingGoogleService: com.vaultbrain.core.integrations.google.GoogleService? = null
    private var pendingGoogleAccount: String? = null
    private var googleJob: kotlinx.coroutines.Job? = null

    val uiState: StateFlow<ConnectionsUiState> = combine(
        repository.observeConnections(),
        calendarState,
        googleState
    ) { connections, calendar, google -> ConnectionsUiState(connections, calendar, google = google) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionsUiState()
        )

    init {
        registry.register(calendarConnector)

        refreshCalendar()
        viewModelScope.launch {
            com.vaultbrain.core.common.security.DecoySessionState.isDecoy.collect { decoy ->
                if (decoy) {
                    googleJob?.cancel()
                    pendingGoogleService = null
                    pendingGoogleAccount = null
                    googleState.value = GoogleConnectionUiState()
                }
            }
        }
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

    fun isRegistered(connectorId: String): Boolean = registry.get(connectorId) != null

    fun connectGmail(email: String) {
        viewModelScope.launch {
            registry.get("gmail")?.connect(email)
        }
    }

    fun connectGoogle(service: com.vaultbrain.core.integrations.google.GoogleService, account: String? = null) {
        if (googleState.value.busy || com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value) return
        googleState.value = GoogleConnectionUiState(busy = true)
        pendingGoogleService = service
        pendingGoogleAccount = account
        googleJob = viewModelScope.launch {
            try {
                when (val step = googleAuthorization.begin(service, account)) {
                    is com.vaultbrain.core.integrations.google.GoogleAuthorizationStep.Consent -> authorizationIntents.send(step.intent)
                    is com.vaultbrain.core.integrations.google.GoogleAuthorizationStep.Connected -> googleConnected()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { googleFailed(error) }
        }
    }

    fun finishGoogleAuthorization(data: android.content.Intent?, success: Boolean) {
        val service = pendingGoogleService ?: return
        if (!success) { googleFailed(com.vaultbrain.core.integrations.google.GoogleConsentRequired()); return }
        googleJob = viewModelScope.launch {
            try {
                googleAuthorization.finish(service, data, pendingGoogleAccount)
                googleConnected()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { googleFailed(error) }
        }
    }

    fun syncGoogle() = com.vaultbrain.sync.gmail.GoogleSyncWorker.enqueue(context, immediate = true)

    fun disconnectGoogle(account: String) {
        if (googleState.value.busy) return
        googleState.value = GoogleConnectionUiState(busy = true)
        googleJob = viewModelScope.launch {
            try { googleAuthorization.disconnect(account); googleState.value = GoogleConnectionUiState() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { googleFailed(error) }
        }
    }

    private fun googleConnected() {
        pendingGoogleService = null
        pendingGoogleAccount = null
        googleState.value = GoogleConnectionUiState()
        syncGoogle()
    }

    private fun googleFailed(error: Exception) {
        pendingGoogleService = null
        pendingGoogleAccount = null
        val kind = when {
            error is com.vaultbrain.core.integrations.google.GoogleConsentRequired -> GoogleConnectionError.CONSENT
            error is com.google.android.gms.common.api.ApiException && error.statusCode == 10 -> GoogleConnectionError.SETUP
            else -> GoogleConnectionError.CONNECTION
        }
        googleState.value = GoogleConnectionUiState(error = kind)
    }
}

data class ConnectionsUiState(
    val connections: List<ConnectionRecord> = emptyList(),
    val calendar: CalendarConnectionUiState = CalendarConnectionUiState(),
    val isLoading: Boolean = false,
    val google: GoogleConnectionUiState = GoogleConnectionUiState()
)

enum class GoogleConnectionError { SETUP, CONSENT, CONNECTION }
data class GoogleConnectionUiState(val busy: Boolean = false, val error: GoogleConnectionError? = null)

data class CalendarConnectionUiState(
    val isAvailable: Boolean = true,
    val hasPermission: Boolean = false,
    val calendars: List<CalendarInfo> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isSyncing: Boolean = false,
    val lastSyncAt: Long? = null,
    val error: String? = null
)
