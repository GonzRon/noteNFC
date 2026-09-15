package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UnitOfWork

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

    /** The pre-2B-2 four-field form, kept so the Phase 1C screens compile unchanged. */
    suspend fun run(
        id: AssetId,
        name: String,
        category: String = "",
        description: String = "",
        notes: String = "",
    ): Asset {
        val current = assets.get(id) ?: throw NoSuchAsset(id)
        return run(
            id,
            AssetCommand(
                name = name,
                category = category,
                description = description,
                notes = notes,
                manufacturer = current.manufacturer,
                model = current.model,
                serialNumber = current.serialNumber,
                purchaseOn = current.purchaseOn,
                inServiceOn = current.inServiceOn,
                purchasePriceMinor = current.purchasePriceMinor,
                currency = current.currency,
                vendor = current.vendor,
                location = current.location,
                warrantyExpiresOn = current.warrantyExpiresOn,
                warrantyNotes = current.warrantyNotes,
                parentAssetId = current.parentAssetId,
                seasonStartMmdd = current.seasonStartMmdd,
                seasonEndMmdd = current.seasonEndMmdd,
            ),
        )
    }
}
