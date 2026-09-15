package com.loosecannon.notenfc.backup

import com.loosecannon.notenfc.core.backup.BackupCorrupt
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.EventProfile
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.MeasurementDefinition
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.usecase.ConsumableInput
import com.loosecannon.notenfc.core.usecase.EventCommand
import com.loosecannon.notenfc.core.usecase.ExportBackup
import com.loosecannon.notenfc.core.usecase.ImportBackupReplace
import com.loosecannon.notenfc.core.usecase.ImportReport
import com.loosecannon.notenfc.data.room.AppDatabase
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomDefinitionRepository
import com.loosecannon.notenfc.data.room.RoomEventRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomProfileRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.data.room.inMemoryDb
import com.loosecannon.notenfc.testing.FakeGraph
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1A's exit proof. Data is created in one database, exported, and imported into a brand-new
 * empty database — the JVM stand-in for "uninstall the app and install it again". What must come
 * back is not "roughly the same data" but the *same identities*: every id, every target, every
 * foreign key. Sorted domain snapshots are compared whole, then the relationship queries are run
 * against the restored database so the FK columns are proved as well as the id columns.
 */
class RestoreProofTest {

    /** Everything Phase 1A needs, assembled over one database. The JVM twin of `AppGraph`. */
    private class Graph(val db: AppDatabase) {
        val assets = RoomAssetRepository(db.assetDao())
        val tags = RoomTagRepository(db.nfcTagDao())
        val links = RoomLinkRepository(db.externalLinkDao())
        val definitions = RoomDefinitionRepository(db.definitionDao())
        val profiles = RoomProfileRepository(db.profileDao())
        val events = RoomEventRepository(db.eventDao())
        val uow = RoomUnitOfWork(db)
        val export = ExportBackup(
            assets, tags, links, definitions, profiles, events, uow,
            Clock { FIXED_NOW }, "test", SCHEMA_VERSION,
        )
        val import = ImportBackupReplace(assets, tags, links, definitions, profiles, events, uow)
    }

    private data class Snapshot(
        val assets: List<Asset>,
        val tags: List<TagBinding>,
        val links: List<ExternalLink>,
        val definitions: List<MeasurementDefinition>,
        val profiles: List<EventProfile>,
        val events: List<AssetEvent>,
    )

    private suspend fun snapshot(g: Graph): Snapshot = Snapshot(
        assets = g.assets.all().sortedBy { it.id.value },
        tags = g.tags.all().sortedBy { it.id.value },
        links = g.links.all().sortedBy { it.id.value },
        definitions = g.definitions.all().sortedBy { it.id.value },
        profiles = g.profiles.all().sortedBy { it.id.value },
        events = g.events.all().sortedBy { it.id.value },
    )

    private fun graphOver(db: AppDatabase) = Graph(db)

    /**
     * Two assets, one link hanging off an asset and one standalone link, and three tags covering
     * every shape of `TagTarget`: bound to an asset (legacy MD5 payload), bound to a standalone
     * link, and unbound.
     */
    private suspend fun seed(g: Graph) {
        g.uow.write {
            g.assets.upsert(
                Asset(
                    id = AssetId("asset-furnace"),
                    name = "Furnace",
                    description = "basement, north wall",
                    category = "HVAC",
                    notes = "filter 16x25x1",
                    status = AssetStatus.ACTIVE,
                    createdAt = 1_000L,
                    updatedAt = 1_100L,
                ),
            )
            g.assets.upsert(
                Asset(
                    id = AssetId("asset-mower"),
                    name = "Mower",
                    description = "shed",
                    category = "Yard",
                    notes = "",
                    status = AssetStatus.ARCHIVED,
                    createdAt = 2_000L,
                    updatedAt = 2_100L,
                ),
            )
            g.links.upsert(
                ExternalLink(
                    id = LinkId("link-furnace-note"),
                    assetId = AssetId("asset-furnace"),
                    kind = LinkKind.JOPLIN,
                    label = "Furnace service log",
                    uri = "joplin://x-callback-url/openNote?id=abc123",
                    createdAt = 3_000L,
                    lastOpenedAt = 3_500L,
                    updatedAt = 3_100L,
                ),
            )
            g.links.upsert(
                ExternalLink(
                    id = LinkId("link-standalone"),
                    assetId = null,
                    kind = LinkKind.WEB,
                    label = "Manual PDF",
                    uri = "https://example.invalid/furnace.pdf",
                    createdAt = 4_000L,
                    lastOpenedAt = null,
                    updatedAt = 4_100L,
                ),
            )
            g.tags.upsert(
                TagBinding(
                    id = TagId("tag-on-furnace"),
                    payloadFormat = PayloadFormat.LEGACY_MD5,
                    payloadKey = "0123456789abcdef",
                    target = TagTarget.AssetTarget(AssetId("asset-furnace")),
                    status = TagStatus.ACTIVE,
                    label = "sticker on the cover",
                    physicalUid = "04A1B2C3D4E5F6",
                    writtenAt = 5_000L,
                    lastScannedAt = 5_500L,
                    createdAt = 5_000L,
                    updatedAt = 5_100L,
                ),
            )
            g.tags.upsert(
                TagBinding(
                    id = TagId("tag-on-manual"),
                    payloadFormat = PayloadFormat.V1,
                    payloadKey = "link-standalone",
                    target = TagTarget.LinkTarget(LinkId("link-standalone")),
                    status = TagStatus.ACTIVE,
                    label = "inside the binder",
                    physicalUid = null,
                    writtenAt = 6_000L,
                    lastScannedAt = null,
                    createdAt = 6_000L,
                    updatedAt = 6_100L,
                ),
            )
            g.tags.upsert(
                TagBinding(
                    id = TagId("tag-spare"),
                    payloadFormat = PayloadFormat.V1,
                    payloadKey = "spare-blank",
                    target = TagTarget.None,
                    status = TagStatus.UNBOUND,
                    label = null,
                    physicalUid = null,
                    writtenAt = null,
                    lastScannedAt = null,
                    createdAt = 7_000L,
                    updatedAt = 7_100L,
                ),
            )
        }
    }

