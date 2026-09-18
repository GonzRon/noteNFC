package com.loosecannon.servicetag.nfc

import com.loosecannon.nfc.tagcore.TagIdentity
import com.loosecannon.servicetag.BuildConfig
import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.TagTarget
import com.loosecannon.servicetag.core.nfc.NdefCodec
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.UuidGenerator
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

    /**
     * This file builds its repositories by hand rather than through `FakeGraph`, so it builds the
     * codec the same way the app does: from the Gradle-owned BuildConfig fields (C9).
     */
    private val ndefCodec = NdefCodec(
        TagIdentity(
            externalDomain = BuildConfig.NDEF_EXTERNAL_DOMAIN,
            typeName = BuildConfig.NDEF_TYPE_NAME,
            aarPackage = BuildConfig.NDEF_AAR_PACKAGE,
        ),
    )

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
            val onTag = ndefCodec.decode(ndefCodec.encodeV1(row.id))   // what the phone will read back
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
}
