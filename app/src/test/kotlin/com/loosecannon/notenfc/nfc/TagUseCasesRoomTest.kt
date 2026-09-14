package com.loosecannon.notenfc.nfc

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.nfc.NdefCodec
import com.loosecannon.notenfc.core.nfc.TagPayload
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UuidGenerator
import com.loosecannon.notenfc.core.usecase.BindTag
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.ResolveTag
import com.loosecannon.notenfc.core.usecase.Resolution
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.data.room.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Phase 1B use cases against the real schema: unique (format,key) lookup, FK targets, cleanup. */
class TagUseCasesRoomTest {

    @Test
    fun provisionWriteScanResolvesThroughRoom() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            val uow = RoomUnitOfWork(db)
            val clock = Clock { 42L }
            val provision = ProvisionTag(tags, assets, links, uow, UuidGenerator, clock)
            val resolve = ResolveTag(tags, assets, links, uow, clock)

            uow.write { assets.upsert(Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)) }
            val row = provision.begin(TagTarget.AssetTarget(AssetId("a1")), "lid")
            val onTag = NdefCodec.decode(NdefCodec.encodeV1(row.id))   // what the phone will read back
            provision.complete(row.id, "04aabbcc")

            val r = resolve.run(onTag)
            assertTrue(r.toString(), r is Resolution.OpenAsset)
            r as Resolution.OpenAsset
            assertEquals("Hot tub", r.asset.name)
            assertEquals(42L, r.tag.lastScannedAt)
            assertEquals("04aabbcc", r.tag.physicalUid)
        } finally {
            db.close()
        }
    }

    @Test
    fun abandonRemovesOnlyTheUnwrittenRow() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            val uow = RoomUnitOfWork(db)
            val provision = ProvisionTag(tags, assets, links, uow, UuidGenerator, Clock { 1L })
            val spare = provision.begin(TagTarget.None, null)
            val written = provision.complete(provision.begin(TagTarget.None, null).id, null)
            provision.abandon(spare.id)
            provision.abandon(written.id)
            assertNull(tags.get(spare.id))
            assertEquals(written, tags.get(written.id))
        } finally {
            db.close()
        }
    }

    @Test
    fun bindingAnUnknownLegacyTagThenRescanningFindsIt() = runTest {
        val db = inMemoryDb()
        try {
            val assets = RoomAssetRepository(db.assetDao())
            val tags = RoomTagRepository(db.nfcTagDao())
            val links = RoomLinkRepository(db.externalLinkDao())
            val uow = RoomUnitOfWork(db)
            val clock = Clock { 3L }
            val bind = BindTag(tags, assets, links, uow, UuidGenerator, clock)
            val resolve = ResolveTag(tags, assets, links, uow, clock)
            uow.write { assets.upsert(Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)) }

            assertEquals(Resolution.UnknownLegacy("63b37acf"), resolve.run(TagPayload.LegacyMd5("63b37acf")))
            val row = bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.AssetTarget(AssetId("a1")))
            assertEquals(TagStatus.ACTIVE, row.status)
            val r = resolve.run(TagPayload.LegacyMd5("63b37acf"))
            assertTrue(r.toString(), r is Resolution.OpenAsset)
            // binding again retargets the same row: still exactly one row for this payload
            bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.AssetTarget(AssetId("a1")), label = "old sticker")
            assertEquals(1, tags.all().size)
            assertEquals("old sticker", tags.all().single().label)
        } finally {
            db.close()
        }
    }
}
