package com.loosecannon.notenfc.data.room

import android.database.SQLException
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.data.room.entities.AssetEntity
import com.loosecannon.notenfc.data.room.entities.AssetEventEntity
import com.loosecannon.notenfc.data.room.entities.ConsumableUsageEntity
import com.loosecannon.notenfc.data.room.entities.EventProfileEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementDefinitionEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementEntity
import com.loosecannon.notenfc.data.room.entities.ProfileConsumableEntity
import com.loosecannon.notenfc.data.room.entities.ProfileFieldEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journal tables at the DAO level: aggregate upserts really replace their children, the SQL
 * ordering is §4.1's, and the foreign keys behave the way schema v2 says they must — CASCADE from
 * the asset down, RESTRICT in front of a definition that has readings against it.
 */
class JournalDaoTest {

    private fun asset(id: String) = AssetEntity(
        id = id, name = "Asset $id", description = "", category = "", notes = "",
        status = "ACTIVE", templateKey = null, createdAt = 1L, updatedAt = 1L,
    )

    private fun definition(id: String, assetId: String, key: String) = MeasurementDefinitionEntity(
        id = id, assetId = assetId, key = key, label = key, unit = "ppm",
        valueType = "NUMBER", decimals = 1, rangeLow = null, rangeHigh = null,
        isMeter = false, sortOrder = 0, archivedAt = null, createdAt = 1L, updatedAt = 1L,
    )

    private fun profile(id: String, assetId: String, sortOrder: Int = 0) = EventProfileEntity(
        id = id, assetId = assetId, name = "Water test", eventKind = "MEASUREMENT",
        defaultTitle = "Water test", templateKey = "hot_tub", sortOrder = sortOrder,
        archivedAt = null, createdAt = 1L, updatedAt = 1L,
    )

    private fun field(id: String, profileId: String, definitionId: String, sortOrder: Int) =
        ProfileFieldEntity(
            id = id, profileId = profileId, definitionId = definitionId,
            required = true, sortOrder = sortOrder,
        )

    private fun suggestion(id: String, profileId: String, name: String) = ProfileConsumableEntity(
        id = id, profileId = profileId, name = name, defaultQuantity = null,
        unit = "oz", sortOrder = 0,
    )

    private fun event(
        id: String,
        assetId: String,
        occurredOn: String = "2026-09-14",
        occurredTime: String? = null,
        createdAt: Long = 1L,
        profileId: String? = null,
    ) = AssetEventEntity(
        id = id, assetId = assetId, kind = "MEASUREMENT", title = "Water test",
        profileId = profileId, occurredOn = occurredOn, occurredTime = occurredTime,
        tzId = "UTC", notes = "", source = "MANUAL", sourceRef = id,
        createdAt = createdAt, updatedAt = createdAt,
    )

    private fun measurement(id: String, eventId: String, definitionId: String, value: Double) =
        MeasurementEntity(
            id = id, eventId = eventId, definitionId = definitionId,
            valueNum = value, valueText = null, unit = "ppm", sortOrder = 0,
        )

    private fun usage(id: String, eventId: String, name: String) = ConsumableUsageEntity(
        id = id, eventId = eventId, name = name, quantity = 1.0, unit = "oz", sortOrder = 0,
    )

    @Test
    fun profileUpsertReplacesChildren() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            db.definitionDao().upsert(definition("d1", "a1", "ph"))
            db.definitionDao().upsert(definition("d2", "a1", "chlorine"))
            val dao = db.profileDao()

            dao.upsert(
                profile("p1", "a1"),
                listOf(field("f1", "p1", "d1", 0), field("f2", "p1", "d2", 1)),
                listOf(suggestion("c1", "p1", "Chlorine")),
            )
            assertEquals(listOf("f1", "f2"), dao.byId("p1")!!.fields.sortedBy { it.sortOrder }.map { it.id })

