package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * A new asset. The scan flow calls this with a name alone — the minimum needed to bind a tag to
 * something new — while the Phase 1C form fills in what the user typed; the profile fields a
 * schedule needs are still Phase 2.
 */
class CreateAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(
        name: String,
        category: String = "",
        description: String = "",
        notes: String = "",
    ): Asset {
        val clean = name.trim()
        if (clean.isEmpty()) throw AssetNameRequired()
        val now = clock.nowMillis()
        val asset = Asset(
            id = AssetId(ids.newId()),
            name = clean,
            description = description.trim(),
            category = category.trim(),
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now,
        )
        uow.write { assets.upsert(asset) }
        return asset
    }
}
