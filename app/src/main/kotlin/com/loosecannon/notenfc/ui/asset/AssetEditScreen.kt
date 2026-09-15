package com.loosecannon.notenfc.ui.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.loosecannon.notenfc.di.AppGraph
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
