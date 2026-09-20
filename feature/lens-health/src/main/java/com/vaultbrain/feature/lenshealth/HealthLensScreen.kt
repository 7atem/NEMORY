package com.vaultbrain.feature.lenshealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Timeline
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaultbrain.shared.model.VaultItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.vaultbrain.core.common.ui.SharedItemCard

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HealthLensScreen(
    items: List<VaultItem>,
    onItemClick: (VaultItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val sevenDays = now + 7 * 24 * 60 * 60 * 1000L
    val alertCount = items.count { it.secondaryAlertDate?.let { date -> date >= now } == true }
    val expiringSoonCount = items.count {
        it.expiryDate?.let { date -> date in now..sevenDays } == true
    }
    val prescriptionCount = items.count {
        it.effectiveClassification?.name.equals("PRESCRIPTION", ignoreCase = true) ||
            it.parsedMetadata["type"].equals("prescription", ignoreCase = true) ||
            it.title.contains("prescription", ignoreCase = true)
    }
    
    val nextDose = items
        .filter { it.recurringRule != null || it.title.contains("dose", ignoreCase = true) }
        .mapNotNull { it.secondaryAlertDate }
        .filter { it > now }
        .minOrNull()
        
    val labResults = items.filter { it.effectiveClassification?.name.equals("LAB_RESULT", ignoreCase = true) }

    val recentItems = items.filter { (now - it.createdAt) <= 7 * 24 * 60 * 60 * 1000L }
    val olderItems = items.filter { (now - it.createdAt) > 7 * 24 * 60 * 60 * 1000L }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.health_lens_view_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            MedicalIdCard()

            Spacer(modifier = Modifier.height(16.dp))

            HealthStatCard(
                totalCount = items.size,
                alertCount = alertCount,
                expiringSoonCount = expiringSoonCount,
                prescriptionCount = prescriptionCount,
                nextDoseDate = nextDose,
                labTrendCount = labResults.size
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (labResults.isNotEmpty()) {
                LabTrendCard(results = labResults)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = stringResource(R.string.health_quick_add_title),
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HealthQuickAddChip(label = stringResource(R.string.health_quick_add_prescription), onClick = onQuickAdd)
                HealthQuickAddChip(label = stringResource(R.string.health_quick_add_test), onClick = onQuickAdd)
                HealthQuickAddChip(label = stringResource(R.string.health_quick_add_insurance), onClick = onQuickAdd)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.health_items_title),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.health_empty_message),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            if (recentItems.isNotEmpty()) {
                stickyHeader {
                    Text(
                        text = stringResource(R.string.health_section_recent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 8.dp)
                    )
                }
                items(recentItems, key = { it.id }) { item ->
                    HealthItemCard(item = item, onClick = { onItemClick(item) })
                }
            }
            
            if (olderItems.isNotEmpty()) {
                stickyHeader {
                    Text(
                        text = stringResource(R.string.health_section_older),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 8.dp)
                    )
                }
                items(olderItems, key = { it.id }) { item ->
                    HealthItemCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun HealthStatCard(
    totalCount: Int,
    alertCount: Int,
    expiringSoonCount: Int,
    prescriptionCount: Int,
    nextDoseDate: Long?,
    labTrendCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.health_stat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = stringResource(R.string.health_stat_total, totalCount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    nextDoseDate?.let { date ->
                        Text(
                            text = stringResource(R.string.health_stat_next_dose, formatTime(date)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.health_stat_lab_trends,
                            labTrendCount,
                            labTrendCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicalIdCard() {
    // Medical ID is not currently populated from real data.
    // Removed hardcoded "Blood Type: O+" and "Penicillin" to avoid user confusion.
}

@Composable
private fun LabTrendCard(results: List<VaultItem>) {
    val dataPoints = results.mapNotNull { item ->
        item.customFields["lab_value"]?.toFloatOrNull()
            ?: item.parsedMetadata["lab_value"]?.toFloatOrNull()
    }
    
    if (dataPoints.size < 2) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.health_vitals_insufficient), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    
    val min = dataPoints.minOrNull() ?: 0f
    val max = dataPoints.maxOrNull() ?: 1f
    val range = if (max == min) 1f else (max - min)
    val normalizedData = dataPoints.map { (it - min) / range }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.health_vitals_trend), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Sparkline(
                    data = normalizedData,
                    modifier = Modifier.fillMaxWidth().height(32.dp)
                )
            }
        }
    }
}

@Composable
fun Sparkline(data: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (data.size < 2) return@Canvas
        val path = Path()
        val xStep = size.width / (data.size - 1)
        data.forEachIndexed { index, value ->
            val x = index * xStep
            val y = size.height - (value * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthQuickAddChip(label: String, onClick: () -> Unit) {
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
private fun HealthItemCard(item: VaultItem, onClick: () -> Unit) {
    val isPrescription = item.effectiveClassification?.name.equals("PRESCRIPTION", ignoreCase = true) ||
            item.parsedMetadata["type"].equals("prescription", ignoreCase = true)

    SharedItemCard(
        item = item,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        extraContent = {
            val medication = item.customFields["medication"] ?: item.parsedMetadata["medication"]
            val dose = item.customFields["dosage"] ?: item.parsedMetadata["dosage"]
            
            if (item.expiryDate != null || !medication.isNullOrBlank() || !dose.isNullOrBlank() || isPrescription) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.expiryDate?.let { expiry ->
                        Text(
                            text = stringResource(R.string.health_item_expiry, formatDate(expiry)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    medication?.takeIf(String::isNotBlank)?.let {
                        Text("Rx: $it", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    dose?.takeIf(String::isNotBlank)?.let {
                        Text("Dose: $it", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (isPrescription) {
                        Icon(
                            imageVector = Icons.Default.Medication,
                            contentDescription = "Prescription",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}
