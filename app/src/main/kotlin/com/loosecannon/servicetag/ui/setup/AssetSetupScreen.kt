package com.loosecannon.servicetag.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.DerivedFormula
import com.loosecannon.servicetag.core.model.DerivedSpec
import com.loosecannon.servicetag.core.model.EventKind
import com.loosecannon.servicetag.core.model.EventProfile
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.model.ProfileId
import com.loosecannon.servicetag.core.model.ValueType
import com.loosecannon.servicetag.core.usecase.DefinitionReferenced
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.components.LedgerList
import com.loosecannon.servicetag.ui.components.NoteNfcIcons
import com.loosecannon.servicetag.ui.components.QuietLine
import com.loosecannon.servicetag.ui.components.SectionHeader
import com.loosecannon.servicetag.ui.components.StatusBadge
import com.loosecannon.servicetag.ui.journal.formatTarget
import com.loosecannon.servicetag.ui.theme.ControlShape
import com.loosecannon.servicetag.ui.theme.Eyebrow
import com.loosecannon.servicetag.ui.theme.MonoText
import com.loosecannon.servicetag.ui.theme.NoteNfcTheme

/**
 * What one asset measures and what can be logged against it (spec §9): two hairline-ruled lists
 * under one app bar, no cards and no FAB (D12 §7). A row is tapped to edit it; everything else it
 * can do — move, archive, delete — is in its own overflow, which is what stands in for a drag
 * (recorded ruling: up/down is the whole reorder gesture).
 *
 * The only refusal that gets a dialog is a delete the domain would not do: it names how many
 * entries, which derived readings and which actions still point at the row, because every one of
 * those is something the user would have to change first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetSetupScreen(
    graph: AppGraph,
    assetId: String,
    onBack: () -> Unit,
    onEditDefinition: (assetId: String, definitionId: String?) -> Unit,
    onEditProfile: (assetId: String, profileId: String?) -> Unit,
) {
    val model: AssetSetupViewModel = viewModel(key = assetId) { AssetSetupViewModel(graph, assetId) }
    val state by model.state.collectAsStateWithLifecycle()
    val refusal by model.refusal.collectAsStateWithLifecycle()
    val missing by model.missing.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(model) { model.messages.collect { snackbars.showSnackbar(it) } }

    // A backup import can replace the asset under an open screen; one latch, one pop.
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(missing) {
        if (missing && !leaving) {
            leaving = true
            onBack()
        }
    }

    var deletingDefinition by remember { mutableStateOf<MeasurementDefinition?>(null) }
    var deletingProfile by remember { mutableStateOf<EventProfile?>(null) }

    val current = state
    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current?.assetName ?: "Readings & actions",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (current == null) {
            QuietLine("Loading…", Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        refusal?.let { refused ->
            ReferencedDialog(
                refusal = refused,
                definitionLabels = current.sourcesById.mapValues { it.value.label },
                profileNames = current.profiles.associate { it.id to it.name },
                onDismiss = model::dismissRefusal,
            )
        }
        deletingDefinition?.let { row ->
            ConfirmDialog(
                title = "Delete this reading?",
                // Whether it can go at all is the use case's call; the refusal dialog says so when
                // it cannot, so the confirm no longer promises "it has no data" before anyone looked.
                body = "This cannot be undone.",
                onDismiss = { deletingDefinition = null },
                onConfirm = {
                    deletingDefinition = null
                    model.deleteDefinition(row.id)
                },
            )
        }
        deletingProfile?.let { row ->
            ConfirmDialog(
                title = "Delete this action?",
                body = "Past entries keep their readings.",
                onDismiss = { deletingProfile = null },
                onConfirm = {
                    deletingProfile = null
                    model.deleteProfile(row.id)
                },
            )
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "READINGS & ACTIONS",
                style = Eyebrow,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionHeader(title = "Readings")
            if (current.definitions.isEmpty()) {
                QuietLine("No readings yet · add one to start measuring this asset")
            } else {
                LedgerList(count = current.definitions.size) { index ->
                    val row = current.definitions[index]
                    DefinitionRow(
                        definition = row,
                        sources = current.sourcesById,
                        canMoveUp = index > 0,
                        canMoveDown = index < current.definitions.lastIndex,
                        onEdit = { onEditDefinition(assetId, row.id.value) },
                        onMove = { up -> model.moveDefinition(row.id, up) },
                        onArchive = { archived -> model.archiveDefinition(row.id, archived) },
                        onDelete = { deletingDefinition = row },
                    )
                }
            }
            AddButton(text = "Add reading") { onEditDefinition(assetId, null) }

            SectionHeader(title = "Actions")
            if (current.profiles.isEmpty()) {
                QuietLine("No actions yet · add one to log this asset in a tap")
            } else {
                LedgerList(count = current.profiles.size) { index ->
                    val row = current.profiles[index]
                    ProfileRow(
                        profile = row,
                        canMoveUp = index > 0,
                        canMoveDown = index < current.profiles.lastIndex,
                        onEdit = { onEditProfile(assetId, row.id.value) },
                        onMove = { up -> model.moveProfile(row.id, up) },
                        onArchive = { archived -> model.archiveProfile(row.id, archived) },
                        onDelete = { deletingProfile = row },
                    )
                }
            }
            AddButton(text = "Add action") { onEditProfile(assetId, null) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * One reading. A DERIVED row carries the equals glyph and its formula in mono with the two sources
 * named, because "Rejection 94.2 %" is only trustworthy if you can see what it is made of (§5).
 */
