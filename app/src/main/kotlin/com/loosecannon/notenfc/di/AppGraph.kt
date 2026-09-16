package com.loosecannon.notenfc.di

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.loosecannon.notenfc.BuildConfig
import com.loosecannon.notenfc.attachments.AttachmentRoot
import com.loosecannon.notenfc.attachments.DocumentTreeRoot
import com.loosecannon.notenfc.attachments.SafAttachmentStorage
import com.loosecannon.notenfc.attachments.Thumbnails
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork
import com.loosecannon.notenfc.core.ports.UuidGenerator
import com.loosecannon.notenfc.core.usecase.AddAttachment
import com.loosecannon.notenfc.core.usecase.ApplyTemplate
import com.loosecannon.notenfc.core.usecase.ArchiveAsset
import com.loosecannon.notenfc.core.usecase.ArchiveDefinition
import com.loosecannon.notenfc.core.usecase.ArchiveProfile
import com.loosecannon.notenfc.core.usecase.BindTag
import com.loosecannon.notenfc.core.usecase.CreateAsset
import com.loosecannon.notenfc.core.usecase.DeleteAsset
import com.loosecannon.notenfc.core.usecase.DeleteAttachment
import com.loosecannon.notenfc.core.usecase.DeleteDefinition
import com.loosecannon.notenfc.core.usecase.DeleteEvent
import com.loosecannon.notenfc.core.usecase.DeleteLink
import com.loosecannon.notenfc.core.usecase.DeleteProfile
import com.loosecannon.notenfc.core.usecase.ExportBackupSet
import com.loosecannon.notenfc.core.usecase.ImportBackupReplace
import com.loosecannon.notenfc.core.usecase.LogEvent
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.ReorderDefinitions
import com.loosecannon.notenfc.core.usecase.ReorderProfiles
import com.loosecannon.notenfc.core.usecase.ResolveTag
import com.loosecannon.notenfc.core.usecase.RestoreArtifacts
import com.loosecannon.notenfc.core.usecase.RetireAsset
import com.loosecannon.notenfc.core.usecase.SaveDefinition
import com.loosecannon.notenfc.core.usecase.SaveLink
import com.loosecannon.notenfc.core.usecase.SaveProfile
import com.loosecannon.notenfc.core.usecase.UpdateAsset
import com.loosecannon.notenfc.core.usecase.UpdateAttachment
import com.loosecannon.notenfc.core.usecase.UpdateEvent
import com.loosecannon.notenfc.data.room.AppDatabase
import com.loosecannon.notenfc.data.room.MIGRATION_1_2
import com.loosecannon.notenfc.data.room.MIGRATION_2_3
import com.loosecannon.notenfc.data.room.MIGRATION_3_4
import com.loosecannon.notenfc.data.room.MIGRATION_4_5
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomAttachmentRepository
import com.loosecannon.notenfc.data.room.RoomDefinitionRepository
import com.loosecannon.notenfc.data.room.RoomEventRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomProfileRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.prefs.AppPrefs
import com.loosecannon.notenfc.prefs.SharedPrefsStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled composition root. No DI framework in Phase 1 (D3 §5). */
class AppGraph(private val context: Context) {
    val db: AppDatabase = Room
        .databaseBuilder<AppDatabase>(
            context = context.applicationContext,
            name = context.applicationContext.getDatabasePath(DB_NAME).absolutePath,
        )
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        .build()

