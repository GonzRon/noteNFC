package com.loosecannon.notenfc.core.backup

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.testing.InMemoryAttachmentStore
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArtifactsCodecTest {

    private val pdf = ByteArray(4096) { (it % 251).toByte() }
    private val text = "x".repeat(4096).toByteArray()

    private fun planEntry(id: String, bytes: ByteArray, mime: String, ext: String) =
        ArtifactsPlanEntry(
            attachmentId = AttachmentId(id),
            entryName = "artifacts/$id.$ext",
            locator = "assets/a1/$id.$ext",
            sha256 = InMemoryAttachmentStore.sha256Hex(bytes),
            sizeBytes = bytes.size.toLong(),
            mimeType = mime,
        )

    private fun plan(vararg entries: ArtifactsPlanEntry, setId: String = "set-1") = ArtifactsPlan(
        backupSetId = setId, dataFormatVersion = 5, createdAt = 1_726_000_000_000L,
        entries = entries.toList(),
    )

    private fun sources(vararg pairs: Pair<String, ByteArray>): suspend (String) -> ByteArrayInputStream? {
        val map = pairs.toMap()
        return { locator -> map[locator]?.let { ByteArrayInputStream(it) } }
    }

    private fun zipEntries(bytes: ByteArray): List<ZipEntry> = buildList {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                zin.readBytes()
                add(entry)
            }
        }
    }

    @Test fun manifestIsFirstAndEveryEntryRoundTrips() = runTest {
        val a = planEntry("att-1", pdf, "application/pdf", "pdf")
        val b = planEntry("att-2", text, "text/plain", "txt")
        val out = ByteArrayOutputStream()
        val written = ArtifactsCodec.write(
            out, plan(a, b),
            sources(a.locator to pdf, b.locator to text),
        )
        assertEquals(2, written.count)
        assertEquals((pdf.size + text.size).toLong(), written.bytes)
        assertTrue(written.missing.isEmpty() && written.mismatched.isEmpty())
        assertTrue(written.complete)
        assertTrue(written.covers(plan(a, b)))

        val names = zipEntries(out.toByteArray()).map { it.name }
        assertEquals(ArtifactsCodec.MANIFEST_ENTRY, names.first())
        assertEquals(listOf("artifacts/att-1.pdf", "artifacts/att-2.txt"), names.drop(1))

        val seen = LinkedHashMap<String, ByteArray>()
        var manifest: ArtifactsManifest? = null
        val report = ArtifactsCodec.read(
            ByteArrayInputStream(out.toByteArray()),
            onManifest = { manifest = it },
            onEntry = { entry, bytes -> seen[entry.attachmentId] = bytes.readBytes() },
        )
        val read = manifest!!
        assertEquals(1, read.artifactFormatVersion)
        assertEquals(5, read.dataFormatVersion)
        assertEquals("set-1", read.backupSetId)
        assertEquals(1_726_000_000_000L, read.createdAt)
        assertContentEquals(pdf, seen["att-1"])
        assertContentEquals(text, seen["att-2"])
        assertTrue(report.missingEntries.isEmpty())
        assertTrue(report.unexpectedEntries.isEmpty())
    }

    @Test fun compressedTypesAreStoredAndEverythingElseIsDeflated() = runTest {
        val a = planEntry("att-1", pdf, "application/pdf", "pdf")
        val b = planEntry("att-2", text, "text/plain", "txt")
        val out = ByteArrayOutputStream()
        ArtifactsCodec.write(out, plan(a, b), sources(a.locator to pdf, b.locator to text))

        val byName = zipEntries(out.toByteArray()).associateBy { it.name }
        assertEquals(ZipEntry.STORED, byName.getValue("artifacts/att-1.pdf").method)
        assertEquals(pdf.size.toLong(), byName.getValue("artifacts/att-1.pdf").size)
        assertEquals(ZipEntry.DEFLATED, byName.getValue("artifacts/att-2.txt").method)
        // and deflating compressible text actually paid for itself
        assertTrue(byName.getValue("artifacts/att-2.txt").compressedSize < text.size.toLong())
    }

    @Test fun anEmptyPlanStillWritesAManifestOnlyArchive() = runTest {
        val out = ByteArrayOutputStream()
        val written = ArtifactsCodec.write(out, plan(), sources())
        assertEquals(0, written.count)
        assertEquals(0L, written.bytes)
        assertTrue(written.covers(plan()))
        assertEquals(listOf(ArtifactsCodec.MANIFEST_ENTRY), zipEntries(out.toByteArray()).map { it.name })
    }

    @Test fun missingBytesAndDriftedBytesAreLeftOutAndReported() = runTest {
        val gone = planEntry("att-1", pdf, "application/pdf", "pdf")
        val drifted = planEntry("att-2", text, "text/plain", "txt")
        val out = ByteArrayOutputStream()
        val written = ArtifactsCodec.write(
            out, plan(gone, drifted),
            // att-1's bytes are not in the store; att-2's are there but are not what the row says
            sources(drifted.locator to "different".toByteArray()),
        )
        assertEquals(0, written.count)
        assertEquals(listOf(AttachmentId("att-1")), written.missing)
        assertEquals(listOf(AttachmentId("att-2")), written.mismatched)
        assertEquals(false, written.complete)
        assertEquals(false, written.covers(plan(gone, drifted)))
        assertEquals(listOf(ArtifactsCodec.MANIFEST_ENTRY), zipEntries(out.toByteArray()).map { it.name })
    }

    @Test fun aNewerArtifactFormatIsRefusedBeforeAnyEntryIsRead() = runTest {
        val a = planEntry("att-1", pdf, "application/pdf", "pdf")
        val out = ByteArrayOutputStream()
        ArtifactsCodec.write(out, plan(a), sources(a.locator to pdf))
        val bumped = bumpArtifactFormat(out.toByteArray(), to = 2)

        var entries = 0
        var manifests = 0
        val boom = assertFailsWith<ArtifactsNewerFormat> {
            ArtifactsCodec.read(
                ByteArrayInputStream(bumped),
                onManifest = { manifests += 1 },
                onEntry = { _, _ -> entries += 1 },
            )
        }
        assertEquals(2, boom.found)
        assertEquals(1, boom.supported)
        assertEquals(0, entries)
        assertEquals(0, manifests)
    }

    @Test fun anArchiveThatDoesNotStartWithTheManifestIsCorrupt() = runTest {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("artifacts/att-1.pdf"))
            zos.write(pdf)
            zos.closeEntry()
        }
        assertFailsWith<BackupCorrupt> {
            ArtifactsCodec.read(ByteArrayInputStream(out.toByteArray()), onManifest = { }, onEntry = { _, _ -> })
        }
    }

    @Test fun anEmptyArchiveIsCorrupt() = runTest {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { }
        assertFailsWith<BackupCorrupt> {
            ArtifactsCodec.read(ByteArrayInputStream(out.toByteArray()), onManifest = { }, onEntry = { _, _ -> })
        }
    }

    @Test fun anUnreadableManifestIsCorrupt() = runTest {
        val entries = LinkedHashMap<String, ByteArray>()
        entries[ArtifactsCodec.MANIFEST_ENTRY] = "{ not json".toByteArray(Charsets.UTF_8)
        assertFailsWith<BackupCorrupt> {
            ArtifactsCodec.read(ByteArrayInputStream(rezip(entries)), onManifest = { }, onEntry = { _, _ -> })
        }
    }

    @Test fun theReaderNamesManifestEntriesWithNoBytesAndBytesTheManifestDoesNotName() = runTest {
        val a = planEntry("att-1", pdf, "application/pdf", "pdf")
        val b = planEntry("att-2", text, "text/plain", "txt")
        val out = ByteArrayOutputStream()
        ArtifactsCodec.write(out, plan(a, b), sources(a.locator to pdf, b.locator to text))

        // att-2's bytes are dropped from the archive, and a stray entry nobody planned is added
        val entries = unzip(out.toByteArray())
        entries.remove("artifacts/att-2.txt")
        entries["artifacts/att-9.bin"] = "stray".toByteArray()

        val seen = mutableListOf<String>()
        val report = ArtifactsCodec.read(
            ByteArrayInputStream(rezip(entries)),
            onManifest = { },
            onEntry = { entry, _ -> seen += entry.attachmentId },
        )
        assertEquals(listOf("att-1"), seen)
        assertEquals(listOf("artifacts/att-2.txt"), report.missingEntries)
        assertEquals(listOf("artifacts/att-9.bin"), report.unexpectedEntries)
    }

    @Test fun anEntryCallbackThatIgnoresItsStreamDoesNotDerailTheLoop() = runTest {
        val a = planEntry("att-1", pdf, "application/pdf", "pdf")
        val b = planEntry("att-2", text, "text/plain", "txt")
        val out = ByteArrayOutputStream()
        ArtifactsCodec.write(out, plan(a, b), sources(a.locator to pdf, b.locator to text))

        val seen = mutableListOf<String>()
        // the first callback closes its stream without reading it; the second must still get bytes
        ArtifactsCodec.read(
            ByteArrayInputStream(out.toByteArray()),
            onManifest = { },
            onEntry = { entry, bytes ->
                if (entry.attachmentId == "att-1") bytes.close()
                else assertContentEquals(text, bytes.readBytes())
                seen += entry.attachmentId
            },
        )
        assertEquals(listOf("att-1", "att-2"), seen)
    }

    @Test fun entryNamesTakeTheirExtensionFromTheLocator() {
        assertEquals(
            "artifacts/att-1.jpg",
            ArtifactsCodec.entryName(AttachmentId("att-1"), "assets/a1/att-1.jpg"),
        )
        assertEquals(
            "artifacts/att-1.bin",
            ArtifactsCodec.entryName(AttachmentId("att-1"), "assets/a1/att-1"),
        )
    }

    // --- zip helpers, mirroring BackupCodecTest's trio -----------------------------------------

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val entry: ZipEntry = zin.nextEntry ?: break
                out[entry.name] = zin.readBytes()
                zin.closeEntry()
            }
        }
        return out
    }

    private fun rezip(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, payload) ->
                val e = ZipEntry(name)
                e.time = 0L
                zos.putNextEntry(e)
                zos.write(payload)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    /** Rewrites the manifest's `artifactFormatVersion`, keeping every other entry as it was. */
    private fun bumpArtifactFormat(bytes: ByteArray, to: Int): ByteArray {
        val entries = unzip(bytes)
        val manifest = String(entries.getValue(ArtifactsCodec.MANIFEST_ENTRY), Charsets.UTF_8)
            .replace(
                "\"artifactFormatVersion\": ${ArtifactsCodec.ARTIFACT_FORMAT_VERSION}",
                "\"artifactFormatVersion\": $to",
            )
        entries[ArtifactsCodec.MANIFEST_ENTRY] = manifest.toByteArray(Charsets.UTF_8)
        return rezip(entries)
    }
}
