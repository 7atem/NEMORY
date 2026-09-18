package com.vaultbrain.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.feature.vault.components.EmptyState
import com.vaultbrain.feature.vault.components.PreviewData
import com.vaultbrain.feature.vault.components.VaultItemCard
import com.vaultbrain.feature.vault.components.lensContainerColor
import com.vaultbrain.feature.vault.components.lensTitle

@Composable
fun LensDetailScreen(
    lensId: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    viewModel: LensDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(lensId) {
        viewModel.loadLens(lensId)
    }
    val state by viewModel.uiState.collectAsState()
    LensDetailContent(
        state = state,
        onBack = onBack,
        onItemClick = onItemClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LensDetailContent(
    state: LensDetailUiState,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit
) {
    val accentColor = lensContainerColor(state.lensId)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accentColor
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatsRow(
                    total = state.totalCount,
                    expiring = state.expiringCount,
                    moneySaved = state.moneySaved,
                    showMoneySaved = state.lensId == LensId.MONEY
                )
            }

            if (state.items.isEmpty()) {
                val (emptyText, emptyAction) = lensEmptyState(state.lensId)
                item {
                    EmptyState(
                        text = emptyText,
                        illustration = com.vaultbrain.core.common.R.drawable.il_empty_lens,
                        actionLabel = emptyAction
                    )
                }
            } else {
                items(state.items, key = { it.id }) { item ->
                    VaultItemCard(
                        item = item,
                        onClick = { onItemClick(item.id) }
                    )
                }
            }
        }
    }
}

/** Returns a (message, actionLabel?) pair for the empty state of each lens. */
private fun lensEmptyState(lensId: String): Pair<String, String?> = when (lensId) {
    LensId.MONEY -> "Drop a receipt or invoice here.\nNemory tracks your spending privately." to "Capture a Receipt"
    LensId.HEALTH -> "Add a prescription, vaccination record, or medical report.\nNemory keeps your health history secure." to "Capture a Document"
    LensId.TRAVEL -> "Add a boarding pass and Nemory will remind you before check-in." to "Capture a Boarding Pass"
    LensId.BUREAUCRACY -> "Store IDs, contracts, or official letters.\nNemory alerts you before they expire." to "Capture a Document"
    LensId.MEDIA -> "Save books, films, or articles for later.\nNemory will remind you when you\'re ready." to "Capture a Watch/Read Item"
    else -> "Nothing here yet. Tap the + button to start capturing." to null
}

@Composable
private fun StatsRow(total: Int, expiring: Int, moneySaved: Double, showMoneySaved: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showMoneySaved) {
            StatCard(label = "Saved", value = "$${moneySaved.toInt()}", modifier = Modifier.weight(1f))
        }
        StatCard(label = "Total", value = total.toString(), modifier = Modifier.weight(1f))
        StatCard(label = "Expiring", value = expiring.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LensDetailContentPreview() {
    MaterialTheme {
        LensDetailContent(
            state = LensDetailUiState(
                lensId = LensId.MONEY,
                title = lensTitle(LensId.MONEY),
                items = PreviewData.sampleItems.filter { LensId.MONEY in it.lensTags },
                totalCount = 2,
                expiringCount = 1
            ),
            onBack = {},
            onItemClick = {}
        )
    }
}