            // The second write drops a field and a suggestion: the old rows must be gone, not merged.
            dao.upsert(
                profile("p1", "a1"),
                listOf(field("f2", "p1", "d2", 0)),
                emptyList(),
            )
            val after = dao.byId("p1")!!
            assertEquals(listOf("f2"), after.fields.map { it.id })
            assertEquals(emptyList<ProfileConsumableEntity>(), after.consumables)
        } finally {
            db.close()
        }
    }

    @Test
    fun eventUpsertReplacesChildrenAndObserveEmits() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            db.definitionDao().upsert(definition("d1", "a1", "ph"))
            db.definitionDao().upsert(definition("d2", "a1", "chlorine"))
            val dao = db.eventDao()

            dao.upsert(
                event("e1", "a1"),
                listOf(measurement("m1", "e1", "d1", 7.4), measurement("m2", "e1", "d2", 2.0)),
                listOf(usage("u1", "e1", "Chlorine")),
            )
            val first = dao.observeForAsset("a1").first().single()
            assertEquals(setOf("m1", "m2"), first.measurements.map { it.id }.toSet())
            assertEquals(listOf("u1"), first.consumables.map { it.id })

            dao.upsert(
                event("e1", "a1"),
                listOf(measurement("m2", "e1", "d2", 3.0)),
                emptyList(),
            )
            val second = dao.observeForAsset("a1").first().single()
            assertEquals(listOf("m2"), second.measurements.map { it.id })
            assertEquals(3.0, second.measurements.single().valueNum!!, 0.0)
            assertEquals(emptyList<ConsumableUsageEntity>(), second.consumables)
        } finally {
            db.close()
        }
    }

    /** The same three events [com.loosecannon.notenfc.core.journal.EventChronology] is built on. */
    @Test
    fun eventsComeNewestFirstByOccurrenceThenTimeThenCreated() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            val dao = db.eventDao()

            // Same day: one untimed (sorts as 00:00), one at 08:00; plus a later day.
            dao.upsert(event("e-untimed", "a1", "2026-09-14", null, createdAt = 50L), emptyList(), emptyList())
            dao.upsert(event("e-morning", "a1", "2026-09-14", "08:00", createdAt = 10L), emptyList(), emptyList())
            dao.upsert(event("e-later-day", "a1", "2026-09-15", null, createdAt = 1L), emptyList(), emptyList())

            assertEquals(
                listOf("e-later-day", "e-morning", "e-untimed"),
                dao.forAsset("a1").map { it.event.id },
            )

            // And the repository's flow agrees, having re-sorted with EventChronology.
            assertEquals(
                listOf("e-later-day", "e-morning", "e-untimed"),
                RoomEventRepository(dao).observeForAsset(AssetId("a1")).first().map { it.id.value },
            )
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingAnAssetCascadesToJournalRows() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            db.definitionDao().upsert(definition("d1", "a1", "ph"))
            db.profileDao().upsert(
                profile("p1", "a1"),
                listOf(field("f1", "p1", "d1", 0)),
                listOf(suggestion("c1", "p1", "Chlorine")),
            )
            db.eventDao().upsert(
                event("e1", "a1", profileId = "p1"),
                listOf(measurement("m1", "e1", "d1", 7.4)),
                listOf(usage("u1", "e1", "Chlorine")),
            )

            db.assetDao().delete("a1")

            assertEquals(emptyList<MeasurementDefinitionEntity>(), db.definitionDao().all())
            assertEquals(emptyList<Any>(), db.profileDao().all())
            assertEquals(emptyList<Any>(), db.eventDao().all())
            assertNull(db.eventDao().byId("e1"))
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingADefinitionWithDataIsRefused() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            db.definitionDao().upsert(definition("d1", "a1", "ph"))
            db.eventDao().upsert(
                event("e1", "a1"),
                listOf(measurement("m1", "e1", "d1", 7.4)),
                emptyList(),
            )

            // RESTRICT: readings are history, and history does not vanish because a row was tidied.
            // Only the type is asserted, not the message: under AGP's mockable `android.jar`
            // `SQLException.getMessage()` is always null (the same quirk `AssetDao.upsert`
            // documents), so "FOREIGN KEY constraint failed" never reaches the test.
            val thrown = runCatching { db.definitionDao().deleteAll() }.exceptionOrNull()
            assertTrue(
                "expected the RESTRICT foreign key to refuse, got $thrown",
                thrown is SQLException,
            )
            assertEquals(1, db.definitionDao().all().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun uniqueDefinitionKeyPerAsset() = runTest {
        val db = inMemoryDb()
        try {
            db.assetDao().upsert(asset("a1"))
            db.assetDao().upsert(asset("a2"))
            db.definitionDao().upsert(definition("d1", "a1", "ph"))

            // The same key under a different asset is fine.
            db.definitionDao().upsert(definition("d2", "a2", "ph"))
            assertEquals(2, db.definitionDao().all().size)

            val thrown = runCatching { db.definitionDao().upsert(definition("d3", "a1", "ph")) }
                .exceptionOrNull()
            assertTrue(
                "expected the unique (asset_id, key) index to refuse, got $thrown",
                thrown is SQLException,
            )
            assertEquals(2, db.definitionDao().all().size)
        } finally {
            db.close()
        }
    }
}
