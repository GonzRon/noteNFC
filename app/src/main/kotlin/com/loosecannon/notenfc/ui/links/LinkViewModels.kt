package com.loosecannon.notenfc.ui.links

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.usecase.DeleteLink
import com.loosecannon.notenfc.core.usecase.LinkStillBound
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.di.AppGraph
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How long the repository flows stay hot after the last collector leaves (a rotation, typically). */
private const val SUBSCRIPTION_GRACE_MS = 5_000L

/** Every saved link, ordered by the repository (by label); the screen only draws them. */
class LinksViewModel(links: LinkRepository) : ViewModel() {

    constructor(graph: AppGraph) : this(graph.links)

    val state: StateFlow<List<ExternalLink>> = links.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), emptyList())
}

/** One link and the tags that point at it — the tags are why a delete can be refused. */
data class LinkDetailState(val link: ExternalLink, val tags: List<TagBinding> = emptyList())

/** What the detail screen has to do outside its own state: launch, leave, or explain a refusal. */
sealed interface LinkEvent {
    data class Launch(val uri: String) : LinkEvent
    data object Deleted : LinkEvent
    data class Refused(val message: String) : LinkEvent
}

/**
 * One saved link. Opening goes through `OpenLink`, which re-checks the stored URI against the
 * policy before anything is launched; deleting goes through `DeleteLink`, which refuses while a
 * physical tag still points here, because that tag cannot be edited from a phone screen.
 */
class LinkDetailViewModel(
    links: LinkRepository,
    tags: TagRepository,
    private val openLink: OpenLink,
    private val deleteLink: DeleteLink,
    private val id: LinkId,
) : ViewModel() {

    constructor(graph: AppGraph, id: String) :
        this(graph.links, graph.tags, graph.openLink, graph.deleteLink, LinkId(id))

    private val link = links.observeAll().map { rows -> rows.firstOrNull { it.id == id } }

    val state: StateFlow<LinkDetailState?> = combine(link, tags.observeForLink(id)) { row, tagRows ->
        row?.let { LinkDetailState(link = it, tags = tagRows) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), null)

    /** "Not loaded yet" and "gone" both read as a null state; only the second one sends you back. */
    val missing: StateFlow<Boolean> = link
        .map { it == null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), false)

    private val _events = MutableSharedFlow<LinkEvent>(replay = 0, extraBufferCapacity = 1)
    val events: SharedFlow<LinkEvent> = _events.asSharedFlow()

    fun open() {
        viewModelScope.launch {
            when (val outcome = openLink.run(id)) {
                is OpenLink.Outcome.Launch -> _events.tryEmit(LinkEvent.Launch(outcome.uri))
                is OpenLink.Outcome.Refused -> _events.tryEmit(LinkEvent.Refused("This link can't be opened: ${outcome.reason}."))
                is OpenLink.Outcome.Missing -> _events.tryEmit(LinkEvent.Refused("This link no longer exists."))
            }
        }
    }

    fun delete() {
        viewModelScope.launch {
            val done = runCatching { deleteLink.run(id) }
            done.fold(
                onSuccess = { _events.tryEmit(LinkEvent.Deleted) },
                onFailure = { failure ->
                    val bound = failure as? LinkStillBound
                    _events.tryEmit(
                        LinkEvent.Refused(
                            if (bound != null) {
                                "${bound.tagCount} tag${if (bound.tagCount == 1) "" else "s"} still point at this link. " +
                                    "Rewrite or retire them first."
                            } else {
                                "Couldn't delete this link: ${failure.message}"
                            },
                        ),
                    )
                },
            )
        }
    }
}

/** A link's host, which is what tells two notes in the same app apart at a glance. */
internal fun ExternalLink.host(): String = uri.substringAfter("://").substringBefore('/').ifBlank { uri }

/** "Joplin note", "Web page" — the kind in the owner's words, not the enum's. */
internal fun LinkKind.label(): String = when (this) {
    LinkKind.JOPLIN -> "Joplin note"
    LinkKind.OBSIDIAN -> "Obsidian note"
    LinkKind.LOGSEQ -> "Logseq page"
    LinkKind.WEB -> "Web page"
    LinkKind.OTHER -> "Other app"
}

internal fun ExternalLink.kindLabel(): String = kind.label()
