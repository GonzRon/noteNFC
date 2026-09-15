package com.loosecannon.notenfc.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.core.journal.RangeState
import com.loosecannon.notenfc.core.journal.classify
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.Measurement
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.ProfileConsumable
import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.model.ValueType
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.usecase.ConsumableInput
import com.loosecannon.notenfc.core.usecase.EventCommand
import com.loosecannon.notenfc.core.usecase.EventValidation
import com.loosecannon.notenfc.core.usecase.FieldProblem
import com.loosecannon.notenfc.core.usecase.LogEvent
import com.loosecannon.notenfc.core.usecase.UpdateEvent
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The entry route's state: the field test sheet of G1 §1.3, one row per profile field, plus the
 * materials that went in and one free note. The form is driven entirely by the profile's fields
 * and each definition's [ValueType] — nothing here knows what a hot tub is.
 */

/** One value row. [problem] is only ever set by a refused save, so nothing is red before a try. */
data class FieldRow(
    val definition: MeasurementDefinition,
    val required: Boolean,
    val text: String,
    val problem: FieldProblem?,
) {
    /** The badge while typing: a number reads against its target the moment it parses. */
    val liveState: RangeState?
        get() = if (definition.valueType == ValueType.NUMBER) {
            text.trim().toDoubleOrNull()?.let { classify(it, definition.rangeLow, definition.rangeHigh) }
        } else {
            null
        }
}

/** One material line as typed. Quantity stays text until the use case parses it. */
data class ConsumableRow(
    val name: String,
    val quantity: String,
    val unit: String,
    val problem: Boolean = false,
)

data class EventEntryState(
    val assetName: String = "",
    val profileName: String = "",
    val title: String = "",
    val occurredOn: String,
    val occurredTime: String?,
    val fields: List<FieldRow> = emptyList(),
    val suggestions: List<ProfileConsumable> = emptyList(),
    val consumables: List<ConsumableRow> = emptyList(),
    val notes: String = "",
    val editing: Boolean = false,
    val saving: Boolean = false,
    /** The one line the screen says out loud when a save is refused; null while nothing is wrong. */
    val firstProblem: String? = null,
    val loaded: Boolean = false,
)

/**
 * New entry ([eventId] null) or edit of a stored one. A new entry takes its rows from the profile;
 * an edit takes them from the event's own profile, falling back to every unarchived definition of
 * the asset as optional rows when the event was logged without one.
 *
 * Validation belongs to the use cases: [save] sends what was typed and maps the [FieldProblem]s
 * that come back onto the rows they belong to. The form never second-guesses the domain.
 */
