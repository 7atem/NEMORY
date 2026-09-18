package com.vaultbrain.feature.lenstravel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.ui.SharedItemCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TravelLensScreen(
    items: List<VaultItem>,
    onItemClick: (VaultItem) -> Unit,
    modifier: Modifier = Modifier,
    onQuickAdd: () -> Unit = {}
) {
    val now = System.currentTimeMillis()
    val placesCount = items.mapNotNull { it.locationName }.distinct().size
    val upcomingCount = items.count { it.expiryDate?.let { date -> date >= now } == true }
    val flightCount = items.count {
        it.effectiveClassification?.name.equals("TICKET", ignoreCase = true) ||
            it.title.contains("flight", ignoreCase = true) ||
            it.parsedMetadata["type"].equals("flight", ignoreCase = true)
    }
    val hotelCount = items.count {
        it.effectiveClassification?.name.equals("HOTEL", ignoreCase = true) ||
            it.title.contains("hotel", ignoreCase = true) ||
            it.parsedMetadata["type"].equals("hotel", ignoreCase = true)
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
                text = stringResource(R.string.travel_lens_view_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            TravelStatCard(
                totalCount = items.size,
                placesCount = placesCount,
                upcomingCount = upcomingCount,
                flightCount = flightCount,
                hotelCount = hotelCount
            )

            Spacer(modifier = Modifier.height(16.dp))

            BillSplitterCard(items = items)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.travel_quick_add_title),
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TravelQuickAddChip(label = stringResource(R.string.travel_quick_add_ticket), onClick = onQuickAdd)
                TravelQuickAddChip(label = stringResource(R.string.travel_quick_add_passport), onClick = onQuickAdd)
                TravelQuickAddChip(label = stringResource(R.string.travel_quick_add_boarding), onClick = onQuickAdd)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.travel_items_title),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (items.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.travel_empty_message),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            if (recentItems.isNotEmpty()) {
                stickyHeader {
                    Text(
                        text = stringResource(R.string.travel_section_recent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 8.dp)
                    )
                }
                items(recentItems, key = { it.id }) { item ->
                    TravelItemCard(item = item, onClick = { onItemClick(item) })
                }
            }
            
            if (olderItems.isNotEmpty()) {
                stickyHeader {
                    Text(
                        text = stringResource(R.string.travel_section_older),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(vertical = 8.dp)
                    )
                }
                items(olderItems, key = { it.id }) { item ->
                    TravelItemCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}

@Composable
private fun TravelStatCard(
    totalCount: Int,
    placesCount: Int,
    upcomingCount: Int,
    flightCount: Int,
    hotelCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HeroStatBox(stringResource(R.string.travel_stat_hero_upcoming), upcomingCount.toString(), Modifier.weight(1f))
        HeroStatBox(stringResource(R.string.travel_stat_hero_flights), flightCount.toString(), Modifier.weight(1f))
        HeroStatBox(stringResource(R.string.travel_stat_hero_hotels), hotelCount.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun HeroStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun BillSplitterCard(items: List<VaultItem>) {
    var travelers by remember { mutableIntStateOf(2) }
    
    val totalCost = items.sumOf { 
        it.targetPrice ?: it.parsedMetadata["total"]?.toDoubleOrNull() ?: it.parsedMetadata["amount"]?.toDoubleOrNull() ?: 0.0
    }
    
    val costPerPerson = if (travelers > 0) totalCost / travelers else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.travel_splitter_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.travel_splitter_total_cost), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text("$${"%.2f".format(totalCost)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.travel_splitter_travelers), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(onClick = { if (travelers > 1) travelers-- }) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.travel_splitter_decrease), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text("$travelers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    androidx.compose.material3.IconButton(onClick = { travelers++ }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.travel_splitter_increase), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.travel_splitter_per_person), style = MaterialTheme.typography.titleSmall)
                    Text("$${"%.2f".format(costPerPerson)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelQuickAddChip(label: String, onClick: () -> Unit) {
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
private fun TravelItemCard(item: VaultItem, onClick: () -> Unit) {
    val parsed = item.rawOcrText?.let { PnrParser.parse(it) } ?: TravelParsedInfo()
    val travelInfo = parsed.copy(
        pnr = item.customFields["pnr"] ?: parsed.pnr,
        flightNumber = item.customFields["flight_number"] ?: parsed.flightNumber,
        seat = item.customFields["seat"] ?: parsed.seat,
        gate = item.customFields["gate"] ?: parsed.gate
    )

    SharedItemCard(
        item = item,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        extraContent = {
            if (travelInfo.flightNumber != null || travelInfo.pnr != null || (travelInfo.originIata != null && travelInfo.destinationIata != null)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    travelInfo.flightNumber?.let { flight ->
                        Text(
                            text = flight,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (travelInfo.originIata != null && travelInfo.destinationIata != null) {
                        Text(
                            text = "${travelInfo.originIata} ➔ ${travelInfo.destinationIata}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    travelInfo.seat?.let { seat ->
                        Text(
                            text = "Seat: $seat",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    travelInfo.pnr?.let { pnr ->
                        Text(
                            text = "PNR: $pnr",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
            item.locationName?.let { location ->
                Text(
                    text = stringResource(R.string.travel_item_location, location),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    )
}
