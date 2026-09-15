package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.isRetired
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Retirement is data, not a status (spec §7): `retiredOn` carries it and [AssetStatus] is left
 * alone, so a retired asset can also be archived — or not — independently. Delete is the one
 * destructive asset action and it stops at a parent that still has children (§5).
 */
class RetireDeleteAssetTest {
    private val assets = InMemoryAssetRepository()
    private val uow = FakeUnitOfWork(assets)
    private var now = 1_000L
    private val clock = Clock { now }
    private val retire = RetireAsset(assets, uow, clock)
    private val delete = DeleteAsset(assets, uow)
    private val archive = ArchiveAsset(assets, uow, clock)

    private suspend fun store(id: String, name: String, parent: AssetId? = null): Asset {
        val asset = Asset(
            id = AssetId(id),
            name = name,
            createdAt = 500L,
            updatedAt = 500L,
            parentAssetId = parent,
        )
        assets.upsert(asset)
        return asset
    }

    @Test fun retireSetsDateAndKeepsStatus() = runTest {
        store("a1", "Pool pump")
        now = 9_000L
        val retired = retire.retire(AssetId("a1"), "2026-09-15")
        assertEquals("2026-09-15", retired.retiredOn)
        assertTrue(retired.isRetired)
        assertEquals(AssetStatus.ACTIVE, retired.status)   // retirement is not archival
        assertEquals(500L, retired.createdAt)
        assertEquals(9_000L, retired.updatedAt)
        assertEquals(retired, assets.rows["a1"])
        assertEquals(1, uow.commits)

        // a date that is not an ISO date is refused, and writes nothing
        val boom = assertFailsWith<AssetValidation> { retire.retire(AssetId("a1"), "15/09/2026") }
        assertEquals(listOf(AssetProblem.BadDate("retiredOn")), boom.problems)
        assertFailsWith<NoSuchAsset> { retire.retire(AssetId("nope"), "2026-09-15") }
        assertEquals(1, uow.commits)
    }

    @Test fun unretireClears() = runTest {
        store("a1", "Pool pump")
        retire.retire(AssetId("a1"), "2026-09-15")
        now = 12_000L
        val back = retire.unretire(AssetId("a1"))
        assertNull(back.retiredOn)
        assertFalse(back.isRetired)
        assertEquals(12_000L, back.updatedAt)
        assertEquals(back, assets.rows["a1"])
        assertEquals(2, uow.commits)
        assertFailsWith<NoSuchAsset> { retire.unretire(AssetId("nope")) }
    }

    @Test fun deleteRefusedWithChildrenNamed() = runTest {
        store("a1", "Hot tub")
        store("a3", "Heater", parent = AssetId("a1"))
        store("a2", "Blower", parent = AssetId("a1"))
        store("a4", "Element", parent = AssetId("a3"))   // a grandchild, not a direct child

        val boom = assertFailsWith<AssetHasChildren> { delete.run(AssetId("a1")) }
        assertEquals(AssetId("a1"), boom.assetId)
        assertEquals(listOf(AssetId("a2"), AssetId("a3")), boom.children)   // by name, case-insensitive
        assertEquals(4, assets.rows.size)
        assertEquals(0, uow.commits)
        assertFailsWith<NoSuchAsset> { delete.run(AssetId("nope")) }
    }

    @Test fun deleteWithoutChildrenRemoves() = runTest {
        store("a1", "Hot tub")
        store("a2", "Heater", parent = AssetId("a1"))
        delete.run(AssetId("a2"))
        assertEquals(setOf("a1"), assets.rows.keys)
        assertEquals(1, uow.commits)
        // with the child gone the parent can go too
        delete.run(AssetId("a1"))
        assertTrue(assets.rows.isEmpty())
        assertEquals(2, uow.commits)
    }

    @Test fun archiveDoesNotCascade() = runTest {
        store("a1", "Hot tub")
        store("a2", "Heater", parent = AssetId("a1"))
        archive.run(AssetId("a1"))
        assertEquals(AssetStatus.ARCHIVED, assets.rows["a1"]!!.status)
        assertEquals(AssetStatus.ACTIVE, assets.rows["a2"]!!.status)
        assertNull(assets.rows["a2"]!!.retiredOn)
        assertEquals(AssetId("a1"), assets.rows["a2"]!!.parentAssetId)

        // nor does retiring one
        retire.retire(AssetId("a1"), "2026-09-15")
        assertNull(assets.rows["a2"]!!.retiredOn)
    }
}
