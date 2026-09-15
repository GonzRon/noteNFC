package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Edits an existing [AssetEvent] in place through the same validation path as [LogEvent]. An edit
 * never re-parents an event: `existing.assetId != cmd.assetId` is an [EventOwnership] failure,
 * checked (along with the command's own profile/definition ownership, via [resolveOwnedProfile])
 * before any field validation runs; nothing is stored if any ownership check fails. `id`,
 * `createdAt`, `source` and `sourceRef` are carried over from the stored row; `updatedAt` is set
 * to now. One `uow.write`.
 *
 * [ids] is only ever consulted for a measurement or consumable line that has no counterpart in
 * the stored event — an edit that keeps a field keeps that field's id.
 */
class UpdateEvent(
    private val events: EventRepository,
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(id: EventId, cmd: EventCommand): AssetEvent = uow.write {
        val existing = events.get(id) ?: throw NoSuchEvent(id)
        if (existing.assetId != cmd.assetId) {
            throw EventOwnership(
                "event ${id.value} belongs to asset ${existing.assetId.value}, not ${cmd.assetId.value}",
            )
        }
        val profile = resolveOwnedProfile(cmd, definitions, profiles)
        val event = buildEvent(cmd, definitions, profile, existing, ids, clock.nowMillis())
        events.upsert(event)
        event
    }
}
