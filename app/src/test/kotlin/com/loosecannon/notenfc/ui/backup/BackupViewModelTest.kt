package com.loosecannon.notenfc.ui.backup

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.ports.BackupIO
import com.loosecannon.notenfc.testing.FakeGraph
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Export and import against a Room-backed [FakeGraph]. The [BackupIO] port is a parameter rather
 * than a `Uri` precisely so these two can run without SAF: one destination keeps the bytes, the
 * other refuses to take them, and the difference has to show up in the preferences.
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

    private fun viewModel() =
        BackupViewModel(graph.exportBackup, graph.importBackupReplace, graph.prefs, graph.clock)

    /** A document the user picked that keeps what it is given. */
    private class MemoryIO : BackupIO {
        var bytes: ByteArray = ByteArray(0)
        override suspend fun write(bytes: ByteArray) { this.bytes = bytes }
        override suspend fun read(): ByteArray = bytes
    }

    /** A full disk, a revoked permission, a provider that went away mid-write. */
    private class ThrowingIO : BackupIO {
        override suspend fun write(bytes: ByteArray): Unit = throw IOException("no space left")
        override suspend fun read(): ByteArray = throw IOException("no space left")
    }

    @Test fun exportMarksPrefsOnlyOnSuccess() = runTest {
        graph.createAsset.run("Pool pump", "Water")
        val vm = viewModel()

        val failed = vm.export(ThrowingIO())
        assertTrue(failed.isFailure)
        // The nudge is a promise about a file that exists; a failed write made no file.
        assertNull(graph.prefs.lastBackupAt)
        assertNull(vm.state.value.lastBackupAt)

        graph.now = 5_000L
        val destination = MemoryIO()
        val exported = vm.export(destination)
        assertEquals(destination.bytes.size, exported.getOrThrow())
        assertTrue(destination.bytes.isNotEmpty())
        assertEquals(5_000L, graph.prefs.lastBackupAt)
        assertEquals(5_000L, vm.state.value.lastBackupAt)
    }

    @Test fun importReplaceReturnsReport() = runTest {
        val pump = graph.createAsset.run("Pool pump", "Water")
        val vm = viewModel()

        val destination = MemoryIO()
        vm.export(destination).getOrThrow()

        // Everything the backup knows about is gone before it is read back.
        graph.uow.write {
            graph.tags.deleteAll()
            graph.links.deleteAll()
            graph.assets.deleteAll()
        }
        assertEquals(emptyList<Asset>(), graph.assets.all())

        val report = vm.importReplace(destination).getOrThrow()
        assertEquals(1, report.assets)
        assertEquals(0, report.tags)
        assertEquals(0, report.links)
        assertEquals(1, report.formatVersion)
        assertEquals(listOf(pump.id), graph.assets.all().map(Asset::id))
    }
}
