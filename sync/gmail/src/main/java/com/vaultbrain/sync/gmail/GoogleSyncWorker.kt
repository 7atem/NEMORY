package com.vaultbrain.sync.gmail

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.google.GoogleConsentRequired
import com.vaultbrain.core.integrations.google.GoogleTasksConnector
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class GoogleSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gmail: GmailConnector,
    private val tasks: GoogleTasksConnector,
    private val repository: ExternalContextRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (DecoySessionState.isDecoy.value) return Result.success()
        var retry = false
        val connections = repository.observeConnections().first().filter {
            it.connectorId in setOf("gmail", "google_tasks") && it.state == ConnectionState.CONNECTED
        }
        for (connection in connections) {
            if (DecoySessionState.isDecoy.value) return Result.success()
            try {
                val connector = if (connection.connectorId == "gmail") gmail else tasks
                connector.sync(connection, SyncRequest(force = false)).getOrThrow()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: GoogleConsentRequired) {
                repository.updateConnectionState(connection.connectorId, connection.accountId, ConnectionState.PERMISSION_REVOKED)
                repository.deleteRecordsForConnection(connection.connectorId, connection.accountId)
            } catch (_: Exception) { retry = true }
        }
        return if (retry && runAttemptCount < 4) Result.retry() else Result.success()
    }

    companion object {
        fun enqueue(context: Context, immediate: Boolean = false) {
            val manager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            manager.enqueueUniquePeriodicWork("google_device_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<GoogleSyncWorker>(1, TimeUnit.HOURS)
                    .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
            if (immediate) manager.enqueueUniqueWork("google_device_sync_now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GoogleSyncWorker>().setConstraints(constraints).build())
        }
    }
}
