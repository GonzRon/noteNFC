package com.loosecannon.servicetag.data.room

import androidx.sqlite.SQLiteException
import com.loosecannon.servicetag.data.room.entities.AssetEntity
import com.loosecannon.servicetag.data.room.entities.ExternalLinkEntity
import com.loosecannon.servicetag.data.room.entities.NfcTagEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NfcTagDaoTest {

    private fun asset(id: String) = AssetEntity(
        id = id, name = "Asset $id", description = "", category = "", notes = "",
        status = "ACTIVE", templateKey = null, createdAt = 1L, updatedAt = 1L,
    )

    private fun link(id: String, assetId: String? = null) = ExternalLinkEntity(
        id = id, assetId = assetId, kind = "WEB", label = "Link $id", uri = "https://example.invalid/$id",
        createdAt = 1L, lastOpenedAt = null, updatedAt = 1L,
    )

    private fun tag(
        id: String,
        format: String = "V1",
        key: String = id,
        assetId: String? = null,
        linkId: String? = null,
    ) = NfcTagEntity(
        id = id, payloadFormat = format, payloadKey = key, assetId = assetId, linkId = linkId,
        status = "ACTIVE", label = null, physicalUid = null, writtenAt = null, lastScannedAt = null,
        createdAt = 1L, updatedAt = 1L,
    )

    @Test
    fun upsertThenByIdRoundTrips() = runTest {
        val db = inMemoryDb()
        try {
            val t = tag("t1")
            db.nfcTagDao().upsert(t)
            assertEquals(t, db.nfcTagDao().byId("t1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun payloadFormatAndKeyAreUniqueTogether() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.nfcTagDao()
            dao.upsert(tag(id = "t1", format = "LEGACY_MD5", key = "deadbeef"))
            try {
                dao.upsert(tag(id = "t2", format = "LEGACY_MD5", key = "deadbeef"))
                fail("expected a UNIQUE constraint violation on (payload_format, payload_key)")
            } catch (e: Exception) {
                // The driver reports this as androidx.sqlite.SQLiteException, which on Android is a
                // typealias for android.database.SQLException. Its message is NOT asserted: under
                // AGP's mockable android.jar the stubbed constructor drops it, so it is always null
                // in JVM unit tests.
                assertTrue("unexpected exception: $e", e is SQLiteException)
            }
            assertEquals(1, dao.all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun sameKeyUnderDifferentFormatIsAllowed() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.nfcTagDao()
            dao.upsert(tag(id = "t1", format = "LEGACY_MD5", key = "deadbeef"))
            dao.upsert(tag(id = "t2", format = "V1", key = "deadbeef"))
            assertEquals(2, dao.all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun byPayloadFindsTheRow() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.nfcTagDao()
            val t = tag(id = "t1", format = "LEGACY_MD5", key = "cafebabe")
            dao.upsert(t)
            assertEquals(t, dao.byPayload("LEGACY_MD5", "cafebabe"))
            assertNull(dao.byPayload("V1", "cafebabe"))
            assertNull(dao.byPayload("LEGACY_MD5", "nope"))
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingAssetSetsAssetIdNullAndKeepsTheTag() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            db.nfcTagDao().upsert(tag(id = "t1", assetId = "a1"))
            assertEquals(1, db.nfcTagDao().forAsset("a1").size)

            db.assetDao().delete("a1")

            assertEquals(0, db.nfcTagDao().forAsset("a1").size)
            val survivor = db.nfcTagDao().byId("t1")
            assertNotNull(survivor)
            assertNull(survivor!!.assetId)
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingLinkSetsLinkIdNullAndKeepsTheTag() = runTest {
        val db = inMemoryDb()
        try {
            db.externalLinkDao().upsert(link("l1"))
            db.nfcTagDao().upsert(tag(id = "t1", linkId = "l1"))
            assertEquals(1, db.nfcTagDao().forLink("l1").size)

            db.externalLinkDao().delete("l1")

            assertEquals(0, db.nfcTagDao().forLink("l1").size)
            val survivor = db.nfcTagDao().byId("t1")
            assertNotNull(survivor)
            assertNull(survivor!!.linkId)
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteAndDeleteAllWork() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.nfcTagDao()
            dao.upsert(tag("t1"))
            dao.upsert(tag("t2"))
            dao.delete("t1")
            assertNull(dao.byId("t1"))
            assertEquals(1, dao.all().size)
            dao.deleteAll()
            assertEquals(0, dao.all().size)
        } finally {
            db.close()
        }
    }
}
