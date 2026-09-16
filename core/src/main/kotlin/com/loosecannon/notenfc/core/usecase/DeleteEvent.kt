package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Deletes an [com.loosecannon.notenfc.core.model.AssetEvent]; its children go with it (CASCADE),
 * attachment rows included. One `uow.write`, which also reads the locators the cascade is about
 * to take out of reach; the bytes then go after the commit, best effort (spec §6).
 */
class DeleteEvent(
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: EventId) {
        val doomed = uow.write {
            val locators = attachments.forOwner(AttachmentOwner.OfEvent(id)).map { it.storageLocator }
            events.delete(id)
            locators
        }
        storage.sweepBytes(doomed)
    }
}