    @Test
    fun forgetEverythingAndRebuildTheSameGraph() = runTest {
        val db1 = inMemoryDb()
        val before: Snapshot
        val bytes: ByteArray
        try {
            val g1 = graphOver(db1)
            seed(g1)
            bytes = g1.export.run()
            before = snapshot(g1)
        } finally {
            db1.close() // the "uninstall": that database and everything in it is gone
        }

        // Reinstall-equivalent: a brand-new, empty database that has never seen this data.
        val db2 = inMemoryDb()
        try {
            val g2 = graphOver(db2)
            assertTrue("the new database must start empty", snapshot(g2).assets.isEmpty())

            val report = g2.import.run(bytes)
            val after = snapshot(g2)

            // Same ids, same names, same targets, same links — the whole graph, compared whole.
            assertEquals(before, after)
            assertEquals(
                ImportReport(
                    formatVersion = 2, assets = 2, tags = 3, links = 2,
                    definitions = 0, profiles = 0, events = 0,
                ),
                report,
            )

            // And the relationships survive through the FK columns, not only through the id strings.
            assertEquals(1, g2.tags.forAsset(before.assets[0].id).size)
            assertEquals(
                TagId("tag-on-furnace"),
                g2.tags.forAsset(before.assets[0].id).single().id,
            )
            val standalone = before.links.single { it.assetId == null }
            assertEquals(1, g2.tags.forLink(standalone.id).size)
            assertEquals(TagId("tag-on-manual"), g2.tags.forLink(standalone.id).single().id)
            assertEquals(1, g2.links.forAsset(AssetId("asset-furnace")).size)
            assertEquals(listOf(LinkId("link-standalone")), g2.links.standalone().map { it.id })
            assertEquals(TagTarget.None, g2.tags.get(TagId("tag-spare"))!!.target)
            assertEquals(
                PayloadFormat.LEGACY_MD5,
                g2.tags.findByPayload(PayloadFormat.LEGACY_MD5, "0123456789abcdef")!!.payloadFormat,
            )
        } finally {
            db2.close()
        }
    }

