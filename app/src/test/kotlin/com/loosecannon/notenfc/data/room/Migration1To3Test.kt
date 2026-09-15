package com.loosecannon.notenfc.data.room

import android.database.SQLException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.loosecannon.notenfc.data.room.entities.AssetEventEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementDefinitionEntity
import com.loosecannon.notenfc.data.room.entities.MeasurementEntity
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The continuous upgrade proof: a database from the *first* shipped version, carried all the way
 * to v3 through both migrations in one open, the way a phone that skipped the 2A release will do
 * it. Neither step is re-proved here — [Migration1To2Test] and [Migration2To3Test] each own one —
 * what is proved is that they compose, and that the table `MIGRATION_2_3` drops and renames is
 * still the table everything else refers to afterwards.
 *
 * That last part is the one genuinely delicate thing about a recreate. Room turns foreign keys off
 * for the duration of a migration, so `measurement` and `profile_field` keep naming
 * `measurement_definition` across the DROP and the RENAME rather than being rewritten to follow
 * the table that went away — the name comes back attached to the new table. If that reasoning were
 * wrong, the writes at the end of this test would fail or the RESTRICT would not bite.
 */
class Migration1To3Test {

    @Test
    fun v1UpgradesThroughBothMigrations() = runTest {
        val file = File.createTempFile("notenfc-migrate-1-3", ".db").also { it.delete() }
        try {
            createSchemaVersion(1, file, ::seedPhase1)

            val db = openMigrated(file)
            try {
                // Phase 1's rows are untouched by either step.
                assertEquals("Spa", db.assetDao().byId("a1")?.name)
                assertEquals(1, db.nfcTagDao().all().size)

                // v3's table takes writes — including a DERIVED row whose sources are foreign keys
                // into the table that was just recreated.
                db.definitionDao().upsert(definition("d-before", "before"))
                db.definitionDao().upsert(definition("d-after", "after"))
                db.definitionDao().upsert(
                    definition("d-drop", "drop").copy(
                        kind = "DERIVED",
                        formula = "PERCENT_DROP",
                        sourceAId = "d-before",
                        sourceBId = "d-after",
                    ),
                )
                assertEquals(3, db.definitionDao().all().size)

                // `measurement` still points at `measurement_definition` by name after the rename:
                // the insert resolves, and the RESTRICT in front of a definition with readings
                // against it still refuses. Only the type is asserted — under AGP's mockable
                // android.jar `SQLException.getMessage()` is always null.
                db.eventDao().upsert(
                    event("e1"),
                    listOf(
                        MeasurementEntity(
                            id = "m1", eventId = "e1", definitionId = "d-before",
                            valueNum = 7.4, valueText = null, unit = "", sortOrder = 0,
                        ),
                    ),
                    emptyList(),
                )
                assertEquals(1, db.eventDao().countMeasurementsFor("d-before"))
                val thrown = runCatching { db.definitionDao().delete("d-before") }.exceptionOrNull()
                assertTrue(
                    "a definition with readings must still be refused after the recreate, got $thrown",
                    thrown is SQLException,
                )
            } finally {
                db.close()
            }

            withConnection(file) { c ->
                assertTrue(
                    "every journal table must exist after the chain, found ${c.tableNames()}",
                    c.tableNames().containsAll(JOURNAL_TABLES),
                )
                assertTrue(
                    "the v3 definition columns must be there, found " +
                        "${c.columnNamesOf("measurement_definition")}",
                    c.columnNamesOf("measurement_definition")
                        .containsAll(setOf("kind", "formula", "source_a_id", "source_b_id")),
                )
            }
        } finally {
            file.delete()
        }
    }

    private fun definition(id: String, key: String) = MeasurementDefinitionEntity(
        id = id, assetId = "a1", key = key, label = key, unit = "", valueType = "NUMBER",
        decimals = 1, rangeLow = null, rangeHigh = null, isMeter = false, sortOrder = 0,
        archivedAt = null, createdAt = 1L, updatedAt = 1L,
        kind = "ENTERED", formula = null, sourceAId = null, sourceBId = null,
    )

    private fun event(id: String) = AssetEventEntity(
        id = id, assetId = "a1", kind = "MEASUREMENT", title = "Water test", profileId = null,
        occurredOn = "2026-09-14", occurredTime = null, tzId = "UTC", notes = "",
        source = "MANUAL", sourceRef = id, createdAt = 1L, updatedAt = 1L,
    )

    /** The rows phase 1 could have on disk, before the journal existed at all. */
    private fun seedPhase1(c: SQLiteConnection) {
        c.execSQL(
            "INSERT INTO asset (id,name,description,category,notes,status,created_at,updated_at) " +
                "VALUES ('a1','Spa','','','','ACTIVE',1,1)",
        )
        c.execSQL(
            "INSERT INTO nfc_tag (id,payload_format,payload_key,asset_id,link_id,status,label," +
                "physical_uid,written_at,last_scanned_at,created_at,updated_at) " +
                "VALUES ('t1','V1','t1','a1',NULL,'ACTIVE',NULL,NULL,NULL,NULL,1,1)",
        )
    }
}
