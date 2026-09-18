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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.vaultbrain.feature.vault.home.AttentionItem
import com.vaultbrain.feature.voice.OnDeviceVoiceCaptureDialog

import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.integrations.calendar.CalendarEventDraft
import com.vaultbrain.core.integrations.calendar.CalendarIntentFactory
import com.vaultbrain.core.integrations.model.ExternalRecord
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
    onAskBrain: (String) -> Unit = {},
    onLensClick: (String) -> Unit = {},
    windowWidthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass = androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dailyInsights by viewModel.dailyInsights.collectAsStateWithLifecycle()
    val gemmaBanner by viewModel.gemmaBanner.collectAsStateWithLifecycle()
    val backupNudgeVisible by viewModel.backupNudgeVisible.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPasteDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceDialog by rememberSaveable { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<VaultReminder?>(null) }
    var showReminderDialog by rememberSaveable { mutableStateOf(false) }
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
        windowWidthSizeClass = windowWidthSizeClass,
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
        dailyInsights = dailyInsights,
        onDismissInsight = viewModel::dismissDailyInsight,
        briefingCard = { MorningBriefingCard(onInsightClick = onItemClick) },
        gemmaBanner = gemmaBanner,
        onGemmaDownloadClick = viewModel::downloadGemmaModel,
        onGemmaBannerDismiss = viewModel::dismissGemmaBanner,
        backupNudgeVisible = backupNudgeVisible,
        onBackupNudgeDismiss = viewModel::dismissBackupNudge,
        onAskBrain = onAskBrain,
        onAttentionClick = { attention ->
            when (attention.kind) {
                com.vaultbrain.feature.vault.home.AttentionItem.Kind.REMINDER ->
                    state.todayReminders.find { it.id == attention.targetId }?.let {
                        reminderToEdit = it
                        showReminderDialog = true
                    }
                com.vaultbrain.feature.vault.home.AttentionItem.Kind.EVENT ->
                    state.todayEvents.find { it.externalId == attention.targetId }?.let { record ->
                        record.deepLinkUri?.let { uri ->
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                            }
                        }
                    }
                com.vaultbrain.feature.vault.home.AttentionItem.Kind.EXPIRING ->
                    attention.targetId?.let(onItemClick)
                com.vaultbrain.feature.vault.home.AttentionItem.Kind.REVIEW ->
                    onFilterClick("needs_review")
                com.vaultbrain.feature.vault.home.AttentionItem.Kind.GMAIL ->
                    attention.targetId?.let { id -> onFilterClick("gmail:$id") }
                com.vaultbrain.feature.vault.home.AttentionItem.Kind.PROACTIVE_ACTION ->
                    attention.targetId?.let(onItemClick)
            }
        },
        onOpenEvent = { record ->
            record.deepLinkUri?.let { uri ->
                runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri)))
                }
            }
        },
        onAddCalendarEvent = {
            runCatching {
                context.startActivity(
                    CalendarIntentFactory.createInsertIntent(
                        CalendarEventDraft(title = context.getString(R.string.home_new_calendar_event))
                    )
                )
            }
        },
        onAddReminder = {
            reminderToEdit = null
            showReminderDialog = true
        },
        onEditReminder = {
            reminderToEdit = it
            showReminderDialog = true
        },
        onSnoozeReminder = { viewModel.snoozeReminder(it.id) },
        onCompleteReminder = { viewModel.completeReminder(it.id) }
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

    if (showReminderDialog) {
        ReminderEditorDialog(
            reminder = reminderToEdit,
            onDismiss = { showReminderDialog = false },
            onSave = { title, dueAt ->
                reminderToEdit?.let { viewModel.editReminderDue(it.id, dueAt) }
                    ?: viewModel.createReminder(title, dueAt)
                showReminderDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun HomeContent(
    state: HomeUiState,
    windowWidthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass = androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Compact,
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
    dailyInsights: List<com.vaultbrain.core.ai.rag.DailyInsight> = emptyList(),
    onDismissInsight: (com.vaultbrain.core.ai.rag.DailyInsight) -> Unit = {},
    briefingCard: @Composable () -> Unit = { MorningBriefingCard() },
    gemmaBanner: GemmaBanner? = null,
    onGemmaDownloadClick: () -> Unit = {},
    onGemmaBannerDismiss: () -> Unit = {},
    onOpenEvent: (ExternalRecord) -> Unit = {},
    onAddCalendarEvent: () -> Unit = {},
    onAddReminder: () -> Unit = {},
    onEditReminder: (VaultReminder) -> Unit = {},
    onSnoozeReminder: (VaultReminder) -> Unit = {},
    onCompleteReminder: (VaultReminder) -> Unit = {},
    backupNudgeVisible: Boolean = true,
    onBackupNudgeDismiss: () -> Unit = {},
    onAskBrain: (String) -> Unit = {},
    onAttentionClick: (AttentionItem) -> Unit = {}
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
                        Column {
                            Text(text = greetingText(), style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL)
                                    .format(java.util.Date()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .widthIn(max = 1000.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
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

                if (state.attentionItems.isNotEmpty()) {
                    item {
                        AttentionSection(
                            items = state.attentionItems.take(3),
                            needsReviewCount = state.needsReviewCount,
                            onClick = onAttentionClick
                        )
                    }
                }

                if (dailyInsights.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.home_proactive_insights_title), style = MaterialTheme.typography.titleMedium)
                            dailyInsights.forEach { insight ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (state.activeItems.any { it.id == insight.itemId }) onItemClick(insight.itemId)
                                        else onAskBrain(insight.title)
                                    },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = insight.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            IconButton(onClick = { onDismissInsight(insight) }) {
                                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_dismiss_insight))
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = insight.evidence,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
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
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.home_search_close))
                                        }
                                    }
                                }
                            )
                        },
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val filteredItems = remember(searchQuery, state.recentItems, state.activeItems) {
                            if (searchQuery.isEmpty()) {
                                state.recentItems
                            } else {
                                state.activeItems.filter {
                                    it.title.contains(searchQuery, ignoreCase = true) ||
                                    (it.summary ?: "").contains(searchQuery, ignoreCase = true)
                                }
                            }
                        }
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (searchQuery.isEmpty()) {
                                item {
                                    Text(stringResource(R.string.home_search_recent), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
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
                    AskComposerCard(
                        onAsk = onAskBrain,
                        onScanClick = onCaptureClick,
                        onImportClick = onGalleryClick,
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
                    briefingCard()
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

                if (backupNudgeVisible) {
                    item {
                        BackupNudgeCard(
                            onAction = onSettingsClick,
                            onDismiss = onBackupNudgeDismiss
                        )
                    }
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
                    TemporalContextCard(
                        state = state,
                        onOpenEvent = onOpenEvent,
                        onAddCalendarEvent = onAddCalendarEvent,
                        onAddReminder = onAddReminder,
                        onEditReminder = onEditReminder,
                        onSnoozeReminder = onSnoozeReminder,
                        onCompleteReminder = onCompleteReminder
                    )
                }
            }
            
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

@Composable
private fun BackupNudgeCard(onAction: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onAction),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_backup_nudge_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = stringResource(R.string.home_backup_nudge_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.home_backup_nudge_setup),
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_backup_nudge_dismiss),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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

@Composable
private fun TemporalContextCard(
    state: HomeUiState,
    onOpenEvent: (ExternalRecord) -> Unit,
    onAddCalendarEvent: () -> Unit,
    onAddReminder: () -> Unit,
    onEditReminder: (VaultReminder) -> Unit,
    onSnoozeReminder: (VaultReminder) -> Unit,
    onCompleteReminder: (VaultReminder) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Today, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_today), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onAddReminder) {
                    Icon(Icons.Default.AlarmAdd, contentDescription = stringResource(R.string.home_add_reminder))
                }
                IconButton(onClick = onAddCalendarEvent) {
                    Icon(Icons.Default.EventAvailable, contentDescription = stringResource(R.string.home_add_calendar_event))
                }
            }
            if (state.todayEvents.isEmpty() && state.todayReminders.isEmpty()) {
                Text(
                    stringResource(R.string.home_today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.todayEvents.forEach { CalendarContextRow(it, onOpenEvent) }
                state.todayReminders.forEach {
                    ReminderContextRow(it, onEditReminder, onSnoozeReminder, onCompleteReminder)
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.home_upcoming), style = MaterialTheme.typography.titleSmall)
            if (state.upcomingEvents.isEmpty() && state.upcomingReminders.isEmpty()) {
                Text(
                    stringResource(R.string.home_upcoming_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                state.upcomingEvents.take(5).forEach { CalendarContextRow(it, onOpenEvent) }
                state.upcomingReminders.take(5).forEach {
                    ReminderContextRow(it, onEditReminder, onSnoozeReminder, onCompleteReminder)
                }
            }
        }
    }
}

@Composable
private fun CalendarContextRow(record: ExternalRecord, onOpen: (ExternalRecord) -> Unit) {
    ListItem(
        overlineContent = { Text(stringResource(R.string.home_from_calendar), color = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(record.title ?: stringResource(R.string.home_calendar_event)) },
        supportingContent = { Text(formatContextTime(record.startAt)) },
        leadingContent = { Icon(Icons.Default.Event, contentDescription = null) },
        modifier = Modifier.clickable { onOpen(record) }
    )
}


@Composable
private fun ReminderContextRow(
    reminder: VaultReminder,
    onEdit: (VaultReminder) -> Unit,
    onSnooze: (VaultReminder) -> Unit,
    onComplete: (VaultReminder) -> Unit
) {
    ListItem(
        overlineContent = { Text(stringResource(R.string.home_vault_reminder_overline), color = MaterialTheme.colorScheme.tertiary) },
        headlineContent = { Text(reminder.title) },
        supportingContent = { Text(formatContextTime(reminder.dueAt)) },
        leadingContent = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
        trailingContent = {
            Row {
                IconButton(onClick = { onEdit(reminder) }) {
                    Icon(Icons.Default.EditCalendar, contentDescription = stringResource(R.string.home_edit_reminder))
                }
                IconButton(onClick = { onSnooze(reminder) }) {
                    Icon(Icons.Default.Snooze, contentDescription = stringResource(R.string.home_snooze_reminder))
                }
                IconButton(onClick = { onComplete(reminder) }) {
                    Icon(Icons.Default.Check, contentDescription = stringResource(R.string.home_complete_reminder))
                }
            }
        }
    )
}

@Composable
private fun ReminderEditorDialog(
    reminder: VaultReminder?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var title by rememberSaveable(reminder?.id) { mutableStateOf(reminder?.title.orEmpty()) }
    var delayHours by rememberSaveable(reminder?.id) { mutableIntStateOf(24) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (reminder == null) R.string.home_add_reminder else R.string.home_edit_reminder
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    enabled = reminder == null,
                    label = { Text(stringResource(R.string.home_reminder_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf(1 to R.string.home_in_one_hour, 24 to R.string.home_tomorrow, 168 to R.string.home_next_week)
                        .forEachIndexed { index, choice ->
                            SegmentedButton(
                                selected = delayHours == choice.first,
                                onClick = { delayHours = choice.first },
                                shape = SegmentedButtonDefaults.itemShape(index, 3)
                            ) { Text(stringResource(choice.second)) }
                        }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title.trim(), System.currentTimeMillis() + delayHours * 60L * 60 * 1000)
                },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.home_save_reminder)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_cancel)) } }
    )
}

