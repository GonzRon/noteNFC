package com.loosecannon.servicetag.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.servicetag.core.journal.Derived
import com.loosecannon.servicetag.core.journal.Reading
import com.loosecannon.servicetag.core.journal.classify
import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.model.DefinitionKind
import com.loosecannon.servicetag.core.model.EventId
import com.loosecannon.servicetag.core.model.MeasurementDefinition
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.DefinitionRepository
import com.loosecannon.servicetag.core.ports.EventRepository
import com.loosecannon.servicetag.core.usecase.DeleteEvent
import com.loosecannon.servicetag.di.AppGraph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the repository flow stays hot after the last collector leaves (a rotation, typically). */
private const val SUBSCRIPTION_GRACE_MS = 5_000L

/**
 * One stored event, read-only. The definitions travel with it because a measurement only means
 * something next to the definition that names and bounds it, and they are looked up by the id the
 * measurement carries rather than by position.
 */
data class EventDetailState(
    val event: AssetEvent,
    val definitions: Map<DefinitionId, MeasurementDefinition>,
    val assetName: String,
    /**
     * What the asset derives from this entry's own readings (spec §5), computed on every emission
     * and stored nowhere. Empty when the asset has no unarchived DERIVED definition, or when the
     * entry carried no readings at all; a row that cannot be computed is present and reads "—".
     */
    val derived: List<Reading> = emptyList(),
)

/**
 * The read side of the journal. [missing] is separate from [state] because "not loaded yet" and
 * "gone" both read as a null state, and only the second sends the user back — a restored back
 * stack or a replacing import can name an event that is no longer there (the 1C pattern).
 */
class EventDetailViewModel(
    events: EventRepository,
    definitions: DefinitionRepository,
    assets: AssetRepository,
    private val deleteEvent: DeleteEvent,
    private val id: EventId,
) : ViewModel() {

    constructor(graph: AppGraph, eventId: String) :
        this(graph.events, graph.definitions, graph.assets, graph.deleteEvent, EventId(eventId))

    private val row = events.observe(id)

    val state: StateFlow<EventDetailState?> = row
        .map { event ->
            event?.let {
                val byId = definitions.forAsset(it.assetId).associateBy(MeasurementDefinition::id)
                EventDetailState(
                    event = it,
                    definitions = byId,
                    assetName = assets.get(it.assetId)?.name.orEmpty(),
                    derived = derivedFor(it, byId),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), null)

    val missing: StateFlow<Boolean> = row
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), false)

    /** One shot, so the screen pops on the delete it asked for rather than on the row vanishing. */
    private val _deleted = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    /**
     * An event is a record of something that happened, so this is the one destructive action in
     * the journal and the screen asks first. Its children go with it (CASCADE).
     */
    fun delete() {
        viewModelScope.launch {
            deleteEvent.run(id)
            _deleted.tryEmit(Unit)
        }
    }
}

/**
 * The asset's unarchived DERIVED definitions against this one event, same-event semantics and all
 * (spec §5). An entry that recorded no readings gets none of them: a row that could never have a
 * value on an entry with nothing to derive from is noise, not information.
 */
private fun derivedFor(
    event: AssetEvent,
    definitions: Map<DefinitionId, MeasurementDefinition>,
): List<Reading> {
    if (event.measurements.isEmpty()) return emptyList()
    return definitions.values
        .filter { it.kind == DefinitionKind.DERIVED && it.archivedAt == null }
        .sortedBy { it.sortOrder }
        .map { definition ->
            val value = Derived.compute(definition, event, definitions)
            Reading(
                definition = definition,
                measurement = null,
                occurredOn = event.occurredOn,
                occurredTime = event.occurredTime,
                state = value?.let { classify(it, definition.rangeLow, definition.rangeHigh) },
                derivedValue = value,
            )
        }
}
