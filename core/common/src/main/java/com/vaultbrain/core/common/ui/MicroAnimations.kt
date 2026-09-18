package com.vaultbrain.core.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Micro-animation utilities for premium feel and smooth interactions.
 * Includes reveal animations, placement animations, and transitions.
 */

/**
 * Standard enter animation for list/grid items appearing.
 * Combines slide-in from left + fade-in for smooth appearance.
 */
val standardEnterAnimation: EnterTransition = slideInHorizontally(
    initialOffsetX = { -it / 2 },
    animationSpec = tween(300)
) + fadeIn(animationSpec = tween(300))

/**
 * Standard exit animation for list/grid items disappearing.
 * Combines slide-out to left + fade-out.
 */
val standardExitAnimation: ExitTransition = slideOutHorizontally(
    targetOffsetX = { -it / 2 },
    animationSpec = tween(200)
) + fadeOut(animationSpec = tween(200))

/**
 * Vertical reveal animation (slides up from bottom).
 * Useful for: bottom sheets, expanding panels, detail views.
 */
val verticalRevealEnter: EnterTransition = slideInVertically(
    initialOffsetY = { it / 2 },
    animationSpec = tween(350)
) + fadeIn(animationSpec = tween(350))

/**
 * Vertical collapse animation (slides down to bottom).
 * Pair with verticalRevealEnter for reversible transitions.
 */
val verticalRevealExit: ExitTransition = slideOutVertically(
    targetOffsetY = { it / 2 },
    animationSpec = tween(250)
) + fadeOut(animationSpec = tween(250))

/**
 * Expand/collapse animation for collapsible content.
 * Useful for: expandable cards, disclosure panels.
 */
val expandCollapseEnter: EnterTransition = expandVertically(
    expandFrom = Alignment.Top,
    animationSpec = tween(300)
) + fadeIn(animationSpec = tween(300))

val expandCollapseExit: ExitTransition = shrinkVertically(
    shrinkTowards = Alignment.Top,
    animationSpec = tween(250)
) + fadeOut(animationSpec = tween(250))

/**
 * Staggered entrance for sequential items (lists, grids).
 * Calculates delay based on item index for cascading effect.
 *
 * @param index Item position in list/grid
 * @param maxItems Total number of items (for calculating stagger percentage)
 * @param maxDelayMs Maximum delay before first item enters (default: 300ms)
 */
fun getStaggeredEnter(
    index: Int,
    maxItems: Int,
    maxDelayMs: Int = 300
): EnterTransition {
    val delayMs = (index * maxDelayMs) / maxOf(maxItems, 1)
    return slideInVertically(
        initialOffsetY = { 50 },
        animationSpec = tween(durationMillis = 350, delayMillis = delayMs)
    ) + fadeIn(animationSpec = tween(350, delayMs))
}

/**
 * Animated visibility wrapper that reveals content with smooth entrance.
 * Replaces plain Visibility for items that need premium feel.
 *
 * @param visible Whether content is visible
 * @param modifier Composable modifier
 * @param enter Enter animation (default: standardEnterAnimation)
 * @param exit Exit animation (default: standardExitAnimation)
 * @param content Composable content to show/hide
 */
@Composable
fun RevealingAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = standardEnterAnimation,
    exit: ExitTransition = standardExitAnimation,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = { content() }
    )
}

/**
 * Grid item wrapper that animates appearance when added to grid.
 * Used with LazyVerticalStaggeredGrid for smooth item placement animations.
 *
 * @param modifier Base modifier (typically passed from grid item lambda)
 * @param index Item index in grid (used for staggered delay)
 * @param totalItems Total items in grid
 * @param content Grid item composable
 */
@Composable
fun LazyGridItemScope.AnimatedGridItem(
    modifier: Modifier = Modifier,
    index: Int = 0,
    totalItems: Int = 0,
    content: @Composable () -> Unit
) {
    this.apply {
        RevealingAnimatedVisibility(
            visible = true,
            modifier = modifier.animateItem(),
            enter = getStaggeredEnter(index, totalItems),
            exit = standardExitAnimation,
            content = content
        )
    }
}

