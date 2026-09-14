package com.loosecannon.notenfc.data.room

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.data.room.entities.NfcTagEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RoomRepositoriesTest {

    private fun asset(id: String, name: String = "Asset $id") = Asset(
        id = AssetId(id),
        name = name,
        description = "a description",
        category = "Hot tub",
        notes = "some notes",
        status = AssetStatus.ARCHIVED,
        createdAt = 111L,
        updatedAt = 222L,
    )

    @Test
    fun assetRoundTripsThroughTheMappers() = runTest {
        val db = inMemoryDb()
        try {
            val repo = RoomAssetRepository(db.assetDao())
            val a = asset("a1")
            repo.upsert(a)
            assertEquals(a, repo.get(AssetId("a1")))
            assertEquals(listOf(a), repo.all())
            repo.delete(AssetId("a1"))
            assertNull(repo.get(AssetId("a1")))
        } finally {
            db.close()
        }
    }

    @Test
    fun assetDeleteAllClearsTheTable() = runTest {
        val db = inMemoryDb()
        try {
            val repo = RoomAssetRepository(db.assetDao())
            repo.upsert(asset("a1"))
            repo.upsert(asset("a2"))
            repo.deleteAll()
            assertEquals(emptyList<Asset>(), repo.all())
        } finally {
            db.close()
        }
    }

    @Test
    fun tagWithAssetTargetRoundTrips() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            assets.upsert(asset("a1"))
            val t = TagBinding(
                id = TagId("t1"),
                payloadFormat = PayloadFormat.LEGACY_MD5,
                payloadKey = "deadbeef",
                target = TagTarget.AssetTarget(AssetId("a1")),
                status = TagStatus.LOST,
                label = "Tag on cover",
                physicalUid = "04A1B2C3",
                writtenAt = 5L,
                lastScannedAt = 6L,
                createdAt = 1L,
                updatedAt = 2L,
            )
            tags.upsert(t)
            assertEquals(t, tags.get(TagId("t1")))
            assertEquals(listOf(t), tags.forAsset(AssetId("a1")))
            assertEquals(t, tags.findByPayload(PayloadFormat.LEGACY_MD5, "deadbeef"))
            assertNull(tags.findByPayload(PayloadFormat.V1, "deadbeef"))
        } finally {
            db.close()
        }
    }

    @Test
    fun tagWithLinkTargetAndNoneTargetRoundTrip() = runTest {
        val db = inMemoryDb()
        try {
            val links = RoomLinkRepository(db.externalLinkDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            links.upsert(
                ExternalLink(
                    id = LinkId("l1"), assetId = null, kind = LinkKind.OBSIDIAN,
                    label = "Note", uri = "obsidian://open?file=x",
                    createdAt = 1L, lastOpenedAt = null, updatedAt = 1L,
                ),
            )
            val bound = TagBinding(
                id = TagId("t1"), payloadFormat = PayloadFormat.V1, payloadKey = "t1",
                target = TagTarget.LinkTarget(LinkId("l1")),
                createdAt = 1L, updatedAt = 1L,
            )
            val unbound = TagBinding(
                id = TagId("t2"), payloadFormat = PayloadFormat.V1, payloadKey = "t2",
                target = TagTarget.None, status = TagStatus.UNBOUND,
                createdAt = 1L, updatedAt = 1L,
            )
            tags.upsert(bound)
            tags.upsert(unbound)
            assertEquals(bound, tags.get(TagId("t1")))
            assertEquals(unbound, tags.get(TagId("t2")))
            assertEquals(listOf(bound), tags.forLink(LinkId("l1")))
            assertEquals(2, tags.all().size)
            tags.delete(TagId("t1"))
            assertNull(tags.get(TagId("t1")))
            tags.deleteAll()
            assertEquals(emptyList<TagBinding>(), tags.all())
        } finally {
            db.close()
        }
    }

    @Test
    fun aTagRowCarryingBothTargetsIsRejected() {
        val both = NfcTagEntity(
            id = "t1", payloadFormat = "V1", payloadKey = "t1",
            assetId = "a1", linkId = "l1",
            status = "ACTIVE", label = null, physicalUid = null,
            writtenAt = null, lastScannedAt = null, createdAt = 1L, updatedAt = 1L,
        )
        val e = assertThrows(IllegalArgumentException::class.java) { both.requireAtMostOneTarget() }
        assertTrue("unexpected message: ${e.message}", (e.message ?: "").contains("asset_id"))
    }

    @Test
    fun aTagRowWithASingleTargetIsAccepted() {
        val onlyAsset = NfcTagEntity(
            id = "t1", payloadFormat = "V1", payloadKey = "t1",
            assetId = "a1", linkId = null,
            status = "ACTIVE", label = null, physicalUid = null,
            writtenAt = null, lastScannedAt = null, createdAt = 1L, updatedAt = 1L,
        )
        assertEquals(onlyAsset, onlyAsset.requireAtMostOneTarget())
        assertEquals(
            onlyAsset.copy(assetId = null),
            onlyAsset.copy(assetId = null).requireAtMostOneTarget(),
        )
    }

    @Test
    fun linkRoundTripsAndPartitionsStandaloneFromOwned() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            assets.upsert(asset("a1"))
            val owned = ExternalLink(
                id = LinkId("l1"), assetId = AssetId("a1"), kind = LinkKind.LOGSEQ,
                label = "Owned", uri = "logseq://graph/x?page=y",
                createdAt = 1L, lastOpenedAt = 7L, updatedAt = 2L,
            )
            val loose = ExternalLink(
                id = LinkId("l2"), assetId = null, kind = LinkKind.OTHER,
                label = "Loose", uri = "custom://thing",
                createdAt = 1L, lastOpenedAt = null, updatedAt = 2L,
            )
            links.upsert(owned)
            links.upsert(loose)
            assertEquals(owned, links.get(LinkId("l1")))
            assertEquals(listOf(owned), links.forAsset(AssetId("a1")))
            assertEquals(listOf(loose), links.standalone())
            assertEquals(2, links.all().size)
            links.delete(LinkId("l2"))
            assertNull(links.get(LinkId("l2")))
            links.deleteAll()
            assertEquals(emptyList<ExternalLink>(), links.all())
        } finally {
            db.close()
        }
    }

    @Test
    fun unitOfWorkRollsBackOnException() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val uow = RoomUnitOfWork(db)
            try {
                uow.write {
                    assets.upsert(asset("a1"))
                    throw IllegalStateException("boom")
                }
                fail("expected the block's exception to propagate")
            } catch (expected: IllegalStateException) {
                assertEquals("boom", expected.message)
            }
            assertNull(assets.get(AssetId("a1")))
            assertEquals(emptyList<Asset>(), assets.all())
        } finally {
            db.close()
        }
    }

    @Test
    fun unitOfWorkCommitsAndReturnsTheBlockValue() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val uow = RoomUnitOfWork(db)
            val result = uow.write {
                assets.upsert(asset("a1"))
                assets.upsert(asset("a2"))
                "done"
            }
            assertEquals("done", result)
            assertEquals(2, assets.all().size)
        } finally {
            db.close()
        }
    }
}
