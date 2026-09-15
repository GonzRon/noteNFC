package com.loosecannon.notenfc.data.room.dao

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Update
import com.loosecannon.notenfc.data.room.entities.AssetEventEntity
import com.loosecannon.notenfc.data.room.entities.ConsumableUsageEntity
import com.loosecannon.notenfc.data.room.entities.EventProfileEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementDefinitionEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementEntity
import com.loosecannon.notenfc.data.room.entities.ProfileConsumableEntity
import com.loosecannon.notenfc.data.room.entities.ProfileFieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DefinitionDao {
    /** See [AssetDao.upsert] for why this is not `@Upsert`. */
    @Transaction
    suspend fun upsert(e: MeasurementDefinitionEntity) {
        if (update(e) == 0) insert(e)
    }

    @Update suspend fun update(e: MeasurementDefinitionEntity): Int

    @Insert suspend fun insert(e: MeasurementDefinitionEntity)

    @Query("SELECT * FROM measurement_definition WHERE id = :id")
    suspend fun byId(id: String): MeasurementDefinitionEntity?

    @Query("SELECT * FROM measurement_definition WHERE asset_id = :assetId ORDER BY sort_order, id")
    suspend fun forAsset(assetId: String): List<MeasurementDefinitionEntity>

    @Query("SELECT * FROM measurement_definition ORDER BY id")
    suspend fun all(): List<MeasurementDefinitionEntity>

    @Query("SELECT * FROM measurement_definition WHERE asset_id = :assetId ORDER BY sort_order, id")
    fun observeForAsset(assetId: String): Flow<List<MeasurementDefinitionEntity>>

    @Query("DELETE FROM measurement_definition")
    suspend fun deleteAll()
}

/**
 * An [EventProfileEntity] with the two child tables that belong to it. The aggregate is loaded
 * whole — a profile without its fields is not a thing the domain has a name for.
 */
data class ProfileWithParts(
    @Embedded val profile: EventProfileEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["profile_id"])
    val fields: List<ProfileFieldEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["profile_id"])
    val consumables: List<ProfileConsumableEntity>,
)

@Dao
interface ProfileDao {
    /**
     * Aggregate upsert: the row, then its children replaced wholesale. Deleting the old children
     * and inserting the new ones is what makes a removed field actually disappear; the ids come
     * from the caller, so a field that survived the edit keeps its identity.
     */
    @Transaction
    suspend fun upsert(
        profile: EventProfileEntity,
        fields: List<ProfileFieldEntity>,
        consumables: List<ProfileConsumableEntity>,
    ) {
        if (update(profile) == 0) insert(profile)
        deleteFields(profile.id)
        deleteConsumables(profile.id)
        fields.forEach { insertField(it) }
        consumables.forEach { insertConsumable(it) }
    }

    @Update suspend fun update(e: EventProfileEntity): Int

    @Insert suspend fun insert(e: EventProfileEntity)

    @Insert suspend fun insertField(e: ProfileFieldEntity)

    @Insert suspend fun insertConsumable(e: ProfileConsumableEntity)

    @Query("DELETE FROM profile_field WHERE profile_id = :profileId")
    suspend fun deleteFields(profileId: String)

    @Query("DELETE FROM profile_consumable WHERE profile_id = :profileId")
    suspend fun deleteConsumables(profileId: String)

    @Transaction
    @Query("SELECT * FROM event_profile WHERE id = :id")
    suspend fun byId(id: String): ProfileWithParts?

    @Transaction
    @Query("SELECT * FROM event_profile WHERE asset_id = :assetId ORDER BY sort_order, id")
    suspend fun forAsset(assetId: String): List<ProfileWithParts>

    @Transaction
    @Query("SELECT * FROM event_profile ORDER BY id")
    suspend fun all(): List<ProfileWithParts>

    @Transaction
    @Query("SELECT * FROM event_profile WHERE asset_id = :assetId ORDER BY sort_order, id")
    fun observeForAsset(assetId: String): Flow<List<ProfileWithParts>>

    @Query("DELETE FROM event_profile")
    suspend fun deleteAll()
}

/** An [AssetEventEntity] with its measurements and consumable usages: the journal aggregate. */
data class EventWithParts(
    @Embedded val event: AssetEventEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val measurements: List<MeasurementEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["event_id"])
    val consumables: List<ConsumableUsageEntity>,
)

@Dao
interface EventDao {
    /** Aggregate upsert, same shape as [ProfileDao.upsert]. */
    @Transaction
    suspend fun upsert(
        event: AssetEventEntity,
        measurements: List<MeasurementEntity>,
        consumables: List<ConsumableUsageEntity>,
    ) {
        if (update(event) == 0) insert(event)
        deleteMeasurements(event.id)
        deleteConsumables(event.id)
        measurements.forEach { insertMeasurement(it) }
        consumables.forEach { insertConsumable(it) }
    }

    @Update suspend fun update(e: AssetEventEntity): Int

    @Insert suspend fun insert(e: AssetEventEntity)

    @Insert suspend fun insertMeasurement(e: MeasurementEntity)

    @Insert suspend fun insertConsumable(e: ConsumableUsageEntity)

    @Query("DELETE FROM measurement WHERE event_id = :eventId")
    suspend fun deleteMeasurements(eventId: String)

    @Query("DELETE FROM consumable_usage WHERE event_id = :eventId")
    suspend fun deleteConsumables(eventId: String)

    @Transaction
    @Query("SELECT * FROM asset_event WHERE id = :id")
    suspend fun byId(id: String): EventWithParts?

    // §4.1's ordering, expressed in SQL. `:core`'s EventChronology re-sorts anyway so the rule
    // has one owner; this keeps the database from handing back an order it would have to undo.
    @Transaction
    @Query(
        "SELECT * FROM asset_event WHERE asset_id = :assetId " +
            "ORDER BY occurred_on DESC, COALESCE(occurred_time,'00:00') DESC, created_at DESC, id DESC",
    )
    suspend fun forAsset(assetId: String): List<EventWithParts>

    @Transaction
    @Query("SELECT * FROM asset_event ORDER BY id")
    suspend fun all(): List<EventWithParts>

    @Transaction
    @Query(
        "SELECT * FROM asset_event WHERE asset_id = :assetId " +
            "ORDER BY occurred_on DESC, COALESCE(occurred_time,'00:00') DESC, created_at DESC, id DESC",
    )
    fun observeForAsset(assetId: String): Flow<List<EventWithParts>>

    @Transaction
    @Query("SELECT * FROM asset_event WHERE id = :id")
    fun observe(id: String): Flow<EventWithParts?>

    @Query("DELETE FROM asset_event WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM asset_event")
    suspend fun deleteAll()
}
