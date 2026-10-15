package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.typeLabel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(viewModel: TimelineViewModel, onOpen: (String) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var choosingMode by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dracula Night by Night") },
                actions = { TextButton(onClick = { choosingMode = true }) { Text("Mode") } },
            )
        },
    ) { padding ->
        when (val state = ui) {
            TimelineUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is TimelineUi.Ready -> {
                Timeline(state.timeline, onOpen, Modifier.padding(padding))
                if (choosingMode) {
                    ModeDialog(current = state.mode, today = state.today, onDismiss = { choosingMode = false }) {
                        viewModel.setMode(it)
                        choosingMode = false
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(state: TimelineState, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    // Item index of each row: one header item per day, then its rows.
    val firstUnreadIndex = remember(state.firstUnreadId, state.days) {
        var index = 0
        var found = -1
        for (day in state.days) {
            index++
            for (row in day.rows) {
                if (row.id == state.firstUnreadId) found = index
                index++
            }
        }
        found
    }
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(firstUnreadIndex) {
        if (!scrolled && firstUnreadIndex > 0) listState.scrollToItem((firstUnreadIndex - 1).coerceAtLeast(0))
        scrolled = true
    }

    LazyColumn(modifier.fillMaxSize(), state = listState) {
        if (state.days.isEmpty()) {
            item { Text("The story has not begun yet.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        for (day in state.days) {
            item(key = "day-${day.date}") {
                Text(
                    day.date.format(Dates.DAY),
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(day.rows, key = { it.id }) { row ->
                EntryRow(row, onClick = { onOpen(row.id) })
                HorizontalDivider()
            }
        }
        item(key = "next") { NextCard(state) }
    }
}

@Composable
private fun EntryRow(row: TimelineRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(52.dp)) {
            Text(
                row.timeLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (row.timeEstimated) FontStyle.Italic else FontStyle.Normal,
                color = if (row.timeEstimated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.unread) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = "Unread" })
                }
                Text("${row.authorName} · ${typeLabel(row.type)}${row.place?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelLarge)
                if (row.bookmarked) Text("★", color = MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "Bookmarked" })
            }
            Text(row.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NextCard(state: TimelineState) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            val next = state.next
            if (next == null) {
                Text("${state.unlockedCount} of ${state.total} entries unlocked.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text("${next.authorName} · ${typeLabel(next.type)}", style = MaterialTheme.typography.titleMedium)
                Text(next.unlocksAt.format(Dates.WEEKDAY_TIME), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
