package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.journal.SeedTemplates
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * A new asset. The scan flow calls this with a name alone — the minimum needed to bind a tag to
 * something new — while the Phase 1C form fills in what the user typed; the profile fields a
 * schedule needs are still Phase 2. An optional `templateKey` seeds the asset's definitions and
 * profiles from one of [SeedTemplates] in the same transaction as the create (§7); an asset made
 * without one can still be set up later through [ApplyTemplate] directly.
 */
class CreateAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val applyTemplate: ApplyTemplate,
) {
    suspend fun run(
        name: String,
        category: String = "",
        description: String = "",
        notes: String = "",
        templateKey: String? = null,
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
        uow.write {
            assets.upsert(asset)
            templateKey?.let { key ->
                applyTemplate.applyInTransaction(asset.id, SeedTemplates.byKey(key) ?: throw UnknownTemplate(key))
            }
        }
        return asset
    }
}
