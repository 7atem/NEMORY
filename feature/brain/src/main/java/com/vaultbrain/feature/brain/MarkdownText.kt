package com.vaultbrain.feature.brain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight markdown renderer for assistant chat messages.
 *
 * Intentionally not a full CommonMark parser: it handles only what the on-device
 * LLM actually emits — `**bold**`, `- `/`* ` bullets, `1.` numbered lists and
 * `#`/`##`/`###` headers — using Material 3 typography.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    MarkdownBlockColumn(blocks, modifier, color)
}

/**
 * Typewriter-friendly variant: parses [fullText] once and reveals already-parsed
 * blocks up to [visibleChars], instead of re-parsing a growing substring per tick.
 */
@Composable
fun RevealedMarkdownText(
    fullText: String,
    visibleChars: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val blocks = remember(fullText) { parseMarkdownBlocks(fullText) }
    val visibleBlocks = remember(blocks, visibleChars) { clipBlocks(blocks, visibleChars) }
    MarkdownBlockColumn(visibleBlocks, modifier, color)
}

private fun clipBlocks(blocks: List<MarkdownBlock>, visibleChars: Int): List<MarkdownBlock> {
    var remaining = visibleChars
    val result = mutableListOf<MarkdownBlock>()
    for (block in blocks) {
        if (remaining <= 0) break
        val text = when (block) {
            is MarkdownBlock.Header -> block.text
            is MarkdownBlock.ListItem -> block.text
            is MarkdownBlock.Paragraph -> block.text
        }
        if (text.length <= remaining) {
            result += block
            remaining -= text.length + 1
        } else {
            val clipped = text.take(remaining)
            result += when (block) {
                is MarkdownBlock.Header -> block.copy(text = clipped)
                is MarkdownBlock.ListItem -> block.copy(text = clipped)
                is MarkdownBlock.Paragraph -> block.copy(text = clipped)
            }
            break
        }
    }
    return result
}

@Composable
private fun MarkdownBlockColumn(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> Text(
                    text = boldAnnotatedString(block.text),
                    style = if (block.level <= 2) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                is MarkdownBlock.ListItem -> Row {
                    Text(
                        text = if (block.ordered) "${block.index}." else "•",
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = boldAnnotatedString(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = color
                    )
                }
                is MarkdownBlock.Paragraph -> Text(
                    text = boldAnnotatedString(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color
                )
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
}

private val NUMBERED_ITEM_REGEX = Regex("""^(\d+)[.)]\s+(.*)$""")

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> =
    text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val numbered = NUMBERED_ITEM_REGEX.matchEntire(line)
            when {
                line.startsWith("#") -> {
                    val level = line.takeWhile { it == '#' }.length.coerceAtMost(6)
                    MarkdownBlock.Header(level, line.drop(level).trim())
                }
                line.startsWith("- ") || line.startsWith("* ") ->
                    MarkdownBlock.ListItem(line.drop(2).trim(), ordered = false, index = 0)
                numbered != null -> MarkdownBlock.ListItem(
                    text = numbered.groupValues[2].trim(),
                    ordered = true,
                    index = numbered.groupValues[1].toIntOrNull() ?: 0
                )
                else -> MarkdownBlock.Paragraph(line)
            }
        }

/**
 * Renders `**bold**` spans as bold; unmatched markers are shown literally.
 */
private fun boldAnnotatedString(text: String): AnnotatedString = buildAnnotatedString {
    val segments = text.split("**")
    if (segments.size < 3) {
        append(text)
        return@buildAnnotatedString
    }
    segments.forEachIndexed { index, segment ->
        if (index % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(segment) }
        } else {
            append(segment)
        }
    }
}
