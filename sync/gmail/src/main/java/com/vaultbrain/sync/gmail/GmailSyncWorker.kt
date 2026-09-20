package com.vaultbrain.sync.gmail

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaultbrain.shared.model.external.ConnectionState
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.integrations.model.SyncRequest
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Best-effort local polling; Gmail push would require a server/PubSub and is intentionally absent. */
@HiltWorker
class GmailSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val connector: GmailConnector,
    private val dataSource: GmailDataSource,
    private val repository: ExternalContextRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (DecoySessionState.isDecoy.value || !dataSource.isConfigured()) return Result.success()
        return runCatching {
            repository.deleteExpired()
            dataSource.authorizedAccounts().forEach { accountId ->
                val connection = repository.getConnection(GmailConnector.CONNECTOR_ID, accountId)
                    ?: return@forEach
                if (connection.state == ConnectionState.CONNECTED) {
                    connector.sync(connection, SyncRequest(force = false)).getOrThrow()
                }
            }
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val WORK_NAME = "gmail_bounded_sync"
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<GmailSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
