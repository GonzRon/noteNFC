package com.loosecannon.servicetag.ui.asset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.loosecannon.servicetag.core.journal.CategorySuggestions
import com.loosecannon.servicetag.core.journal.SeedTemplates
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.components.ServiceTagIcons
import com.loosecannon.servicetag.ui.components.SectionHeader
import com.loosecannon.servicetag.ui.theme.BadgeShape
import com.loosecannon.servicetag.ui.theme.ControlShape
import com.loosecannon.servicetag.ui.theme.MonoText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * Create ([assetId] null) or edit one asset: the grouped form of spec §9 — IDENTITY, PLACEMENT,
 * PURCHASE, WARRANTY, NOTES, and on a new asset only, TEMPLATE. Save sits in the app bar and
 * again at the bottom so it is reachable with the keyboard open (G1 §1.3).
 *
 * [parentId] is the "Part of" a new asset opens with, which is how "+ Add component" on a
 * parent's screen makes a child. Every rule belongs to the use cases; the screen only draws what
 * they refused — a line under each bad field, and the refused reparent on the snackbar, because
 * "that asset is inside this one" is about a pair and not about any single input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetEditScreen(
    graph: AppGraph,
    assetId: String?,
    onDone: (String) -> Unit,
    onBack: () -> Unit,
    parentId: String? = null,
) {
    // The key carries the parent as well as the id: "+ Add component" on two different parents
    // must not share one half-filled form, and neither must a plain "Add asset" and a component.
    val model: AssetEditViewModel = viewModel(key = assetId ?: "new-${parentId ?: "root"}") {
        AssetEditViewModel(graph, assetId, parentId)
    }
    val state by model.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    // The save itself belongs to the ViewModel; this only listens for where it says to go next.
    LaunchedEffect(model) { model.saved.collect { id -> onDone(id.value) } }
    LaunchedEffect(model) { model.messages.collect { snackbars.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
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
            IdentityBlock(state, model)
            PlacementBlock(state, model)
            PurchaseBlock(state, model)
            WarrantyBlock(state, model)

            SectionHeader(title = "Notes")
            FormField(
                value = state.notes,
                onValueChange = model::onNotes,
                label = "Notes",
                minLines = 3,
            )

            // Editing an existing asset shows no template row: a template is starter data, and
            // an asset that has been in use has rows of its own that it must not clobber (§7).
            if (!state.editing) {
                TemplateRow(selected = state.templateKey, onSelect = model::onTemplate)
            }
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

/** What the thing is. Name first, because it is the one field every version of this app required. */
@Composable
private fun IdentityBlock(state: AssetEditState, model: AssetEditViewModel) {
    SectionHeader(title = "Identity")
    FormField(
        value = state.name,
        onValueChange = model::onName,
        label = "Name",
        problem = state.problems[AssetField.NAME],
    )
    CategoryField(value = state.category, onValueChange = model::onCategory)
    FormField(value = state.manufacturer, onValueChange = model::onManufacturer, label = "Manufacturer")
    FormField(value = state.model, onValueChange = model::onModel, label = "Model")
    FormField(value = state.serialNumber, onValueChange = model::onSerialNumber, label = "Serial number")
    FormField(value = state.description, onValueChange = model::onDescription, label = "Description")
}

/** Where it is, what it is part of, and when it is in use at all (spec §5, §6). */
@Composable
private fun PlacementBlock(state: AssetEditState, model: AssetEditViewModel) {
    SectionHeader(title = "Placement")
    FormField(value = state.location, onValueChange = model::onLocation, label = "Location")
    ChoiceField(
        label = "Part of",
        choices = state.parentChoices,
        selected = state.parentId,
        onSelect = model::onParent,
        problem = state.problems[AssetField.PARENT],
    )
    SeasonField(state, model)
}

/** What it cost and when it arrived. A price is text until [priceHint]'s currency resolves it. */
@Composable
private fun PurchaseBlock(state: AssetEditState, model: AssetEditViewModel) {
    SectionHeader(title = "Purchase")
    DateField(
        value = state.purchaseOn,
        onValueChange = model::onPurchaseOn,
        label = "Purchase date",
        problem = state.problems[AssetField.PURCHASE_ON],
    )
    DateField(
        value = state.inServiceOn,
        onValueChange = model::onInServiceOn,
        label = "In service date",
        problem = state.problems[AssetField.IN_SERVICE_ON],
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        FormField(
            value = state.price,
            onValueChange = model::onPrice,
            label = "Price",
            problem = state.problems[AssetField.PRICE],
            hint = priceHint(state.currency),
            mono = true,
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        FormField(
            value = state.currency,
            onValueChange = model::onCurrency,
            label = "Currency",
            problem = state.problems[AssetField.CURRENCY],
            mono = true,
            modifier = Modifier.width(126.dp),
        )
    }
    FormField(value = state.vendor, onValueChange = model::onVendor, label = "Vendor")
}

/** When the cover runs out, and whatever the paperwork says about it. */
@Composable
private fun WarrantyBlock(state: AssetEditState, model: AssetEditViewModel) {
    SectionHeader(title = "Warranty")
    DateField(
        value = state.warrantyExpiresOn,
        onValueChange = model::onWarrantyExpiresOn,
        label = "Expires on",
        problem = state.problems[AssetField.WARRANTY_EXPIRES_ON],
    )
    FormField(
        value = state.warrantyNotes,
        onValueChange = model::onWarrantyNotes,
        label = "Warranty notes",
        minLines = 2,
    )
}

/**
 * Year-round is the absence of a window (spec §6), so the switch is the first thing asked and the
 * two month-day fields only exist once it is off. The picker is an ordinary date picker whose year
 * is thrown away: a season has a month and a day and no year at all.
 */
@Composable
private fun SeasonField(state: AssetEditState, model: AssetEditViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Year-round", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "Off means this asset is only in use between two dates each year.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.seasonYearRound, onCheckedChange = model::onSeasonYearRound)
    }
    if (!state.seasonYearRound) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MonthDayField(
                value = state.seasonStart,
                onValueChange = model::onSeasonStart,
                label = "Season starts",
                problem = state.problems[AssetField.SEASON_START],
                modifier = Modifier.weight(1f),
            )
            MonthDayField(
                value = state.seasonEnd,
                onValueChange = model::onSeasonEnd,
                label = "Season ends",
                problem = state.problems[AssetField.SEASON_END],
                modifier = Modifier.weight(1f),
            )
        }
        val bothOrNeither = state.problems[AssetField.SEASON]
        if (bothOrNeither != null) {
            Text(
                text = bothOrNeither,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * The plain outlined field every section is made of, on the 6dp control corner (D12 §7). A
 * [problem] both reddens it and replaces the hint, so the one line under a field is always the
 * most urgent thing that field has to say.
 */
@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    problem: String? = null,
    hint: String? = null,
    minLines: Int = 1,
    mono: Boolean = false,
    numeric: Boolean = false,
    placeholder: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val supporting = problem ?: hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = if (placeholder == null) null else { { Text(placeholder) } },
        isError = problem != null,
        supportingText = if (supporting == null) null else { { Text(supporting) } },
        trailingIcon = trailingIcon,
        singleLine = minLines == 1,
        minLines = minLines,
        textStyle = if (mono) MonoText else LocalTextStyle.current,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = KeyboardType.Decimal)
        } else {
            KeyboardOptions.Default
        },
        shape = ControlShape,
        modifier = modifier,
    )
}

