package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKinds
import com.loosecannon.notenfc.core.model.AttachmentLocator
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.AttachmentProblem
import com.loosecannon.notenfc.core.model.MAX_ATTACHMENT_BYTES
import com.loosecannon.notenfc.core.model.MimeTypes
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Bytes first, row second (spec §6). The store hashes while it copies, so the row records what
 * the store saw and the two can never disagree; if the row write then fails, the bytes are
 * deleted, because nothing would point at them.
 *
 * The owner check and the `put` both happen *outside* `uow.write`: the transaction holds the row
 * write alone. `FakeUnitOfWork` is not re-entrant and a multi-megabyte copy has no business
 * inside a database transaction.
 */
class AddAttachment(
    private val attachments: AttachmentRepository,
    private val assets: AssetRepository,
    private val events: EventRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(
        owner: AttachmentOwner,
        cmd: AddAttachmentCommand,
        source: ByteSource,
    ): AttachmentResult<Attachment> {
        val name = cmd.displayName.trim()
        if (name.isEmpty()) return AttachmentResult.Refused(AttachmentProblem.BlankName)
        if (!ownerExists(owner)) return AttachmentResult.Refused(AttachmentProblem.OwnerMissing)
        if (cmd.sizeBytes != null && cmd.sizeBytes > MAX_ATTACHMENT_BYTES) {
            return AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES))
        }
        val store = when (storage.state()) {
            StoreState.NotConfigured -> return AttachmentResult.Refused(AttachmentProblem.NoStore)
            is StoreState.AccessLost ->
                return AttachmentResult.Refused(AttachmentProblem.StoreUnavailable)
            is StoreState.Ready -> storage.store()
                ?: return AttachmentResult.Refused(AttachmentProblem.StoreUnavailable)
        }

        val id = AttachmentId(ids.newId())
        val mimeType = MimeTypes.normalise(cmd.mimeType)
        val locator = AttachmentLocator.forOwner(owner, id, name, mimeType)
        val stored = store.put(locator, source)
        // A provider that under-reported its size (or reported none) is caught here instead.
        if (stored.sizeBytes > MAX_ATTACHMENT_BYTES) {
            store.delete(locator)
            return AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES))
        }

        val now = clock.nowMillis()
        val row = Attachment(
            id = id,
            owner = owner,
            kind = cmd.kind ?: AttachmentKinds.inferFrom(mimeType, cmd.fromCamera),
            displayName = name,
            mimeType = mimeType,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            storageLocator = locator,
            capturedOn = cmd.capturedOn?.trim()?.takeIf { it.isNotEmpty() },
            notes = cmd.notes.trim(),
            createdAt = now,
            updatedAt = now,
        )
        try {
            uow.write { attachments.upsert(row) }
        } catch (t: Throwable) {
            store.delete(locator)   // the bytes were ours and now nothing names them
            throw t
        }
        return AttachmentResult.Ok(row)
    }

    private suspend fun ownerExists(owner: AttachmentOwner): Boolean = when (owner) {
        is AttachmentOwner.OfAsset -> assets.get(owner.assetId) != null
        is AttachmentOwner.OfEvent -> events.get(owner.eventId) != null
    }
}
