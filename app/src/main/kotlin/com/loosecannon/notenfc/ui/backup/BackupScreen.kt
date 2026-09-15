package com.loosecannon.notenfc.ui.backup

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

/**
 * Export a v1 backup to a SAF document, or replace everything from one.
 *
 * Phase 1C Task 4 ships the route and its callbacks only; the body arrives with the screen's own
 * task, which replaces this file rather than moving it.
 */
@Suppress("UNUSED_PARAMETER")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    graph: AppGraph,
    onBack: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Backup") }) }) { padding ->
        QuietLine("Coming in a later task", Modifier.padding(padding).padding(16.dp))
    }
}
