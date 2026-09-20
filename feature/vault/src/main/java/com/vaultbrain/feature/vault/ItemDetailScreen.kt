package com.vaultbrain.feature.vault

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.vaultbrain.core.common.ui.LocalSharedTransitionScope
import com.vaultbrain.core.common.ui.LocalAnimatedVisibilityScope
import com.vaultbrain.core.common.ui.MetadataFields
import com.vaultbrain.core.common.util.humanize
import com.vaultbrain.core.common.R as CommonR
import androidx.compose.foundation.clickable

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.animation.core.tween
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.SourceType
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.PersonalCollectionSuggestion
import com.vaultbrain.shared.model.ItemProcessingStatus
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.feature.vault.components.DynamicActionRow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ItemDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit = {},
    onOpenCollection: (String) -> Unit = {},
    onManageCollections: () -> Unit = {},
    viewModel: ItemDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(itemId) {
        viewModel.loadItem(itemId)
    }
    val state by viewModel.uiState.collectAsState()
    ItemDetailContent(
        state = state,
        onBack = onBack,
        onDelete = { viewModel.deleteItem(onBack) },
        onArchiveToggle = viewModel::toggleArchive,
        onPinToggle = viewModel::togglePinned,
        onOpenItem = onOpenItem,
        onOpenCollection = onOpenCollection,
        onManageCollections = onManageCollections,
        onCollectionToggle = viewModel::setCollectionMembership,
        onAcceptSuggestion = viewModel::acceptSuggestion,
        onRejectSuggestion = viewModel::rejectSuggestion,
        onUpdate = viewModel::updateItem,
        onAddReminder = { id, title, expiry ->
            viewModel.createReminder(title, expiry - 86400000L, id) // remind 1 day before
        },
        onTranslate = viewModel::translate,
        onConfirmCloudTranslation = { rememberForCategory ->
            state.translationTargetLanguage?.let {
                viewModel.translate(
                    it,
                    explicitCloudConsent = true,
                    rememberForCategory = rememberForCategory
                )
            }
        },
        onDismissCloudConsent = viewModel::dismissCloudConsent,
        onDismissTranslationMessage = viewModel::clearTranslationMessage,
        onGenerateAiSummary = viewModel::generateAiSummary,
        onExecuteAction = viewModel::executeAction
    )
}

private fun exportJson(context: Context, item: VaultItem) {
    try {
        val jsonString = Json.encodeToString(item)
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "vault_item_${item.id}.json")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use {
                it.write(jsonString.toByteArray())
            }
            Toast.makeText(context, context.getString(R.string.detail_exported_json), Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, context.getString(R.string.detail_export_failed), Toast.LENGTH_SHORT).show()
    }
}

