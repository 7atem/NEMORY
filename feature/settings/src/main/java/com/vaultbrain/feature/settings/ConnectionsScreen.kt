package com.vaultbrain.feature.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.health.connect.client.PermissionController
import com.vaultbrain.core.integrations.calendar.CalendarConnector
import com.vaultbrain.core.integrations.health.HealthConnectConnector
import com.vaultbrain.feature.settings.viewmodel.ConnectionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    onBack: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val nonCalendarConnections = uiState.connections.filterNot {
        it.connectorId == CalendarConnector.CONNECTOR_ID ||
            it.connectorId == HealthConnectConnector.CONNECTOR_ID
    }
    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::onCalendarPermissionResult
    )
    val healthPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
        viewModel::onHealthPermissionResult
    )
    LaunchedEffect(Unit) { viewModel.refreshCalendar(); viewModel.refreshHealth() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_connections)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_connections)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_calendar_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            !uiState.calendar.isAvailable -> stringResource(R.string.settings_calendar_unavailable)
                            !uiState.calendar.hasPermission -> stringResource(R.string.settings_calendar_permission_body)
                            else -> stringResource(R.string.settings_calendar_choose_body)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (!uiState.calendar.hasPermission && uiState.calendar.isAvailable) {
                        Button(
                            onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.settings_calendar_allow))
                        }
                    } else if (uiState.calendar.hasPermission) {
                        uiState.calendar.calendars.forEach { calendar ->
                            ListItem(
                                headlineContent = { Text(calendar.name) },
                                supportingContent = calendar.accountName?.let { account -> { Text(account) } },
                                leadingContent = {
                                    Checkbox(
                                        checked = calendar.id in uiState.calendar.selectedIds,
                                        onCheckedChange = { viewModel.toggleCalendar(calendar.id) }
                                    )
                                }
                            )
                        }
                        uiState.calendar.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = viewModel::syncCalendar,
                            enabled = uiState.calendar.selectedIds.isNotEmpty() && !uiState.calendar.isSyncing
                        ) {
                            Text(
                                if (uiState.calendar.isSyncing) stringResource(R.string.settings_calendar_syncing)
                                else stringResource(R.string.settings_calendar_sync)
                            )
                        }
                        TextButton(onClick = viewModel::disconnectCalendar) {
                            Text(stringResource(R.string.settings_calendar_disconnect))
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_health_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = when {
                            !uiState.health.isAvailable -> stringResource(R.string.settings_health_unavailable)
                            !uiState.health.hasPermission -> stringResource(R.string.settings_health_permission_body)
                            else -> stringResource(R.string.settings_health_connected_body)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (!uiState.health.hasPermission && uiState.health.isAvailable) {
                        Button(
                            onClick = { healthPermission.launch(viewModel.healthPermissions) },
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.settings_health_allow))
                        }
                    } else if (uiState.health.hasPermission) {
                        uiState.health.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Button(
                            onClick = viewModel::syncHealth,
                            enabled = !uiState.health.isSyncing,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                if (uiState.health.isSyncing) stringResource(R.string.settings_health_syncing)
                                else stringResource(R.string.settings_health_sync)
                            )
                        }
                        TextButton(onClick = viewModel::disconnectHealth) {
                            Text(stringResource(R.string.settings_health_disconnect))
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_connections_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
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
                                supportingContent = {
                                    Text(
                                        text = connection.state.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