@Composable
private fun DefinitionRow(
    definition: MeasurementDefinition,
    sources: Map<DefinitionId, MeasurementDefinition>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Boolean) -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (definition.kind == DefinitionKind.DERIVED) {
                    Icon(
                        imageVector = NoteNfcIcons.Equal,
                        contentDescription = "Derived",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = definition.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (definition.archivedAt != null) {
                    StatusBadge(label = "Archived", colors = NoteNfcTheme.semanticColors.seasonInactive)
                }
            }
            definition.derived?.let { spec ->
                Text(
                    text = formulaLine(spec, sources),
                    style = MonoText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = definition.metaLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowOverflow(
            archived = definition.archivedAt != null,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onEdit = onEdit,
            onMove = onMove,
            onArchive = onArchive,
            onDelete = onDelete,
        )
    }
}

/** One quick action: what it is called, what kind of entry it logs, and how many fields it asks for. */
@Composable
private fun ProfileRow(
    profile: EventProfile,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Boolean) -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f).padding(top = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (profile.archivedAt != null) {
                    StatusBadge(label = "Archived", colors = NoteNfcTheme.semanticColors.seasonInactive)
                }
            }
            Text(
                text = "${kindLabel(profile.eventKind)} · ${fieldCount(profile.fields.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RowOverflow(
            archived = profile.archivedAt != null,
            canMoveUp = canMoveUp,
            canMoveDown = canMoveDown,
            onEdit = onEdit,
            onMove = onMove,
            onArchive = onArchive,
            onDelete = onDelete,
        )
    }
}

/**
 * The per-row menu. Move up / Move down is the reorder gesture: a drag handle inside a scrolling
 * column is a gesture fight, and two menu items are unambiguous with one thumb. The end of a list
 * disables the move that would do nothing rather than hiding it, so the menu keeps its shape.
 */
@Composable
private fun RowOverflow(
    archived: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onMove: (Boolean) -> Unit,
    onArchive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
        DropdownMenuItem(
            text = { Text("Move up") },
            enabled = canMoveUp,
            onClick = { open = false; onMove(true) },
        )
        DropdownMenuItem(
            text = { Text("Move down") },
            enabled = canMoveDown,
            onClick = { open = false; onMove(false) },
        )
        DropdownMenuItem(
            text = { Text(if (archived) "Unarchive" else "Archive") },
            onClick = { open = false; onArchive(!archived) },
        )
        DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; onDelete() })
    }
}

/** The one way to add a row in either section: outlined, full width, no FAB (D12 §7). */
@Composable
private fun AddButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = ControlShape,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text = text, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * Why the delete did not happen, in the words of the things that stopped it. Archive is offered in
 * the sentence rather than as a second button: the dialog's job is to explain, and the row's own
 * overflow already has Archive in it.
 */
@Composable
internal fun ReferencedDialog(
    refusal: DefinitionReferenced,
    definitionLabels: Map<DefinitionId, String>,
    profileNames: Map<ProfileId, String>,
    onDismiss: () -> Unit,
) {
    val lines = buildList {
        if (refusal.measurements > 0) add(readingCount(refusal.measurements))
        refusal.derivedBy
            .mapNotNull { definitionLabels[it] }
            .takeIf { it.isNotEmpty() }
            ?.let { add("Used by ${it.joinToString(", ")}") }
        refusal.profiles
            .mapNotNull { profileNames[it] }
            .takeIf { it.isNotEmpty() }
            ?.let { add("Offered by ${it.joinToString(", ")}") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cannot delete this reading") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                lines.forEach { Text(it) }
                Text("Archive it instead to take it off the forms and keep its history.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** A destructive confirm in the destructive family (D12 §5); the words carry the consequence. */
@Composable
internal fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = NoteNfcTheme.semanticColors.destructiveAction.foreground)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The formula under a derived row's label, with the sources named: "= (Pre-filter TDS −
 * Post-membrane TDS) / Pre-filter TDS × 100". A source the asset no longer has reads "?" rather
 * than vanishing, because a formula with a hole in it is the thing worth seeing.
 */
internal fun formulaLine(
    spec: DerivedSpec,
    sources: Map<DefinitionId, MeasurementDefinition>,
): String {
    val a = sources[spec.sourceA]?.label ?: "?"
    val b = sources[spec.sourceB]?.label ?: "?"
    return when (spec.formula) {
        DerivedFormula.PERCENT_DROP -> "= ($a − $b) / $a × 100"
    }
}

/** Unit, target and the meter flag on one quiet line under the label. */
private fun MeasurementDefinition.metaLine(): String {
    val shape = when (valueType) {
        ValueType.NUMBER -> formatTarget(this)
        ValueType.TEXT -> "Text"
        ValueType.BOOLEAN -> "Yes / no"
    }
    return listOfNotNull(
        unit.takeIf { it.isNotBlank() },
        shape,
        "Meter".takeIf { isMeter },
    ).joinToString(" · ")
}

/** The event kind in words, for the row and for the profile editor's picker (Task 7's dropdown). */
internal fun kindLabel(kind: EventKind): String = when (kind) {
    EventKind.MAINTENANCE -> "Maintenance"
    EventKind.INSPECTION -> "Inspection"
    EventKind.MEASUREMENT -> "Measurement"
    EventKind.TREATMENT -> "Treatment"
    EventKind.INCIDENT -> "Incident"
    EventKind.REPLACEMENT -> "Replacement"
    EventKind.SEASON_START -> "Season start"
    EventKind.SEASON_END -> "Season end"
    EventKind.NOTE -> "Note"
    EventKind.CUSTOM -> "Custom"
}

private fun fieldCount(n: Int): String = if (n == 1) "1 field" else "$n fields"

private fun readingCount(n: Int): String = if (n == 1) "1 reading logged" else "$n readings logged"