    @Test
    fun replaceImportIsAtomic() = runTest {
        // A populated database, and a backup taken from a *different* database.
        val donor = inMemoryDb()
        val backup: ByteArray
        try {
            val gd = graphOver(donor)
            seed(gd)
            backup = gd.export.run()
        } finally {
            donor.close()
        }

        val db = inMemoryDb()
        try {
            val g = graphOver(db)
            g.uow.write {
                g.assets.upsert(
                    Asset(
                        id = AssetId("asset-local"),
                        name = "Local only",
                        description = "",
                        category = "",
                        notes = "",
                        status = AssetStatus.ACTIVE,
                        createdAt = 10L,
                        updatedAt = 11L,
                    ),
                )
                g.links.upsert(
                    ExternalLink(
                        id = LinkId("link-local"),
                        assetId = AssetId("asset-local"),
                        kind = LinkKind.OBSIDIAN,
                        label = "local note",
                        uri = "obsidian://open?file=local",
                        createdAt = 12L,
                        lastOpenedAt = null,
                        updatedAt = 13L,
                    ),
                )
                g.tags.upsert(
                    TagBinding(
                        id = TagId("tag-local"),
                        payloadFormat = PayloadFormat.V1,
                        payloadKey = "local",
                        target = TagTarget.AssetTarget(AssetId("asset-local")),
                        status = TagStatus.ACTIVE,
                        label = null,
                        physicalUid = null,
                        writtenAt = null,
                        lastScannedAt = null,
                        createdAt = 14L,
                        updatedAt = 15L,
                    ),
                )
            }
            val untouched = snapshot(g)

            // 1. Corrupt bytes are refused and change nothing.
            val corrupt = backup.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
            assertThrows(BackupCorrupt::class.java) {
                kotlinx.coroutines.runBlocking { g.import.run(corrupt) }
            }
            assertEquals(untouched, snapshot(g))

            // 2. A valid backup replaces everything: none of the local rows survive.
            val report = g.import.run(backup)
            assertEquals(
                ImportReport(
                    formatVersion = 2, assets = 2, tags = 3, links = 2,
                    definitions = 0, profiles = 0, events = 0,
                ),
                report,
            )
            val after = snapshot(g)
            assertTrue(
                "no local row may survive a replace import",
                after.assets.none { it.id.value == "asset-local" } &&
                    after.links.none { it.id.value == "link-local" } &&
                    after.tags.none { it.id.value == "tag-local" },
            )
            assertEquals(2, after.assets.size)
            assertEquals(3, after.tags.size)
            assertEquals(2, after.links.size)
        } finally {
            db.close()
        }
    }

    /**
     * Phase 2A's half of the same proof. The journal is seeded the way the app itself would seed
     * it — a template applied by [com.loosecannon.notenfc.core.usecase.CreateAsset], an event
     * logged through [com.loosecannon.notenfc.core.usecase.LogEvent] — so the rows under test are
     * rows the production path actually produces, children and their ids included. Then the whole
     * seven-table graph goes out to a backup and comes back into an empty database unchanged.
     */
    @Test
    fun theJournalSurvivesTheSameRoundTrip() = runTest {
        val before: Snapshot
        val bytes: ByteArray
        val g1 = FakeGraph()
        try {
            val spa = g1.createAsset.run(name = "Hot tub", templateKey = "hot_tub")
            val waterTest = g1.profiles.forAsset(spa.id).first { it.name == "Water test" }
            g1.logEvent.run(
                EventCommand(
                    assetId = spa.id,
                    profileId = waterTest.id,
                    kind = waterTest.eventKind,
                    title = "",
                    occurredOn = "2026-09-14",
                    occurredTime = "08:30",
                    tzId = "UTC",
                    notes = "after the storm",
                    values = waterTest.fields.associate { it.definitionId to "7.4" },
                    consumables = listOf(ConsumableInput("Chlorine", "2", "tab")),
                ),
            )
            bytes = g1.exportBackup.run()
            before = snapshot(graphOver(g1.db))
        } finally {
            g1.close()
        }

        assertTrue("the template must have seeded definitions", before.definitions.isNotEmpty())
        assertTrue("the template must have seeded profiles", before.profiles.isNotEmpty())
        assertEquals(1, before.events.size)
        assertTrue("the event must carry its children", before.events.single().measurements.isNotEmpty())

        val db2 = inMemoryDb()
        try {
            val g2 = graphOver(db2)
            assertTrue("the new database must start empty", snapshot(g2).events.isEmpty())

            val report = g2.import.run(bytes)
            assertEquals(
                ImportReport(
                    formatVersion = 2,
                    assets = 1,
                    tags = 0,
                    links = 0,
                    definitions = before.definitions.size,
                    profiles = before.profiles.size,
                    events = 1,
                ),
                report,
            )
            // All seven tables, compared whole: ids, child ids, foreign keys, snapshot units.
            assertEquals(before, snapshot(g2))

            // And the journal reads back through its own relationship queries, not only `all()`.
            val spa = before.assets.single()
            assertEquals(before.definitions, g2.definitions.forAsset(spa.id).sortedBy { it.id.value })
            assertEquals(before.profiles, g2.profiles.forAsset(spa.id).sortedBy { it.id.value })
            assertEquals(before.events, g2.events.forAsset(spa.id))
            assertEquals("hot_tub", spa.templateKey)
        } finally {
            db2.close()
        }
    }

    private companion object {
        const val FIXED_NOW = 1_757_000_000_000L
        const val SCHEMA_VERSION = 2
    }
}
