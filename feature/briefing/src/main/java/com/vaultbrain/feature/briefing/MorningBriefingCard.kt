package com.vaultbrain.feature.briefing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Proactive, evidence-based Today surface. Taps only open a record for review. */
@Composable
fun MorningBriefingCard(
    modifier: Modifier = Modifier,
    onInsightClick: (String) -> Unit = {},
    onInsightAction: (InsightAction) -> Unit = {},
    viewModel: BriefingViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    MorningBriefingContent(
        greeting = state.greeting,
        insights = state.insights,
        weekAhead = state.weekAhead,
        explanations = state.explanations,
        dailySummary = state.dailySummary,
        isSummaryLoading = state.isSummaryLoading,
        onInsightClick = onInsightClick,
        onInsightAction = onInsightAction,
        modifier = modifier
    )
}

@Composable
internal fun MorningBriefingContent(
    greeting: BriefingGreeting,
    insights: List<TodayInsight>,
    weekAhead: List<com.vaultbrain.feature.briefing.model.WeekAheadEvent> = emptyList(),
    explanations: Map<String, String> = emptyMap(),
    dailySummary: String? = null,
    isSummaryLoading: Boolean = false,
    onInsightClick: (String) -> Unit = {},
    onInsightAction: (InsightAction) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greetingText(greeting),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.today_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    )
                }
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            if (isSummaryLoading || dailySummary != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSummaryLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = stringResource(R.string.today_summary_loading),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (dailySummary != null) {
                            Text(
                                text = dailySummary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (insights.isEmpty()) {
                Text(
                    text = stringResource(R.string.today_clear),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = stringResource(R.string.today_priority_radar),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    insights.forEach { insight ->
                        TodayInsightRow(
                            insight = insight,
                            explanation = explanations[insight.id],
                            onClick = { onInsightClick(insight.primaryItemId) },
                            onActionClick = onInsightAction
                        )
                    }
                }
            }
        }
    }
    
    if (weekAhead.isNotEmpty()) {
        com.vaultbrain.feature.briefing.ui.WeekAheadTimeline(
            events = weekAhead,
            onEventClick = onInsightClick, // Maps to item view
            modifier = Modifier.fillMaxWidth()
        )
    }
}
}

