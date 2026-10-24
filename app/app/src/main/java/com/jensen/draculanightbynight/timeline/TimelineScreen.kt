package com.jensen.draculanightbynight.timeline

import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.animateContentSize
import com.jensen.draculanightbynight.ui.largeText
import com.jensen.draculanightbynight.ui.focusRings
import com.jensen.draculanightbynight.ui.notSpoken
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.R
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.typeLabel
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.ui.Ornament
import com.jensen.draculanightbynight.ui.StartOverDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel,
    onOpen: (id: String, filtered: Boolean) -> Unit,
    onBookmarks: () -> Unit,
    onSettings: () -> Unit,
    /** The entry open in the other pane; its row is highlighted and kept in view. Null on a phone. */
    selectedId: String? = null,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var startingOver by rememberSaveable { mutableStateOf(false) }
    var confirmingSkip by rememberSaveable { mutableStateOf(false) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    val filter = (ui as? TimelineUi.Ready)?.timeline?.filter ?: EntryFilter()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                // At large text the title wraps to three lines here; it moves to the top of the list instead (#151).
                title = { if (!largeText()) AppTitle() },
                actions = {
                    IconButton(onClick = { filtering = true }, enabled = ui is TimelineUi.Ready) {
                        val label = if (filter.isActive) "Filter, ${filter.activeCount} active" else "Filter"
                        BadgedBox(badge = { if (filter.isActive) Badge() }) { Icon(painterResource(R.drawable.ic_filter), contentDescription = label) }
                    }
                    IconButton(onClick = onBookmarks) { Icon(painterResource(R.drawable.ic_bookmarks), contentDescription = "Bookmarks") }
                    IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings") }
                },
            )
        },
    ) { padding ->
        when (val state = ui) {
            TimelineUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is TimelineUi.Ready -> {
                Timeline(
                    state.timeline,
                    canSkip = state.mode != ReadingMode.Free,
                    onOpen = { id -> onOpen(id, state.timeline.filter.isActive) },
                    onClearFilter = viewModel::clearFilter,
                    // The first skip explains itself; once ahead, further skips need no confirmation.
                    onReadItNow = { if (state.timeline.skipAhead.isZero) confirmingSkip = true else viewModel.readItNow() },
                    onBackToRealTime = viewModel::backToRealTime,
                    onStartOver = { startingOver = true },
                    onNotNow = viewModel::dismissSeasonPrompt,
                    selectedId = selectedId,
                    modifier = Modifier.padding(padding),
                )
                if (confirmingSkip) {
                    AlertDialog(
                        modifier = Modifier.focusRings(),
                        onDismissRequest = { confirmingSkip = false },
                        title = { Text("Read it now?") },
                        text = { Text("The next entry arrives early, and later ones keep the same head start. Back to real time undoes it.") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.readItNow()
                                confirmingSkip = false
                            }) { Text("Read it now") }
                        },
                        dismissButton = { TextButton(onClick = { confirmingSkip = false }) { Text("Cancel") } },
                    )
                }
                if (filtering) {
                    FilterSheet(state.timeline.filter, state.timeline.voices, viewModel::toggleVoice, viewModel::toggleType, viewModel::clearFilter) { filtering = false }
                }
                if (startingOver) {
                    StartOverDialog(
                        bookmarks = state.bookmarks,
                        sameTempo = state.mode is ReadingMode.SameTempo,
                        today = state.today,
                        onDismiss = { startingOver = false },
                    ) { removeBookmarks, tempoStart ->
                        viewModel.startOver(removeBookmarks, tempoStart)
                        startingOver = false
                    }
                }
            }
        }
    }
}

