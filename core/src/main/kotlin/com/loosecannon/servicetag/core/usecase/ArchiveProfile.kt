package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.ProfileId
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.ProfileRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

/**
 * Takes a quick action off the asset screen without deleting it: events logged through it keep
 * pointing at it, and unarchiving puts it back exactly as it was.
 */
class ArchiveProfile(
    private val profiles: ProfileRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(id: ProfileId, archived: Boolean) {
        val current = profiles.get(id) ?: throw NoSuchProfile(id)
        val now = clock.nowMillis()
        val saved = current.copy(archivedAt = if (archived) now else null, updatedAt = now)
        uow.write { profiles.upsert(saved) }
    }
}
