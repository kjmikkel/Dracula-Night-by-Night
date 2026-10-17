package com.jensen.draculanightbynight.ui

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** The top bar's "‹ Back": TalkBack says "Back" once, not the arrow as well. */
@Composable
fun BackButton(onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("‹ Back", Modifier.notSpoken()) }
}

/** The top bar's "✕" in the split view, where the reader closes instead of going back. */
@Composable
fun CloseButton(onClose: () -> Unit) {
    TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close entry" }) { Text("✕", Modifier.notSpoken()) }
}

/** For text inside a control whose own description already says it: keeps the text out of what TalkBack reads. */
fun Modifier.notSpoken(): Modifier = clearAndSetSemantics {}
