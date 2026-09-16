package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetTree
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * The one destructive asset action, behind the typed confirmation flow. Children-first (spec §5):
 * a parent that still has children is refused with them named, so nobody loses a sub-assembly to
 * a cascade they did not picture. Everything that hangs off the asset itself — its tags, links,
 * definitions, profiles, events and attachment rows — goes with it, by the schema's own cascades;
 * the attachment *bytes* are nothing the schema can cascade, so they are swept here.
 */
class DeleteAsset(
    private val assets: AssetRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: AssetId) {
        val all = assets.all()
        if (all.none { it.id == id }) throw NoSuchAsset(id)
        val children = AssetTree.children(all, id)
        if (children.isNotEmpty()) throw AssetHasChildren(id, children.map { it.id })

        // The locators are read *before* the cascade takes the rows with it, inside the same
        // transaction that deletes them: after the commit there is nothing left to ask.
        val doomed = uow.write {
            val own = attachments.forAsset(id)
            val theirs = events.forAsset(id)
                .flatMap { event -> attachments.forOwner(AttachmentOwner.OfEvent(event.id)) }
            val locators = (own + theirs).map { it.storageLocator }
            assets.delete(id)
            locators
        }

        // Bytes after the commit, best effort: a file the store will not delete is an orphan,
        // not a reason to keep an asset the person deleted.
        storage.sweepBytes(doomed)
    }
}
