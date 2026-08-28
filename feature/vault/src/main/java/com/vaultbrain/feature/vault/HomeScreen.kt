package com.vaultbrain.feature.vault

import androidx.compose.ui.res.stringResource

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.feature.briefing.MorningBriefingCard
import com.vaultbrain.feature.vault.components.EmptyState
import com.vaultbrain.feature.vault.components.PreviewData
import com.vaultbrain.feature.vault.components.VaultItemCard
import com.vaultbrain.feature.voice.OnDeviceVoiceCaptureDialog

import com.vaultbrain.core.common.model.VaultItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSearchClick: (String?) -> Unit,
    onFilterClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryImagesPicked: (List<String>) -> Unit,
    onTextPasted: (String) -> Unit,
    onVoiceCaptured: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val gemmaBanner by viewModel.gemmaBanner.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPasteDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceDialog by rememberSaveable { mutableStateOf(false) }
    val archivedMessage = stringResource(R.string.home_item_archived)
    val undoLabel = stringResource(R.string.home_undo)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) onGalleryImagesPicked(uris.map(android.net.Uri::toString))
    }
    // UI reacts immediately to database state; Room Flows auto-update.

    HomeContent(
        state = state,
        onSearchClick = onSearchClick,
        onFilterClick = onFilterClick,
        onCollectionsClick = onCollectionsClick,
        onCollectionClick = onCollectionClick,
        onItemClick = onItemClick,
        onCaptureClick = onCaptureClick,
        onGalleryClick = { galleryLauncher.launch(arrayOf("image/*", "application/pdf")) },
        onPasteClick = {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val textToPaste = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (!textToPaste.isNullOrBlank()) {
                onTextPasted(textToPaste)
            } else {
                showPasteDialog = true
            }
        },
        onVoiceClick = {
            showVoiceDialog = true
        },
        onSettingsClick = onSettingsClick,
        snackbarHostState = snackbarHostState,
        onArchiveItem = { item ->
            viewModel.archiveItem(item)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = archivedMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.restoreItem(item)
            }
        },
        briefingCard = { MorningBriefingCard(onInsightClick = onItemClick) },
        gemmaBanner = gemmaBanner,
        onGemmaDownloadClick = viewModel::downloadGemmaModel,
        onGemmaBannerDismiss = viewModel::dismissGemmaBanner
    )

    if (showPasteDialog) {
        PasteTextDialog(
            onDismiss = { showPasteDialog = false },
            onContinue = { text ->
                showPasteDialog = false
                onTextPasted(text)
            }
        )
    }

    if (showVoiceDialog) {
        OnDeviceVoiceCaptureDialog(
            onDismiss = { showVoiceDialog = false },
            onCaptured = { text ->
                showVoiceDialog = false
                onVoiceCaptured(text)
            },
            onManualEntry = {
                showVoiceDialog = false
                showPasteDialog = true
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    onSearchClick: (String?) -> Unit,
    onFilterClick: (String) -> Unit,
    onCollectionsClick: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onPasteClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onArchiveItem: (VaultItem) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    briefingCard: @Composable () -> Unit = { MorningBriefingCard() },
    gemmaBanner: GemmaBanner? = null,
    onGemmaDownloadClick: () -> Unit = {},
    onGemmaBannerDismiss: () -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = stringResource(R.string.vault_home_title), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.home_action_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    briefingCard()
                }

                if (gemmaBanner != null) {
                    item {
                        GemmaDownloadBanner(
                            banner = gemmaBanner,
                            onDownloadClick = onGemmaDownloadClick,
                            onDismiss = onGemmaBannerDismiss
                        )
                    }
                }

                item {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = {
                                    searchActive = false
                                    onSearchClick(searchQuery.takeIf(String::isNotBlank))
                                },
                                expanded = searchActive,
                                onExpandedChange = { searchActive = it },
                                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                trailingIcon = {
                                    if (searchActive) {
                                        IconButton(onClick = {
                                            if (searchQuery.isNotEmpty()) {
                                                searchQuery = ""
                                            } else {
                                                searchActive = false
                                            }
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close search")
                                        }
                                    }
                                }
                            )
                        },
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val filteredItems = if (searchQuery.isEmpty()) {
                            state.recentItems
                        } else {
                            state.activeItems.filter {
                                it.title.contains(searchQuery, ignoreCase = true) ||
                                (it.summary ?: "").contains(searchQuery, ignoreCase = true)
                            }
                        }
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                item {
                                    Text("Recent", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                                }
                            }
                            items(filteredItems, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            searchActive = false
                                            onItemClick(item.id) 
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (searchQuery.isEmpty()) Icons.Default.History else Icons.Default.Description, 
                                        contentDescription = null, 
                                        modifier = Modifier.padding(end = 16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Column {
                                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                        if (searchQuery.isNotEmpty()) {
                                            Text(item.summary ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    QuickAddCard(
                        onScanClick = onCaptureClick,
                        onImportClick = onGalleryClick,
                        onPasteClick = onPasteClick,
                        onVoiceClick = onVoiceClick
                    )
                }

                if (state.quickCaptureSuggestion != null) {
                    item {
                        Card(
                            onClick = onCaptureClick,
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Column {
                                    Text(
                                        text = state.quickCaptureSuggestion.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = state.quickCaptureSuggestion.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    VaultOverviewCard(
                        itemCount = state.activeItems.size,
                        reviewCount = state.needsReviewCount,
                        expiringCount = state.expiringSoonCount,
                        onAllClick = { onSearchClick(null) },
                        onReviewClick = { onFilterClick("needs_review") },
                        onExpiringClick = { onFilterClick("expiring") }
                    )
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.home_recently_dumped),
                        actionLabel = if (state.recentItems.isNotEmpty()) stringResource(R.string.home_see_all) else null,
                        onAction = { onSearchClick(null) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.recentItems.isEmpty()) {
                        EmptyState(
                            text = stringResource(R.string.home_empty_recent),
                            illustration = com.vaultbrain.core.common.R.drawable.il_empty_vault,
                            actionLabel = stringResource(R.string.home_scan_first_document),
                            onAction = onCaptureClick
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(state.recentItems, key = { _, it -> it.id }) { index, item ->
                                val itemAlpha by animateFloatAsState(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 500, delayMillis = index * 60),
                                    label = "itemAlpha"
                                )
                                VaultItemCard(
                                    item = item,
                                    onClick = { onItemClick(item.id) },
                                    onArchive = { onArchiveItem(item) },
                                    modifier = Modifier.width(280.dp).graphicsLayer { alpha = itemAlpha }
                                )
                            }
                        }
                    }
                }

                item {
                    SectionHeader(
                        title = stringResource(R.string.home_your_collections),
                        actionLabel = stringResource(R.string.home_see_all),
                        onAction = onCollectionsClick
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (state.collections.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = onCollectionsClick),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.home_collections_empty_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    stringResource(R.string.home_collections_empty_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                TextButton(onClick = onCollectionsClick) {
                                    Text(stringResource(R.string.home_create_first_collection))
                                }
                            }
                        }
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.collections, key = { it.id }) { collection ->
                                CollectionCard(
                                    collection = collection,
                                    onClick = { onCollectionClick(collection.id) },
                                    modifier = Modifier.width(280.dp)
                                )
                            }
                        }
                    }
                }
            }
            
        }
    }

}

@Composable
private fun QuickAddCard(
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    onPasteClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_quick_add), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.home_quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAction(
                    label = stringResource(R.string.home_action_scan),
                    icon = Icons.Default.DocumentScanner,
                    primary = true,
                    onClick = onScanClick,
                    modifier = Modifier.weight(1f)
                )
                QuickAction(
                    label = stringResource(R.string.home_action_import),
                    icon = Icons.Default.PhotoLibrary,
                    onClick = onImportClick,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(
                    label = stringResource(R.string.home_action_paste),
                    icon = Icons.Default.ContentPaste,
                    onClick = onPasteClick,
                    modifier = Modifier.weight(1f)
                )
                QuickAction(
                    label = "Voice",
                    icon = Icons.Default.Mic,
                    onClick = onVoiceClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier.height(76.dp).clickable {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = MaterialTheme.shapes.large,
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun VaultOverviewCard(
    itemCount: Int,
    reviewCount: Int,
    expiringCount: Int,
    onAllClick: () -> Unit,
    onReviewClick: () -> Unit,
    onExpiringClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.home_overview))
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverviewStat(itemCount, stringResource(R.string.home_overview_items), Icons.Default.Inventory2, onAllClick, Modifier.weight(1f))
                VerticalDivider(Modifier.height(52.dp))
                OverviewStat(
                    reviewCount,
                    stringResource(R.string.home_overview_review),
                    Icons.AutoMirrored.Filled.FactCheck,
                    onReviewClick,
                    Modifier.weight(1f)
                )
                VerticalDivider(Modifier.height(52.dp))
                OverviewStat(expiringCount, stringResource(R.string.home_overview_expiring), Icons.Default.EventBusy, onExpiringClick, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewStat(
    count: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(count.toString(), style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasteTextDialog(onDismiss: () -> Unit, onContinue: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
        title = { Text(stringResource(R.string.home_paste_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.home_paste_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.home_paste_placeholder)) },
                    minLines = 5,
                    maxLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onContinue(text.trim()) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.home_continue))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) } }
    )
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium
    )
}

