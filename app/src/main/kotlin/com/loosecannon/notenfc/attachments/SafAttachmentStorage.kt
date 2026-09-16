package com.loosecannon.notenfc.attachments

import android.net.Uri
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.prefs.AppPrefs

/**
 * Owns the tree preference and answers the one question every attachment path asks first: is
 * there a folder, and can we write to it (spec §5.3).
 *
 * `AccessLost` is not a failure mode to be papered over — a persisted grant really does go away
 * when the owner clears the app's data in Settings or the provider is uninstalled — so it is a
 * state with its own wording and its own repair (re-choosing the *same* folder).
 */
class SafAttachmentStorage(
    private val prefs: AppPrefs,
    private val rootResolver: (treeUri: String) -> AttachmentRoot?,
    private val grantCheck: (treeUri: String) -> Boolean,
) : AttachmentStorage {

    override fun state(): StoreState {
        val uri = prefs.attachmentTreeUri ?: return StoreState.NotConfigured
        val root = rootResolver(uri) ?: return StoreState.AccessLost(nameOf(uri))
        if (!grantCheck(uri) || !root.canWrite()) return StoreState.AccessLost(root.displayName)
        return StoreState.Ready(root.displayName, root.authority)
    }

    override fun store(): AttachmentStore? =
        prefs.attachmentTreeUri
            ?.takeIf { state() is StoreState.Ready }
            ?.let { rootResolver(it)?.store() }

    /** For `ACTION_VIEW`: the document's own URI, or null when the bytes are not on this device. */
    fun viewUri(locator: String): Uri? =
        prefs.attachmentTreeUri
            ?.takeIf { state() is StoreState.Ready }
            ?.let { rootResolver(it)?.viewUri(locator) }

    /** A folder whose root we cannot even resolve still has a name worth showing. */
    private fun nameOf(uri: String): String =
        uri.substringAfterLast("%2F").substringAfterLast('/').ifEmpty { "Folder" }
}
