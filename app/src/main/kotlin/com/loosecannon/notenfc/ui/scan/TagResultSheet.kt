package com.loosecannon.notenfc.ui.scan

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.di.AppGraph
import com.loosecannon.notenfc.links.LinkLauncher
import com.loosecannon.notenfc.ui.components.NoteNfcIcons
import com.loosecannon.notenfc.ui.links.host
import com.loosecannon.notenfc.ui.components.QuietLine
import com.loosecannon.notenfc.ui.nav.Route
import com.loosecannon.notenfc.ui.theme.ControlShape
import com.loosecannon.notenfc.ui.theme.MonoText
import com.loosecannon.notenfc.ui.theme.NoteNfcTheme
import com.loosecannon.notenfc.ui.theme.SheetShape
import com.loosecannon.notenfc.ui.theme.SheetSentence

/**
 * What a scanned (format, key) pair turned out to be — including "not ours" (format NONE).
 *
 * The sheets here are the D12 §11 / G1 §1.4 set: eyebrow, one sentence, the mono identifier,
 * actions stacked with the filled one first. None of them is an error: an unregistered tag, a
 * legacy tag and a foreign tag are all offers, and only the wording and the glyph change.
 *
 * @param key the scanned tag's identifier — but only while [format] names a payload format we
 *   wrote. When `format == "NONE"` there is no identifier to show and `key` carries a prose reason
 *   the tag could not be read, so nothing may present it as an id or look it up as one.
 */
@Composable
fun TagResultSheet(
    graph: AppGraph,
    format: String,
    key: String,
    onDismiss: () -> Unit,
    onWriteTag: (Route.WriteTag) -> Unit,
    onOpenAsset: (String) -> Unit,
    onNewAsset: () -> Unit,
) {
    val model: TagResultViewModel =
        viewModel(key = "$format/$key") { TagResultViewModel(graph, format, key) }
    val state by model.state.collectAsStateWithLifecycle()
    val targets by model.targets.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is TagResultEvent.OpenAsset -> onOpenAsset(event.id)
                is TagResultEvent.Failed -> { picking = false; problem = event.message }
                TagResultEvent.Dismiss -> onDismiss()
            }
        }
    }

    SheetHost(onDismiss = onDismiss) {
        when (val result = state) {
            TagResult.Loading -> NfcSheet(eyebrow = "Reading tag", sentence = "Looking this tag up…")

            is TagResult.OpensAsset -> {
                // A bound tag needs no decision: the sheet says what it is and the screen moves on.
                LaunchedEffect(result) { onOpenAsset(result.asset.id.value) }
                NfcSheet(
                    eyebrow = "Tag detected",
                    accent = NoteNfcTheme.semanticColors.maintenanceOkay.foreground,
                    glyph = NoteNfcIcons.NfcTag,
                    sentence = result.asset.name,
                    identifier = result.tag.identityLine(),
                ) {
                    QuietLine("Opening asset…")
                }
            }

            is TagResult.LaunchesLink -> {
                // A link tag launches its note and shows no sheet at all (R-7).
                LaunchedEffect(result) {
                    activity?.let { LinkLauncher.open(it, result.uri) }
                    onDismiss()
                }
            }

            is TagResult.Unregistered -> NfcSheet(
                eyebrow = "Unregistered tag",
                accent = NoteNfcTheme.semanticColors.dueSoon.foreground,
                border = NoteNfcTheme.semanticColors.dueSoon.foreground,
                glyph = Icons.Outlined.Info,
                sentence = "This tag is not assigned to anything yet.",
                identifier = result.tag.identityLine(),
                problem = problem,
                actions = {
                    FilledAction("Bind to asset or note") { picking = true }
                    TextAction("Cancel", onDismiss)
                },
            )

            is TagResult.Revoked -> NfcSheet(
                eyebrow = if (result.tag.status == TagStatus.LOST) "Tag marked lost" else "Tag retired",
                accent = NoteNfcTheme.semanticColors.dueSoon.foreground,
                border = NoteNfcTheme.semanticColors.dueSoon.foreground,
                glyph = Icons.Outlined.Info,
                sentence = "This tag was taken out of service. Binding it again puts it back to work.",
                identifier = result.tag.identityLine(),
                problem = problem,
                actions = {
                    FilledAction("Bind to asset or note") { picking = true }
                    TextAction("Cancel", onDismiss)
                },
            )

            is TagResult.NotInRecords -> NfcSheet(
                eyebrow = "Unregistered tag",
                accent = NoteNfcTheme.semanticColors.dueSoon.foreground,
                border = NoteNfcTheme.semanticColors.dueSoon.foreground,
                glyph = Icons.Outlined.Info,
                sentence = "This noteNFC tag is not in this phone's records.",
                identifier = identityLine(PayloadFormat.V1.name, result.tagId),
                problem = problem,
                actions = {
                    FilledAction("Bind to asset or note") { picking = true }
                    OutlinedAction("Write a new tag over it") { onWriteTag(Route.WriteTag("none", null, null)) }
                    TextAction("Cancel", onDismiss)
                },
            )

            is TagResult.Legacy -> NfcSheet(
                // A migration opportunity, not damaged data (D12 §11, D13 §3).
                eyebrow = "Legacy tag",
                accent = MaterialTheme.colorScheme.tertiary,
                glyph = NoteNfcIcons.History,
                sentence = "This tag uses the 2024 noteNFC identifier.",
                identifier = identityLine(PayloadFormat.LEGACY_MD5.name, result.key),
                problem = problem,
                actions = {
                    FilledAction("Rewrite in format v1") { onWriteTag(Route.WriteTag("none", null, null)) }
                    OutlinedAction("Bind as-is") { picking = true }
                    TextAction("Cancel", onDismiss)
                },
            )

            is TagResult.NotOurs -> NfcSheet(
                eyebrow = "Not a noteNFC tag",
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
                glyph = Icons.Outlined.Info,
                sentence = "This tag holds something else.",
                problem = problem,
                actions = {
                    FilledAction("Write a new tag over it") { onWriteTag(Route.WriteTag("none", null, null)) }
                    TextAction("Cancel", onDismiss)
                },
            ) {
                QuietLine(result.reason)
            }
        }
    }

    if (picking) {
        BindTargetPicker(
            targets = targets,
            onDismiss = { picking = false },
            onNewAsset = { picking = false; onNewAsset() },
            onPick = { target -> model.bind(target) },
        )
    }
}

