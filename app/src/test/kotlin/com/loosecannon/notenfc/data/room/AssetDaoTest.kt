package com.loosecannon.notenfc.data.room

import com.loosecannon.notenfc.data.room.entities.AssetEntity
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
