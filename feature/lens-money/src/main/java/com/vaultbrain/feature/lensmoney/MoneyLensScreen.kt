package com.vaultbrain.feature.lensmoney

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.common.ui.SharedItemCard
import com.vaultbrain.shared.model.VaultItem

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

    val categories = remember { SpendingAnalyticsProvider.getCategories() }
    val warranties = remember { SpendingAnalyticsProvider.getWarranties() }

    val recentItems = items.filter { (now - it.createdAt) <= 7 * 24 * 60 * 60 * 1000L }
    val olderItems = items.filter { (now - it.createdAt) > 7 * 24 * 60 * 60 * 1000L }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.money_lens_view_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (upcomingTrials.isNotEmpty()) {
            item {
                upcomingTrials.forEach { trialItem ->
                    TrialAlertCard(
                        title = trialItem.title,
                        alertDate = trialItem.secondaryAlertDate!!,
                        onClick = { onItemClick(trialItem) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Monthly Spend Trends & Category Progress Breakdown
        item {
            MonthlySpendingCard(categories = categories)
        }

        // Warranties & Return Windows Section
        item {
            WarrantiesSection(warranties = warranties)
        }

        // Tax Deductible Expenses & CSV Export Hero
        item {
            TaxDeductibleCard(onExportClick = onExportClick)
        }

        item {
            MoneyStatCard(
                monthlyBurnRate = monthlyBurnRate,
                subscriptionCount = subscriptions.size,
                receiptCount = receiptCount
            )
        }

        item {
            Text(
                text = stringResource(R.string.money_iban_validator),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            IbanValidatorCard()
        }

        item {
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
        }

        item {
            Text(
                text = stringResource(R.string.money_items_title),
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.money_empty_message),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center
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

@Composable
private fun MonthlySpendingCard(categories: List<ExpenseCategoryItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.money_spend_trends_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.money_spend_current_month, "$1,420.50"),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "+19.8%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.money_categories_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Category Breakdown Items
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                categories.forEach { cat ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${(cat.percentage * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = cat.totalFormatted,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { cat.percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(cat.colorHex),
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WarrantiesSection(warranties: List<WarrantyCardItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.money_warranty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(warranties, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.width(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (item.isUrgent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = if (item.isUrgent) {
                                        stringResource(R.string.money_warranty_expiring_soon)
                                    } else {
                                        stringResource(R.string.money_warranty_active)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isUrgent) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = item.issuer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )

                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (item.isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.money_warranty_days_left, item.daysRemaining),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (item.isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = item.expirationFormatted,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxDeductibleCard(onExportClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.money_tax_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.money_tax_total_label, "$1,085.45"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Button(
                onClick = onExportClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.money_export_csv),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
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
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Active Subscriptions Burn Rate",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$${"%.2f".format(monthlyBurnRate)} / month",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Tracked subscriptions: $subscriptionCount | Total receipts: $receiptCount",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun IbanValidatorCard() {
    var iban by remember { mutableStateOf("") }
    val normalizedIban = iban.filterNot(Char::isWhitespace).uppercase()
    val isValid = isValidIban(normalizedIban)

    OutlinedTextField(
        value = iban,
        onValueChange = { iban = it.uppercase().take(34) },
        label = { Text("Validate IBAN") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            if (iban.isNotEmpty()) {
                Icon(
                    imageVector = if (isValid) Icons.Default.Add else Icons.Default.Receipt,
                    contentDescription = null,
                    tint = if (isValid) Color.Green else MaterialTheme.colorScheme.error
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

/**
 * Parses a numeric amount from receipt metadata keys commonly produced by the
 * heuristic extractor (e.g. "total", "amount", "value").
 */
internal fun Map<String, String>.parseAmount(): Double? {
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
            val amount = item.targetPrice ?: item.parsedMetadata.parseAmount()
            if (amount != null) {
                Text(
                    text = stringResource(R.string.money_item_price, amount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
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
