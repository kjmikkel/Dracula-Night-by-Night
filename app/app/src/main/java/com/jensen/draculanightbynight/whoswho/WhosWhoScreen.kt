package com.jensen.draculanightbynight.whoswho

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.domain.ShownPerson
import com.jensen.draculanightbynight.ui.BackButton

/** The who's who (#204): the people the reader has met, grouped, each profile only as far as they've read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhosWhoScreen(viewModel: WhosWhoViewModel, onBack: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Who's who", Modifier.semantics { heading() }) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        when (val state = ui) {
            WhosWhoUi.Loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            is WhosWhoUi.Ready -> if (state.groups.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), Alignment.Center) {
                    Text("People appear here as you meet them in the story.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                Profiles(state, viewModel::toggle, Modifier.fillMaxSize().padding(padding))
            }
        }
    }
}

@Composable
private fun Profiles(state: WhosWhoUi.Ready, onToggle: (String) -> Unit, modifier: Modifier) {
    val list = rememberLazyListState()
    // Rows in list order: a group's heading, then its people, so the person opened on can be found by index.
    val keys = state.groups.flatMap { g -> listOf("group:${g.id}") + g.people.map { "person:${it.id}" } }
    var scrolled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.scrollTo) {
        val index = state.scrollTo?.let { keys.indexOf("person:$it") } ?: -1
        if (!scrolled && index >= 0) list.scrollToItem(index)
        scrolled = true
    }
    LazyColumn(modifier, state = list) {
        state.groups.forEach { group ->
            item(key = "group:${group.id}") {
                Text(
                    group.name,
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            group.people.forEach { person ->
                item(key = "person:${person.id}") {
                    Profile(person, expanded = person.id in state.expanded, state.sources) { onToggle(person.id) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Profile(person: ShownPerson, expanded: Boolean, sources: Map<String, String>, onToggle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(person.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (person.hasNew && !expanded) New()
            Text(if (expanded) "▴" else "▾", Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            person.lines.forEach { line ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(line.text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
                        if (line.isNew) New()
                    }
                    sources[line.entryId]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        } else {
            Text(person.lines.last().text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The mark on a line read since the last visit; TalkBack reads it as "new". */
@Composable
private fun New() {
    Text(
        "new",
        Modifier.padding(start = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}
