package com.loosecannon.notenfc.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class BackupCodecTest {

    // --- fixture -------------------------------------------------------------------------------

    private fun fixture(): BackupData = BackupData(
        assets = listOf(
            AssetDto("a1", "Furnace", "basement unit", "hvac", "filter 16x25", "ACTIVE", 100L, 200L),
            AssetDto("a2", "Mower", "", "yard", "", "ARCHIVED", 101L, 201L),
            AssetDto("a3", "Water heater", "40 gal", "plumbing", "", "RETIRED", 102L, 202L),
        ),
        nfcTags = listOf(
            // bound to an asset, legacy payload
            NfcTagDto("t1", "LEGACY_MD5", "9e107d9d372bb6826bd81d3542a419d6", "a1", null, "ACTIVE", "furnace tag", "04A224B2", 300L, 400L, 103L, 203L),
            // bound to a standalone link
            NfcTagDto("t2", "V1", "key-t2", null, "l3", "ACTIVE", null, null, null, null, 104L, 204L),
            // unbound, everything optional is null
            NfcTagDto("t3", "V1", "key-t3", null, null, "UNBOUND", null, null, null, null, 105L, 205L),
            // lost tag still pointing at an asset
            NfcTagDto("t4", "V1", "key-t4", "a2", null, "LOST", "mower", null, 301L, null, 106L, 206L),
        ),
        externalLinks = listOf(
            ExternalLinkDto("l1", "a1", "JOPLIN", "Furnace notes", "joplin://x-callback-url/openNote?id=abc", 107L, 500L, 207L),
            ExternalLinkDto("l2", "a2", "WEB", "Manual", "https://example.invalid/mower.pdf", 108L, null, 208L),
            // standalone link (no asset)
            ExternalLinkDto("l3", null, "OBSIDIAN", "Loose note", "obsidian://open?vault=v&file=f", 109L, null, 209L),
        ),
    )

    // --- zip helpers used only by the tampering tests ------------------------------------------

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

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Replace data.json and repair the manifest hash, so the corruption under test is the only one. */
    private fun resealed(bytes: ByteArray, newData: ByteArray): ByteArray {
        val entries = unzip(bytes)
        val oldHash = sha256Hex(entries.getValue(BackupCodec.DATA_ENTRY))
        val manifest = String(entries.getValue(BackupCodec.MANIFEST_ENTRY), Charsets.UTF_8)
            .replace(oldHash, sha256Hex(newData))
        entries[BackupCodec.MANIFEST_ENTRY] = manifest.toByteArray(Charsets.UTF_8)
        entries[BackupCodec.DATA_ENTRY] = newData
        return rezip(entries)
    }

    private fun encoded(data: BackupData = fixture()): ByteArray =
        BackupCodec.encode(data, appVersion = "2.0", schemaVersion = 1, createdAt = 1_726_000_000_000L)

    // --- tests ---------------------------------------------------------------------------------

    @Test
    fun `round trip preserves the whole fixture`() {
        val data = fixture()
        val decoded = BackupCodec.decode(encoded(data))
        assertEquals(data, decoded.data)
    }

    @Test
    fun `ids are preserved verbatim`() {
        val decoded = BackupCodec.decode(encoded())
        assertEquals(listOf("a1", "a2", "a3"), decoded.data.assets.map { it.id })
        assertEquals(listOf("t1", "t2", "t3", "t4"), decoded.data.nfcTags.map { it.id })
        assertEquals(listOf("l1", "l2", "l3"), decoded.data.externalLinks.map { it.id })
        assertEquals("l3", decoded.data.nfcTags.first { it.id == "t2" }.linkId)
        assertEquals("a1", decoded.data.nfcTags.first { it.id == "t1" }.assetId)
    }

    @Test
    fun `zip contains exactly manifest and data`() {
        val names = unzip(encoded()).keys.toList()
        assertEquals(listOf(BackupCodec.MANIFEST_ENTRY, BackupCodec.DATA_ENTRY), names)
    }

    @Test
    fun `manifest carries counts and provenance`() {
        val manifest = BackupCodec.decode(encoded()).manifest
        assertEquals(BackupCodec.FORMAT_VERSION, manifest.formatVersion)
        assertEquals("2.0", manifest.appVersion)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(1_726_000_000_000L, manifest.createdAt)
        assertEquals(mapOf("assets" to 3, "nfcTags" to 4, "externalLinks" to 3), manifest.counts)
    }

    @Test
    fun `manifest hash matches the data entry bytes`() {
        val bytes = encoded()
        val entries = unzip(bytes)
        val manifest = BackupCodec.decode(bytes).manifest
        assertEquals(sha256Hex(entries.getValue(BackupCodec.DATA_ENTRY)), manifest.dataSha256)
    }

    @Test
    fun `output is deterministic and sorted by id`() {
        val shuffled = fixture().let { f ->
            BackupData(f.assets.reversed(), f.nfcTags.reversed(), f.externalLinks.reversed())
        }
        assertContentEquals(encoded(), encoded(shuffled))
        val json = String(unzip(encoded(shuffled)).getValue(BackupCodec.DATA_ENTRY), Charsets.UTF_8)
        // each list is checked inside its own section: ids also appear as references elsewhere
        val assetsJson = json.substring(json.indexOf("\"assets\""), json.indexOf("\"nfcTags\""))
        val tagsJson = json.substring(json.indexOf("\"nfcTags\""), json.indexOf("\"externalLinks\""))
        val linksJson = json.substring(json.indexOf("\"externalLinks\""))
        assertTrue(assetsJson.indexOf("\"a1\"") < assetsJson.indexOf("\"a3\""), "assets not sorted by id")
        assertTrue(tagsJson.indexOf("\"t1\"") < tagsJson.indexOf("\"t4\""), "tags not sorted by id")
        assertTrue(linksJson.indexOf("\"l1\"") < linksJson.indexOf("\"l3\""), "links not sorted by id")
    }

    @Test
    fun `a tampered data entry is corrupt`() {
        val entries = unzip(encoded())
        val data = entries.getValue(BackupCodec.DATA_ENTRY).copyOf()
        val i = String(data, Charsets.UTF_8).indexOf("Furnace")
        data[i] = 'G'.code.toByte()
        entries[BackupCodec.DATA_ENTRY] = data
        val e = assertFailsWith<BackupCorrupt> { BackupCodec.decode(rezip(entries)) }
        assertTrue(e.message!!.contains("sha256") || e.message!!.contains("hash"), "unhelpful: ${e.message}")
    }

    @Test
    fun `a newer format version is refused`() {
        val entries = unzip(encoded())
        val manifest = String(entries.getValue(BackupCodec.MANIFEST_ENTRY), Charsets.UTF_8)
            .replace(Regex("\"formatVersion\"\\s*:\\s*1"), "\"formatVersion\": 2")
        entries[BackupCodec.MANIFEST_ENTRY] = manifest.toByteArray(Charsets.UTF_8)
        val e = assertFailsWith<BackupNewerFormat> { BackupCodec.decode(rezip(entries)) }
        assertEquals(2, e.found)
        assertEquals(1, e.supported)
    }

    @Test
    fun `a missing data entry is corrupt`() {
        val entries = unzip(encoded())
        entries.remove(BackupCodec.DATA_ENTRY)
        assertFailsWith<BackupCorrupt> { BackupCodec.decode(rezip(entries)) }
    }

    @Test
    fun `a missing manifest entry is corrupt`() {
        val entries = unzip(encoded())
        entries.remove(BackupCodec.MANIFEST_ENTRY)
        assertFailsWith<BackupCorrupt> { BackupCodec.decode(rezip(entries)) }
    }

    @Test
    fun `bytes that are not a zip are corrupt`() {
        assertFailsWith<BackupCorrupt> { BackupCodec.decode("not a zip at all".toByteArray()) }
    }

    @Test
    fun `unparsable data json is corrupt`() {
        val broken = "{ this is not json".toByteArray(Charsets.UTF_8)
        assertFailsWith<BackupCorrupt> { BackupCodec.decode(resealed(encoded(), broken)) }
    }

    @Test
    fun `an unknown asset status is corrupt`() {
        val data = String(unzip(encoded()).getValue(BackupCodec.DATA_ENTRY), Charsets.UTF_8)
            .replace("\"ARCHIVED\"", "\"MULCHED\"")
        assertFailsWith<BackupCorrupt> { BackupCodec.decode(resealed(encoded(), data.toByteArray(Charsets.UTF_8))) }
    }

    @Test
    fun `an unknown payload format is corrupt`() {
        val data = String(unzip(encoded()).getValue(BackupCodec.DATA_ENTRY), Charsets.UTF_8)
            .replace("\"LEGACY_MD5\"", "\"LEGACY_SHA9\"")
        assertFailsWith<BackupCorrupt> { BackupCodec.decode(resealed(encoded(), data.toByteArray(Charsets.UTF_8))) }
    }

    @Test
    fun `an unknown link kind is corrupt`() {
        val data = String(unzip(encoded()).getValue(BackupCodec.DATA_ENTRY), Charsets.UTF_8)
            .replace("\"OBSIDIAN\"", "\"SCRIVENER\"")
        assertFailsWith<BackupCorrupt> { BackupCodec.decode(resealed(encoded(), data.toByteArray(Charsets.UTF_8))) }
    }

    @Test
    fun `decode of encode is identity over fifty random fixtures`() {
        val rng = Random(42)
        repeat(50) { round ->
            val data = randomData(rng)
            val decoded = BackupCodec.decode(
                BackupCodec.encode(data, "2.0", 1, rng.nextLong(0, 2_000_000_000_000L)),
            )
            assertEquals(data, decoded.data, "round $round did not survive the round trip")
        }
    }

    // --- random fixture generation (ids pre-sorted, so the identity is literal) -----------------

    private fun id(prefix: String, n: Int): String = "$prefix-%04d".format(n)

    private fun randomData(rng: Random): BackupData {
        val assetIds = (0 until rng.nextInt(0, 6)).map { id("a", it) }
        val linkIds = (0 until rng.nextInt(0, 6)).map { id("l", it) }
        val assets = assetIds.map { aid ->
            AssetDto(
                id = aid,
                name = rng.word(),
                description = if (rng.nextBoolean()) "" else rng.word(),
                category = rng.word(),
                notes = if (rng.nextBoolean()) "" else rng.word(),
                status = rng.pick(listOf("ACTIVE", "ARCHIVED", "RETIRED")),
                createdAt = rng.nextLong(0, 2_000_000_000_000L),
                updatedAt = rng.nextLong(0, 2_000_000_000_000L),
            )
        }
        val links = linkIds.map { lid ->
            ExternalLinkDto(
                id = lid,
                assetId = if (assetIds.isEmpty() || rng.nextBoolean()) null else rng.pick(assetIds),
                kind = rng.pick(listOf("JOPLIN", "OBSIDIAN", "LOGSEQ", "WEB", "OTHER")),
                label = rng.word(),
                uri = "scheme://${rng.word()}",
                createdAt = rng.nextLong(0, 2_000_000_000_000L),
                lastOpenedAt = if (rng.nextBoolean()) null else rng.nextLong(0, 2_000_000_000_000L),
                updatedAt = rng.nextLong(0, 2_000_000_000_000L),
            )
        }
        val tags = (0 until rng.nextInt(0, 8)).map { i ->
            val target = rng.nextInt(0, 3)
            NfcTagDto(
                id = id("t", i),
                payloadFormat = rng.pick(listOf("LEGACY_MD5", "V1")),
                payloadKey = rng.word(),
                assetId = if (target == 0 && assetIds.isNotEmpty()) rng.pick(assetIds) else null,
                linkId = if (target == 1 && linkIds.isNotEmpty()) rng.pick(linkIds) else null,
                status = rng.pick(listOf("ACTIVE", "UNBOUND", "LOST", "RETIRED")),
                label = if (rng.nextBoolean()) null else rng.word(),
                physicalUid = if (rng.nextBoolean()) null else rng.word(),
                writtenAt = if (rng.nextBoolean()) null else rng.nextLong(0, 2_000_000_000_000L),
                lastScannedAt = if (rng.nextBoolean()) null else rng.nextLong(0, 2_000_000_000_000L),
                createdAt = rng.nextLong(0, 2_000_000_000_000L),
                updatedAt = rng.nextLong(0, 2_000_000_000_000L),
            )
        }
        return BackupData(assets, tags, links)
    }

    private fun Random.word(): String =
        (0 until nextInt(1, 12)).map { "abcdefghijklmnopqrstuvwxyz ".random(this) }.joinToString("").trim()
            .ifEmpty { "x" }

    private fun <T> Random.pick(items: List<T>): T = items[nextInt(items.size)]
}
