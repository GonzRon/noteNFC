package com.loosecannon.notenfc.ui.attachments

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.usecase.AddAttachmentCommand
import com.loosecannon.notenfc.core.usecase.AssetCommand
import com.loosecannon.notenfc.core.usecase.AttachmentResult
import com.loosecannon.notenfc.core.usecase.EventCommand
import com.loosecannon.notenfc.core.usecase.UpdateAttachmentCommand
import com.loosecannon.notenfc.testing.FakeGraph
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The DOCUMENTS section's one ViewModel, against a Room-backed [FakeGraph] so the observe flow,
 * the mapper and the use cases are the production ones. `viewModelScope` dispatches on
 * `Dispatchers.Main`, so the main dispatcher is an unconfined test one for the length of each
 * test; every assertion waits for a state rather than reading `value` after a write.
 *
 * `messages` has no replay by design, so a test that wants a line subscribes before the call
 * that produces it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentsSectionViewModelTest {

    private lateinit var graph: FakeGraph
    private lateinit var asset: Asset

    /** `AssetId` is a value class, so the seeded asset itself is what the test field holds. */
    private val assetId: AssetId get() = asset.id

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        graph = FakeGraph()
        runBlocking { asset = graph.createAsset.run(AssetCommand(name = "Hot tub")) }
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private fun model(owner: AttachmentOwner = AttachmentOwner.OfAsset(assetId)) =
        AttachmentsSectionViewModel(
            owner, graph.attachments, graph.attachmentStorage, graph.addAttachment,
            graph.updateAttachment, graph.deleteAttachment, graph.thumbnails,
        )

    private fun picked(name: String, mime: String = "application/pdf", body: String = "x") =
        PickedFile(name, mime, body.length.toLong()) { body.toByteArray().inputStream() }

    /** A pick whose bytes cannot be read: the store's `put` throws while copying it. */
    private fun broken(name: String) =
        PickedFile(name, "application/pdf", 1L) { throw IOException("the provider went away") }

    @Test fun aFreshInstallSaysTheStoreIsNotConfiguredAndListsNothing() = runTest {
        graph.attachmentStorage.state = StoreState.NotConfigured
        val vm = model()
        backgroundScope.launch { vm.state.collect() }

        val initial = vm.state.first()
        assertEquals(StoreState.NotConfigured, initial.store)
        assertEquals(emptyList<AttachmentRowState>(), initial.rows)
        assertNull(initial.progress)

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.add(listOf(picked("Guide.pdf")))
        assertEquals("Choose an attachment folder in Settings first", said.await())
        assertEquals(emptyList<AttachmentRowState>(), vm.state.value.rows)
        assertTrue(graph.attachmentStorage.store.files.isEmpty())
    }

    @Test fun withAFolderChosenAddedFilesAppearAsRowsOrderedByName() = runTest {
        val vm = model()
        backgroundScope.launch { vm.state.collect() }

        vm.add(listOf(picked("Zebra.pdf")))
        vm.state.first { it.rows.size == 1 }
        vm.add(listOf(picked("Apple.pdf")))

        val state = vm.state.first { it.rows.size == 2 }
        assertEquals(StoreState.Ready("Attachments", "com.example.provider"), state.store)
        assertEquals(listOf("Apple.pdf", "Zebra.pdf"), state.rows.map { it.displayName })
        assertEquals(
            listOf(AttachmentKind.DOCUMENT, AttachmentKind.DOCUMENT),
            state.rows.map { it.kind },
        )
        assertEquals(listOf(1L, 1L), state.rows.map { it.sizeBytes })
        assertEquals(listOf(false, false), state.rows.map { it.isImage })
        // The bytes really are in the store under each row's own locator, which is what
        // `present` claims: the flag itself is proved false by `aRowWhoseBytesAreGone...`.
        assertEquals(
            state.rows.map { it.locator }.sorted(),
            graph.attachmentStorage.store.files.keys.sorted(),
        )
        assertEquals(listOf(true, true), state.rows.map { it.present })
        assertNull(state.progress)
    }

    @Test fun theProgressLineNamesTheFileNumberAndTheTotal() {
        assertEquals("Adding 3 of 8…", addingProgressLine(3, 8))
    }

    @Test fun addingSeveralFilesReportsProgressAndKeepsGoingPastAFailure() = runTest {
        val vm = model()
        val seen = mutableListOf<AttachmentsSectionState>()
        backgroundScope.launch { vm.state.collect { seen += it } }
        val said = mutableListOf<String>()
        backgroundScope.launch(Dispatchers.Main) { vm.messages.collect { said += it } }

        vm.add(listOf(picked("First.pdf"), broken("Middle.pdf"), picked("Last.pdf")))

        val state = vm.state.first { it.rows.size == 2 && it.progress == null }
        assertEquals(listOf("First.pdf", "Last.pdf"), state.rows.map { it.displayName })
        assertEquals(listOf("Could not add Middle.pdf"), said)
        assertNull(state.progress)
        // Nothing was left behind for the file that failed mid-copy.
        assertEquals(2, graph.attachmentStorage.store.files.size)

        val progressLines = seen.mapNotNull { it.progress }.distinct()
        assertTrue("no progress was ever reported", progressLines.isNotEmpty())
        assertEquals(
            emptyList<String>(),
            progressLines - setOf("Adding 1 of 3…", "Adding 2 of 3…", "Adding 3 of 3…"),
        )
    }

    @Test fun anAccessLostStoreRefusesAddAndSaysWhyOnce() = runTest {
        val vm = model()
        backgroundScope.launch { vm.state.collect() }
        vm.add(listOf(picked("Guide.pdf")))
        val before = vm.state.first { it.rows.size == 1 }.rows

        graph.attachmentStorage.state = StoreState.AccessLost("Attachments")
        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.add(listOf(picked("Another.pdf")))

        assertEquals("The attachment folder is not available", said.await())
        // The refusal is news about the folder too: the section re-reads it and flips over.
        val after = vm.state.first { it.store is StoreState.AccessLost }
        assertEquals(StoreState.AccessLost("Attachments"), after.store)
        assertEquals(before.map { it.id }, after.rows.map { it.id })
        assertEquals(1, graph.attachmentStorage.store.files.size)
    }

    @Test fun aRowWhoseBytesAreGoneIsMarkedNotPresent() = runTest {
        val owner = AttachmentOwner.OfAsset(assetId)
        val added = graph.addAttachment.run(
            owner,
            AddAttachmentCommand(displayName = "Guide.pdf", mimeType = "application/pdf"),
            ByteSource { "x".byteInputStream() },
        )
        val row = (added as AttachmentResult.Ok).value
        // Behind the store's back: the sync tool, or the owner's file manager, removed the file.
        graph.attachmentStorage.store.files.remove(row.storageLocator)

        val vm = model(owner)
        backgroundScope.launch { vm.state.collect() }

        val rows = vm.state.first { it.rows.size == 1 && !it.rows.single().present }.rows
        assertEquals("Guide.pdf", rows.single().displayName)
        assertFalse(rows.single().present)
        assertNull(rows.single().thumbnail)
    }

    @Test fun savingRenamesTheRowAndLeavesTheLocatorAlone() = runTest {
        val vm = model()
        backgroundScope.launch { vm.state.collect() }
        vm.add(listOf(picked("guide.pdf")))
        val before = vm.state.first { it.rows.size == 1 }.rows.single()

        vm.save(
            before.id,
            UpdateAttachmentCommand("Installation guide", AttachmentKind.MANUAL, "2026-09-14", ""),
        )

        val after = vm.state
            .first { it.rows.singleOrNull()?.displayName == "Installation guide" }
            .rows.single()
        assertEquals(AttachmentKind.MANUAL, after.kind)
        assertEquals("2026-09-14", after.capturedOn)
        assertEquals(before.locator, after.locator)
        assertTrue(before.locator in graph.attachmentStorage.store.files)
    }

    @Test fun savingNothingIsSilent() = runTest {
        val vm = model()
        backgroundScope.launch { vm.state.collect() }
        vm.add(listOf(picked("guide.pdf")))
        val row = vm.state.first { it.rows.size == 1 }.rows.single()

        val said = mutableListOf<String>()
        backgroundScope.launch(Dispatchers.Main) { vm.messages.collect { said += it } }

        vm.save(row.id, UpdateAttachmentCommand(row.displayName, row.kind, row.capturedOn, row.notes))
        // A real save behind it is the barrier: once its rename lands, the `Unchanged` one has
        // been through the whole path and said nothing.
        vm.save(row.id, UpdateAttachmentCommand("Renamed.pdf", row.kind, row.capturedOn, row.notes))

        vm.state.first { it.rows.singleOrNull()?.displayName == "Renamed.pdf" }
        assertEquals(emptyList<String>(), said)
    }

    @Test fun deletingRemovesTheRowAndTheBytes() = runTest {
        val vm = model()
        backgroundScope.launch { vm.state.collect() }
        vm.add(listOf(picked("guide.pdf")))
        val row = vm.state.first { it.rows.size == 1 }.rows.single()

        vm.delete(row.id)

        assertEquals(emptyList<AttachmentRowState>(), vm.state.first { it.rows.isEmpty() }.rows)
        assertFalse(row.locator in graph.attachmentStorage.store.files)
    }

    @Test fun anEventOwnerSeesOnlyItsOwnFiles() = runTest {
        val event = graph.logEvent.run(
            EventCommand(
                assetId = assetId,
                profileId = null,
                kind = EventKind.MAINTENANCE,
                title = "Filter change",
                occurredOn = "2026-09-14",
                occurredTime = null,
                tzId = "UTC",
                notes = "",
                values = emptyMap(),
                consumables = emptyList(),
            ),
        )
        val assetVm = model()
        val eventVm = model(AttachmentOwner.OfEvent(event.id))
        backgroundScope.launch { assetVm.state.collect() }
        backgroundScope.launch { eventVm.state.collect() }

        assetVm.add(listOf(picked("Asset.pdf")))
        assetVm.state.first { it.rows.size == 1 }
        eventVm.add(listOf(picked("Event.pdf")))

        assertEquals(
            listOf("Event.pdf"),
            eventVm.state.first { it.rows.size == 1 }.rows.map { it.displayName },
        )
        assertEquals(listOf("Asset.pdf"), assetVm.state.value.rows.map { it.displayName })
    }

    @Test fun capturedOnDefaultsToTodayForAPickedFile() = runTest {
        val vm = AttachmentsSectionViewModel(
            AttachmentOwner.OfAsset(assetId), graph.attachments, graph.attachmentStorage,
            graph.addAttachment, graph.updateAttachment, graph.deleteAttachment, graph.thumbnails,
            today = { "2026-09-16" },
        )
        backgroundScope.launch { vm.state.collect() }
        vm.add(listOf(picked("guide.pdf")))
        assertEquals("2026-09-16", vm.state.first { it.rows.size == 1 }.rows.single().capturedOn)
    }

    /**
     * The mime is deliberately not an image's: `fromCamera` alone decides the kind, so this
     * proves the flag travels from the pick to the command rather than re-proving the inference.
     * A JVM test cannot hold an image row anyway — the thumbnail pass needs `BitmapFactory`.
     */
    @Test fun aCameraCaptureIsAPhotoWhateverElseItLooksLike() = runTest {
        val vm = model()
        backgroundScope.launch { vm.state.collect() }
        vm.add(
            listOf(
                PickedFile("shot.pdf", "application/pdf", 1L, fromCamera = true) {
                    "x".byteInputStream()
                },
            ),
        )
        val row = vm.state.first { it.rows.size == 1 }.rows.single()
        assertEquals(AttachmentKind.PHOTO, row.kind)
        assertFalse(row.isImage)
    }
}
