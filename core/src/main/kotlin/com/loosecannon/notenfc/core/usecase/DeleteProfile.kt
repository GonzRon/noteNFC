package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.ProfileId
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Deletes a profile. Always allowed (spec §6): a profile is a shortcut, not data. Events logged
 * through it keep their title, kind, measurements and consumables — the schema SET NULLs their
 * `profile_id`, so history survives the shortcut being retired.
 */
class DeleteProfile(
    private val profiles: ProfileRepository,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: ProfileId) {
        profiles.get(id) ?: throw NoSuchProfile(id)
        uow.write { profiles.delete(id) }
    }
}
