package com.loosecannon.notenfc.data.room

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v1 -> v2 upgrade, run on a real file over [BundledSQLiteDriver].
 *
 * Room ships `SQLiteDriverMigrationTestHelper`, but only in `room3-testing`'s **android** variant:
 * every constructor there takes an `Instrumentation` and loads the exported schema out of the
 * instrumentation context's assets, so it cannot run in a JVM unit test. There is no JVM variant
 * of that artefact at 3.0.3. So the same proof is assembled by hand, and it is not weaker: the v1
 * database is built from the *exported* `1.json` (its DDL and its `room_master_table` identity
 * hash, not a hand-copied guess), and the v2 side is opened through Room itself with
 * [MIGRATION_1_2] registered — which makes Room run the migration and then validate the result
 * against the compiled v2 schema, exactly what `runMigrationsAndValidate` does. A migration that
 * produced a column, index or foreign key the entities don't declare fails this test on open.
 */
class Migration1To2Test {

    @Test
    fun migratesAndKeepsPhase1Rows() = runTest {
        val file = File.createTempFile("notenfc-migrate", ".db").also { it.delete() }
        try {
            createVersion1(file)

            // Opening through Room is what runs MIGRATION_1_2 and validates the result.
            val db = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Default)
                .addMigrations(MIGRATION_1_2)
                .build()
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
                val tables = mutableSetOf<String>()
                c.prepare("SELECT name FROM sqlite_master WHERE type='table'").use { s ->
                    while (s.step()) tables += s.getText(0)
                }
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

    /** Builds the database phase 1 shipped, from the schema phase 1 exported. */
    private fun createVersion1(file: File) = withConnection(file) { c ->
        val schema = Json.parseToJsonElement(schemaFile(1).readText()).jsonObject
            .getValue("database").jsonObject
        schema.getValue("entities").jsonArray.forEach { element ->
            val entity = element.jsonObject
            val table = entity.getValue("tableName").jsonPrimitive.content
            c.execSQL(entity.sql("createSql", table))
            entity["indices"]?.jsonArray?.forEach { index ->
                c.execSQL(index.jsonObject.sql("createSql", table))
            }
        }
        schema.getValue("setupQueries").jsonArray.forEach { c.execSQL(it.jsonPrimitive.content) }
        c.execSQL("PRAGMA user_version = 1")

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

    private fun JsonObject.sql(key: String, table: String): String =
        getValue(key).jsonPrimitive.content.replace("\${TABLE_NAME}", table)

    private fun <T> withConnection(file: File, block: (SQLiteConnection) -> T): T =
        BundledSQLiteDriver().open(file.absolutePath).use(block)

    /**
     * `app/schemas/...`. Gradle runs JVM unit tests with the module directory as the working
     * directory, but an IDE run configuration may use the repository root, so both are tried.
     */
    private fun schemaFile(version: Int): File {
        val relative = "schemas/com.loosecannon.notenfc.data.room.AppDatabase/$version.json"
        return listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
            ?: error("cannot find the exported schema $relative from ${File(".").absolutePath}")
    }

    private companion object {
        val JOURNAL_TABLES = setOf(
            "measurement_definition",
            "event_profile",
            "profile_field",
            "profile_consumable",
            "asset_event",
            "measurement",
            "consumable_usage",
        )
    }
}
