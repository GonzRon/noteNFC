package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Retirement is data, not a lifecycle enum (spec §7): `retiredOn` is the whole state, so a thing
 * that is gone from the house can still be archived — or not — and its tags still resolve. The
 * date is the user's, not the clock's: backdating "I replaced this in April" is the normal case.
 * Retiring a parent does not touch its children.
 */
class RetireAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun retire(id: AssetId, on: String): Asset {
        val date = on.trim()
        if (!isIsoDate(date)) throw AssetValidation(listOf(AssetProblem.BadDate("retiredOn")))
        return save(id, date)
    }

    suspend fun unretire(id: AssetId): Asset = save(id, null)

    private suspend fun save(id: AssetId, retiredOn: String?): Asset {
        val current = assets.get(id) ?: throw NoSuchAsset(id)
        val saved = current.copy(retiredOn = retiredOn, updatedAt = clock.nowMillis())
        uow.write { assets.upsert(saved) }
        return saved
    }
}
