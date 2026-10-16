package com.jensen.draculanightbynight.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Lets a keyboard reader take focus in a list and scroll it (#114): arrows by a line, Page Up/Down and Space by most
 * of a screen, Home and End to either end. At the end a key is left alone, so it moves focus on as usual.
 */
fun Modifier.keyboardScroll(state: LazyListState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val line = with(LocalDensity.current) { LINE.toPx() }
    this
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val page = state.layoutInfo.viewportSize.height * PAGE_FRACTION
            val by = when (event.key) {
                Key.DirectionDown -> line
                Key.DirectionUp -> -line
                Key.PageDown -> page
                Key.PageUp -> -page
                Key.Spacebar -> if (event.isShiftPressed) -page else page
                Key.MoveHome -> {
                    if (!state.canScrollBackward) return@onKeyEvent false
                    scope.launch { state.animateScrollToItem(0) }
                    return@onKeyEvent true
                }
                Key.MoveEnd -> {
                    if (!state.canScrollForward) return@onKeyEvent false
                    scope.launch { state.animateScrollToItem(state.layoutInfo.totalItemsCount - 1) }
                    return@onKeyEvent true
                }
                else -> return@onKeyEvent false
            }
            if (by > 0 && !state.canScrollForward || by < 0 && !state.canScrollBackward) return@onKeyEvent false
            scope.launch { state.animateScrollBy(by) }
            true
        }
        .focusable()
}

/** About a line of reading text. */
private val LINE = 48.dp

/** A page keeps a little of the last screen in view, so the reader doesn't lose their place. */
private const val PAGE_FRACTION = 0.85f
