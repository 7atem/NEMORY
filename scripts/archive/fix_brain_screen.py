import re

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove the pendingWrite?.let block
pending_write_block_start = content.find('pendingWrite?.let { pending ->')
if pending_write_block_start != -1:
    brace_count = 1
    idx = pending_write_block_start + len('pendingWrite?.let { pending ->')
    
    while idx < len(content) and brace_count > 0:
        if content[idx] == '{': brace_count += 1
        elif content[idx] == '}': brace_count -= 1
        idx += 1
        
    pending_write_block_end = idx
    content = content[:pending_write_block_start] + content[pending_write_block_end:]


# 2. Insert PendingWriteCard into the items loop
insertion_point = content.find('                            ChatBubble(')
if insertion_point != -1:
    # Find the closing brace of AnimatedVisibility
    # Wait, it's easier to just append it below the ChatBubble
    chat_bubble_end = content.find('                            )', insertion_point)
    chat_bubble_end = content.find('\n', chat_bubble_end)
    
    card_code = """
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
                            }"""
    content = content[:chat_bubble_end] + card_code + content[chat_bubble_end:]

# 3. Append PendingWriteCard composable definition to the file
composable_code = """
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
                com.vaultbrain.feature.brain.model.MediaCompletionKind.WATCHED -> R.string.brain_write_watched_description
                com.vaultbrain.feature.brain.model.MediaCompletionKind.FINISHED -> R.string.brain_write_read_description
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
"""
content += composable_code

with open(r'd:\Vault Brain\feature\brain\src\main\java\com\vaultbrain\feature\brain\BrainChatScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
