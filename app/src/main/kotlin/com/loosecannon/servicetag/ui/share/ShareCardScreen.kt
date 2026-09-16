package com.loosecannon.servicetag.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.loosecannon.servicetag.core.links.LinkCheck
import com.loosecannon.servicetag.core.links.LinkLaunchPolicy
import com.loosecannon.servicetag.core.model.LinkKind
import com.loosecannon.servicetag.core.usecase.SaveLink
import com.loosecannon.servicetag.di.AppGraph
import com.loosecannon.servicetag.ui.components.QuietLine
import com.loosecannon.servicetag.ui.links.label
import com.loosecannon.servicetag.ui.nav.Route
import com.loosecannon.servicetag.ui.theme.ControlShape
import com.loosecannon.servicetag.ui.theme.MonoText
import com.loosecannon.servicetag.ui.theme.PlateShape
import com.loosecannon.servicetag.ui.theme.SheetSentence
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything "share a note link to noteNFC" can be (D12 §9). One state at a time, no dialogs. */
sealed interface ShareState {
    /** The shared text held no `scheme://…` token, or the policy will not launch the one it held. */
    data class Nothing(val message: String) : ShareState

    /** A scheme outside the allowlist: launchable only after one explicit confirmation. */
    data class NeedsConfirmation(val scheme: String, val uri: String) : ShareState

    data class Card(val uri: String, val kind: String, val label: String?) : ShareState
    /** Saved and done; the id is kept so a later step could name the row it made. */
    data class Saved(val linkId: String) : ShareState

    /** Saved, and now writing it to a tag — the write screen runs inside this same task. */
    data class Writing(val route: Route.WriteTag) : ShareState
}

/**
 * The share flow's decisions. The URI is extracted once and checked by `LinkLaunchPolicy`; both
 * exits ("Write to a new tag" and "Keep as link") save through `SaveLink`, which runs the same
 * gate again — a shared URI is never trusted because it arrived through a share sheet.
 */
class ShareViewModel(
    private val saveLink: SaveLink,
    sharedText: String?,
) : ViewModel() {

    constructor(graph: AppGraph, sharedText: CharSequence?) : this(graph.saveLink, sharedText?.toString())

    private val uri = LinkLaunchPolicy.extractUri(sharedText)

    /** The first non-empty line that is not the URI itself — the note's title, in practice. */
    private val label: String? = uri?.let { found ->
        sharedText?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() && it != found }
    }

    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ShareState> = _state.asStateFlow()

    private fun initial(): ShareState {
        val found = uri ?: return ShareState.Nothing("No link in the shared text.")
        return when (val check = LinkLaunchPolicy.check(found)) {
            is LinkCheck.Accepted -> ShareState.Card(check.uri, check.kind.label(), label)
            is LinkCheck.NeedsConfirmation -> ShareState.NeedsConfirmation(check.scheme, check.uri)
            is LinkCheck.Rejected -> ShareState.Nothing("noteNFC won't save that link: ${check.reason}.")
        }
    }

    /** One explicit confirmation for an unknown scheme; it is remembered as `LinkKind.OTHER`. */
    fun confirmUnknownScheme() {
        val asked = _state.value as? ShareState.NeedsConfirmation ?: return
        _state.value = ShareState.Card(asked.uri, LinkKind.OTHER.label(), label)
    }

    fun keepAsLink() = save { link -> ShareState.Saved(link) }

    fun writeToTag() = save { link -> ShareState.Writing(Route.WriteTag("link", link, label)) }

    /**
     * Saves once and hands the new link's id to [next]. `confirmedOther` is true because the only
     * way to reach a non-allowlisted scheme here is through the confirmation sheet above.
     */
    private fun save(next: (String) -> ShareState) {
        val card = _state.value as? ShareState.Card ?: return
        viewModelScope.launch {
            val saved = runCatching { saveLink.run(card.uri, label, confirmedOther = true) }
            saved.fold(
                onSuccess = { link -> _state.value = next(link.id.value) },
                onFailure = { _state.value = ShareState.Nothing("Couldn't save that link: ${it.message}") },
            )
        }
    }
}

/**
 * The share card: the kind in words, the URI in mono, and the two things worth doing with it —
 * writing it to a tag (the reason this app exists) or simply keeping it.
 */
@Composable
fun ShareCard(
    state: ShareState.Card,
    onWriteTag: () -> Unit,
    onKeepAsLink: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = state.kind.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = state.label ?: state.uri, style = SheetSentence)
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = PlateShape,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = state.uri,
                style = MonoText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
        Button(onClick = onWriteTag, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
            Text("Write to a new tag")
        }
        OutlinedButton(onClick = onKeepAsLink, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
            Text("Keep as link")
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
    }
}

/** An unknown scheme is asked about once, in plain words, before anything is saved. */
@Composable
fun ShareConfirmation(
    state: ShareState.NeedsConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "UNFAMILIAR SCHEME",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "noteNFC has not seen '${state.scheme}' before.", style = SheetSentence)
        QuietLine("Saving it means noteNFC may hand this URI to whichever app claims that scheme.")
        Text(text = state.uri, style = MonoText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onConfirm, shape = ControlShape, modifier = Modifier.fillMaxWidth()) {
            Text("Save it anyway")
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
    }
}
