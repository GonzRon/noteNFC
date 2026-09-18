package com.loosecannon.servicetag.data.room.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.loosecannon.servicetag.data.room.entities.ExternalLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalLinkDao {
    /** See [AssetDao.upsert] for why this is not `@Upsert`. */
    @Transaction
    suspend fun upsert(e: ExternalLinkEntity) {
        if (update(e) == 0) insert(e)
    }

    @Update suspend fun update(e: ExternalLinkEntity): Int

    @Insert suspend fun insert(e: ExternalLinkEntity)

    @Query("SELECT * FROM external_link WHERE id = :id")
    suspend fun byId(id: String): ExternalLinkEntity?

    @Query("SELECT * FROM external_link WHERE asset_id = :assetId ORDER BY label COLLATE NOCASE")
    suspend fun forAsset(assetId: String): List<ExternalLinkEntity>

    @Query("SELECT * FROM external_link WHERE asset_id IS NULL ORDER BY label COLLATE NOCASE")
    suspend fun standalone(): List<ExternalLinkEntity>

    @Query("SELECT * FROM external_link ORDER BY created_at")
    suspend fun all(): List<ExternalLinkEntity>

    @Query("SELECT * FROM external_link ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<ExternalLinkEntity>>

    @Query("SELECT * FROM external_link WHERE asset_id = :assetId ORDER BY label COLLATE NOCASE")
    fun observeForAsset(assetId: String): Flow<List<ExternalLinkEntity>>

    @Query("DELETE FROM external_link WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM external_link")
    suspend fun deleteAll()
}
