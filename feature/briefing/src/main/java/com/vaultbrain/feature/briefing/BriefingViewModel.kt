package com.vaultbrain.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.database.repository.VaultRepository
import com.vaultbrain.core.integrations.model.ExternalRecord
import com.vaultbrain.core.integrations.repository.ExternalContextRepository
import com.vaultbrain.core.notifications.VaultReminderManager
import com.vaultbrain.core.common.model.VaultReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class BriefingGreeting { MORNING, AFTERNOON, EVENING, NIGHT }

data class BriefingUiState(
    val greeting: BriefingGreeting = BriefingGreeting.MORNING,
    val insights: List<TodayInsight> = emptyList(),
    val weekAhead: List<com.vaultbrain.feature.briefing.model.WeekAheadEvent> = emptyList(),
    val explanations: Map<String, String> = emptyMap(),
    val dailySummary: String? = null,
    val isSummaryLoading: Boolean = false
)

/** Reactive Today briefing. Deterministic insights are published before optional Nano prose. */
@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val externalContextRepository: ExternalContextRepository,
    private val vaultReminderManager: VaultReminderManager,
    private val deterministicInsightEngine: DeterministicInsightEngine,
    private val digestEngine: DigestEngine,
    private val explanationGenerator: BriefingExplanationGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(BriefingUiState(greeting = greetingFor()))
    val uiState: StateFlow<BriefingUiState> = _uiState.asStateFlow()

    // Set after the first summary generation attempt so a null LLM result
    // doesn't re-show the spinner on every subsequent DB emission.
    private var summaryAttempted = false

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.observeActive(),
                externalContextRepository.observeRecords(),
                vaultReminderManager.observeActive()
            ) { items, externalRecords, reminders ->
                // Deterministic computation only — LLM calls run downstream in collectLatest,
                // so new emissions cancel in-flight generation instead of queuing behind it.
                val now = System.currentTimeMillis()
                val digest = digestEngine.generateDigest(items, externalRecords, now)
                val insights = digest.items
                val weekAhead = deterministicInsightEngine.generateWeekAhead(items, now)
                val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
                val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val todayRecords = externalRecords.filter { record ->
                    val anchor = record.startAt ?: record.createdAt
                    anchor >= startOfDay && anchor < endOfDay
                }
                val todayReminders = reminders.filter { reminder ->
                    reminder.dueAt >= startOfDay && reminder.dueAt < endOfDay
                }
                BriefingInputs(insights, weekAhead, todayRecords, todayReminders)
            }
                .distinctUntilChanged()
                .collectLatest { inputs ->
                    _uiState.value = BriefingUiState(
                        greeting = greetingFor(),
                        insights = inputs.insights,
                        weekAhead = inputs.weekAhead,
                        dailySummary = _uiState.value.dailySummary,
                        isSummaryLoading = !summaryAttempted
                    )

                    val language = Locale.getDefault().language
                    val leading = inputs.insights.firstOrNull()
                    val explanation = leading?.let { kotlinx.coroutines.withTimeoutOrNull(8_000) { explanationGenerator.explain(it, language) } }
                    val dailySummary = kotlinx.coroutines.withTimeoutOrNull(8_000) { explanationGenerator.generateDailySummary(
                        insights = inputs.insights,
                        externalRecords = inputs.todayRecords,
                        reminders = inputs.todayReminders,
                        languageCode = language
                    ) }
                    summaryAttempted = true

                    _uiState.value = _uiState.value.copy(
                        explanations = if (leading != null && explanation != null) mapOf(leading.id to explanation) else emptyMap(),
                        dailySummary = dailySummary,
                        isSummaryLoading = false
                    )
                }
        }
    }

    private data class BriefingInputs(
        val insights: List<TodayInsight>,
        val weekAhead: List<com.vaultbrain.feature.briefing.model.WeekAheadEvent>,
        val todayRecords: List<ExternalRecord>,
        val todayReminders: List<VaultReminder>
    )

    internal fun greetingFor(
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): BriefingGreeting = when (Instant.ofEpochMilli(now).atZone(zoneId).hour) {
        in 5..11 -> BriefingGreeting.MORNING
        in 12..16 -> BriefingGreeting.AFTERNOON
        in 17..20 -> BriefingGreeting.EVENING
        else -> BriefingGreeting.NIGHT
    }
}
