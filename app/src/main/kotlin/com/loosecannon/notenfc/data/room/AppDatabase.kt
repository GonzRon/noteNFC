package com.loosecannon.notenfc.data.room

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.loosecannon.notenfc.data.room.dao.AssetDao
import com.loosecannon.notenfc.data.room.dao.DefinitionDao
import com.loosecannon.notenfc.data.room.dao.EventDao
import com.loosecannon.notenfc.data.room.dao.ExternalLinkDao
import com.loosecannon.notenfc.data.room.dao.NfcTagDao
import com.loosecannon.notenfc.data.room.dao.ProfileDao
import com.loosecannon.notenfc.data.room.entities.AssetEntity
import com.loosecannon.notenfc.data.room.entities.AssetEventEntity
import com.loosecannon.notenfc.data.room.entities.ConsumableUsageEntity
import com.loosecannon.notenfc.data.room.entities.EventProfileEntity
import com.loosecannon.notenfc.data.room.entities.ExternalLinkEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementDefinitionEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementEntity
import com.loosecannon.notenfc.data.room.entities.NfcTagEntity
import com.loosecannon.notenfc.data.room.entities.ProfileConsumableEntity
import com.loosecannon.notenfc.data.room.entities.ProfileFieldEntity

@Database(
    entities = [
        AssetEntity::class,
        NfcTagEntity::class,
        ExternalLinkEntity::class,
        MeasurementDefinitionEntity::class,
        EventProfileEntity::class,
        ProfileFieldEntity::class,
        ProfileConsumableEntity::class,
        AssetEventEntity::class,
        MeasurementEntity::class,
        ConsumableUsageEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assetDao(): AssetDao
    abstract fun nfcTagDao(): NfcTagDao
    abstract fun externalLinkDao(): ExternalLinkDao
    abstract fun definitionDao(): DefinitionDao
    abstract fun profileDao(): ProfileDao
    abstract fun eventDao(): EventDao
}
