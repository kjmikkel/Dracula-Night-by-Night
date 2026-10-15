package com.jensen.draculanightbynight.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.jensen.draculanightbynight.domain.ReadingMode
import com.jensen.draculanightbynight.domain.UnlockSchedule
import java.time.LocalDate

/** Minimal mode picker; plan 2 replaces it with onboarding and a settings screen. */
@Composable
fun ModeDialog(current: ReadingMode, onDismiss: () -> Unit, onChoose: (ReadingMode) -> Unit) {
    val today = LocalDate.now()
    val options = listOf(
        "On the actual days" to ReadingMode.Calendar(UnlockSchedule.seasonYearFor(today)),
        "In the same tempo, starting today" to ReadingMode.SameTempo(today),
        "Read freely" to ReadingMode.Free,
    )
    var selected by remember { mutableStateOf(options.indexOfFirst { it.second::class == current::class }.coerceAtLeast(0)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to read") },
        text = {
            Column {
                options.forEachIndexed { i, (label, _) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = i == selected, role = Role.RadioButton) { selected = i }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Changing mode keeps what you have read.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onChoose(options[selected].second) }) { Text("Choose") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
