package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.ProfileId
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.ProfileRepository
import com.loosecannon.servicetag.core.ports.UnitOfWork

/**
 * Rewrites `sortOrder` so the asset's quick actions read in [orderedIds] order — the same partial
 * list rule as [ReorderDefinitions]: named profiles first, the rest keep their relative order.
 */
class ReorderProfiles(
    private val profiles: ProfileRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(assetId: AssetId, orderedIds: List<ProfileId>) {
        val rows = profiles.forAsset(assetId).sortedBy { it.sortOrder }
        val byId = rows.associateBy { it.id }
        val named = orderedIds.distinct()
        named.firstOrNull { it !in byId }?.let {
            throw EventOwnership("profile ${it.value} does not belong to asset ${assetId.value}")
        }
        val ordered = named.map { byId.getValue(it) } + rows.filter { it.id !in named.toSet() }
        val now = clock.nowMillis()
        val moved = ordered.mapIndexedNotNull { i, p ->
            if (p.sortOrder == i) null else p.copy(sortOrder = i, updatedAt = now)
        }
        uow.write { moved.forEach { profiles.upsert(it) } }
    }
}
