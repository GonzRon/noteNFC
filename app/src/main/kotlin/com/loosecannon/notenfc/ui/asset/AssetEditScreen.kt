package com.loosecannon.notenfc.ui.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.core.journal.SeedTemplates
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.SectionHeader
import com.loosecannon.notenfc.ui.theme.BadgeShape
import com.loosecannon.notenfc.ui.theme.ControlShape

/**
 * Create ([assetId] null) or edit one asset: four outlined fields on the 6dp control corner
 * (D12 §7), Save in the app bar and again at the bottom so it is reachable with the keyboard open
 * (G1 §1.3). The blank-name rule belongs to the use case; the screen only shows what it refused.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetEditScreen(
    graph: AppGraph,
    assetId: String?,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
) {
    val model: AssetEditViewModel =
        viewModel(key = assetId ?: "new") { AssetEditViewModel(graph, assetId) }
    val state by model.state.collectAsStateWithLifecycle()

    // The save itself belongs to the ViewModel; this only listens for where it says to go next.
    LaunchedEffect(model) { model.saved.collect { id -> onDone(id.value) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editing) "Edit asset" else "New asset") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(onClick = model::save, enabled = !state.saving) { Text("Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = model::onName,
                label = { Text("Name") },
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text("An asset needs a name.") }
                } else {
                    null
                },
                singleLine = true,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.category,
                onValueChange = model::onCategory,
                label = { Text("Category") },
                singleLine = true,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            )
            // Editing an existing asset shows no template row: a template is starter data, and
            // an asset that has been in use has rows of its own that it must not clobber (§7).
            if (!state.editing) {
                TemplateRow(selected = state.templateKey, onSelect = model::onTemplate)
            }
            OutlinedTextField(
                value = state.description,
                onValueChange = model::onDescription,
                label = { Text("Description") },
                singleLine = true,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.notes,
                onValueChange = model::onNotes,
                label = { Text("Notes") },
                minLines = 3,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = model::save,
                enabled = !state.saving,
                shape = ControlShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save asset")
            }
        }
    }
}

/**
 * The five seeds plus None, as chips rather than a dropdown: six short options are quicker to
 * read side by side than behind a menu, and the default has to be visibly the selected one.
 */
@Composable
private fun TemplateRow(selected: String?, onSelect: (String?) -> Unit) {
    val options = listOf<Pair<String?, String>>(NONE to "None · set up later") +
        SeedTemplates.all.map { it.key to it.name }
    Column {
        SectionHeader(title = "Template")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            options.forEach { (key, label) ->
                FilterChip(
                    selected = key == selected,
                    onClick = { onSelect(key) },
                    label = { Text(label) },
                    shape = BadgeShape,
                )
            }
        }
        Text(
            text = "Starts the asset with its readings and quick actions. " +
                "Choose None to decide on the asset later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "None · set up later" is the absence of a template key, not a template called none. */
private val NONE: String? = null