@Composable
private fun TodayInsightRow(
    insight: TodayInsight,
    explanation: String?,
    onClick: () -> Unit,
    onActionClick: (InsightAction) -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = insightIcon(insight),
                    contentDescription = null,
                    tint = insightColor(insight),
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = insightTitle(insight),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = insightDetail(insight),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.today_review),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            val deterministicNote = when (insight) {
                is TodayInsight.Expiring -> stringResource(R.string.today_note_expiring)
                is TodayInsight.PossibleDuplicates -> stringResource(R.string.today_note_duplicates)
                is TodayInsight.SpendingIncrease -> stringResource(R.string.today_note_spending)
                is TodayInsight.UpcomingTravel -> stringResource(R.string.today_note_travel)
                is TodayInsight.MediaBacklog -> stringResource(R.string.today_note_media)
                is TodayInsight.ActionableExternalInsight -> "Automated radar insight based on a linked account or message."
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(9.dp)) {
                    Text(
                        text = stringResource(R.string.today_why_seeing_this),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = explanation ?: deterministicNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            if (insight.actions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    insight.actions.forEachIndexed { index, action ->
                        if (index == 0) {
                            androidx.compose.material3.Button(
                                onClick = { onActionClick(action) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(action.labelRes))
                            }
                        } else {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onActionClick(action) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(action.labelRes))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun greetingText(greeting: BriefingGreeting): String = stringResource(
    when (greeting) {
        BriefingGreeting.MORNING -> R.string.today_good_morning
        BriefingGreeting.AFTERNOON -> R.string.today_good_afternoon
        BriefingGreeting.EVENING -> R.string.today_good_evening
        BriefingGreeting.NIGHT -> R.string.today_good_night
    }
)

@Composable
private fun insightTitle(insight: TodayInsight): String = when (insight) {
    is TodayInsight.Expiring -> if (insight.daysRemaining == 0L) {
        stringResource(R.string.today_expires_today, insight.itemTitle)
    } else {
        pluralStringResource(
            R.plurals.today_expires_in_days,
            insight.daysRemaining.toInt(),
            insight.itemTitle,
            insight.daysRemaining
        )
    }
    is TodayInsight.PossibleDuplicates -> pluralStringResource(
        R.plurals.today_possible_duplicates,
        insight.captureCount,
        insight.captureCount
    )
    is TodayInsight.SpendingIncrease -> stringResource(
        R.string.today_charge_increased,
        insight.provider
    )
    is TodayInsight.UpcomingTravel -> if (insight.daysRemaining == 0L) {
        stringResource(R.string.today_travel_today, insight.itemTitle)
    } else {
        pluralStringResource(
            R.plurals.today_travel_in_days,
            insight.daysRemaining.toInt(),
            insight.itemTitle,
            insight.daysRemaining
        )
    }
    is TodayInsight.MediaBacklog -> pluralStringResource(
        if (insight.isReading) R.plurals.today_reading_backlog else R.plurals.today_watch_backlog,
        insight.itemCount,
        insight.itemCount
    )
    is TodayInsight.ActionableExternalInsight -> insight.insightTitle
}

@Composable
private fun insightDetail(insight: TodayInsight): String = when (insight) {
    is TodayInsight.Expiring -> stringResource(R.string.today_date_label, formatDate(insight.expiryAt))
    is TodayInsight.PossibleDuplicates -> stringResource(R.string.today_duplicates_detail)
    is TodayInsight.SpendingIncrease -> stringResource(
        R.string.today_charge_change_detail,
        insight.currency,
        insight.previousAmount.display(),
        insight.currency,
        insight.currentAmount.display(),
        insight.currency,
        insight.delta.display()
    )
    is TodayInsight.UpcomingTravel -> stringResource(R.string.today_date_label, formatDate(insight.eventAt))
    is TodayInsight.MediaBacklog -> stringResource(R.string.today_media_sample, insight.sampleTitle)
    is TodayInsight.ActionableExternalInsight -> insight.insightBody
}

private fun insightIcon(insight: TodayInsight): ImageVector = when (insight) {
    is TodayInsight.Expiring -> Icons.Default.Warning
    is TodayInsight.PossibleDuplicates -> Icons.Default.ContentCopy
    is TodayInsight.SpendingIncrease -> Icons.AutoMirrored.Filled.TrendingUp
    is TodayInsight.UpcomingTravel -> Icons.Default.FlightTakeoff
    is TodayInsight.MediaBacklog -> if (insight.isReading) Icons.AutoMirrored.Filled.MenuBook else Icons.Default.Movie
    is TodayInsight.ActionableExternalInsight -> Icons.Default.AutoAwesome
}

@Composable
private fun insightColor(insight: TodayInsight) = when (insight) {
    is TodayInsight.Expiring -> MaterialTheme.colorScheme.error
    is TodayInsight.PossibleDuplicates -> MaterialTheme.colorScheme.tertiary
    is TodayInsight.SpendingIncrease -> MaterialTheme.colorScheme.error
    is TodayInsight.UpcomingTravel -> MaterialTheme.colorScheme.primary
    is TodayInsight.MediaBacklog -> MaterialTheme.colorScheme.secondary
    is TodayInsight.ActionableExternalInsight -> MaterialTheme.colorScheme.primary
}

private fun formatDate(timestamp: Long): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate())

private fun BigDecimal.display(): String = stripTrailingZeros().toPlainString()

@Preview(showBackground = true)
@Composable
private fun MorningBriefingCardPreview() {
    MaterialTheme {
        MorningBriefingContent(
            greeting = BriefingGreeting.MORNING,
            insights = listOf(
                TodayInsight.Expiring("1", "Passport", 1_800_000_000_000, 12, 100),
                TodayInsight.SpendingIncrease(
                    "2", "Internet", "EGP", BigDecimal("500"), BigDecimal("550"),
                    BigDecimal("50"), listOf("2", "3")
                )
            )
        )
    }
}
