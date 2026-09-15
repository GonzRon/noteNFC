package com.loosecannon.notenfc.core.backup

import com.loosecannon.notenfc.core.model.shapeMatches
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Backup format v2: a ZIP holding exactly two entries.
 *
 * ```
 * manifest.json   { formatVersion, appVersion, schemaVersion, createdAt, counts, dataSha256 }
 * data.json       { assets: [...], nfcTags: [...], externalLinks: [...],
 *                    measurementDefinitions: [...], eventProfiles: [...], assetEvents: [...] }
 * ```
 *
 * IDs are written verbatim, lists are sorted by id (children by sortOrder within their parent),
 * and the manifest carries the SHA-256 of the data entry, so the same input always produces the
 * same bytes and an edited file is refused. A format-1 file (the three original lists only) still
 * decodes: the new lists default to empty. JDK ZIP + JDK SHA-256 + kotlinx-serialization only; no
 * Android types anywhere in here.
 */
object BackupCodec {
    const val FORMAT_VERSION = 2
    const val MANIFEST_ENTRY = "manifest.json"
    const val DATA_ENTRY = "data.json"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(data: BackupData, appVersion: String, schemaVersion: Int, createdAt: Long): ByteArray =
        encode(data, appVersion, schemaVersion, createdAt, FORMAT_VERSION)

