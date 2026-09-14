package com.loosecannon.notenfc.data.room

import com.loosecannon.notenfc.data.room.entities.AssetEntity
import com.loosecannon.notenfc.data.room.entities.ExternalLinkEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalLinkDaoTest {

    private fun asset(id: String) = AssetEntity(
        id = id, name = "Asset $id", description = "", category = "", notes = "",
        status = "ACTIVE", createdAt = 1L, updatedAt = 1L,
    )

    private fun link(id: String, label: String, assetId: String? = null) = ExternalLinkEntity(
        id = id, assetId = assetId, kind = "JOPLIN", label = label,
        uri = "joplin://x-callback-url/openNote?id=$id",
        createdAt = 1L, lastOpenedAt = null, updatedAt = 1L,
    )

    @Test
    fun upsertThenByIdRoundTrips() = runTest {
        val db = inMemoryDb()
        try {
            val l = link("l1", "Manual")
            db.externalLinkDao().upsert(l)
            assertEquals(l, db.externalLinkDao().byId("l1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun standaloneAndForAssetPartitionTheRows() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            val dao = db.externalLinkDao()
            dao.upsert(link("l1", "beta", assetId = "a1"))
            dao.upsert(link("l2", "Alpha", assetId = "a1"))
            dao.upsert(link("l3", "loose note"))

            assertEquals(listOf("Alpha", "beta"), dao.forAsset("a1").map { it.label })
            assertEquals(listOf("l3"), dao.standalone().map { it.id })
            assertEquals(3, dao.all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingAssetCascadesItsLinks() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            val dao = db.externalLinkDao()
            dao.upsert(link("l1", "owned", assetId = "a1"))
            dao.upsert(link("l2", "standalone"))

            db.assetDao().delete("a1")

            assertNull(dao.byId("l1"))
            assertEquals(listOf("l2"), dao.all().map { it.id })
        } finally {
            db.close()
        }
    }

    @Test
    fun deleteAndDeleteAllWork() = runTest {
        val db = inMemoryDb()
        try {
            val dao = db.externalLinkDao()
            dao.upsert(link("l1", "one"))
            dao.upsert(link("l2", "two"))
            dao.delete("l1")
            assertNull(dao.byId("l1"))
            assertEquals(1, dao.all().size)
            dao.deleteAll()
            assertEquals(0, dao.all().size)
        } finally {
            db.close()
        }
    }
}
