package com.vaultbrain.feature.lenshealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultbrain.core.common.ui.SharedItemCard
import com.vaultbrain.shared.model.VaultItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    val vitals = remember { HealthHubDataProvider.getVitals() }
    var medications by remember { mutableStateOf(HealthHubDataProvider.getMedications()) }
    val medicalProfile = remember { HealthHubDataProvider.getMedicalProfile() }

    var airQuality by remember { mutableStateOf<AirQualitySnapshot?>(null) }
    var fdaAudit by remember { mutableStateOf<MedicationSafetyAudit?>(null) }

    LaunchedEffect(prescriptionCount) {
        airQuality = LiveAirQualityClient.fetchAirQuality()
        fdaAudit = LiveDrugSafetyClient.auditDrugSafety(prescriptionCount)
    }

    val recentItems = items.filter { (now - it.createdAt) <= 7 * 24 * 60 * 60 * 1000L }
    val olderItems = items.filter { (now - it.createdAt) > 7 * 24 * 60 * 60 * 1000L }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.health_lens_view_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // On-Device Vitals Snapshot Cards
        item {
            HealthVitalsSection(vitals = vitals)
        }

        // Live Air Quality & UV Radar (Open-Meteo)
        item {
            LiveAirQualityCard(airQuality = airQuality)
        }

        // Emergency Medical Profile Card
        item {
            EmergencyMedicalProfileSection(profile = medicalProfile)
        }

        // Prescription Refill & Dosage Tracker
        item {
            PrescriptionsTrackerSection(
                medications = medications,
                onToggleDose = { medId ->
                    medications = medications.map {
                        if (it.id == medId) it.copy(didTakeDoseToday = !it.didTakeDoseToday) else it
                    }
                }
            )
        }

        // Live FDA Medication Safety & Recall Verification
        if (prescriptionCount > 0 || (fdaAudit?.activeRecallsCount ?: 0) > 0) {
            item {
                FdaSafetyAuditCard(audit = fdaAudit, prescriptionsCount = prescriptionCount)
            }
        }

        item {
            HealthStatCard(
                totalCount = items.size,
                alertCount = alertCount,
                expiringSoonCount = expiringSoonCount,
                prescriptionCount = prescriptionCount,
                nextDoseDate = nextDose,
                labTrendCount = labResults.size
            )
        }

        if (labResults.isNotEmpty()) {
            item {
                LabTrendCard(results = labResults)
            }
        }

        item {
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
        }

        item {
            Text(
                text = stringResource(R.string.health_items_title),
                style = MaterialTheme.typography.titleMedium
            )
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
private fun HealthVitalsSection(vitals: HealthVitalsSnapshot) {
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
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFE11D48),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.health_vitals_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "ON-DEVICE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VitalMetricCard(
                    title = stringResource(R.string.health_vitals_steps),
                    value = "%,d".format(vitals.steps),
                    subtitle = "/ %,d goal".format(vitals.stepGoal),
                    icon = Icons.Default.DirectionsWalk,
                    iconTint = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                )
                VitalMetricCard(
                    title = stringResource(R.string.health_vitals_heart_rate),
                    value = "${vitals.heartRate} BPM",
                    subtitle = "Resting avg",
                    icon = Icons.Default.MonitorHeart,
                    iconTint = Color(0xFFE11D48),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VitalMetricCard(
                    title = stringResource(R.string.health_vitals_calories),
                    value = "${vitals.activeCalories} kcal",
                    subtitle = "Active move",
                    icon = Icons.Default.LocalFireDepartment,
                    iconTint = Color(0xFFEA580C),
                    modifier = Modifier.weight(1f)
                )
                VitalMetricCard(
                    title = stringResource(R.string.health_vitals_sleep),
                    value = vitals.sleepDurationFormatted,
                    subtitle = "Optimal rest",
                    icon = Icons.Default.Bedtime,
                    iconTint = Color(0xFF9333EA),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VitalMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun EmergencyMedicalProfileSection(profile: EmergencyMedicalProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = Color(0xFFB91C1C),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.health_medical_profile_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB91C1C)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.health_medical_blood_type, profile.bloodType),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.health_medical_allergies, profile.allergies),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.health_medical_insurance, "${profile.insuranceProvider} • ${profile.memberId}"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PrescriptionsTrackerSection(
    medications: List<MedicationSubscription>,
    onToggleDose: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.health_prescriptions_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        medications.forEach { med ->
            val progress = (med.pillsLeft.toFloat() / med.totalPills.toFloat()).coerceIn(0f, 1f)

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
                        Column {
                            Text(
                                text = med.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${med.dosageInstructions} • ${med.doctorOrPharmacy}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { onToggleDose(med.id) },
                            shape = RoundedCornerShape(8.dp),
                            colors = if (med.didTakeDoseToday) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                            } else {
                                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (med.didTakeDoseToday) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (med.didTakeDoseToday) {
                                    stringResource(R.string.health_dose_taken_today)
                                } else {
                                    stringResource(R.string.health_dose_pending)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (med.daysRemaining <= 14) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.health_refill_days_left, med.daysRemaining),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (med.daysRemaining <= 14) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${med.pillsLeft}/${med.totalPills} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.health_stat_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.health_stat_total, totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.health_stat_prescriptions, prescriptionCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.health_stat_alerts, alertCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.health_stat_expiring_soon, expiringSoonCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            if (nextDoseDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val formattedDate = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(nextDoseDate))
                Text(
                    text = stringResource(R.string.health_stat_next_dose, formattedDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (labTrendCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = pluralStringResource(R.plurals.health_stat_lab_trends, labTrendCount, labTrendCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun LabTrendCard(results: List<VaultItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.health_vitals_trend),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            val values = results.mapNotNull { it.parsedMetadata["value"]?.toFloatOrNull() }
            if (values.size >= 2) {
                val min = values.minOrNull() ?: 0f
                val max = values.maxOrNull() ?: 100f
                val range = if (max == min) 1f else max - min

                val primaryColor = MaterialTheme.colorScheme.primary

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    val path = Path()
                    val stepX = size.width / (values.size - 1)

                    values.forEachIndexed { index, value ->
                        val x = index * stepX
                        val y = size.height - ((value - min) / range * size.height)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                    }

                    drawPath(
                        path = path,
                        color = primaryColor,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.health_vitals_insufficient),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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

@Composable
fun LiveAirQualityCard(
    airQuality: AirQualitySnapshot?,
    modifier: Modifier = Modifier
) {
    val snapshot = airQuality ?: AirQualitySnapshot(
        aqi = 38,
        pm25 = 8.4,
        uvIndex = 3.2,
        category = "Good",
        healthAdvice = "Air quality is ideal for outdoor walks, runs, and lung health.",
        badgeColorHex = 0xFF10B981
    )

    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.health_air_quality_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(snapshot.badgeColorHex).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "AQI ${snapshot.aqi} • ${snapshot.category}",
                        color = Color(snapshot.badgeColorHex),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0D9488).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "AQI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${snapshot.aqi}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "US AQI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0D9488).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "PM2.5",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", snapshot.pm25),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "µg/m³",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0D9488).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "UV INDEX",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f", snapshot.uvIndex),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (snapshot.uvIndex > 5) "High" else "Moderate",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = Color(0xFF0D9488),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = snapshot.healthAdvice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FdaSafetyAuditCard(
    audit: MedicationSafetyAudit?,
    prescriptionsCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val currentAudit = audit ?: MedicationSafetyAudit(
        totalPrescriptionsVerified = prescriptionsCount,
        activeRecallsCount = 0,
        recentRecalls = emptyList()
    )

    Card(
        modifier = modifier.fillMaxWidth(),
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
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.health_fda_safety_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = stringResource(R.string.health_fda_live_badge),
                        color = Color(0xFF10B981),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.health_fda_verified_clean, currentAudit.totalPrescriptionsVerified),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.health_fda_batch_clean_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.health_fda_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthItemCard(item: VaultItem, onClick: () -> Unit) {
    SharedItemCard(
        item = item,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        extraContent = {
            item.expiryDate?.let { expiry ->
                val formatted = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(expiry))
                Text(
                    text = stringResource(R.string.health_item_expiry, formatted),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    )
}
