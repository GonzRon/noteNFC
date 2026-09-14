package com.loosecannon.notenfc.data.room

import com.loosecannon.notenfc.data.room.entities.AssetEntity
import com.loosecannon.notenfc.data.room.entities.ExternalLinkEntity
import com.loosecannon.notenfc.data.room.entities.NfcTagEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssetDaoTest {

    private fun asset(id: String, name: String) = AssetEntity(
        id = id,
        name = name,
        description = "desc-$id",
        category = "cat",
        notes = "",
        status = "ACTIVE",
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    @Test
    fun upsertThenByIdRoundTrips() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.assetDao()
            val a = asset("a1", "Hot tub")
            dao.upsert(a)
            assertEquals(a, dao.byId("a1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun upsertReplacesExistingRow() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.assetDao()
            dao.upsert(asset("a1", "Hot tub"))
            val updated = asset("a1", "Hot tub").copy(name = "Spa", updatedAt = 9_000L)
            dao.upsert(updated)
            assertEquals(updated, dao.byId("a1"))
            assertEquals(1, dao.all().size)
        } finally {
            db.close()
        }
    }

    /**
     * The upsert ruling: an update of an existing row must not delete and re-insert it. A REPLACE
     * would, and the delete would fire `external_link` CASCADE and `nfc_tag` SET NULL, quietly
     * unbinding every child. This is the test that would fail if anyone "simplified" the DAO back
     * to `@Upsert` or `INSERT OR REPLACE`.
     */
    @Test
    fun upsertOfAnExistingAssetKeepsItsChildrenBound() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1", "Hot tub"))
            db.externalLinkDao().upsert(
                ExternalLinkEntity(
                    id = "l1", assetId = "a1", kind = "WEB", label = "Manual",
                    uri = "https://example.invalid/l1", createdAt = 1L, lastOpenedAt = null,
                    updatedAt = 1L,
                ),
            )
            db.nfcTagDao().upsert(
                NfcTagEntity(
                    id = "t1", payloadFormat = "V1", payloadKey = "key-t1", assetId = "a1",
                    linkId = null, status = "ACTIVE", label = null, physicalUid = null,
                    writtenAt = null, lastScannedAt = null, createdAt = 1L, updatedAt = 1L,
                ),
            )

            db.assetDao().upsert(asset("a1", "Hot tub").copy(name = "Spa", updatedAt = 9_000L))

            assertEquals("Spa", db.assetDao().byId("a1")!!.name)
            assertEquals("a1", db.nfcTagDao().byId("t1")!!.assetId)
            assertEquals("a1", db.externalLinkDao().byId("l1")!!.assetId)
            assertEquals(listOf("t1"), db.nfcTagDao().forAsset("a1").map { it.id })
            assertEquals(listOf("l1"), db.externalLinkDao().forAsset("a1").map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun allIsSortedCaseInsensitively() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.assetDao()
            dao.upsert(asset("a1", "banana"))
            dao.upsert(asset("a2", "Apple"))
            dao.upsert(asset("a3", "cherry"))
            assertEquals(listOf("Apple", "banana", "cherry"), dao.all().map { it.name })
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteRemovesOneRowAndDeleteAllClearsTable() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.assetDao()
            dao.upsert(asset("a1", "One"))
            dao.upsert(asset("a2", "Two"))
            dao.delete("a1")
            assertNull(dao.byId("a1"))
            assertEquals(1, dao.all().size)
            dao.deleteAll()
            assertEquals(0, dao.all().size)
        } finally {
            db.close()
        }
    }
}
