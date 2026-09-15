package com.loosecannon.notenfc.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loosecannon.notenfc.core.links.LinkLaunchPolicy
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.QuietLine

/**
 * "Share a note link to noteNFC" (D12 §9). Task 6 turns this into save-then-write-a-tag; Task 4
 * ships the host and shows what the extractor actually found in the shared text, which is the one
 * thing worth seeing before the flow exists.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFlow(graph: AppGraph, sharedText: CharSequence?, onFinished: () -> Unit) {
    val uri = LinkLaunchPolicy.extractUri(sharedText?.toString())
    Scaffold(topBar = { TopAppBar(title = { Text("Save a link") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uri == null) {
                QuietLine("No link in the shared text.")
            } else {
                Text(uri, style = MaterialTheme.typography.bodyMedium)
                QuietLine("Saving and tag writing arrive in a later task.")
            }
            TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}
