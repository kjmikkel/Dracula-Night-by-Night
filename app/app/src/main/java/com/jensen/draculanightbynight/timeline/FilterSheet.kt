package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.content.DocumentType
import com.jensen.draculanightbynight.domain.EntryFilter
import com.jensen.draculanightbynight.domain.Voice
import com.jensen.draculanightbynight.domain.typeChipLabel

/** The timeline filter: voice and document chips that apply at once, wrapping onto as many lines as they need. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: EntryFilter,
    onToggleVoice: (Voice) -> Unit,
    onToggleType: (DocumentType) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Opens fully: at large text a half-open sheet would hide the last chips and Clear all.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Heading("Voices")
            Chips(Voice.entries, { it.label }, filter.voices, onToggleVoice)
            Heading("Documents")
            Chips(DocumentType.entries, ::typeChipLabel, filter.types, onToggleType)
            TextButton(onClick = onClear, enabled = filter.isActive, modifier = Modifier.align(Alignment.End)) {
                Text("Clear all")
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Chips(all: List<T>, label: (T) -> String, selected: Set<T>, onToggle: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (choice in all) {
            val on = choice in selected
            FilterChip(
                selected = on,
                onClick = { onToggle(choice) },
                label = { Text(label(choice)) },
            )
        }
    }
}
