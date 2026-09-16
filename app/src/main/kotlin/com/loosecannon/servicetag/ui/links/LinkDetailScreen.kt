package com.loosecannon.servicetag.ui.links

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.links.LinkLauncher
import com.loosecannon.servicetag.ui.components.LabelValue
import com.loosecannon.servicetag.ui.components.QuietLine
import com.loosecannon.servicetag.ui.components.SectionHeader
import com.loosecannon.servicetag.ui.nav.Route
import com.loosecannon.servicetag.ui.theme.ControlShape
import com.loosecannon.servicetag.ui.theme.ServiceTagTheme

/**
 * One saved link: where it points and what may launch it. Delete is a hard delete here — a link is
 * a pointer, not a record — but `DeleteLink` refuses while a physical tag still points at it, and
 * the refusal says how many tags to deal with first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkDetailScreen(
    graph: AppGraph,
    linkId: String,
    onBack: () -> Unit,
    onWriteTag: (Route.WriteTag) -> Unit,
) {
    val model: LinkDetailViewModel = viewModel(key = linkId) { LinkDetailViewModel(graph, linkId) }
    val state by model.state.collectAsStateWithLifecycle()
    val missing by model.missing.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var confirming by remember { mutableStateOf(false) }
    var refusal by remember { mutableStateOf<String?>(null) }

    // A deep link or a restored back stack can name a link a backup import has since replaced.
    LaunchedEffect(missing) { if (missing) onBack() }

    LaunchedEffect(model, activity) {
        model.events.collect { event ->
            when (event) {
                is LinkEvent.Launch -> activity?.let { LinkLauncher.open(it, event.uri) }
                is LinkEvent.Refused -> { confirming = false; refusal = event.message }
                LinkEvent.Deleted -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        if (current == null) {
            QuietLine("Loading…", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = current.link.label, style = MaterialTheme.typography.headlineSmall)
            LabelValue(label = "Kind", value = current.link.kindLabel())
            LabelValue(label = "Opens", value = current.link.uri, mono = true)

            Button(onClick = model::open, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
                Text("Open")
            }
            OutlinedButton(
                onClick = { onWriteTag(Route.WriteTag("link", linkId, current.link.label)) },
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Write tag")
            }

            SectionHeader(title = "Tags")
            if (current.tags.isEmpty()) {
                QuietLine("No tag points here yet")
            } else {
                current.tags.forEach { tag ->
                    QuietLine("${tag.id.value.take(8)} · ${tag.status.name.lowercase()}")
                }
            }

            refusal?.let { QuietLine(it) }

            TextButton(
                onClick = { refusal = null; confirming = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete link", color = ServiceTagTheme.semanticColors.destructiveAction.foreground)
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete this link?") },
            text = { Text("The link is removed from ServiceTag. Whatever it points at is left alone.") },
            confirmButton = {
                TextButton(onClick = { confirming = false; model.delete() }) {
                    Text("Delete", color = ServiceTagTheme.semanticColors.destructiveAction.foreground)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep it") } },
            shape = ControlShape,
        )
    }
}