    val clock: Clock = Clock { System.currentTimeMillis() }
    val ids: IdGenerator = UuidGenerator
    val uow: UnitOfWork = RoomUnitOfWork(db)
    val assets: AssetRepository = RoomAssetRepository(db.assetDao())
    val tags: TagRepository = RoomTagRepository(db.nfcTagDao())
    val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())
    val definitions: DefinitionRepository = RoomDefinitionRepository(db.definitionDao())
    val profiles: ProfileRepository = RoomProfileRepository(db.profileDao())
    val events: EventRepository = RoomEventRepository(db.eventDao())
    val attachments: AttachmentRepository = RoomAttachmentRepository(db.attachmentDao())
    val prefs: AppPrefs = AppPrefs(SharedPrefsStore(context))

    /**
     * Swapped only by the instrumented suite, which has no SAF picker to drive and no persisted
     * grant to check (spec §12): it points these at `DocumentFile.fromFile` on an app-external
     * directory. Production never reassigns them.
     */
    @VisibleForTesting
    var attachmentRootResolver: (String) -> AttachmentRoot? = { treeUri ->
        DocumentFile.fromTreeUri(context.applicationContext, treeUri.toUri())
            ?.let { DocumentTreeRoot(it, context.applicationContext.contentResolver) }
    }

    @VisibleForTesting
    var attachmentGrantCheck: (String) -> Boolean = { treeUri ->
        context.applicationContext.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == treeUri && it.isReadPermission && it.isWritePermission
        }
    }

    /** The one gate every attachment path passes through: a folder, and the right to write in it. */
    val attachmentStorage: SafAttachmentStorage = SafAttachmentStorage(
        prefs = prefs,
        rootResolver = { uri -> attachmentRootResolver(uri) },
        grantCheck = { uri -> attachmentGrantCheck(uri) },
    )

    val thumbnails: Thumbnails = Thumbnails(context.applicationContext.cacheDir, attachmentStorage)

    // Phase 4A — attachments.
    val addAttachment: AddAttachment =
        AddAttachment(attachments, assets, events, attachmentStorage, uow, ids, clock)
    val updateAttachment: UpdateAttachment = UpdateAttachment(attachments, uow, clock)
    val deleteAttachment: DeleteAttachment = DeleteAttachment(attachments, attachmentStorage, uow)
    val restoreArtifacts: RestoreArtifacts = RestoreArtifacts(attachments, attachmentStorage)

    /** A cache file the camera can write into through the FileProvider (spec §9.3). */
    fun cameraCaptureUri(): Uri {
        val file = File(File(context.applicationContext.cacheDir, "camera"), "${ids.newId()}.jpg")
        file.parentFile?.mkdirs()
        return FileProvider.getUriForFile(
            context.applicationContext,
            "${BuildConfig.APPLICATION_ID}.files",
            file,
        )
    }

    /**
     * Produces a backup *set*: the data archive's bytes plus the plan for the artifacts archive
     * beside them. `BackupViewModel.exportSet` writes both files into the folder the owner picks.
     */
    val exportBackupSet: ExportBackupSet = ExportBackupSet(
        assets, tags, links, definitions, profiles, events, attachments, uow, ids, clock,
        BuildConfig.VERSION_NAME, SCHEMA_VERSION,
    )

    /** Wipe-and-load import. Replace is the only mode Phase 1A ships (D7 1A). */
    val importBackupReplace: ImportBackupReplace = ImportBackupReplace(
        assets, tags, links, definitions, profiles, events, attachments, attachmentStorage, uow,
    )

    /** Process-wide scope for work that must outlive a finishing activity (e.g. abandoning a row). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Phase 1B — NFC identity
    val resolveTag: ResolveTag = ResolveTag(tags, assets, links, uow, clock)
    val bindTag: BindTag = BindTag(tags, assets, links, uow, ids, clock)
    val provisionTag: ProvisionTag = ProvisionTag(tags, assets, links, uow, ids, clock)
    val applyTemplate: ApplyTemplate = ApplyTemplate(definitions, profiles, assets, uow, ids, clock)
    val createAsset: CreateAsset = CreateAsset(assets, uow, ids, clock, applyTemplate)
    val saveLink: SaveLink = SaveLink(links, uow, ids, clock)
    val openLink: OpenLink = OpenLink(links, uow, clock)

    // Phase 1C — the asset form. Archive-first: no hard delete for an asset in Phase 1 (R-9).
    val updateAsset: UpdateAsset = UpdateAsset(assets, uow, clock)
    val archiveAsset: ArchiveAsset = ArchiveAsset(assets, uow, clock)

    // Phase 2B-2 — retirement is a date the person picks, not a status (spec §7), and delete is
    // the one destructive asset action: it refuses a parent that still has children.
    val retireAsset: RetireAsset = RetireAsset(assets, uow, clock)
    val deleteAsset: DeleteAsset = DeleteAsset(assets, events, attachments, attachmentStorage, uow)

    /** A link is a pointer, not a record, so it can be deleted — unless a tag still points at it. */
    val deleteLink: DeleteLink = DeleteLink(links, tags, uow)

    // Phase 2A — the maintenance journal.
    val logEvent: LogEvent = LogEvent(events, definitions, profiles, assets, uow, ids, clock)
    val updateEvent: UpdateEvent = UpdateEvent(events, definitions, profiles, uow, ids, clock)
    val deleteEvent: DeleteEvent = DeleteEvent(events, attachments, attachmentStorage, uow)

    // Phase 2B-1 — the definition and profile editors. Archive is the ordinary retirement; delete
    // exists only for a row nothing references yet, and each use case checks that before writing.
    val saveDefinition: SaveDefinition =
        SaveDefinition(definitions, events, profiles, assets, uow, ids, clock)
    val archiveDefinition: ArchiveDefinition = ArchiveDefinition(definitions, uow, clock)
    val deleteDefinition: DeleteDefinition = DeleteDefinition(definitions, events, profiles, uow)
    val reorderDefinitions: ReorderDefinitions = ReorderDefinitions(definitions, uow, clock)
    val saveProfile: SaveProfile = SaveProfile(profiles, definitions, assets, uow, ids, clock)
    val archiveProfile: ArchiveProfile = ArchiveProfile(profiles, uow, clock)
    val deleteProfile: DeleteProfile = DeleteProfile(profiles, uow)
    val reorderProfiles: ReorderProfiles = ReorderProfiles(profiles, uow, clock)

    private companion object {
        const val DB_NAME = "notenfc.db"

        /** Room's `@Database(version = ...)`; recorded in the manifest so an import can refuse. */
        const val SCHEMA_VERSION = 5
    }
}
