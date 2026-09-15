package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Deletes a definition, but only when nothing points at it: no measurement, no DERIVED definition
 * reading it as a source, no profile field offering it. Otherwise [DefinitionReferenced] names
 * every referrer so the UI can say "used by Water test" and offer [ArchiveDefinition] instead.
 *
 * Profile fields cascade at the schema level; they are still counted here, because deleting a
 * definition out from under a quick action is a surprise, not a cleanup.
 */
class DeleteDefinition(
    private val definitions: DefinitionRepository,
    private val events: EventRepository,
    private val profiles: ProfileRepository,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: DefinitionId) {
        val current = definitions.get(id) ?: throw NoSuchDefinition(id)
        val measurements = events.countMeasurementsFor(id)
        val derivedBy = definitions.forAsset(current.assetId)
            .filter { it.id != id && (it.derived?.sourceA == id || it.derived?.sourceB == id) }
            .map { it.id }
        val referencedBy = profiles.forAsset(current.assetId)
            .filter { p -> p.fields.any { it.definitionId == id } }
            .map { it.id }
        if (measurements > 0 || derivedBy.isNotEmpty() || referencedBy.isNotEmpty()) {
            throw DefinitionReferenced(id, measurements, derivedBy, referencedBy)
        }
        uow.write { definitions.delete(id) }
    }
}
