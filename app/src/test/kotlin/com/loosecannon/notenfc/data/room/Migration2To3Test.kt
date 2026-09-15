package com.loosecannon.notenfc.data.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.loosecannon.notenfc.core.model.DefinitionId
import com.loosecannon.notenfc.core.model.DefinitionKind
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MIGRATION_2_3]: the table-recreate that gives `measurement_definition` its DERIVED columns
 * (spec §7). A v2 database is built from the exported `2.json`, seeded with the rows a 2A install
 * would really have — an asset, a definition, an event, a reading naming that definition — and
 * then opened through Room, which runs the migration and validates the result against the
 * compiled v3 schema. See [openMigrated] for why the proof is assembled this way.
 *
 * What is actually at risk in a recreate is the data and the things that point at it, so that is
 * what is asserted: the definition row comes back with `kind = 'ENTERED'` and no sources, the
 * reading is still attached to it by `definition_id`, and the four v3 indexes exist on the new
 * table (the two the table always had, which the DROP took with it, plus one per source column).
 */
class Migration2To3Test {

    @Test
    fun migratesAndKeepsJournalRows() = runTest {
        val file = File.createTempFile("notenfc-migrate-2-3", ".db").also { it.delete() }
        try {
            createSchemaVersion(2, file, ::seedPhase2a)

            val db = openMigrated(file)
            try {
                val definition = db.definitionDao().byId("d-ph")
                assertEquals("ph", definition?.key)
                assertEquals("pH", definition?.label)
                assertEquals(7.2, definition?.rangeLow!!, 0.0)

                // Every definition that existed before this phase was entered by hand.
                assertEquals("ENTERED", definition.kind)
                assertNull(definition.formula)
                assertNull(definition.sourceAId)
                assertNull(definition.sourceBId)

                // And the domain reads that back as the kind it is, with no spec attached.
                val domain = RoomDefinitionRepository(db.definitionDao()).get(DefinitionId("d-ph"))!!
                assertEquals(DefinitionKind.ENTERED, domain.kind)
                assertNull(domain.derived)

                // The reading survives, still pointing at the definition through the recreated
                // table: the RESTRICT foreign key on `measurement` was not left dangling.
                val event = db.eventDao().byId("e1")!!
                assertEquals(listOf("d-ph"), event.measurements.map { it.definitionId })
                assertEquals(7.4, event.measurements.single().valueNum!!, 0.0)
                assertEquals(1, db.eventDao().countMeasurementsFor("d-ph"))
            } finally {
                db.close()
            }

            withConnection(file) { c ->
                assertTrue(
                    "the recreated table must keep the whole journal",
                    c.tableNames().containsAll(JOURNAL_TABLES),
                )
                assertTrue(
                    "the scaffolding table must be gone",
                    "_new_measurement_definition" !in c.tableNames(),
                )
                assertEquals(
                    setOf("kind", "formula", "source_a_id", "source_b_id"),
                    c.columnNamesOf("measurement_definition") - V2_DEFINITION_COLUMNS,
                )
                assertTrue(
                    "v3's indexes must exist on the new table, found " +
                        "${c.indexNamesOn("measurement_definition")}",
                    c.indexNamesOn("measurement_definition").containsAll(V3_DEFINITION_INDEXES),
                )
            }
        } finally {
            file.delete()
        }
    }

    /** What a 2A install has on disk: one asset with one definition, one event, one reading. */
    private fun seedPhase2a(c: SQLiteConnection) {
        c.execSQL(
            "INSERT INTO asset (id,name,description,category,notes,status,template_key," +
                "created_at,updated_at) " +
                "VALUES ('a1','Hot tub','','','','ACTIVE','hot_tub',1,1)",
        )
        c.execSQL(
            "INSERT INTO measurement_definition (id,asset_id,`key`,label,unit,value_type,decimals," +
                "range_low,range_high,is_meter,sort_order,archived_at,created_at,updated_at) " +
                "VALUES ('d-ph','a1','ph','pH','','NUMBER',1,7.2,7.8,0,0,NULL,1,1)",
        )
        c.execSQL(
            "INSERT INTO asset_event (id,asset_id,kind,title,profile_id,occurred_on,occurred_time," +
                "tz_id,notes,source,source_ref,created_at,updated_at) " +
                "VALUES ('e1','a1','MEASUREMENT','Water test',NULL,'2026-09-14','08:30','UTC','',"
                + "'MANUAL','e1',1,1)",
        )
        c.execSQL(
            "INSERT INTO measurement (id,event_id,definition_id,value_num,value_text,unit,sort_order) " +
                "VALUES ('m1','e1','d-ph',7.4,NULL,'',0)",
        )
    }

    private companion object {
        val V2_DEFINITION_COLUMNS = setOf(
            "id", "asset_id", "key", "label", "unit", "value_type", "decimals",
            "range_low", "range_high", "is_meter", "sort_order", "archived_at",
            "created_at", "updated_at",
        )
        val V3_DEFINITION_INDEXES = setOf(
            "index_measurement_definition_asset_id",
            "index_measurement_definition_asset_id_key",
            "index_measurement_definition_source_a_id",
            "index_measurement_definition_source_b_id",
        )
    }
}
