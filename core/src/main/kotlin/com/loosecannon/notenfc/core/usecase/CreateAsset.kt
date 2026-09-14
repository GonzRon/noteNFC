package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** The minimum needed to bind a tag to something new; the full asset form is Phase 2. */
class CreateAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(name: String, category: String = ""): Asset {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "an asset needs a name" }
        val now = clock.nowMillis()
        val asset = Asset(id = AssetId(ids.newId()), name = clean, category = category.trim(), createdAt = now, updatedAt = now)
        uow.write { assets.upsert(asset) }
        return asset
    }
}