private fun exportPdf(context: Context, item: VaultItem) {
    try {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val contentWidth = 495

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 20f
            isFakeBoldText = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 12f
        }

        var currentY = 50f
        fun drawSection(title: String, body: String?, isTitle: Boolean = false) {
            if (currentY >= 790f || (body.isNullOrBlank() && !isTitle)) return
            canvas.save()
            canvas.translate(50f, currentY)
            val text = if (isTitle) title else "$title\n$body"
            val paint = if (isTitle) titlePaint else bodyPaint
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()
            layout.draw(canvas)
            canvas.restore()
            currentY += layout.height + 16f
        }

        drawSection(context.getString(R.string.detail_pdf_title, item.title), null, isTitle = true)
        item.summary?.takeIf { it.isNotBlank() }?.let {
            drawSection(context.getString(R.string.detail_pdf_summary), it)
        }
        item.rawOcrText?.takeIf { it.isNotBlank() }?.let {
            drawSection(context.getString(R.string.detail_pdf_ocr), it)
        }

        document.finishPage(page)

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "vault_item_${item.id}.pdf")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use {
                document.writeTo(it)
            }
            Toast.makeText(context, context.getString(R.string.detail_exported_pdf), Toast.LENGTH_SHORT).show()
        }
        document.close()
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, context.getString(R.string.detail_export_failed), Toast.LENGTH_SHORT).show()
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ItemDetailContent(
    state: ItemDetailUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onArchiveToggle: () -> Unit,
    onPinToggle: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onManageCollections: () -> Unit,
    onCollectionToggle: (String, Boolean) -> Unit,
    onAcceptSuggestion: (String) -> Unit,
    onRejectSuggestion: (String) -> Unit,
    onUpdate: (VaultItem) -> Unit,
    onAddReminder: (String, String, Long) -> Unit,
    onTranslate: (String) -> Unit,
    onConfirmCloudTranslation: (Boolean) -> Unit,
    onDismissCloudConsent: () -> Unit,
    onDismissTranslationMessage: () -> Unit,
    onGenerateAiSummary: () -> Unit = {},
    onExecuteAction: (com.vaultbrain.shared.model.ProactiveAction) -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showPrivacyInfo by remember { mutableStateOf(false) }
    var showCollectionPicker by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current

    var dominantColor by remember { mutableStateOf<ComposeColor?>(null) }
    val animatedColor by animateColorAsState(targetValue = dominantColor ?: MaterialTheme.colorScheme.surface, animationSpec = tween(500))

    LaunchedEffect(state.item?.capturedImageUri) {
        state.item?.capturedImageUri?.let { uri ->
            val request = ImageRequest.Builder(context)
                .data(uri)
                .allowHardware(false)
                .build()
            val result = context.imageLoader.execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
                Palette.from(bitmap).generate { palette ->
                    palette?.dominantSwatch?.rgb?.let { rgb ->
                        dominantColor = ComposeColor(rgb).copy(alpha = 0.15f)
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = animatedColor,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = ComposeColor.Transparent),
                title = { Text(text = stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPinToggle() 
                    }, enabled = state.item != null) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(
                                if (state.item?.isPinned == true) R.string.detail_unpin else R.string.detail_pin
                            ),
                            tint = if (state.item?.isPinned == true) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        val title = state.item?.title ?: ""
                        val summary = state.item?.summary ?: ""
                        val ocr = state.item?.rawOcrText ?: ""
                        val shareIntent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "$title\n\n$summary\n\n$ocr".trim())
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, null))
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = stringResource(R.string.detail_share))
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = stringResource(R.string.detail_edit))
                    }
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_export_json)) },
                                onClick = {
                                    showExportMenu = false
                                    state.item?.let { exportJson(context, it) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.detail_export_pdf)) },
                                onClick = {
                                    showExportMenu = false
                                    state.item?.let { exportPdf(context, it) }
                                }
                            )
                        }
                    }
                }
            )
        }

    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .widthIn(max = 900.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            when {
                state.isLoading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.detail_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                state.item == null -> {
                    Text(
                        text = stringResource(R.string.detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                else -> {
                    val item = state.item

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineMedium
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    stringResource(
                                        when (item.processingStatus) {
                                            ItemProcessingStatus.SAVED -> R.string.card_status_saved
                                            ItemProcessingStatus.ORGANIZING -> R.string.card_status_organizing
                                            ItemProcessingStatus.READY -> R.string.card_status_ready
                                            ItemProcessingStatus.NEEDS_REVIEW -> R.string.card_status_needs_review
                                        }
                                    )
                                )
                            }
                        )
                        AssistChip(
                            onClick = { showPrivacyInfo = true },
                            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
                            label = {
                                Text(
                                    stringResource(
                                        if (state.translationOrigin == AiResponseOrigin.CLOUD_AI) {
                                            R.string.detail_cloud_assisted
                                        } else {
                                            R.string.detail_on_device
                                        }
                                    )
                                )
                            }
                        )
                        (listOfNotNull(item.subtype) + item.topics + item.entities + item.tags)
                            .distinct()
                            .take(6)
                            .forEach { label ->
                                AssistChip(onClick = {}, label = { Text(label) })
                            }
                        if (item.isPinned) {
                            AssistChip(
                                onClick = onPinToggle,
                                leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                label = { Text(stringResource(R.string.detail_pinned)) }
                            )
                        }
                    }

                    DynamicActionRow(
                        item = item,
                        onExecuteAction = onExecuteAction
                    )

                    item.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    PersonalCollectionsCard(
                        collections = state.itemCollections,
                        onOpenCollection = onOpenCollection,
                        onAdd = { showCollectionPicker = true }
                    )

                    if (state.suggestedCollections.isNotEmpty()) {
                        SuggestedCollectionsCard(
                            suggestions = state.suggestedCollections,
                            onAccept = onAcceptSuggestion,
                            onReject = onRejectSuggestion
                        )
                    }

                    item.customFields["ai_highlights"]
                        ?.takeIf(String::isNotBlank)
                        ?.let { highlights ->
                            DetailTextCard(
                                title = stringResource(R.string.detail_ai_highlights),
                                text = highlights
                            )
                        }

                    val duplicateItemId = item.possibleDuplicateOfItemId
                    if (duplicateItemId != null) {
                        PossibleDuplicateCard(
                            duplicateTitle = state.possibleDuplicateTitle,
                            onOpen = { onOpenItem(duplicateItemId) }
                        )
                    } else if (item.needsReview) {
                        ReviewWarningCard(onEdit = { showEditDialog = true })
                    }
                    val currentFacts = state.sourceFacts.filter { it.sourceUpdatedAt == item.updatedAt }
                    if (currentFacts.isNotEmpty()) {
                        DetailTextCard(
                            title = stringResource(R.string.detail_source_facts),
                            text = currentFacts.take(12).joinToString("\n\n") { it.evidence }
                        )
                    }
                    if (currentFacts.isNotEmpty() && state.relatedItems.isNotEmpty()) {
                        Text(stringResource(R.string.detail_shared_entities), style = MaterialTheme.typography.titleMedium)
                        state.relatedItems.forEach { related ->
                            TextButton(onClick = { onOpenItem(related.id) }) { Text(related.title) }
                        }
                    }

                    AiActionCard(
                        state = state,
                        onGenerateSummary = onGenerateAiSummary
                    )

                    TranslationCard(
                        state = state,
                        onTranslate = { state.item?.id?.let(onTranslate) }
                    )

                    MetadataCard(item = item, onAddReminder = onAddReminder)

                    item.capturedImageUri?.let { uri ->
                        CapturedMediaCard(
                            uri = uri,
                            mimeType = item.parsedMetadata["media_mime_type"],
                            itemId = item.id
                        )
                    }

                    item.userNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                        DetailTextCard(title = stringResource(R.string.detail_notes), text = notes)
                    }

                    ActionButtons(
                        isArchived = item.isArchived,
                        onArchiveToggle = onArchiveToggle,
                        onDeleteClick = { showDeleteDialog = true }
                    )

                    item.rawOcrText?.takeIf { it.isNotBlank() }?.let { ExtractedTextCard(it) }
                }
            }
        }
    }
}

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.detail_delete_title)) },
            text = { Text(stringResource(R.string.detail_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.detail_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }

    if (showCollectionPicker) {
        AlertDialog(
            onDismissRequest = { showCollectionPicker = false },
            title = { Text(stringResource(R.string.detail_add_to_collection)) },
            text = {
                if (state.activeCollections.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.collections_empty))
                        OutlinedButton(onClick = {
                            showCollectionPicker = false
                            onManageCollections()
                        }) {
                            Text(stringResource(R.string.collections_new))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                    ) {
                        state.activeCollections.forEach { collection ->
                            val selected = state.itemCollections.any { it.id == collection.id }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onCollectionToggle(collection.id, !selected)
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        onCollectionToggle(collection.id, checked)
                                    }
                                )
                                Text(collection.name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCollectionPicker = false }) {
                    Text(stringResource(R.string.detail_done))
                }
            },
            dismissButton = {
                if (state.activeCollections.isNotEmpty()) {
                    TextButton(onClick = {
                        showCollectionPicker = false
                        onManageCollections()
                    }) { Text(stringResource(R.string.detail_manage_collections)) }
                }
            }
        )
    }

    if (showPrivacyInfo) {
        AlertDialog(
            onDismissRequest = { showPrivacyInfo = false },
            title = {
                Text(
                    stringResource(
                        if (state.translationOrigin == AiResponseOrigin.CLOUD_AI) {
                            R.string.detail_cloud_assisted_title
                        } else {
                            R.string.detail_on_device_title
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (state.translationOrigin == AiResponseOrigin.CLOUD_AI) {
                            R.string.detail_cloud_assisted_body
                        } else {
                            R.string.detail_on_device_body
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyInfo = false }) {
                    Text(stringResource(R.string.detail_done))
                }
            }
        )
    }

    state.cloudConsent?.let { disclosure ->
        var rememberCategory by remember(disclosure) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = onDismissCloudConsent,
            title = { Text(stringResource(R.string.detail_cloud_consent_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.detail_cloud_consent_body, disclosure.provider))
                    Text(
                        stringResource(R.string.detail_cloud_consent_exact_text),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            disclosure.exactText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (disclosure.canRememberForCategory &&
                        disclosure.rememberableCategories.isNotEmpty()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberCategory,
                                onCheckedChange = { rememberCategory = it }
                            )
                            Text(
                                stringResource(R.string.detail_cloud_consent_remember_category),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onConfirmCloudTranslation(rememberCategory) }) {
                    Text(stringResource(R.string.detail_cloud_consent_send_once))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissCloudConsent) {
                    Text(stringResource(R.string.detail_cancel))
                }
            }
        )
    }

    state.translationMessage?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissTranslationMessage,
            title = { Text(stringResource(R.string.detail_translation_unavailable_title)) },
            text = {
                Text(
                    stringResource(
                        when (message) {
                            TranslationMessage.LOCAL_MODEL_UNAVAILABLE -> R.string.detail_translation_local_only_unavailable
                            TranslationMessage.CLOUD_NOT_CONFIGURED -> R.string.detail_translation_cloud_not_configured
                            TranslationMessage.GENERATION_FAILED -> R.string.detail_translation_failed
                            TranslationMessage.NO_TEXT -> R.string.detail_translation_no_text
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissTranslationMessage) {
                    Text(stringResource(R.string.detail_done))
                }
            }
        )
    }

    if (showEditDialog && state.item != null) {
        var editTitle by remember { mutableStateOf(state.item.title) }
        var editSummary by remember { mutableStateOf(state.item.summary ?: "") }
        var editNotes by remember { mutableStateOf(state.item.userNotes ?: "") }
        var editLensTags by remember { mutableStateOf(state.item.lensTags.mapNotNull(LensId::canonicalOrNull).toSet()) }
        var editExpiry by remember { mutableStateOf(formatEditableDate(state.item.expiryDate)) }
        var editReminder by remember { mutableStateOf(formatEditableDate(state.item.secondaryAlertDate)) }
        var editRecurring by remember { mutableStateOf(state.item.recurringRule.orEmpty()) }
        var editTargetPrice by remember { mutableStateOf(state.item.targetPrice?.toString().orEmpty()) }
        var editStealth by remember { mutableStateOf(state.item.isStealth) }
        var editMetadata by remember { mutableStateOf(formatKeyValueLines(state.item.parsedMetadata)) }
        var editCustomFields by remember { mutableStateOf(state.item.customFields) }
        var editClassification by remember { mutableStateOf(state.item.userClassificationOverride) }
        val lensFields = remember(editLensTags) { lensFieldDefinitions(editLensTags) }
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.detail_edit_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 620.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.detail_edit_label_title)) }
                    )
                    OutlinedTextField(
                        value = editSummary,
                        onValueChange = { editSummary = it },
                        label = { Text(stringResource(R.string.detail_edit_label_summary)) },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = editNotes,
                        onValueChange = { editNotes = it },
                        label = { Text(stringResource(R.string.detail_notes)) },
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = editExpiry,
                        onValueChange = { editExpiry = it },
                        label = { Text(stringResource(R.string.detail_edit_expiry)) },
                        isError = editExpiry.isNotBlank() && parseEditableDate(editExpiry) == null
                    )
                    OutlinedTextField(
                        value = editReminder,
                        onValueChange = { editReminder = it },
                        label = { Text(stringResource(R.string.detail_edit_reminder)) },
                        isError = editReminder.isNotBlank() && parseEditableDate(editReminder) == null
                    )
                    OutlinedTextField(
                        value = editRecurring,
                        onValueChange = { editRecurring = it },
                        label = { Text(stringResource(R.string.detail_edit_recurrence)) }
                    )
                    if (LensId.MONEY in editLensTags) {
                        OutlinedTextField(
                            value = editTargetPrice,
                            onValueChange = { editTargetPrice = it.filter { char -> char.isDigit() || char == '.' } },
                            label = { Text(stringResource(R.string.detail_edit_target_price)) },
                            isError = editTargetPrice.isNotBlank() && editTargetPrice.toDoubleOrNull() == null
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.detail_edit_hide_private_gift), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.detail_edit_hide_private_gift_desc), style = MaterialTheme.typography.bodySmall)
                            }
                            val stealthEnabledText = stringResource(R.string.detail_edit_stealth_enabled)
                            val stealthDisabledText = stringResource(R.string.detail_edit_stealth_disabled)
                            Switch(
                                checked = editStealth,
                                onCheckedChange = { editStealth = it },
                                modifier = Modifier.semantics {
                                    stateDescription = if (editStealth) stealthEnabledText else stealthDisabledText
                                }
                            )
                        }
                    }
                    if (lensFields.isNotEmpty()) {
                        Text(stringResource(R.string.detail_edit_lens_workflow), style = MaterialTheme.typography.titleSmall)
                        for ((key, label) in lensFields.entries) {
                            OutlinedTextField(
                                value = editCustomFields[key].orEmpty(),
                                onValueChange = { value -> editCustomFields = editCustomFields + Pair(key, value) },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editMetadata,
                        onValueChange = { editMetadata = it },
                        label = { Text(stringResource(R.string.detail_edit_detected_metadata)) },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdate(
                        state.item.copy(
                            title = editTitle,
                            summary = editSummary,
                            userNotes = editNotes,
                            lensTags = editLensTags,
                            expiryDate = parseEditableDate(editExpiry),
                            secondaryAlertDate = parseEditableDate(editReminder),
                            recurringRule = editRecurring.trim().takeIf(String::isNotEmpty),
                            targetPrice = editTargetPrice.toDoubleOrNull(),
                            isStealth = editStealth && LensId.MONEY in editLensTags,
                            parsedMetadata = parseKeyValueLines(editMetadata),
                            customFields = editCustomFields.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() },
                            userClassificationOverride = editClassification
                        )
                    )
                    showEditDialog = false
                }, enabled = editTitle.isNotBlank() &&
                    (editExpiry.isBlank() || parseEditableDate(editExpiry) != null) &&
                    (editReminder.isBlank() || parseEditableDate(editReminder) != null)
                ) { Text(stringResource(R.string.detail_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.detail_cancel)) }
            }
        )
    }
}

