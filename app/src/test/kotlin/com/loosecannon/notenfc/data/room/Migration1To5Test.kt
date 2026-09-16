package com.loosecannon.notenfc.data.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentMode
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.StorageProvider
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The continuous upgrade proof, now four steps long: a database from the *first* shipped version,
 * carried to v5 through all four migrations in one open, the way a phone that skipped three
 * releases will do it. No single step is re-proved here — [Migration1To2Test], [Migration2To3Test],
 * [Migration3To4Test] and [Migration4To5Test] each own one — what is proved is that they compose:
 * the phase 1 rows are still there and still resolve to each other afterwards, and the table the
 * last step adds arrives on top of a file that has been rewritten twice on the way.
 *
 * `MIGRATION_4_5` is additive, so the delicate part is not its own SQL but where it lands: the
 * `asset` and `asset_event` tables its foreign keys name are the ones `MIGRATION_3_4`'s recreate
 * left behind. If those clauses had followed the dropped table instead of the name, the write at
 * the end of this test would fail, or Room's `foreign_key_check` would have refused the open.
 */
class Migration1To5Test {

    @Test
    fun v1UpgradesThroughAllFourMigrations() = runTest {
        val file = File.createTempFile("notenfc-migrate-1-5", ".db").also { it.delete() }
        try {
            createSchemaVersion(1, file, ::seedPhase1)

            val db = openMigrated(file)
            try {
                // Phase 1's rows are untouched by any of the four steps, and the asset arrives
                // with the v4 columns at their empty values.
                val spa = db.assetDao().byId("a1")!!
                assertEquals("Spa", spa.name)
                assertEquals("", spa.manufacturer)
                assertNull(spa.parentAssetId)
                assertNull(spa.retiredOn)

                // ...and they still resolve to each other through their foreign key columns.
                assertEquals(listOf("t1"), db.nfcTagDao().forAsset("a1").map { it.id })
                assertEquals(
                    listOf(LinkId("l1")),
                    RoomLinkRepository(db.externalLinkDao()).forAsset(AssetId("a1")).map { it.id },
                )

                // The table v5 adds is there, empty, and takes a row against the carried asset.
                val repo = RoomAttachmentRepository(db.attachmentDao())
                assertEquals(0, repo.count())
                repo.upsert(
                    Attachment(
                        id = AttachmentId("att-1"),
                        owner = AttachmentOwner.OfAsset(AssetId("a1")),
                        kind = AttachmentKind.MANUAL,
                        mode = AttachmentMode.MANAGED,
                        displayName = "spa-manual.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 12L,
                        sha256 = "a".repeat(64),
                        storageProvider = StorageProvider.SAF_TREE,
                        storageLocator = "assets/a1/att-1.pdf",
                        capturedOn = null,
                        notes = "",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                )
                assertEquals(
                    listOf(AttachmentId("att-1")),
                    repo.forAsset(AssetId("a1")).map { it.id },
                )

                // And the cascade into the table everything hangs off still bites after the chain.
                db.assetDao().delete("a1")
                assertEquals(0, repo.count())
            } finally {
                db.close()
            }

            withConnection(file) { c ->
                assertTrue(
                    "every journal table must exist after the chain, found ${c.tableNames()}",
                    c.tableNames().containsAll(JOURNAL_TABLES),
                )
                assertTrue("attachment" in c.tableNames())
                assertTrue(
                    "the v4 asset columns must be there, found ${c.columnNamesOf("asset")}",
                    c.columnNamesOf("asset").containsAll(
                        setOf("parent_asset_id", "retired_on", "purchase_price_minor", "currency"),
                    ),
                )
                assertTrue(
                    "v5's indexes must exist, found ${c.indexNamesOn("attachment")}",
                    c.indexNamesOn("attachment").containsAll(
                        setOf(
                            "index_attachment_asset_id",
                            "index_attachment_event_id",
                            "index_attachment_storage_provider_storage_locator",
                        ),
                    ),
                )
            }
        } finally {
            file.delete()
        }
    }

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
        c.execSQL(
            "INSERT INTO external_link (id,asset_id,kind,label,uri,created_at,last_opened_at," +
                "updated_at) " +
                "VALUES ('l1','a1','WEB','Manual','https://example.invalid/spa.pdf',1,NULL,1)",
        )
    }
}
