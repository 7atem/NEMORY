package com.vaultbrain.feature.briefing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaultbrain.feature.briefing.model.EventType
import com.vaultbrain.feature.briefing.model.WeekAheadEvent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.vaultbrain.feature.briefing.R

@Composable
fun WeekAheadTimeline(
    events: List<WeekAheadEvent>,
    onEventClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Your Week Ahead",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(events, key = { it.id }) { event ->
                EventCard(event = event, onClick = { onEventClick(event.itemId) })
            }
        }
    }
}

@Composable
private fun EventCard(event: WeekAheadEvent, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = event.type.icon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Text(
                    text = event.dateFormatted(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
        }
    }
}

private fun EventType.icon(): ImageVector = when (this) {
    EventType.BILL -> Icons.Default.Payment
    EventType.EXPIRY -> Icons.Default.Warning // Should use EventBusy or Warning
    EventType.TRAVEL -> Icons.Default.FlightTakeoff
    EventType.APPOINTMENT -> Icons.Default.Schedule
}

private fun WeekAheadEvent.dateFormatted(): String {
    val date = Instant.ofEpochMilli(this.date).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    
    return when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}
