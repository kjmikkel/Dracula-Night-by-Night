package com.jensen.draculanightbynight.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.onFocusedBoundsChanged
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.dp

/**
 * Draws a 2 dp ring in the theme's primary colour round whatever inside has keyboard focus: Material's own focus
 * wash is about 1.1:1 against the app's backgrounds (#113). One modifier covers Material's controls and the app's own,
 * but only within its window, so the app's root and every dialog and sheet each need it. In touch mode only text
 * fields take focus, so a ring otherwise shows only for keyboard and switch users.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.focusRings(): Modifier = composed {
    val color = MaterialTheme.colorScheme.primary
    val self = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var ring by remember { mutableStateOf<Rect?>(null) }
    this
        .onPlaced { self[0] = it }
        .onFocusedBoundsChanged { focused ->
            val own = self[0]
            ring = if (focused != null && focused.isAttached && own != null && own.isAttached) own.localBoundingBoxOf(focused) else null
        }
        .drawWithContent {
            drawContent()
            val r = ring
            if (r != null) {
                val width = RING_WIDTH.toPx()
                // Inside the focused element's bounds, so a parent that clips can't cut it off.
                drawRoundRect(
                    color,
                    topLeft = r.topLeft + Offset(width / 2, width / 2),
                    size = Size(r.width - width, r.height - width),
                    cornerRadius = CornerRadius(RING_CORNER.toPx()),
                    style = Stroke(width),
                )
            }
        }
}

private val RING_WIDTH = 2.dp
private val RING_CORNER = 8.dp
