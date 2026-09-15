package com.loosecannon.notenfc.data.room.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.loosecannon.notenfc.data.room.entities.AssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {
    /**
     * Update-then-insert upsert. Room's `@Upsert` cannot be used: its
     * [androidx.room3.EntityUpsertAdapter] recognises the constraint violation by reading
     * `android.database.SQLException.getMessage()`, and under AGP's mockable `android.jar`
     * that message is always null, so every `@Upsert` of an existing row rethrows in JVM
     * unit tests. `INSERT OR REPLACE` is also unusable here: a REPLACE deletes the old row
     * first, which fires `external_link` CASCADE and `nfc_tag` SET NULL.
     */
    @Transaction
    suspend fun upsert(e: AssetEntity) {
        if (update(e) == 0) insert(e)
    }

    @Update suspend fun update(e: AssetEntity): Int

    @Insert suspend fun insert(e: AssetEntity)

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun byId(id: String): AssetEntity?

    @Query("SELECT * FROM asset ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<AssetEntity>

    @Query("SELECT * FROM asset ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, name COLLATE NOCASE")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("DELETE FROM asset WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM asset")
    suspend fun deleteAll()
}
