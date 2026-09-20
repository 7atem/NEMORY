package com.vaultbrain.feature.vault.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vaultbrain.shared.model.ProactiveAction
import com.vaultbrain.shared.model.ProactiveActionResolver
import com.vaultbrain.shared.model.VaultItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DynamicActionRow(
    item: VaultItem,
    onExecuteAction: (ProactiveAction) -> Unit
) {
    val resolvedActions = ProactiveActionResolver.resolve(item).toSet()
    if (resolvedActions.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "AI Recommended Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                resolvedActions.forEach { action ->
                    val (icon, label) = action.displayInfo()
                    ElevatedButton(
                        onClick = { onExecuteAction(action) },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(label)
                    }
                }
            }
        }
    }
}

private fun ProactiveAction.displayInfo(): Pair<ImageVector, String> = when (this) {
    ProactiveAction.COPY_TOTAL -> Icons.Default.ContentCopy to "Copy Total"
    ProactiveAction.ADD_CONTACT -> Icons.Default.PersonAdd to "Add Contact"
    ProactiveAction.FIND_MANUAL -> Icons.Default.Search to "Find Manual"
    ProactiveAction.ADD_TO_CALENDAR -> Icons.Default.Event to "Add to Calendar"
    ProactiveAction.REFILL_REMINDER -> Icons.Default.MedicalServices to "Refill Reminder"
    ProactiveAction.OPEN_SOURCE -> Icons.AutoMirrored.Filled.OpenInNew to "Open Link"
    ProactiveAction.REMIND_LATER -> Icons.Default.Notifications to "Remind Later"
    ProactiveAction.TRACK_PRICE -> Icons.AutoMirrored.Filled.TrendingDown to "Compare Price"
    ProactiveAction.REVIEW_EXPIRY -> Icons.Default.Timer to "Review Expiry"
}
