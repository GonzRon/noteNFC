package com.loosecannon.servicetag.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.servicetag.R
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.components.QuietLine
import com.loosecannon.servicetag.ui.components.SectionHeader
import com.loosecannon.servicetag.ui.theme.ControlShape
import com.loosecannon.servicetag.ui.theme.Eyebrow
import com.loosecannon.servicetag.ui.theme.PlateShape

/**
 * The landing screen: what needs attention, then the ways out of it (D12 §4).
 *
 * The section order is fixed — ATTENTION · UPCOMING · CURRENT · OUT OF SEASON (G1 §1.2) — and a
 * section with no rows is omitted, so Phase 1C only ever draws CURRENT: schedules, and with them
 * the other three sections, arrive in Phase 3. Scan has no FAB of its own to replace (G1 §1.2
 * "Navigation") — [onScan] pushes the scan screen from the empty-state action, same as Settings
 * does with its own Read / inspect tag row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    graph: AppGraph,
    onOpenAsset: (String) -> Unit,
    onNewAsset: () -> Unit,
    onBackup: () -> Unit,
    onSettings: () -> Unit,
    onScan: () -> Unit,
) {
    val model: DashboardViewModel = viewModel(key = "dashboard") { DashboardViewModel(graph) }
    val state by model.state.collectAsStateWithLifecycle()

    // An export that happened on the backup screen is a preference, and nothing observes those:
    // coming back here is the moment to ask again whether the nudge is still true.
    LaunchedEffect(Unit) { model.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        // The nudge and the box stay put; only the rows scroll. A search field inside a lazy list
        // is disposed the moment it scrolls out of view, which drops focus and the keyboard
        // mid-word — and a filter you have to scroll back to find is not a quick one.
        Column(modifier = Modifier.padding(padding)) {
            if (state.needsBackup) {
                BackupNudge(onExport = onBackup, modifier = Modifier.padding(16.dp))
            }
            if (!state.anyInService) {
                FirstRun(onNewAsset = onNewAsset, onScan = onScan)
            } else {
                SearchBox(
                    query = state.query,
                    onQueryChange = model::onQueryChange,
                    onClear = model::clearQuery,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                // Said once, and only while there is something it explains: a list that is short
                // because the parts are on their systems should say where they went.
                if (state.query.isBlank() && state.hiddenComponents > 0) {
                    QuietLine(
                        text = "Components are listed on the asset they belong to. Search to find one.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                if (state.assets.isEmpty()) {
                    QuietLine(text = "Nothing matches that.", modifier = Modifier.padding(16.dp))
                } else {
                    SectionHeader(title = "Current", modifier = Modifier.padding(horizontal = 16.dp))
                    LazyColumn {
                        items(state.assets, key = { it.asset.id.value }) { row ->
                            CurrentRow(row = row, onClick = { onOpenAsset(row.asset.id.value) })
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The quick filter (#39). An `OutlinedTextField`, not a Material 3 `SearchBar`: a `SearchBar`
 * expands over the screen and owns a results surface of its own, and what this needs is one line
 * that narrows the list already underneath it. The clear action appears only once there is
 * something to clear, so a first look is not two glyphs and a hint.
 */
@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        shape = ControlShape,
        placeholder = { Text("Search assets and components") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * The one card this screen is allowed (D12 §7, G1 §1.2): the nudge is not another row of the list,
 * it is a separate fact about the install, and a card is how a separate fact reads. It says what is
 * missing and what that costs before it offers the button.
 */
@Composable
private fun BackupNudge(onExport: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = PlateShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "BACKUP",
                style = Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = "No backup yet", style = MaterialTheme.typography.titleMedium)
            QuietLine("Tags survive a phone change only if you have one.")
            Button(
                onClick = onExport,
                shape = ControlShape,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text("Export now")
            }
        }
    }
}

/** No assets at all: say so once, then offer the two ways an asset gets here. */
@Composable
private fun FirstRun(onNewAsset: () -> Unit, onScan: () -> Unit) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuietLine("Nothing here yet")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewAsset, shape = ControlShape) { Text("Add your first asset") }
            OutlinedButton(onClick = onScan, shape = ControlShape) { Text("Scan a tag") }
        }
    }
}

/**
 * One asset in service. 28dp glyph · text block · chevron, 11dp vertical padding (G1 §1.2). The
 * glyph is `onSurfaceVariant`, not a state colour: until schedules exist there is no state to
 * carry, and a row that looked OK by colour would be claiming something it does not know.
 *
 * A component — which is only ever here because a search asked for it (#39) — says whose component
 * it is in place of the schedule line. Neither row has a schedule until Phase 3, and of the two
 * facts the parentage is the one that makes the hit make sense.
 */
@Composable
private fun CurrentRow(row: DashboardRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.asset.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            QuietLine(row.parentName?.let { parent -> "Part of $parent" } ?: "No schedule yet")
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
