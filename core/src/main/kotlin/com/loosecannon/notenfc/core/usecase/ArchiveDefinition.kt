package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Retires a definition from the forms without touching its history (spec §5): stored measurements
 * stay, a derived definition that reads it simply stops computing while it is archived. Allowed
 * even when a DERIVED definition names it as a source — that is why archiving is the way out of a
 * definition you can't delete.
 */
class ArchiveDefinition(
    private val definitions: DefinitionRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(id: DefinitionId, archived: Boolean) {
        val current = definitions.get(id) ?: throw NoSuchDefinition(id)
        val now = clock.nowMillis()
        val saved = current.copy(archivedAt = if (archived) now else null, updatedAt = now)
        uow.write { definitions.upsert(saved) }
    }
}
