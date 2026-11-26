package com.vaultbrain.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.common.ui.InfoPill
import com.vaultbrain.core.common.ui.ResponsiveContainer
import com.vaultbrain.core.common.ui.SectionHeading
import com.vaultbrain.core.common.ui.VaultSpacing
import com.vaultbrain.core.integrations.calendar.CalendarConnector
import com.vaultbrain.feature.settings.viewmodel.ConnectionsViewModel
import com.vaultbrain.shared.model.external.ConnectionState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDisconnectCalendarDialog by remember { mutableStateOf(false) }
    val nonCalendarConnections = uiState.connections.filterNot {
        it.connectorId == CalendarConnector.CONNECTOR_ID
    }
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onCalendarPermissionResult
    )
    val googleConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.finishGoogleAuthorization(result.data, result.resultCode == android.app.Activity.RESULT_OK)
    }
    LaunchedEffect(Unit) {
        viewModel.googleAuthorizationIntents.collect { pending ->
            googleConsent.launch(androidx.activity.result.IntentSenderRequest.Builder(pending).build())
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshCalendar() }

    if (showDisconnectCalendarDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectCalendarDialog = false },
            title = { Text(stringResource(R.string.settings_calendar_disconnect_title)) },
            text = { Text(stringResource(R.string.settings_calendar_disconnect_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectCalendarDialog = false
                        viewModel.disconnectCalendar()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.settings_calendar_disconnect)) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectCalendarDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_connections)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        ResponsiveContainer(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(VaultSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.lg)
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(VaultSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                    ) {
                        SectionHeading(text = stringResource(R.string.settings_calendar_title))
                        Text(
                            text = when {
                                !uiState.calendar.isAvailable -> stringResource(R.string.settings_calendar_unavailable)
                                !uiState.calendar.hasPermission -> stringResource(R.string.settings_calendar_permission_body)
                                else -> stringResource(R.string.settings_calendar_choose_body)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!uiState.calendar.hasPermission && uiState.calendar.isAvailable) {
                            Button(
                                onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                                modifier = Modifier.padding(top = VaultSpacing.xs)
                            ) {
                                Text(stringResource(R.string.settings_calendar_allow))
                            }
                        } else if (uiState.calendar.hasPermission) {
                            uiState.calendar.calendars.forEach { calendar ->
                                val selected = calendar.id in uiState.calendar.selectedIds
                                ListItem(
                                    modifier = Modifier.toggleable(
                                        value = selected,
                                        role = Role.Checkbox,
                                        onValueChange = { viewModel.toggleCalendar(calendar.id) }
                                    ),
                                    headlineContent = { Text(calendar.name) },
                                    supportingContent = calendar.accountName?.let { account -> { Text(account) } },
                                    leadingContent = {
                                        Checkbox(
                                            checked = selected,
                                            onCheckedChange = null
                                        )
                                    }
                                )
                            }
                            uiState.calendar.error?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
                            ) {
                                Button(
                                    onClick = viewModel::syncCalendar,
                                    enabled = uiState.calendar.selectedIds.isNotEmpty() && !uiState.calendar.isSyncing
                                ) {
                                    Text(
                                        if (uiState.calendar.isSyncing) stringResource(R.string.settings_calendar_syncing)
                                        else stringResource(R.string.settings_calendar_sync)
                                    )
                                }
                                TextButton(
                                    onClick = { showDisconnectCalendarDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.settings_calendar_disconnect))
                                }
                            }
                        }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(VaultSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_connections_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = VaultSpacing.xs))
                        if (nonCalendarConnections.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_connections_empty),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            nonCalendarConnections.forEach { connection ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            connection.accountName
                                                ?: connection.connectorId
                                        )
                                    },
                                    trailingContent = {
                                        val isErrorState = connection.state in setOf(
                                            ConnectionState.ERROR,
                                            ConnectionState.PERMISSION_REVOKED
                                        )
                                        InfoPill(
                                            text = connectionStateLabel(connection.state),
                                            containerColor = if (isErrorState) MaterialTheme.colorScheme.errorContainer
                                            else MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = if (isErrorState) MaterialTheme.colorScheme.onErrorContainer
                                            else MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.google_device_sync_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        uiState.google.error?.let { error ->
                            Text(stringResource(when (error) {
                                com.vaultbrain.feature.settings.viewmodel.GoogleConnectionError.SETUP -> R.string.google_setup_required
                                com.vaultbrain.feature.settings.viewmodel.GoogleConnectionError.CONSENT -> R.string.google_consent_required
                                com.vaultbrain.feature.settings.viewmodel.GoogleConnectionError.CONNECTION -> R.string.google_connection_failed
                            }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
                        ) {
                            com.vaultbrain.core.integrations.google.GoogleService.entries.forEach { service ->
                                Button(
                                    onClick = { viewModel.connectGoogle(service) },
                                    enabled = !uiState.google.busy
                                ) {
                                    Text(stringResource(if (service == com.vaultbrain.core.integrations.google.GoogleService.GMAIL)
                                        R.string.google_link_gmail else R.string.google_link_tasks))
                                }
                            }
                        }
                        val googleConnections = nonCalendarConnections.filter { it.connectorId in setOf("gmail", "google_tasks") }
                        googleConnections.filter { it.state != ConnectionState.CONNECTED }.forEach { connection ->
                            TextButton(onClick = {
                                viewModel.connectGoogle(com.vaultbrain.core.integrations.google.GoogleService.entries.first {
                                    it.connectorId == connection.connectorId }, connection.accountId)
                            }, enabled = !uiState.google.busy) { Text(stringResource(R.string.google_reconnect, connection.accountName ?: connection.accountId)) }
                        }
                        if (googleConnections.isNotEmpty()) {
                            TextButton(onClick = viewModel::syncGoogle, enabled = !uiState.google.busy) { Text(stringResource(R.string.google_sync_now)) }
                            googleConnections.distinctBy { it.accountId }.forEach { connection ->
                                TextButton(
                                    onClick = { viewModel.disconnectGoogle(connection.accountId) },
                                    enabled = !uiState.google.busy,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(stringResource(R.string.google_disconnect_account, connection.accountName ?: connection.accountId))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun connectionStateLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.CONNECTED -> R.string.settings_connection_state_connected
        ConnectionState.CONNECTING -> R.string.settings_connection_state_syncing
        ConnectionState.PERMISSION_REVOKED,
        ConnectionState.LIMITED -> R.string.settings_connection_state_needs_reauth
        ConnectionState.ERROR -> R.string.settings_connection_state_error
        ConnectionState.DISCONNECTED,
        ConnectionState.UNAVAILABLE -> R.string.settings_connection_state_disconnected
    }
)
