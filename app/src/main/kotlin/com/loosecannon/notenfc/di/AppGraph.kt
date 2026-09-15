package com.loosecannon.notenfc.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.loosecannon.notenfc.BuildConfig
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork
import com.loosecannon.notenfc.core.ports.UuidGenerator
import com.loosecannon.notenfc.core.usecase.ArchiveAsset
import com.loosecannon.notenfc.core.usecase.BindTag
import com.loosecannon.notenfc.core.usecase.CreateAsset
import com.loosecannon.notenfc.core.usecase.DeleteLink
import com.loosecannon.notenfc.core.usecase.ExportBackup
import com.loosecannon.notenfc.core.usecase.ImportBackupReplace
import com.loosecannon.notenfc.core.usecase.OpenLink
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.ResolveTag
import com.loosecannon.notenfc.core.usecase.SaveLink
import com.loosecannon.notenfc.core.usecase.UpdateAsset
import com.loosecannon.notenfc.data.room.AppDatabase
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.prefs.AppPrefs
import com.loosecannon.notenfc.prefs.SharedPrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled composition root. No DI framework in Phase 1 (D3 §5). */
class AppGraph(context: Context) {
    val db: AppDatabase = Room
        .databaseBuilder<AppDatabase>(
            context = context.applicationContext,
            name = context.applicationContext.getDatabasePath(DB_NAME).absolutePath,
        )
        .setDriver(AndroidSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    val clock: Clock = Clock { System.currentTimeMillis() }
    val ids: IdGenerator = UuidGenerator
    val uow: UnitOfWork = RoomUnitOfWork(db)
    val assets: AssetRepository = RoomAssetRepository(db.assetDao())
    val tags: TagRepository = RoomTagRepository(db.nfcTagDao())
    val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())
    val prefs: AppPrefs = AppPrefs(SharedPrefsStore(context))

    /** Produces the bytes of a v1 backup; where they go is the caller's choice (a SAF document). */
    val exportBackup: ExportBackup =
        ExportBackup(assets, tags, links, uow, clock, BuildConfig.VERSION_NAME, SCHEMA_VERSION)

    /** Wipe-and-load import. Replace is the only mode Phase 1A ships (D7 1A). */
    val importBackupReplace: ImportBackupReplace = ImportBackupReplace(assets, tags, links, uow)

    /** Process-wide scope for work that must outlive a finishing activity (e.g. abandoning a row). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Phase 1B — NFC identity
    val resolveTag: ResolveTag = ResolveTag(tags, assets, links, uow, clock)
    val bindTag: BindTag = BindTag(tags, assets, links, uow, ids, clock)
    val provisionTag: ProvisionTag = ProvisionTag(tags, assets, links, uow, ids, clock)
    val createAsset: CreateAsset = CreateAsset(assets, uow, ids, clock)
    val saveLink: SaveLink = SaveLink(links, uow, ids, clock)
    val openLink: OpenLink = OpenLink(links, uow, clock)

    // Phase 1C — the asset form. Archive-first: no hard delete for an asset in Phase 1 (R-9).
    val updateAsset: UpdateAsset = UpdateAsset(assets, uow, clock)
    val archiveAsset: ArchiveAsset = ArchiveAsset(assets, uow, clock)

    /** A link is a pointer, not a record, so it can be deleted — unless a tag still points at it. */
    val deleteLink: DeleteLink = DeleteLink(links, tags, uow)

    private companion object {
        const val DB_NAME = "notenfc.db"

        /** Room's `@Database(version = ...)`; recorded in the manifest so an import can refuse. */
        const val SCHEMA_VERSION = 1
    }
}
