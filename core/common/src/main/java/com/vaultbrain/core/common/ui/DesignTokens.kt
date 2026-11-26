package com.vaultbrain.core.common.ui

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale. Screens should use these steps instead of ad-hoc values so
 * rhythm stays consistent across features.
 */
@Immutable
object VaultSpacing {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Minimum touch target for interactive elements (Material / WCAG 2.5.8 comfortable size). */
    val minTouchTarget: Dp = 48.dp

    /** Bottom padding for scrolling content so the last row clears the FAB. */
    val fabClearance: Dp = 88.dp
}

/**
 * Motion preference: true when the user turned animations off (Developer options or the
 * accessibility "Remove animations" switch, which both set the animator duration scale to 0).
 * Provided by the app theme; defaults to false for previews and tests.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** Pure mapping from the system animator duration scale to a reduced-motion preference. */
fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

/** Reads the system animator duration scale once per composition of the theme root. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
        isReducedMotion(scale)
    }
}

/**
 * Non-interactive label pill. Use for status, tags and metadata that are informational —
 * never use clickable chips with empty handlers, which TalkBack announces as buttons and
 * which inflate layouts to a 48dp touch target.
 */
@Composable
fun InfoPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingIcon: ImageVector? = null,
    leadingText: String? = null
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = VaultSpacing.sm, vertical = VaultSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.xs)
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            if (leadingText != null) {
                Text(leadingText, style = MaterialTheme.typography.labelMedium)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Section title announced as a heading so TalkBack users can jump between sections. */
@Composable
fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() }
    )
}
