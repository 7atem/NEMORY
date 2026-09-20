package com.vaultbrain.feature.lensmoney

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vaultbrain.shared.model.VaultItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.background
import com.vaultbrain.core.common.ui.SharedItemCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MoneyLensScreen(
    items: List<VaultItem>,
    onItemClick: (VaultItem) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: () -> Unit = {}
) {
    val receiptCount = items.count {
        it.title.contains("receipt", ignoreCase = true) ||
            it.parsedMetadata["type"].equals("receipt", ignoreCase = true)
    }
    
    val subscriptions = items.filter { 
        it.recurringRule != null || 
        it.parsedMetadata["type"].equals("subscription", ignoreCase = true) ||
        it.title.contains("subscription", ignoreCase = true)
    }
    
    val monthlyBurnRate = subscriptions.sumOf { it.parsedMetadata.parseAmount() ?: 0.0 }
    
    val now = System.currentTimeMillis()
    val upcomingTrials = items.filter {
        val alertDate = it.secondaryAlertDate ?: return@filter false
        val daysUntil = (alertDate - now) / (1000 * 60 * 60 * 24)
        daysUntil in 0..7
    }

    val recentItems = items.filter { (now - it.createdAt) <= 7 * 24 * 60 * 60 * 1000L }
    val olderItems = items.filter { (now - it.createdAt) > 7 * 24 * 60 * 60 * 1000L }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.money_lens_view_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
            item {
                if (upcomingTrials.isNotEmpty()) {
                    upcomingTrials.forEach { trialItem ->
                        TrialAlertCard(
                            title = trialItem.title,
                            alertDate = trialItem.secondaryAlertDate!!,
                            onClick = { onItemClick(trialItem) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                MoneyStatCard(
                    monthlyBurnRate = monthlyBurnRate,
                    subscriptionCount = subscriptions.size,
                    receiptCount = receiptCount
                )

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = onExportClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.money_export_csv))
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.money_iban_validator),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                IbanValidatorCard()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.money_quick_add_title),
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MoneyQuickAddChip(label = stringResource(R.string.money_quick_add_receipt), onClick = onQuickAdd)
                    MoneyQuickAddChip(label = stringResource(R.string.money_quick_add_bill), onClick = onQuickAdd)
                    MoneyQuickAddChip(label = stringResource(R.string.money_quick_add_warranty), onClick = onQuickAdd)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.money_items_title),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.money_empty_message),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                if (recentItems.isNotEmpty()) {
                    stickyHeader {
                        Text(
                            text = stringResource(R.string.money_section_recent),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(vertical = 8.dp)
                        )
                    }
                    items(recentItems, key = { it.id }) { item ->
                        MoneyItemCard(item = item, onClick = { onItemClick(item) })
                    }
                }
                
                if (olderItems.isNotEmpty()) {
                    stickyHeader {
                        Text(
                            text = stringResource(R.string.money_section_older),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(vertical = 8.dp)
                        )
                    }
                    items(olderItems, key = { it.id }) { item ->
                        MoneyItemCard(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrialAlertCard(title: String, alertDate: Long, onClick: () -> Unit) {
    val daysUntil = ((alertDate - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(stringResource(R.string.money_trial_expiring_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.money_trial_expiring_message, title, daysUntil.toInt()), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MoneyStatCard(
    monthlyBurnRate: Double,
    subscriptionCount: Int,
    receiptCount: Int
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Monthly Subscriptions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${"%.2f".format(monthlyBurnRate)} / month",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tracking $subscriptionCount active subscriptions & $receiptCount receipts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun IbanValidatorCard() {
    var iban by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val normalizedIban = iban.filterNot(Char::isWhitespace).uppercase()
    val isValid = isValidIban(normalizedIban)
    
    androidx.compose.material3.OutlinedTextField(
        value = iban,
        onValueChange = { iban = it.uppercase().take(34) },
        label = { Text("Validate IBAN") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (iban.isNotEmpty()) {
                Icon(
                    imageVector = if (isValid) androidx.compose.material.icons.Icons.Default.Add else androidx.compose.material.icons.Icons.Default.Receipt,
                    contentDescription = null,
                    tint = if (isValid) androidx.compose.ui.graphics.Color.Green else MaterialTheme.colorScheme.error
                )
            }
        },
        supportingText = {
            if (iban.isNotEmpty()) {
                if (isValid) Text("Valid IBAN format (${iban.take(2)} Country Code)")
                else Text("Invalid IBAN format")
            }
        }
    )
}

/** ISO 13616 format and MOD-97 checksum validation. */
internal fun isValidIban(value: String): Boolean {
    val iban = value.filterNot(Char::isWhitespace).uppercase()
    if (iban.length !in 15..34 || !iban.matches(Regex("^[A-Z]{2}[0-9]{2}[A-Z0-9]+$"))) return false
    val rearranged = iban.drop(4) + iban.take(4)
    var remainder = 0
    rearranged.forEach { char ->
        val digits = if (char.isDigit()) char.toString() else (char.code - 'A'.code + 10).toString()
        digits.forEach { digit -> remainder = (remainder * 10 + digit.digitToInt()) % 97 }
    }
    return remainder == 1
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
}

/**
 * Parses a numeric amount from receipt metadata keys commonly produced by the
 * heuristic extractor (e.g. "total", "amount", "value").
 */
private fun Map<String, String>.parseAmount(): Double? {
    val amountKeys = listOf("total", "amount", "value", "price", "sum")
    return amountKeys.firstNotNullOfOrNull { key ->
        this[key]?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyQuickAddChip(label: String, onClick: () -> Unit) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoneyItemCard(item: VaultItem, onClick: () -> Unit) {
    val isRecurring = item.recurringRule != null || 
            item.parsedMetadata["type"].equals("subscription", ignoreCase = true)

    SharedItemCard(
        item = item,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        extraContent = {
            if (isRecurring || !item.customFields["category"].isNullOrBlank() || !item.customFields["tax"].isNullOrBlank() || !item.parsedMetadata["tax"].isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item.customFields["category"]?.takeIf(String::isNotBlank)?.let {
                        Text("Category: $it", style = MaterialTheme.typography.labelMedium)
                    }
                    (item.customFields["tax"] ?: item.parsedMetadata["tax"])?.takeIf(String::isNotBlank)?.let {
                        Text("Tax: $it", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (isRecurring) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = stringResource(R.string.money_item_recurring),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}
