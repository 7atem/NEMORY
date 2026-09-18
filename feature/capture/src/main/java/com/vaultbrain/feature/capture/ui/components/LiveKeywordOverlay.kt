package com.vaultbrain.feature.capture.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.ai.heuristics.experience.ExperienceKeywordLibrary.KeywordCategory
import kotlinx.coroutines.delay

/**
 * Animated overlay that shows detected keywords as they are found during OCR processing.
 *
 * Keywords appear with staggered fade-in animations, grouped by category with colored icons.
 * This gives the user immediate visual feedback that the app is "reading" their document.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveKeywordOverlay(
    detectedKeywords: List<KeywordDisplayItem>,
    modifier: Modifier = Modifier
) {
    if (detectedKeywords.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Detected",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            detectedKeywords.forEachIndexed { index, item ->
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(item.keyword) {
                    delay(index * 120L) // staggered
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
                ) {
                    KeywordChip(item)
                }
            }
        }
    }
}

@Composable
private fun KeywordChip(item: KeywordDisplayItem) {
    val (containerColor, contentColor, icon) = when (item.category) {
        KeywordCategory.DOCUMENT_TYPE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.Label
        )
        KeywordCategory.IDENTIFIER -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            Icons.Default.AttachMoney
        )
        KeywordCategory.DETAIL -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            Icons.Default.Store
        )
        KeywordCategory.CONTEXT -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.CalendarToday
        )
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = item.keyword,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Display model for a keyword chip in the overlay. */
data class KeywordDisplayItem(
    val keyword: String,
    val category: KeywordCategory
)

/** Map icon for a keyword category. */
fun categoryIcon(category: KeywordCategory): ImageVector = when (category) {
    KeywordCategory.DOCUMENT_TYPE -> Icons.Default.Label
    KeywordCategory.IDENTIFIER -> Icons.Default.AttachMoney
    KeywordCategory.DETAIL -> Icons.Default.Store
    KeywordCategory.CONTEXT -> Icons.Default.CalendarToday
}
