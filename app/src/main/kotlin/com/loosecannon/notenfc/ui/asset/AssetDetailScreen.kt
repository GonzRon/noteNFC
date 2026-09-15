package com.loosecannon.notenfc.ui.asset

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
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.ui.components.ActionGrid
import com.loosecannon.notenfc.ui.components.ActionSpec
import com.loosecannon.notenfc.ui.components.IdentityPlate
import com.loosecannon.notenfc.ui.components.LedgerEntry
import com.loosecannon.notenfc.ui.components.LedgerList
import com.loosecannon.notenfc.ui.components.NoteNfcIcons
import com.loosecannon.notenfc.ui.components.PlateValue
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.components.SectionHeader
import com.loosecannon.notenfc.ui.components.StatusBadge
import com.loosecannon.notenfc.ui.theme.NoteNfcTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * One asset, as the Apollo Service Binder draws it (D12 §8, G1 §1.1): identity plate, quick
 * actions, then sections separated by hairline rules. Phase 1C has no schedules and no events, so
 * the status block is replaced by one quiet line and the ledger's first real use is the tag list.
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
) {
    val model: AssetDetailViewModel = viewModel(key = assetId) { AssetDetailViewModel(graph, assetId) }
    val state by model.state.collectAsStateWithLifecycle()
    val missing by model.missing.collectAsStateWithLifecycle()

    // A deep link, a restored back stack or a replacing import can name an asset that is not there
    // any more. Leaving is the honest answer; an empty plate would pretend it still exists.
    LaunchedEffect(missing) { if (missing) onBack() }

    val asset = state?.asset
    Scaffold(
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
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AssetPlate(current.asset, current.tags, current.links)
            Spacer(Modifier.height(10.dp))
            // No schedules in 1C, so nothing can be due: one quiet line, never a red one (G1 §1.1).
            QuietLine("No schedule yet")
            Spacer(Modifier.height(14.dp))
            ActionGrid(
                actions = listOf(
                    ActionSpec("Write tag", NoteNfcIcons.NfcTag, outlined = true) { onWriteTag(assetId) },
                    ActionSpec("Edit", Icons.Outlined.Edit, outlined = true) { onEdit(assetId) },
                    ActionSpec("Links", NoteNfcIcons.Description, outlined = false, onClick = onOpenLinks),
                    ActionSpec("Backup", Icons.Outlined.Share, outlined = false, onClick = onBackup),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            TagsSection(current.tags)
            LinksSection(current.links)
            NotesSection(current.asset.notes)
            Spacer(Modifier.height(24.dp))
        }
    }
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
