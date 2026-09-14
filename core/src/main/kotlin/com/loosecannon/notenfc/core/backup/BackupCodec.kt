package com.loosecannon.notenfc.core.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Backup format v1: a ZIP holding exactly two entries.
 *
 * ```
 * manifest.json   { formatVersion, appVersion, schemaVersion, createdAt, counts, dataSha256 }
 * data.json       { assets: [...], nfcTags: [...], externalLinks: [...] }
 * ```
 *
 * IDs are written verbatim, lists are sorted by id, and the manifest carries the SHA-256 of the
 * data entry, so the same input always produces the same bytes and an edited file is refused.
 * JDK ZIP + JDK SHA-256 + kotlinx-serialization only; no Android types anywhere in here.
 */
object BackupCodec {
    const val FORMAT_VERSION = 1
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(data: BackupData, appVersion: String, schemaVersion: Int, createdAt: Long): ByteArray {
        val sorted = BackupData(
            assets = data.assets.sortedBy { it.id },
            nfcTags = data.nfcTags.sortedBy { it.id },
            externalLinks = data.externalLinks.sortedBy { it.id },
        )
        val dataBytes = json.encodeToString(BackupData.serializer(), sorted).toByteArray(Charsets.UTF_8)
        val manifest = BackupManifest(
            formatVersion = FORMAT_VERSION,
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            counts = mapOf(
                "assets" to sorted.assets.size,
                "nfcTags" to sorted.nfcTags.size,
                "externalLinks" to sorted.externalLinks.size,
            ),
            dataSha256 = sha256Hex(dataBytes),
        )
        val manifestBytes =
            json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.writeEntry(MANIFEST_ENTRY, manifestBytes, createdAt)
            zos.writeEntry(DATA_ENTRY, dataBytes, createdAt)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Backup {
        val entries = readEntries(bytes)

        val manifestBytes = entries[MANIFEST_ENTRY]
            ?: throw BackupCorrupt("backup is missing $MANIFEST_ENTRY")
        val manifest = try {
            json.decodeFromString(BackupManifest.serializer(), String(manifestBytes, Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupCorrupt("$MANIFEST_ENTRY is not readable: ${e.message}")
        }

        // Refuse a newer file before touching its contents: we cannot know what we would drop.
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw BackupNewerFormat(manifest.formatVersion, FORMAT_VERSION)
        }

        val dataBytes = entries[DATA_ENTRY]
            ?: throw BackupCorrupt("backup is missing $DATA_ENTRY")
        val actual = sha256Hex(dataBytes)
        if (!actual.equals(manifest.dataSha256, ignoreCase = true)) {
            throw BackupCorrupt("$DATA_ENTRY sha256 $actual does not match manifest ${manifest.dataSha256}")
        }

        val data = try {
            json.decodeFromString(BackupData.serializer(), String(dataBytes, Charsets.UTF_8))
        } catch (e: SerializationException) {
            throw BackupCorrupt("$DATA_ENTRY is not readable: ${e.message}")
        }

        // Every row must be nameable in the domain, otherwise the caller would only find out
        // halfway through a destructive import. Result discarded; this is a validation pass.
        data.assets.forEach { it.toDomain() }
        data.externalLinks.forEach { it.toDomain() }
        data.nfcTags.forEach { it.toDomain() }

        return Backup(manifest, data)
    }

    private fun ZipOutputStream.writeEntry(name: String, payload: ByteArray, time: Long) {
        val entry = ZipEntry(name)
        entry.time = time // fixed, not "now", so encode() is reproducible
        putNextEntry(entry)
        write(payload)
        closeEntry()
    }

    private fun readEntries(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    entries[entry.name] = zin.readBytes()
                    zin.closeEntry()
                }
            }
        } catch (e: Exception) {
            throw BackupCorrupt("backup is not a readable zip: ${e.message}")
        }
        if (entries.isEmpty()) throw BackupCorrupt("backup is not a readable zip: no entries")
        return entries
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { b -> "%02x".format(b) }
}
