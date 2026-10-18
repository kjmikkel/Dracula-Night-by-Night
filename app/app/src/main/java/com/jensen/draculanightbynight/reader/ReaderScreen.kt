package com.jensen.draculanightbynight.reader

import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import com.jensen.draculanightbynight.ui.keyboardScroll
import com.jensen.draculanightbynight.ui.largeText
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import com.jensen.draculanightbynight.ui.notSpoken
import com.jensen.draculanightbynight.ui.BackButton
import com.jensen.draculanightbynight.ui.CloseButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.storyTime
import com.jensen.draculanightbynight.content.typeLabel
import com.jensen.draculanightbynight.ui.DisplayCapsFamily
import com.jensen.draculanightbynight.ui.LocalReadingStyle
import com.jensen.draculanightbynight.ui.talkBackOn
import com.jensen.draculanightbynight.ui.Ornament
import androidx.compose.ui.platform.testTag
import com.jensen.draculanightbynight.ui.TelegramStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val SAVE_DEBOUNCE_MILLIS = 500L
private val NEWSPAPER_COLUMN = 520.dp

/** The split view's controls for the reader on the right: close the entry, and show or hide the timeline. */
data class PaneControls(val timelineShown: Boolean, val onToggleTimeline: () -> Unit, val onClose: () -> Unit)

