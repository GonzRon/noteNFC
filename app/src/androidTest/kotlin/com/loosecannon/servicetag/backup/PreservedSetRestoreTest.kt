package com.loosecannon.servicetag.backup

import com.loosecannon.servicetag.core.backup.BackupCodec
import com.loosecannon.servicetag.ui.app
import com.loosecannon.servicetag.ui.clearInstall
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * 2.6 proof (6) — the real pre-split set, restored into a 2.6 install. The link row the owner has
 * had since before the split must arrive, stay, and leave again in the next export's
 * `externalLinks` array byte-for-byte. Nothing from the set is printed: the assertions compare, the
 * report counts.
 *
 * Skipped unless the controller has pushed the archive; ordinary CI never runs connected tests.
 */
class PreservedSetRestoreTest {

    private val archive = File("/data/local/tmp/servicetag-proof-data.zip")

    @Before fun freshInstall() {
        assumeTrue("the preserved set was not pushed", archive.canRead())
        clearInstall()
    }

    /** `BackupCodec.readEntries` is private, so the archive's `data.json` is unzipped here. */
    private fun dataJson(archive: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == BackupCodec.DATA_ENTRY) return zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        error("no ${BackupCodec.DATA_ENTRY} in the archive")
    }

    /** The `externalLinks` array as it appears in a data.json, sliced by its neighbours' keys. */
    private fun linksSlice(json: String): String =
        json.substring(json.indexOf("\"externalLinks\""), json.indexOf("\"measurementDefinitions\""))

    @Test fun thePreservedSetRestoresAndItsLinkRowsComeBackOutUnchanged() {
        val bytes = archive.readBytes()
        val preserved = BackupCodec.decode(bytes)
        assertEquals(BackupCodec.FORMAT_VERSION, preserved.manifest.formatVersion)
        assertEquals(5, preserved.manifest.formatVersion)

        val graph = app.graph
        val report = runBlocking { graph.importBackupReplace.run(bytes) }
        assertEquals(preserved.data.externalLinks.size, report.links)
        assertEquals(preserved.data.externalLinks.size, runBlocking { graph.links.all().size })

        // `ExportBackupSet.run(): BackupSet(data, plan)` — the data archive's bytes are `data`.
        val reexported = runBlocking { graph.exportBackupSet.run() }
        val out = BackupCodec.decode(reexported.data)
        assertEquals(preserved.data.externalLinks, out.data.externalLinks)
        // The manifest legitimately differs (backupSetId, createdAt, appVersion — the C.8 fields);
        // the tombstone slice of data.json must not.
        assertEquals(linksSlice(dataJson(bytes)), linksSlice(dataJson(reexported.data)))
    }
}
