package com.jensen.draculanightbynight.bookmarks

import androidx.compose.ui.semantics.clearAndSetSemantics
import com.jensen.draculanightbynight.ui.notSpoken
import com.jensen.draculanightbynight.ui.BackButton
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.R
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.typeLabel
import com.jensen.draculanightbynight.user.BookmarkSort
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(viewModel: BookmarksViewModel, onBack: () -> Unit, onOpen: (id: String) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // The entry id, so the dialog survives rotation; the row itself is looked up in the current list.
    var confirmingId by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Bookmarks", Modifier.semantics { heading() }) }, navigationIcon = { BackButton(onBack) })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (val state = ui) {
            BookmarksUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is BookmarksUi.Ready -> Column(Modifier.fillMaxSize().padding(padding)) {
                SortSwitch(state.sort, viewModel::setSort)
                if (state.rows.isEmpty()) {
                    Text(
                        "No bookmarks yet. Tap ☆ Bookmark while reading to keep an entry here.",
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(state.rows, key = { it.row.id }) { row ->
                        BookmarkRowItem(
                            row,
                            showMade = state.sort == BookmarkSort.RECENT,
                            onOpen = { if (row.arrivesAt == null) onOpen(row.row.id) else confirmingId = row.row.id },
                            onRemove = {
                                viewModel.remove(row)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val named = "Removed ${row.row.authorName}, ${row.date.format(Dates.DAY)}"
                                    if (snackbar.showSnackbar(named, actionLabel = "Undo") == SnackbarResult.ActionPerformed) viewModel.undo(row)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    val confirming = confirmingId?.let { id -> (ui as? BookmarksUi.Ready)?.rows?.firstOrNull { it.row.id == id && it.arrivesAt != null } }
    confirming?.let { row -> ReadNowDialog(row, viewModel.skipCount(row) - 1, onDismiss = { confirmingId = null }) {
        confirmingId = null
        viewModel.readNow(row, onOpen)
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSwitch(sort: BookmarkSort, onSort: (BookmarkSort) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        SegmentedButton(sort == BookmarkSort.STORY, { onSort(BookmarkSort.STORY) }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Story order") }
        SegmentedButton(sort == BookmarkSort.RECENT, { onSort(BookmarkSort.RECENT) }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("Recently bookmarked") }
    }
}

@Composable
private fun BookmarkRowItem(row: BookmarkRow, showMade: Boolean, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    val r = row.row
    val arrives = row.arrivesAt?.format(Dates.WEEKDAY_TIME)
    // Locked rows are marked by a lighter background and a lock, never by fainter text.
    Row(
        modifier.fillMaxWidth()
            .background(if (arrives != null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f)
                .clickable(onClickLabel = if (arrives != null) "Read it now" else "Open", onClick = onOpen)
                .then(
                    // A locked row's description names the entry too, so it replaces the texts rather than adding to them.
                    lockedDescription(row)?.let { d -> Modifier.clearAndSetSemantics { contentDescription = d } }
                        ?: Modifier.semantics(mergeDescendants = true) {},
                ),
        ) {
            Text("${row.date.format(Dates.DAY)}, ${r.timeLabel}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("${r.authorName} · ${typeLabel(r.type)}${r.place?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.labelLarge)
            Text(r.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (arrives != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_lock), contentDescription = null, Modifier.size(16.dp))
                    Text("Arrives again $arrives", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (showMade) {
                row.bookmarkedOn?.let {
                    Text("Bookmarked ${it.format(Dates.SHORT_DAY)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.semantics { contentDescription = "Remove bookmark, ${r.authorName}, ${row.date.format(Dates.DAY)}" },
        ) { Text("★", Modifier.notSpoken(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun ReadNowDialog(row: BookmarkRow, earlier: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val arrives = row.arrivesAt?.format(Dates.WEEKDAY_TIME).orEmpty()
    val also = when {
        earlier <= 0 -> ""
        earlier == 1 -> " Reading it now also makes 1 earlier entry readable"
        else -> " Reading it now also makes $earlier earlier entries readable"
    }
    val text = "This entry arrives again $arrives." + if (also.isEmpty()) " Reading it now puts you ahead of real time." else "$also and puts you ahead of real time."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read it now?") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Read it now") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