private fun formatEditableDate(timestamp: Long?): String = timestamp?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}.orEmpty()

private fun parseEditableDate(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching {
        LocalDate.parse(value.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrNull()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailTextCard(title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PossibleDuplicateCard(duplicateTitle: String?, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.detail_possible_duplicate), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                Text(duplicateTitle ?: stringResource(R.string.detail_possible_duplicate_desc), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpen) { Text(stringResource(R.string.detail_view)) }
        }
    }
}

@Composable
private fun ReviewWarningCard(onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.detail_needs_review_confidence), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text(stringResource(R.string.detail_review)) }
        }
    }
}

@Composable
private fun AiActionCard(
    state: ItemDetailUiState,
    onGenerateSummary: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.detail_ai_doc_intelligence), style = MaterialTheme.typography.titleSmall)
                }
                if (!state.isGeneratingAiSummary && state.aiSummary == null) {
                    Button(onClick = onGenerateSummary) { Text(stringResource(R.string.detail_summarize)) }
                }
            }
            if (state.isGeneratingAiSummary) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.detail_generating_ai_summary), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.aiSummary?.let { summary ->
                Text(text = summary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TranslationCard(
    state: ItemDetailUiState,
    onTranslate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(stringResource(R.string.detail_translate), style = MaterialTheme.typography.titleSmall)
                }
                if (!state.isTranslating && state.translation == null) {
                    OutlinedButton(onClick = onTranslate) { Text(stringResource(R.string.detail_translate)) }
                }
            }
            if (state.isTranslating) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.detail_translating), style = MaterialTheme.typography.bodySmall)
                }
            }
            state.translation?.let { translation ->
                Text(text = translation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ExtractedTextCard(rawOcrText: String) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.detail_extracted_text), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) stringResource(R.string.detail_hide_text) else stringResource(R.string.detail_show_text))
                }
            }
            if (expanded) {
                Text(
                    text = rawOcrText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonalCollectionsCard(
    collections: List<PersonalCollection>,
    onOpenCollection: (String) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.detail_collections), style = MaterialTheme.typography.titleSmall)
            if (collections.isEmpty()) {
                Text(
                    stringResource(R.string.detail_no_collections),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    collections.forEach { collection ->
                        AssistChip(
                            onClick = { onOpenCollection(collection.id) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text(collection.name) }
                        )
                    }
                }
            }
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.detail_add_to_collection))
            }
        }
    }
}