/**
 * Category is free text with the catalog of spec §8 behind it: an editable field whose menu
 * narrows by prefix as you type, so typing something the catalog never heard of is no harder
 * than picking a suggestion. Picking one is what the creation-time template hint listens to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryField(value: String, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = CategorySuggestions.all.filter {
        value.isBlank() || it.label.startsWith(value.trim(), ignoreCase = true)
    }
    val open = expanded && matches.isNotEmpty()
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { typed -> onValueChange(typed); expanded = true },
            label = { Text("Category") },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            shape = ControlShape,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { expanded = false }) {
            matches.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion.label) },
                    onClick = { onValueChange(suggestion.label); expanded = false },
                )
            }
        }
    }
}

/** A read-only field over a fixed list — "Part of", whose first row is always "None" (spec §5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    choices: List<ParentChoice>,
    selected: String?,
    onSelect: (String?) -> Unit,
    problem: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = choices.firstOrNull { it.id == selected }?.label ?: NO_PARENT
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = problem != null,
            supportingText = if (problem == null) null else { { Text(problem) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ControlShape,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = { onSelect(choice.id); expanded = false },
                )
            }
        }
    }
}

/**
 * An ISO date: typed, or picked from a calendar that writes the same `YYYY-MM-DD` text. Internal
 * rather than private because the retirement dialog of spec §7 asks for a date the same way, and
 * "how this app asks for a day" should have one owner.
 */
@Composable
internal fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    problem: String? = null,
) {
    var picking by remember { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        problem = problem,
        placeholder = "YYYY-MM-DD",
        mono = true,
        trailingIcon = {
            IconButton(onClick = { picking = true }) {
                Icon(ServiceTagIcons.CalendarMonth, contentDescription = "Pick $label")
            }
        },
    )
    if (picking) {
        CalendarDialog(
            initial = value,
            onDismiss = { picking = false },
            onPicked = { date -> onValueChange(date.toString()); picking = false },
        )
    }
}

/** A `MM-DD` boundary: the same calendar, with the year it hands back thrown away (spec §6). */
@Composable
private fun MonthDayField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    problem: String? = null,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    var picking by remember { mutableStateOf(false) }
    FormField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        problem = problem,
        placeholder = "MM-DD",
        mono = true,
        modifier = modifier,
        trailingIcon = {
            IconButton(onClick = { picking = true }) {
                Icon(ServiceTagIcons.CalendarMonth, contentDescription = "Pick $label")
            }
        },
    )
    if (picking) {
        CalendarDialog(
            initial = "",
            onDismiss = { picking = false },
            onPicked = { date ->
                onValueChange(String.format(Locale.US, "%02d-%02d", date.monthValue, date.dayOfMonth))
                picking = false
            },
        )
    }
}

/**
 * The Material date picker in a dialog, in UTC both ways: the picker speaks epoch millis and the
 * app stores calendar dates, so a fixed offset is what keeps the day the user tapped the day that
 * gets written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDialog(initial: String, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val start = runCatching { LocalDate.parse(initial) }.getOrNull()
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = start?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = picker.selectedDateMillis
                    if (millis != null) {
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = picker)
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