@Composable
private fun Timeline(
    state: TimelineState,
    canSkip: Boolean,
    onOpen: (String) -> Unit,
    onReadItNow: () -> Unit,
    onBackToRealTime: () -> Unit,
    onStartOver: () -> Unit,
    onNotNow: () -> Unit,
    onClearFilter: () -> Unit,
    selectedId: String?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Opens at the first unread entry, and again whenever the filter changes (the old position means nothing in
    // the new list); otherwise the list stays where the reader left it.
    // On a short window (landscape, or large text) the cards join the list instead of crowding it out.
    // Measured in lines of text rather than dp, so large text counts as a short window too.
    val density = LocalDensity.current
    // At large text the app's title leads the list too, above the cards, and scrolls away with them.
    val large = largeText()
    val cardsInList = large || with(density) { LocalWindowInfo.current.containerSize.height.toDp() } / density.fontScale < SHORT_WINDOW_DP.dp
    var anchoredFor by rememberSaveable { mutableStateOf<String?>(null) }
    // Pinned cards shrink to a line once the reader scrolls the list, and grow again back at its top (#180).
    var cardsShrunk by rememberSaveable { mutableStateOf(false) }
    val ownScroll = remember(listState) { OwnScroll(listState) }
    LaunchedEffect(state.filter) {
        val key = state.filter.summary
        if (anchoredFor != key) {
            // With the cards in the list it opens at the top, so they show; the Tonight card leads to the first unread.
            ownScroll { scrollToItem(if (cardsInList) 0 else openingItem(state)) }
            cardsShrunk = false
            anchoredFor = key
        }
    }
    // Rotating into a short window moves the cards into the list, where the kept scroll position would leave them
    // just above the screen: scroll up so they show, as when the timeline opens in a short window.
    var cardsWereInList by rememberSaveable { mutableStateOf(cardsInList) }
    LaunchedEffect(cardsInList) {
        if (cardsInList && !cardsWereInList) {
            ownScroll { scrollToItem(0) }
            cardsShrunk = false
        }
        cardsWereInList = cardsInList
    }
    // Only the reader's own scrolling counts: positions are watched from after the opening anchor, the first one seen
    // (the anchor's, or the one restored after rotation) is skipped, and so are the app's own scrolls.
    LaunchedEffect(cardsInList, state.filter.summary) {
        if (cardsInList) return@LaunchedEffect
        val key = state.filter.summary
        snapshotFlow { anchoredFor == key }.first { it }
        // A list that fits once the cards shrink is pushed back to its top by that alone; growing them then would make it
        // scroll again, and the cards would flicker between the two. So they only grow back while there's still more below.
        snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, listState.canScrollForward) }
            .drop(1)
            .collect { (index, offset, more) ->
                if (ownScroll.explains(index, offset)) return@collect
                val atTop = index == 0 && offset == 0
                if (!atTop) cardsShrunk = true else if (more) cardsShrunk = false
            }
    }
    // Keeps the open entry's row on screen as the reader moves between entries; runs after the opening position.
    LaunchedEffect(selectedId) {
        val item = selectedId?.let { rowItem(state, it) }?.let { if (cardsInList) it + 1 else it } ?: return@LaunchedEffect
        // Wait for a real layout after the opening anchor ran: before that nothing is visible yet, and a restored
        // scroll position (rotation, folding) must not be replaced.
        val key = state.filter.summary
        snapshotFlow { anchoredFor == key && listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == item }) ownScroll { animateScrollToItem(item) }
    }
    // In the list (short window) the cards scroll away instead, so they never shrink.
    val shrink = cardsShrunk && !cardsInList
    val cards: @Composable () -> Unit = {
        if (large) AppTitle(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        state.tonight?.let { if (shrink) TonightStrip(it, onOpen) else TonightCardView(it, onOpen) }
        if (!shrink && state.seasonPromptDue) SeasonCard(onStartOver, onNotNow)
        if (!shrink && !state.skipAhead.isZero) AheadBanner(state, onBackToRealTime)
        if (state.filter.isActive) FilteredLine(state, onClearFilter, oneLine = shrink)
        if (!shrink) Ornament()
    }

    // The cards stay pinned above the list: the list opens scrolled to the first unread entry, which would
    // otherwise hide them exactly when they matter.
    Column(modifier.fillMaxSize()) {
        // Shrinking and growing animate, so the list doesn't jump under the reader's finger.
        if (!cardsInList) Column(Modifier.animateContentSize()) { cards() }
        TimelineList(state, listState, canSkip, selectedId, onOpen, onReadItNow, onStartOver, onClearFilter, header = cards.takeIf { cardsInList })
    }
}

