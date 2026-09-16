package com.loosecannon.servicetag.nfc

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.PayloadFormat
import com.loosecannon.servicetag.core.model.TagStatus
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.core.nfc.TagPayload
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.UuidGenerator
import com.loosecannon.servicetag.core.usecase.BindTag
import com.loosecannon.servicetag.core.usecase.ProvisionTag
import com.loosecannon.servicetag.core.usecase.ResolveTag
import com.loosecannon.servicetag.core.usecase.Resolution
import com.loosecannon.servicetag.data.room.RoomAssetRepository
import com.loosecannon.servicetag.data.room.RoomLinkRepository
import com.loosecannon.servicetag.data.room.RoomTagRepository
import com.loosecannon.servicetag.data.room.RoomUnitOfWork
import com.loosecannon.servicetag.data.room.inMemoryDb
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
            // two clocks, so `lastScannedAt` cannot pass by coinciding with the write time
            val provision = ProvisionTag(tags, assets, links, uow, UuidGenerator, Clock { 42L })
            val resolve = ResolveTag(tags, assets, links, uow, Clock { 43L })

            uow.write { assets.upsert(Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)) }
            val row = provision.begin(TagTarget.AssetTarget(AssetId("a1")), "lid")
            val onTag = NdefCodec.decode(NdefCodec.encodeV1(row.id))   // what the phone will read back
            provision.complete(row.id, "04aabbcc")

            val r = resolve.run(onTag)
            assertTrue(r.toString(), r is Resolution.OpenAsset)
            r as Resolution.OpenAsset
            assertEquals("Hot tub", r.asset.name)
            assertEquals(42L, r.tag.writtenAt)
            assertEquals(43L, r.tag.lastScannedAt)
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
            val bind = BindTag(tags, assets, links, uow, UuidGenerator, Clock { 42L })
            val resolve = ResolveTag(tags, assets, links, uow, Clock { 43L })
            uow.write { assets.upsert(Asset(AssetId("a1"), "Hot tub", createdAt = 1L, updatedAt = 1L)) }

            assertEquals(Resolution.UnknownLegacy("63b37acf"), resolve.run(TagPayload.LegacyMd5("63b37acf")))
            val row = bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.AssetTarget(AssetId("a1")))
            assertEquals(TagStatus.ACTIVE, row.status)
            val r = resolve.run(TagPayload.LegacyMd5("63b37acf"))
            assertTrue(r.toString(), r is Resolution.OpenAsset)
            r as Resolution.OpenAsset
            assertEquals(43L, r.tag.lastScannedAt)
            // binding again retargets the same row: still exactly one row for this payload
            bind.run(PayloadFormat.LEGACY_MD5, "63b37acf", TagTarget.AssetTarget(AssetId("a1")), label = "old sticker")
            assertEquals(1, tags.all().size)
            assertEquals("old sticker", tags.all().single().label)
        } finally {
            db.close()
        }
    }
}
