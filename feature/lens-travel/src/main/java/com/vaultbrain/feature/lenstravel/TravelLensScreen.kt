package com.vaultbrain.feature.lenstravel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.common.ui.SharedItemCard
import com.vaultbrain.shared.model.VaultItem

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

    val sampleTrip = remember { DefaultTripProvider.getSampleTrip() }
    var weather by remember { mutableStateOf<DestinationWeather?>(null) }
    var holidays by remember { mutableStateOf<List<PublicHoliday>>(emptyList()) }
    LaunchedEffect(Unit) {
        weather = LiveWeatherClient.fetchWeather(40.7128, -74.0060)
        holidays = LiveHolidayClient.fetchUpcomingHolidays("US")
    }
    var selectedGatePassDoc by remember { mutableStateOf<TripDocItem?>(null) }

    val recentItems = items.filter { (now - it.createdAt) <= 7 * 24 * 60 * 60 * 1000L }
    val olderItems = items.filter { (now - it.createdAt) > 7 * 24 * 60 * 60 * 1000L }

    selectedGatePassDoc?.let { doc ->
        GatePassDialog(
            flightItem = doc,
            onDismiss = { selectedGatePassDoc = null }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
        }

        // Active Trip Binder Hero Card
        item {
            TripHeroCard(
                trip = sampleTrip,
                onShowGatePass = {
                    selectedGatePassDoc = sampleTrip.flightItems.firstOrNull()
                }
            )
        }

        weather?.let { w ->
            item {
                LiveDestinationWeatherCard(weather = w)
            }
        }

        if (holidays.isNotEmpty()) {
            item {
                LiveHolidaysCard(holidays = holidays)
            }
        }

        item {
            SkyscannerFlightDealsCard()
        }

        // Destination Places & Google Maps
        item {
            PlacesAndMapsSection(places = sampleTrip.places)
        }

        // Offline Departure Checklist
        item {
            OfflineChecklistSection()
        }

        // Group Bill Splitter
        item {
            BillSplitterCard(items = items)
        }

        // Quick Add Section
        item {
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
        }

        item {
            Text(
                text = stringResource(R.string.travel_items_title),
                style = MaterialTheme.typography.titleMedium
            )
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
                    TravelItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onGatePassClick = {
                            selectedGatePassDoc = TripDocItem(
                                id = item.id,
                                title = item.title,
                                subtitle = item.summary ?: "",
                                category = "Flight",
                                refCode = item.customFields["pnr"] ?: "EK 203",
                                originIata = item.customFields["origin"] ?: "DXB",
                                destinationIata = item.customFields["destination"] ?: "JFK",
                                seat = item.customFields["seat"] ?: "14A",
                                gate = item.customFields["gate"] ?: "B22",
                                terminal = item.customFields["terminal"] ?: "3",
                                passengerName = item.customFields["passenger"] ?: "EL SAYED / HATEM"
                            )
                        }
                    )
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
                    TravelItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        onGatePassClick = {
                            selectedGatePassDoc = TripDocItem(
                                id = item.id,
                                title = item.title,
                                subtitle = item.summary ?: "",
                                category = "Flight",
                                refCode = item.customFields["pnr"] ?: "EK 203",
                                originIata = item.customFields["origin"] ?: "DXB",
                                destinationIata = item.customFields["destination"] ?: "JFK",
                                seat = item.customFields["seat"] ?: "14A",
                                gate = item.customFields["gate"] ?: "B22",
                                terminal = item.customFields["terminal"] ?: "3",
                                passengerName = item.customFields["passenger"] ?: "EL SAYED / HATEM"
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TripHeroCard(
    trip: TripBinder,
    onShowGatePass: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlightTakeoff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = trip.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = trip.dateRangeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.travel_trip_binder_upcoming),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Route summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DXB ➔ JFK",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Emirates EK 203 • Seat 14A",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onShowGatePass,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.travel_gate_pass_action),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacesAndMapsSection(places: List<TripPlaceItem>) {
    val context = LocalContext.current

    fun openInGoogleMaps(place: TripPlaceItem) {
        val geoUri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${Uri.encode("${place.name}, ${place.address}")}")
        val mapIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }
        try {
            context.startActivity(mapIntent)
        } catch (_: Exception) {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("${place.name} ${place.address}")}")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.travel_places_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(places, key = { it.id }) { place ->
                Card(
                    modifier = Modifier.width(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = place.category.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "~${place.etaMinutes} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = place.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = place.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Button(
                            onClick = { openInGoogleMaps(place) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Directions,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.travel_places_navigate),
                                style = MaterialTheme.typography.labelSmall,
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
private fun OfflineChecklistSection() {
    var passportChecked by remember { mutableStateOf(true) }
    var boardingChecked by remember { mutableStateOf(true) }
    var hotelChecked by remember { mutableStateOf(true) }
    var insuranceChecked by remember { mutableStateOf(true) }
    var roamingChecked by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Checklist,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.travel_checklist_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            ChecklistItemRow(
                title = stringResource(R.string.travel_checklist_passport),
                isChecked = passportChecked,
                onToggle = { passportChecked = !passportChecked }
            )
            ChecklistItemRow(
                title = stringResource(R.string.travel_checklist_boarding),
                isChecked = boardingChecked,
                onToggle = { boardingChecked = !boardingChecked }
            )
            ChecklistItemRow(
                title = stringResource(R.string.travel_checklist_hotel),
                isChecked = hotelChecked,
                onToggle = { hotelChecked = !hotelChecked }
            )
            ChecklistItemRow(
                title = stringResource(R.string.travel_checklist_insurance),
                isChecked = insuranceChecked,
                onToggle = { insuranceChecked = !insuranceChecked }
            )
            ChecklistItemRow(
                title = stringResource(R.string.travel_checklist_roaming),
                isChecked = roamingChecked,
                onToggle = { roamingChecked = !roamingChecked }
            )
        }
    }
}

@Composable
private fun ChecklistItemRow(
    title: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isChecked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
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
                    IconButton(onClick = { if (travelers > 1) travelers-- }) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.travel_splitter_decrease), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Text("$travelers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    IconButton(onClick = { travelers++ }) {
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
private fun TravelItemCard(
    item: VaultItem,
    onClick: () -> Unit,
    onGatePassClick: () -> Unit
) {
    val parsed = item.rawOcrText?.let { PnrParser.parse(it) } ?: TravelParsedInfo()
    val travelInfo = parsed.copy(
        pnr = item.customFields["pnr"] ?: parsed.pnr,
        flightNumber = item.customFields["flight_number"] ?: parsed.flightNumber,
        seat = item.customFields["seat"] ?: parsed.seat,
        gate = item.customFields["gate"] ?: parsed.gate
    )

    val isFlight = travelInfo.flightNumber != null || travelInfo.pnr != null ||
        item.title.contains("flight", ignoreCase = true) ||
        item.effectiveClassification?.name.equals("TICKET", ignoreCase = true)

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

            if (isFlight) {
                OutlinedButton(
                    onClick = onGatePassClick,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.travel_gate_pass_action), style = MaterialTheme.typography.labelSmall)
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

@Composable
private fun LiveDestinationWeatherCard(weather: DestinationWeather) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color(0xFFEA580C),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.travel_weather_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEA580C).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stringResource(R.string.travel_weather_live_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFEA580C),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${weather.temperatureCelsius.toInt()}°C",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
                Column {
                    Text(
                        text = weather.conditionText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Humidity: ${weather.humidity}% • Wind: ${weather.windSpeedKmh.toInt()} km/h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = weather.packingAdvice,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveHolidaysCard(holidays: List<PublicHoliday>) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = Color(0xFF6366F1),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.travel_holidays_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stringResource(R.string.travel_holidays_badge),
                        color = Color(0xFF4F46E5),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            holidays.take(3).forEach { holiday ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = holiday.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (holiday.isLongWeekend) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.travel_holidays_long_weekend),
                                        color = Color(0xFF059669),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "${holiday.dayOfWeek} • ${holiday.date}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    FilledTonalButton(
                        onClick = {
                            SkyscannerDeepLinker.launchEverywhere(context = context, origin = "DXB")
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Flight, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.travel_holidays_getaway_btn),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyscannerFlightDealsCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.travel_skyscanner_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0D9488).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "LIVE FARES",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0D9488),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Button(
                onClick = {
                    SkyscannerDeepLinker.launchFlightSearch(
                        context = context,
                        origin = "DXB",
                        destination = "JFK",
                        departDateIso = "261105",
                        returnDateIso = "261112"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D9488))
            ) {
                Icon(Icons.Default.TrendingDown, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.travel_skyscanner_compare),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    SkyscannerDeepLinker.launchEverywhere(context = context, origin = "DXB")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.travel_skyscanner_everywhere),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
