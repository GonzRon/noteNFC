package com.loosecannon.notenfc.data.room

import android.database.SQLException
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.data.room.entities.AssetEntity
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The continuous upgrade proof, now three steps long: a database from the *first* shipped version,
 * carried to v4 through all three migrations in one open, the way a phone that skipped two releases
 * will do it. No single step is re-proved here — [Migration1To2Test], [Migration2To3Test] and
 * [Migration3To4Test] each own one — what is proved is that they compose, and that the table
 * `MIGRATION_3_4` drops and renames is still the table everything else refers to afterwards.
 *
 * That is the genuinely delicate part of recreating `asset`, because `nfc_tag`, `external_link`,
 * `measurement_definition`, `event_profile` and `asset_event` all name it. Room turns foreign keys
 * off for the duration of a migration, so those clauses keep naming `asset` across the DROP and the
 * RENAME rather than following the table that went away — the name comes back attached to the new
 * table. If that reasoning were wrong, the writes at the end of this test would fail, or Room's own
 * `foreign_key_check` after the migration would have refused to open the file at all.
 */
class Migration1To4Test {

    @Test
    fun v1UpgradesThroughAllThreeMigrations() = runTest {
        val file = File.createTempFile("notenfc-migrate-1-4", ".db").also { it.delete() }
        try {
            createSchemaVersion(1, file, ::seedPhase1)

            val db = openMigrated(file)
            try {
                // Phase 1's rows are untouched by any of the three steps, and the asset arrives
                // with the v4 columns at their empty values.
                val spa = db.assetDao().byId("a1")!!
                assertEquals("Spa", spa.name)
                assertEquals("", spa.manufacturer)
                assertNull(spa.parentAssetId)
                assertNull(spa.retiredOn)
                assertEquals(listOf("t1"), db.nfcTagDao().forAsset("a1").map { it.id })

                // The recreated table takes writes, including a child that points at the row the
                // recreate carried over: the self-foreign-key resolves against the renamed table.
                db.assetDao().upsert(child("a2", "Cover lifter", parent = "a1"))
                assertEquals("a1", db.assetDao().byId("a2")?.parentAssetId)

                // ...and RESTRICT bites on the other side of it. Only the type is asserted: under
                // AGP's mockable android.jar `SQLException.getMessage()` is always null.
                val thrown = runCatching { db.assetDao().delete("a1") }.exceptionOrNull()
                assertTrue(
                    "a parent with a child must still be undeletable after the chain, got $thrown",
                    thrown is SQLException,
                )

                // An unknown parent is refused too, which is the same FK speaking on insert.
                val orphan = runCatching {
                    db.assetDao().upsert(child("a3", "Nowhere", parent = "missing"))
                }.exceptionOrNull()
                assertTrue("an unknown parent must be refused, got $orphan", orphan is SQLException)

                // The repository's children-first wipe clears the whole tree the ordinary way.
                val repo = RoomAssetRepository(db.assetDao())
                db.nfcTagDao().deleteAll()
                repo.deleteAll()
                assertNull(repo.get(AssetId("a1")))
                assertEquals(0, db.assetDao().all().size)
            } finally {
                db.close()
            }

            withConnection(file) { c ->
                assertTrue(
                    "every journal table must exist after the chain, found ${c.tableNames()}",
                    c.tableNames().containsAll(JOURNAL_TABLES),
                )
                assertTrue(
                    "the v4 asset columns must be there, found ${c.columnNamesOf("asset")}",
                    c.columnNamesOf("asset").containsAll(
                        setOf("parent_asset_id", "retired_on", "purchase_price_minor", "currency"),
                    ),
                )
                assertTrue(
                    "the parent index must be there, found ${c.indexNamesOn("asset")}",
                    "index_asset_parent_asset_id" in c.indexNamesOn("asset"),
                )
            }
        } finally {
            file.delete()
        }
    }

    private fun child(id: String, name: String, parent: String) = AssetEntity(
        id = id, name = name, description = "", category = "", notes = "", status = "ACTIVE",
        templateKey = null, createdAt = 1L, updatedAt = 1L, parentAssetId = parent,
    )

    /** The rows phase 1 could have on disk, before the journal or the asset record existed. */
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