class EventEntryViewModel(
    private val assets: AssetRepository,
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val logEvent: LogEvent,
    private val updateEvent: UpdateEvent,
    private val clock: Clock,
    private val assetId: AssetId,
    private val profileId: ProfileId?,
    private val eventId: EventId?,
) : ViewModel() {

    constructor(graph: AppGraph, assetId: String, profileId: String?, eventId: String?) : this(
        graph.assets, graph.definitions, graph.profiles, graph.events,
        graph.logEvent, graph.updateEvent, graph.clock,
        AssetId(assetId), profileId?.let(::ProfileId), eventId?.let(::EventId),
    )

    /** The zone the entry is being made in; stored on the event as `tzId` for the audit trail. */
    private val zone: ZoneId = ZoneId.systemDefault()

    /** Set once the event is loaded, so an edit's save carries the kind it was logged with. */
    private var kind: EventKind = EventKind.NOTE

    /** The profile the save names, which in edit mode is the event's own, not the route's. */
    private var commandProfileId: ProfileId? = profileId

    private val _state = MutableStateFlow(
        EventEntryState(
            occurredOn = clock.nowMillis().at(zone).toLocalDate().format(DATE),
            occurredTime = clock.nowMillis().at(zone).toLocalTime().format(TIME),
            editing = eventId != null,
        ),
    )
    val state: StateFlow<EventEntryState> = _state.asStateFlow()

    /** One shot per successful save: the screen that started it pops, a later visitor is not told. */
    private val _saved = MutableSharedFlow<EventId>(replay = 0, extraBufferCapacity = 1)
    val saved: SharedFlow<EventId> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            val assetName = assets.get(assetId)?.name.orEmpty()
            val existing = eventId?.let { events.get(it) }
            val profile = (existing?.profileId ?: profileId)?.let { profiles.get(it) }
            commandProfileId = profile?.id
            kind = existing?.kind ?: profile?.eventKind ?: EventKind.NOTE
            val fields = rows(profile, existing)
            _state.update { current ->
                current.copy(
                    assetName = assetName,
                    profileName = profile?.name.orEmpty(),
                    title = existing?.title ?: profile?.defaultTitle.orEmpty(),
                    occurredOn = existing?.occurredOn ?: current.occurredOn,
                    occurredTime = if (existing != null) existing.occurredTime else current.occurredTime,
                    fields = fields,
                    suggestions = profile?.consumables.orEmpty(),
                    consumables = existing?.consumables.orEmpty().map {
                        ConsumableRow(it.name, plain(it.quantity), it.unit)
                    },
                    notes = existing?.notes.orEmpty(),
                    loaded = true,
                )
            }
        }
    }

    /**
     * The profile's fields in `sortOrder`, or — for an event logged without a profile — every
     * unarchived definition of the asset as an optional row, so an edit can still reach a value
     * the event already carries.
     */
    private suspend fun rows(profile: EventProfile?, existing: AssetEvent?): List<FieldRow> {
        val fields: List<Pair<DefinitionId, Boolean>> = profile
            ?.fields
            ?.sortedBy { it.sortOrder }
            ?.map { it.definitionId to it.required }
            ?: definitions.forAsset(assetId)
                .filter { it.archivedAt == null }
                .sortedBy { it.sortOrder }
                .map { it.id to false }
        return fields.mapNotNull { (id, required) ->
            val definition = definitions.get(id) ?: return@mapNotNull null
            val measurement = existing?.measurements?.firstOrNull { it.definitionId == id }
            FieldRow(definition, required, measurement.asText(definition), problem = null)
        }
    }

    fun onTitle(value: String) = _state.update { it.copy(title = value, firstProblem = null) }

    fun onDate(value: String) = _state.update { it.copy(occurredOn = value, firstProblem = null) }

    fun onTime(value: String?) = _state.update { it.copy(occurredTime = value, firstProblem = null) }

    fun onNotes(value: String) = _state.update { it.copy(notes = value) }

    /** Typing in a row clears that row's mark and the line under the app bar, as 1C's name field does. */
    fun onValue(definitionId: DefinitionId, value: String) = _state.update { current ->
        current.copy(
            fields = current.fields.map { row ->
                if (row.definition.id == definitionId) row.copy(text = value, problem = null) else row
            },
            firstProblem = null,
        )
    }

    /** A suggestion is a head start, not an entry: it arrives with its unit and an open quantity. */
    fun addSuggested(suggestion: ProfileConsumable) = _state.update { current ->
        current.copy(
            consumables = current.consumables + ConsumableRow(
                name = suggestion.name,
                quantity = suggestion.defaultQuantity?.let(::plain).orEmpty(),
                unit = suggestion.unit,
            ),
            firstProblem = null,
        )
    }

    fun addBlankConsumable() = _state.update { current ->
        current.copy(consumables = current.consumables + ConsumableRow("", "", ""), firstProblem = null)
    }

    fun onConsumable(index: Int, name: String? = null, quantity: String? = null, unit: String? = null) =
        _state.update { current ->
            current.copy(
                consumables = current.consumables.mapIndexed { i, row ->
                    if (i != index) {
                        row
                    } else {
                        row.copy(
                            name = name ?: row.name,
                            quantity = quantity ?: row.quantity,
                            unit = unit ?: row.unit,
                            problem = false,
                        )
                    }
                },
                firstProblem = null,
            )
        }

    fun removeConsumable(index: Int) = _state.update { current ->
        current.copy(
            consumables = current.consumables.filterIndexed { i, _ -> i != index },
            firstProblem = null,
        )
    }

    /**
     * Logs or edits, then names the event the screen should leave for, once, on [saved]. The write
     * runs in `viewModelScope` so a rotation halfway through cannot abandon it with `saving` stuck
     * true, and the guard is set before the first suspension, so two taps in one frame log one
     * event rather than two.
     */
    fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, firstProblem = null) }
        viewModelScope.launch {
            val form = _state.value
            val submitted = form.consumables.submitted()
            val cmd = EventCommand(
                assetId = assetId,
                profileId = commandProfileId,
                kind = kind,
                title = form.title,
                occurredOn = form.occurredOn,
                occurredTime = form.occurredTime?.takeIf { it.isNotBlank() },
                tzId = zone.id,
                notes = form.notes,
                values = form.fields
                    .filter { it.text.isNotBlank() }
                    .associate { it.definition.id to it.text },
                consumables = submitted.map { it.second },
            )
            val result = runCatching { if (eventId == null) logEvent.run(cmd) else updateEvent.run(eventId, cmd) }

            when (val failure = result.exceptionOrNull()) {
                null -> {
                    _state.update { it.copy(saving = false) }
                    result.getOrNull()?.let { _saved.tryEmit(it.id) }
                }
                is EventValidation -> markProblems(failure, submitted.map { it.first })
                else -> _state.update { it.copy(saving = false, firstProblem = "Could not save this entry.") }
            }
        }
    }

    /** Puts every [FieldProblem] back on the row it belongs to and names the first one out loud. */
    private fun markProblems(failure: EventValidation, submittedRows: List<Int>) {
        val byDefinition = failure.problems.mapNotNull { p -> p.definitionId?.let { it to p } }.toMap()
        val badConsumables = failure.problems
            .filterIsInstance<FieldProblem.BadConsumable>()
            .mapNotNull { submittedRows.getOrNull(it.index) }
            .toSet()
        _state.update { current ->
            val fields = current.fields.map { it.copy(problem = byDefinition[it.definition.id]) }
            current.copy(
                saving = false,
                fields = fields,
                consumables = current.consumables.mapIndexed { i, row -> row.copy(problem = i in badConsumables) },
                firstProblem = failure.problems.firstProblemText(fields),
            )
        }
    }

    /**
     * A row that has not been touched at all is not a material the user forgot to fill in — it is
     * one they added and changed their mind about, so it never reaches validation. The row's own
     * index travels with it, so [FieldProblem.BadConsumable] still marks the right line.
     */
    private fun List<ConsumableRow>.submitted(): List<Pair<Int, ConsumableInput>> = withIndex()
        .filterNot { (_, row) -> row.name.isBlank() && row.quantity.isBlank() && row.unit.isBlank() }
        .map { (index, row) -> index to ConsumableInput(row.name, row.quantity, row.unit) }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun Long.at(zone: ZoneId) = Instant.ofEpochMilli(this).atZone(zone)

