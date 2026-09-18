package com.vaultbrain.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.lifecycle.ProcessLifecycleOwner
import com.vaultbrain.core.ai.heuristics.experience.ExperienceKeywordLibrary
import com.vaultbrain.core.ai.llm.gemma.GemmaDownloadEligibility
import com.vaultbrain.core.ai.llm.gemma.GemmaModelManager
import com.vaultbrain.core.ai.llm.gemma.OnDeviceModelStatus
import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.ai.llm.di.Gemma
import com.vaultbrain.core.notifications.GemmaDownloadPromptManager
import com.vaultbrain.core.integrations.calendar.CalendarConnector
import com.vaultbrain.core.integrations.calendar.CalendarSyncWorker
import com.vaultbrain.core.integrations.connector.ConnectorRegistry
import com.vaultbrain.sync.gmail.GmailConnector
import com.vaultbrain.sync.gmail.GmailSyncWorker
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.feature.capture.LocalAiAdoptionManager
import com.vaultbrain.feature.capture.worker.DeferredAnalysisWorker
import com.vaultbrain.feature.capture.worker.LlmEnrichmentWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

import coil.ImageLoader
import coil.ImageLoaderFactory
import com.vaultbrain.feature.capture.MediaVaultStorage
import com.vaultbrain.feature.capture.coil.VaultMediaFetcher

/**
 * Application entry point for VaultBrain.
 *
 * Hilt generates the component graph from this class. WorkManager is configured
 * for dependency injection via [HiltWorkerFactory].
 */
@HiltAndroidApp
class VaultBrainApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var mediaVaultStorage: MediaVaultStorage

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VaultMediaFetcher.Factory(mediaVaultStorage))
            }
            .build()
    }


    @Inject
    lateinit var persistentCaptureNotificationManager: com.vaultbrain.core.notifications.PersistentCaptureNotificationManager

    @Inject
    lateinit var gemmaDownloadEligibility: GemmaDownloadEligibility

    @Inject
    lateinit var gemmaModelManager: GemmaModelManager

    @Inject
    @Gemma
    lateinit var gemmaLlmClient: LlmClient

    @Inject
    lateinit var localAiAdoptionManager: LocalAiAdoptionManager

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    lateinit var gemmaDownloadPromptManager: GemmaDownloadPromptManager

    @Inject
    lateinit var connectorRegistry: ConnectorRegistry

    @Inject
    lateinit var calendarConnector: CalendarConnector

    @Inject
    lateinit var gmailConnector: GmailConnector

    @Inject
    lateinit var googleTasksConnector: com.vaultbrain.core.integrations.google.GoogleTasksConnector

    @Inject
    lateinit var externalContextRepository: com.vaultbrain.core.integrations.repository.ExternalContextRepository

    @Inject
    lateinit var vaultReminderDao: com.vaultbrain.core.database.dao.VaultReminderDao

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gemmaRuntimeActivated = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stacktrace = android.util.Log.getStackTraceString(throwable)
                // Fix: use internal filesDir to avoid leaking sensitive data
                val dir = filesDir
                if (dir != null) {
                    val file = java.io.File(dir, "vaultbrain_crash.txt")
                    file.writeText(stacktrace)
                }
            } catch (e: Exception) {
                // Ignore
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }

        runCatching {
            androidx.work.WorkManager.initialize(this, workManagerConfiguration)
        }
        runCatching { persistentCaptureNotificationManager.showPersistentNotification() }
        com.vaultbrain.app.widgets.WidgetUpdateWorker.enqueue(this)
        com.vaultbrain.core.notifications.AlertWorker.clearLegacyPreviews(this)
        connectorRegistry.register(calendarConnector)
        connectorRegistry.register(gmailConnector)
        connectorRegistry.register(googleTasksConnector)
        runCatching { androidx.work.WorkManager.getInstance(this).cancelUniqueWork("gmail_bounded_sync") }
        runCatching { com.vaultbrain.sync.gmail.GoogleSyncWorker.enqueue(this) }
        runCatching { androidx.work.WorkManager.getInstance(this).cancelUniqueWork("radar_proactive_worker") }
        runCatching { com.vaultbrain.core.ai.rag.ProactiveInsightWorker.enqueue(this) }
        runCatching { CalendarSyncWorker.enqueuePeriodic(this) }
        // Precompile the ~944 keyword regexes off the main thread so the first
        // capture doesn't pay the compilation cost.
        applicationScope.launch {
            runCatching { ExperienceKeywordLibrary.warmUp() }
        }
        applicationScope.launch {
            combine(vaultRepository.observeActive(), externalContextRepository.observeRecords(),
                vaultReminderDao.observeActive()) { items, records, reminders ->
                com.vaultbrain.core.ai.rag.DailyInsightStore.digest(items.toString() + records.toString() + reminders.toString())
            }.distinctUntilChanged().collect {
                if (!com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value)
                    com.vaultbrain.core.ai.rag.ProactiveInsightWorker.enqueue(this@VaultBrainApplication, changed = true)
            }
        }
        // One-time backfill: embed items saved before embedding generation existed.
        runCatching { com.vaultbrain.feature.capture.worker.EmbeddingBackfillWorker.enqueueOnce(this) }
        runCatching { com.vaultbrain.feature.capture.worker.VaultInsightsWorker.enqueue(this) }
        // Offer the Gemma Tier 2 model once when this device has no Gemini Nano.
        applicationScope.launch {
            runCatching { maybePromptGemmaDownload() }
        }
        // A verified file is not enough: load the MediaPipe runtime first, then adopt Gemma as
        // the primary local model and visibly improve both historical and future items.
        applicationScope.launch {
            gemmaModelManager.status.collect { status ->
                if (status == OnDeviceModelStatus.READY) {
                    val activated = runCatching { gemmaLlmClient.warmup() }.getOrDefault(false)
                    gemmaRuntimeActivated.value = activated
                    if (activated) {
                        val historicalItemsQueued = localAiAdoptionManager.prepareExistingItemsIfNeeded()
                        DeferredAnalysisWorker.enqueue(this@VaultBrainApplication)
                        LlmEnrichmentWorker.enqueue(
                            context = this@VaultBrainApplication,
                            replaceCurrent = historicalItemsQueued > 0
                        )
                    }
                } else {
                    gemmaRuntimeActivated.value = false
                }
            }
        }
        // Covers every future insert path, not only CaptureViewModel: whenever a ready item
        // appears anywhere in the app, ensure a local-AI worker is attached to the queue.
        applicationScope.launch {
            combine(
                gemmaRuntimeActivated,
                vaultRepository.observeEnrichmentCandidateCount()
            ) { activated, count -> activated && count > 0 }
                .distinctUntilChanged()
                .collect { shouldRun ->
                    if (shouldRun) LlmEnrichmentWorker.enqueue(this@VaultBrainApplication)
                }
        }
    }

    /**
     * Shows the one-time on-device-model download prompt only when Gemini Nano/AICore is not
     * ready, the device supports Gemma (>= 4 GB RAM), the model is not downloaded, and the
     * user has not declined twice (handled inside the prompt manager's store).
     */
    private suspend fun maybePromptGemmaDownload() {
        if (!com.vaultbrain.core.common.OnboardingState.hasSeenOnboarding(this)) return
        if (!gemmaDownloadEligibility.isPromptEligible()) return
        gemmaDownloadPromptManager.showPromptIfEligible()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
