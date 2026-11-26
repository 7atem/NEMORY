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
    lateinit var mediaVaultStorage: dagger.Lazy<MediaVaultStorage>

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VaultMediaFetcher.Factory(mediaVaultStorage.get()))
            }
            .build()
    }

    @Inject
    lateinit var persistentCaptureNotificationManager: dagger.Lazy<com.vaultbrain.core.notifications.PersistentCaptureNotificationManager>

    @Inject
    lateinit var gemmaDownloadEligibility: dagger.Lazy<GemmaDownloadEligibility>

    @Inject
    lateinit var gemmaModelManager: dagger.Lazy<GemmaModelManager>

    @Inject
    @Gemma
    lateinit var gemmaLlmClient: dagger.Lazy<LlmClient>

    @Inject
    lateinit var localAiAdoptionManager: dagger.Lazy<LocalAiAdoptionManager>

    @Inject
    lateinit var vaultRepository: dagger.Lazy<VaultRepository>

    @Inject
    lateinit var gemmaDownloadPromptManager: dagger.Lazy<GemmaDownloadPromptManager>

    @Inject
    lateinit var connectorRegistry: dagger.Lazy<ConnectorRegistry>

    @Inject
    lateinit var calendarConnector: dagger.Lazy<CalendarConnector>

    @Inject
    lateinit var gmailConnector: dagger.Lazy<GmailConnector>

    @Inject
    lateinit var googleTasksConnector: dagger.Lazy<com.vaultbrain.core.integrations.google.GoogleTasksConnector>

    @Inject
    lateinit var externalContextRepository: dagger.Lazy<com.vaultbrain.core.integrations.repository.ExternalContextRepository>

    @Inject
    lateinit var vaultReminderDao: dagger.Lazy<com.vaultbrain.shared.database.dao.VaultReminderDao>

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

        // Defer WorkManager, connector registration, and DB/AI singleton initialization off the
        // main thread so cold start renders the first frame immediately without risking ANRs.
        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                androidx.work.WorkManager.initialize(this@VaultBrainApplication, workManagerConfiguration)
            }
            runCatching { persistentCaptureNotificationManager.get().showPersistentNotification() }
            runCatching { com.vaultbrain.app.widgets.WidgetUpdateWorker.enqueue(this@VaultBrainApplication) }
            runCatching { com.vaultbrain.core.notifications.AlertWorker.clearLegacyPreviews(this@VaultBrainApplication) }
            runCatching {
                val registry = connectorRegistry.get()
                registry.register(calendarConnector.get())
                registry.register(gmailConnector.get())
                registry.register(googleTasksConnector.get())
            }
            runCatching { androidx.work.WorkManager.getInstance(this@VaultBrainApplication).cancelUniqueWork("gmail_bounded_sync") }
            runCatching { com.vaultbrain.sync.gmail.GoogleSyncWorker.enqueue(this@VaultBrainApplication) }
            runCatching { androidx.work.WorkManager.getInstance(this@VaultBrainApplication).cancelUniqueWork("radar_proactive_worker") }
            runCatching { com.vaultbrain.core.ai.rag.ProactiveInsightWorker.enqueue(this@VaultBrainApplication) }
            runCatching { CalendarSyncWorker.enqueuePeriodic(this@VaultBrainApplication) }
            // One-time backfill: embed items saved before embedding generation existed.
            runCatching { com.vaultbrain.feature.capture.worker.EmbeddingBackfillWorker.enqueueOnce(this@VaultBrainApplication) }
            runCatching { com.vaultbrain.feature.capture.worker.VaultInsightsWorker.enqueue(this@VaultBrainApplication) }
        }

        // Precompile the ~944 keyword regexes off the main thread so the first
        // capture doesn't pay the compilation cost.
        applicationScope.launch {
            runCatching { ExperienceKeywordLibrary.warmUp() }
        }
        applicationScope.launch(Dispatchers.IO) {
            combine(
                vaultRepository.get().observeActive(),
                externalContextRepository.get().observeRecords(),
                vaultReminderDao.get().observeActive()
            ) { items, records, reminders ->
                com.vaultbrain.core.ai.rag.DailyInsightStore.digest(items.toString() + records.toString() + reminders.toString())
            }.distinctUntilChanged().collect {
                if (!com.vaultbrain.core.common.security.DecoySessionState.isDecoy.value)
                    com.vaultbrain.core.ai.rag.ProactiveInsightWorker.enqueue(this@VaultBrainApplication, changed = true)
            }
        }
        // Offer the Gemma Tier 2 model once when this device has no Gemini Nano.
        applicationScope.launch(Dispatchers.IO) {
            runCatching { maybePromptGemmaDownload() }
        }
        // A verified file is not enough: load the MediaPipe runtime first, then adopt Gemma as
        // the primary local model and visibly improve both historical and future items.
        applicationScope.launch(Dispatchers.IO) {
            gemmaModelManager.get().status.collect { status ->
                if (status == OnDeviceModelStatus.READY) {
                    val activated = runCatching { gemmaLlmClient.get().warmup() }.getOrDefault(false)
                    gemmaRuntimeActivated.value = activated
                    if (activated) {
                        val historicalItemsQueued = localAiAdoptionManager.get().prepareExistingItemsIfNeeded()
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
        applicationScope.launch(Dispatchers.IO) {
            combine(
                gemmaRuntimeActivated,
                vaultRepository.get().observeEnrichmentCandidateCount()
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
        if (!gemmaDownloadEligibility.get().isPromptEligible()) return
        gemmaDownloadPromptManager.get().showPromptIfEligible()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
