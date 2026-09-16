package com.loosecannon.notenfc.core.backup

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.MimeTypes
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One row's bytes, as the archive names them. */
@Serializable
data class ArtifactEntry(
    val attachmentId: String,
    val entryName: String,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
)

@Serializable
data class ArtifactsManifest(
    val artifactFormatVersion: Int,
    /** The cross-reference to the data archive's `formatVersion` (spec §11.4). */
    val dataFormatVersion: Int,
    val backupSetId: String,
    val createdAt: Long,
    val entries: List<ArtifactEntry>,
)

/** What `ExportBackupSet` hands the app: which rows' bytes to stream, and from where. */
data class ArtifactsPlanEntry(
    val attachmentId: AttachmentId,
    val entryName: String,
    val locator: String,
    val sha256: String,
    val sizeBytes: Long,
    val mimeType: String,
)

data class ArtifactsPlan(
    val backupSetId: String,
    val dataFormatVersion: Int,
    val createdAt: Long,
    val entries: List<ArtifactsPlanEntry>,
)

/**
 * What the write actually managed. `missing`/`mismatched` rows are left out of the manifest —
 * and the export treats any of them as a failed backup (owner's ruling, spec §7.3): a set that
 * lists eight attachments and carries seven is not a restorable set.
 */
data class ArtifactsWritten(
    val count: Int,
    val bytes: Long,
    val missing: List<AttachmentId>,
    val mismatched: List<AttachmentId>,
) {
    val complete: Boolean get() = missing.isEmpty() && mismatched.isEmpty()

    /** True only when every planned row was written with its planned size. */
    fun covers(plan: ArtifactsPlan): Boolean =
        complete && count == plan.entries.size && bytes == plan.entries.sumOf { it.sizeBytes }
}

/**
 * What the read saw that the manifest did not promise, or promised and did not carry. Both are
 * damage: neither is silently ignored, so a restore can tell the person which bytes are gone.
 */
data class ArtifactsReadReport(
    val missingEntries: List<String>,
    val unexpectedEntries: List<String>,
)

/**
 * Zip-level damage is the reader's business, not the caller's: a truncated or garbage archive
 * arrives as `EOFException`/`ZipException` from the stream and leaves as [BackupCorrupt], the
 * family the restore is built on. Only operations on the zip stream itself are wrapped — a
 * callback's own failure (`ArtifactsSetMismatch`, a `StoreIoException` from a put) travels intact.
 */
private inline fun <T> readingZip(block: () -> T): T =
    try {
        block()
    } catch (e: ZipException) {
        throw BackupCorrupt("artifacts archive is not readable: ${e.message}")
    } catch (e: EOFException) {
        throw BackupCorrupt("artifacts archive is not readable: ${e.message}")
    }

/**
 * Artifact format 1: a ZIP whose first entry is `manifest.json` and whose remaining entries are
 * `artifacts/<attachment-id>.<ext>`, one per MANAGED row.
 *
 * Bytes are streamed both ways and never materialised — this archive is the only thing in the
 * product that can be hundreds of megabytes. Two consequences shape the API:
 *
 *  - Already-compressed payloads (`MimeTypes.isCompressed`) go in STORED, which means the entry's
 *    size and CRC-32 must be known *before* the first byte is written. So [write] makes two
 *    passes: pass one opens every source to compute its CRC, its length and its digest; pass two
 *    opens each one again and copies it. Nothing is buffered in memory between them.
 *  - The manifest is the first entry (a reader must be able to plan before it unpacks), and it is
 *    built from pass one's results, so a row whose bytes are gone or whose bytes no longer hash to
 *    what the row claims is simply not in the manifest and not in the archive. It is reported.
 */
object ArtifactsCodec {
    const val ARTIFACT_FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val ENTRY_PREFIX = "artifacts/"
    private const val BUFFER = 64 * 1024

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    /** `artifacts/<id>.<ext>`, the extension taken from the locator so the two always agree. */
    fun entryName(id: AttachmentId, locator: String): String =
        ENTRY_PREFIX + id.value + "." + locator.substringAfterLast('.', "bin")

    suspend fun write(
        sink: OutputStream,
        plan: ArtifactsPlan,
        open: suspend (locator: String) -> InputStream?,
    ): ArtifactsWritten {
        val missing = mutableListOf<AttachmentId>()
        val mismatched = mutableListOf<AttachmentId>()
        val resolved = mutableListOf<Pair<ArtifactsPlanEntry, Long>>()   // entry to crc

        // pass one: what is really there, and does it still hash to what the row says?
        plan.entries.forEach { entry ->
            val source = open(entry.locator)
            if (source == null) {
                missing += entry.attachmentId
                return@forEach
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val crc = CRC32()
            var size = 0L
            DigestInputStream(source, digest).use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    crc.update(buffer, 0, read)
                    size += read
                }
            }
            val hex = digest.digest().joinToString("") { b -> "%02x".format(b) }
            if (hex != entry.sha256 || size != entry.sizeBytes) {
                mismatched += entry.attachmentId
                return@forEach
            }
            resolved += entry to crc.value
        }