/** Dismissible Today-tab banner offering the on-device model download. */
@Composable
private fun GemmaDownloadBanner(
    banner: GemmaBanner,
    onDownloadClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.gemma_banner_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (banner.progress == null && !banner.queued) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.gemma_banner_dismiss),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = stringResource(
                    when {
                        banner.queued -> R.string.gemma_banner_queued
                        banner.progress != null -> R.string.gemma_banner_downloading
                        else -> R.string.gemma_banner_body
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (banner.queued) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            } else if (banner.progress != null) {
                LinearProgressIndicator(
                    progress = { banner.progress / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
            } else {
                TextButton(onClick = onDownloadClick, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.gemma_banner_download))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String?, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionTitle(title)
        if (actionLabel != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    MaterialTheme {
        HomeContent(
            state = HomeUiState(
                recentItems = PreviewData.sampleItems,
                activeItems = PreviewData.sampleItems,
                collections = listOf(
                    com.vaultbrain.core.common.model.PersonalCollection(
                        id = "collection",
                        name = "My Italy Adventure",
                        createdAt = 1L,
                        updatedAt = 1L,
                        isPinned = true,
                        itemCount = 3
                    )
                )
            ),
            onSearchClick = { _ -> },
            onFilterClick = {},
            onCollectionsClick = {},
            onCollectionClick = {},
            onItemClick = {},
            onArchiveItem = {},
            onCaptureClick = {},
            onGalleryClick = {},
            onPasteClick = {},
            onVoiceClick = {},
            onSettingsClick = {},
            briefingCard = {}
        )
    }
}