@Composable
private fun SuggestedCollectionsCard(
    suggestions: List<PersonalCollectionSuggestion>,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.suggested_collections), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = suggestion.collectionName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onAccept(suggestion.collectionId) }) {
                            Text(stringResource(R.string.add_suggestion))
                        }
                        TextButton(onClick = { onReject(suggestion.collectionId) }) {
                            Text(stringResource(R.string.reject_suggestion))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataCard(item: VaultItem, onAddReminder: (String, String, Long) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetadataRow(label = "Created", value = formatDateTime(item.createdAt))
            item.expiryDate?.let { expiry ->
                MetadataRow(label = "Expires", value = formatDateTime(expiry))
                androidx.compose.material3.OutlinedButton(
                    onClick = {
                        onAddReminder(item.id, "Expiry: ${item.title.orEmpty()}", expiry)
                        Toast.makeText(context, "Reminder set for 1 day before expiry", Toast.LENGTH_SHORT).show()
                    },
                    enabled = expiry - 86400000L > System.currentTimeMillis(),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.detail_remind_expiry))
                }
            }

            val orderedFields = MetadataFields.orderedFields(item.effectiveClassification, item.parsedMetadata)
            if (orderedFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_extracted_details),
                    style = MaterialTheme.typography.titleSmall
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    orderedFields.forEach { (key, value) ->
                        MetadataField(label = MetadataFields.label(key), value = value)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun MetadataField(label: String, value: String) {
    var expanded by remember { mutableStateOf(false) }
    val collapsible = value.length > 180 || value.count { it == '\n' } >= 6
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded || !collapsible) Int.MAX_VALUE else 6,
            overflow = TextOverflow.Ellipsis
        )
        if (collapsible) {
            Text(
                text = stringResource(
                    if (expanded) CommonR.string.show_less else CommonR.string.show_more
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded = !expanded }
            )
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CapturedMediaCard(uri: String, mimeType: String?, itemId: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (mimeType == "application/pdf") {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

                var imageModifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(MaterialTheme.shapes.medium)

                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        imageModifier = imageModifier.sharedElement(
                            state = rememberSharedContentState(key = "image_${itemId}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }

                coil.compose.AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Captured image",
                    modifier = imageModifier,
                    contentScale = ContentScale.Fit
                )
            }
            Text(
                text = if (mimeType == "application/pdf") "Captured PDF document" else "Captured image",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionButtons(
    isArchived: Boolean,
    onArchiveToggle: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onArchiveToggle,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(if (isArchived) R.string.detail_unarchive else R.string.detail_archive))
        }
        Button(
            onClick = onDeleteClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.detail_delete))
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    )
    return DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").format(dateTime)
}

