package com.loosecannon.notenfc.ui.asset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.StatusBadge
import com.loosecannon.notenfc.ui.theme.ControlShape
import com.loosecannon.notenfc.ui.theme.NoteNfcTheme

/**
 * The asset list: one line per asset, active rows first and archived ones only when asked for
 * (archive is not delete — R-9 — but a list that keeps showing what you archived is no better
 * than never archiving). Rows are hairline-separated lines, not cards (D12 §7), and adding an
 * asset is an app-bar action: the one FAB this app allows belongs to the ledger (G1 §3 c).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    graph: AppGraph,
    onOpenAsset: (String) -> Unit,
    onNewAsset: () -> Unit,
) {
    val model: AssetsViewModel = viewModel { AssetsViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assets") },
                actions = {
                    IconButton(onClick = onNewAsset) {
                        Icon(Icons.Outlined.Add, contentDescription = "Add asset")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            FilterChip(
                selected = state.showArchived,
                onClick = model::toggleArchived,
                label = { Text("Show archived") },
                shape = ControlShape,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (state.items.isEmpty()) {
                // "Nothing here" and "nothing here because the chip is off" are different facts,
                // and telling someone the first while the second is true is how they conclude
                // their assets are gone. The offer to look is part of the sentence.
                val onlyArchivedLeft = !state.showArchived && state.archivedCount > 0
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuietLine(
                        if (onlyArchivedLeft) {
                            "No active assets · ${state.archivedCount} archived"
                        } else {
                            "No assets yet"
                        },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onNewAsset, shape = ControlShape) { Text("Add asset") }
                        if (onlyArchivedLeft) {
                            OutlinedButton(onClick = model::toggleArchived, shape = ControlShape) {
                                Text("Show archived")
                            }
                        }
                    }
                }
            } else {
                LazyColumn {
                    itemsIndexed(state.items, key = { _, asset -> asset.id.value }) { index, asset ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        AssetRow(asset = asset, onClick = { onOpenAsset(asset.id.value) })
                    }
                }
            }
        }
    }
}

/** Name over category, with the status badge doing the work colour alone must not (D12 §5). */
@Composable
private fun AssetRow(asset: Asset, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (asset.category.isNotBlank()) {
                Text(
                    text = asset.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        statusLabel(asset.status)?.let { label ->
            StatusBadge(label = label, colors = NoteNfcTheme.semanticColors.seasonInactive)
        }
    }
}

/** An active asset says nothing; the other says what it is, in the neutral family. */
internal fun statusLabel(status: AssetStatus): String? = when (status) {
    AssetStatus.ACTIVE -> null
    AssetStatus.ARCHIVED -> "Archived"
}
