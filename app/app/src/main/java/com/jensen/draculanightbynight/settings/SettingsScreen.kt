package com.jensen.draculanightbynight.settings

import com.jensen.draculanightbynight.ui.focusRings
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.DisplayMode
import com.jensen.draculanightbynight.ui.largeText
import com.jensen.draculanightbynight.ui.BackButton
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jensen.draculanightbynight.BuildConfig
import com.jensen.draculanightbynight.content.Dates
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.notify.Notifications
import com.jensen.draculanightbynight.notify.rememberNotificationPermissionRequest
import com.jensen.draculanightbynight.timeline.ModeDialog
import com.jensen.draculanightbynight.timeline.aheadLabel
import com.jensen.draculanightbynight.ui.StartOverDialog
import com.jensen.draculanightbynight.ui.family
import com.jensen.draculanightbynight.ui.systemHighContrast
import com.jensen.draculanightbynight.user.ReadingFont
import com.jensen.draculanightbynight.user.TextSize
import com.jensen.draculanightbynight.user.ThemeChoice
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

private enum class Dialog { MODE, START_DATE, SKIP_DAY, START_OVER }

private const val SAMPLE = "“I was not able to light on any map or work giving the exact locality of the Castle Dracula.”"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onAbout: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<Dialog?>(null) }
    // Android's notification settings can change while the app is away: look again on every return.
    var blocked by remember { mutableStateOf(!Notifications.allowed(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        blocked = !Notifications.allowed(context)
        viewModel.recheckNotifications()
    }
    // The reader asked for notifications: keep that wish even if Android refuses (or, after two refusals, no longer
    // asks), so the row says they are blocked and leads to Android's settings instead of going dead.
    val askPermission = rememberNotificationPermissionRequest {
        viewModel.setNotify(true)
        blocked = !Notifications.allowed(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", Modifier.semantics { heading() }) }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { Section("Reading") }
            item { Setting("Mode", s.modeLabel) { dialog = Dialog.MODE } }
            if (s.tempoStart != null) {
                item { Setting("Start date", s.tempoStart.format(Dates.DAY_YEAR)) { dialog = Dialog.START_DATE } }
                if (s.offerStartTomorrow && s.firstEntryTime != null) {
                    item {
                        Setting(
                            "Start tomorrow",
                            "${s.firstEntryAuthor}'s first entry comes at ${s.firstEntryTime.format(Dates.TIME)}. Start tomorrow to read it as it happens.",
                        ) { viewModel.setTempoStart(s.today.plusDays(1)) }
                    }
                }
            }
            if (s.mode != ReadingMode.Free) {
                item { Setting("Skip to a day", "Unlock every entry up to a day of the story") { dialog = Dialog.SKIP_DAY } }
                if (!s.ahead.isZero) {
                    item { Setting("Back to real time", "Reading ${aheadLabel(s.ahead)} ahead") { viewModel.backToRealTime() } }
                }
            }

            item { Section("Notifications") }
            item {
                ListItem(
                    headlineContent = { Text("Notify me when entries arrive") },
                    supportingContent = {
                        Text(if (blocked && s.notify) "Notifications are blocked in Android's settings · Open settings" else "Silent notifications")
                    },
                    trailingContent = { Switch(checked = s.notify && !blocked, onCheckedChange = null) },
                    // The whole row is the switch; while Android blocks notifications it opens Android's settings instead.
                    modifier = Modifier.toggleable(value = s.notify && !blocked, role = Role.Switch) { on ->
                        when {
                            blocked && s.notify -> context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                            on -> askPermission()
                            else -> viewModel.setNotify(false)
                        }
                    },
                )
            }
            item {
                Setting("Sound and vibration…", "Silent by default; Android decides how New entries are announced") {
                    context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL_NEW_ENTRIES),
                    )
                }
            }

            item { Section("Display") }
            // Each set of choices is one group, so TalkBack says "1 of 4" and keeps the title with it.
            item {
                ChoiceGroup("Text size") {
                    TextSize.entries.forEach { size ->
                        Choice(size.label, size == s.textSize, sampleStyle = MaterialTheme.typography.bodyLarge.let { it.copy(fontSize = it.fontSize * size.scale) }) {
                            viewModel.setTextSize(size)
                        }
                    }
                }
            }
            item {
                ChoiceGroup("Reading font") {
                    ReadingFont.entries.forEach { font ->
                        Choice(font.label, font == s.readingFont, sampleStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = font.family())) {
                            viewModel.setReadingFont(font)
                        }
                    }
                }
            }
            item {
                Text(
                    SAMPLE,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge.let { it.copy(fontFamily = s.readingFont.family(), fontSize = it.fontSize * s.textSize.scale) },
                )
            }
            item { ChoiceGroup("Theme") { ThemeChoice.entries.forEach { theme -> Choice(theme.label, theme == s.theme) { viewModel.setTheme(theme) } } } }
            item {
                val systemHigh = systemHighContrast()
                ListItem(
                    headlineContent = { Text("High contrast") },
                    supportingContent = { Text(if (s.highContrast == null) "Follows Android's contrast setting" else "Strongest contrast for all text") },
                    trailingContent = { Switch(checked = s.highContrast ?: systemHigh, onCheckedChange = null) },
                    modifier = Modifier.toggleable(value = s.highContrast ?: systemHigh, role = Role.Switch, onValueChange = viewModel::setHighContrast),
                )
            }

            item { HorizontalDivider(Modifier.padding(top = 16.dp)) }
            item {
                ListItem(
                    headlineContent = { Text("Start over", color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text("Clear your reading progress and begin the story again") },
                    modifier = Modifier.clickable(role = Role.Button) { dialog = Dialog.START_OVER },
                )
            }
            item { Setting("About", "Version ${BuildConfig.VERSION_NAME} · sources, licences, privacy", onAbout) }
        }

        when (dialog) {
            Dialog.MODE -> ModeDialog(current = s.mode, today = s.today, onDismiss = { dialog = null }) {
                viewModel.setMode(it)
                dialog = null
            }
            Dialog.START_DATE -> {
                val large = largeText()
                // English like the rest of the app, whatever the phone's language.
                val picker = remember {
                    DatePickerState(
                        locale = Locale.ENGLISH,
                        initialSelectedDateMillis = s.tempoStart?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                        // The month grid's day cells are cut off at large text; typing the date fits.
                        initialDisplayMode = if (large) DisplayMode.Input else DisplayMode.Picker,
                    )
                }
                DatePickerDialog(
                    modifier = Modifier.focusRings(),
                    onDismissRequest = { dialog = null },
                    confirmButton = {
                        TextButton(onClick = {
                            picker.selectedDateMillis?.let { viewModel.setTempoStart(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                            dialog = null
                        }) { Text("Choose") }
                    },
                    dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
                ) {
                    // The headline date is formatted with the configuration's locale: make that English too.
                    val english = Configuration(LocalConfiguration.current).apply { setLocale(Locale.ENGLISH) }
                    CompositionLocalProvider(LocalConfiguration provides english) { DatePicker(picker) }
                }
            }
            Dialog.SKIP_DAY -> AlertDialog(
                modifier = Modifier.focusRings(),
                onDismissRequest = { dialog = null },
                title = { Text("Skip to a day") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(s.storyDays) { day ->
                            Text(
                                day.format(Dates.DAY),
                                Modifier.fillMaxWidth().clickable(role = Role.Button) {
                                    viewModel.skipToDay(day)
                                    dialog = null
                                }.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
            Dialog.START_OVER -> StartOverDialog(
                bookmarks = s.bookmarks,
                sameTempo = s.mode is ReadingMode.SameTempo,
                today = s.today,
                onDismiss = { dialog = null },
            ) { removeBookmarks, tempoStart ->
                viewModel.startOver(removeBookmarks, tempoStart)
                dialog = null
            }
            null -> Unit
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp).semantics { heading() }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChoiceGroup(title: String, choices: @Composable () -> Unit) {
    Column(Modifier.selectableGroup()) {
        Label(title)
        choices()
    }
}

@Composable
private fun Label(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 12.dp).semantics { heading() }, style = MaterialTheme.typography.labelLarge)
}

@Composable
private fun Setting(title: String, summary: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(summary) }, modifier = Modifier.clickable(role = Role.Button, onClick = onClick))
}

@Composable
private fun Choice(label: String, selected: Boolean, sampleStyle: TextStyle? = null, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 12.dp)) {
            Text(label, style = sampleStyle ?: MaterialTheme.typography.bodyLarge)
        }
    }
}
