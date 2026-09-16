package com.loosecannon.servicetag.data.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MIGRATION_1_2] — the journal's arrival (spec 8) — run on a real file over the bundled driver.
 * See [openMigrated] for why the proof is assembled by hand rather than with Room's own migration
 * test helper.
 *
 * The compiled database is three versions ahead of a v1 file, so opening one runs the whole
 * registered chain: there is no way to ask Room to stop at v2, and pretending otherwise would
 * only mean not validating at all. The assertions here are still v1 -> v2's own question — do the
 * phase-1 rows survive, do the seven journal tables arrive — and [Migration1To4Test] owns what the
 * far end of the chain must look like.
 */
class Migration1To2Test {

    @Test
    fun migratesAndKeepsPhase1Rows() = runTest {
        val file = File.createTempFile("notenfc-migrate", ".db").also { it.delete() }
        try {
            createSchemaVersion(1, file, ::seedPhase1)

            // Opening through Room is what runs the migrations and validates the result.
            val db = openMigrated(file)
            try {
                val asset = db.assetDao().byId("a1")
                assertEquals("Spa", asset?.name)
                assertEquals(null, asset?.templateKey)
                assertEquals(1, db.nfcTagDao().all().size)
                // The journal starts empty but usable: the new tables are really there.
                assertEquals(emptyList<Any>(), db.definitionDao().all())
            } finally {
                db.close()
            }

            withConnection(file) { c ->
                val tables = c.tableNames()
                assertTrue(
                    "every journal table must exist after the migration, found $tables",
                    tables.containsAll(JOURNAL_TABLES),
                )
                c.prepare("SELECT COUNT(*) FROM nfc_tag").use { s ->
                    s.step()
                    assertEquals(1L, s.getLong(0))
                }
                c.prepare("SELECT template_key FROM asset WHERE id='a1'").use { s ->
                    s.step()
                    assertTrue("template_key must arrive null for a pre-2A asset", s.isNull(0))
                }
            }
        } finally {
            file.delete()
        }
    }

    /** The rows phase 1 could have on disk: one asset, one tag pointing at it. */
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