        val manifest = ArtifactsManifest(
            artifactFormatVersion = ARTIFACT_FORMAT_VERSION,
            dataFormatVersion = plan.dataFormatVersion,
            backupSetId = plan.backupSetId,
            createdAt = plan.createdAt,
            entries = resolved.map { (entry, _) ->
                ArtifactEntry(
                    attachmentId = entry.attachmentId.value,
                    entryName = entry.entryName,
                    sha256 = entry.sha256,
                    sizeBytes = entry.sizeBytes,
                    mimeType = entry.mimeType,
                )
            },
        )

        var writtenBytes = 0L
        ZipOutputStream(sink).use { zos ->
            val manifestBytes = json
                .encodeToString(ArtifactsManifest.serializer(), manifest)
                .toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry(MANIFEST_ENTRY).also { it.time = plan.createdAt })
            zos.write(manifestBytes)
            zos.closeEntry()

            resolved.forEach { (entry, crc) ->
                val zipEntry = ZipEntry(entry.entryName).also { it.time = plan.createdAt }
                if (MimeTypes.isCompressed(entry.mimeType)) {
                    zipEntry.method = ZipEntry.STORED
                    zipEntry.size = entry.sizeBytes
                    zipEntry.compressedSize = entry.sizeBytes
                    zipEntry.crc = crc
                } else {
                    zipEntry.method = ZipEntry.DEFLATED
                }
                // Pass two trusts nothing pass one measured: the entry is already in the archive
                // by the time a drifted source shows itself, so a difference is a failed write,
                // never something to report and keep.
                writtenBytes += try {
                    zos.putNextEntry(zipEntry)
                    val source = open(entry.locator)
                        ?: throw ArtifactsWriteFailed("artifacts: ${entry.locator} vanished mid-export")
                    val copied = source.use { it.copyTo(zos, BUFFER) }
                    if (copied != entry.sizeBytes) {
                        throw ArtifactsWriteFailed(
                            "artifacts: ${entry.locator} changed mid-export: " +
                                "wrote $copied bytes, planned ${entry.sizeBytes}",
                        )
                    }
                    zos.closeEntry()
                    copied
                } catch (e: ZipException) {
                    // A STORED entry whose source grew or shrank between the passes: the JDK
                    // refuses the entry against the size and CRC pass one declared.
                    throw ArtifactsWriteFailed(
                        "artifacts: ${entry.locator} could not be written: ${e.message}",
                        e,
                    )
                }
            }
        }
        return ArtifactsWritten(
            count = resolved.size,
            bytes = writtenBytes,
            missing = missing,
            mismatched = mismatched,
        )
    }

    /**
     * Streams the archive. [onManifest] runs first and may throw to refuse the whole file;
     * [onEntry] is then called once per `artifacts/` entry with a stream bounded to that entry.
     * A callback that does not read its stream is fine — `nextEntry` skips the remainder.
     *
     * The returned report names the two kinds of damage the loop can see: a manifest entry with
     * no bytes in the archive, and bytes the manifest never named.
     */
    suspend fun read(
        source: InputStream,
        onManifest: suspend (ArtifactsManifest) -> Unit,
        onEntry: suspend (ArtifactEntry, InputStream) -> Unit,
    ): ArtifactsReadReport {
        val seen = mutableSetOf<String>()
        val unexpected = mutableListOf<String>()
        val manifest: ArtifactsManifest
        ZipInputStream(source).use { zin ->
            val first = readingZip { zin.nextEntry }
                ?: throw BackupCorrupt("artifacts archive has no entries")
            if (first.name != MANIFEST_ENTRY) {
                throw BackupCorrupt("artifacts archive must start with $MANIFEST_ENTRY, found ${first.name}")
            }
            val manifestBytes = readingZip { zin.readBytes() }
            manifest = try {
                json.decodeFromString(ArtifactsManifest.serializer(), String(manifestBytes, Charsets.UTF_8))
            } catch (e: kotlinx.serialization.SerializationException) {
                throw BackupCorrupt("$MANIFEST_ENTRY is not readable: ${e.message}")
            }
            if (manifest.artifactFormatVersion > ARTIFACT_FORMAT_VERSION) {
                throw ArtifactsNewerFormat(manifest.artifactFormatVersion, ARTIFACT_FORMAT_VERSION)
            }
            onManifest(manifest)
            val byName = manifest.entries.associateBy { it.entryName }
            while (true) {
                val zipEntry = readingZip { zin.nextEntry } ?: break
                val entry = byName[zipEntry.name]
                if (entry == null) {
                    // not in the manifest: not ours, but worth saying so rather than ignoring
                    if (!zipEntry.isDirectory) unexpected += zipEntry.name
                    continue
                }
                seen += zipEntry.name
                onEntry(entry, NonClosing(zin))
            }
        }
        return ArtifactsReadReport(
            missingEntries = manifest.entries.map { it.entryName }.filterNot { it in seen },
            unexpectedEntries = unexpected,
        )
    }

    /**
     * So a callback's `use {}` cannot close the whole zip stream out from under the loop. Reads
     * go through [readingZip] as well: a truncation inside an entry's bytes is zip-level damage
     * wherever it surfaces, even though the read was asked for from inside a callback.
     */
    private class NonClosing(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = readingZip { delegate.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            readingZip { delegate.read(b, off, len) }
        override fun close() { /* the ZipInputStream owns its own lifetime */ }
    }
}
