package com.vaultbrain.feature.lenstravel

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun GatePassDialog(
    flightItem: TripDocItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Elevated screen brightness for physical airport optical scanners
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalBrightness = activity?.window?.attributes?.screenBrightness
        activity?.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1.0f
            window.attributes = layoutParams
        }

        onDispose {
            activity?.window?.let { window ->
                val layoutParams = window.attributes
                layoutParams.screenBrightness = originalBrightness ?: -1f
                window.attributes = layoutParams
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top status bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.gate_pass_brightness_notice),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.gate_pass_close),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Boarding Gate Pass Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Header airline bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFD32F2F))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
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
                                        imageVector = Icons.Default.Flight,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.gate_pass_airline),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 1.2.sp
                                    )
                                }
                                Text(
                                    text = flightItem.refCode ?: "EK 203",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Airport Origin -> Destination Route
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = flightItem.originIata ?: "DXB",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "DUBAI INTL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "13h 45m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = flightItem.destinationIata ?: "JFK",
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "NEW YORK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                        // Passenger & Seat Specs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.gate_pass_passenger_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = flightItem.passengerName ?: "EL SAYED / HATEM",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.gate_pass_gate_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = flightItem.gate ?: "B22",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFD32F2F)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.gate_pass_seat_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = flightItem.seat ?: "14A",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }

                        // Terminal, Group, Boarding Time
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF8FAFC))
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.gate_pass_terminal_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = flightItem.terminal ?: "3",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.gate_pass_boarding_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "07:45 AM",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFFD32F2F)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.gate_pass_group_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = flightItem.boardingGroup ?: "A",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        // Scannable Barcode Pattern Area
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                                .padding(vertical = 20.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Canvas barcode visualizer
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White)
                            ) {
                                val barWidths = floatArrayOf(
                                    3f, 1f, 2f, 4f, 1f, 3f, 2f, 1f, 4f, 2f,
                                    1f, 3f, 1f, 2f, 4f, 3f, 1f, 2f, 3f, 1f,
                                    4f, 2f, 1f, 3f, 2f, 4f, 1f, 2f, 3f, 1f,
                                    2f, 4f, 1f, 3f, 2f, 1f, 4f, 3f, 2f, 1f,
                                    3f, 1f, 4f, 2f, 1f, 3f, 2f, 4f, 1f, 2f,
                                    4f, 1f, 3f, 2f, 1f, 4f, 2f, 3f, 1f, 2f
                                )
                                val totalUnits = barWidths.sum() + barWidths.size * 2f
                                val unitScale = size.width / totalUnits
                                var currentX = 0f

                                for (i in barWidths.indices) {
                                    val barWidth = barWidths[i] * unitScale
                                    drawRect(
                                        color = Color.Black,
                                        topLeft = Offset(currentX, 0f),
                                        size = Size(barWidth, size.height)
                                    )
                                    currentX += barWidth + (2f * unitScale)
                                }
                            }

                            Text(
                                text = "ETKT: 1762849102847 • SEC: 041 • FAST-TRACK",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = Color.Black.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
