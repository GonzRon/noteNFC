package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.AttachmentId
import com.loosecannon.servicetag.core.ports.AttachmentRepository
import com.loosecannon.servicetag.core.ports.AttachmentStorage
import com.loosecannon.servicetag.core.ports.UnitOfWork

/**
 * The row goes inside the transaction; the bytes go after it, through [sweepBytes]: the row is
 * gone, a file the store will not delete is an orphan, and sweeping orphans is 4B's job (spec §6).
 * Deleting an attachment that is not there is not an error.
 */
class DeleteAttachment(
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: AttachmentId) {
        val row = attachments.get(id) ?: return
        uow.write { attachments.delete(id) }
        storage.sweepBytes(listOf(row.storageLocator))
    }
}
