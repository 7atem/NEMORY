package com.vaultbrain.core.ai.rag

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.vaultbrain.core.common.security.DecoySessionState
import com.vaultbrain.core.database.dao.VaultReminderDao
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Query start cancels background model work; native inference cooperates with coroutine cancellation. */
object ProactiveReasoning {
    @Volatile var job: Job? = null
    fun preempt() { job?.cancel() }
}

@HiltWorker
class ProactiveInsightWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vault: VaultRepository,
    private val external: ExternalContextRepository,
    private val reminders: VaultReminderDao,
    private val intelligence: DailyIntelligence
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (DecoySessionState.isDecoy.value) return Result.success()
        val current = currentCoroutineContext()[Job]
        ProactiveReasoning.job = current
        return try {
            withTimeout(90_000) {
                intelligence.select(vault.getActive(), external.observeRecords().first(), reminders.observeActive().first().map {
                    com.vaultbrain.core.common.model.VaultReminder(it.id, it.title, it.dueAt, it.status,
                        it.vaultItemId, it.externalConnectorId, it.externalAccountId, it.externalRecordId,
                        it.personalCollectionId, it.createdAt, it.updatedAt)
                })
            }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { if (runAttemptCount < 2) Result.retry() else Result.failure() }
        finally { if (ProactiveReasoning.job === current) ProactiveReasoning.job = null }
    }

    companion object {
        fun enqueue(context: Context, changed: Boolean = false) {
            val work = WorkManager.getInstance(context)
            val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresCharging(true).build()
            work.enqueueUniquePeriodicWork("daily_intelligence_refresh", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ProactiveInsightWorker>(6, TimeUnit.HOURS).setConstraints(constraints).build())
            if (changed) work.enqueueUniqueWork("daily_intelligence_changed", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ProactiveInsightWorker>().setInitialDelay(30, TimeUnit.SECONDS).setConstraints(constraints).build())
        }
    }
}
