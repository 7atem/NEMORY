package com.vaultbrain.core.integrations.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability
import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.connector.ExternalConnector
import com.vaultbrain.core.integrations.model.ConnectionRecord
import com.vaultbrain.core.integrations.model.ConnectorAvailability
import com.vaultbrain.core.integrations.model.ContextQuery
import com.vaultbrain.core.integrations.model.ExternalAction
import com.vaultbrain.core.integrations.model.ExternalActionResult
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.model.SyncResult
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ExternalContextRepository
) : ExternalConnector {
    override val connectorId = CONNECTOR_ID
    override val displayName = "Health Connect"
    override val capabilities = setOf(
        ConnectorCapability.READ,
        ConnectorCapability.BACKGROUND_SYNC
    )

    private val client: HealthConnectClient? by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    override suspend fun availability(): ConnectorAvailability {
        val available = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        val granted = available && hasReadPermissions()
        return ConnectorAvailability(
            connectorId = connectorId,
            displayName = displayName,
            isAvailable = available,
            state = when {
                !available -> ConnectionState.UNAVAILABLE
                granted -> ConnectionState.CONNECTED
                else -> ConnectionState.DISCONNECTED
            },
            capabilities = capabilities,
            accountIds = if (granted) listOf(ACCOUNT_ID) else emptyList()
        )
    }

    override suspend fun connect(accountId: String?): Result<ConnectionRecord> {
        val available = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        if (!available) return Result.failure(IllegalStateException("Health Connect not available"))
        if (!hasReadPermissions()) {
            repository.upsertConnection(connection(ConnectionState.PERMISSION_REVOKED, "Health Connect permission required"))
            return Result.failure(SecurityException("Health Connect permission required"))
        }

        val connected = connection(ConnectionState.CONNECTED)
        repository.upsertConnection(connected)
        return Result.success(connected)
    }

    override suspend fun disconnect(connection: ConnectionRecord): Result<Unit> {
        repository.deleteRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
        repository.upsertConnection(connection(ConnectionState.DISCONNECTED))
        return Result.success(Unit)
    }

    override suspend fun sync(connection: ConnectionRecord, request: SyncRequest): Result<SyncResult> {
        if (DecoySessionState.isDecoy.value) return Result.success(emptySync())
        val currentClient = client ?: return Result.failure(IllegalStateException("Client unavailable"))
        if (!hasReadPermissions()) {
            repository.updateConnectionState(CONNECTOR_ID, ACCOUNT_ID, ConnectionState.PERMISSION_REVOKED)
            repository.deleteRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
            return Result.failure(SecurityException("Health Connect permission revoked"))
        }

        return runCatching {
            val now = Instant.now()
            // Bucket by the user's local day so Today/briefing day windows align.
            val zone = java.time.ZoneId.systemDefault()
            val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val endOfDay = java.time.LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
            
            val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)
            
            val stepsRequest = ReadRecordsRequest(StepsRecord::class, timeRange)
            val stepsResponse = currentClient.readRecords(stepsRequest)
            val totalSteps = stepsResponse.records.sumOf { it.count }
            
            val sleepRequest = ReadRecordsRequest(SleepSessionRecord::class, timeRange)
            val sleepResponse = currentClient.readRecords(sleepRequest)
            val totalSleepMinutes = sleepResponse.records.sumOf { 
                ChronoUnit.MINUTES.between(it.startTime, it.endTime)
            }
            
            val caloriesRequest = ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRange)
            val caloriesResponse = currentClient.readRecords(caloriesRequest)
            val totalCalories = caloriesResponse.records.sumOf { it.energy.inKilocalories }

            val externalId = "${startOfDay.toEpochMilli()}"
            val record = ExternalRecord(
                connectorId = CONNECTOR_ID,
                accountId = ACCOUNT_ID,
                externalId = externalId,
                source = ExternalSource.HEALTH_CONNECT,
                recordType = ExternalRecordType.EVENT,
                title = "Daily Health Summary",
                description = "Steps: $totalSteps, Sleep: ${totalSleepMinutes / 60}h ${totalSleepMinutes % 60}m, Active Calories: ${totalCalories.toInt()} kcal",
                startAt = startOfDay.toEpochMilli(),
                endAt = endOfDay.toEpochMilli(),
                payload = mapOf(
                    "steps" to totalSteps.toString(),
                    "sleep_minutes" to totalSleepMinutes.toString(),
                    "active_calories" to totalCalories.toString()
                ),
                deepLinkUri = null,
                hash = listOf(totalSteps, totalSleepMinutes, totalCalories).hashCode().toString(),
                updatedAt = now.toEpochMilli(),
                expiresAt = endOfDay.toEpochMilli() + 7L * 24 * 60 * 60 * 1000 // expire in 7 days
            )
            
            repository.upsertRecord(record)
            repository.updateLastSync(CONNECTOR_ID, ACCOUNT_ID, now.toEpochMilli())
            
            SyncResult(CONNECTOR_ID, ACCOUNT_ID, true, 1, 0, 0)
        }
    }

    override suspend fun query(connection: ConnectionRecord, query: ContextQuery): Result<List<ExternalRecord>> {
        val now = System.currentTimeMillis()
        return runCatching {
            repository.getRecordsForConnection(CONNECTOR_ID, ACCOUNT_ID)
                .filter { record ->
                    val anchor = record.startAt ?: record.createdAt
                    anchor in (now - query.timeWindowMs)..(now + query.timeWindowMs)
                }
        }
    }

    override suspend fun perform(action: ExternalAction): Result<ExternalActionResult> {
        return Result.success(ExternalActionResult(false))
    }

    private suspend fun hasReadPermissions(): Boolean {
        val currentClient = client ?: return false
        return runCatching {
            currentClient.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)
        }.getOrDefault(false)
    }

    private fun connection(state: ConnectionState) = ConnectionRecord(
        connectorId = CONNECTOR_ID,
        accountId = ACCOUNT_ID,
        state = state,
        accountName = "Health Data",
        capabilities = capabilities,
        lastError = null
    )

    private fun connection(state: ConnectionState, error: String?) = ConnectionRecord(
        connectorId = CONNECTOR_ID,
        accountId = ACCOUNT_ID,
        state = state,
        accountName = "Health Data",
        capabilities = capabilities,
        lastError = error
    )

    private fun emptySync() = SyncResult(CONNECTOR_ID, ACCOUNT_ID, success = true)

    companion object {
        const val CONNECTOR_ID = "health_connect"
        const val ACCOUNT_ID = "device"
        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        )
    }
}

/** Periodic local re-read of today's Health Connect aggregates. All reads are on-device. */
@HiltWorker
class HealthConnectSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val connector: HealthConnectConnector,
    private val repository: ExternalContextRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (DecoySessionState.isDecoy.value) return Result.success()
        val connection = repository.getConnection(HealthConnectConnector.CONNECTOR_ID, HealthConnectConnector.ACCOUNT_ID)
            ?: return Result.success()
        if (connection.state != ConnectionState.CONNECTED) return Result.success()
        return connector.sync(connection, SyncRequest()).fold(
            onSuccess = { Result.success() },
            onFailure = { if (it is SecurityException) Result.success() else Result.retry() }
        )
    }

    companion object {
        private const val WORK_NAME = "health_connect_bounded_sync"

        fun enqueuePeriodic(context: Context) {
            val work = PeriodicWorkRequestBuilder<HealthConnectSyncWorker>(6, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}