/**
 * The sheets are destinations rather than overlays (the trampoline can land on one with nothing
 * behind it), so the panel is anchored to the bottom of the canvas instead of floating over a
 * scrim. Tapping the canvas above it dismisses, exactly as a scrim would.
 */
@Composable
private fun SheetHost(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // A scrimless dismiss target: the canvas above the panel, without a full-screen ripple.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        verticalArrangement = Arrangement.Bottom,
        content = content,
    )
}

/**
 * The shared sheet anatomy of G1 §1.4: eyebrow (bold uppercase, with a glyph) → one sentence at
 * 18sp Medium → the identifier in mono → actions stacked, filled first.
 */
@Composable
internal fun NfcSheet(
    eyebrow: String,
    sentence: String,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = MaterialTheme.colorScheme.surface,
    border: Color? = null,
    glyph: ImageVector? = null,
    identifier: String? = null,
    problem: String? = null,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    supporting: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Surface(
        color = container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = SheetShape,
        tonalElevation = 3.dp,
        border = border?.let { BorderStroke(1.dp, it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (glyph != null) {
                    Icon(imageVector = glyph, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = accent,
                )
            }
            Text(text = sentence, style = SheetSentence)
            identifier?.let {
                Text(text = it, style = MonoText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            supporting?.invoke(this)
            problem?.let { QuietLine(it) }
            actions?.invoke(this)
        }
    }
}

@Composable
internal fun ColumnScope.FilledAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, shape = ControlShape, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
internal fun ColumnScope.OutlinedAction(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = ControlShape, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
internal fun ColumnScope.TextAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.align(Alignment.End)) { Text(label) }
}

/**
 * Where a tag may point: an asset, a saved link, or an asset that does not exist yet — and for
 * that last one the honest answer is to go and make it, then scan the tag again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BindTargetPicker(
    targets: BindTargets,
    onDismiss: () -> Unit,
    onNewAsset: () -> Unit,
    onPick: (TagTarget) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "BIND THIS TAG TO",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PickerRow(title = "New asset…", detail = "Create it, then scan this tag again", onClick = onNewAsset)
            if (targets.assets.isEmpty() && targets.links.isEmpty()) {
                QuietLine("Nothing to bind to yet")
            }
            targets.assets.forEach { asset ->
                PickerRow(
                    title = asset.name,
                    detail = asset.category.ifBlank { "Asset" },
                    onClick = { onPick(TagTarget.AssetTarget(asset.id)) },
                )
            }
            targets.links.forEach { link ->
                PickerRow(
                    title = link.label,
                    detail = link.host(),
                    mono = true,
                    onClick = { onPick(TagTarget.LinkTarget(link.id)) },
                )
            }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    detail: String,
    mono: Boolean = false,
    onClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = detail,
                style = if (mono) MonoText else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