/** The top bar's start: Back, or in the split view ✕ then the ☰ timeline switch. */
@Composable
private fun NavigationControls(onBack: () -> Unit, pane: PaneControls?) {
    if (pane == null) {
        BackButton(onBack)
    } else {
        Row {
            CloseButton(pane.onClose)
            // A switch for TalkBack: "Show timeline, on/off", without the glyph.
            TextButton(
                onClick = pane.onToggleTimeline,
                modifier = Modifier.semantics {
                    contentDescription = "Show timeline"
                    role = Role.Switch
                    toggleableState = ToggleableState(pane.timelineShown)
                },
            ) { Text("☰", Modifier.notSpoken()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    pane: PaneControls? = null,
    modifier: Modifier = Modifier,
    /** Opens the who's who on the author of the entry being read (#204). */
    onWhosWho: (authorId: String) -> Unit = {},
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    when (val state = ui) {
        is ReaderUi.Ready -> Pages(state, viewModel, onBack, pane, modifier, onWhosWho)
        else -> Scaffold(
            modifier = modifier,
            topBar = { TopAppBar(title = {}, navigationIcon = { NavigationControls(onBack, pane) }) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                if (state is ReaderUi.Locked) Locked(state, pane, onBack) else CircularProgressIndicator()
            }
        }
    }
}

/** An entry that hasn't arrived: when it will, and the way back (#195): Back, or in the split view Close. */
@Composable
private fun Locked(state: ReaderUi.Locked, pane: PaneControls?, onBack: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("This entry has not arrived yet.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        lockedNote(state.arrivesAt)?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        TextButton(onClick = pane?.onClose ?: onBack, Modifier.padding(top = 8.dp)) { Text(if (pane != null) "Close" else "Back") }
    }
}

/** The entries as pages that follow the finger; Previous and Next turn them too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pages(ready: ReaderUi.Ready, viewModel: ReaderViewModel, onBack: () -> Unit, pane: PaneControls?, modifier: Modifier, onWhosWho: (String) -> Unit) {
    val latest by rememberUpdatedState(ready)
    // Not saved by the pager itself: after Android kills the app the view model's saved page is the truth, and a
    // restored index could point at another entry once new ones have arrived.
    val pagerState = remember { PagerState(ready.pager.index) { latest.pager.pages.size } }
    val scope = rememberCoroutineScope()
    // Only the page the reader comes to rest on is current and counts as read; pages dragged past do not.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page -> latest.pager.pages.getOrNull(page)?.let(viewModel::onPageSettled) }
    }
    // A newly arrived entry or the switch can move the current entry to another position: follow the entry.
    LaunchedEffect(ready.pager) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != ready.pager.index) pagerState.scrollToPage(ready.pager.index)
    }
    val reader = ready.reader
    // At large text the author leads the text, where it can wrap and scrolls away, instead of collapsing to "…" in
    // the bar (#118) or taking a line under it (#151). The bars stay put: hiding them on scroll would hide Back and
    // Next from TalkBack too, whose scrolling moves them.
    val large = largeText()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { if (!large) Text(reader.authorName, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { NavigationControls(onBack, pane) },
                actions = {
                    TextButton(onClick = { onWhosWho(reader.entry.author) }) { Text("Who's who") }
                    // A switch for TalkBack: "Bookmark, on/off", without the star.
                    TextButton(
                        onClick = viewModel::toggleBookmark,
                        modifier = Modifier.semantics {
                            contentDescription = "Bookmark"
                            role = Role.Switch
                            toggleableState = ToggleableState(reader.bookmarked)
                        },
                    ) { Text(if (reader.bookmarked) "★ Bookmarked" else "☆ Bookmark", Modifier.notSpoken()) }
                },
            )
        },
    ) { padding ->
        // The bottom bar comes after the text, not in Scaffold's bottomBar: Tab follows composition order, and
        // Scaffold composes its bars first, which made Tab skip the text until after Next (#114).
        val layoutDirection = LocalLayoutDirection.current
        Column(
            Modifier.fillMaxSize().padding(
                start = padding.calculateStartPadding(layoutDirection),
                top = padding.calculateTopPadding(),
                end = padding.calculateEndPadding(layoutDirection),
            ),
        ) {
            HorizontalPager(
                pagerState,
                Modifier.weight(1f),
                key = { latest.pager.pages.getOrElse(it) { "page-$it" } },
            ) { page ->
                val shown = latest.pager.pages.getOrNull(page)?.let { latest.pages[it] }
                if (shown == null) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                } else {
                    Body(shown, viewModel, authorLine = large)
                }
            }
            BottomAppBar {
                val page = pagerState.currentPage
                val order = ready.pager.filterLabel?.takeIf { ready.pager.following == Following.FILTER }
                    ?.let { if ('+' in it) "filtered" else it }
                // At large text the buttons shrink to arrows (still labelled for TalkBack) so the switch fits.
                val compact = large
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                    enabled = page > 0,
                    modifier = Modifier.semantics { contentDescription = order?.let { "Previous $it entry" } ?: "Previous entry in the story" },
                ) { Text(if (compact) "‹" else "‹ Previous", Modifier.notSpoken()) }
                Box(Modifier.weight(1f), Alignment.Center) {
                    ready.pager.filterLabel?.let { FollowSwitch(it, ready.pager.following, viewModel::follow) }
                }
                TextButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(page + 1) } },
                    enabled = page < ready.pager.pages.lastIndex,
                    modifier = Modifier.semantics { contentDescription = order?.let { "Next $it entry" } ?: "Next entry in the story" },
                ) { Text(if (compact) "›" else "Next ›", Modifier.notSpoken()) }
            }
        }
    }
}

/** [filter | All]: which order Previous, Next and swipe follow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FollowSwitch(filterLabel: String, following: Following, onFollow: (Following) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.semantics { contentDescription = "Previous and Next follow" }) {
        SegmentedButton(
            selected = following == Following.FILTER,
            onClick = { onFollow(Following.FILTER) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(filterLabel, Modifier.widthIn(max = 120.dp), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) }
        SegmentedButton(
            selected = following == Following.ALL,
            onClick = { onFollow(Following.ALL) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text("All", maxLines = 1) }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun Body(reader: ReaderState, viewModel: ReaderViewModel, authorLine: Boolean) {
    val (startItem, startOffset) = listPosition(reader.startBlock, reader.startOffset)
    val listState = rememberLazyListState(startItem, startOffset)
    // The pixel offset is only a first guess: the block may be taller or shorter now (text size, font, screen). Once it
    // is laid out, move to the same share of it, and only then save, so the guess never overwrites the place (#200).
    var placed by remember(reader.entry.id) { mutableStateOf(reader.startPermille == null || startItem == 0) }
    LaunchedEffect(reader.entry.id) {
        if (placed) return@LaunchedEffect
        val permille = reader.startPermille ?: 0
        listState.scrollToItem(startItem)
        val height = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == startItem }?.size ?: 0
        listState.scrollToItem(startItem, offsetInto(height, permille))
        placed = true
    }
    fun save() {
        if (!placed) return
        val index = listState.firstVisibleItemIndex
        val height = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
        viewModel.savePosition(reader.entry.id, savedPosition(index, listState.firstVisibleItemScrollOffset, height))
    }
    // Save while reading (debounced), when the app goes to the background, and once more on leaving the page,
    // so a killed app still resumes where the reader was.
    LaunchedEffect(reader.entry.id) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(SAVE_DEBOUNCE_MILLIS)
            .collect { save() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { save() }
    DisposableEffect(reader.entry.id) {
        onDispose { save() }
    }
    // A newspaper is set in a narrower column, as in print.
    val column = if (reader.style == HeaderStyle.NEWSPAPER) Modifier.widthIn(max = NEWSPAPER_COLUMN) else Modifier
    // An opening dateline that only repeats the header can be skipped; TalkBack users skip it unless they choose (#177).
    // A skipped block stays as an empty item, so saved positions keep their blocks.
    val skip = reader.skipRepeatedDates ?: talkBackOn()
    val body = if (skip) reader.entry.body.map { it.withoutRepeat() } else reader.entry.body
    // The text takes keyboard focus and scrolls with the arrow and page keys (#114).
    LazyColumn(Modifier.fillMaxSize().keyboardScroll(listState).wrapContentWidth().then(column).padding(horizontal = 20.dp), state = listState) {
        // One item with the header, so saved positions (item 0 is the header) mean the same at any text size.
        item {
            Column {
                if (authorLine) Text(reader.authorName, Modifier.padding(top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                Header(reader)
            }
        }
        itemsIndexed(body) { i, block ->
            BlockText(block, atEdge = i == 0 || i == body.lastIndex, indent = indentsFirstLine(body, i), closeBelow = indentsFirstLine(body, i + 1))
        }
    }
}

@Composable
private fun Header(reader: ReaderState) {
    val e = reader.entry
    // The book's own date and time; an undated entry (the preface) shows neither (#190).
    val time = e.storyTime?.let { if (e.timeEstimated) "about $it" else it }
    val dateLine = listOfNotNull(e.date.format(Dates.DAY_YEAR).takeUnless { e.undated }, time, e.place).joinToString(" · ")
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        val fromTo = "From ${reader.authorName}${reader.recipientName?.let { " to $it" } ?: ""}"
        // Unless the reader chose to drop it when Stoker's heading already names both (#177).
        val showFromTo = !(reader.hidePartiesLine && e.headerNamesParties)
        when (reader.style) {
            // A telegram form: typewritten strip and addresses.
            HeaderStyle.TELEGRAM -> {
                Text(
                    "TELEGRAM",
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp).semantics { heading() },
                    style = TelegramStyle,
                    textAlign = TextAlign.Center,
                )
                // Stoker's own heading, typed on the form like the rest, notes on its delivery and all.
                e.header?.let { Text(it, Modifier.padding(top = 6.dp), style = TelegramStyle) }
                if (showFromTo) Text(fromTo, Modifier.padding(top = 6.dp), style = TelegramStyle)
            }
            // A cutting: the masthead between rules, the headline below.
            HeaderStyle.NEWSPAPER -> {
                val (masthead, headline) = mastheadOf(e)
                HorizontalDivider(thickness = 2.dp)
                Text(masthead, Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, fontFamily = DisplayCapsFamily, textAlign = TextAlign.Center)
                HorizontalDivider()
                headline?.let { Text(it, Modifier.fillMaxWidth().padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) }
            }
            else -> {
                Text(e.header ?: typeLabel(e.type), Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (reader.style == HeaderStyle.LETTER && showFromTo) Text(fromTo, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (dateLine.isNotEmpty()) {
            Row(Modifier.padding(top = 4.dp)) {
                Text(dateLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Ornament(Modifier.padding(top = 8.dp))
    }
}

/** In em, so it grows with the reading font and size. */
private val PARAGRAPH_INDENT = 1.5.em

private fun annotated(block: Block): AnnotatedString = buildAnnotatedString {
    for (span in block.spans) {
        if (span.italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) } else append(span.text)
    }
}

/**
 * One block of the entry; [atEdge] when it's the first or last, where a divider would only part the text from nothing.
 * A paragraph that runs on from the one before is [indent]ed, and with the indent marking it, the gap above it is
 * smaller ([closeBelow] on the paragraph before) (#179).
 */
@Composable
private fun BlockText(block: Block, atEdge: Boolean, indent: Boolean, closeBelow: Boolean) {
    // A dateline skipped as a repeat of the header (#177).
    if (block.spans.isEmpty()) return
    // The story text uses the reader's font and size; headers keep the theme's.
    val reading = LocalReadingStyle.current
    val body = reading.applyTo(MaterialTheme.typography.bodyLarge)
    when (block.role) {
        BlockRole.NOTE -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = reading.applyTo(MaterialTheme.typography.bodyMedium),
            fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        BlockRole.DATELINE, BlockRole.SIGNOFF -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = body, textAlign = TextAlign.End)
        BlockRole.VERSE -> Text(annotated(block), Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 12.dp), style = body, fontStyle = FontStyle.Italic)
        BlockRole.SALUTATION -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = body)
        BlockRole.PARA -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = if (closeBelow) 4.dp else 12.dp),
            style = if (indent) body.copy(textIndent = TextIndent(firstLine = PARAGRAPH_INDENT)) else body)
        // The book's lines of dots (#178). Dropped at an edge, but still an item, so saved positions keep their blocks.
        BlockRole.DIVIDER -> if (!atEdge) Ornament(Modifier.testTag("divider").padding(bottom = 12.dp))
    }
}
