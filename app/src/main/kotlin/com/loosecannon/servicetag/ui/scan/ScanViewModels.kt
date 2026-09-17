package com.loosecannon.servicetag.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.ExternalLink
import com.loosecannon.servicetag.core.model.LinkId
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagBinding
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.LinkRepository
import com.loosecannon.servicetag.core.usecase.BindTag
import com.loosecannon.servicetag.core.usecase.OpenLink
import com.loosecannon.servicetag.core.usecase.Resolution
import com.loosecannon.servicetag.core.usecase.ResolveTag
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.nav.Route
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long the repository flows stay hot after the last collector leaves (a rotation, typically). */
private const val SUBSCRIPTION_GRACE_MS = 5_000L

/**
 * The one mapping from a resolved scan to the route that shows it, shared by the foreground
 * scanner and the background dispatch trampoline so the two never drift apart in wording.
 *
 * `LaunchLink` has no route on purpose: a link tag launches its note and shows no sheet (R-7).
 */
internal fun Resolution.asTagResult(): Route.TagResult = when (this) {
    is Resolution.OpenAsset -> Route.TagResult(TagResultWire.wordFor(tag.payloadFormat), tag.payloadKey)
    is Resolution.Unbound -> Route.TagResult(TagResultWire.wordFor(tag.payloadFormat), tag.payloadKey)
    is Resolution.Revoked -> Route.TagResult(TagResultWire.wordFor(tag.payloadFormat), tag.payloadKey)
    is Resolution.UnknownV1 -> Route.TagResult(TagResultWire.wordFor(PayloadFormat.V1), tagId.value)
    is Resolution.NeedsNewerApp ->
        Route.TagResult(TagResultWire.FORMAT_NONE, "written by a newer ServiceTag (payload format $version)")
    is Resolution.NotOurs -> Route.TagResult(TagResultWire.FORMAT_NONE, describe(payload))
    is Resolution.LaunchLink -> error("a link tag launches its note; it has no sheet (R-7)")
}

private fun describe(p: TagPayload): String = when (p) {
    TagPayload.Empty -> "empty tag"
    is TagPayload.Foreign -> "not a ServiceTag tag: ${p.description}"
    is TagPayload.Malformed -> "unreadable ServiceTag record: ${p.reason}"
    else -> "not a ServiceTag tag"
}

/** The scanner is either waiting, reading a tag it has just felt, or explaining a read that failed. */
data class ScanState(val reading: Boolean = false, val problem: String? = null)

/** What a finished scan asks the screen to do; the screen owns the Activity, the ViewModel does not. */
sealed interface ScanEvent {
    data class Show(val route: Route.TagResult) : ScanEvent

    /** A link tag: launch the note straight away, with no sheet in between (R-7). */
    data class Launch(val uri: String) : ScanEvent
}

/**
 * Foreground scanning. Reader mode hands tags in from a binder thread; the tag read itself goes to
 * [ioDispatcher] and everything the screen sees comes back as state or a one-shot event.
 */
class ScanViewModel(
    private val resolveTag: ResolveTag,
    private val openLink: OpenLink,
    private val io: TagIo,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    constructor(graph: AppGraph) : this(graph.resolveTag, graph.openLink, RealTagIo(graph.ndefCodec))

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScanEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<ScanEvent> = _events.asSharedFlow()

    /** Single-flight: a tag left in the field is re-delivered, and one scan is one answer. */
    @Volatile private var busy = false

    fun onTag(tag: TagHandle) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            _state.update { it.copy(reading = true, problem = null) }
            try {
                val payload = withContext(ioDispatcher) { io.inspect(tag) }?.existing
                    ?: TagPayload.Malformed("this tag does not support NDEF")
                when (val resolution = resolveTag.run(payload)) {
                    is Resolution.LaunchLink -> launch(resolution.link.id)
                    else -> _events.tryEmit(ScanEvent.Show(resolution.asTagResult()))
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(problem = "Couldn't read that tag (${e.javaClass.simpleName}). Hold it still and try again.")
                }
            } finally {
                _state.update { it.copy(reading = false) }
                busy = false
            }
        }
    }

    private suspend fun launch(id: LinkId) {
        when (val outcome = openLink.run(id)) {
            is OpenLink.Outcome.Launch -> _events.tryEmit(ScanEvent.Launch(outcome.uri))
            is OpenLink.Outcome.Refused ->
                _events.tryEmit(ScanEvent.Show(Route.TagResult(TagResultWire.FORMAT_NONE, "link refused: ${outcome.reason}")))
            is OpenLink.Outcome.Missing ->
                _events.tryEmit(ScanEvent.Show(Route.TagResult(TagResultWire.FORMAT_NONE, "the link this tag pointed at no longer exists")))
        }
    }
}

