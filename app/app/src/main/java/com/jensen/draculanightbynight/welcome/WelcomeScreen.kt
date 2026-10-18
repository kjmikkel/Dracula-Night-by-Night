package com.jensen.draculanightbynight.welcome

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.notify.rememberNotificationPermissionRequest

private enum class Choice { CALENDAR, TEMPO, FREE }

/** First launch: the idea, the mode, notifications — then Begin. */
@Composable
fun WelcomeScreen(viewModel: WelcomeViewModel, onBegun: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val welcome = state
    if (welcome == null) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var choice by rememberSaveable { mutableStateOf(Choice.TEMPO) }
    var tomorrow by rememberSaveable { mutableStateOf(false) }
    var notify by rememberSaveable { mutableStateOf(true) }

    fun mode(): ReadingMode = when (choice) {
        Choice.CALENDAR -> ReadingMode.Calendar(welcome.seasonYear)
        Choice.TEMPO -> ReadingMode.SameTempo(if (tomorrow && welcome.firstEntryClose) welcome.today.plusDays(1) else welcome.today)
        Choice.FREE -> ReadingMode.Free
    }
    // A refused permission means no notifications; Settings can turn them on later.
    val askPermission = rememberNotificationPermissionRequest { granted -> viewModel.begin(mode(), granted, onBegun) }

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Dracula Night by Night", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "Dracula is told in diaries, letters, telegrams and newspaper cuttings, each of them dated. " +
                    "This app gives them to you as they happen, night by night.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("How would you like to read?", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            // One group, so TalkBack says "1 of 3". The start-day chips are a group of their own inside it.
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModeCard(choice == Choice.TEMPO, "In the same tempo", "Today is 3 May: entries arrive with the story's own gaps between them.") {
                    choice = Choice.TEMPO
                }
                if (choice == Choice.TEMPO && welcome.firstEntryClose && welcome.firstEntryTime != null) {
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(
                            "${welcome.firstEntryAuthor}'s first entry comes at ${welcome.firstEntryTime.format(Dates.TIME)}. " +
                                "Start tomorrow to read it as it happens.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text("Start today") })
                            FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text("Start tomorrow") })
                        }
                    }
                }
                ModeCard(
                    choice == Choice.CALENDAR,
                    "On the actual days",
                    "Each entry arrives on its own date, 3 May – 6 November ${welcome.seasonYear}." +
                        if (welcome.calendarWaits) " Nothing unlocks until 3 May." else calendarBacklog(welcome)?.let { " $it" }.orEmpty(),
                ) { choice = Choice.CALENDAR }
                ModeCard(choice == Choice.FREE, "Read freely", "Every entry, now.") { choice = Choice.FREE }
            }

            // The whole row is the switch, so it is one target for touch and for TalkBack.
            Row(Modifier.toggleable(value = notify, role = Role.Switch) { notify = it }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notify me when entries arrive", style = MaterialTheme.typography.bodyLarge)
                    Text("Silent; you can change this in Settings.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = notify, onCheckedChange = null)
            }
            Button(
                onClick = { if (notify) askPermission() else viewModel.begin(mode(), notify = false, onDone = onBegun) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Begin") }
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier.padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
