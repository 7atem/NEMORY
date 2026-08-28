package com.vaultbrain.feature.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.database.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

enum class BriefingGreeting { MORNING, AFTERNOON, EVENING, NIGHT }

data class BriefingUiState(
    val greeting: BriefingGreeting = BriefingGreeting.MORNING,
    val insights: List<TodayInsight> = emptyList(),
    val weekAhead: List<com.vaultbrain.feature.briefing.model.WeekAheadEvent> = emptyList(),
    val explanations: Map<String, String> = emptyMap()
)

/** Reactive Today briefing. Deterministic insights are published before optional Nano prose. */
@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val insightEngine: DeterministicInsightEngine,
    private val explanationGenerator: BriefingExplanationGenerator
) : ViewModel() {

    private val _uiState = MutableStateFlow(BriefingUiState(greeting = greetingFor()))
    val uiState: StateFlow<BriefingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeActive().collectLatest { items ->
                val now = System.currentTimeMillis()
                val insights = insightEngine.generate(items, now)
                val weekAhead = insightEngine.generateWeekAhead(items, now)
                _uiState.value = BriefingUiState(
                    greeting = greetingFor(now),
                    insights = insights,
                    weekAhead = weekAhead
                )

                // One bounded foreground request at most. All cards remain useful without it.
                val leading = insights.firstOrNull() ?: return@collectLatest
                val explanation = explanationGenerator.explain(leading, Locale.getDefault().language)
                    ?: return@collectLatest
                if (_uiState.value.insights.firstOrNull()?.id == leading.id) {
                    _uiState.value = _uiState.value.copy(explanations = mapOf(leading.id to explanation))
                }
            }
        }
    }

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
