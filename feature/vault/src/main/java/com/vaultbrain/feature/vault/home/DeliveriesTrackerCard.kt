package com.vaultbrain.feature.vault.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vaultbrain.feature.vault.R

enum class DeliveryStatus(val stepIndex: Int) {
    ORDERED(0),
    SHIPPED(1),
    OUT_FOR_DELIVERY(2),
    DELIVERED(3)
}

data class DeliveryItem(
    val id: String,
    val merchant: String,
    val orderNumber: String,
    val title: String,
    val status: DeliveryStatus,
    val carrier: String,
    val trackingNumber: String,
    val estimatedDelivery: String,
    val price: String,
    val merchantColorHex: Long
)

object DefaultDeliveriesProvider {
    fun getActiveDeliveries(): List<DeliveryItem> {
        return listOf(
            DeliveryItem(
                id = "amz-1",
                merchant = "Amazon Prime",
                orderNumber = "114-8920193-4829104",
                title = "Anker Prime 100W GaN Fast Charger & USB-C Cable",
                status = DeliveryStatus.OUT_FOR_DELIVERY,
                carrier = "Amazon Logistics",
                trackingNumber = "TBA309482019482",
                estimatedDelivery = "Today by 8:00 PM",
                price = "$64.99",
                merchantColorHex = 0xFFFF9900
            ),
            DeliveryItem(
                id = "amz-2",
                merchant = "Amazon Prime",
                orderNumber = "114-3829104-1940291",
                title = "Nike Air Zoom Pegasus 40 Running Shoes (Size 10.5)",
                status = DeliveryStatus.SHIPPED,
                carrier = "UPS Next Day Air",
                trackingNumber = "1Z9999999999999999",
                estimatedDelivery = "Tomorrow",
                price = "$129.50",
                merchantColorHex = 0xFFFF9900
            ),
            DeliveryItem(
                id = "apl-1",
                merchant = "Apple Store",
                orderNumber = "W904810294",
                title = "AirPods Pro (2nd Gen) MagSafe Case (USB-C)",
                status = DeliveryStatus.ORDERED,
                carrier = "FedEx Express",
                trackingNumber = "782019482019",
                estimatedDelivery = "In 3 days",
                price = "$249.00",
                merchantColorHex = 0xFF2563EB
            )
        )
    }

    fun parseEmailText(rawText: String): DeliveryItem {
        val isAmazon = rawText.contains("amazon", ignoreCase = true)
        val isApple = rawText.contains("apple", ignoreCase = true)
        val isUps = rawText.contains("ups", ignoreCase = true)
        val isFedex = rawText.contains("fedex", ignoreCase = true)

        val merchant = when {
            isAmazon -> "Amazon Prime"
            isApple -> "Apple Store"
            else -> "Online Merchant"
        }

        val carrier = when {
            isAmazon -> "Amazon Logistics"
            isUps -> "UPS Express"
            isFedex -> "FedEx Ground"
            else -> "Standard Carrier"
        }

        val trackingRegex = Regex("""([A-Z0-9]{10,20})""")
        val foundCode = trackingRegex.find(rawText)?.value ?: "TRK-${System.currentTimeMillis() % 1000000}"

        return DeliveryItem(
            id = "scanned-${System.currentTimeMillis()}",
            merchant = merchant,
            orderNumber = "ORD-${System.currentTimeMillis() % 10000000}",
            title = if (rawText.length > 50) rawText.substring(0, 48) + "…" else rawText.ifBlank { "Forwarded Package Order" },
            status = DeliveryStatus.SHIPPED,
            carrier = carrier,
            trackingNumber = foundCode,
            estimatedDelivery = "In 2 days",
            price = "$49.99",
            merchantColorHex = if (isAmazon) 0xFFFF9900 else 0xFF2563EB
        )
    }
}

@Composable
fun DeliveriesTrackerCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var deliveries by remember { mutableStateOf(DefaultDeliveriesProvider.getActiveDeliveries()) }
    var selectedDeliveryId by remember { mutableStateOf(deliveries.firstOrNull()?.id) }
    var showScanDialog by remember { mutableStateOf(false) }

    val activeItem = deliveries.find { it.id == selectedDeliveryId } ?: deliveries.firstOrNull()

    fun trackCarrier(item: DeliveryItem) {
        val trackingUrl = when {
            item.carrier.contains("Amazon", ignoreCase = true) ->
                "https://www.amazon.com/progress-tracker/package/ref=ppx_yo_dt_b_track_package"
            item.carrier.contains("UPS", ignoreCase = true) ->
                "https://www.ups.com/track?tracknum=${item.trackingNumber}"
            item.carrier.contains("FedEx", ignoreCase = true) ->
                "https://www.fedex.com/fedextrack/?trknbr=${item.trackingNumber}"
            else ->
                "https://www.google.com/search?q=tracking+${item.trackingNumber}"
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(trackingUrl))
        try {
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            // Ignore if browser not found
        }
    }

    if (showScanDialog) {
        OrderEmailScannerDialog(
            onDismiss = { showScanDialog = false },
            onImport = { text ->
                val newDelivery = DefaultDeliveriesProvider.parseEmailText(text)
                deliveries = listOf(newDelivery) + deliveries
                selectedDeliveryId = newDelivery.id
                showScanDialog = false
            }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with scan email action button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFFF9900), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.deliveries_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${deliveries.size} active shipments",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                FilledTonalButton(
                    onClick = { showScanDialog = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.deliveries_scan_email_action),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Package Selection Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(deliveries, key = { it.id }) { item ->
                    val isSelected = item.id == activeItem?.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedDeliveryId = item.id },
                        label = {
                            Text(
                                text = "${item.merchant}: ${item.title.take(16)}…",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(item.merchantColorHex), CircleShape)
                            )
                        }
                    )
                }
            }

            activeItem?.let { item ->
                // Active Delivery Hero Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(item.merchantColorHex).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = item.merchant.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = Color(item.merchantColorHex),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = item.estimatedDelivery,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${item.carrier} • #${item.trackingNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 4-Step Progress Stepper
                        DeliveryProgressStepper(currentStatus = item.status)

                        Button(
                            onClick = { trackCarrier(item) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.deliveries_track_action),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryProgressStepper(currentStatus: DeliveryStatus) {
    val steps = listOf(
        Pair(stringResource(R.string.deliveries_ordered), DeliveryStatus.ORDERED),
        Pair(stringResource(R.string.deliveries_shipped), DeliveryStatus.SHIPPED),
        Pair(stringResource(R.string.deliveries_out_for_delivery), DeliveryStatus.OUT_FOR_DELIVERY),
        Pair(stringResource(R.string.deliveries_delivered), DeliveryStatus.DELIVERED)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (label, status) ->
            val isCompleted = currentStatus.stepIndex >= status.stepIndex
            val isCurrent = currentStatus == status

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> MaterialTheme.colorScheme.primary
                                isCompleted -> Color(0xFF2E7D32)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun OrderEmailScannerDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var rawText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.deliveries_scan_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = stringResource(R.string.deliveries_scan_dialog_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.deliveries_scan_dialog_placeholder),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.deliveries_scan_dialog_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { if (rawText.isNotBlank()) onImport(rawText) },
                        enabled = rawText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.deliveries_scan_dialog_import))
                    }
                }
            }
        }
    }
}
