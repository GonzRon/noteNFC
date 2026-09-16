package com.loosecannon.servicetag.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.components.QuietLine
import com.loosecannon.servicetag.ui.scan.WriteTagScreen
import com.loosecannon.servicetag.ui.theme.SheetSentence

/**
 * "Share a note link to noteNFC" (D12 §9). The whole flow lives in `ShareActivity`'s own task: the
 * URI is extracted and checked, the card offers the two things worth doing with it, and both of
 * them save. Writing a tag pushes the ordinary write screen inside this task, so Done here means
 * "back to Joplin" and never "into noteNFC's back stack".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFlow(graph: AppGraph, sharedText: CharSequence?, onFinished: () -> Unit) {
    val model: ShareViewModel = viewModel(key = "share") { ShareViewModel(graph, sharedText) }
    val state by model.state.collectAsStateWithLifecycle()

    val writing = state as? ShareState.Writing
    if (writing != null) {
        WriteTagScreen(graph = graph, key = writing.route, onDone = onFinished)
        return
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Save a link") }) }) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            when (val current = state) {
                is ShareState.Card -> ShareCard(
                    state = current,
                    onWriteTag = model::writeToTag,
                    onKeepAsLink = model::keepAsLink,
                    onCancel = onFinished,
                )

                is ShareState.NeedsConfirmation -> ShareConfirmation(
                    state = current,
                    onConfirm = model::confirmUnknownScheme,
                    onCancel = onFinished,
                )

                is ShareState.Saved -> Closing("Saved to noteNFC.", onFinished)
                is ShareState.Nothing -> Closing(current.message, onFinished)

                // Handled above; the write screen replaces this one entirely.
                is ShareState.Writing -> Unit
            }
        }
    }
}

/** One sentence and one way out — the flow is over, and this task should not linger. */
@Composable
private fun Closing(message: String, onFinished: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = message, style = SheetSentence)
        QuietLine("Links live under Dashboard · Links.")
        TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.End)) { Text("Close") }
    }
}
