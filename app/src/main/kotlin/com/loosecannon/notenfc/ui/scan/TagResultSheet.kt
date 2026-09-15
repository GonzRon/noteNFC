package com.loosecannon.notenfc.ui.scan

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.nav.Route

/**
 * What a scanned (format, key) pair turned out to be — including "not ours" (format NONE).
 *
 * Phase 1C Task 4 ships the route and its callbacks only; the body arrives with the screen's own
 * task, which replaces this file rather than moving it.
 *
 * @param key the scanned tag's identifier — but only while [format] names a payload format we
 *   wrote. When `format == "NONE"` there is no identifier to show and `key` carries a prose reason
 *   the tag could not be read, so nothing may present it as an id or look it up as one.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagResultSheet(
    graph: AppGraph,
    format: String,
    key: String,
    onDismiss: () -> Unit,
    onWriteTag: (Route.WriteTag) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Tag") }) }) { padding ->
        QuietLine("Coming in a later task", Modifier.padding(padding).padding(16.dp))
    }
}
