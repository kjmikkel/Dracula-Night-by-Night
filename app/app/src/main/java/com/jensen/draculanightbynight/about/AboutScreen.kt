package com.jensen.draculanightbynight.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.BuildConfig
import com.jensen.draculanightbynight.ui.Ornament

/** What the app is, where its text and code come from, the font licences and the privacy promise. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val links = LocalUriHandler.current
    var shown by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹ Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text("Dracula Night by Night", Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("Version ${BuildConfig.VERSION_NAME}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
                Text(
                    "Bram Stoker's Dracula, told in diaries, letters and telegrams, arriving on the days and at the hours they were written.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Ornament()
            }
            item { Section("The text") }
            item {
                ListItem(
                    headlineContent = { Text("Project Gutenberg eBook #345") },
                    supportingContent = { Text("Public domain in the United States. Project Gutenberg's header and licence have been removed, as its terms allow for copies that do not carry its name.") },
                )
            }
            item { Section("The app") }
            item {
                ListItem(
                    headlineContent = { Text("Source code") },
                    supportingContent = { Text("Apache License 2.0, on GitHub") },
                    modifier = Modifier.linkRow("Open the source code on GitHub") { links.openUri(SOURCE_URL) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Privacy") },
                    supportingContent = { Text("The app collects nothing and sends nothing anywhere. Read the privacy policy.") },
                    modifier = Modifier.linkRow("Open the privacy policy") { links.openUri(PRIVACY_URL) },
                )
            }
            item { Section("Fonts") }
            items(Licences) { licence ->
                ListItem(
                    headlineContent = { Text(licence.name) },
                    supportingContent = { Text(licence.licence) },
                    modifier = Modifier.linkRow("Show the ${licence.name} licence") { shown = licence.asset },
                )
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }
    }
    shown?.let { asset -> LicenceDialog(asset) { shown = null } }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(start = 16.dp, top = 16.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** A row that opens something: a button for TalkBack, with [label] said as what a tap does. */
private fun Modifier.linkRow(label: String, onClick: () -> Unit): Modifier =
    clickable(role = Role.Button, onClickLabel = label, onClick = onClick)

@Composable
private fun LicenceDialog(asset: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember(asset) { context.assets.open("licenses/$asset").bufferedReader().use { it.readText() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Licences.first { it.asset == asset }.name) },
        text = { Text(text, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
