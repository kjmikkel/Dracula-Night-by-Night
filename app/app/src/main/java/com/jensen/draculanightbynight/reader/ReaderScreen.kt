package com.jensen.draculanightbynight.reader

import androidx.compose.material3.TopAppBarDefaults
import com.jensen.draculanightbynight.ui.largeText
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import com.jensen.draculanightbynight.ui.notSpoken
import com.jensen.draculanightbynight.ui.BackButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.content.typeLabel
import com.jensen.draculanightbynight.ui.DisplayCapsFamily
import com.jensen.draculanightbynight.ui.LocalReadingStyle
import com.jensen.draculanightbynight.ui.Ornament
import com.jensen.draculanightbynight.ui.TelegramStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val SAVE_DEBOUNCE_MILLIS = 500L
private val NEWSPAPER_COLUMN = 520.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    when (val state = ui) {
        is ReaderUi.Ready -> Pages(state, viewModel, onBack)
        else -> Scaffold(
            topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(onBack) }) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                if (state == ReaderUi.Locked) Text("This entry has not arrived yet.") else CircularProgressIndicator()
            }
        }
    }
}

/** The entries as pages that follow the finger; Previous and Next turn them too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Pages(ready: ReaderUi.Ready, viewModel: ReaderViewModel, onBack: () -> Unit) {
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
    // At large text the author gets a line of its own under the bar, where it can wrap instead of collapsing to "…".
    val large = largeText()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { if (!large) Text(reader.authorName, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { BackButton(onBack) },
                    actions = {
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
                if (large) {
                    Text(
                        reader.authorName,
                        Modifier.fillMaxWidth().background(TopAppBarDefaults.topAppBarColors().containerColor).padding(horizontal = 16.dp, vertical = 4.dp)
                            .semantics { heading() },
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
        bottomBar = {
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
        },
    ) { padding ->
        HorizontalPager(
            pagerState,
            Modifier.fillMaxSize().padding(padding),
            key = { latest.pager.pages.getOrElse(it) { "page-$it" } },
        ) { page ->
            val shown = latest.pager.pages.getOrNull(page)?.let { latest.pages[it] }
            if (shown == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                Body(shown, viewModel)
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
private fun Body(reader: ReaderState, viewModel: ReaderViewModel) {
    val (startItem, startOffset) = listPosition(reader.startBlock, reader.startOffset)
    val listState = rememberLazyListState(startItem, startOffset)
    fun save() {
        val (block, offset) = savedPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        viewModel.savePosition(reader.entry.id, block, offset)
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
    LazyColumn(Modifier.fillMaxSize().wrapContentWidth().then(column).padding(horizontal = 20.dp), state = listState) {
        item { Header(reader) }
        itemsIndexed(reader.entry.body) { _, block -> BlockText(block) }
    }
}

@Composable
private fun Header(reader: ReaderState) {
    val e = reader.entry
    val time = if (e.timeEstimated) "about ${e.time}" else "${e.time}"
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        val fromTo = "From ${reader.authorName}${reader.recipientName?.let { " to $it" } ?: ""}"
        when (reader.style) {
            // A telegram form: typewritten strip and addresses.
            HeaderStyle.TELEGRAM -> {
                Text(
                    "TELEGRAM",
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp).semantics { heading() },
                    style = TelegramStyle,
                    textAlign = TextAlign.Center,
                )
                Text(fromTo, Modifier.padding(top = 6.dp), style = TelegramStyle)
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
                if (reader.style == HeaderStyle.LETTER) Text(fromTo, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text("${e.date.format(Dates.DAY_YEAR)} · $time${e.place?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Ornament(Modifier.padding(top = 8.dp))
    }
}

private fun annotated(block: Block): AnnotatedString = buildAnnotatedString {
    for (span in block.spans) {
        if (span.italic) withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(span.text) } else append(span.text)
    }
}

@Composable
private fun BlockText(block: Block) {
    // The story text uses the reader's font and size; headers keep the theme's.
    val reading = LocalReadingStyle.current
    val body = reading.applyTo(MaterialTheme.typography.bodyLarge)
    when (block.role) {
        BlockRole.NOTE -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = reading.applyTo(MaterialTheme.typography.bodyMedium),
            fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        BlockRole.DATELINE, BlockRole.SIGNOFF -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = body, textAlign = TextAlign.End)
        BlockRole.VERSE -> Text(annotated(block), Modifier.fillMaxWidth().padding(start = 24.dp, bottom = 12.dp), style = body, fontStyle = FontStyle.Italic)
        BlockRole.SALUTATION, BlockRole.PARA -> Text(annotated(block), Modifier.fillMaxWidth().padding(bottom = 12.dp), style = body)
    }
}
