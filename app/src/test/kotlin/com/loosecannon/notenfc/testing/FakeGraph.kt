package com.loosecannon.notenfc.testing

import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork
import com.loosecannon.notenfc.core.usecase.ArchiveAsset
import com.loosecannon.notenfc.core.usecase.CreateAsset
import com.loosecannon.notenfc.core.usecase.DeleteLink
import com.loosecannon.notenfc.core.usecase.ProvisionTag
import com.loosecannon.notenfc.core.usecase.UpdateAsset
import com.loosecannon.notenfc.data.room.AppDatabase
import com.loosecannon.notenfc.data.room.RoomAssetRepository
import com.loosecannon.notenfc.data.room.RoomLinkRepository
import com.loosecannon.notenfc.data.room.RoomTagRepository
import com.loosecannon.notenfc.data.room.RoomUnitOfWork
import com.loosecannon.notenfc.data.room.inMemoryDb

/**
 * `AppGraph` without a `Context`: the same members, built on `inMemoryDb()` and the real Room
 * repositories, so a ViewModel test exercises the production query, the production mapper and the
 * production invalidation flow rather than a hand-written fake that agrees with itself.
 *
 * The clock is a `var` a test moves by hand and the ids count up, so an assertion can name both.
 */
class FakeGraph(private val db: AppDatabase = inMemoryDb()) {

    /** Move this before a call to give the write a timestamp the test can assert on. */
    var now: Long = 1_000L

    val clock: Clock = Clock { now }

    private var seq = 0
    val ids: IdGenerator = IdGenerator { "00000000-0000-4000-8000-%012d".format(++seq) }

    val uow: UnitOfWork = RoomUnitOfWork(db)
    val assets: AssetRepository = RoomAssetRepository(db.assetDao())
    val tags: TagRepository = RoomTagRepository(db.nfcTagDao())
    val links: LinkRepository = RoomLinkRepository(db.externalLinkDao())

    val createAsset: CreateAsset = CreateAsset(assets, uow, ids, clock)
    val updateAsset: UpdateAsset = UpdateAsset(assets, uow, clock)
    val archiveAsset: ArchiveAsset = ArchiveAsset(assets, uow, clock)
    val provisionTag: ProvisionTag = ProvisionTag(tags, assets, links, uow, ids, clock)
    val deleteLink: DeleteLink = DeleteLink(links, tags, uow)

    fun close() = db.close()
}