@Composable
private fun AppTitle(modifier: Modifier = Modifier) {
    Text("Dracula Night by Night", modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

/** Below this window height the pinned cards become the first item of the list. */
private const val SHORT_WINDOW_DP = 480

@Composable
private fun TonightCardView(card: TonightCard, onOpen: (String) -> Unit) {
    // The tag lets the accessibility ratchet count the card's TalkBack stops.
    Card(Modifier.testTag("tonight-card").fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(card.heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            // One TalkBack stop for the summary: heading, summary, Read.
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                if (card.waiting > 1) Text("${card.waiting} entries waiting", style = MaterialTheme.typography.bodyMedium)
                Text("${card.authorName} · ${typeLabel(card.type)}", style = MaterialTheme.typography.labelLarge)
                Text(card.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            ReadButton(card, onOpen, Modifier.padding(top = 8.dp))
        }
    }
}

/** Tells the app's own scrolls of [list] (the opening anchor, following the open entry) from the reader's. */
private class OwnScroll(private val list: LazyListState) {
    private var running = 0
    private var landedAt: Pair<Int, Int>? = null

    suspend operator fun invoke(scroll: suspend LazyListState.() -> Unit) {
        running++
        try { list.scroll() } finally {
            running--
            landedAt = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset
        }
    }

    /** Whether the list being at [index] and [offset] is down to one of the app's scrolls rather than the reader. */
    fun explains(index: Int, offset: Int): Boolean {
        if (running > 0) return true
        val landed = landedAt == index to offset
        landedAt = null
        return landed
    }
}

/** The Tonight card shrunk to one line while the reader scrolls the list (#180): still the way to read it. */
@Composable
private fun TonightStrip(card: TonightCard, onOpen: (String) -> Unit) {
    Card(Modifier.testTag("tonight-strip").fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(card.heading, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(" · ${card.authorName}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ReadButton(card, onOpen)
        }
    }
}

/** TalkBack names what Read opens, since the button's own text doesn't say. */
@Composable
private fun ReadButton(card: TonightCard, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { onOpen(card.entryId) },
        modifier = modifier.semantics { contentDescription = "Read ${card.authorName}, ${typeLabel(card.type)}" },
    ) { Text("Read", Modifier.notSpoken()) }
}

@Composable
private fun TimelineList(
    state: TimelineState,
    listState: LazyListState,
    canSkip: Boolean,
    selectedId: String?,
    onOpen: (String) -> Unit,
    onReadItNow: () -> Unit,
    onStartOver: () -> Unit,
    onClearFilter: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("timeline-list"), state = listState) {
        header?.let { cards -> item(key = "cards") { Column { cards() } } }
        if (state.days.isEmpty() && state.hiddenCount > 0) {
            item(key = "no-match") {
                Column(Modifier.padding(16.dp)) {
                    Text("No entries match yet.", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onClearFilter) { Text("Clear filter") }
                }
            }
        } else if (state.days.isEmpty()) {
            item { Text("The story has not begun yet.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        }
        for (day in state.days) {
            item(key = "day-${day.date}") {
                Text(
                    day.date.format(Dates.DAY),
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 6.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            items(day.rows, key = { it.id }) { row ->
                EntryRow(row, selected = row.id == selectedId, onClick = { onOpen(row.id) })
                HorizontalDivider()
            }
        }
        // Nothing left to arrive is not the end until it has all been read (#189). In Read freely nothing ever
        // arrives, so only the end itself shows there.
        item(key = "next") {
            when {
                state.finished && state.allRead -> EndCard(onStartOver)
                state.finished -> if (canSkip) AllArrivedCard(state.unreadCount)
                else -> NextCard(state, canSkip, onReadItNow)
            }
        }
    }
}

@Composable
private fun EntryRow(row: TimelineRow, selected: Boolean, onClick: () -> Unit) {
    // On the highlight everything uses the one colour the palette checks against it.
    val onSelected = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else null
    Row(
        Modifier.fillMaxWidth()
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer).semantics { this.selected = true } else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.widthIn(min = 52.dp)) {
            Text(
                row.timeLabel,
                // Italics mark an estimated time; TalkBack says it in words, as the reader does.
                if (row.timeEstimated) Modifier.clearAndSetSemantics { contentDescription = "about ${row.timeLabel}" } else Modifier,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = if (row.timeEstimated) FontStyle.Italic else FontStyle.Normal,
                color = onSelected ?: if (row.timeEstimated) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.unread) {
                    Box(Modifier.size(8.dp).background(onSelected ?: MaterialTheme.colorScheme.primary, CircleShape).semantics { contentDescription = "Unread" })
                }
                // Wraps rather than pushing the star off the edge at large text.
                Text("${row.authorName} · ${typeLabel(row.type)}${row.place?.let { " · $it" } ?: ""}", Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge, color = onSelected ?: Color.Unspecified)
                if (row.bookmarked) Text("★", color = onSelected ?: MaterialTheme.colorScheme.primary, modifier = Modifier.semantics { contentDescription = "Bookmarked" })
            }
            Text(row.preview, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = onSelected ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NextCard(state: TimelineState, canSkip: Boolean, onReadItNow: () -> Unit) {
    val next = state.next ?: return
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${next.authorName} · ${typeLabel(next.type)}", style = MaterialTheme.typography.titleMedium)
            Text(next.unlocksAt.format(Dates.WEEKDAY_TIME), style = MaterialTheme.typography.bodyMedium)
            if (canSkip) TextButton(onClick = onReadItNow) { Text("Read it now") }
        }
    }
}

@Composable
private fun EndCard(onStartOver: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("The end.", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("You have read Dracula night by night.", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onStartOver) { Text("Start over") }
        }
    }
}

/** Every entry has arrived, but not every one has been read. */
@Composable
private fun AllArrivedCard(unread: Int) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Everything has arrived", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (unread == 1) "1 entry is still unread." else "$unread entries are still unread.", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun SeasonCard(onStartOver: () -> Unit, onNotNow: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Dracula begins again on 3 May", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Start over and read it again this year?", style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = onStartOver) { Text("Start over") }
                TextButton(onClick = onNotNow) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun FilteredLine(state: TimelineState, onClear: () -> Unit, oneLine: Boolean = false) {
    Row(
        Modifier.testTag("filtered-line").fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Shrunk to one line (#180), a long summary scrolls sideways rather than wrapping; Clear stays in reach.
        val text = Modifier.weight(1f).then(if (oneLine) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
        Text(
            "Filtered: ${state.filter.summary} · ${state.hiddenCount} hidden",
            text.padding(vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (oneLine) 1 else Int.MAX_VALUE,
            softWrap = !oneLine,
        )
        TextButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun AheadBanner(state: TimelineState, onBackToRealTime: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(role = Role.Button, onClick = onBackToRealTime)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Reading ${aheadLabel(state.skipAhead)} ahead", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("Back to real time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