    /**
     * [formatVersion] escape hatch exists only so tests can seal a manifest that claims an older
     * format than this codec writes by default (`formatOneFileStillDecodes`). Production callers
     * use the four-arg overload above, which always stamps [FORMAT_VERSION].
     */
    internal fun encode(
        data: BackupData,
        appVersion: String,
        schemaVersion: Int,
        createdAt: Long,
        formatVersion: Int,
    ): ByteArray {
        val sorted = BackupData(
            assets = data.assets.sortedBy { it.id },
            nfcTags = data.nfcTags.sortedBy { it.id },
            externalLinks = data.externalLinks.sortedBy { it.id },
            measurementDefinitions = data.measurementDefinitions.sortedBy { it.id },
            eventProfiles = data.eventProfiles.sortedBy { it.id }.map { profile ->
                profile.copy(
                    fields = profile.fields.sortedBy { it.sortOrder },
                    consumables = profile.consumables.sortedBy { it.sortOrder },
                )
            },
            assetEvents = data.assetEvents.sortedBy { it.id }.map { event ->
                event.copy(
                    measurements = event.measurements.sortedBy { it.sortOrder },
                    consumables = event.consumables.sortedBy { it.sortOrder },
                )
            },
        )
        val dataBytes = json.encodeToString(BackupData.serializer(), sorted).toByteArray(Charsets.UTF_8)
        val manifest = BackupManifest(
            formatVersion = formatVersion,
            appVersion = appVersion,
            schemaVersion = schemaVersion,
            createdAt = createdAt,
            counts = mapOf(
                "assets" to sorted.assets.size,
                "nfcTags" to sorted.nfcTags.size,
                "externalLinks" to sorted.externalLinks.size,
                "measurementDefinitions" to sorted.measurementDefinitions.size,
                "eventProfiles" to sorted.eventProfiles.size,
                "assetEvents" to sorted.assetEvents.size,
                "profileFields" to sorted.eventProfiles.sumOf { it.fields.size },
                "profileConsumables" to sorted.eventProfiles.sumOf { it.consumables.size },
                "measurements" to sorted.assetEvents.sumOf { it.measurements.size },
                "consumableUsages" to sorted.assetEvents.sumOf { it.consumables.size },
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
        data.measurementDefinitions.forEach { it.toDomain() }
        data.eventProfiles.forEach { it.toDomain() }
        data.assetEvents.forEach { it.toDomain() }

        // And the graph has to hold together. A replace-mode import deletes everything and then
        // replays the insert loops in one transaction: a duplicate id or a reference to a row
        // that is not in the file would only fail down there, on the foreign keys, with the
        // user's data already gone.
        validateGraph(data)

        return Backup(manifest, data)
    }

    /** Ids unique within each table, and every non-null reference resolvable inside the file. */
    private fun validateGraph(data: BackupData) {
        val assetIds = uniqueIds("assets", data.assets.map { it.id })
        val linkIds = uniqueIds("externalLinks", data.externalLinks.map { it.id })
        uniqueIds("nfcTags", data.nfcTags.map { it.id })

        data.externalLinks.forEach { link ->
            if (link.assetId != null && link.assetId !in assetIds) {
                throw BackupCorrupt(
                    "externalLinks: link ${link.id} points at asset ${link.assetId}, " +
                        "which is not in assets",
                )
            }
        }
        data.nfcTags.forEach { tag ->
            if (tag.assetId != null && tag.assetId !in assetIds) {
                throw BackupCorrupt(
                    "nfcTags: tag ${tag.id} points at asset ${tag.assetId}, which is not in assets",
                )
            }
            if (tag.linkId != null && tag.linkId !in linkIds) {
                throw BackupCorrupt(
                    "nfcTags: tag ${tag.id} points at link ${tag.linkId}, " +
                        "which is not in externalLinks",
                )
            }
        }

        // --- journal tables --------------------------------------------------------------------

        uniqueIds("measurementDefinitions", data.measurementDefinitions.map { it.id })
        val definitionsById = data.measurementDefinitions.associateBy { it.id }
        data.measurementDefinitions.forEach { definition ->
            if (definition.assetId !in assetIds) {
                throw BackupCorrupt(
                    "measurementDefinitions: definition ${definition.id} points at asset " +
                        "${definition.assetId}, which is not in assets",
                )
            }
        }

        uniqueIds("eventProfiles", data.eventProfiles.map { it.id })
        val profileAssetIds = data.eventProfiles.associate { it.id to it.assetId }
        val profileFieldIds = mutableListOf<String>()
        val profileConsumableIds = mutableListOf<String>()
        data.eventProfiles.forEach { profile ->
            if (profile.assetId !in assetIds) {
                throw BackupCorrupt(
                    "eventProfiles: profile ${profile.id} points at asset ${profile.assetId}, " +
                        "which is not in assets",
                )
            }
            profile.fields.forEach { field ->
                profileFieldIds += field.id
                val definition = definitionsById[field.definitionId]
                    ?: throw BackupCorrupt(
                        "eventProfiles: profile ${profile.id} field ${field.id} references " +
                            "definition ${field.definitionId}, which is not in measurementDefinitions",
                    )
                if (definition.assetId != profile.assetId) {
                    throw BackupCorrupt(
                        "eventProfiles: profile ${profile.id} field ${field.id} references " +
                            "definition ${field.definitionId} from a different asset",
                    )
                }
            }
            profile.consumables.forEach { consumable -> profileConsumableIds += consumable.id }
        }
        uniqueIds("profileFields", profileFieldIds)
        uniqueIds("profileConsumables", profileConsumableIds)

        uniqueIds("assetEvents", data.assetEvents.map { it.id })
        val measurementIds = mutableListOf<String>()
        val consumableUsageIds = mutableListOf<String>()
        data.assetEvents.forEach { event ->
            if (event.assetId !in assetIds) {
                throw BackupCorrupt(
                    "assetEvents: event ${event.id} points at asset ${event.assetId}, " +
                        "which is not in assets",
                )
            }
            if (event.profileId != null) {
                val profileAssetId = profileAssetIds[event.profileId]
                    ?: throw BackupCorrupt(
                        "assetEvents: event ${event.id} points at profile ${event.profileId}, " +
                            "which is not in eventProfiles",
                    )
                if (profileAssetId != event.assetId) {
                    throw BackupCorrupt(
                        "assetEvents: event ${event.id} references profile ${event.profileId} " +
                            "from a different asset",
                    )
                }
            }
            event.measurements.forEach { measurement ->
                measurementIds += measurement.id
                val definition = definitionsById[measurement.definitionId]
                    ?: throw BackupCorrupt(
                        "assetEvents: measurement ${measurement.id} references definition " +
                            "${measurement.definitionId}, which is not in measurementDefinitions",
                    )
                if (definition.assetId != event.assetId) {
                    throw BackupCorrupt(
                        "assetEvents: measurement ${measurement.id} references definition " +
                            "${measurement.definitionId} from a different asset",
                    )
                }
                // The definition's valueType was already proven nameable in the enum-check pass
                // above, so toDomain() here cannot throw; it is only how we get at the enum.
                val valueType = definition.toDomain().valueType
                if (!measurement.toDomain().shapeMatches(valueType)) {
                    throw BackupCorrupt(
                        "assetEvents: measurement ${measurement.id} does not match definition " +
                            "${definition.id}'s value shape for $valueType",
                    )
                }
            }
            event.consumables.forEach { consumable -> consumableUsageIds += consumable.id }
        }
        uniqueIds("measurements", measurementIds)
        uniqueIds("consumableUsages", consumableUsageIds)
    }

    private fun uniqueIds(table: String, ids: List<String>): Set<String> {
        val seen = LinkedHashSet<String>(ids.size)
        ids.forEach { id ->
            if (!seen.add(id)) throw BackupCorrupt("$table: duplicate id $id")
        }
        return seen
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
