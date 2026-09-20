package com.vaultbrain.feature.lensbureaucracy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaultbrain.shared.model.VaultItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.vaultbrain.core.common.ui.SharedItemCard

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.bureaucracy_lens_view_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        BureaucracyStatCard(
            totalCount = items.size,
            expiringCount = expiringCount,
            pinnedCount = pinnedCount
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.bureaucracy_quick_add_title),
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            BureaucracyQuickAddChip(label = stringResource(R.string.bureaucracy_quick_add_id), onClick = onQuickAdd)
            BureaucracyQuickAddChip(label = stringResource(R.string.bureaucracy_quick_add_contract), onClick = onQuickAdd)
            BureaucracyQuickAddChip(label = stringResource(R.string.bureaucracy_quick_add_permit), onClick = onQuickAdd)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.bureaucracy_items_title),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { item ->
                BureaucracyItemCard(item = item, onClick = { onItemClick(item) })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BureaucracyQuickAddChip(label: String, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.height(18.dp)
            )
        }
    )
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

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
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        "MRZ document: ${mrzInfo.documentNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (mrzInfo.isChecksumValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(
                        if (mrzInfo.isChecksumValid) "MRZ checksums verified" else "MRZ needs manual verification",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    )
}
