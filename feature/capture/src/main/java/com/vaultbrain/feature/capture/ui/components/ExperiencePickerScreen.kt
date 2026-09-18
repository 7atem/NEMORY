package com.vaultbrain.feature.capture.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.vaultbrain.core.common.util.humanize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.ai.heuristics.experience.ExperienceKeywordLibrary
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.feature.capture.ExperienceDefinitions
import com.vaultbrain.feature.capture.R

/**
 * Redesigned experience picker that shows:
 * 1. Hero section with top-3 OCR-ranked suggestions as large tappable cards
 * 2. "Something else?" expander that opens the full categorized picker
 * 3. Optional search when the full picker is expanded
 *
 * Most users will tap one of the top-3 cards and never scroll further.
 */
@Composable
fun ExperiencePickerScreen(
    drafts: List<VaultItem>,
    suggestions: List<String>,
    currentIndex: Int,
    onExperienceSelected: (String) -> Unit,
    onJustSave: () -> Unit,
    modifier: Modifier = Modifier,
    scoredSuggestions: List<ExperienceKeywordLibrary.ScoredExperience> = emptyList()
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }

    val grouped = remember {
        ExperienceDefinitions.lensesWithExperiences()
            .map { (lensId, experiences) ->
                lensId to experiences.map { experience ->
                    ExperienceRowInfo(
                        experience = experience,
                        title = context.getString(experience.titleRes),
                        hint = experience.hintRes?.let { context.getString(it) }.orEmpty()
                    )
                }
            }
    }

    // Build top-3 hero suggestion data. Sub-experiences are presented through their
    // primary parent (title), with the specific subtype name as subtitle; selecting
    // the card still stores the specific sub-experience id on the item.
    val heroSuggestions = remember(scoredSuggestions, suggestions) {
        val scored = scoredSuggestions.take(3)
        if (scored.isNotEmpty()) {
            scored.mapNotNull { se ->
                ExperienceDefinitions.experienceById(se.experienceId)?.let { exp ->
                    val primary = ExperienceDefinitions.resolvePrimary(se.experienceId) ?: exp
                    HeroSuggestion(
                        experienceId = se.experienceId,
                        title = context.getString(primary.titleRes),
                        subtitle = if (primary.id != exp.id) context.getString(exp.titleRes) else null,
                        matchedKeywords = se.matchedKeywords,
                        score = se.score
                    )
                }
            }.distinctBy { it.title }
        } else {
            // Fallback to old-style suggestions
            suggestions.take(3).mapNotNull { id ->
                ExperienceDefinitions.experienceById(id)?.let { exp ->
                    val primary = ExperienceDefinitions.resolvePrimary(id) ?: exp
                    HeroSuggestion(
                        experienceId = id,
                        title = context.getString(primary.titleRes),
                        subtitle = if (primary.id != exp.id) context.getString(exp.titleRes) else null,
                        matchedKeywords = emptyList(),
                        score = 0f
                    )
                }
            }
        }
    }

    val filtered = remember(query, showAll) {
        when {
            query.isNotBlank() -> {
                grouped.map { (lensId, rows) ->
                    lensId to rows.filter { info ->
                        info.title.contains(query, ignoreCase = true) ||
                            info.hint.contains(query, ignoreCase = true)
                    }
                }.filter { (_, rows) -> rows.isNotEmpty() }
            }
            showAll -> grouped
            else -> emptyList()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onJustSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(stringResource(R.string.feature_capture_experience_just_save))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.feature_capture_experience_picker_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = stringResource(R.string.feature_capture_experience_picker_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (drafts.size > 1) {
                        Text(
                            text = stringResource(
                                R.string.feature_capture_item_position,
                                currentIndex + 1,
                                drafts.size
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Hero suggestion cards (top 3)
            if (heroSuggestions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.feature_capture_experience_top_matches),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(
                    items = heroSuggestions,
                    key = { "hero_${it.experienceId}" }
                ) { hero ->
                    ExperienceSuggestionCard(
                        title = hero.title,
                        subtitle = hero.subtitle,
                        matchedKeywords = hero.matchedKeywords,
                        confidencePercent = (hero.score * 10).toInt().coerceIn(0, 100),
                        isTopMatch = hero == heroSuggestions.firstOrNull(),
                        onClick = { onExperienceSelected(hero.experienceId) }
                    )
                }
            }

            // "Something else?" section
            if (!showAll && query.isBlank()) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    TextButton(
                        onClick = { showAll = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Something else?")
                    }
                }
            }

            // Expanded full picker
            if (showAll || query.isNotBlank()) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            if (it.isNotBlank()) showAll = true
                        },
                        label = { Text(stringResource(R.string.feature_capture_experience_search)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                filtered.forEach { (lensId, rows) ->
                    if (rows.isNotEmpty()) {
                        item(key = "header_$lensId") {
                            Text(
                                text = lensId.humanize(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(
                            items = rows,
                            key = { "list_${it.experience.id}" }
                        ) { info ->
                            ExperienceRow(
                                title = info.title,
                                hint = info.hint,
                                onClick = { onExperienceSelected(info.experience.id) }
                            )
                        }
                    }
                }
            }

            item { androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp)) }
        }
    }
}

private data class HeroSuggestion(
    val experienceId: String,
    val title: String,
    val subtitle: String? = null,
    val matchedKeywords: List<String>,
    val score: Float
)

private data class ExperienceRowInfo(
    val experience: com.vaultbrain.core.common.model.PersonalExperience,
    val title: String,
    val hint: String
)

@Composable
private fun ExperienceRow(
    title: String,
    hint: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            if (hint.isNotBlank()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
