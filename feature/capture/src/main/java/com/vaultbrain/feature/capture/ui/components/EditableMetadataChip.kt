package com.vaultbrain.feature.capture.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.vaultbrain.core.common.util.humanize
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * An inline editable metadata chip that shows a key-value pair.
 *
 * - Default state: displays as a compact chip showing "Key: Value"
 * - Editing state: expands into an inline text field
 * - Edited values are highlighted in a different color to indicate user correction
 */
@Composable
fun EditableMetadataChip(
    key: String,
    value: String,
    isUserEdited: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var editing by remember { mutableStateOf(false) }
    var editText by remember(value) { mutableStateOf(value) }

    val containerColor by animateColorAsState(
        targetValue = when {
            isUserEdited -> MaterialTheme.colorScheme.tertiaryContainer
            editing -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        label = "chipColor"
    )
    val contentColor = when {
        isUserEdited -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    if (editing) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = key.humanize(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            onValueChange(editText.trim())
                            editing = false
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = contentColor.copy(alpha = 0.3f)
                    ),
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    onValueChange(editText.trim())
                                    editing = false
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = {
                                    editText = value
                                    editing = false
                                }
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                )
            }
        }
    } else {
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                editing = true
            },
            shape = MaterialTheme.shapes.medium,
            color = containerColor,
            contentColor = contentColor,
            modifier = modifier
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${key.humanize()}: $value",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(14.dp),
                    tint = contentColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}