private fun formatContextTime(value: Long?): String = value?.let {
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
        .format(java.util.Date(it))
}.orEmpty()


@Composable
private fun greetingText(): String {
    val hour = remember { java.time.LocalTime.now().hour }
    return stringResource(
        when {
            hour < 12 -> R.string.home_greeting_morning
            hour < 17 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        }
    )
}

@Composable
private fun AttentionSection(
    items: List<AttentionItem>,
    needsReviewCount: Int,
    onClick: (AttentionItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.home_attention_title), style = MaterialTheme.typography.titleMedium)
        Card(shape = MaterialTheme.shapes.extraLarge) {
            Column {
                items.forEach { item ->
                    val (icon, tint) = when (item.kind) {
                        AttentionItem.Kind.REMINDER ->
                            Icons.Default.NotificationsActive to MaterialTheme.colorScheme.tertiary
                        AttentionItem.Kind.EVENT ->
                            Icons.Default.Event to MaterialTheme.colorScheme.primary
                        AttentionItem.Kind.EXPIRING ->
                            Icons.Default.WarningAmber to MaterialTheme.colorScheme.error
                        AttentionItem.Kind.REVIEW ->
                            Icons.Default.RateReview to MaterialTheme.colorScheme.secondary
                        AttentionItem.Kind.GMAIL ->
                            Icons.Default.Email to MaterialTheme.colorScheme.tertiary
                        AttentionItem.Kind.PROACTIVE_ACTION ->
                            Icons.Default.AutoAwesome to MaterialTheme.colorScheme.primary
                    }
                    ListItem(
                        headlineContent = {
                            Text(
                                if (item.kind == AttentionItem.Kind.REVIEW) {
                                    stringResource(R.string.home_attention_review, needsReviewCount)
                                } else {
                                    item.title
                                }
                            )
                        },
                        supportingContent = item.dueAt?.let { due ->
                            { Text(formatContextTime(due)) }
                        },
                        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
                        modifier = Modifier.clickable { onClick(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AskComposerCard(
    onAsk: (String) -> Unit,
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.home_ask_placeholder)) },
                modifier = Modifier.weight(1f),
                maxLines = 2,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (query.isNotBlank()) {
                            onAsk(query.trim())
                            query = ""
                        }
                    }
                )
            )
            IconButton(onClick = onScanClick) {
                Icon(Icons.Default.DocumentScanner, contentDescription = stringResource(R.string.home_action_scan))
            }
            IconButton(onClick = onVoiceClick) {
                Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.home_action_voice))
            }
            IconButton(onClick = onImportClick) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.home_action_import))
            }
        }
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
