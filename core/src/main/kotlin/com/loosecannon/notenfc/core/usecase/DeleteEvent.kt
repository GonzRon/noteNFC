package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** Deletes an [com.loosecannon.notenfc.core.model.AssetEvent]; its children go with it (CASCADE). One `uow.write`. */
class DeleteEvent(
    private val events: EventRepository,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: EventId) {
        uow.write { events.delete(id) }
    }
}
