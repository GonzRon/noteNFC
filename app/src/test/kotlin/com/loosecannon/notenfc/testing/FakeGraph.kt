package com.loosecannon.notenfc.testing

import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork
import com.loosecannon.notenfc.core.usecase.ApplyTemplate
import com.loosecannon.notenfc.core.usecase.ArchiveAsset
import com.loosecannon.notenfc.core.usecase.ArchiveDefinition
import com.loosecannon.notenfc.core.usecase.ArchiveProfile
import com.loosecannon.notenfc.core.usecase.CreateAsset
import com.loosecannon.notenfc.core.usecase.DeleteAsset
import com.loosecannon.notenfc.core.usecase.DeleteDefinition
import com.loosecannon.notenfc.core.usecase.DeleteEvent
import com.loosecannon.notenfc.core.usecase.DeleteLink
import com.loosecannon.notenfc.core.usecase.DeleteProfile
import com.loosecannon.notenfc.core.usecase.ExportBackup
import com.loosecannon.notenfc.core.usecase.ImportBackupReplace
import com.loosecannon.notenfc.core.usecase.LogEvent
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.ReorderDefinitions
import com.loosecannon.notenfc.core.usecase.ReorderProfiles
import com.loosecannon.notenfc.core.usecase.RetireAsset
import com.loosecannon.notenfc.core.usecase.SaveDefinition
import com.loosecannon.notenfc.core.usecase.SaveProfile
import com.loosecannon.notenfc.core.usecase.UpdateAsset
import com.loosecannon.notenfc.core.usecase.UpdateEvent
import com.loosecannon.notenfc.data.room.AppDatabase
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomDefinitionRepository
import com.loosecannon.notenfc.data.room.RoomEventRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomProfileRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.data.room.inMemoryDb
import com.loosecannon.notenfc.prefs.AppPrefs
import com.loosecannon.notenfc.prefs.KeyValueStore

/**
 * `AppGraph` without a `Context`: the same members, built on `inMemoryDb()` and the real Room
 * repositories, so a ViewModel test exercises the production query, the production mapper and the
 * production invalidation flow rather than a hand-written fake that agrees with itself.
 *
 * The clock is a `var` a test moves by hand and the ids count up, so an assertion can name both.
 */
class FakeGraph(val db: AppDatabase = inMemoryDb()) {

    /** Move this before a call to give the write a timestamp the test can assert on. */
    var now: Long = 1_000L

    val clock: Clock = Clock { now }

    private var seq = 0
    val ids: IdGenerator = IdGenerator { "00000000-0000-4000-8000-%012d".format(++seq) }

    val uow: UnitOfWork = RoomUnitOfWork(db)
    val assets: AssetRepository = RoomAssetRepository(db.assetDao())
    val tags: TagRepository = RoomTagRepository(db.nfcTagDao())
    val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())
    val definitions: DefinitionRepository = RoomDefinitionRepository(db.definitionDao())
    val profiles: ProfileRepository = RoomProfileRepository(db.profileDao())
    val events: EventRepository = RoomEventRepository(db.eventDao())

    val applyTemplate: ApplyTemplate = ApplyTemplate(definitions, profiles, assets, uow, ids, clock)
    val createAsset: CreateAsset = CreateAsset(assets, uow, ids, clock, applyTemplate)
    val updateAsset: UpdateAsset = UpdateAsset(assets, uow, clock)
    val archiveAsset: ArchiveAsset = ArchiveAsset(assets, uow, clock)
    val retireAsset: RetireAsset = RetireAsset(assets, uow, clock)
    val deleteAsset: DeleteAsset = DeleteAsset(assets, uow)
    val provisionTag: ProvisionTag = ProvisionTag(tags, assets, links, uow, ids, clock)
    val deleteLink: DeleteLink = DeleteLink(links, tags, uow)
    val logEvent: LogEvent = LogEvent(events, definitions, profiles, assets, uow, ids, clock)
    val updateEvent: UpdateEvent = UpdateEvent(events, definitions, profiles, uow, ids, clock)
    val deleteEvent: DeleteEvent = DeleteEvent(events, uow)
    val saveDefinition: SaveDefinition =
        SaveDefinition(definitions, events, profiles, assets, uow, ids, clock)
    val archiveDefinition: ArchiveDefinition = ArchiveDefinition(definitions, uow, clock)
    val deleteDefinition: DeleteDefinition = DeleteDefinition(definitions, events, profiles, uow)
    val reorderDefinitions: ReorderDefinitions = ReorderDefinitions(definitions, uow, clock)
    val saveProfile: SaveProfile = SaveProfile(profiles, definitions, assets, uow, ids, clock)
    val archiveProfile: ArchiveProfile = ArchiveProfile(profiles, uow, clock)
    val deleteProfile: DeleteProfile = DeleteProfile(profiles, uow)
    val reorderProfiles: ReorderProfiles = ReorderProfiles(profiles, uow, clock)

    /** Device-local preferences, in a map: a test can read back exactly what the UI wrote. */
    val prefs: AppPrefs = AppPrefs(InMemoryKeyValueStore())

    val exportBackup: ExportBackup = ExportBackup(
        assets, tags, links, definitions, profiles, events, uow, clock, APP_VERSION, SCHEMA_VERSION,
    )
    val importBackupReplace: ImportBackupReplace =
        ImportBackupReplace(assets, tags, links, definitions, profiles, events, uow)

    fun close() = db.close()

    private companion object {
        const val APP_VERSION = "test"
        const val SCHEMA_VERSION = 4
    }
}

/** The `SharedPreferences` side of [AppPrefs] without Android under it. */
private class InMemoryKeyValueStore : KeyValueStore {
    private val longs = mutableMapOf<String, Long>()
    private val strings = mutableMapOf<String, String>()

    override fun getLong(key: String): Long? = longs[key]
    override fun putLong(key: String, value: Long) { longs[key] = value }
    override fun getString(key: String): String? = strings[key]
    override fun putString(key: String, value: String) { strings[key] = value }
}
