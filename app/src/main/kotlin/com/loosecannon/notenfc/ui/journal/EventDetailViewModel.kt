package com.loosecannon.notenfc.ui.journal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.usecase.DeleteEvent
import com.loosecannon.notenfc.di.AppGraph
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
                EventDetailState(
                    event = it,
                    definitions = definitions.forAsset(it.assetId).associateBy(MeasurementDefinition::id),
                    assetName = assets.get(it.assetId)?.name.orEmpty(),
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
