package com.jensen.draculanightbynight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * "Start over?": progress is cleared and entries lock again on schedule. Bookmarks are kept unless the reader
 * ticks the box (shown only when there are bookmarks); Same tempo restarts today or tomorrow.
 */
@Composable
fun StartOverDialog(
    bookmarks: Int,
    sameTempo: Boolean,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (removeBookmarks: Boolean, tempoStart: LocalDate) -> Unit,
) {
    var removeBookmarks by rememberSaveable { mutableStateOf(false) }
    var tomorrow by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start over?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Your reading progress is cleared and entries lock again on schedule.")
                if (bookmarks > 0) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(removeBookmarks, role = Role.Checkbox) { removeBookmarks = it }
                            // TalkBack 17 says "checked" but stays silent when unticked; say both states ourselves.
                            .semantics { stateDescription = if (removeBookmarks) "Checked" else "Not checked" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = removeBookmarks, onCheckedChange = null)
                        Text(if (bookmarks == 1) "Also remove my bookmark" else "Also remove my $bookmarks bookmarks")
                    }
                }
                if (sameTempo) {
                    Text("Restart from", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !tomorrow, onClick = { tomorrow = false }, label = { Text("Today") })
                        FilterChip(selected = tomorrow, onClick = { tomorrow = true }, label = { Text("Tomorrow") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(removeBookmarks, if (tomorrow) today.plusDays(1) else today) }) {
                Text("Start over", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
