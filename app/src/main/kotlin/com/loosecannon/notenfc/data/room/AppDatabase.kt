package com.loosecannon.notenfc.data.room

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.loosecannon.notenfc.data.room.dao.AssetDao
import com.loosecannon.notenfc.data.room.dao.ExternalLinkDao
import com.loosecannon.notenfc.data.room.dao.NfcTagDao
import com.loosecannon.notenfc.data.room.entities.AssetEntity
import com.loosecannon.notenfc.data.room.entities.ExternalLinkEntity
import com.loosecannon.notenfc.data.room.entities.NfcTagEntity

@Database(
    entities = [AssetEntity::class, NfcTagEntity::class, ExternalLinkEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun nfcTagDao(): NfcTagDao
    abstract fun externalLinkDao(): ExternalLinkDao
}
