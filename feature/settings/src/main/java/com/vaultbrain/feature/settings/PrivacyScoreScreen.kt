package com.vaultbrain.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vaultbrain.core.common.ui.ResponsiveContainer
import com.vaultbrain.core.common.ui.SectionHeading
import com.vaultbrain.core.common.ui.VaultSpacing

/** Explains configured protection boundaries without claiming measured network traffic. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScoreScreen(onBack: () -> Unit) {
    val items = listOf(
        Triple(Icons.Default.AutoAwesome, R.string.privacy_card_ai_title, R.string.privacy_local_ai),
        Triple(Icons.Default.Lock, R.string.privacy_card_storage_title, R.string.privacy_storage),
        Triple(Icons.Default.CloudOff, R.string.privacy_card_network_title, R.string.privacy_network),
        Triple(Icons.Default.CalendarMonth, R.string.privacy_card_calendar_title, R.string.privacy_calendar),
        Triple(Icons.Default.MailOutline, R.string.privacy_card_gmail_title, R.string.settings_gmail_unavailable)
    )
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.privacy_overview_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.privacy_back)) }
        })
    }) { padding ->
        ResponsiveContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier.padding(VaultSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(VaultSpacing.md)
            ) {
                items.forEach { (icon, titleRes, bodyRes) ->
                    PrivacyBoundaryCard(
                        icon = icon,
                        title = stringResource(titleRes),
                        body = stringResource(bodyRes)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyBoundaryCard(
    icon: ImageVector,
    title: String,
    body: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(VaultSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(VaultSpacing.sm)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                SectionHeading(text = title)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
