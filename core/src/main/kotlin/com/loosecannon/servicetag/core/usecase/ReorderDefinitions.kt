package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.DefinitionId
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.DefinitionRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

/**
 * Rewrites `sortOrder` so the asset's definitions read in [orderedIds] order. A partial list is
 * legal — the named definitions come first, everything else keeps its relative order behind them,
 * which is what a drag of one row into place actually means. Only rows whose position really
 * changed are written, so a no-op drag doesn't bump `updatedAt` across the whole asset.
 */
class ReorderDefinitions(
    private val definitions: DefinitionRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(assetId: AssetId, orderedIds: List<DefinitionId>) {
        val rows = definitions.forAsset(assetId).sortedBy { it.sortOrder }
        val byId = rows.associateBy { it.id }
        val named = orderedIds.distinct()
        named.firstOrNull { it !in byId }?.let {
            throw EventOwnership("definition ${it.value} does not belong to asset ${assetId.value}")
        }
        val ordered = named.map { byId.getValue(it) } + rows.filter { it.id !in named.toSet() }
        val now = clock.nowMillis()
        val moved = ordered.mapIndexedNotNull { i, d ->
            if (d.sortOrder == i) null else d.copy(sortOrder = i, updatedAt = now)
        }
        uow.write { moved.forEach { definitions.upsert(it) } }
    }
}
