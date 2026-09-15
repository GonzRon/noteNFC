package com.loosecannon.notenfc.core.model

import java.util.PriorityQueue

/**
 * Pure hierarchy logic over a flat asset collection (spec §5). One parent, any depth, no cycles.
 */
object AssetTree {

    /**
     * True if giving [assetId] the parent [newParentId] would create a cycle: walking up from
     * [newParentId] through existing `parentAssetId` links reaches [assetId] itself, or the walk
     * runs longer than the asset count, which means the input already contains a cycle elsewhere
     * in the chain (restore validation must reject that too).
     */
    fun wouldCycle(assets: Collection<Asset>, assetId: AssetId, newParentId: AssetId?): Boolean {
        if (newParentId == null) return false
        val byId = assets.associateBy { it.id }
        var current: AssetId? = newParentId
        var steps = 0
        while (current != null) {
            if (current == assetId) return true
            steps++
            if (steps > assets.size) return true
            current = byId[current]?.parentAssetId
        }
        return false
    }

    /** Every asset transitively parented under [assetId], direct children included. */
    fun descendants(assets: Collection<Asset>, assetId: AssetId): Set<AssetId> {
        val childrenOf = assets.groupBy { it.parentAssetId }
        val result = mutableSetOf<AssetId>()
        fun collect(id: AssetId) {
            for (child in childrenOf[id].orEmpty()) {
                if (result.add(child.id)) collect(child.id)
            }
        }
        collect(assetId)
        return result
    }

    /** Direct children of [assetId], by name, case-insensitive. */
    fun children(assets: Collection<Asset>, assetId: AssetId): List<Asset> =
        assets.filter { it.parentAssetId == assetId }.sortedBy { it.name.lowercase() }

    /**
     * A topological order (Kahn's algorithm): roots first, ties broken by [AssetId.value].
     * Throws [IllegalStateException] when the input contains a cycle.
     */
    fun parentsFirst(assets: Collection<Asset>): List<Asset> {
        val byId = assets.associateBy { it.id }
        val childrenOf = assets.groupBy { it.parentAssetId }
        val indegree = assets.associateTo(mutableMapOf()) { asset ->
            asset.id to if (asset.parentAssetId != null && byId.containsKey(asset.parentAssetId)) 1 else 0
        }

        val ready = PriorityQueue<Asset>(compareBy { it.id.value })
        assets.filterTo(mutableListOf()) { indegree[it.id] == 0 }.forEach(ready::add)

        val result = mutableListOf<Asset>()
        while (ready.isNotEmpty()) {
            val node = ready.poll()
            result += node
            for (child in childrenOf[node.id].orEmpty()) {
                val remaining = (indegree[child.id] ?: 0) - 1
                indegree[child.id] = remaining
                if (remaining == 0) ready.add(child)
            }
        }

        if (result.size != assets.size) throw IllegalStateException("cycle detected in asset hierarchy")
        return result
    }
}
