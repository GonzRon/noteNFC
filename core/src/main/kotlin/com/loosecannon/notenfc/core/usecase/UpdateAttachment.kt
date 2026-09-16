package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentProblem
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Name, kind, captured-on and notes. Nothing here touches the locator or the sha256: the bytes
 * are not being edited, so a rename is metadata and the file stays where it is (spec §4).
 * `Unchanged` is a refusal rather than a silent no-op so the sheet can close without claiming a
 * save that did not happen.
 */
class UpdateAttachment(
    private val attachments: AttachmentRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(id: AttachmentId, cmd: UpdateAttachmentCommand): AttachmentResult<Attachment> {
        val row = attachments.get(id)
            ?: return AttachmentResult.Refused(AttachmentProblem.OwnerMissing)
        val name = cmd.displayName.trim()
        if (name.isEmpty()) return AttachmentResult.Refused(AttachmentProblem.BlankName)

        val updated = row.copy(
            displayName = name,
            kind = cmd.kind,
            capturedOn = cmd.capturedOn?.trim()?.takeIf { it.isNotEmpty() },
            notes = cmd.notes.trim(),
            updatedAt = clock.nowMillis(),
        )
        if (updated.copy(updatedAt = row.updatedAt) == row) {
            return AttachmentResult.Refused(AttachmentProblem.Unchanged)
        }
        uow.write { attachments.upsert(updated) }
        return AttachmentResult.Ok(updated)
    }
}
