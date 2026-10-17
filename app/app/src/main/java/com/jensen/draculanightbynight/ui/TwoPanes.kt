package com.jensen.draculanightbynight.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of the timeline pane beside an open entry. */
val TIMELINE_PANE_WIDTH = 360.dp

private val TWO_PANE_MIN_WIDTH = 600.dp

/**
 * Matches `SHORT_WINDOW_DP` in the timeline: the height rule is what keeps sideways phones on one
 * pane, since a phone turned on its side is wide but short.
 */
private val TWO_PANE_MIN_HEIGHT = 480.dp

/** Whether a window of this size has room for the timeline and an entry side by side. */
fun twoPanes(width: Dp, height: Dp): Boolean = width >= TWO_PANE_MIN_WIDTH && height >= TWO_PANE_MIN_HEIGHT

/** [twoPanes] for the current window, measured in raw window dp (text size must not change the layout). */
@Composable
fun windowIsWide(): Boolean {
    val density = LocalDensity.current
    val size = LocalWindowInfo.current.containerSize
    return with(density) { twoPanes(size.width.toDp(), size.height.toDp()) }
}
