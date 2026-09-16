package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.ports.AssetRepository
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.UnitOfWork

/** The asset this edit or archive was aimed at is no longer there — a stale back stack, usually. */
class NoSuchAsset(id: AssetId) : IllegalArgumentException("no asset ${id.value}")

/**
 * Edits the fields a person types. Identity is not editable: id, `createdAt`, `status`,
 * `retiredOn` and `templateKey` come from the stored row, so an edit can never resurrect an
 * archived asset or un-retire one by accident (R-9 keeps those on [ArchiveAsset] and
 * [RetireAsset], where they are deliberate acts). Everything else goes through [validateAsset],
 * the same gate [CreateAsset] uses.
 */
class UpdateAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(id: AssetId, cmd: AssetCommand): Asset {
        val all = assets.all()
        val current = all.firstOrNull { it.id == id } ?: throw NoSuchAsset(id)
        val clean = validateAsset(cmd, all, id)
        val saved = current.applying(clean, clock.nowMillis())
        uow.write { assets.upsert(saved) }
        return saved
    }
}
