package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetTree
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * The one destructive asset action, behind the typed confirmation flow. Children-first (spec §5):
 * a parent that still has children is refused with them named, so nobody loses a sub-assembly to
 * a cascade they did not picture. Everything that hangs off the asset itself — its tags, links,
 * definitions, profiles and events — goes with it, by the schema's own cascades.
 */
class DeleteAsset(
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: AssetId) {
        val all = assets.all()
        if (all.none { it.id == id }) throw NoSuchAsset(id)
        val children = AssetTree.children(all, id)
        if (children.isNotEmpty()) throw AssetHasChildren(id, children.map { it.id })
        uow.write { assets.delete(id) }
    }
}
