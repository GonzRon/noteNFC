package com.loosecannon.notenfc.ui.asset

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.usecase.ArchiveAsset
import com.loosecannon.notenfc.core.usecase.AssetNameRequired
import com.loosecannon.notenfc.core.usecase.CreateAsset
import com.loosecannon.notenfc.core.usecase.UpdateAsset
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The three asset ViewModels. Each takes the `AppGraph` members it actually uses — the secondary
 * constructor is what the Compose entry calls, the primary one is what a test builds on a
 * Room-backed fake graph. No screen ever reaches past its state and its callbacks.
 */

/** How long the repository flows stay hot after the last collector leaves (a rotation, typically). */
private const val SUBSCRIPTION_GRACE_MS = 5_000L

data class AssetsState(
    val items: List<Asset> = emptyList(),
    val showArchived: Boolean = false,
    /** How many rows the chip is hiding, so an empty list can say why it is empty. */
    val archivedCount: Int = 0,
)

/**
 * The list. `observeAll` already orders active rows before archived ones and then by name, so the
 * only decision here is whether the archived tail is shown at all — archive is not delete (R-9),
 * but a list that keeps showing everything you archived is no better than never archiving.
 */
class AssetsViewModel(assets: AssetRepository) : ViewModel() {

    constructor(graph: AppGraph) : this(graph.assets)

    private val showArchived = MutableStateFlow(false)

    val state: StateFlow<AssetsState> = combine(assets.observeAll(), showArchived) { rows, archived ->
        AssetsState(
            items = if (archived) rows else rows.filter { it.status == AssetStatus.ACTIVE },
            showArchived = archived,
            archivedCount = rows.count { it.status != AssetStatus.ACTIVE },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), AssetsState())

    fun toggleArchived() = showArchived.update { !it }
}

/** Everything the detail screen draws about one asset, or null while it is still unknown. */
data class AssetDetailState(
    val asset: Asset,
    val tags: List<TagBinding> = emptyList(),
    val links: List<ExternalLink> = emptyList(),
)

/**
 * One asset and the rows that point at it. [missing] is separate from [state] because "not loaded
 * yet" and "gone" both read as a null state, and only the second one should send the user back —
 * a deep link or a restored back stack can name an asset a backup import has since replaced.
 */
class AssetDetailViewModel(
    assets: AssetRepository,
    tags: TagRepository,
    links: LinkRepository,
    private val archiveAsset: ArchiveAsset,
    private val id: AssetId,
) : ViewModel() {

    constructor(graph: AppGraph, id: String) :
        this(graph.assets, graph.tags, graph.links, graph.archiveAsset, AssetId(id))

    private val asset = assets.observeAll().map { rows -> rows.firstOrNull { it.id == id } }

    val state: StateFlow<AssetDetailState?> =
        combine(asset, tags.observeForAsset(id), links.observeForAsset(id)) { row, tagRows, linkRows ->
            row?.let { AssetDetailState(asset = it, tags = tagRows, links = linkRows) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), null)

    val missing: StateFlow<Boolean> = asset
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), false)

    fun archive() {
        viewModelScope.launch { archiveAsset.run(id) }
    }

    fun unarchive() {
        viewModelScope.launch { archiveAsset.unarchive(id) }
    }
}

/**
 * The edit form. [nameError] is only ever raised by a refused save, so the field is not red before
 * the user has tried anything, and typing in it clears the mark.
 */
data class AssetEditState(
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val notes: String = "",
    val editing: Boolean = false,
    val nameError: Boolean = false,
    val saving: Boolean = false,
)

/** Create ([id] null) or edit one asset. The blank-name rule lives in the use cases, not here. */
class AssetEditViewModel(
    private val assets: AssetRepository,
    private val createAsset: CreateAsset,
    private val updateAsset: UpdateAsset,
    private val id: AssetId?,
) : ViewModel() {

    constructor(graph: AppGraph, id: String?) :
        this(graph.assets, graph.createAsset, graph.updateAsset, id?.let(::AssetId))

    private val _state = MutableStateFlow(AssetEditState(editing = id != null))
    val state: StateFlow<AssetEditState> = _state.asStateFlow()

    /**
     * One shot per successful save. A buffer of one and no replay: the screen that started the
     * save is told where to go, and a screen that comes back later is not told again.
     */
    private val _saved = MutableSharedFlow<AssetId>(replay = 0, extraBufferCapacity = 1)
    val saved: SharedFlow<AssetId> = _saved.asSharedFlow()

    init {
        if (id != null) {
            viewModelScope.launch {
                val row = assets.get(id) ?: return@launch
                _state.update {
                    it.copy(
                        name = row.name,
                        category = row.category,
                        description = row.description,
                        notes = row.notes,
                    )
                }
            }
        }
    }

    fun onName(value: String) = _state.update { it.copy(name = value, nameError = false) }
    fun onCategory(value: String) = _state.update { it.copy(category = value) }
    fun onDescription(value: String) = _state.update { it.copy(description = value) }
    fun onNotes(value: String) = _state.update { it.copy(notes = value) }

    /**
     * Saves, and then names the asset the screen should show, once, on [saved]. A failure leaves
     * the form exactly as the user typed it; only [AssetNameRequired] marks the field, because no
     * other failure is something a different name would fix.
     *
     * The write runs in `viewModelScope`, not in the screen's composition scope: a rotation
     * halfway through must not abandon it with `saving` stuck true. The guard is set before the
     * first suspension, so two taps inside one frame create one asset, not two.
     */
    fun save() {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val form = _state.value
            val result = runCatching {
                if (id == null) {
                    createAsset.run(form.name, form.category, form.description, form.notes).id
                } else {
                    updateAsset.run(id, form.name, form.category, form.description, form.notes).id
                }
            }
            _state.update {
                it.copy(saving = false, nameError = result.exceptionOrNull() is AssetNameRequired)
            }
            result.getOrNull()?.let { _saved.tryEmit(it) }
        }
    }
}
