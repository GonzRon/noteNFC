package com.loosecannon.notenfc.ui.backup

import com.loosecannon.notenfc.backup.BackupSetNames
import com.loosecannon.notenfc.core.backup.ArtifactsCodec
import com.loosecannon.notenfc.core.backup.BackupSetIncomplete
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.ports.BackupIO
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.usecase.AddAttachmentCommand
import com.loosecannon.notenfc.core.usecase.AttachmentResult
import com.loosecannon.notenfc.testing.FakeGraph
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The backup set, against a Room-backed [FakeGraph]. The destination is a [BackupSetSink] rather
 * than a folder precisely so these can run without SAF: one sink keeps both files, another refuses
 * one of the two, a third breaks half-way through a write the way a full disk does — and the rule
 * the owner asked for has to hold in every case.
 *
 * That rule: a backup set is a *set*. Both files land and the artifacts archive covers the plan,
 * or nothing is saved and the nudge does not move.
 *
 * `messages` has no replay by design, so a test that wants a line subscribes before the call that
 * produces it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    private lateinit var graph: FakeGraph

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        graph = FakeGraph()
    }

    @After fun tearDown() {
        graph.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = BackupViewModel(
        graph.exportBackupSet, graph.importBackupReplace, graph.restoreArtifacts,
        graph.attachmentStorage, graph.prefs, graph.clock,
    )

    // --- doubles ---------------------------------------------------------------------------

    /** A document the user picked that keeps what it is given. */
    private class MemoryIO(private val bytes: ByteArray) : BackupIO {
        override suspend fun read(): ByteArray = bytes
        override suspend fun openStream(): InputStream = ByteArrayInputStream(bytes)
    }

    /** A sink that keeps what it was handed, and can be told to fail on one of the two names. */
    private class RecordingSink(private val failOnPrefix: String? = null) : BackupSetSink {
        val files = LinkedHashMap<String, ByteArray>()
        val deleted = mutableListOf<String>()

        override suspend fun write(name: String, body: suspend (OutputStream) -> Unit): String {
            if (failOnPrefix != null && name.startsWith(failOnPrefix)) {
                error("rigged write failure for $name")
            }
            val out = ByteArrayOutputStream()
            body(out)
            files[name] = out.toByteArray()
            return name
        }

        override suspend fun delete(handle: String) {
            deleted += handle
            files.remove(handle)
        }
    }

    /**
     * `SafBackupSetWriter`'s own shape: the document exists from the moment `write` is called, and
     * a body that throws half-way takes that document with it before the failure travels on.
     */
    private class PartialWriteSink(private val failOnPrefix: String) : BackupSetSink {
        val files = LinkedHashMap<String, ByteArray>()
        val deleted = mutableListOf<String>()

        override suspend fun write(name: String, body: suspend (OutputStream) -> Unit): String {
            files[name] = ByteArray(0)          // created before a single byte is written
            val out = ByteArrayOutputStream()
            try {
                body(if (name.startsWith(failOnPrefix)) BreakingStream(out, after = 64) else out)
            } catch (t: Throwable) {
                // The invariant the SAF writer keeps: its own half-written document goes away.
                files.remove(name)
                deleted += name
                throw t
            }
            files[name] = out.toByteArray()
            return name
        }

        override suspend fun delete(handle: String) {
            deleted += handle
            files.remove(handle)
        }
    }

    /** Takes [after] bytes and then fails, the way a disk that filled up does. */
    private class BreakingStream(
        private val sink: OutputStream,
        private val after: Int,
    ) : OutputStream() {
        private var written = 0
        override fun write(b: Int) {
            if (written++ >= after) throw IOException("rigged mid-write failure")
            sink.write(b)
        }
    }

    // --- fixtures --------------------------------------------------------------------------

    private val stamped = Regex("""^noteNFC-(data|artifacts)-\d{8}-\d{6}\.zip$""")

    private val payload = ByteArray(5_000) { (it % 251).toByte() }

    /** Adds a file the way the picker would, and returns the row. */
    private suspend fun addFile(asset: AssetId, name: String = "Guide.pdf"): Attachment {
        val result = graph.addAttachment.run(
            AttachmentOwner.OfAsset(asset),
            AddAttachmentCommand(
                displayName = name,
                mimeType = "application/pdf",
                sizeBytes = payload.size.toLong(),
            ),
            ByteSource { ByteArrayInputStream(payload) },
        )
        return (result as AttachmentResult.Ok).value
    }

    private fun entryNames(zip: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                names += entry.name
                zin.closeEntry()
            }
        }
        return names
    }

    /** The set id an artifacts archive carries, read out of its manifest. */
    private fun setIdOf(artifacts: ByteArray): String {
        val manifest = ZipInputStream(ByteArrayInputStream(artifacts)).use { zin ->
            val first = zin.nextEntry!!
            assertEquals(ArtifactsCodec.MANIFEST_ENTRY, first.name)
            String(zin.readBytes(), Charsets.UTF_8)
        }
        return Regex(""""backupSetId"\s*:\s*"([^"]*)"""").find(manifest)!!.groupValues[1]
    }

    /**
     * The same data archive as a format-4 file: the version goes back one and the four fields
     * format 4 never had come out of the manifest. `data.json` is untouched, so the manifest's
     * `dataSha256` still seals it.
     */
    private fun asFormatFour(data: ByteArray): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(data)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                entries[entry.name] = zin.readBytes()
            }
        }
        val dropped = setOf("backupSetId", "artifactFormatVersion", "artifactCount", "artifactBytes")
        val manifest = String(entries.getValue("manifest.json"), Charsets.UTF_8)
            .lines()
            .filterNot { line -> dropped.any { line.trimStart().startsWith("\"$it\"") } }
            .joinToString("\n")
            .replace(Regex(""""formatVersion"\s*:\s*5"""), "\"formatVersion\": 4")
            .replace(Regex(""",(\s*})"""), "$1") // the comma the dropped fields left behind
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("data.json"))
            zos.write(entries.getValue("data.json"))
            zos.closeEntry()
        }
        return out.toByteArray()
    }

    // --- export ----------------------------------------------------------------------------

    @Test fun exportWritesTwoStampedFilesAndMarksTheBackupOnce() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()

        val said = async(Dispatchers.Main) { vm.messages.first() }
        val message = vm.exportSet(sink).getOrThrow()
        vm.exportSetTo(RecordingSink())

        val stamp = BackupSetNames.stamp(7_000L)
        assertEquals(
            listOf(BackupSetNames.data(stamp), BackupSetNames.artifacts(stamp)),
            sink.files.keys.toList(),
        )
        assertEquals(emptyList<String>(), sink.files.keys.filterNot { stamped.matches(it) })
        assertTrue("both archives must carry bytes", sink.files.values.all { it.isNotEmpty() })
        assertEquals(emptyList<String>(), sink.deleted)
        assertEquals(BackupSetNames.data(stamp) + " + " + BackupSetNames.artifacts(stamp), message)
        assertEquals("Exported as $message", said.await())
        assertEquals(7_000L, graph.prefs.lastBackupAt)
        assertEquals(7_000L, vm.state.value.lastBackupAt)
    }

    @Test fun anInstallWithNoAttachmentsStillWritesBothFiles() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()

        vm.exportSet(sink).getOrThrow()

        val artifacts = sink.files.getValue(BackupSetNames.artifacts(BackupSetNames.stamp(7_000L)))
        assertEquals(listOf(ArtifactsCodec.MANIFEST_ENTRY), entryNames(artifacts))
    }

    @Test fun aFailedDataWriteWritesNothingAndLeavesTheNudgeAlone() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        val vm = viewModel()
        val sink = RecordingSink(failOnPrefix = "noteNFC-data")

        assertTrue(vm.exportSet(sink).isFailure)

        assertEquals(emptyList<String>(), sink.files.keys.toList())
        assertNull(graph.prefs.lastBackupAt)
        assertNull(vm.state.value.lastBackupAt)
    }

    @Test fun aFailedArtifactsWriteRemovesTheDataFileAndLeavesTheNudgeAlone() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink(failOnPrefix = "noteNFC-artifacts")

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.exportSetTo(sink)
        // The line arrives when the export is over, so waiting for it is how the assertions
        // below know they are looking at the end state and not at a step on the way.
        assertEquals(
            "Export failed: the files archive could not be written. Nothing was saved.",
            said.await(),
        )

        val stamp = BackupSetNames.stamp(7_000L)
        assertEquals(emptyList<String>(), sink.files.keys.toList())
        assertEquals(listOf(BackupSetNames.data(stamp)), sink.deleted)
        assertNull(graph.prefs.lastBackupAt)
        assertNull(vm.state.value.lastBackupAt)
    }

    @Test fun missingManagedAttachmentMakesExportFailAndLeavesNoArchives() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        val row = addFile(pump.id)
        // The row still claims its bytes; the folder no longer has them.
        graph.attachmentStorage.store.files.clear()
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()

        val failure = vm.exportSet(sink).exceptionOrNull()

        assertTrue("expected BackupSetIncomplete, got $failure", failure is BackupSetIncomplete)
        failure as BackupSetIncomplete
        assertEquals(listOf(row.id), failure.missing)
        assertEquals(emptyList<AttachmentId>(), failure.mismatched)
        val stamp = BackupSetNames.stamp(7_000L)
        assertEquals(emptyList<String>(), sink.files.keys.toList())
        assertEquals(
            listOf(BackupSetNames.artifacts(stamp), BackupSetNames.data(stamp)),
            sink.deleted,
        )
        assertNull(graph.prefs.lastBackupAt)
        assertNull(vm.state.value.lastBackupAt)

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.exportSetTo(RecordingSink())
        assertEquals("Backup not saved: 1 attachment file is missing", said.await())
    }

    @Test fun mismatchedManagedAttachmentMakesExportFailAndLeavesNoArchives() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        val row = addFile(pump.id)
        // Someone edited the file in the folder: same length, different bytes.
        graph.attachmentStorage.store.files[row.storageLocator] = ByteArray(payload.size) { 1 }
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()

        val failure = vm.exportSet(sink).exceptionOrNull()

        assertTrue("expected BackupSetIncomplete, got $failure", failure is BackupSetIncomplete)
        failure as BackupSetIncomplete
        assertEquals(emptyList<AttachmentId>(), failure.missing)
        assertEquals(listOf(row.id), failure.mismatched)
        val stamp = BackupSetNames.stamp(7_000L)
        assertEquals(emptyList<String>(), sink.files.keys.toList())
        assertEquals(
            listOf(BackupSetNames.artifacts(stamp), BackupSetNames.data(stamp)),
            sink.deleted,
        )
        assertNull(graph.prefs.lastBackupAt)

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.exportSetTo(RecordingSink())
        assertEquals(
            "Backup not saved: 1 attachment file has changed since it was added",
            said.await(),
        )
    }

    @Test fun midArtifactsWriteRemovesPartialArtifactsAndDataZip() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        addFile(pump.id)
        graph.now = 7_000L
        val vm = viewModel()
        val sink = PartialWriteSink(failOnPrefix = "noteNFC-artifacts")

        assertTrue(vm.exportSet(sink).isFailure)

        val stamp = BackupSetNames.stamp(7_000L)
        assertEquals(emptyList<String>(), sink.files.keys.toList())
        // The sink dropped its own half-written artifacts document; the export dropped the data one.
        assertEquals(
            listOf(BackupSetNames.artifacts(stamp), BackupSetNames.data(stamp)),
            sink.deleted,
        )
        assertNull(graph.prefs.lastBackupAt)
        assertNull(vm.state.value.lastBackupAt)
    }

    // --- restore ---------------------------------------------------------------------------

    @Test fun restoringDataReplacesEverythingFromTheDataArchive() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()
        vm.exportSet(sink).getOrThrow()
        val data = MemoryIO(sink.files.getValue(BackupSetNames.data(BackupSetNames.stamp(7_000L))))

        // Everything the backup knows about is gone before it is read back.
        graph.uow.write {
            graph.tags.deleteAll()
            graph.links.deleteAll()
            graph.assets.deleteAll()
        }
        assertEquals(emptyList<Asset>(), graph.assets.all())

        val report = vm.restoreData(data).getOrThrow()

        assertEquals(1, report.assets)
        assertEquals(0, report.tags)
        assertEquals(0, report.links)
        assertEquals(5, report.formatVersion)
        assertEquals(listOf(pump.id), graph.assets.all().map(Asset::id))
    }

    @Test fun restoringDataRemembersTheSetIdAndMentionsTheAttachmentsItListed() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        addFile(pump.id)
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()
        vm.exportSet(sink).getOrThrow()
        val stamp = BackupSetNames.stamp(7_000L)
        val setId = setIdOf(sink.files.getValue(BackupSetNames.artifacts(stamp)))
        val data = MemoryIO(sink.files.getValue(BackupSetNames.data(stamp)))

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.restoreDataFrom(data)
        val line = said.await()

        assertEquals(setId, graph.prefs.lastRestoredBackupSetId)
        assertEquals(setId, vm.state.value.lastRestoredBackupSetId)
        assertTrue(
            "the line must point at the second half: $line",
            line.contains("1 attachments listed; restore the files archive to get their contents"),
        )
    }

    @Test fun restoringTheFilesArchiveOfTheSameSetRestoresTheBytes() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        val row = addFile(pump.id)
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()
        vm.exportSet(sink).getOrThrow()
        val stamp = BackupSetNames.stamp(7_000L)

        // A new phone: the rows arrive from the data archive, the bytes are not here yet.
        graph.attachmentStorage.store.files.clear()
        vm.restoreData(MemoryIO(sink.files.getValue(BackupSetNames.data(stamp)))).getOrThrow()
        assertTrue(graph.attachmentStorage.store.files.isEmpty())

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.restoreFilesFrom(MemoryIO(sink.files.getValue(BackupSetNames.artifacts(stamp))))

        assertEquals("Restored 1 files, skipped 0", said.await())
        assertArrayEquals(payload, graph.attachmentStorage.store.files.getValue(row.storageLocator))
    }

    @Test fun restoringTheFilesArchiveOfAnotherSetIsRefusedByTheTwoShortIds() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        addFile(pump.id)
        graph.now = 7_000L
        val vm = viewModel()
        val stamp = BackupSetNames.stamp(7_000L)
        val first = RecordingSink().also { vm.exportSet(it).getOrThrow() }
        val second = RecordingSink().also { vm.exportSet(it).getOrThrow() }
        val firstId = setIdOf(first.files.getValue(BackupSetNames.artifacts(stamp)))
        val secondId = setIdOf(second.files.getValue(BackupSetNames.artifacts(stamp)))

        graph.attachmentStorage.store.files.clear()
        vm.restoreData(MemoryIO(second.files.getValue(BackupSetNames.data(stamp)))).getOrThrow()

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.restoreFilesFrom(MemoryIO(first.files.getValue(BackupSetNames.artifacts(stamp))))

        assertEquals(
            "Those files belong to backup set ${firstId.take(8)}, not ${secondId.take(8)}",
            said.await(),
        )
        assertTrue(
            "the refused archive must add nothing",
            graph.attachmentStorage.store.files.isEmpty(),
        )
    }

    @Test fun restoringFilesWithNoFolderConfiguredSaysToChooseOne() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        addFile(pump.id)
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()
        vm.exportSet(sink).getOrThrow()
        val artifacts = sink.files.getValue(BackupSetNames.artifacts(BackupSetNames.stamp(7_000L)))

        graph.attachmentStorage.store.files.clear()
        graph.attachmentStorage.state = StoreState.NotConfigured

        val said = async(Dispatchers.Main) { vm.messages.first() }
        vm.restoreFilesFrom(MemoryIO(artifacts))

        assertEquals("Choose an attachment folder in Settings first", said.await())
        assertTrue(graph.attachmentStorage.store.files.isEmpty())
    }

    @Test fun aFormatFourFileStillImportsThroughRestoreData() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        graph.now = 7_000L
        val vm = viewModel()
        val sink = RecordingSink()
        vm.exportSet(sink).getOrThrow()
        val data = sink.files.getValue(BackupSetNames.data(BackupSetNames.stamp(7_000L)))

        val report = vm.restoreData(MemoryIO(asFormatFour(data))).getOrThrow()

        assertEquals(4, report.formatVersion)
        assertEquals(1, report.assets)
        assertEquals(0, report.attachments)
        assertEquals("", report.lastRestoredBackupSetId)
        // Nothing to pair a files archive with, so nothing is remembered.
        assertNull(graph.prefs.lastRestoredBackupSetId)
        assertNull(vm.state.value.lastRestoredBackupSetId)
    }
}
