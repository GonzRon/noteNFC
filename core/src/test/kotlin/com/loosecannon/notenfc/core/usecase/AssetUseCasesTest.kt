package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The two edit-side asset use cases. Archive-first (R-9): nothing here deletes a row, so an
 * archived asset keeps its id, its created date and every tag already bound to it.
 */
class AssetUseCasesTest {
    private val assets = InMemoryAssetRepository()
    private val uow = FakeUnitOfWork(assets)
    private var now = 1_000L
    private val clock = Clock { now }
    private val update = UpdateAsset(assets, uow, clock)
    private val archive = ArchiveAsset(assets, uow, clock)

    private suspend fun store(
        name: String = "Pool pump",
        status: AssetStatus = AssetStatus.ACTIVE,
    ): Asset {
        val asset = Asset(
            id = AssetId("a1"),
            name = name,
            description = "Pentair",
            category = "Water",
            notes = "",
            status = status,
            createdAt = 500L,
            updatedAt = 500L,
        )
        assets.upsert(asset)
        return asset
    }

    @Test fun updateTrimsEveryFieldAndBumpsUpdatedAt() = runTest {
        store()
        now = 9_000L
        val saved = update.run(AssetId("a1"), "  Hot tub  ", "  Water  ", "  Jacuzzi J-235  ", "  drains in Oct  ")
        assertEquals("Hot tub", saved.name)
        assertEquals("Water", saved.category)
        assertEquals("Jacuzzi J-235", saved.description)
        assertEquals("drains in Oct", saved.notes)
        assertEquals(9_000L, saved.updatedAt)
        assertEquals(saved, assets.rows["a1"])
        assertEquals(1, uow.commits)
    }

    @Test fun updateKeepsTheCreatedDateAndTheArchivedStatus() = runTest {
        store(status = AssetStatus.ARCHIVED)
        now = 9_000L
        val saved = update.run(AssetId("a1"), "Hot tub")
        assertEquals(500L, saved.createdAt)
        assertEquals(AssetStatus.ARCHIVED, saved.status)
    }

    @Test fun updateRefusesABlankNameAndWritesNothing() = runTest {
        val before = store()
        assertFailsWith<AssetNameRequired> { update.run(AssetId("a1"), "   ") }
        assertEquals(before, assets.rows["a1"])
        assertEquals(0, uow.commits)
    }

    @Test fun updateRefusesAnUnknownId() = runTest {
        assertFailsWith<NoSuchAsset> { update.run(AssetId("nope"), "Hot tub") }
        assertEquals(0, uow.commits)
    }

    @Test fun archiveFlipsTheStatusAndBumpsUpdatedAt() = runTest {
        store()
        now = 9_000L
        val archived = archive.run(AssetId("a1"))
        assertEquals(AssetStatus.ARCHIVED, archived.status)
        assertEquals(9_000L, archived.updatedAt)
        assertEquals(500L, archived.createdAt)
        assertEquals(archived, assets.rows["a1"])
        assertEquals(1, uow.commits)
    }

    @Test fun unarchiveRestoresActiveAndAnUnknownIdIsRefused() = runTest {
        store(status = AssetStatus.ARCHIVED)
        now = 9_000L
        assertEquals(AssetStatus.ACTIVE, archive.unarchive(AssetId("a1")).status)
        assertFailsWith<NoSuchAsset> { archive.unarchive(AssetId("nope")) }
        assertFailsWith<NoSuchAsset> { archive.run(AssetId("nope")) }
        assertEquals(1, uow.commits)
    }
}