/**
 * The problems that are not about a single row name themselves; anything else is a row, and the
 * first of those is named by its definition's label ("pH is required").
 */
private fun List<FieldProblem>.firstProblemText(fields: List<FieldRow>): String? {
    firstNotNullOfOrNull { problem ->
        when (problem) {
            is FieldProblem.BadDate -> "Enter a date as YYYY-MM-DD"
            is FieldProblem.BadTime -> "Enter a time as HH:MM"
            FieldProblem.TitleRequired -> "Give the entry a title"
            is FieldProblem.BadConsumable -> "Check material ${problem.index + 1}"
            else -> null
        }
    }?.let { return it }

    val row = fields.firstOrNull { it.problem != null } ?: return null
    return when (row.problem) {
        is FieldProblem.Required -> "${row.definition.label} is required"
        is FieldProblem.NotANumber -> "${row.definition.label} is not a number"
        else -> null
    }
}

/**
 * A stored value back as the text that produced it — the entry field holds what was typed, not a
 * formatted reading, so an edit that changes nothing else re-saves the same number.
 */
private fun Measurement?.asText(definition: MeasurementDefinition): String {
    val m = this ?: return ""
    return when (definition.valueType) {
        ValueType.TEXT -> m.valueText.orEmpty()
        ValueType.BOOLEAN -> if (m.valueNum == 1.0) "1" else "0"
        ValueType.NUMBER -> m.valueNum?.let { value ->
            if (definition.decimals == 0) plain(value) else value.toString()
        }.orEmpty()
    }
}

/** A quantity with only the decimals it needs: 1.0 typed back as "1", 0.5 as "0.5". */
private fun plain(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) whole.toString() else value.toString()
}
