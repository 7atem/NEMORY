package com.vaultbrain.feature.lensbureaucracy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.common.ui.ResponsiveContainer
import com.vaultbrain.core.common.ui.SectionHeading
import com.vaultbrain.core.common.ui.SharedItemCard
import com.vaultbrain.core.common.ui.VaultSpacing
import com.vaultbrain.shared.model.VaultItem
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BureaucracyLensScreen(
    items: List<VaultItem>,
    onItemClick: (VaultItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val thirtyDays = TimeUnit.DAYS.toMillis(30)
    val expiringCount = items.count {
        it.expiryDate != null && it.expiryDate in now..(now + thirtyDays)
    }
    val pinnedCount = items.count { it.isPinned }

    ResponsiveContainer(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = VaultSpacing.lg,
                top = VaultSpacing.lg,
                end = VaultSpacing.lg,
                bottom = VaultSpacing.fabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.md),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = stringResource(R.string.bureaucracy_lens_view_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                BureaucracyStatCard(
                    totalCount = items.size,
                    expiringCount = expiringCount,
                    pinnedCount = pinnedCount
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)) {
                    Text(
                        text = stringResource(R.string.bureaucracy_quick_add_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(VaultSpacing.xs),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BureaucracyQuickAddChip(label = stringResource(R.string.bureaucracy_quick_add_id), onClick = onQuickAdd)
                        BureaucracyQuickAddChip(label = stringResource(R.string.bureaucracy_quick_add_contract), onClick = onQuickAdd)
                        BureaucracyQuickAddChip(label = stringResource(R.string.bureaucracy_quick_add_permit), onClick = onQuickAdd)
                    }
                }
            }

            item {
                SectionHeading(text = stringResource(R.string.bureaucracy_items_title))
            }

            if (items.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = stringResource(R.string.bureaucracy_empty_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(VaultSpacing.xl)
                        )
                    }
                }
            } else {
                items(items, key = { it.id }) { item ->
                    BureaucracyItemCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun BureaucracyStatCard(
    totalCount: Int,
    expiringCount: Int,
    pinnedCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.bureaucracy_stat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.bureaucracy_stat_total, totalCount),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.bureaucracy_stat_expiring, expiringCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = stringResource(R.string.bureaucracy_stat_pinned, pinnedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun BureaucracyQuickAddChip(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BureaucracyItemCard(item: VaultItem, onClick: () -> Unit) {
    val mrzInfo = item.customFields["mrz"]?.let(MrzParser::parse)
        ?: item.rawOcrText?.let(MrzParser::parse)

    SharedItemCard(
        item = item,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        extraContent = {
            if (mrzInfo != null) {
                val statusColor = if (mrzInfo.isChecksumValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
                    ) {
                        Icon(
                            imageVector = if (mrzInfo.isChecksumValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            stringResource(R.string.bureaucracy_mrz_document, mrzInfo.documentNumber),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor
                        )
                    }
                    Text(
                        stringResource(
                            if (mrzInfo.isChecksumValid) R.string.bureaucracy_mrz_verified
                            else R.string.bureaucracy_mrz_needs_review
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}
