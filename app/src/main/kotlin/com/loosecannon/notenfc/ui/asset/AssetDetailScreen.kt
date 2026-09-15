package com.loosecannon.notenfc.ui.asset

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.core.journal.RangeState
import com.loosecannon.notenfc.core.journal.Reading
import com.loosecannon.notenfc.core.journal.SeedTemplates
import com.loosecannon.notenfc.core.journal.classify
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.ValueType
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.ActionGrid
import com.loosecannon.notenfc.ui.components.ActionSpec
import com.loosecannon.notenfc.ui.components.IdentityPlate
import com.loosecannon.notenfc.ui.components.InstrumentList
import com.loosecannon.notenfc.ui.components.InstrumentRow
import com.loosecannon.notenfc.ui.components.LedgerEntry
import com.loosecannon.notenfc.ui.components.LedgerList
import com.loosecannon.notenfc.ui.components.NoteNfcIcons
import com.loosecannon.notenfc.ui.components.PlateValue
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.SectionHeader
import com.loosecannon.notenfc.ui.components.StatusBadge
import com.loosecannon.notenfc.ui.journal.eventDetailLine
import com.loosecannon.notenfc.ui.journal.formatTarget
import com.loosecannon.notenfc.ui.journal.formatValue
import com.loosecannon.notenfc.ui.journal.quickActionLabel
import com.loosecannon.notenfc.ui.journal.stateColors
import com.loosecannon.notenfc.ui.journal.stateIcon
import com.loosecannon.notenfc.ui.journal.stateLabel
import com.loosecannon.notenfc.ui.theme.NoteNfcTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One asset, as the Apollo Service Binder draws it (D12 §8, G1 §1.1): identity plate, the current
 * readings, quick actions, then the service record and the reference sections, separated by
 * hairline rules. There are still no schedules, so the status block is one quiet line; everything
 * else on the screen is the journal of spec §10.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetDetailScreen(
    graph: AppGraph,
    assetId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onWriteTag: (String) -> Unit,
    onOpenLinks: () -> Unit,
    onBackup: () -> Unit,
    onLogEvent: (assetId: String, profileId: String) -> Unit,
    onOpenEvent: (eventId: String) -> Unit,
) {
    val model: AssetDetailViewModel = viewModel(key = assetId) { AssetDetailViewModel(graph, assetId) }
    val state by model.state.collectAsStateWithLifecycle()
    val missing by model.missing.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    var pickingTemplate by remember { mutableStateOf(false) }

    // A deep link, a restored back stack or a replacing import can name an asset that is not there
    // any more. Leaving is the honest answer; an empty plate would pretend it still exists.
    LaunchedEffect(missing) { if (missing) onBack() }
    LaunchedEffect(model) { model.messages.collect { snackbars.showSnackbar(it) } }

    val asset = state?.asset
    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = asset?.name ?: "Asset",
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
                    if (asset != null) {
                        DetailOverflow(
                            archived = asset.status != AssetStatus.ACTIVE,
                            onEdit = { onEdit(assetId) },
                            onArchive = model::archive,
                            onUnarchive = model::unarchive,
                        )
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
        if (pickingTemplate) {
            TemplatePicker(
                onDismiss = { pickingTemplate = false },
                onPick = { key ->
                    pickingTemplate = false
                    model.setUpFromTemplate(key)
                },
            )
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AssetPlate(current.asset, current.tags, current.links)
            ReadingsSection(current.readings)
            Spacer(Modifier.height(10.dp))
            // No schedules in 2A, so nothing can be due: one quiet line, never a red one (G1 §1.1).
            QuietLine("No schedule yet")
            Spacer(Modifier.height(14.dp))
            ActionGrid(
                actions = detailActions(
                    assetId = assetId,
                    profiles = current.profiles,
                    bare = current.definitions.isEmpty() && current.profiles.isEmpty(),
                    onLogEvent = onLogEvent,
                    onEdit = onEdit,
                    onWriteTag = onWriteTag,
                    onOpenLinks = onOpenLinks,
                    onBackup = onBackup,
                    onSetUp = { pickingTemplate = true },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            ServiceRecordSection(current.events, current.definitions, onOpenEvent)
            TagsSection(current.tags)
            LinksSection(current.links)
            NotesSection(current.asset.notes)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Quick actions first, because logging is what someone standing next to the machine came to do
 * (spec §10); the four utility actions keep the order 1C gave them. "Set up from template" only
 * appears while the asset has nothing to log against — once it has, the template is refused
 * anyway, and an action that cannot work is worse than no action.
 */
@Composable
private fun detailActions(
    assetId: String,
    profiles: List<EventProfile>,
    bare: Boolean,
    onLogEvent: (String, String) -> Unit,
    onEdit: (String) -> Unit,
    onWriteTag: (String) -> Unit,
    onOpenLinks: () -> Unit,
    onBackup: () -> Unit,
    onSetUp: () -> Unit,
): List<ActionSpec> {
    val ledger = NoteNfcIcons.History
    val nfc = NoteNfcIcons.NfcTag
    val documents = NoteNfcIcons.Description
    val backup = NoteNfcIcons.Backup
    return buildList {
        profiles.forEach { profile ->
            add(
                ActionSpec(quickActionLabel(profile), ledger, outlined = false) {
                    onLogEvent(assetId, profile.id.value)
                },
            )
        }
        add(ActionSpec("Write tag", nfc, outlined = true) { onWriteTag(assetId) })
        add(ActionSpec("Edit", Icons.Outlined.Edit, outlined = true) { onEdit(assetId) })
        add(ActionSpec("Links", documents, outlined = false, onClick = onOpenLinks))
        add(ActionSpec("Backup", backup, outlined = false, onClick = onBackup))
        if (bare) add(ActionSpec("Set up from template", Icons.Outlined.Add, outlined = true, onClick = onSetUp))
    }
}

/** The five seeds by name. A template is starter data, so the dialog explains nothing further. */
@Composable
private fun TemplatePicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up from template") },
        text = {
            Column {
                SeedTemplates.all.forEach { template ->
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(template.key) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Absent, not empty, when the asset has no definitions: there is no instrument panel to show. */
@Composable
private fun ReadingsSection(readings: List<Reading>) {
    if (readings.isEmpty()) return
    SectionHeader(title = "Current readings")
    InstrumentList(count = readings.size) { index ->
        val reading = readings[index]
        InstrumentRow(
            label = reading.definition.label,
            target = formatTarget(reading.definition),
            value = formatValue(reading.measurement, reading.definition),
            // The measurement's unit is a snapshot of the definition's at entry (§4): show what
            // was actually measured in, and fall back to the definition only for an empty row.
            unit = reading.measurement?.unit ?: reading.definition.unit,
            state = reading.state,
        )
    }
}

/** The chronological ledger of D12 §8 — a maintenance record, newest first, never a feed. */
@Composable
private fun ServiceRecordSection(
    events: List<AssetEvent>,
    definitions: List<MeasurementDefinition>,
    onOpenEvent: (String) -> Unit,
) {
    SectionHeader(title = "Service record")
    if (events.isEmpty()) {
        QuietLine("No service recorded yet")
        return
    }
    val byId: Map<DefinitionId, MeasurementDefinition> = definitions.associateBy { it.id }
    LedgerList(count = events.size) { index ->
        val event = events[index]
        val (day, month, year) = event.occurredOn.asLedgerDate()
        val flagged = outOfRange(event, byId)
        LedgerEntry(
            day = day,
            month = month,
            year = year,
            title = event.title,
            detail = eventDetailLine(event, byId).takeIf { it.isNotBlank() },
            badge = flagged?.let { state ->
                {
                    StatusBadge(
                        label = stateLabel(state),
                        colors = stateColors(state, NoteNfcTheme.semanticColors),
                        icon = stateIcon(state),
                    )
                }
            },
            modifier = Modifier.clickable { onOpenEvent(event.id.value) },
        )
    }
}

/**
 * The ledger badge says something only when a reading in the entry is outside its target — an
 * in-range entry is the normal case and does not need decorating (D12 §5).
 */
private fun outOfRange(
    event: AssetEvent,
    definitions: Map<DefinitionId, MeasurementDefinition>,
): RangeState? = event.measurements
    .sortedBy { it.sortOrder }
    .firstNotNullOfOrNull { m ->
        val definition = definitions[m.definitionId] ?: return@firstNotNullOfOrNull null
        if (definition.valueType != ValueType.NUMBER) return@firstNotNullOfOrNull null
        val value = m.valueNum ?: return@firstNotNullOfOrNull null
        classify(value, definition.rangeLow, definition.rangeHigh)
            .takeIf { it == RangeState.LOW || it == RangeState.HIGH }
    }

@Composable
private fun DetailOverflow(
    archived: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; onEdit() })
        // Archive-first (R-9): there is no delete here, and there will not be one in Phase 1.
        DropdownMenuItem(
            text = { Text(if (archived) "Unarchive" else "Archive") },
            onClick = { open = false; if (archived) onUnarchive() else onArchive() },
        )
    }
}

/**
 * The plate's four cells are fixed in G1 §1.1. SERIAL has no field behind it until the Phase 2
 * asset profile, and a blank [PlateValue] renders as "—", which is the honest thing to show.
 */
@Composable
private fun AssetPlate(asset: Asset, tags: List<TagBinding>, links: List<ExternalLink>) {
    IdentityPlate(
        category = asset.category.ifBlank { "Asset" },
        model = asset.name,
        name = asset.description.takeIf { it.isNotBlank() },
        cells = listOf(
            "Serial" to PlateValue("", mono = true),
            "NFC tag" to PlateValue(tags.firstOrNull()?.let(::tagIdentity).orEmpty(), mono = true),
            "Created" to PlateValue(asset.createdAt.asPlateDate()),
            "Links" to PlateValue(links.size.takeIf { it > 0 }?.toString().orEmpty()),
        ),
        icon = categoryIcon(asset.category),
        badge = statusLabel(asset.status)?.let { label ->
            { StatusBadge(label = label, colors = NoteNfcTheme.semanticColors.seasonInactive) }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The tag's own id, not the chip's hardware UID (G1 §3 correction a), with the payload format. */
private fun tagIdentity(tag: TagBinding): String {
    val format = if (tag.payloadFormat == PayloadFormat.V1) "v1" else "legacy"
    return "${tag.id.value.take(8)} · $format"
}

@Composable
private fun TagsSection(tags: List<TagBinding>) {
    SectionHeader(title = "Tags")
    if (tags.isEmpty()) {
        QuietLine("No tag yet · Write tag to add one")
        return
    }
    LedgerList(count = tags.size) { index ->
        val tag = tags[index]
        val stamped = tag.writtenAt ?: tag.createdAt
        val (day, month, year) = stamped.asLedgerDate()
        LedgerEntry(
            day = day,
            month = month,
            year = year,
            title = if (tag.writtenAt != null) "Tag written" else "Tag bound",
            detail = tagIdentity(tag),
            badge = if (tag.status != TagStatus.ACTIVE) {
                {
                    StatusBadge(
                        label = tag.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        colors = NoteNfcTheme.semanticColors.seasonInactive,
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun LinksSection(links: List<ExternalLink>) {
    SectionHeader(title = "Links")
    if (links.isEmpty()) {
        QuietLine("No links yet")
        return
    }
    Column {
        links.forEach { link ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = link.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = link.kind.name.lowercase().replaceFirstChar { it.uppercase() },
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
        Text(
            text = notes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Broad categories only (D12 §12): a keyword in the category the user typed picks one of a handful
 * of glyphs. There is deliberately no per-brand icon — the asset's name supplies that specificity.
 */
@Composable
private fun categoryIcon(category: String): ImageVector {
    val text = category.lowercase()
    fun any(vararg words: String) = words.any { it in text }
    return when {
        any("tool", "equip", "mower", "machine", "engine", "pump", "hvac") -> Icons.Outlined.Build
        any("power", "battery", "electric", "ups", "meter", "gauge") -> NoteNfcIcons.Speed
        any("computer", "network", "server", "nfc", "tag") -> NoteNfcIcons.NfcTag
        any("home", "house", "water", "pool", "tub", "yard", "garden", "outdoor") -> Icons.Outlined.Home
        else -> Icons.Outlined.Info
    }
}

private val plateDate = DateTimeFormatter.ofPattern("d MMM uuuu")
private val ledgerDay = DateTimeFormatter.ofPattern("dd")
private val ledgerMonth = DateTimeFormatter.ofPattern("MMM")
private val ledgerYear = DateTimeFormatter.ofPattern("uuuu")

private fun Long.zoned() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

private fun Long.asPlateDate(): String = zoned().format(plateDate)

/** The ledger's 64dp date column wants the three parts apart, not one formatted string. */
private fun Long.asLedgerDate(): Triple<String, String, String> {
    val at = zoned()
    return Triple(at.format(ledgerDay), at.format(ledgerMonth), at.format(ledgerYear))
}

/**
 * An event is dated by the calendar day it happened on, not by when the row was written, so the
 * ledger splits `occurredOn` rather than a millisecond instant. A string the domain would have
 * refused is shown verbatim rather than dropped.
 */
private fun String.asLedgerDate(): Triple<String, String, String> {
    val date = runCatching { LocalDate.parse(this) }.getOrNull()
        ?: return Triple(this, "", "")
    return Triple(date.format(ledgerDay), date.format(ledgerMonth), date.format(ledgerYear))
}
