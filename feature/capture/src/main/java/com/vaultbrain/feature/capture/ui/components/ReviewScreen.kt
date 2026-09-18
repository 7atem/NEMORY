package com.vaultbrain.feature.capture.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.VaultItem
import com.vaultbrain.core.common.util.humanize
import com.vaultbrain.feature.capture.ExperienceDefinitions
import com.vaultbrain.feature.capture.R
import kotlin.math.roundToInt

/**
 * A user-centred verification step using progressive disclosure.
 *
 * Simpler default state for 90% of captures, with expandable sections for power users.
 * Uses inline-editable metadata chips instead of large form fields.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    draft: VaultItem,
    confidence: Float,
    suggestedLensTags: List<String>,
    onTitleChange: (String) -> Unit,
    onSummaryChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onClassificationChange: (Classification?) -> Unit,
    onLensTagToggle: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    batchPosition: Int = 1,
    batchTotal: Int = 1,
    onMetadataChange: ((String, String) -> Unit)? = null,
    editedMetadataKeys: Set<String> = emptySet(),
    showOnDeviceAiHint: Boolean = false,
    onOnDeviceAiDownload: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showDiscardDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showOnDeviceAiHint) {
                        TextButton(
                            onClick = onOnDeviceAiDownload,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.feature_capture_on_device_ai_hint))
                        }
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSave()
                        },
                        enabled = draft.title.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            if (batchPosition < batchTotal) stringResource(R.string.feature_capture_save_next)
                            else stringResource(R.string.feature_capture_save)
                        )
                    }
                    if (draft.title.isBlank()) {
                        Text(
                            stringResource(R.string.feature_capture_title_required),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.feature_capture_review_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    if (batchTotal > 1) {
                        Text(
                            stringResource(
                                R.string.feature_capture_item_position,
                                batchPosition,
                                batchTotal
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TextButton(onClick = { showDiscardDialog = true }) {
                    Text(stringResource(R.string.feature_capture_discard))
                }
            }

            // Card 1: Hero (Image, Title, Confidence)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        draft.capturedImageUri?.let { uri ->
                            if (draft.parsedMetadata["media_mime_type"] == "application/pdf") {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                AnnotatedImageViewer(
                                    imageUri = uri,
                                    contentDescription = stringResource(R.string.feature_capture_image_preview),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = draft.title,
                            onValueChange = onTitleChange,
                            isError = draft.title.isBlank(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.titleMedium
                        )
                        val ocrText = draft.rawOcrText
                        if (ocrText.isNullOrBlank() || ocrText.trim().length < 20) {
                            Text(
                                stringResource(R.string.feature_capture_arabic_ocr_notice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Card 1.5: AI Key Highlights
            val highlightsText = draft.customFields["ai_highlights"]
            if (!highlightsText.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.feature_capture_ai_highlights),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = highlightsText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Card 2: Smart Details
            if (draft.parsedMetadata.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            stringResource(R.string.feature_capture_detected_details),
                            style = MaterialTheme.typography.titleSmall
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            draft.parsedMetadata.forEach { (key, value) ->
                                if (key != "media_mime_type" && key != "barcodes" && key != "media_type" && key != "media_status") {
                                    EditableMetadataChip(
                                        key = key,
                                        value = value,
                                        isUserEdited = key in editedMetadataKeys,
                                        onValueChange = { onMetadataChange?.invoke(key, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (draft.subtype != null || draft.topics.isNotEmpty() || draft.entities.isNotEmpty() ||
                draft.tags.isNotEmpty() || draft.suggestions.isNotEmpty()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.feature_capture_semantic_context),
                            style = MaterialTheme.typography.titleSmall
                        )
                        draft.subtype?.let {
                            Text("${stringResource(R.string.feature_capture_subtype)}: $it")
                        }
                        if (draft.topics.isNotEmpty()) {
                            Text("${stringResource(R.string.feature_capture_topics)}: ${draft.topics.joinToString(", ")}")
                        }
                        if (draft.entities.isNotEmpty()) {
                            Text("${stringResource(R.string.feature_capture_entities)}: ${draft.entities.joinToString(", ")}")
                        }
                        if (draft.tags.isNotEmpty()) {
                            Text("${stringResource(R.string.feature_capture_tags)}: ${draft.tags.joinToString(", ")}")
                        }
                        if (draft.suggestions.isNotEmpty()) {
                            Text(
                                stringResource(R.string.feature_capture_suggestions),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(draft.suggestions.joinToString("\n") { "• $it" })
                        }
                    }
                }
            }

            // Expandable Sections
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                ExpandableSection(title = stringResource(R.string.feature_capture_summary_notes)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = draft.summary.orEmpty(),
                            onValueChange = onSummaryChange,
                            label = { Text(stringResource(R.string.feature_capture_summary)) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft.userNotes.orEmpty(),
                            onValueChange = onNotesChange,
                            label = { Text(stringResource(R.string.feature_capture_notes)) },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

            }
            
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDiscardDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.feature_capture_discard_confirm_title)) },
            text = { Text(stringResource(R.string.feature_capture_discard_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDiscard()
                    }
                ) {
                    Text(stringResource(R.string.feature_capture_discard_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.feature_capture_cancel))
                }
            }
        )
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "expandRotation")

    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Box(modifier = Modifier.padding(bottom = 12.dp)) {
                content()
            }
        }
    }
}
