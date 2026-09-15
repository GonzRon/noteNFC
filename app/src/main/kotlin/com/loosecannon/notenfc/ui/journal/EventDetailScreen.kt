package com.loosecannon.notenfc.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.core.journal.Reading
import com.loosecannon.notenfc.core.journal.classify
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.ValueType
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.InstrumentList
import com.loosecannon.notenfc.ui.components.InstrumentRow
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.SectionHeader
import com.loosecannon.notenfc.ui.theme.Eyebrow
import com.loosecannon.notenfc.ui.theme.MonoText
import com.loosecannon.notenfc.ui.theme.NoteNfcTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One stored entry, read-only: the same instrument rows the asset screen draws, the materials that
 * went in, and the note. Edit reopens the entry route in edit mode; Delete is the one destructive
 * action in the journal and asks first, in the destructive family (D12 §5).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    graph: AppGraph,
    eventId: String,
    onEdit: (assetId: String, eventId: String) -> Unit,
    onBack: () -> Unit,
) {
    val model: EventDetailViewModel = viewModel(key = eventId) { EventDetailViewModel(graph, eventId) }
    val state by model.state.collectAsStateWithLifecycle()
    val missing by model.missing.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf(false) }

    // Deleting an entry makes it missing too, so both routes out are funnelled through one latch:
    // two pops would take the asset screen with them.
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(model) {
        model.deleted.collect {
            if (!leaving) {
                leaving = true
                onBack()
            }
        }
    }
    LaunchedEffect(missing) {
        if (missing && !leaving) {
            leaving = true
            onBack()
        }
    }

    val current = state
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current?.assetName ?: "Entry",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (current != null) {
                        EntryOverflow(
                            onEdit = { onEdit(current.event.assetId.value, current.event.id.value) },
                            onDelete = { confirming = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            QuietLine("Loading…", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        if (confirming) {
            DeleteDialog(
                onDismiss = { confirming = false },
                onConfirm = {
                    confirming = false
                    model.delete()
                },
            )
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = current.event.title.uppercase(),
                style = Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = loggedLine(current.event),
                style = MonoText,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            ReadingsSection(current.event, current.definitions, current.derived)
            MaterialsSection(current.event)
            NotesSection(current.event.notes)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "15 Sep 2026 · 14:42", or just the day when the entry carries no time (§4). */
private fun loggedLine(event: AssetEvent): String {
    val day = runCatching { LocalDate.parse(event.occurredOn).format(LOGGED_DATE) }
        .getOrDefault(event.occurredOn)
    return listOfNotNull(day, event.occurredTime).joinToString(" · ")
}

private val LOGGED_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM uuuu")

/**
 * The readings the entry actually carries, in the order it stored them (§4) — including a value for
 * a definition archived since, because history is what happened — then whatever this entry's own
 * numbers derive (spec §5), marked DERIVED and reading "—" where they do not compute.
 */
@Composable
private fun ReadingsSection(
    event: AssetEvent,
    definitions: Map<DefinitionId, MeasurementDefinition>,
    derived: List<Reading>,
) {
    val rows = event.measurements.sortedBy { it.sortOrder }
    if (rows.isEmpty()) return
    SectionHeader(title = "Readings")
    InstrumentList(count = rows.size + derived.size) { index ->
        if (index >= rows.size) {
            val reading = derived[index - rows.size]
            InstrumentRow(
                eyebrow = "Derived",
                label = reading.definition.label,
                target = formatTarget(reading.definition),
                value = formatValue(reading),
                unit = reading.definition.unit,
                state = reading.state,
            )
            return@InstrumentList
        }
        val measurement = rows[index]
        val definition = definitions[measurement.definitionId]
        InstrumentRow(
            label = definition?.label ?: "Reading",
            target = definition?.let(::formatTarget).orEmpty(),
            value = definition?.let { formatValue(measurement, it) },
            // What it was measured in at the time, not what the definition says today (§4).
            unit = measurement.unit,
            state = definition?.let { d ->
                if (d.valueType != ValueType.NUMBER) {
                    null
                } else {
                    measurement.valueNum?.let { classify(it, d.rangeLow, d.rangeHigh) }
                }
            },
        )
    }
}

@Composable
private fun MaterialsSection(event: AssetEvent) {
    val rows = event.consumables.sortedBy { it.sortOrder }
    SectionHeader(title = "Materials used")
    if (rows.isEmpty()) {
        QuietLine("None recorded")
        return
    }
    Column {
        rows.forEach { used ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = used.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatNumber(used.quantity),
                    style = MonoText,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (used.unit.isNotBlank()) {
                    Text(
                        text = used.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesSection(notes: String) {
    SectionHeader(title = "Notes")
    if (notes.isBlank()) {
        QuietLine("No notes")
    } else {
        Text(text = notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun EntryOverflow(onEdit: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
        DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; onDelete() })
    }
}

/** The readings go with the entry (CASCADE), and the dialog says so before anything happens. */
@Composable
private fun DeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this entry?") },
        text = { Text("Its readings go with it.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = NoteNfcTheme.semanticColors.destructiveAction.foreground)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
