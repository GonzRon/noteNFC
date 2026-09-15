package com.loosecannon.notenfc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetTreeTest {

    private fun asset(id: String, name: String, parent: String? = null) = Asset(
        id = AssetId(id), name = name, createdAt = 0L, updatedAt = 0L,
        parentAssetId = parent?.let(::AssetId),
    )

    @Test fun selfIsACycle() {
        val assets = listOf(asset("a1", "Furnace"))
        assertTrue(AssetTree.wouldCycle(assets, AssetId("a1"), AssetId("a1")))
    }

    @Test fun directCycle() {
        // a1 -> a2 (a1's parent is a2); now try to make a2's parent a1.
        val assets = listOf(asset("a1", "Furnace", parent = "a2"), asset("a2", "Panel"))
        assertTrue(AssetTree.wouldCycle(assets, AssetId("a2"), AssetId("a1")))
    }

    @Test fun transitiveCycle() {
        // a2 -> a1, a3 -> a2; now try to make a1's parent a3.
        val assets = listOf(
            asset("a1", "Root"),
            asset("a2", "Mid", parent = "a1"),
            asset("a3", "Leaf", parent = "a2"),
        )
        assertTrue(AssetTree.wouldCycle(assets, AssetId("a1"), AssetId("a3")))
    }

    @Test fun preExistingCycleIsDetected() {
        // a1 <-> a2 already cycle in the input; a3 is unrelated but its walk enters that cycle.
        val assets = listOf(
            asset("a1", "One", parent = "a2"),
            asset("a2", "Two", parent = "a1"),
            asset("a3", "Three"),
        )
        assertTrue(AssetTree.wouldCycle(assets, AssetId("a3"), AssetId("a1")))
    }

    @Test fun parentsFirstPutsRootsFirstFromShuffledInput() {
        // Two roots (r1, r2), three levels deep: r1 -> c1 -> g1, r2 -> c2.
        val r1 = asset("r1", "Root One")
        val r2 = asset("r2", "Root Two")
        val c1 = asset("c1", "Child One", parent = "r1")
        val c2 = asset("c2", "Child Two", parent = "r2")
        val g1 = asset("g1", "Grandchild", parent = "c1")
        val shuffled = listOf(g1, c2, r2, c1, r1)

        val ordered = AssetTree.parentsFirst(shuffled)
        // Global priority queue seeded with roots, ties broken by id.value: r1 dequeues before r2,
        // then c1 (now ready) sorts ahead of r2 ("c1" < "r2"), then g1 ahead of r2 ("g1" < "r2").
        val expected = listOf(r1, c1, g1, r2, c2)
        assertEquals(expected.map { it.id }, ordered.map { it.id })

        val position = ordered.mapIndexed { i, a -> a.id to i }.toMap()
        for (a in ordered) {
            val parentId = a.parentAssetId ?: continue
            assertTrue(position.getValue(parentId) < position.getValue(a.id), "${a.id} precedes its parent $parentId")
        }

        // Deterministic: a second shuffle gives the same order.
        val orderedAgain = AssetTree.parentsFirst(listOf(c1, r1, g1, r2, c2))
        assertEquals(ordered.map { it.id }, orderedAgain.map { it.id })
    }

    @Test fun parentsFirstThrowsOnCycle() {
        val assets = listOf(
            asset("a1", "One", parent = "a2"),
            asset("a2", "Two", parent = "a1"),
        )
        assertFailsWith<IllegalStateException> { AssetTree.parentsFirst(assets) }
    }

    @Test fun descendantsAreTransitive() {
        val r1 = asset("r1", "Root")
        val c1 = asset("c1", "Child", parent = "r1")
        val g1 = asset("g1", "Grandchild", parent = "c1")
        val other = asset("o1", "Unrelated")
        val assets = listOf(r1, c1, g1, other)

        assertEquals(setOf(AssetId("c1"), AssetId("g1")), AssetTree.descendants(assets, AssetId("r1")))
        assertEquals(setOf(AssetId("g1")), AssetTree.descendants(assets, AssetId("c1")))
        assertTrue(AssetTree.descendants(assets, AssetId("g1")).isEmpty())
        assertFalse(AssetTree.descendants(assets, AssetId("r1")).contains(AssetId("o1")))
    }
}