/** One of the six D12 §11 sheets, already decided. The screen draws it and never resolves anything. */
sealed interface TagResult {
    data object Loading : TagResult

    /** Known and bound: the sheet says so and the screen moves on without a tap (G1 §1.4). */
    data class OpensAsset(val tag: TagBinding, val asset: Asset) : TagResult

    /** A link tag reached through a deep link rather than a scan; it still launches, no sheet. */
    data class LaunchesLink(val uri: String) : TagResult

    data class Unregistered(val tag: TagBinding) : TagResult
    data class Revoked(val tag: TagBinding) : TagResult

    /** A v1 tag this phone has no row for — another phone's tag, or one from before a wipe. */
    data class NotInRecords(val tagId: String) : TagResult

    /** Not ours at all; [reason] is prose, never an id. An offer, never an error. */
    data class NotOurs(val reason: String) : TagResult
}

/** Everything the bind picker can point a tag at. "New asset…" is the screen's own row. */
data class BindTargets(val assets: List<Asset> = emptyList(), val links: List<ExternalLink> = emptyList())

/** Where the sheet goes once a bind is done: onto the asset it bound, or simply away. */
sealed interface TagResultEvent {
    data class OpenAsset(val id: String) : TagResultEvent
    data object Dismiss : TagResultEvent
    data class Failed(val message: String) : TagResultEvent
}

/**
 * Re-resolves the (format, key) pair the trampoline or the scanner handed over. It resolves again
 * rather than trusting a carried object: the pair survives process death and a backup import, an
 * in-memory `Resolution` does not.
 */
class TagResultViewModel(
    private val resolveTag: ResolveTag,
    private val bindTag: BindTag,
    private val openLink: OpenLink,
    assets: AssetRepository,
    links: LinkRepository,
    private val format: String,
    private val key: String,
) : ViewModel() {

    constructor(graph: AppGraph, format: String, key: String) :
        this(graph.resolveTag, graph.bindTag, graph.openLink, graph.assets, graph.links, format, key)

    private val _state = MutableStateFlow<TagResult>(TagResult.Loading)
    val state: StateFlow<TagResult> = _state.asStateFlow()

    private val _events = MutableSharedFlow<TagResultEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<TagResultEvent> = _events.asSharedFlow()

    val targets: StateFlow<BindTargets> =
        combine(assets.observeAll(), links.observeAll()) { assetRows, linkRows ->
            BindTargets(assets = assetRows, links = linkRows)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), BindTargets())

    init {
        viewModelScope.launch { resolve() }
    }

    private suspend fun resolve() {
        val payload = TagResultWire.payloadOf(format, key)
        if (payload == null) {
            // The key is the prose reason the tag could not be used, not an id.
            _state.value = TagResult.NotOurs(key)
            return
        }
        _state.value = when (val resolution = runCatching { resolveTag.run(payload) }.getOrNull()) {
            is Resolution.OpenAsset -> TagResult.OpensAsset(resolution.tag, resolution.asset)
            is Resolution.LaunchLink -> launched(resolution.link)
            is Resolution.Unbound -> TagResult.Unregistered(resolution.tag)
            is Resolution.Revoked -> TagResult.Revoked(resolution.tag)
            is Resolution.UnknownV1 -> TagResult.NotInRecords(resolution.tagId.value)
            is Resolution.NeedsNewerApp ->
                TagResult.NotOurs("written by a newer ServiceTag (payload format ${resolution.version})")
            is Resolution.NotOurs -> TagResult.NotOurs(describe(resolution.payload))
            null -> TagResult.NotOurs("this tag could not be resolved")
        }
    }

    private suspend fun launched(link: ExternalLink): TagResult =
        when (val outcome = openLink.run(link.id)) {
            is OpenLink.Outcome.Launch -> TagResult.LaunchesLink(outcome.uri)
            is OpenLink.Outcome.Refused -> TagResult.NotOurs("link refused: ${outcome.reason}")
            is OpenLink.Outcome.Missing -> TagResult.NotOurs("the link this tag pointed at no longer exists")
        }

    /**
     * Binds this tag to [target]. A v1 key that is not a canonical UUID is refused by `BindTag`,
     * and the refusal is shown, not swallowed.
     */
    fun bind(target: TagTarget) {
        viewModelScope.launch {
            val done = runCatching { bindTag.run(PayloadFormat.valueOf(format), key, target) }
            done.fold(
                onSuccess = {
                    _events.tryEmit(
                        when (target) {
                            is TagTarget.AssetTarget -> TagResultEvent.OpenAsset(target.assetId.value)
                            else -> TagResultEvent.Dismiss
                        },
                    )
                },
                onFailure = { _events.tryEmit(TagResultEvent.Failed("Couldn't bind this tag: ${it.message}")) },
            )
        }
    }
}

