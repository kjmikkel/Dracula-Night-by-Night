package com.jensen.draculanightbynight.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.Block
import com.jensen.draculanightbynight.content.BlockRole
import com.jensen.draculanightbynight.timeline.typeLabel
import com.jensen.draculanightbynight.ui.LocalReadingStyle
import com.jensen.draculanightbynight.ui.TelegramStyle
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.time.format.DateTimeFormatter

private val STORY_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy")
private const val SAVE_DEBOUNCE_MILLIS = 500L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(viewModel: ReaderViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val ready = ui as? ReaderUi.Ready
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ready?.reader?.authorName.orEmpty()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back") } },
                actions = {
                    if (ready != null) {
                        TextButton(onClick = viewModel::toggleBookmark) { Text(if (ready.reader.bookmarked) "★ Bookmarked" else "☆ Bookmark") }
                    }
                },
            )
        },
        bottomBar = {
            if (ready != null) {
                BottomAppBar {
                    TextButton(onClick = { ready.reader.previousId?.let(onOpen) }, enabled = ready.reader.previousId != null) { Text("‹ Previous") }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { ready.reader.nextId?.let(onOpen) }, enabled = ready.reader.nextId != null) { Text("Next ›") }
                }
            }
        },
    ) { padding ->
        when (val state = ui) {
            ReaderUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            ReaderUi.Locked -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { Text("This entry has not arrived yet.") }
            is ReaderUi.Ready -> Body(state.reader, viewModel, Modifier.padding(padding))
        }
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun Body(reader: ReaderState, viewModel: ReaderViewModel, modifier: Modifier) {
    val (startItem, startOffset) = listPosition(reader.startBlock, reader.startOffset)
    val listState = rememberLazyListState(startItem, startOffset)
    fun save() {
        val (block, offset) = savedPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        viewModel.savePosition(block, offset)
    }
    // Save while reading (debounced), when the app goes to the background, and once more on leaving,
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
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 20.dp), state = listState) {
        item { Header(reader) }
        itemsIndexed(reader.entry.body) { _, block -> BlockText(block) }
    }
}

@Composable
private fun Header(reader: ReaderState) {
    val e = reader.entry
    val time = if (e.timeEstimated) "about ${e.time}" else "${e.time}"
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        when (reader.style) {
            HeaderStyle.TELEGRAM -> Text(
                "TELEGRAM",
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                style = TelegramStyle,
                textAlign = TextAlign.Center,
            )
            HeaderStyle.NEWSPAPER -> {
                HorizontalDivider()
                Text(e.header?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "Newspaper",
                    Modifier.fillMaxWidth().padding(vertical = 6.dp), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                HorizontalDivider()
            }
            else -> Text(e.header ?: typeLabel(e.type), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (reader.style == HeaderStyle.LETTER || reader.style == HeaderStyle.TELEGRAM) {
            Text("From ${reader.authorName}${reader.recipientName?.let { " to $it" } ?: ""}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.padding(top = 4.dp)) {
            Text("${e.date.format(STORY_DATE)} · $time${e.place?.let { " · $it" } ?: ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
