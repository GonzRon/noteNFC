package com.loosecannon.servicetag.data.room

import androidx.room3.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * What the migration tests share: building an old database from the schema that shipped with it,
 * and opening the result through Room so Room runs the migrations and validates what they left
 * behind against the compiled schema.
 *
 * Room ships `SQLiteDriverMigrationTestHelper`, but only in `room3-testing`'s **android** variant:
 * every constructor there takes an `Instrumentation` and loads the exported schema out of the
 * instrumentation context's assets, so it cannot run in a JVM unit test. There is no JVM variant
 * of that artefact at 3.0.3, so the same proof is assembled by hand — and it is not weaker. The
 * old database is built from the *exported* schema (its DDL and its `room_master_table` identity
 * hash, not a hand-copied guess) and the new side is opened through Room with the migrations
 * registered, which is exactly what `runMigrationsAndValidate` does. A migration that produced a
 * column, index or foreign key the entities don't declare fails on open.
 */

/** Opens [file] over the bundled driver, outside Room, for raw schema and row questions. */
internal fun <T> withConnection(file: File, block: (SQLiteConnection) -> T): T =
    BundledSQLiteDriver().open(file.absolutePath).use(block)

/**
 * Builds the database schema [version] shipped — every table, every index, the `setupQueries`
 * that carry Room's identity hash, and `PRAGMA user_version` — then hands the connection to
 * [seed] for the rows the test wants to watch survive.
 */
internal fun createSchemaVersion(version: Int, file: File, seed: (SQLiteConnection) -> Unit) =
    withConnection(file) { c ->
        val schema = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject
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
        c.execSQL("PRAGMA user_version = $version")
        seed(c)
    }

/**
 * Opens [file] as the compiled [AppDatabase] with the whole migration chain registered. Opening
 * is what runs the migrations the file needs and validates the result; nothing here chooses which
 * ones run — the database's own `user_version` does.
 */
internal fun openMigrated(file: File): AppDatabase = Room
    .databaseBuilder<AppDatabase>(name = file.absolutePath)
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Default)
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
    .build()

internal fun SQLiteConnection.tableNames(): Set<String> = buildSet {
    prepare("SELECT name FROM sqlite_master WHERE type='table'").use { s ->
        while (s.step()) add(s.getText(0))
    }
}

internal fun SQLiteConnection.indexNamesOn(table: String): Set<String> = buildSet {
    prepare("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name=?").use { s ->
        s.bindText(1, table)
        while (s.step()) add(s.getText(0))
    }
}

internal fun SQLiteConnection.columnNamesOf(table: String): Set<String> = buildSet {
    prepare("SELECT name FROM pragma_table_info(?)").use { s ->
        s.bindText(1, table)
        while (s.step()) add(s.getText(0))
    }
}

private fun JsonObject.sql(key: String, table: String): String =
    getValue(key).jsonPrimitive.content.replace("\${TABLE_NAME}", table)

/**
 * `app/schemas/...`. Gradle runs JVM unit tests with the module directory as the working
 * directory, but an IDE run configuration may use the repository root, so both are tried.
 */
private fun schemaFile(version: Int): File {
    val relative = "schemas/com.loosecannon.servicetag.data.room.AppDatabase/$version.json"
    return listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
        ?: error("cannot find the exported schema $relative from ${File(".").absolutePath}")
}

/** The seven tables schema v2 added. */
internal val JOURNAL_TABLES = setOf(
    "measurement_definition",
    "event_profile",
    "profile_field",
    "profile_consumable",
    "asset_event",
    "measurement",
    "consumable_usage",
)
