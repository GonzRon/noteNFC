package com.loosecannon.servicetag.core.usecase

import com.loosecannon.servicetag.core.model.Asset
import com.loosecannon.servicetag.core.model.AssetEvent
import com.loosecannon.servicetag.core.model.AssetId
import com.loosecannon.servicetag.core.model.AttachmentId
import com.loosecannon.servicetag.core.model.AttachmentKind
import com.loosecannon.servicetag.core.model.AttachmentOwner
import com.loosecannon.servicetag.core.model.AttachmentProblem
import com.loosecannon.servicetag.core.model.EventId
import com.loosecannon.servicetag.core.model.EventKind
import com.loosecannon.servicetag.core.model.EventSource
import com.loosecannon.servicetag.core.model.MAX_ATTACHMENT_BYTES
import com.loosecannon.servicetag.core.ports.AttachmentStorage
import com.loosecannon.servicetag.core.ports.AttachmentStore
import com.loosecannon.servicetag.core.ports.ByteSource
import com.loosecannon.servicetag.core.ports.Clock
import com.loosecannon.servicetag.core.ports.IdGenerator
import com.loosecannon.servicetag.core.ports.StoreIoException
import com.loosecannon.servicetag.core.ports.StoreState
import com.loosecannon.servicetag.core.ports.StoredBytes
import com.loosecannon.servicetag.core.testing.FakeAttachmentStorage
import com.loosecannon.servicetag.core.testing.FakeUnitOfWork
import com.loosecannon.servicetag.core.testing.InMemoryAssetRepository
import com.loosecannon.servicetag.core.testing.InMemoryAttachmentRepository
import com.loosecannon.servicetag.core.testing.InMemoryAttachmentStore
import com.loosecannon.servicetag.core.testing.InMemoryEventRepository
import com.loosecannon.servicetag.core.testing.RiggedFailure
import kotlinx.coroutines.test.runTest
import java.io.InputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentUseCasesTest {

    private val attachments = InMemoryAttachmentRepository()
    private val assets = InMemoryAssetRepository()
    private val events = InMemoryEventRepository()
    private val uow = FakeUnitOfWork(assets, events, attachments)
    private val storage = FakeAttachmentStorage()
    private val store: InMemoryAttachmentStore get() = storage.store
    private var now = 5_000L
    private var seq = 0
    private val ids = IdGenerator { "att-${++seq}" }

    private val add = AddAttachment(attachments, assets, events, storage, uow, ids, Clock { now })
    private val update = UpdateAttachment(attachments, uow, Clock { now })
    private val remove = DeleteAttachment(attachments, storage, uow)

    private val payload = "1-2-3 easy installation".toByteArray()
    private fun source() = ByteSource { payload.inputStream() }

    private suspend fun asset(id: String = "a1"): AssetId {
        assets.upsert(Asset(id = AssetId(id), name = "Hot tub", createdAt = 1L, updatedAt = 1L))
        return AssetId(id)
    }

    private suspend fun event(id: String = "e1", assetId: String = "a1"): EventId {
        events.upsert(
            AssetEvent(
                id = EventId(id), assetId = AssetId(assetId), kind = EventKind.MAINTENANCE,
                title = "Filter change", profileId = null, occurredOn = "2026-09-15",
                occurredTime = null, tzId = "UTC", notes = "", source = EventSource.MANUAL,
                sourceRef = null, createdAt = 1L, updatedAt = 1L,
                measurements = emptyList(), consumables = emptyList(),
            ),
        )
        return EventId(id)
    }

    private fun cmd(
        name: String = "1-2-3 Easy Installation Guide.pdf",
        mime: String = "application/pdf",
        size: Long? = null,
        kind: AttachmentKind? = null,
    ) = AddAttachmentCommand(displayName = name, mimeType = mime, sizeBytes = size, kind = kind)

    @Test fun addPutsTheBytesFirstThenTheRow() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val result = add.run(owner, cmd(), source())
        val row = (result as AttachmentResult.Ok).value

        assertEquals("assets/a1/att-1.pdf", row.storageLocator)
        assertEquals(AttachmentKind.DOCUMENT, row.kind)          // inferred from application/pdf
        assertEquals(payload.size.toLong(), row.sizeBytes)
        assertEquals(InMemoryAttachmentStore.sha256Hex(payload), row.sha256)
        assertEquals(5_000L, row.createdAt)
        assertEquals(5_000L, row.updatedAt)
        assertEquals(row, attachments.rows["att-1"])
        assertTrue(store.exists("assets/a1/att-1.pdf"))
        assertEquals(1, uow.commits)
    }

    @Test fun addToAnEventUsesTheEventDirectory() = runTest {
        asset()
        val owner = AttachmentOwner.OfEvent(event())
        val row = (
            add.run(owner, cmd(name = "photo.jpg", mime = "image/jpeg"), source())
                as AttachmentResult.Ok
            ).value
        assertEquals("events/e1/att-1.jpg", row.storageLocator)
        assertEquals(AttachmentKind.PHOTO, row.kind)
        assertEquals(listOf(row), attachments.forOwner(owner))
    }

    @Test fun aCameraCaptureIsAPhotoWhateverTheMimeSays() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (
            add.run(
                owner,
                AddAttachmentCommand(
                    displayName = "capture.bin",
                    mimeType = "application/octet-stream",
                    fromCamera = true,
                ),
                source(),
            ) as AttachmentResult.Ok
            ).value
        assertEquals(AttachmentKind.PHOTO, row.kind)
    }

    @Test fun anExplicitKindBeatsTheInference() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (
            add.run(owner, cmd(kind = AttachmentKind.WARRANTY), source()) as AttachmentResult.Ok
            ).value
        assertEquals(AttachmentKind.WARRANTY, row.kind)
    }

    @Test fun addRefusesABlankNameAMissingOwnerAndNoStore() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.BlankName),
            add.run(owner, cmd(name = "   "), source()),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.OwnerMissing),
            add.run(AttachmentOwner.OfAsset(AssetId("nope")), cmd(), source()),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.OwnerMissing),
            add.run(AttachmentOwner.OfEvent(EventId("nope")), cmd(), source()),
        )

        storage.state = StoreState.NotConfigured
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.NoStore),
            add.run(owner, cmd(), source()),
        )
        storage.state = StoreState.AccessLost("Attachments")
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.StoreUnavailable),
            add.run(owner, cmd(), source()),
        )

        // nothing was written, either way
        assertTrue(attachments.rows.isEmpty())
        assertTrue(store.files.isEmpty())
        assertEquals(0, uow.commits)
    }

    @Test fun addRefusesAnOversizeFileBeforeCopyingAnyByte() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES)),
            add.run(owner, cmd(size = MAX_ATTACHMENT_BYTES + 1), source()),
        )
        assertTrue(store.files.isEmpty())
        // and the limit is inclusive: exactly 256 MiB is allowed through the guard
        assertTrue(add.run(owner, cmd(size = MAX_ATTACHMENT_BYTES), source()) is AttachmentResult.Ok)
    }

    /**
     * A camera reports no size, so the only number to guard on is the one the store came back
     * with. This rig over-reports it without writing 256 MiB anywhere.
     */
    @Test fun addRefusesAnOversizeFileTheProviderNeverDeclared() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val oversized = RiggedStore(reportedSize = MAX_ATTACHMENT_BYTES + 1)
        val adder = AddAttachment(
            attachments, assets, events, OneStore(oversized), uow, ids, Clock { now },
        )

        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES)),
            adder.run(owner, cmd(), source()),
        )
        assertTrue(attachments.rows.isEmpty())
        assertFalse(oversized.inner.exists("assets/a1/att-1.pdf"))   // its bytes were removed
        assertEquals(1, oversized.deleteAttempts)
        assertEquals(0, uow.commits)
    }

    /** Cleaning up after the refusal is itself best effort: it cannot turn into a thrown error. */
    @Test fun anOversizeRefusalSurvivesACleanupDeleteThatThrows() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val brittle = RiggedStore(
            reportedSize = MAX_ATTACHMENT_BYTES + 1,
            failDeleteWith = { StoreIoException("rigged delete failure") },
        )
        val adder = AddAttachment(
            attachments, assets, events, OneStore(brittle), uow, ids, Clock { now },
        )

        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES)),
            adder.run(owner, cmd(), source()),
        )
        assertEquals(1, brittle.deleteAttempts)
        assertTrue(attachments.rows.isEmpty())
        assertTrue(brittle.inner.exists("assets/a1/att-1.pdf"))   // an orphan, but still refused
        assertEquals(0, uow.commits)
    }

    @Test fun aFailedRowWriteDeletesTheBytesItHadAlreadyWritten() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        attachments.failOnUpsert = 1
        assertFailsWith<RiggedFailure> { add.run(owner, cmd(), source()) }
        assertTrue(attachments.rows.isEmpty())
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        assertEquals(1, store.deletes)
        assertEquals(1, uow.rollbacks)
    }

    /** The row-write failure is what the caller needs; a failing cleanup must not take its place. */
    @Test fun aCleanupDeleteThatThrowsDoesNotHideTheRowWriteFailure() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val brittle = RiggedStore(failDeleteWith = { StoreIoException("rigged delete failure") })
        val adder = AddAttachment(
            attachments, assets, events, OneStore(brittle), uow, ids, Clock { now },
        )
        attachments.failOnUpsert = 1

        val boom = assertFailsWith<RiggedFailure> { adder.run(owner, cmd(), source()) }

        assertTrue(boom.suppressedExceptions.any { it is StoreIoException })   // not lost, either
        assertEquals(1, brittle.deleteAttempts)
        assertTrue(attachments.rows.isEmpty())
        assertEquals(1, uow.rollbacks)
    }

    @Test fun updateChangesMetadataAndNeverTheLocator() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        now = 9_000L
        val saved = (
            update.run(
                row.id,
                UpdateAttachmentCommand(
                    displayName = "  Installation guide  ",
                    kind = AttachmentKind.MANUAL,
                    capturedOn = "2026-09-14",
                    notes = " keep ",
                ),
            ) as AttachmentResult.Ok
            ).value

        assertEquals("Installation guide", saved.displayName)
        assertEquals(AttachmentKind.MANUAL, saved.kind)
        assertEquals("2026-09-14", saved.capturedOn)
        assertEquals("keep", saved.notes)
        assertEquals(row.storageLocator, saved.storageLocator)   // bytes did not move
        assertEquals(row.sha256, saved.sha256)
        assertEquals(row.createdAt, saved.createdAt)
        assertEquals(9_000L, saved.updatedAt)
        assertEquals(saved, attachments.rows[row.id.value])
        assertTrue(store.exists(row.storageLocator))
    }

    @Test fun updateRefusesBlankUnchangedAndUnknown() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        val same = UpdateAttachmentCommand(row.displayName, row.kind, row.capturedOn, row.notes)

        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.Unchanged),
            update.run(row.id, same),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.BlankName),
            update.run(row.id, same.copy(displayName = " ")),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.OwnerMissing),
            update.run(AttachmentId("nope"), same),
        )
        assertEquals(row, attachments.rows[row.id.value])   // untouched by all three
        assertEquals(1, uow.commits)   // only the add committed
    }

    @Test fun deleteRemovesTheRowThenTheBytes() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        remove.run(row.id)
        assertTrue(attachments.rows.isEmpty())
        assertFalse(store.exists(row.storageLocator))
        // an unknown id is a no-op: it opens no transaction and deletes no bytes
        remove.run(AttachmentId("nope"))
        assertEquals(2, uow.commits)   // add + delete; the no-op opened no transaction
        assertEquals(1, store.deletes)

        // and a store that will not delete is not surfaced: the row still goes
        val orphaned = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        storage.state = StoreState.AccessLost("Attachments")
        remove.run(orphaned.id)
        assertTrue(attachments.rows.isEmpty())
        assertEquals(4, uow.commits)
        assertTrue(store.exists(orphaned.storageLocator))   // an orphan for 4B to sweep
    }

    /** The point of the best-effort sweep: a store that throws still loses its row. */
    @Test fun aStoreThatThrowsOnDeleteDoesNotHoldOntoTheRow() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val brittle = RiggedStore(failDeleteWith = { StoreIoException("rigged delete failure") })
        val adder = AddAttachment(
            attachments, assets, events, OneStore(brittle), uow, ids, Clock { now },
        )
        val row = (adder.run(owner, cmd(), source()) as AttachmentResult.Ok).value

        DeleteAttachment(attachments, OneStore(brittle), uow).run(row.id)

        assertTrue(attachments.rows.isEmpty())
        assertEquals(1, brittle.deleteAttempts)               // it was asked, and it refused
        assertTrue(brittle.inner.exists(row.storageLocator))  // an orphan, not a failure
    }

    /**
     * The sweep's allowance is a broken destination, not cancellation: swallowing that would let a
     * cancelled caller watch `run` return normally.
     */
    @Test fun aCancelledSweepIsNotSwallowed() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val cancelling = RiggedStore(failDeleteWith = { CancellationException("cancelled") })
        val adder = AddAttachment(
            attachments, assets, events, OneStore(cancelling), uow, ids, Clock { now },
        )
        val row = (adder.run(owner, cmd(), source()) as AttachmentResult.Ok).value

        assertFailsWith<CancellationException> {
            DeleteAttachment(attachments, OneStore(cancelling), uow).run(row.id)
        }
        assertTrue(attachments.rows.isEmpty())   // the row went first, inside the transaction
        assertEquals(1, cancelling.deleteAttempts)
    }
}

/**
 * The in-memory store with the two lies a use-case test needs: what `put` claims the bytes weigh,
 * and what `delete` raises instead of deleting.
 */
private class RiggedStore(
    private val reportedSize: Long? = null,
    private val failDeleteWith: (() -> Throwable)? = null,
) : AttachmentStore {
    val inner = InMemoryAttachmentStore()
    var deleteAttempts = 0
        private set

    override suspend fun put(locator: String, source: ByteSource): StoredBytes {
        val stored = inner.put(locator, source)
        return if (reportedSize == null) stored else stored.copy(sizeBytes = reportedSize)
    }

    override suspend fun open(locator: String): InputStream? = inner.open(locator)
    override suspend fun exists(locator: String): Boolean = inner.exists(locator)

    override suspend fun delete(locator: String) {
        deleteAttempts += 1
        failDeleteWith?.let { throw it() }
        inner.delete(locator)
    }
}

/** An always-ready [AttachmentStorage] over one given store. */
private class OneStore(private val store: AttachmentStore) : AttachmentStorage {
    override fun state(): StoreState = StoreState.Ready("Attachments", "com.example.provider")
    override fun store(): AttachmentStore = store
}
