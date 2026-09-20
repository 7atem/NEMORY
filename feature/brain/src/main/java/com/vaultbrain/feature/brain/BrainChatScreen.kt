package com.vaultbrain.feature.brain

import android.Manifest
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vaultbrain.core.ai.llm.AiResponseOrigin
import com.vaultbrain.core.ai.rag.RagEvidence
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.feature.brain.model.ChatMessage
import com.vaultbrain.feature.voice.OnDeviceVoiceCaptureDialog
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

private fun parseMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var currentIndex = 0
        // Match bold (**text**), headers (# text), or list items (- text or * text)
        val regex = Regex("(\\*\\*(.*?)\\*\\*)|(^(#+)\\s+(.*)$)|(^[\\-\\*]\\s+(.*)$)", RegexOption.MULTILINE)
        val matches = regex.findAll(text)

        for (match in matches) {
            val startIndex = match.range.first
            if (startIndex > currentIndex) {
                append(text.substring(currentIndex, startIndex))
            }
            if (match.groups[1] != null) {
                // Bold
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groups[2]!!.value)
                }
            } else if (match.groups[3] != null) {
                // Header
                val hashes = match.groups[4]!!.value
                val content = match.groups[5]!!.value
                val fontSize = when (hashes.length) {
                    1 -> 24.sp
                    2 -> 20.sp
                    else -> 18.sp
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize)) {
                    append(content)
                }
            } else if (match.groups[6] != null) {
                // List
                val content = match.groups[7]!!.value
                append("• ")
                append(content)
            }
            currentIndex = match.range.last + 1


        }
        if (currentIndex < text.length) {
            append(text.substring(currentIndex))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainChatScreen(onSourceClick: (String) -> Unit = {}, 
    onBack: () -> Unit,
    viewModel: BrainChatViewModel = hiltViewModel()
) {    val isAiCoreEnabled by viewModel.isAiCoreEnabled.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val isDeepResearch by viewModel.isDeepResearch.collectAsStateWithLifecycle()
    val cloudConsent by viewModel.cloudConsent.collectAsStateWithLifecycle()
    val cloudMessage by viewModel.cloudMessage.collectAsStateWithLifecycle()
    val pendingWrite by viewModel.pendingWrite.collectAsStateWithLifecycle()
    val agentProposals by viewModel.agentProposals.collectAsStateWithLifecycle()
    val pendingCollectionCreation by viewModel.pendingCollectionCreation.collectAsStateWithLifecycle()
    val pendingCollectionTarget by viewModel.pendingCollectionTarget.collectAsStateWithLifecycle()
    val activeCollections by viewModel.activeCollections.collectAsStateWithLifecycle()
    
    // Convert suggestion to just keyword if not conversational
    val suggestionQueries = suggestions.map { 
        if (isAiCoreEnabled) stringResource(it.queryRes)
        else stringResource(it.queryRes).replace("What ", "").replace("are ", "").replace("?", "").trim()
    }
    
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val interactionBlocked = isLoading || pendingWrite != null || pendingCollectionCreation != null
    var showClearDialog by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.confirmWriteAction()
        } else {
            viewModel.dismissWriteAction(BrainWriteIssue.NOTIFICATION_PERMISSION_DENIED)
        }
    }

    val lastMessageText = messages.lastOrNull()?.let { (it as? ChatMessage.Assistant)?.text }
    LaunchedEffect(messages.size, isStreaming, lastMessageText?.length) {
        if (messages.isNotEmpty()) {
            val isNearBottom = !listState.canScrollForward ||
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= messages.lastIndex - 1
            if (isNearBottom || messages.size == 1) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isAiCoreEnabled) stringResource(R.string.brain_chat_title)
                            else stringResource(R.string.brain_chat_title_standard)
                        )
                        Text(
                            if (isAiCoreEnabled) stringResource(R.string.brain_chat_private)
                            else stringResource(R.string.brain_chat_subtitle_standard),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.brain_chat_back)
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.FilterChip(
                        selected = isDeepResearch,
                        onClick = { viewModel.toggleDeepResearch() },
                        label = { Text(stringResource(R.string.brain_chat_deep_filter)) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }, enabled = !interactionBlocked) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.brain_chat_clear))
                        }
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ChatInputBar(
                    input = input,
                    isLoading = interactionBlocked,
                    onInputChanged = viewModel::onInputChanged,
                    onSend = { viewModel.sendQuery(input) },
                    onSuggestedQuery = { viewModel.sendQuery(it) },
                    suggestions = suggestionQueries,
                    isAiCoreEnabled = isAiCoreEnabled,
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 840.dp),
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 16.dp,
                        bottom = paddingValues.calculateBottomPadding() + 16.dp,
                        start = 16.dp,
                        end = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (messages.isEmpty()) {
                        item {
                            WelcomePanel(
                                suggestions = suggestionQueries,
                                onQueryClick = viewModel::sendQuery,
                                isAiCoreEnabled = isAiCoreEnabled
                            )
                        }
                    }
                    items(messages.size, key = { messages[it].id }) { index ->
                        val message = messages[index]
                        val isLatest = index == messages.lastIndex
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { it / 2 }
                        ) {
                            ChatBubble(
                                message = message,
                                isLatest = isLatest,
                                isStreaming = isStreaming,
                                onSourceClick = onSourceClick,
                                onRetry = viewModel::retryQuery,
                                onSuggestedAction = viewModel::onSuggestedAction
                            )
                            if (pendingWrite?.assistantMessageId == message.id) {
                                PendingWriteCard(
                                    pending = pendingWrite!!,
                                    isLoading = isLoading,
                                    onConfirm = {
                                        val needsNotificationPermission =
                                            pendingWrite!!.preview.action is BrainWriteAction.SetReminder &&
                                                android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                                androidx.core.content.ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.POST_NOTIFICATIONS
                                                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (needsNotificationPermission) {
                                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            viewModel.confirmWriteAction()
                                        }
                                    },
                                    onDismiss = { viewModel.dismissWriteAction() },
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    items(agentProposals.size, key = { agentProposals[it].id }) { index ->
                        val pending = agentProposals[index]
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(R.string.brain_agent_proposal), style = MaterialTheme.typography.titleMedium)
                                val details = when (val proposal = pending.proposal) {
                                    is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.Reminder -> stringResource(R.string.brain_agent_reminder, proposal.title, proposal.date)
                                    is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.CalendarEvent -> stringResource(R.string.brain_agent_calendar, proposal.title, proposal.start, proposal.end, proposal.notes)
                                    is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.MetadataUpdate -> "${pending.source?.title.orEmpty()}\n${proposal.key}: ${proposal.value}"
                                    is com.vaultbrain.core.ai.rag.LocalAgent.ActionProposal.CollectionMembership -> pending.source?.title.orEmpty()
                                }
                                Text(details)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { viewModel.confirmAgentProposal(pending.id) }, enabled = !isLoading) {
                                        Text(stringResource(R.string.brain_agent_confirm))
                                    }
                                    TextButton(onClick = { viewModel.dismissAgentProposal(pending.id) }) { Text(stringResource(R.string.brain_agent_dismiss)) }
                                }
                            }
                        }
                    }
                    if (isLoading) {
                        item {
                            AssistantLoadingBubble()
                        }
                    } else if (isStreaming) {
                        item {
                            StreamingIndicator()
                        }
                    }
                }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.brain_chat_clear_title)) },
            text = { Text(stringResource(R.string.brain_chat_clear_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearConversation()
                    showClearDialog = false
                }) { Text(stringResource(R.string.brain_chat_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.brain_chat_cancel))
                }
            }
        )
    }

    

    pendingCollectionCreation?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCollectionCreation() },
            title = { Text(stringResource(R.string.brain_collection_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.brain_collection_create_confirm, pending.name, pending.items.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.brain_collection_create_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.brain_write_confirm_safety),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmCollectionCreation() }) {
                    Text(stringResource(R.string.brain_collection_create_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCollectionCreation() }) {
                    Text(stringResource(R.string.brain_chat_cancel))
                }
            }
        )
    }

    pendingCollectionTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCollectionTarget() },
            title = { Text(stringResource(R.string.brain_collection_add_title)) },
            text = {
                if (activeCollections.isEmpty()) {
                    Text(
                        stringResource(R.string.brain_collection_empty_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        activeCollections.forEach { collection ->
                            TextButton(
                                onClick = { viewModel.confirmAddToCollection(collection.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    collection.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCollectionTarget() }) {
                    Text(stringResource(R.string.brain_chat_cancel))
                }
            }
        )
    }

    cloudConsent?.let { pending ->
        var rememberCategories by remember(pending) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = viewModel::dismissCloudConsent,
            title = { Text(stringResource(R.string.brain_cloud_consent_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.brain_cloud_consent_body, pending.disclosure.provider))
                    Text(
                        stringResource(R.string.brain_cloud_consent_exact_text),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            pending.disclosure.exactText,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (pending.disclosure.canRememberForCategory &&
                        pending.disclosure.rememberableCategories.isNotEmpty()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberCategories,
                                onCheckedChange = { rememberCategories = it }
                            )
                            Text(
                                stringResource(R.string.brain_cloud_remember_categories),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmCloudAssist(rememberCategories) }) {
                    Text(stringResource(R.string.brain_cloud_send_once))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCloudConsent) {
                    Text(stringResource(R.string.brain_chat_cancel))
                }
            }
        )
    }

    cloudMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCloudMessage,
            title = { Text(stringResource(R.string.brain_cloud_unavailable_title)) },
            text = {
                Text(
                    stringResource(
                        when (message) {
                            BrainCloudMessage.NOT_CONFIGURED -> R.string.brain_cloud_not_configured
                            BrainCloudMessage.GENERATION_FAILED -> R.string.brain_cloud_generation_failed
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissCloudMessage) {
                    Text(stringResource(R.string.brain_cloud_done))
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isLatest: Boolean,
    isStreaming: Boolean,
    onSourceClick: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSuggestedAction: (com.vaultbrain.feature.brain.model.SuggestedAction) -> Unit,
    modifier: Modifier = Modifier
) {
    when (message) {
        is ChatMessage.User -> UserBubble(text = message.text, modifier = modifier)
        is ChatMessage.Assistant -> AssistantBubble(
            text = message.text,
            sources = message.sources,
            externalSources = message.externalSources,
            confidence = message.confidence,
            evidence = message.evidence,
            responseOrigin = message.responseOrigin,
            isError = message.isError,
            originalQuery = message.originalQuery,
            suggestedActions = message.suggestedActions,
            isLatest = isLatest,
            isStreaming = isStreaming,
            onSourceClick = onSourceClick,
            onRetry = onRetry,
            onSuggestedAction = onSuggestedAction,
            modifier = modifier
        )
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp),
            modifier = Modifier.widthIn(min = 80.dp, max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (text.isNotBlank()) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun TypewriterText(
    fullText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    charDelayMs: Long = 20L,
    content: @Composable (visibleText: String) -> Unit
) {
    content(fullText)
}

@Composable
private fun StreamingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    val dotCount by transition.animateValue(
        initialValue = 1,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotsAnimation"
    )
    Text(
        text = stringResource(R.string.brain_chat_generating) + ".".repeat(dotCount),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun AssistantBubble(
    text: String,
    sources: List<VaultItem>,
    externalSources: List<com.vaultbrain.core.integrations.model.ExternalRecord>,
    confidence: Float,
    evidence: RagEvidence?,
    responseOrigin: AiResponseOrigin?,
    isError: Boolean,
    originalQuery: String?,
    suggestedActions: List<com.vaultbrain.feature.brain.model.SuggestedAction> = emptyList(),
    isLatest: Boolean,
    isStreaming: Boolean,
    onSourceClick: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSuggestedAction: (com.vaultbrain.feature.brain.model.SuggestedAction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (text.isBlank() && evidence == null) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
            modifier = Modifier.widthIn(min = 120.dp, max = 760.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                evidence?.let { result ->
                    Text(
                        text = result.headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    result.supportingFacts.forEach { fact ->
                        Text(text = "• $fact", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (text.isNotBlank()) {
                    if (isLatest) {
                        TypewriterText(
                            fullText = text,
                            isStreaming = isStreaming
                        ) { visibleText ->
                            RevealedMarkdownText(fullText = text, visibleChars = visibleText.length)
                        }
                    } else {
                        MarkdownText(text = text)
                    }
                }
                responseOrigin?.let { origin ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            stringResource(
                                when (origin) {
                                    AiResponseOrigin.ON_DEVICE_AI -> R.string.brain_response_on_device
                                    AiResponseOrigin.CLOUD_AI -> R.string.brain_response_cloud
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val copyText = buildList {
                                evidence?.let {
                                    add(it.headline)
                                    addAll(it.supportingFacts)
                                }
                                if (text.isNotBlank()) add(text)
                            }.joinToString("\n")
                            clipboard.setText(AnnotatedString(copyText))
                            Toast.makeText(context, context.getString(R.string.brain_chat_copied), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.brain_chat_copy))
                    }
                    if (isError && originalQuery != null) {
                        TextButton(onClick = { onRetry(originalQuery) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.brain_chat_retry))
                        }
                    }
                }
            }
        }

        if (suggestedActions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedActions.forEach { action ->
                    androidx.compose.material3.ElevatedAssistChip(
                        onClick = {
                            if (action.actionId == "OPEN_ITEM") {
                                action.payload["itemId"]?.let { onSourceClick(it) }
                            } else {
                                onSuggestedAction(action)
                            }
                        },
                        label = { Text(action.label) },
                        leadingIcon = {
                            when (action.actionId) {
                                "REMIND_EXPIRY" -> Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                "ADD_COLLECTION" -> Icon(Icons.Default.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                                "OPEN_ITEM" -> Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }
            }
        }

        if (sources.isNotEmpty() || externalSources.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.brain_chat_based_on,
                    evidenceLabel(confidence),
                    sources.size + externalSources.size
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (confidence >= 0.65f) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEachIndexed { index, item ->
                    SourceCitationCard(index = index + 1, item = item, onClick = onSourceClick)
                }
                externalSources.forEachIndexed { index, record ->
                    ExternalCitationCard(index = sources.size + index + 1, record = record)
                }
            }
        }
    }
}

@Composable
private fun evidenceLabel(confidence: Float): String = stringResource(
    when {
        confidence >= 0.65f -> R.string.brain_chat_evidence_strong
        confidence >= 0.4f -> R.string.brain_chat_evidence_moderate
        else -> R.string.brain_chat_evidence_low
    }
)

@Composable
private fun WelcomePanel(
    suggestions: List<String>,
    onQueryClick: (String) -> Unit,
    isAiCoreEnabled: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.vaultbrain.core.common.R.drawable.il_empty_chat),
            contentDescription = null,
            modifier = Modifier.size(140.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        )
        Text(
            if (isAiCoreEnabled) stringResource(R.string.brain_chat_welcome)
            else "Welcome to Ask Nemory", 
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            if (isAiCoreEnabled) stringResource(R.string.brain_chat_welcome_body)
            else "Chat with Nemory to manage your vault or ask questions.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 360.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.width(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.brain_chat_private), style = MaterialTheme.typography.labelMedium)
        }
        if (suggestions.isNotEmpty()) {
            Text(
                if (isAiCoreEnabled) stringResource(R.string.brain_chat_try)
                else "Quick filters to try...",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { query ->
                Card(
                    onClick = { onQueryClick(query) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = query,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceCitationCard(
    index: Int,
    item: VaultItem,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = { onClick(item.id) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "[$index]",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val supportingText = item.subtype
                    ?: item.topics.firstOrNull()
                    ?: item.entities.firstOrNull()
                    ?: item.summary
                    ?: item.rawOcrText?.lineSequence()?.firstOrNull { it.isNotBlank() }
                supportingText?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalCitationCard(
    index: Int,
    record: com.vaultbrain.core.integrations.model.ExternalRecord,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("[$index]", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.title ?: record.source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                record.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AssistantLoadingBubble(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dots")
    val dotCount by transition.animateValue(
        initialValue = 0,
        targetValue = 4,
        typeConverter = Int.VectorConverter,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotsAnimation"
    )
    
    val baseText = stringResource(R.string.brain_chat_thinking).replace("?", "").replace("…", "")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)
        ) {
            Text(
                text = baseText + ".".repeat(dotCount),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    input: String,
    isLoading: Boolean,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onSuggestedQuery: (String) -> Unit,
    suggestions: List<String>,
    isAiCoreEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var showSuggestions by remember { mutableStateOf(true) }
    var showVoiceDialog by remember { mutableStateOf(false) }

    if (showVoiceDialog) {
        OnDeviceVoiceCaptureDialog(
            onDismiss = { showVoiceDialog = false },
            onCaptured = { text ->
                showVoiceDialog = false
                if (text.isNotBlank()) {
                    onInputChanged(if (input.isBlank()) text else "$input $text")
                }
            },
            onManualEntry = { showVoiceDialog = false }
        )
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (suggestions.isNotEmpty() && showSuggestions) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                ) {
                    items(suggestions) { query ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                onSuggestedQuery(query)
                                showSuggestions = false
                            },
                            label = { Text(query) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showVoiceDialog = true },
                    enabled = !isLoading
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.brain_chat_voice_input)
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        onInputChanged(it)
                        if (it.isNotBlank()) showSuggestions = false
                        if (it.isBlank()) showSuggestions = true
                    },
                    placeholder = {
                        Text(stringResource(R.string.brain_chat_placeholder))
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank() && !isLoading) onSend() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !isLoading
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.brain_chat_send)
                    )
                }
            }
        }
    }
}

private val BrainSuggestion.queryRes: Int
    get() = when (this) {
        BrainSuggestion.RECENT -> R.string.brain_suggestion_recent
        BrainSuggestion.EXPIRING -> R.string.brain_suggestion_expiring
        BrainSuggestion.TRAVEL -> R.string.brain_suggestion_travel
        BrainSuggestion.WARRANTIES -> R.string.brain_suggestion_warranties
        BrainSuggestion.RECURRING_SPEND -> R.string.brain_suggestion_recurring_spend
        BrainSuggestion.RECEIPTS -> R.string.brain_suggestion_receipts
        BrainSuggestion.MEDIA -> R.string.brain_suggestion_media
        BrainSuggestion.HEALTH -> R.string.brain_suggestion_health
    }

@Composable
fun PendingWriteCard(
    pending: BrainPendingWrite,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val actionDescription = when (val action = pending.preview.action) {
        BrainWriteAction.Pin -> stringResource(R.string.brain_write_pin_description)
        BrainWriteAction.Unpin -> stringResource(R.string.brain_write_unpin_description)
        BrainWriteAction.Archive -> stringResource(R.string.brain_write_archive_description)
        BrainWriteAction.Restore -> stringResource(R.string.brain_write_restore_description)
        is BrainWriteAction.SetReminder -> stringResource(
            R.string.brain_write_reminder_description,
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(action.triggerAt))
        )
        BrainWriteAction.ClearReminder -> stringResource(R.string.brain_write_clear_reminder_description)
        is BrainWriteAction.MarkMediaCompleted -> stringResource(
            when (action.kind) {
                com.vaultbrain.feature.brain.MediaCompletionKind.WATCHED -> R.string.brain_write_watched_description
                com.vaultbrain.feature.brain.MediaCompletionKind.FINISHED -> R.string.brain_write_read_description
            }
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.brain_write_confirm_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                pending.preview.item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(actionDescription, style = MaterialTheme.typography.bodyMedium)
            
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(stringResource(R.string.brain_chat_cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = !isLoading
                ) {
                    Text(stringResource(R.string.brain_write_confirm))
                }
            }
        }
    }
}
