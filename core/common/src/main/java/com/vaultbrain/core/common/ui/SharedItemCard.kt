package com.vaultbrain.core.common.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.vaultbrain.core.common.model.VaultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedItemCard(
    item: VaultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    val presentation = VaultItemPresenter.present(item)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            presentation.keyFact?.let { keyFact ->
                Text(
                    text = keyFact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            presentation.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val topFields = MetadataFields.topFields(item.effectiveClassification, item.parsedMetadata)
            if (topFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                topFields.forEach { (key, value) ->
                    Text(
                        text = "${MetadataFields.label(key)}: $value",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (extraContent != null) {
                extraContent()
            }
        }
    }
}
