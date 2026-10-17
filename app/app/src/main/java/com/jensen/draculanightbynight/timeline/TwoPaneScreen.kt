package com.jensen.draculanightbynight.timeline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.reader.PaneControls
import com.jensen.draculanightbynight.reader.ReaderScreen
import com.jensen.draculanightbynight.reader.ReaderViewModel
import com.jensen.draculanightbynight.ui.TIMELINE_PANE_WIDTH

/**
 * The timeline, and once an entry is open ([reader] not null) the entry beside it (#58). Without a reader it is the
 * timeline as it always was. With one, the timeline is a [TIMELINE_PANE_WIDTH] pane on the left, unless
 * [timelineHidden], and Back closes the entry.
 */
@Composable
fun TwoPaneScreen(
    timeline: TimelineViewModel,
    reader: ReaderViewModel?,
    timelineHidden: Boolean,
    onOpen: (id: String, filtered: Boolean) -> Unit,
    onClose: () -> Unit,
    onToggleTimeline: () -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
) {
    // Keeps the hidden timeline's list position and opening anchor for when it shows again.
    val saveable = rememberSaveableStateHolder()
    val selectedId = reader?.currentId?.collectAsStateWithLifecycle()?.value
    Row(Modifier.fillMaxSize()) {
        // Always the first child, so the timeline keeps its place as the reader comes and goes.
        if (reader == null || !timelineHidden) {
            saveable.SaveableStateProvider("timeline") {
                TimelineScreen(
                    timeline, onOpen, onBookmarks, onSettings,
                    selectedId = selectedId,
                    modifier = if (reader != null) Modifier.width(TIMELINE_PANE_WIDTH) else Modifier.weight(1f),
                )
            }
            if (reader != null) VerticalDivider()
        }
        if (reader != null) {
            ReaderScreen(reader, onBack = onClose, pane = PaneControls(!timelineHidden, onToggleTimeline, onClose), modifier = Modifier.weight(1f))
            BackHandler(onBack = onClose)
        }
    }
}