@Preview(showBackground = true)
@Composable
private fun ItemDetailContentPreview() {
    MaterialTheme {
        ItemDetailContent(
            state = ItemDetailUiState(
                item = VaultItem(
                    id = "1",
                    title = "Boarding pass",
                    summary = "Flight AB123 to Tokyo",
                    sourceType = SourceType.CAMERA,
                    createdAt = System.currentTimeMillis(),
                    lensTags = setOf(LensId.TRAVEL),
                    parsedMetadata = mapOf("flight" to "AB123", "seat" to "12A"),
                    capturedImageUri = "file:///mock/path.jpg",
                    aiClassification = Classification.TICKET
                ),
                isLoading = false
            ),
            onBack = {},
            onDelete = {},
            onArchiveToggle = {},
            onPinToggle = {},
            onOpenItem = {},
            onOpenCollection = {},
            onManageCollections = {},
            onCollectionToggle = { _, _ -> },
            onAcceptSuggestion = {},
            onRejectSuggestion = {},
            onUpdate = { _ -> },
            onAddReminder = { _, _, _ -> },
            onTranslate = {},
            onConfirmCloudTranslation = { _ -> },
            onDismissCloudConsent = {},
            onDismissTranslationMessage = {}
        )
    }
}

private fun formatKeyValueLines(metadata: Map<String, String>): String {
    return metadata.entries.joinToString("\n") { "${it.key}=${it.value}" }
}

private fun parseKeyValueLines(text: String): Map<String, String> {
    return text.lines()
        .map { it.trim() }
        .filter { it.contains("=") }
        .associate {
            val parts = it.split("=", limit = 2)
            parts[0].trim() to parts[1].trim()
        }
}

private fun lensFieldDefinitions(lensTags: Set<String>): Map<String, String> {
    val fields = mutableMapOf<String, String>()
    if (lensTags.contains(LensId.MONEY)) {
        fields["merchant"] = "Merchant / Vendor"
        fields["amount"] = "Amount"
    }
    if (lensTags.contains(LensId.HEALTH)) {
        fields["doctor"] = "Doctor / Clinic"
        fields["prescription"] = "Prescription Details"
    }
    if (lensTags.contains(LensId.TRAVEL)) {
        fields["destination"] = "Destination"
        fields["confirmation_code"] = "Confirmation Code"
    }
    if (lensTags.contains(LensId.BUREAUCRACY)) {
        fields["document_number"] = "Document Number"
        fields["issuer"] = "Issuing Authority"
    }
    return fields
}