/**
 * The write screen's state, and the owner of the controller that produces it. The controller is
 * built from `viewModelScope`, so a tap that started survives a rotation and dies with the screen.
 */
class WriteTagViewModel(
    assets: AssetRepository,
    links: LinkRepository,
    target: TagTarget,
    label: String?,
    controllerFor: (CoroutineScope) -> TagWriteController,
) : ViewModel() {

    constructor(graph: AppGraph, target: TagTarget, label: String?) : this(
        graph.assets,
        graph.links,
        target,
        label,
        { scope -> TagWriteController(graph, RealTagIo(graph.ndefCodec), target, label, scope) },
    )

    private val controller: TagWriteController = controllerFor(viewModelScope)

    /**
     * What this tag will identify, in the owner's words rather than in ids: the overwrite question
     * reads "will make the tag identify Hot tub", and a UUID would not tell anyone anything.
     */
    private val _targetName = MutableStateFlow(label ?: unnamed(target))
    val targetName: StateFlow<String> = _targetName.asStateFlow()

    init {
        viewModelScope.launch {
            val named = when (target) {
                is TagTarget.AssetTarget -> assets.get(target.assetId)?.name
                is TagTarget.LinkTarget -> links.get(target.linkId)?.label
                TagTarget.None -> null
            }
            if (named != null) _targetName.value = named
        }
    }

    val state: StateFlow<WriteState> = controller.state
    val lock: StateFlow<Boolean> = controller.lock

    fun onTag(tag: TagHandle) = controller.onTag(tag)
    fun confirmOverwrite() = controller.confirmOverwrite()
    fun keepIt() = controller.keepIt()
    fun setLock(value: Boolean) = controller.setLock(value)

    /** The screen is going away for good: a row with no verified write leaves nothing behind. */
    override fun onCleared() {
        controller.abandonIfUnwritten()
        super.onCleared()
    }
}

/** The tag row the sheet is talking about, as the plate spells identity (G1 §3 correction a). */
fun TagBinding.identityLine(): String =
    "${id.value.take(8)} · ${payloadFormat.name.lowercase()}"

/** The same line for a tag that has no row yet. */
fun identityLine(key: String): String =
    "${key.take(8)} · ${PayloadFormat.V1.name.lowercase()}"

/** The honest name for a target that has none: a spare tag is bound on its first scan. */
private fun unnamed(target: TagTarget): String =
    if (target == TagTarget.None) "nothing yet — a spare tag, bound on its first scan" else "this target"
