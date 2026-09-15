package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * The one rule an asset form can break: an asset has to be named. Typed rather than a bare
 * `require`, so a screen can tell "you left the name empty" apart from "that asset is gone" and
 * mark the right field (the [LinkRefused] pattern).
 */
class AssetNameRequired : IllegalArgumentException("an asset needs a name")

/** The asset this edit or archive was aimed at is no longer there — a stale back stack, usually. */
class NoSuchAsset(id: AssetId) : IllegalArgumentException("no asset ${id.value}")

/**
 * Edits the fields a person types. Identity is not editable: id, `createdAt` and `status` come
 * from the stored row, so an edit can never resurrect an archived asset by accident (R-9 keeps
 * that on [ArchiveAsset], where it is a deliberate act).
 */
class UpdateAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(
        id: AssetId,
        name: String,
        category: String = "",
        description: String = "",
        notes: String = "",
    ): Asset {
        val clean = name.trim()
        if (clean.isEmpty()) throw AssetNameRequired()
        val current = assets.get(id) ?: throw NoSuchAsset(id)
        val saved = current.copy(
            name = clean,
            category = category.trim(),
            description = description.trim(),
            notes = notes.trim(),
            updatedAt = clock.nowMillis(),
        )
        uow.write { assets.upsert(saved) }
        return saved
    }
}
