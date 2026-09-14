package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.BackupCodec
import com.loosecannon.notenfc.core.backup.BackupNewerFormat
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetStatus
import com.loosecannon.notenfc.core.model.ExternalLink
import com.loosecannon.notenfc.core.model.LinkId
import com.loosecannon.notenfc.core.model.LinkKind
import com.loosecannon.notenfc.core.model.PayloadFormat
import com.loosecannon.notenfc.core.model.TagBinding
import com.loosecannon.notenfc.core.model.TagId
import com.loosecannon.notenfc.core.model.TagStatus
import com.loosecannon.notenfc.core.model.TagTarget
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import com.loosecannon.notenfc.core.testing.InMemoryLinkRepository
import com.loosecannon.notenfc.core.testing.InMemoryTagRepository
import com.loosecannon.notenfc.core.testing.RiggedFailure
import java.io.ByteArrayInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class BackupUseCasesTest {

    private class Fakes {
        val assets = InMemoryAssetRepository()
        val tags = InMemoryTagRepository()
        val links = InMemoryLinkRepository()
        val uow = FakeUnitOfWork(assets, tags, links)
    }

    private fun asset(id: String, name: String, status: AssetStatus = AssetStatus.ACTIVE) =
        Asset(AssetId(id), name, "desc $id", "cat", "notes", status, createdAt = 1L, updatedAt = 2L)

    private fun link(id: String, assetId: String?) = ExternalLink(
        id = LinkId(id),
        assetId = assetId?.let(::AssetId),
        kind = LinkKind.JOPLIN,
        label = "label $id",
        uri = "joplin://$id",
        createdAt = 3L,
        lastOpenedAt = null,
        updatedAt = 4L,
    )

    private fun tag(id: String, target: TagTarget, status: TagStatus = TagStatus.ACTIVE) = TagBinding(
        id = TagId(id),
        payloadFormat = if (id == "t1") PayloadFormat.LEGACY_MD5 else PayloadFormat.V1,
        payloadKey = "key-$id",
        target = target,
        status = status,
        label = "tag $id",
        physicalUid = null,
        writtenAt = 5L,
        lastScannedAt = null,
        createdAt = 6L,
        updatedAt = 7L,
    )

    private suspend fun populate(f: Fakes) {
        f.assets.upsert(asset("a1", "Furnace"))
        f.assets.upsert(asset("a2", "Mower", AssetStatus.ARCHIVED))
        f.assets.upsert(asset("a3", "Heater", AssetStatus.RETIRED))
        f.links.upsert(link("l1", "a1"))
        f.links.upsert(link("l2", "a2"))
        f.links.upsert(link("l3", null))
        f.tags.upsert(tag("t1", TagTarget.AssetTarget(AssetId("a1"))))
        f.tags.upsert(tag("t2", TagTarget.LinkTarget(LinkId("l3"))))
        f.tags.upsert(tag("t3", TagTarget.None, TagStatus.UNBOUND))
        f.tags.upsert(tag("t4", TagTarget.AssetTarget(AssetId("a2")), TagStatus.LOST))
    }

    private fun exportOf(f: Fakes, now: Long = 1_726_000_000_000L): ByteArray = runBlocking {
        ExportBackup(f.assets, f.tags, f.links, Clock { now }, appVersion = "2.0", schemaVersion = 1).run()
    }

    private fun importInto(f: Fakes, bytes: ByteArray): ImportReport = runBlocking {
        ImportBackupReplace(f.assets, f.tags, f.links, f.uow).run(bytes)
    }

    @Test
    fun `export then import into empty fakes reproduces contents and ids`() {
        val source = Fakes()
        runBlocking { populate(source) }
        val bytes = exportOf(source)

        val target = Fakes()
        val report = importInto(target, bytes)

        runBlocking {
            assertEquals(source.assets.all().sortedBy { it.id.value }, target.assets.all().sortedBy { it.id.value })
            assertEquals(source.tags.all().sortedBy { it.id.value }, target.tags.all().sortedBy { it.id.value })
            assertEquals(source.links.all().sortedBy { it.id.value }, target.links.all().sortedBy { it.id.value })
        }
        assertEquals(ImportReport(assets = 3, tags = 4, links = 3, formatVersion = 1), report)
    }

    @Test
    fun `tag targets survive the round trip`() {
        val source = Fakes()
        runBlocking { populate(source) }
        val target = Fakes()
        importInto(target, exportOf(source))

        runBlocking {
            assertEquals(TagTarget.AssetTarget(AssetId("a1")), target.tags.get(TagId("t1"))!!.target)
            assertEquals(TagTarget.LinkTarget(LinkId("l3")), target.tags.get(TagId("t2"))!!.target)
            assertEquals(TagTarget.None, target.tags.get(TagId("t3"))!!.target)
            assertEquals(PayloadFormat.LEGACY_MD5, target.tags.get(TagId("t1"))!!.payloadFormat)
            assertEquals(1, target.links.standalone().size)
        }
    }

    @Test
    fun `export uses the clock for createdAt`() {
        val f = Fakes()
        runBlocking { populate(f) }
        val manifest = BackupCodec.decode(exportOf(f, now = 777_000L)).manifest
        assertEquals(777_000L, manifest.createdAt)
        assertEquals("2.0", manifest.appVersion)
    }

    @Test
    fun `import replaces pre-existing rows`() {
        val source = Fakes()
        runBlocking { populate(source) }
        val bytes = exportOf(source)

        val target = Fakes()
        runBlocking {
            target.assets.upsert(asset("old-asset", "Stale"))
            target.links.upsert(link("old-link", null))
            target.tags.upsert(tag("old-tag", TagTarget.None))
        }

        importInto(target, bytes)

        runBlocking {
            assertNull(target.assets.get(AssetId("old-asset")))
            assertNull(target.links.get(LinkId("old-link")))
            assertNull(target.tags.get(TagId("old-tag")))
            assertEquals(3, target.assets.all().size)
            assertEquals(4, target.tags.all().size)
            assertEquals(3, target.links.all().size)
        }
    }

    @Test
    fun `a failed insert rolls the whole import back`() {
        val source = Fakes()
        runBlocking { populate(source) }
        val bytes = exportOf(source)

        val target = Fakes()
        runBlocking {
            target.assets.upsert(asset("keep-a", "Keep me"))
            target.links.upsert(link("keep-l", null))
            target.tags.upsert(tag("keep-t", TagTarget.None))
        }
        val before = runBlocking {
            Triple(target.assets.all(), target.tags.all(), target.links.all())
        }

        target.tags.failOnUpsert = 3 + 1 // the pre-existing keep-t upsert counts, so this is the third imported tag

        assertFailsWith<RiggedFailure> { importInto(target, bytes) }

        runBlocking {
            assertEquals(before.first, target.assets.all())
            assertEquals(before.second, target.tags.all())
            assertEquals(before.third, target.links.all())
        }
        assertEquals(1, target.uow.rollbacks)
        assertEquals(0, target.uow.commits)
    }

    @Test
    fun `importing a newer format changes nothing`() {
        val source = Fakes()
        runBlocking { populate(source) }
        val bytes = bumpFormatVersion(exportOf(source))

        val target = Fakes()
        runBlocking { target.assets.upsert(asset("untouched", "Untouched")) }

        val e = assertFailsWith<BackupNewerFormat> { importInto(target, bytes) }
        assertEquals(2, e.found)
        assertEquals(1, e.supported)

        runBlocking {
            assertEquals(listOf("untouched"), target.assets.all().map { it.id.value })
            assertTrue(target.tags.all().isEmpty())
            assertTrue(target.links.all().isEmpty())
        }
        assertEquals(0, target.uow.commits)
        assertEquals(0, target.uow.rollbacks) // decode happens outside the transaction
    }

    @Test
    fun `exporting empty repositories yields an empty but valid backup`() {
        val f = Fakes()
        val decoded = BackupCodec.decode(exportOf(f))
        assertTrue(decoded.data.assets.isEmpty())
        assertTrue(decoded.data.nfcTags.isEmpty())
        assertTrue(decoded.data.externalLinks.isEmpty())
        assertEquals(mapOf("assets" to 0, "nfcTags" to 0, "externalLinks" to 0), decoded.manifest.counts)

        val target = Fakes()
        runBlocking { target.assets.upsert(asset("gone", "Gone")) }
        val report = importInto(target, exportOf(f))
        assertEquals(ImportReport(0, 0, 0, 1), report)
        runBlocking { assertTrue(target.assets.all().isEmpty()) }
    }

    // --- helper: rewrite the manifest to claim format version 2 --------------------------------

    private fun bumpFormatVersion(bytes: ByteArray): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            while (true) {
                val e: ZipEntry = zin.nextEntry ?: break
                entries[e.name] = zin.readBytes()
                zin.closeEntry()
            }
        }
        val manifest = String(entries.getValue(BackupCodec.MANIFEST_ENTRY), Charsets.UTF_8)
            .replace(Regex("\"formatVersion\"\\s*:\\s*1"), "\"formatVersion\": 2")
        entries[BackupCodec.MANIFEST_ENTRY] = manifest.toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            entries.forEach { (name, payload) ->
                zos.putNextEntry(ZipEntry(name).also { it.time = 0L })
                zos.write(payload)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
