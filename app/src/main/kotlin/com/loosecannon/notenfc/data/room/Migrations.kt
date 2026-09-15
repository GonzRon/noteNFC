package com.loosecannon.notenfc.data.room

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Schema v1 -> v2: the journal (spec 8). `asset` gains `template_key`, and the seven journal
 * tables arrive with the keys and indexes Room expects for them.
 *
 * Every statement below is the SQL Room itself generates for these entities, copied verbatim from
 * the exported `2.json` with `${'$'}{TABLE_NAME}` substituted. That is deliberate: Room validates the
 * result of a migration against the compiled schema and refuses to open a database whose tables
 * do not match byte for byte, so the migration and the schema have one source, not two. Tables
 * are created parents-first so the foreign keys resolve as they are declared.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `asset` ADD COLUMN `template_key` TEXT",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurement_definition` (`id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, `unit` TEXT NOT NULL, `value_type` TEXT NOT NULL, `decimals` INTEGER NOT NULL, `range_low` REAL, `range_high` REAL, `is_meter` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `archived_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`asset_id`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurement_definition_asset_id` ON `measurement_definition` (`asset_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_definition_asset_id_key` ON `measurement_definition` (`asset_id`, `key`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `event_profile` (`id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `name` TEXT NOT NULL, `event_kind` TEXT NOT NULL, `default_title` TEXT NOT NULL, `template_key` TEXT, `sort_order` INTEGER NOT NULL, `archived_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`asset_id`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_event_profile_asset_id` ON `event_profile` (`asset_id`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_field` (`id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `definition_id` TEXT NOT NULL, `required` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profile_id`) REFERENCES `event_profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`definition_id`) REFERENCES `measurement_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_profile_field_profile_id_definition_id` ON `profile_field` (`profile_id`, `definition_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_profile_field_definition_id` ON `profile_field` (`definition_id`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile_consumable` (`id` TEXT NOT NULL, `profile_id` TEXT NOT NULL, `name` TEXT NOT NULL, `default_quantity` REAL, `unit` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`profile_id`) REFERENCES `event_profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_profile_consumable_profile_id` ON `profile_consumable` (`profile_id`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `asset_event` (`id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `profile_id` TEXT, `occurred_on` TEXT NOT NULL, `occurred_time` TEXT, `tz_id` TEXT NOT NULL, `notes` TEXT NOT NULL, `source` TEXT NOT NULL, `source_ref` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`asset_id`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`profile_id`) REFERENCES `event_profile`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_asset_event_asset_id_occurred_on_created_at` ON `asset_event` (`asset_id` ASC, `occurred_on` DESC, `created_at` DESC)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_asset_event_source_source_ref` ON `asset_event` (`source`, `source_ref`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_asset_event_profile_id` ON `asset_event` (`profile_id`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `measurement` (`id` TEXT NOT NULL, `event_id` TEXT NOT NULL, `definition_id` TEXT NOT NULL, `value_num` REAL, `value_text` TEXT, `unit` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`event_id`) REFERENCES `asset_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`definition_id`) REFERENCES `measurement_definition`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurement_definition_id_event_id` ON `measurement` (`definition_id`, `event_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurement_event_id` ON `measurement` (`event_id`)",
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `consumable_usage` (`id` TEXT NOT NULL, `event_id` TEXT NOT NULL, `name` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`event_id`) REFERENCES `asset_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_consumable_usage_event_id` ON `consumable_usage` (`event_id`)",
        )
    }
}

/**
 * Schema v2 -> v3: the DERIVED definition (spec §7). `measurement_definition` gains `kind`,
 * `formula`, `source_a_id` and `source_b_id`, the last two foreign keys into the table itself with
 * ON DELETE RESTRICT.
 *
 * SQLite cannot add a column that carries a foreign key, so the table is recreated the way Room
 * recreates one: build `_new_measurement_definition` from `3.json`'s createSql, copy every v2 row
 * into it with `kind = 'ENTERED'` (every definition that existed before this phase is entered by
 * hand — there was no other kind), drop the old table, rename the new one into its place, and
 * create the four indexes `3.json` declares — the two the table already had, which the DROP took
 * with it, plus one for each new source column.
 *
 * Two details make that safe, and neither is incidental:
 *
 *  - Room turns `PRAGMA foreign_keys` **off** for the duration of `migrate` (and runs
 *    `foreign_key_check` afterwards), so dropping `measurement_definition` while `measurement`,
 *    `profile_field` and the new table still name it in their REFERENCES clauses is not an error.
 *    With foreign keys off SQLite also leaves those clauses alone across the RENAME, so they keep
 *    referring to the table *by name* — and the name comes back, attached to the new table, the
 *    moment the rename lands. `Migration1To3Test` runs the whole chain and proves it.
 *  - The new table's own `source_a_id` / `source_b_id` clauses say `measurement_definition`, not
 *    `_new_measurement_definition`, exactly as `3.json` spells them: before the rename they point
 *    at the table being replaced (which nothing inserts into), after it they point at themselves.
 *
 * As everywhere in this file, the SQL is copied verbatim from the exported schema so the migration
 * and the compiled entities have one source; Room validates the result on open.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_measurement_definition` (`id` TEXT NOT NULL, `asset_id` TEXT NOT NULL, `key` TEXT NOT NULL, `label` TEXT NOT NULL, `unit` TEXT NOT NULL, `value_type` TEXT NOT NULL, `decimals` INTEGER NOT NULL, `range_low` REAL, `range_high` REAL, `is_meter` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `archived_at` INTEGER, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `kind` TEXT NOT NULL, `formula` TEXT, `source_a_id` TEXT, `source_b_id` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`asset_id`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`source_a_id`) REFERENCES `measurement_definition`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`source_b_id`) REFERENCES `measurement_definition`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )",
        )
        connection.execSQL(
            "INSERT INTO `_new_measurement_definition` (`id`, `asset_id`, `key`, `label`, `unit`, " +
                "`value_type`, `decimals`, `range_low`, `range_high`, `is_meter`, `sort_order`, " +
                "`archived_at`, `created_at`, `updated_at`, `kind`) " +
                "SELECT `id`, `asset_id`, `key`, `label`, `unit`, `value_type`, `decimals`, " +
                "`range_low`, `range_high`, `is_meter`, `sort_order`, `archived_at`, `created_at`, " +
                "`updated_at`, 'ENTERED' FROM `measurement_definition`",
        )
        connection.execSQL("DROP TABLE `measurement_definition`")
        connection.execSQL(
            "ALTER TABLE `_new_measurement_definition` RENAME TO `measurement_definition`",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurement_definition_asset_id` ON `measurement_definition` (`asset_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_measurement_definition_asset_id_key` ON `measurement_definition` (`asset_id`, `key`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurement_definition_source_a_id` ON `measurement_definition` (`source_a_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_measurement_definition_source_b_id` ON `measurement_definition` (`source_b_id`)",
        )
    }
}
