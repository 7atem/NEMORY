package com.vaultbrain.feature.vault.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vaultbrain.core.common.theme.*
import com.vaultbrain.shared.domain.LensId
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.common.ui.VaultItemPresenter
import com.vaultbrain.core.common.ui.VaultCardDateKind
import com.vaultbrain.core.common.ui.MetadataFields
import com.vaultbrain.shared.model.ItemProcessingStatus
import com.vaultbrain.feature.vault.R
import androidx.compose.ui.res.stringResource
import androidx.core.text.BidiFormatter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

import androidx.compose.animation.ExperimentalSharedTransitionApi
import com.vaultbrain.core.common.ui.LocalSharedTransitionScope
import com.vaultbrain.core.common.ui.LocalAnimatedVisibilityScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import com.vaultbrain.core.common.ui.softShadow
import androidx.compose.material3.CardElevation

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VaultItemCard(
    item: VaultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArchive: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    extraContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    val presentation = VaultItemPresenter.present(item)
    val bidi = BidiFormatter.getInstance()
    val statusText = stringResource(
        when (item.processingStatus) {
            ItemProcessingStatus.SAVED -> R.string.card_status_saved
            ItemProcessingStatus.ORGANIZING -> R.string.card_status_organizing
            ItemProcessingStatus.READY -> R.string.card_status_ready
            ItemProcessingStatus.NEEDS_REVIEW -> R.string.card_status_needs_review
        }
    )
    val dateText = stringResource(
        when (presentation.dateKind) {
            VaultCardDateKind.SAVED -> R.string.card_date_saved
            VaultCardDateKind.EXPIRES -> R.string.card_date_expires
            VaultCardDateKind.DUE -> R.string.card_date_due
            VaultCardDateKind.EVENT -> R.string.card_date_event
        },
        formatDate(presentation.dateMillis)
    )
    val haptic = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onArchive?.invoke()
                true
            } else false
        }
    )

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalAnimatedVisibilityScope.current

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 2.dp,
        animationSpec = tween(200),
        label = "card_shadow"
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.secondary
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = stringResource(R.string.detail_archive),
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = onArchive != null
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .scale(scale)
                .softShadow(blurRadius = shadowElevation, offsetY = if (isHovered) 6.dp else 2.dp)
                .semantics {
                    contentDescription = listOfNotNull(
                        item.title,
                        presentation.keyFact,
                        dateText,
                        statusText
                    ).joinToString(". ")
                }
                .combinedClickable(interactionSource = interactionSource, indication = androidx.compose.material3.ripple(), onClick = onClick, onLongClick = onLongClick),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val uri = item.capturedImageUri
                if (uri != null) {
                    val context = LocalContext.current
                    var imageModifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            imageModifier = imageModifier.sharedElement(
                                state = rememberSharedContentState(key = "image_${item.id}"),
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        }
                    }
                    
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Image for ${item.title}",
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = bidi.unicodeWrap(item.title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    presentation.keyFact?.let { keyFact ->
                        Text(
                            text = bidi.unicodeWrap(keyFact),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    presentation.summary?.let { summary ->
                        Text(
                            text = bidi.unicodeWrap(summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val topFields = MetadataFields.topFields(item.effectiveClassification, item.parsedMetadata)
                    if (topFields.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        topFields.forEach { (key, value) ->
                            Text(
                                text = bidi.unicodeWrap("${MetadataFields.label(key)}: $value"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (extraContent != null) {
                        extraContent()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = presentation.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (item.processingStatus) {
                                ItemProcessingStatus.NEEDS_REVIEW -> MaterialTheme.colorScheme.error
                                ItemProcessingStatus.READY -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.tertiary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    @androidx.annotation.DrawableRes illustration: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    var isVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { isVisible = true }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn(tween(400)) + androidx.compose.animation.slideInVertically(tween(400)) { it / 8 }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (illustration != null) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = illustration),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SkeletonItem(modifier: Modifier = Modifier) {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        MaterialTheme.colorScheme.surfaceVariant
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .background(brush)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.2f))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.width(160.dp).height(16.dp).background(Color.Gray.copy(alpha = 0.2f)))
                Box(modifier = Modifier.width(240.dp).height(12.dp).background(Color.Gray.copy(alpha = 0.1f)))
                Box(modifier = Modifier.width(100.dp).height(10.dp).background(Color.Gray.copy(alpha = 0.1f)))
            }
        }
    }
}

fun lensEmoji(lensId: String): String = when (lensId) {
    LensId.MONEY -> "💰"
    LensId.HEALTH -> "🏥"
    LensId.TRAVEL -> "✈️"
    LensId.BUREAUCRACY -> "📄"
    LensId.MEDIA -> "🎬"
    else -> "📁"
}

fun lensTitle(lensId: String): String = when (lensId) {
    LensId.MONEY -> "Money"
    LensId.HEALTH -> "Health"
    LensId.TRAVEL -> "Travel"
    LensId.BUREAUCRACY -> "Bureaucracy"
    LensId.MEDIA -> "Watchlist"
    else -> lensId.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
fun lensContainerColor(lensId: String): Color = when (lensId) {
    LensId.MONEY -> LensMoneyGreenContainer
    LensId.HEALTH -> LensHealthBlueContainer
    LensId.TRAVEL -> LensTravelOrangeContainer
    LensId.BUREAUCRACY -> LensBureaucracyPurpleContainer
    LensId.MEDIA -> LensMediaCyanContainer
    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
}

private fun formatDate(timestamp: Long): String {
    val dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochMilli(timestamp),
        ZoneId.systemDefault()
    )
    return DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()).format(dateTime)
}

@Preview(showBackground = true)
@Composable
private fun VaultItemCardPreview() {
    MaterialTheme {
        VaultItemCard(
            item = PreviewData.sampleItems.first(),
            onClick = {}
        )
    }
}

@Composable
fun ConfidenceGauge(confidence: Float, modifier: Modifier = Modifier) {
    val color = when {
        confidence >= 0.8f -> Color(0xFF4CAF50)
        confidence >= 0.5f -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
    Row(
        modifier = modifier.semantics { 
            contentDescription = "${(confidence * 100).toInt()}% Confidence" 
        }, 
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "${(confidence * 100).toInt()}% Confidence",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
