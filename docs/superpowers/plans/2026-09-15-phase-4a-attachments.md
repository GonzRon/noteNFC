# Phase 4A — Attachments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Photos and documents attach to an asset or an event, their bytes live in a SAF folder the owner chose, they open in the system viewer with thumbnails in the list, and a backup is a *set* of two archives (data + artifacts) so a database-only restore stays a first-class outcome.

**Architecture:** `:core` gains the `Attachment` model with its single owners (`AttachmentLocator`, `MimeTypes`, `AttachmentKinds`), the `AttachmentStore`/`AttachmentStorage`/`AttachmentRepository` ports, the add/update/delete use cases, backup format 5, and a second codec (`ArtifactsCodec`) plus `ExportBackupSet`/`RestoreArtifacts`. `:app` gains Room v5 (`attachment` table, migration 4→5), a `SafTreeAttachmentStore` over `androidx.documentfile`, a `Thumbnails` cache with no new image library, the DOCUMENTS section shared by asset detail and event detail, Settings → Attachment storage, and a Backup screen that writes two stamped zips into a picked folder.

**Tech Stack:** as 2B-2 — AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, Room 3.0.3, Navigation 3 1.1.7, kotlinx-serialization 1.9.0, minSdk 26, compileSdk 37. One new dependency: `androidx.documentfile:documentfile:1.1.0`.

**Spec:** `docs/superpowers/specs/2026-09-15-phase-4a-attachments-design.md` (read it first; its §11 rulings are decided and are not reopened here).

## Global Constraints

- Test bodies written here as comments are contracts, not placeholders: the implementer turns
  each comment into real assertions covering every clause. A test without an assertion is a
  defect the task review rejects.
- Export completeness (owner's ruling 2026-09-16): a backup set is successful only when every
  managed attachment's bytes landed in the artifacts archive with the planned size; any missing
  or hash-mismatched row fails the export, neither ZIP remains, and `lastBackupAt` does not
  advance. The SAF writer deletes any document it created if its write body throws. Invariant:
  **a failed export leaves no file from that attempted backup set.**

- Toolchain unchanged. The only new dependency is `androidx.documentfile:documentfile:1.1.0`. No Coil, no CameraX, no WorkManager.
- `:core` stays JVM-only: no `android.*` import anywhere in it. `java.util.zip`, `java.security` and `java.io` streams are allowed (they already are, in `BackupCodec`).
- Bytes are never in Room. Rows hold metadata; the store holds bytes.
- The managed store is the owner's SAF tree. With none configured, attaching is **refused** with a path to Settings; nothing is ever written to app-private storage as a fallback.
- Every new use case runs inside `UnitOfWork.write`. `FakeUnitOfWork` is **non-re-entrant**: a use case never calls another use case's `run` inside its own transaction, and never opens a second `write` inside one.
- IDs are UUID strings from `IdGenerator`; timestamps from `Clock`. Nothing calls `System.currentTimeMillis()` outside `AppGraph`.
- No owner data in tracked files: no personal paths, usernames, device names, serials or note ids. The owner's home is written `~`.
- Instrumented suites run **on the emulator only**. `./gradlew :app:connectedDebugAndroidTest` is never pointed at a physical device — the phone holds real data and an instrumented run wipes it.
- `:core` tests are JUnit 5 + `kotlin.test` assertions; `:app` unit tests are JUnit 4 on plain JVM Room over `BundledSQLiteDriver` (no Robolectric).
- Commits are casual and terse, lowercase-ish, **no trailers and no attribution lines of any kind**.
- Per-task gate: `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` (plus `:app:assembleDebug` where the task touches the manifest or resources).
- `versionCode = 6`, `versionName = "2.4"` land in Task 11, with the evidence file.

## Single owners (one rule, one place)

| Rule | Owner |
|---|---|
| provider-relative path of an attachment's bytes | `AttachmentLocator` (`:core.model`) |
| mime → extension, and "is this already compressed" | `MimeTypes` (`:core.model`) |
| default kind for a new attachment | `AttachmentKinds.inferFrom` |
| hashing the bytes | `AttachmentStore.put` — the row can never disagree with what the store saw |
| data archive shape and validation | `BackupCodec` (format 5) |
| artifacts archive shape and validation | `ArtifactsCodec` (artifact format 1) |
| whether a store exists and is writable | `AttachmentStorage.state()` |

## File structure

`:core` — `model/Ids.kt` (+`AttachmentId`), `model/Attachment.kt` (new: enums, owner, row, `AttachmentLocator`, `MimeTypes`, `AttachmentKinds`, `AttachmentProblem`), `ports/AttachmentStore.kt` (new: store port, `ByteSource`, `StoredBytes`, `StoreState`, `AttachmentStorage`, `StoreIoException`), `ports/Repositories.kt` (+`AttachmentRepository`), `usecase/AttachmentCommands.kt` (new), `usecase/{AddAttachment,UpdateAttachment,DeleteAttachment}.kt` (new), `usecase/{DeleteAsset,DeleteEvent}.kt` (byte cleanup), `backup/{BackupFormat,BackupCodec}.kt` (format 5), `backup/ArtifactsCodec.kt` (new), `backup/BackupErrors.kt` (+two), `usecase/ExportBackupSet.kt` (replaces `ExportBackup.kt`), `usecase/RestoreArtifacts.kt` (new), `usecase/ImportBackupReplace.kt`; test fixtures `testing/InMemoryRepositories.kt` (+`InMemoryAttachmentRepository`), `testing/InMemoryAttachmentStore.kt` (new).

`:app` — `data/room/entities/AttachmentEntity.kt`, `data/room/dao/AttachmentDao.kt`, `data/room/{Mappers,RoomRepositories,Migrations,AppDatabase}.kt`, `schemas/.../5.json`; `attachments/{AttachmentRoot,SafTreeAttachmentStore,SafAttachmentStorage,Thumbnails}.kt` (new); `backup/SafBackupSetIO.kt` (new); `prefs/AppPrefs.kt`; `di/AppGraph.kt`; `ui/attachments/{DocumentsSection,AttachmentEditSheet,AttachmentPickers,AttachmentsSectionViewModel}.kt` (new); `ui/asset/AssetDetailScreen.kt`, `ui/journal/EventDetailScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/backup/{BackupScreen,BackupViewModel}.kt`; `res/xml/file_paths.xml`, `res/drawable/ic_photo.xml`, `res/drawable/ic_attach_file.xml`, `AndroidManifest.xml`, `build.gradle.kts`; `gradle/libs.versions.toml`; tests beside each; `androidTest/.../attachments/SafTreeAttachmentStoreContractTest.kt`, `androidTest/.../ui/AttachmentsDeviceProofTest.kt`; `docs/design/phase-4a-evidence.md`.

---

### Task 1: The attachment model and its three pure rules (`:core`)

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/model/Ids.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/model/Attachment.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/model/AttachmentRulesTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: everything below, verbatim. Tasks 2–11 name these types and do not redefine them.

```kotlin
// Ids.kt — appended, keeping every id in one file as the codebase does
@JvmInline
value class AttachmentId(val value: String)
```

```kotlin
// model/Attachment.kt
package com.loosecannon.notenfc.core.model

/** Bigger than this is a mistaken pick, not a product limit (spec §11.11): 256 MiB. */
const val MAX_ATTACHMENT_BYTES: Long = 268_435_456L

enum class AttachmentKind { PHOTO, LABEL_PHOTO, RECEIPT, MANUAL, WARRANTY, DOCUMENT, OTHER }

/** REFERENCE is 4B's `SAF_DOCUMENT` pointer; 4A writes MANAGED rows only. */
enum class AttachmentMode { MANAGED, REFERENCE }

/** No LOCAL member by the owner's ruling (spec §11.8): absent, not reserved. */
enum class StorageProvider { SAF_TREE, SAF_DOCUMENT }

sealed interface AttachmentOwner {
    data class OfAsset(val assetId: AssetId) : AttachmentOwner
    data class OfEvent(val eventId: EventId) : AttachmentOwner
}

data class Attachment(
    val id: AttachmentId,
    val owner: AttachmentOwner,
    val kind: AttachmentKind,
    val mode: AttachmentMode = AttachmentMode.MANAGED,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,                 // lowercase hex, 64 chars
    val storageProvider: StorageProvider = StorageProvider.SAF_TREE,
    val storageLocator: String,         // provider-relative, see AttachmentLocator
    val capturedOn: String?,            // ISO date, user-editable
    val notes: String = "",
    val createdAt: Long,
    val updatedAt: Long,
)

/** The only question the thumbnail path asks. */
val Attachment.isImage: Boolean get() = mimeType.startsWith("image/")

/** One thing wrong with an attachment command. The list is closed (spec §4). */
sealed interface AttachmentProblem {
    data object BlankName : AttachmentProblem
    data object NoStore : AttachmentProblem
    data object StoreUnavailable : AttachmentProblem
    data class TooLarge(val limit: Long) : AttachmentProblem
    /** The thing you named is not there: the owner row, or the attachment row itself. */
    data object OwnerMissing : AttachmentProblem
    data object Unchanged : AttachmentProblem
}

/**
 * Where an attachment's bytes live, relative to the store's root. The display name is never in
 * the path: a rename must not move bytes, and a file listing must not read as a private label.
 */
object AttachmentLocator {
    private val EXTENSION = Regex("^[a-z0-9]{1,8}$")

    /** `assets/<asset-id>` or `events/<event-id>` — the per-owner directory. */
    fun dirFor(owner: AttachmentOwner): String = when (owner) {
        is AttachmentOwner.OfAsset -> "assets/${owner.assetId.value}"
        is AttachmentOwner.OfEvent -> "events/${owner.eventId.value}"
    }

    fun forOwner(
        owner: AttachmentOwner,
        id: AttachmentId,
        displayName: String,
        mimeType: String,
    ): String = "${dirFor(owner)}/${id.value}.${extension(displayName, mimeType)}"

    /** The name's own extension wins; then what [MimeTypes] knows; then `bin`. */
    fun extension(displayName: String, mimeType: String): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName.isNotEmpty() && EXTENSION.matches(fromName)) return fromName
        return MimeTypes.extensionFor(mimeType) ?: "bin"
    }

    /** What the backup reader checks: this locator could only have been built for this row. */
    fun matchesShape(locator: String, owner: AttachmentOwner, id: AttachmentId): Boolean =
        Regex("^${Regex.escape(dirFor(owner))}/${Regex.escape(id.value)}\\.[a-z0-9]{1,8}$")
            .matches(locator)
}

object MimeTypes {
    private const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private val EXTENSIONS = mapOf(
        "image/jpeg" to "jpg",
        "image/png" to "png",
        "application/pdf" to "pdf",
        "application/zip" to "zip",
        "text/plain" to "txt",
        DOCX to "docx",
        XLSX to "xlsx",
    )

    /** Already-compressed payloads, which the artifacts archive STOREs rather than deflating. */
    private val COMPRESSED = setOf("image/jpeg", "image/png", "application/pdf", "application/zip")

    /** Lowercased and stripped of parameters: `image/jpeg; charset=x` is `image/jpeg`. */
    fun normalise(mimeType: String): String =
        mimeType.substringBefore(';').trim().lowercase().ifEmpty { "application/octet-stream" }

    fun extensionFor(mimeType: String): String? = EXTENSIONS[normalise(mimeType)]

    fun isCompressed(mimeType: String): Boolean = normalise(mimeType) in COMPRESSED
}

/** A default the person may override; never a constraint (spec §4). */
object AttachmentKinds {
    fun inferFrom(mimeType: String, fromCamera: Boolean): AttachmentKind = when {
        fromCamera -> AttachmentKind.PHOTO
        MimeTypes.normalise(mimeType).startsWith("image/") -> AttachmentKind.PHOTO
        MimeTypes.normalise(mimeType) == "application/pdf" -> AttachmentKind.DOCUMENT
        else -> AttachmentKind.OTHER
    }
}
```

- [ ] **Step 1: Write the failing test**

`core/src/test/kotlin/com/loosecannon/notenfc/core/model/AttachmentRulesTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentRulesTest {

    private val id = AttachmentId("att-1")
    private val ofAsset = AttachmentOwner.OfAsset(AssetId("a1"))
    private val ofEvent = AttachmentOwner.OfEvent(EventId("e1"))

    @Test fun locatorUsesTheOwnerDirectoryAndTheAttachmentId() {
        assertEquals(
            "assets/a1/att-1.pdf",
            AttachmentLocator.forOwner(ofAsset, id, "Owners Manual.pdf", "application/pdf"),
        )
        assertEquals(
            "events/e1/att-1.jpg",
            AttachmentLocator.forOwner(ofEvent, id, "IMG_0042.JPG", "image/jpeg"),
        )
        // the display name is never in the path (privacy, and a rename must not move bytes)
        assertFalse("Manual" in AttachmentLocator.forOwner(ofAsset, id, "Manual.pdf", "application/pdf"))
    }

    @Test fun theExtensionComesFromTheNameThenTheMimeTypeThenBin() {
        assertEquals("docx", AttachmentLocator.extension("Chemistry.docx", "application/pdf"))
        // a name with no usable extension falls through to the mime table
        assertEquals("pdf", AttachmentLocator.extension("scan", "application/pdf"))
        assertEquals("jpg", AttachmentLocator.extension("photo", "image/jpeg"))
        // nine characters, or anything that is not [a-z0-9], is not an extension
        assertEquals("bin", AttachmentLocator.extension("thing.abcdefghi", "application/unknown"))
        assertEquals("bin", AttachmentLocator.extension("thing.tar gz", "application/unknown"))
        assertEquals("bin", AttachmentLocator.extension("noextension", "application/unknown"))
    }

    @Test fun mimeTypesKnowsTheSevenExtensionsAndTheFourCompressedTypes() {
        assertEquals("zip", MimeTypes.extensionFor("application/zip"))
        assertEquals("txt", MimeTypes.extensionFor("text/plain"))
        assertEquals("xlsx", MimeTypes.extensionFor(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ))
        assertEquals("jpg", MimeTypes.extensionFor("IMAGE/JPEG; charset=binary"))
        assertNull(MimeTypes.extensionFor("application/x-nothing"))
        assertTrue(MimeTypes.isCompressed("application/pdf"))
        assertTrue(MimeTypes.isCompressed("image/png"))
        assertFalse(MimeTypes.isCompressed("text/plain"))
        assertEquals("application/octet-stream", MimeTypes.normalise("  "))
    }

    @Test fun locatorShapeIsCheckedAgainstItsOwnerAndId() {
        assertTrue(AttachmentLocator.matchesShape("assets/a1/att-1.pdf", ofAsset, id))
        assertTrue(AttachmentLocator.matchesShape("events/e1/att-1.bin", ofEvent, id))
        assertFalse(AttachmentLocator.matchesShape("assets/a1/att-1.pdf", ofEvent, id))
        assertFalse(AttachmentLocator.matchesShape("assets/a2/att-1.pdf", ofAsset, id))
        assertFalse(AttachmentLocator.matchesShape("assets/a1/other.pdf", ofAsset, id))
        assertFalse(AttachmentLocator.matchesShape("assets/a1/att-1", ofAsset, id))
        assertFalse(AttachmentLocator.matchesShape("../assets/a1/att-1.pdf", ofAsset, id))
    }

    @Test fun kindIsInferredAndIsOnlyADefault() {
        assertEquals(AttachmentKind.PHOTO, AttachmentKinds.inferFrom("application/pdf", fromCamera = true))
        assertEquals(AttachmentKind.PHOTO, AttachmentKinds.inferFrom("image/heic", fromCamera = false))
        assertEquals(AttachmentKind.DOCUMENT, AttachmentKinds.inferFrom("application/pdf", fromCamera = false))
        assertEquals(AttachmentKind.OTHER, AttachmentKinds.inferFrom("application/zip", fromCamera = false))
    }

    @Test fun isImageIsTheOnlyThingTheThumbnailPathAsks() {
        val row = Attachment(
            id = id, owner = ofAsset, kind = AttachmentKind.PHOTO, displayName = "a.jpg",
            mimeType = "image/jpeg", sizeBytes = 10L, sha256 = "0".repeat(64),
            storageLocator = "assets/a1/att-1.jpg", capturedOn = null, createdAt = 1L, updatedAt = 1L,
        )
        assertTrue(row.isImage)
        assertFalse(row.copy(mimeType = "application/pdf").isImage)
        assertEquals(268_435_456L, MAX_ATTACHMENT_BYTES)
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

Run: `./gradlew :core:test --tests '*AttachmentRulesTest'`
Expected: FAIL — compilation error, `AttachmentId`/`Attachment`/`AttachmentLocator` unresolved.

- [ ] **Step 3: Write the implementation**

Append the `AttachmentId` value class to `Ids.kt` and create `model/Attachment.kt` with exactly the code in the **Produces** block above.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :core:test`
Expected: PASS — the existing suite plus six new tests.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/loosecannon/notenfc/core/model/Ids.kt \
        core/src/main/kotlin/com/loosecannon/notenfc/core/model/Attachment.kt \
        core/src/test/kotlin/com/loosecannon/notenfc/core/model/AttachmentRulesTest.kt
git commit -m "attachment model, locator and mime rules"
```

---

### Task 2: The store boundary and its fakes (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/AttachmentStore.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/Repositories.kt`
- Create: `core/src/test/kotlin/com/loosecannon/notenfc/core/testing/InMemoryAttachmentStore.kt`
- Modify: `core/src/test/kotlin/com/loosecannon/notenfc/core/testing/InMemoryRepositories.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/ports/AttachmentStoreContractTest.kt`

**Interfaces:**
- Consumes: `AttachmentId`, `Attachment`, `AttachmentOwner`, `AssetId`, `EventId` (Task 1).
- Produces:

```kotlin
// ports/AttachmentStore.kt
package com.loosecannon.notenfc.core.ports

import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.AssetId
import java.io.InputStream
import kotlinx.coroutines.flow.Flow

/** One byte source, opened when the store is ready to read it. Never a `Uri` in `:core`. */
fun interface ByteSource { fun open(): InputStream }

/** What the store actually saw. The row records this, so bytes and metadata cannot disagree. */
data class StoredBytes(val sha256: String, val sizeBytes: Long)

/** A store that could not do what it was asked. Not a domain refusal — a broken destination. */
class StoreIoException(message: String, cause: Throwable? = null) :
    java.io.IOException(message, cause)

interface AttachmentStore {
    /** Streams [source] into [locator] (creating parents), returns the bytes' sha256 and size. */
    suspend fun put(locator: String, source: ByteSource): StoredBytes
    suspend fun open(locator: String): InputStream?        // null when absent
    suspend fun exists(locator: String): Boolean
    suspend fun delete(locator: String)                    // absent is not an error
}

sealed interface StoreState {
    data object NotConfigured : StoreState
    data class Ready(val displayName: String, val authority: String) : StoreState
    data class AccessLost(val displayName: String) : StoreState
}

/** The app-level resolver that owns the tree preference and hands out a store when it can. */
interface AttachmentStorage {
    fun state(): StoreState
    fun store(): AttachmentStore?       // null unless Ready
}
```

```kotlin
// ports/Repositories.kt — appended
interface AttachmentRepository {
    suspend fun upsert(a: Attachment)
    suspend fun get(id: AttachmentId): Attachment?
    suspend fun forOwner(owner: AttachmentOwner): List<Attachment>
    /** The asset's own rows only — not its events'. `DeleteAsset` asks for both, separately. */
    suspend fun forAsset(assetId: AssetId): List<Attachment>
    suspend fun all(): List<Attachment>
    suspend fun delete(id: AttachmentId)
    suspend fun deleteAll()
    suspend fun count(): Int
    fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>>
}
```

Test fixtures produced for Tasks 3–5:

```kotlin
// testing/InMemoryAttachmentStore.kt
package com.loosecannon.notenfc.core.testing

import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreIoException
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.ports.StoredBytes
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * The JVM half of the store contract: a map keyed by locator. `put` hashes while it copies, in
 * one place, exactly as `SafTreeAttachmentStore` does, so a use-case test can assert on a sha256
 * without an emulator. `failOnPut` lets a test force the mid-write failure `AddAttachment` has
 * to clean up after.
 */
class InMemoryAttachmentStore : AttachmentStore {
    val files = LinkedHashMap<String, ByteArray>()
    var failOnPut: String? = null
    var deletes = 0
        private set

    override suspend fun put(locator: String, source: ByteSource): StoredBytes {
        if (locator == failOnPut) throw StoreIoException("rigged put failure at $locator")
        val bytes = source.open().use { it.readBytes() }
        files[locator] = bytes
        return StoredBytes(sha256Hex(bytes), bytes.size.toLong())
    }

    override suspend fun open(locator: String): InputStream? =
        files[locator]?.let { ByteArrayInputStream(it) }

    override suspend fun exists(locator: String): Boolean = locator in files

    override suspend fun delete(locator: String) {
        deletes += 1
        files.remove(locator)
    }

    companion object {
        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { b -> "%02x".format(b) }
    }
}

/** An [AttachmentStorage] a test drives by hand: the state is a `var`, the store is the fake. */
class FakeAttachmentStorage(
    val store: InMemoryAttachmentStore = InMemoryAttachmentStore(),
    var state: StoreState = StoreState.Ready("Attachments", "com.example.provider"),
) : AttachmentStorage {
    override fun state(): StoreState = state
    override fun store(): AttachmentStore? = store.takeIf { state is StoreState.Ready }
}
```

```kotlin
// testing/InMemoryRepositories.kt — appended, in the shape of the six stores already there
class InMemoryAttachmentRepository : AttachmentRepository, Rollbackable, Witnessed {
    val rows = LinkedHashMap<String, Attachment>()
    override var witness: TransactionWitness? = null
    private val version = MutableStateFlow(0)
    var failOnUpsert: Int? = null
    private var upserts = 0

    override fun snapshot(): () -> Unit {
        val copy = LinkedHashMap(rows)
        return { rows.clear(); rows.putAll(copy); version.value += 1 }
    }

    override suspend fun upsert(a: Attachment) {
        upserts += 1
        if (upserts == failOnUpsert) throw RiggedFailure("rigged attachment upsert failure at #$upserts")
        rows[a.id.value] = a
        version.value += 1
    }

    override suspend fun get(id: AttachmentId): Attachment? = rows[id.value]

    override suspend fun forOwner(owner: AttachmentOwner): List<Attachment> =
        rows.values.filter { it.owner == owner }

    override suspend fun forAsset(assetId: AssetId): List<Attachment> =
        forOwner(AttachmentOwner.OfAsset(assetId))

    override suspend fun all(): List<Attachment> {
        witness?.observeAll()
        return rows.values.toList()
    }

    override suspend fun delete(id: AttachmentId) { rows.remove(id.value); version.value += 1 }
    override suspend fun deleteAll() { rows.clear(); version.value += 1 }
    override suspend fun count(): Int = rows.size

    override fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>> = version.map {
        rows.values.filter { it.owner == owner }.sortedBy { it.displayName.lowercase() }
    }
}
```

- [ ] **Step 1: Write the failing contract test**

`core/src/test/kotlin/com/loosecannon/notenfc/core/ports/AttachmentStoreContractTest.kt`. These six claims are the store contract; `SafTreeAttachmentStoreContractTest` (Task 10) asserts the same six on the emulator. The list is written out twice on purpose — JVM test fixtures cannot be shared with an `androidTest` variant — and both name the same claims so a drift is visible in review.

```kotlin
package com.loosecannon.notenfc.core.ports

import com.loosecannon.notenfc.core.testing.InMemoryAttachmentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentStoreContractTest {

    private val store = InMemoryAttachmentStore()
    private val payload = "spa water chemistry".toByteArray()

    @Test fun putThenOpenRoundTripsTheBytes() = runTest {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertContentEquals(payload, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    @Test fun putReturnsTheShaAndSizeTheStoreItselfSaw() = runTest {
        val stored = store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertEquals(payload.size.toLong(), stored.sizeBytes)
        assertEquals(InMemoryAttachmentStore.sha256Hex(payload), stored.sha256)
        assertEquals(64, stored.sha256.length)
        assertEquals(stored.sha256.lowercase(), stored.sha256)
    }

    @Test fun existsAnswersForBothCases() = runTest {
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertTrue(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun openOfAnAbsentLocatorIsNull() = runTest {
        assertNull(store.open("assets/a1/nothing.pdf"))
    }

    @Test fun deleteOfAnAbsentLocatorIsSilent() = runTest {
        store.delete("assets/a1/nothing.pdf")   // no throw
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        store.delete("assets/a1/att-1.pdf")
        assertFalse(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun aLocatorWithTwoDirectoryLevelsIsCreated() = runTest {
        store.put("events/e1/att-2.jpg", ByteSource { payload.inputStream() })
        assertTrue(store.exists("events/e1/att-2.jpg"))
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew :core:test --tests '*AttachmentStoreContractTest'`
Expected: FAIL — `AttachmentStore`, `ByteSource`, `InMemoryAttachmentStore` unresolved.

- [ ] **Step 3: Write the ports and the fakes**

Create `ports/AttachmentStore.kt` and `testing/InMemoryAttachmentStore.kt`, append `AttachmentRepository` to `ports/Repositories.kt` and `InMemoryAttachmentRepository` to `testing/InMemoryRepositories.kt`, exactly as the **Produces** blocks spell them. Add the imports the appended code needs (`Attachment`, `AttachmentId`, `AttachmentOwner`).

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/loosecannon/notenfc/core/ports core/src/test/kotlin/com/loosecannon/notenfc/core
git commit -m "attachment store port and in-memory fakes"
```

---

### Task 3: Add, update, delete an attachment, and byte cleanup on asset/event delete (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/AttachmentCommands.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/AddAttachment.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/UpdateAttachment.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/DeleteAttachment.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/DeleteAsset.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/DeleteEvent.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/AttachmentUseCasesTest.kt`
- Test: modify `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/RetireDeleteAssetTest.kt` and `EventUseCasesTest.kt` (the two constructors grew)

**Interfaces:**
- Consumes: Task 1's model, Task 2's `AttachmentRepository`/`AttachmentStorage`/`AttachmentStore`/`ByteSource`/`StoredBytes`/`StoreState`, and the existing `AssetRepository`, `EventRepository`, `UnitOfWork`, `IdGenerator`, `Clock`.
- Produces:

```kotlin
// usecase/AttachmentCommands.kt
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentProblem

/**
 * Two outcomes, no exception, because every one of [AttachmentProblem]'s members is something the
 * DOCUMENTS section draws rather than something that has gone wrong. `kotlin.Result` carries one
 * type parameter, so spec §6's `Result<Attachment, AttachmentProblem>` is spelled like this.
 */
sealed interface AttachmentResult<out T> {
    data class Ok<out T>(val value: T) : AttachmentResult<T>
    data class Refused(val problem: AttachmentProblem) : AttachmentResult<Nothing>
}

/**
 * What the picker or the camera knows about a file it is handing over. [sizeBytes] is null when
 * the provider reports none (a camera capture): the guard then runs on what the store actually
 * wrote instead of before the copy.
 */
data class AddAttachmentCommand(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long? = null,
    /** Null lets [com.loosecannon.notenfc.core.model.AttachmentKinds] choose the default. */
    val kind: AttachmentKind? = null,
    val capturedOn: String? = null,
    val notes: String = "",
    val fromCamera: Boolean = false,
)

/** What the edit sheet can change. The locator is not here: a rename never moves bytes. */
data class UpdateAttachmentCommand(
    val displayName: String,
    val kind: AttachmentKind,
    val capturedOn: String? = null,
    val notes: String = "",
)
```

```kotlin
// usecase/AddAttachment.kt
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKinds
import com.loosecannon.notenfc.core.model.AttachmentLocator
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.AttachmentProblem
import com.loosecannon.notenfc.core.model.MAX_ATTACHMENT_BYTES
import com.loosecannon.notenfc.core.model.MimeTypes
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Bytes first, row second (spec §6). The store hashes while it copies, so the row records what
 * the store saw and the two can never disagree; if the row write then fails, the bytes are
 * deleted, because nothing would point at them.
 *
 * The owner check and the `put` both happen *outside* `uow.write`: the transaction holds the row
 * write alone. `FakeUnitOfWork` is not re-entrant and a multi-megabyte copy has no business
 * inside a database transaction.
 */
class AddAttachment(
    private val attachments: AttachmentRepository,
    private val assets: AssetRepository,
    private val events: EventRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(
        owner: AttachmentOwner,
        cmd: AddAttachmentCommand,
        source: ByteSource,
    ): AttachmentResult<Attachment> {
        val name = cmd.displayName.trim()
        if (name.isEmpty()) return AttachmentResult.Refused(AttachmentProblem.BlankName)
        if (!ownerExists(owner)) return AttachmentResult.Refused(AttachmentProblem.OwnerMissing)
        if (cmd.sizeBytes != null && cmd.sizeBytes > MAX_ATTACHMENT_BYTES) {
            return AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES))
        }
        val store = when (storage.state()) {
            StoreState.NotConfigured -> return AttachmentResult.Refused(AttachmentProblem.NoStore)
            is StoreState.AccessLost ->
                return AttachmentResult.Refused(AttachmentProblem.StoreUnavailable)
            is StoreState.Ready -> storage.store()
                ?: return AttachmentResult.Refused(AttachmentProblem.StoreUnavailable)
        }

        val id = AttachmentId(ids.newId())
        val mimeType = MimeTypes.normalise(cmd.mimeType)
        val locator = AttachmentLocator.forOwner(owner, id, name, mimeType)
        val stored = store.put(locator, source)
        // A provider that under-reported its size (or reported none) is caught here instead.
        if (stored.sizeBytes > MAX_ATTACHMENT_BYTES) {
            store.delete(locator)
            return AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES))
        }

        val now = clock.nowMillis()
        val row = Attachment(
            id = id,
            owner = owner,
            kind = cmd.kind ?: AttachmentKinds.inferFrom(mimeType, cmd.fromCamera),
            displayName = name,
            mimeType = mimeType,
            sizeBytes = stored.sizeBytes,
            sha256 = stored.sha256,
            storageLocator = locator,
            capturedOn = cmd.capturedOn?.trim()?.takeIf { it.isNotEmpty() },
            notes = cmd.notes.trim(),
            createdAt = now,
            updatedAt = now,
        )
        try {
            uow.write { attachments.upsert(row) }
        } catch (t: Throwable) {
            store.delete(locator)   // the bytes were ours and now nothing names them
            throw t
        }
        return AttachmentResult.Ok(row)
    }

    private suspend fun ownerExists(owner: AttachmentOwner): Boolean = when (owner) {
        is AttachmentOwner.OfAsset -> assets.get(owner.assetId) != null
        is AttachmentOwner.OfEvent -> events.get(owner.eventId) != null
    }
}
```

```kotlin
// usecase/UpdateAttachment.kt
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentProblem
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * Name, kind, captured-on and notes. Nothing here touches the locator or the sha256: the bytes
 * are not being edited, so a rename is metadata and the file stays where it is (spec §4).
 * `Unchanged` is a refusal rather than a silent no-op so the sheet can close without claiming a
 * save that did not happen.
 */
class UpdateAttachment(
    private val attachments: AttachmentRepository,
    private val uow: UnitOfWork,
    private val clock: Clock,
) {
    suspend fun run(id: AttachmentId, cmd: UpdateAttachmentCommand): AttachmentResult<Attachment> {
        val row = attachments.get(id)
            ?: return AttachmentResult.Refused(AttachmentProblem.OwnerMissing)
        val name = cmd.displayName.trim()
        if (name.isEmpty()) return AttachmentResult.Refused(AttachmentProblem.BlankName)

        val updated = row.copy(
            displayName = name,
            kind = cmd.kind,
            capturedOn = cmd.capturedOn?.trim()?.takeIf { it.isNotEmpty() },
            notes = cmd.notes.trim(),
            updatedAt = clock.nowMillis(),
        )
        if (updated.copy(updatedAt = row.updatedAt) == row) {
            return AttachmentResult.Refused(AttachmentProblem.Unchanged)
        }
        uow.write { attachments.upsert(updated) }
        return AttachmentResult.Ok(updated)
    }
}
```

```kotlin
// usecase/DeleteAttachment.kt
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.UnitOfWork

/**
 * The row goes inside the transaction; the bytes go after it, best effort. A byte delete that
 * fails is not surfaced: the row is gone, the file is an orphan, and sweeping orphans is 4B's job
 * (spec §6). Deleting an attachment that is not there is not an error.
 */
class DeleteAttachment(
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: AttachmentId) {
        val row = attachments.get(id) ?: return
        uow.write { attachments.delete(id) }
        runCatching { storage.store()?.delete(row.storageLocator) }
    }
}
```

```kotlin
// usecase/DeleteAsset.kt — the whole run(), rewritten
class DeleteAsset(
    private val assets: AssetRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: AssetId) {
        val all = assets.all()
        if (all.none { it.id == id }) throw NoSuchAsset(id)
        val children = AssetTree.children(all, id)
        if (children.isNotEmpty()) throw AssetHasChildren(id, children.map { it.id })

        // The locators are read *before* the cascade takes the rows with it, inside the same
        // transaction that deletes them: after the commit there is nothing left to ask.
        val doomed = uow.write {
            val own = attachments.forAsset(id)
            val theirs = events.forAsset(id)
                .flatMap { event -> attachments.forOwner(AttachmentOwner.OfEvent(event.id)) }
            val locators = (own + theirs).map { it.storageLocator }
            assets.delete(id)
            locators
        }

        // Bytes after the commit, best effort: a file the store will not delete is an orphan,
        // not a reason to keep an asset the person deleted.
        val store = storage.store() ?: return
        doomed.forEach { locator -> runCatching { store.delete(locator) } }
    }
}
```

```kotlin
// usecase/DeleteEvent.kt — the whole file, rewritten
class DeleteEvent(
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(id: EventId) {
        val doomed = uow.write {
            val locators = attachments.forOwner(AttachmentOwner.OfEvent(id)).map { it.storageLocator }
            events.delete(id)
            locators
        }
        val store = storage.store() ?: return
        doomed.forEach { locator -> runCatching { store.delete(locator) } }
    }
}
```

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/AttachmentUseCasesTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AssetEvent
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.AttachmentProblem
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.EventKind
import com.loosecannon.notenfc.core.model.EventSource
import com.loosecannon.notenfc.core.model.MAX_ATTACHMENT_BYTES
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.core.testing.FakeAttachmentStorage
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import com.loosecannon.notenfc.core.testing.InMemoryAttachmentRepository
import com.loosecannon.notenfc.core.testing.InMemoryAttachmentStore
import com.loosecannon.notenfc.core.testing.InMemoryEventRepository
import com.loosecannon.notenfc.core.testing.RiggedFailure
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentUseCasesTest {

    private val attachments = InMemoryAttachmentRepository()
    private val assets = InMemoryAssetRepository()
    private val events = InMemoryEventRepository()
    private val uow = FakeUnitOfWork(assets, events, attachments)
    private val storage = FakeAttachmentStorage()
    private val store: InMemoryAttachmentStore get() = storage.store
    private var now = 5_000L
    private var seq = 0
    private val ids = IdGenerator { "att-${++seq}" }

    private val add = AddAttachment(attachments, assets, events, storage, uow, ids, Clock { now })
    private val update = UpdateAttachment(attachments, uow, Clock { now })
    private val remove = DeleteAttachment(attachments, storage, uow)

    private val payload = "1-2-3 easy installation".toByteArray()
    private fun source() = ByteSource { payload.inputStream() }

    private suspend fun asset(id: String = "a1"): AssetId {
        assets.upsert(Asset(id = AssetId(id), name = "Hot tub", createdAt = 1L, updatedAt = 1L))
        return AssetId(id)
    }

    private suspend fun event(id: String = "e1", assetId: String = "a1"): EventId {
        events.upsert(
            AssetEvent(
                id = EventId(id), assetId = AssetId(assetId), kind = EventKind.MAINTENANCE,
                title = "Filter change", profileId = null, occurredOn = "2026-09-15",
                occurredTime = null, tzId = "UTC", notes = "", source = EventSource.MANUAL,
                sourceRef = null, createdAt = 1L, updatedAt = 1L,
                measurements = emptyList(), consumables = emptyList(),
            ),
        )
        return EventId(id)
    }

    private fun cmd(
        name: String = "1-2-3 Easy Installation Guide.pdf",
        mime: String = "application/pdf",
        size: Long? = null,
        kind: AttachmentKind? = null,
    ) = AddAttachmentCommand(displayName = name, mimeType = mime, sizeBytes = size, kind = kind)

    @Test fun addPutsTheBytesFirstThenTheRow() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val result = add.run(owner, cmd(), source())
        val row = (result as AttachmentResult.Ok).value

        assertEquals("assets/a1/att-1.pdf", row.storageLocator)
        assertEquals(AttachmentKind.DOCUMENT, row.kind)          // inferred from application/pdf
        assertEquals(payload.size.toLong(), row.sizeBytes)
        assertEquals(InMemoryAttachmentStore.sha256Hex(payload), row.sha256)
        assertEquals(5_000L, row.createdAt)
        assertEquals(5_000L, row.updatedAt)
        assertEquals(row, attachments.rows["att-1"])
        assertTrue(store.exists("assets/a1/att-1.pdf"))
        assertEquals(1, uow.commits)
    }

    @Test fun addToAnEventUsesTheEventDirectory() = runTest {
        asset()
        val owner = AttachmentOwner.OfEvent(event())
        val row = (add.run(owner, cmd(name = "photo.jpg", mime = "image/jpeg"), source())
            as AttachmentResult.Ok).value
        assertEquals("events/e1/att-1.jpg", row.storageLocator)
        assertEquals(AttachmentKind.PHOTO, row.kind)
        assertEquals(listOf(row), attachments.forOwner(owner))
    }

    @Test fun aCameraCaptureIsAPhotoWhateverTheMimeSays() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(
            owner,
            AddAttachmentCommand(displayName = "capture.jpg", mimeType = "image/jpeg", fromCamera = true),
            source(),
        ) as AttachmentResult.Ok).value
        assertEquals(AttachmentKind.PHOTO, row.kind)
    }

    @Test fun addRefusesABlankNameAMissingOwnerAndNoStore() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.BlankName),
            add.run(owner, cmd(name = "   "), source()),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.OwnerMissing),
            add.run(AttachmentOwner.OfAsset(AssetId("nope")), cmd(), source()),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.OwnerMissing),
            add.run(AttachmentOwner.OfEvent(EventId("nope")), cmd(), source()),
        )

        storage.state = StoreState.NotConfigured
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.NoStore),
            add.run(owner, cmd(), source()),
        )
        storage.state = StoreState.AccessLost("Attachments")
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.StoreUnavailable),
            add.run(owner, cmd(), source()),
        )

        // nothing was written, either way
        assertTrue(attachments.rows.isEmpty())
        assertTrue(store.files.isEmpty())
        assertEquals(0, uow.commits)
    }

    @Test fun addRefusesAnOversizeFileBeforeCopyingAnyByte() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.TooLarge(MAX_ATTACHMENT_BYTES)),
            add.run(owner, cmd(size = MAX_ATTACHMENT_BYTES + 1), source()),
        )
        assertTrue(store.files.isEmpty())
        // and the limit is inclusive: exactly 256 MiB is allowed through the guard
        assertTrue(add.run(owner, cmd(size = MAX_ATTACHMENT_BYTES), source()) is AttachmentResult.Ok)
    }

    @Test fun aFailedRowWriteDeletesTheBytesItHadAlreadyWritten() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        attachments.failOnUpsert = 1
        assertFailsWith<RiggedFailure> { add.run(owner, cmd(), source()) }
        assertTrue(attachments.rows.isEmpty())
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        assertEquals(1, store.deletes)
        assertEquals(1, uow.rollbacks)
    }

    @Test fun updateChangesMetadataAndNeverTheLocator() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        now = 9_000L
        val saved = (update.run(
            row.id,
            UpdateAttachmentCommand(
                displayName = "  Installation guide  ",
                kind = AttachmentKind.MANUAL,
                capturedOn = "2026-09-14",
                notes = " keep ",
            ),
        ) as AttachmentResult.Ok).value

        assertEquals("Installation guide", saved.displayName)
        assertEquals(AttachmentKind.MANUAL, saved.kind)
        assertEquals("2026-09-14", saved.capturedOn)
        assertEquals("keep", saved.notes)
        assertEquals(row.storageLocator, saved.storageLocator)   // bytes did not move
        assertEquals(row.sha256, saved.sha256)
        assertEquals(9_000L, saved.updatedAt)
        assertEquals(saved, attachments.rows[row.id.value])
    }

    @Test fun updateRefusesBlankUnchangedAndUnknown() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        val same = UpdateAttachmentCommand(row.displayName, row.kind, row.capturedOn, row.notes)

        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.Unchanged),
            update.run(row.id, same),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.BlankName),
            update.run(row.id, same.copy(displayName = " ")),
        )
        assertEquals(
            AttachmentResult.Refused(AttachmentProblem.OwnerMissing),
            update.run(AttachmentId("nope"), same),
        )
        assertEquals(1, uow.commits)   // only the add committed
    }

    @Test fun deleteRemovesTheRowThenTheBytes() = runTest {
        val owner = AttachmentOwner.OfAsset(asset())
        val row = (add.run(owner, cmd(), source()) as AttachmentResult.Ok).value
        remove.run(row.id)
        assertTrue(attachments.rows.isEmpty())
        assertFalse(store.exists(row.storageLocator))
        // an unknown id is a no-op, and a store that refuses is not surfaced
        remove.run(AttachmentId("nope"))
        assertEquals(2, uow.commits)   // add + delete; the no-op opened no transaction
    }
}
```

Extend `RetireDeleteAssetTest` with the asset cascade row, and `EventUseCasesTest` with the event one (both constructors grew, so every existing construction site in those files is updated to the new signature):

```kotlin
// RetireDeleteAssetTest — the fakes and the use case under test
private val attachments = InMemoryAttachmentRepository()
private val events = InMemoryEventRepository()
private val storage = FakeAttachmentStorage()
private val uow = FakeUnitOfWork(assets, events, attachments)
private val delete = DeleteAsset(assets, events, attachments, storage, uow)

@Test fun deletingAnAssetRemovesItsOwnAndItsEventsAttachmentBytes() = runTest {
    store("a1", "Hot tub")
    events.upsert(
        AssetEvent(
            id = EventId("e1"), assetId = AssetId("a1"), kind = EventKind.MAINTENANCE,
            title = "Filter change", profileId = null, occurredOn = "2026-09-15",
            occurredTime = null, tzId = "UTC", notes = "", source = EventSource.MANUAL,
            sourceRef = null, createdAt = 1L, updatedAt = 1L,
            measurements = emptyList(), consumables = emptyList(),
        ),
    )
    val onAsset = attachment("att-1", AttachmentOwner.OfAsset(AssetId("a1")), "assets/a1/att-1.pdf")
    val onEvent = attachment("att-2", AttachmentOwner.OfEvent(EventId("e1")), "events/e1/att-2.jpg")
    // a third row on another asset, which must survive
    store("a2", "Mower")
    val elsewhere = attachment("att-3", AttachmentOwner.OfAsset(AssetId("a2")), "assets/a2/att-3.pdf")

    delete.run(AssetId("a1"))

    assertFalse(storage.store.exists(onAsset.storageLocator))
    assertFalse(storage.store.exists(onEvent.storageLocator))
    assertTrue(storage.store.exists(elsewhere.storageLocator))
    assertEquals(setOf("a2"), assets.rows.keys)
}

@Test fun anAbsentStoreIsNotAReasonToKeepTheAsset() = runTest {
    store("a1", "Hot tub")
    attachment("att-1", AttachmentOwner.OfAsset(AssetId("a1")), "assets/a1/att-1.pdf")
    storage.state = StoreState.AccessLost("Attachments")
    delete.run(AssetId("a1"))
    assertTrue(assets.rows.isEmpty())
}

/** Seeds a row and its bytes: the fake repository has no cascade, so the row goes too. */
private suspend fun attachment(id: String, owner: AttachmentOwner, locator: String): Attachment {
    val row = Attachment(
        id = AttachmentId(id), owner = owner, kind = AttachmentKind.DOCUMENT,
        displayName = "$id.pdf", mimeType = "application/pdf", sizeBytes = 3L,
        sha256 = "0".repeat(64), storageLocator = locator, capturedOn = null,
        createdAt = 1L, updatedAt = 1L,
    )
    attachments.upsert(row)
    storage.store.put(locator, ByteSource { "abc".toByteArray().inputStream() })
    return row
}
```

```kotlin
// EventUseCasesTest — DeleteEvent's new signature, plus the byte cleanup row
private val attachments = InMemoryAttachmentRepository()
private val storage = FakeAttachmentStorage()
private val deleteEvent = DeleteEvent(events, attachments, storage, uow)

@Test fun deletingAnEventRemovesItsAttachmentBytes() = runTest {
    // This file's seed helpers (`seedHotTub()` etc.) generate ids, so seed explicit ids here:
    val assetId = seedHotTub()
    events.upsert(
        AssetEvent(
            id = EventId("e1"), assetId = assetId, kind = EventKind.MAINTENANCE,
            title = "Filter change", profileId = null, occurredOn = "2026-09-15",
            occurredTime = null, tzId = "UTC", notes = "", source = EventSource.MANUAL,
            sourceRef = null, createdAt = 1L, updatedAt = 1L,
            measurements = emptyList(), consumables = emptyList(),
        ),
    )
    attachments.upsert(
        Attachment(
            id = AttachmentId("att-1"), owner = AttachmentOwner.OfEvent(EventId("e1")),
            kind = AttachmentKind.PHOTO, displayName = "before.jpg", mimeType = "image/jpeg",
            sizeBytes = 3L, sha256 = "0".repeat(64), storageLocator = "events/e1/att-1.jpg",
            capturedOn = null, createdAt = 1L, updatedAt = 1L,
        ),
    )
    storage.store.put("events/e1/att-1.jpg", ByteSource { "abc".toByteArray().inputStream() })

    deleteEvent.run(EventId("e1"))

    assertFalse(storage.store.exists("events/e1/att-1.jpg"))
    assertNull(events.rows["e1"])
}
```

- [ ] **Step 2: Run the tests and watch them fail**

Run: `./gradlew :core:test --tests '*AttachmentUseCasesTest'`
Expected: FAIL — `AddAttachment`, `AttachmentResult`, `AddAttachmentCommand` unresolved.

- [ ] **Step 3: Write the use cases**

Create the four new files and rewrite `DeleteAsset`/`DeleteEvent` exactly as the **Produces** blocks spell them, adding the imports (`AttachmentOwner`, `AttachmentRepository`, `AttachmentStorage`, `EventRepository`). No use case calls another use case's `run`, and none opens a nested `uow.write`.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :core:test`
Expected: PASS. `:app` does not compile yet — `AppGraph` still builds `DeleteAsset`/`DeleteEvent` with the old signature; that is Task 7's wiring, so `:core` is the only gate for this task.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/loosecannon/notenfc/core/usecase core/src/test/kotlin/com/loosecannon/notenfc/core/usecase
git commit -m "add/update/delete attachment, byte cleanup on asset and event delete"
```

---

### Task 4: Backup data format 5 — attachment rows travel with the data archive (`:core`)

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/BackupFormat.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/BackupCodec.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ImportBackupReplace.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ExportBackup.kt` (gains `ids`; renamed in Task 5)
- Test: modify `core/src/test/kotlin/com/loosecannon/notenfc/core/backup/BackupCodecTest.kt`
- Test: modify `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/BackupUseCasesTest.kt`

**Interfaces:**
- Consumes: Task 1's model, Task 2's `AttachmentRepository`/`AttachmentStorage`, Task 3's nothing.
- Produces:

```kotlin
// backup/BackupFormat.kt
/**
 * `formatVersion` keeps its name and becomes 5 (spec §11.4 — D7's `dataFormatVersion` is this
 * field; renaming it would break the branch-on-version reader). The four new fields carry
 * defaults so a format ≤4 manifest still decodes.
 */
@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val counts: Map<String, Int>,
    val dataSha256: String,
    /** Ties this data archive to its artifacts archive. Empty only on a format ≤4 file. */
    val backupSetId: String = "",
    val artifactFormatVersion: Int = 1,
    val artifactCount: Int = 0,
    val artifactBytes: Long = 0L,
)

/** Owner is `assetId` xor `eventId`; there is no SQL CHECK, so the readers are the rule (§11.5). */
@Serializable
data class AttachmentDto(
    val id: String,
    val assetId: String?,
    val eventId: String?,
    val kind: String,
    val mode: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val storageProvider: String,
    val storageLocator: String,
    val capturedOn: String?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupData(
    val assets: List<AssetDto>,
    val nfcTags: List<NfcTagDto>,
    val externalLinks: List<ExternalLinkDto>,
    val measurementDefinitions: List<MeasurementDefinitionDto> = emptyList(),
    val eventProfiles: List<EventProfileDto> = emptyList(),
    val assetEvents: List<AssetEventDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
)

fun Attachment.toDto(): AttachmentDto = AttachmentDto(
    id = id.value,
    assetId = (owner as? AttachmentOwner.OfAsset)?.assetId?.value,
    eventId = (owner as? AttachmentOwner.OfEvent)?.eventId?.value,
    kind = kind.name,
    mode = mode.name,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    storageProvider = storageProvider.name,
    storageLocator = storageLocator,
    capturedOn = capturedOn,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AttachmentDto.toDomain(): Attachment {
    if ((assetId == null) == (eventId == null)) {
        throw BackupCorrupt("attachment $id must name exactly one owner, an asset or an event")
    }
    return Attachment(
        id = AttachmentId(id),
        owner = assetId?.let { AttachmentOwner.OfAsset(AssetId(it)) }
            ?: AttachmentOwner.OfEvent(EventId(eventId!!)),
        kind = enumOrCorrupt<AttachmentKind>(kind, "attachment kind", "attachment $id"),
        mode = enumOrCorrupt<AttachmentMode>(mode, "attachment mode", "attachment $id"),
        displayName = displayName,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        storageProvider = enumOrCorrupt<StorageProvider>(
            storageProvider, "storage provider", "attachment $id",
        ),
        storageLocator = storageLocator,
        capturedOn = capturedOn,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
```

```kotlin
// backup/BackupCodec.kt — the changed parts
const val FORMAT_VERSION = 5
private val SHA256_HEX = Regex("^[0-9a-f]{64}$")

fun encode(
    data: BackupData,
    appVersion: String,
    schemaVersion: Int,
    createdAt: Long,
    backupSetId: String,
): ByteArray = encode(data, appVersion, schemaVersion, createdAt, backupSetId, FORMAT_VERSION)

internal fun encode(
    data: BackupData,
    appVersion: String,
    schemaVersion: Int,
    createdAt: Long,
    backupSetId: String,
    formatVersion: Int,
): ByteArray {
    val sorted = BackupData(
        /* ...the six existing lists, unchanged... */
        attachments = data.attachments.sortedBy { it.id },
    )
    // The artifact tallies are derived here, in one place, from the rows themselves: a MANAGED row
    // is a row whose bytes belong in the artifacts archive.
    val managed = sorted.attachments.filter { it.mode == AttachmentMode.MANAGED.name }
    val manifest = BackupManifest(
        formatVersion = formatVersion,
        /* ...appVersion, schemaVersion, createdAt... */
        counts = mapOf(
            /* ...the ten existing counts... */
            "attachments" to sorted.attachments.size,
        ),
        dataSha256 = sha256Hex(dataBytes),
        backupSetId = backupSetId,
        artifactFormatVersion = 1,
        artifactCount = managed.size,
        artifactBytes = managed.sumOf { it.sizeBytes },
    )
    /* ...the zip write, unchanged... */
}
```

`decode` keeps its shape and gains, in the enum-check pass, `data.attachments.forEach { it.toDomain() }`, and in `validateGraph`, after the event block:

```kotlin
// --- attachments (spec §7.1) ---------------------------------------------------------------
val eventIds = data.assetEvents.map { it.id }.toSet()
uniqueIds("attachments", data.attachments.map { it.id })
val locators = mutableSetOf<Pair<String, String>>()
data.attachments.forEach { attachment ->
    val domain = attachment.toDomain()   // already proven nameable above; this is how we get the owner
    when (val owner = domain.owner) {
        is AttachmentOwner.OfAsset -> if (owner.assetId.value !in assetIds) throw BackupCorrupt(
            "attachments: attachment ${attachment.id} points at asset ${owner.assetId.value}, " +
                "which is not in assets",
        )
        is AttachmentOwner.OfEvent -> if (owner.eventId.value !in eventIds) throw BackupCorrupt(
            "attachments: attachment ${attachment.id} points at event ${owner.eventId.value}, " +
                "which is not in assetEvents",
        )
    }
    if (!SHA256_HEX.matches(attachment.sha256)) throw BackupCorrupt(
        "attachments: attachment ${attachment.id} has a malformed sha256",
    )
    if (attachment.sizeBytes < 0) throw BackupCorrupt(
        "attachments: attachment ${attachment.id} has a negative size",
    )
    if (!AttachmentLocator.matchesShape(attachment.storageLocator, domain.owner, domain.id)) {
        throw BackupCorrupt(
            "attachments: attachment ${attachment.id} has a locator that is not its own",
        )
    }
    if (!locators.add(attachment.storageProvider to attachment.storageLocator)) throw BackupCorrupt(
        "attachments: duplicate locator ${attachment.storageLocator}",
    )
}
```

and, immediately after the manifest is parsed and the newer-format check has run:

```kotlin
// A format-5 file without a set id could never be paired with its artifacts archive.
if (manifest.formatVersion >= 5 && manifest.backupSetId.isBlank()) {
    throw BackupCorrupt("manifest.json is format ${manifest.formatVersion} with no backupSetId")
}
```

```kotlin
// usecase/ImportBackupReplace.kt — the changed parts
data class ImportReport(
    val formatVersion: Int,
    val assets: Int,
    val tags: Int,
    val links: Int,
    val definitions: Int,
    val profiles: Int,
    val events: Int,
    val attachments: Int,
    /** What a later `RestoreArtifacts` must match; `""` for a format ≤4 file. */
    val lastRestoredBackupSetId: String,
)

class ImportBackupReplace(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
    private val uow: UnitOfWork,
) {
    suspend fun run(bytes: ByteArray): ImportReport {
        val backup = BackupCodec.decode(bytes)
        val data = backup.data

        val orphaned = uow.write {
            // The bytes of everything about to be replaced, read before the wipe.
            val doomed = attachments.all().map { it.storageLocator }
            attachments.deleteAll()
            events.deleteAll()
            /* ...the rest of the existing delete order... */

            /* ...the existing insert order... */
            // Attachment rows go last: every owner, asset or event, is already in.
            data.attachments.forEach { attachments.upsert(it.toDomain()) }
            doomed - data.attachments.map { it.storageLocator }.toSet()
        }

        // After the commit, best effort: a file the store will not delete is an orphan for 4B.
        storage.store()?.let { store ->
            orphaned.forEach { locator -> runCatching { store.delete(locator) } }
        }

        return ImportReport(
            /* ...the six existing counts... */
            attachments = data.attachments.size,
            lastRestoredBackupSetId = backup.manifest.backupSetId,
        )
    }
}
```

`ExportBackup` gains `private val ids: IdGenerator` and stamps `backupSetId = ids.newId()` on the archive it encodes; it also reads `attachments.all().map { it.toDto() }` inside the same `uow.read`. Task 5 renames it and gives it the artifacts plan; this task only keeps it compiling and honest.

- [ ] **Step 1: Write the failing tests**

Append to `BackupCodecTest`, reusing its existing `assetDto`/`assetEventDto` helpers:

```kotlin
private fun attachmentDto(
    id: String,
    assetId: String? = "a1",
    eventId: String? = null,
    locator: String = "assets/a1/$id.pdf",
    sha256: String = "a".repeat(64),
    sizeBytes: Long = 12L,
    mode: String = "MANAGED",
) = AttachmentDto(
    id = id, assetId = assetId, eventId = eventId, kind = "DOCUMENT", mode = mode,
    displayName = "Manual.pdf", mimeType = "application/pdf", sizeBytes = sizeBytes,
    sha256 = sha256, storageProvider = "SAF_TREE", storageLocator = locator,
    capturedOn = "2026-09-15", notes = "", createdAt = 1L, updatedAt = 2L,
)

private fun encoded(data: BackupData, setId: String = "set-1"): ByteArray =
    BackupCodec.encode(data, appVersion = "2.4", schemaVersion = 5, createdAt = 1L, backupSetId = setId)

@Test
fun formatFiveRoundTripsAttachmentsOnBothOwners() {
    val data = BackupData(
        assets = listOf(assetDto("a1")),
        nfcTags = emptyList(),
        externalLinks = emptyList(),
        assetEvents = listOf(assetEventDto("e1", "a1")),
        attachments = listOf(
            attachmentDto("att-1"),
            attachmentDto("att-2", assetId = null, eventId = "e1", locator = "events/e1/att-2.jpg"),
        ),
    )
    val decoded = BackupCodec.decode(encoded(data))

    assertEquals(5, decoded.manifest.formatVersion)
    assertEquals("set-1", decoded.manifest.backupSetId)
    assertEquals(1, decoded.manifest.artifactFormatVersion)
    assertEquals(2, decoded.manifest.artifactCount)
    assertEquals(24L, decoded.manifest.artifactBytes)
    assertEquals(2, decoded.manifest.counts["attachments"])
    assertEquals(data.attachments, decoded.data.attachments)
    assertEquals(
        AttachmentOwner.OfEvent(EventId("e1")),
        decoded.data.attachments.first { it.id == "att-2" }.toDomain().owner,
    )
}

@Test
fun aReferenceRowIsNotCountedAsAnArtifact() {
    val data = BackupData(
        assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList(),
        attachments = listOf(attachmentDto("att-1"), attachmentDto("att-2", mode = "REFERENCE", locator = "assets/a1/att-2.pdf")),
    )
    val manifest = BackupCodec.decode(encoded(data)).manifest
    assertEquals(1, manifest.artifactCount)
    assertEquals(12L, manifest.artifactBytes)
}

@Test
fun formatFourFileStillDecodesWithNoAttachments() {
    val data = BackupData(assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList())
    val bytes = BackupCodec.encode(data, "2.3", 4, 1L, backupSetId = "", formatVersion = 4)
    val decoded = BackupCodec.decode(bytes)
    assertEquals(4, decoded.manifest.formatVersion)
    assertEquals("", decoded.manifest.backupSetId)
    assertTrue(decoded.data.attachments.isEmpty())
    assertEquals(0, decoded.manifest.artifactCount)
}

@Test
fun aFormatFiveFileWithNoSetIdIsCorrupt() {
    val data = BackupData(assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList())
    val boom = assertFailsWith<BackupCorrupt> { BackupCodec.decode(encoded(data, setId = "  ")) }
    assertTrue("backupSetId" in boom.message!!)
}

@Test
fun anAttachmentWithNoOwnerOrTwoOwnersIsCorrupt() {
    val base = BackupData(assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList())
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(encoded(base.copy(attachments = listOf(attachmentDto("att-1", assetId = null)))))
    }
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(encoded(base.copy(attachments = listOf(attachmentDto("att-1", eventId = "e1")))))
    }
}

@Test
fun anAttachmentPointingAtARowThatIsNotInTheFileIsCorrupt() {
    val base = BackupData(assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList())
    val onAGhostAsset = attachmentDto("att-1", assetId = "a9", locator = "assets/a9/att-1.pdf")
    assertFailsWith<BackupCorrupt> { BackupCodec.decode(encoded(base.copy(attachments = listOf(onAGhostAsset)))) }
    val onAGhostEvent = attachmentDto("att-2", assetId = null, eventId = "e9", locator = "events/e9/att-2.pdf")
    assertFailsWith<BackupCorrupt> { BackupCodec.decode(encoded(base.copy(attachments = listOf(onAGhostEvent)))) }
}

@Test
fun aBadShaABadLocatorANegativeSizeAndADuplicateLocatorAreAllCorrupt() {
    val base = BackupData(assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList())
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(encoded(base.copy(attachments = listOf(attachmentDto("att-1", sha256 = "A".repeat(64))))))
    }
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(encoded(base.copy(attachments = listOf(attachmentDto("att-1", sha256 = "abc")))))
    }
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(encoded(base.copy(attachments = listOf(attachmentDto("att-1", sizeBytes = -1L)))))
    }
    // a locator that belongs to another row's id
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(encoded(base.copy(attachments = listOf(attachmentDto("att-1", locator = "assets/a1/att-9.pdf")))))
    }
    // two rows claiming the same provider + locator
    assertFailsWith<BackupCorrupt> {
        BackupCodec.decode(
            encoded(
                base.copy(
                    attachments = listOf(
                        attachmentDto("att-1", locator = "assets/a1/att-1.pdf"),
                        attachmentDto("att-1", locator = "assets/a1/att-1.pdf"),
                    ),
                ),
            ),
        )
    }
}

@Test
fun encodeIsStillReproducibleAndAttachmentsAreSortedById() {
    val data = BackupData(
        assets = listOf(assetDto("a1")), nfcTags = emptyList(), externalLinks = emptyList(),
        attachments = listOf(attachmentDto("att-2", locator = "assets/a1/att-2.pdf"), attachmentDto("att-1")),
    )
    assertContentEquals(encoded(data), encoded(data))
    assertEquals(listOf("att-1", "att-2"), BackupCodec.decode(encoded(data)).data.attachments.map { it.id })
}
```

Append to `BackupUseCasesTest` (its `Fakes` grows an `attachments` repository and a `FakeAttachmentStorage`; `exportOf`/`importInto` pass them, and `ExportBackup` gets `IdGenerator { "set-1" }`):

```kotlin
@Test
fun `attachment rows survive the round trip and are reported`() {
    val source = Fakes()
    runBlocking {
        populate(source)
        populateJournal(source)
        source.attachments.upsert(attachment("att-1", AttachmentOwner.OfAsset(AssetId("a1")), "assets/a1/att-1.pdf"))
        source.attachments.upsert(attachment("att-2", AttachmentOwner.OfEvent(EventId("e1")), "events/e1/att-2.jpg"))
    }
    val target = Fakes()
    val report = importInto(target, exportOf(source))

    runBlocking {
        assertEquals(
            source.attachments.all().sortedBy { it.id.value },
            target.attachments.all().sortedBy { it.id.value },
        )
    }
    assertEquals(2, report.attachments)
    assertEquals(5, report.formatVersion)
    assertEquals("set-1", report.lastRestoredBackupSetId)
}

@Test
fun `a replace import deletes the bytes of the rows it replaced`() {
    val target = Fakes()
    runBlocking {
        populate(target)
        target.attachments.upsert(attachment("old", AttachmentOwner.OfAsset(AssetId("a1")), "assets/a1/old.pdf"))
        target.storage.store.put("assets/a1/old.pdf", ByteSource { "x".toByteArray().inputStream() })
    }
    val source = Fakes()
    runBlocking { populate(source) }
    importInto(target, exportOf(source))

    runBlocking { assertTrue(target.attachments.all().isEmpty()) }
    assertFalse(target.storage.store.exists("assets/a1/old.pdf"))
}

@Test
fun `a row that comes back in the file keeps its bytes`() {
    val f = Fakes()
    runBlocking {
        populate(f)
        f.attachments.upsert(attachment("att-1", AttachmentOwner.OfAsset(AssetId("a1")), "assets/a1/att-1.pdf"))
        f.storage.store.put("assets/a1/att-1.pdf", ByteSource { "x".toByteArray().inputStream() })
    }
    importInto(f, exportOf(f))   // export and re-import the same install
    assertTrue(f.storage.store.exists("assets/a1/att-1.pdf"))
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :core:test --tests '*BackupCodecTest' --tests '*BackupUseCasesTest'`
Expected: FAIL — `AttachmentDto` unresolved, and `encode` has no `backupSetId` parameter.

- [ ] **Step 3: Implement format 5**

Edit `BackupFormat.kt`, `BackupCodec.kt`, `ImportBackupReplace.kt` and `ExportBackup.kt` as the **Produces** blocks spell them. Update the codec's KDoc header to describe format 5 and to say that a format ≤4 file decodes with an empty attachment list.

- [ ] **Step 4: Run the tests and watch them pass**

Run: `./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/loosecannon/notenfc/core/backup core/src/main/kotlin/com/loosecannon/notenfc/core/usecase \
        core/src/test/kotlin/com/loosecannon/notenfc/core
git commit -m "backup format 5: attachment rows"
```

---

### Task 5: The artifacts archive, `RestoreArtifacts`, `ExportBackupSet` (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/ArtifactsCodec.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/BackupErrors.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/RestoreArtifacts.kt`
- Rename + rewrite: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ExportBackup.kt` → `ExportBackupSet.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/backup/ArtifactsCodecTest.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/ArtifactsUseCasesTest.kt`

**Interfaces:**
- Consumes: Tasks 1, 2, 4.
- Produces:

```kotlin
// backup/ArtifactsCodec.kt
package com.loosecannon.notenfc.core.backup

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.MimeTypes
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable

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

/** Thrown by the export when [ArtifactsWritten.covers] is false; the archives are already gone. */
class BackupSetIncomplete(val missing: List<AttachmentId>, val mismatched: List<AttachmentId>) :
    Exception("backup set incomplete: ${missing.size} missing, ${mismatched.size} mismatched")

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

    private val json = kotlinx.serialization.json.Json { prettyPrint = true; encodeDefaults = true }

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
                zos.putNextEntry(zipEntry)
                val source = open(entry.locator)
                    ?: throw BackupCorrupt("artifacts: ${entry.locator} vanished mid-export")
                source.use { it.copyTo(zos, BUFFER) }
                zos.closeEntry()
            }
        }
        return ArtifactsWritten(
            count = resolved.size,
            bytes = resolved.sumOf { (entry, _) -> entry.sizeBytes },
            missing = missing,
            mismatched = mismatched,
        )
    }

    /*
     * Hardening the owner flagged for review, not for redesign: the reader should report
     * manifest entries that have no ZIP entry, and ZIP entries the manifest does not name,
     * rather than silently ignoring either. Add both counts to the read report if it fits
     * this task cleanly; otherwise the task reviewer records it in the ledger for 4B.
     */
    /**
     * Streams the archive. [onManifest] runs first and may throw to refuse the whole file;
     * [onEntry] is then called once per `artifacts/` entry with a stream bounded to that entry.
     * A callback that does not read its stream is fine — `nextEntry` skips the remainder.
     */
    suspend fun read(
        source: InputStream,
        onManifest: suspend (ArtifactsManifest) -> Unit,
        onEntry: suspend (ArtifactEntry, InputStream) -> Unit,
    ) {
        ZipInputStream(source).use { zin ->
            val first = zin.nextEntry ?: throw BackupCorrupt("artifacts archive has no entries")
            if (first.name != MANIFEST_ENTRY) {
                throw BackupCorrupt("artifacts archive must start with $MANIFEST_ENTRY, found ${first.name}")
            }
            val manifest = try {
                json.decodeFromString(ArtifactsManifest.serializer(), String(zin.readBytes(), Charsets.UTF_8))
            } catch (e: kotlinx.serialization.SerializationException) {
                throw BackupCorrupt("$MANIFEST_ENTRY is not readable: ${e.message}")
            }
            if (manifest.artifactFormatVersion > ARTIFACT_FORMAT_VERSION) {
                throw ArtifactsNewerFormat(manifest.artifactFormatVersion, ARTIFACT_FORMAT_VERSION)
            }
            onManifest(manifest)
            val byName = manifest.entries.associateBy { it.entryName }
            while (true) {
                val zipEntry = zin.nextEntry ?: break
                val entry = byName[zipEntry.name] ?: continue   // not in the manifest: not ours
                onEntry(entry, NonClosing(zin))
            }
        }
    }

    /** So a callback's `use {}` cannot close the whole zip stream out from under the loop. */
    private class NonClosing(private val delegate: InputStream) : InputStream() {
        override fun read(): Int = delegate.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)
        override fun close() { /* the ZipInputStream owns its own lifetime */ }
    }
}
```

```kotlin
// backup/BackupErrors.kt — appended
/** The artifacts archive was written by a newer build than this one understands. */
class ArtifactsNewerFormat(val found: Int, val supported: Int) :
    BackupException("artifact format $found is newer than supported $supported")

/** These bytes belong to a different backup set than the data that was restored (spec §11.3). */
class ArtifactsSetMismatch(val expected: String, val found: String) :
    BackupException("artifacts belong to backup set $found, not $expected")
```

```kotlin
// usecase/ExportBackupSet.kt
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.ArtifactsCodec
import com.loosecannon.notenfc.core.backup.ArtifactsPlan
import com.loosecannon.notenfc.core.backup.ArtifactsPlanEntry
import com.loosecannon.notenfc.core.backup.BackupCodec
import com.loosecannon.notenfc.core.backup.BackupData
import com.loosecannon.notenfc.core.backup.toDto
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentMode
import com.loosecannon.notenfc.core.ports.AssetRepository
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.DefinitionRepository
import com.loosecannon.notenfc.core.ports.EventRepository
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.ports.LinkRepository
import com.loosecannon.notenfc.core.ports.ProfileRepository
import com.loosecannon.notenfc.core.ports.TagRepository
import com.loosecannon.notenfc.core.ports.UnitOfWork

/** The data archive's bytes, and the plan for the second archive that goes with it. */
data class BackupSet(val data: ByteArray, val plan: ArtifactsPlan) {
    // A ByteArray in a data class: equals/hashCode are identity, which is what callers want here
    // (nobody compares two backup sets) but is worth saying out loud.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * One read transaction over every canonical table, a set id minted once, and two outputs: the
 * data archive's bytes (small, held whole, as before) and a plan the app streams into the
 * artifacts archive. `:core` never opens a store here — it does not know where the bytes are.
 *
 * With zero attachments the plan is empty, and the app still writes the artifacts archive: a set
 * is always two files (spec §7.3).
 */
class ExportBackupSet(
    private val assets: AssetRepository,
    private val tags: TagRepository,
    private val links: LinkRepository,
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val events: EventRepository,
    private val attachments: AttachmentRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val appVersion: String,
    private val schemaVersion: Int,
) {
    suspend fun run(): BackupSet {
        val backupSetId = ids.newId()
        val createdAt = clock.nowMillis()
        val (data, rows) = uow.read {
            val rows = attachments.all()
            BackupData(
                assets = assets.all().map { it.toDto() },
                nfcTags = tags.all().map { it.toDto() },
                externalLinks = links.all().map { it.toDto() },
                measurementDefinitions = definitions.all().map { it.toDto() },
                eventProfiles = profiles.all().map { it.toDto() },
                assetEvents = events.all().map { it.toDto() },
                attachments = rows.map { it.toDto() },
            ) to rows
        }
        val plan = ArtifactsPlan(
            backupSetId = backupSetId,
            dataFormatVersion = BackupCodec.FORMAT_VERSION,
            createdAt = createdAt,
            entries = rows
                .filter { it.mode == AttachmentMode.MANAGED }
                .sortedBy { it.id.value }
                .map { it.planEntry() },
        )
        return BackupSet(
            data = BackupCodec.encode(data, appVersion, schemaVersion, createdAt, backupSetId),
            plan = plan,
        )
    }

    private fun Attachment.planEntry() = ArtifactsPlanEntry(
        attachmentId = id,
        entryName = ArtifactsCodec.entryName(id, storageLocator),
        locator = storageLocator,
        sha256 = sha256,
        sizeBytes = sizeBytes,
        mimeType = mimeType,
    )
}
```

```kotlin
// usecase/RestoreArtifacts.kt
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.ArtifactsCodec
import com.loosecannon.notenfc.core.backup.ArtifactsSetMismatch
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentRepository
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreIoException
import java.io.InputStream

/** Restored, skipped, and the ids the archive carried that this install has no row for. */
data class ArtifactsReport(
    val backupSetId: String,
    val restored: Int,
    val skipped: Int,
    val missingRows: List<String>,
)

/**
 * The second half of a restore. It writes bytes only, never rows: the rows came from the data
 * archive, and an entry that no row claims is skipped rather than invented (spec §6).
 *
 * Verification happens twice, on purpose. The manifest's sha256 is checked against the row before
 * anything is opened, and the store's own digest — `put` hashes while it copies — is checked
 * against the row afterwards; a mismatch deletes what was written, so a bad entry can never leave
 * partial bytes behind a row that claims they are good.
 */
class RestoreArtifacts(
    private val attachments: AttachmentRepository,
    private val storage: AttachmentStorage,
) {
    suspend fun run(archive: InputStream, expectedSetId: String?): ArtifactsReport {
        val store = storage.store()
            ?: throw StoreIoException("no attachment folder is configured")
        var setId = ""
        var restored = 0
        var skipped = 0
        val missingRows = mutableListOf<String>()

        ArtifactsCodec.read(
            source = archive,
            onManifest = { manifest ->
                if (expectedSetId != null && manifest.backupSetId != expectedSetId) {
                    throw ArtifactsSetMismatch(expectedSetId, manifest.backupSetId)
                }
                setId = manifest.backupSetId
            },
            onEntry = { entry, bytes ->
                val row = attachments.get(AttachmentId(entry.attachmentId))
                when {
                    row == null -> {
                        skipped += 1
                        missingRows += entry.attachmentId
                    }
                    row.sha256 != entry.sha256 -> skipped += 1
                    else -> {
                        val stored = store.put(row.storageLocator, ByteSource { bytes })
                        if (stored.sha256 != row.sha256) {
                            store.delete(row.storageLocator)
                            skipped += 1
                        } else {
                            restored += 1
                        }
                    }
                }
            },
        )
        return ArtifactsReport(setId, restored, skipped, missingRows)
    }
}
```

- [ ] **Step 1: Write the failing codec tests**

`core/src/test/kotlin/com/loosecannon/notenfc/core/backup/ArtifactsCodecTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.backup

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.testing.InMemoryAttachmentStore
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

        val names = zipEntries(out.toByteArray()).map { it.name }
        assertEquals(ArtifactsCodec.MANIFEST_ENTRY, names.first())
        assertEquals(listOf("artifacts/att-1.pdf", "artifacts/att-2.txt"), names.drop(1))

        val seen = LinkedHashMap<String, ByteArray>()
        var manifest: ArtifactsManifest? = null
        ArtifactsCodec.read(
            ByteArrayInputStream(out.toByteArray()),
            onManifest = { manifest = it },
            onEntry = { entry, bytes -> seen[entry.attachmentId] = bytes.readBytes() },
        )
        assertEquals(1, manifest!!.artifactFormatVersion)
        assertEquals(5, manifest!!.dataFormatVersion)
        assertEquals("set-1", manifest!!.backupSetId)
        assertContentEquals(pdf, seen["att-1"])
        assertContentEquals(text, seen["att-2"])
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
        assertEquals(listOf(ArtifactsCodec.MANIFEST_ENTRY), zipEntries(out.toByteArray()).map { it.name })
    }

    @Test fun aNewerArtifactFormatIsRefusedBeforeAnyEntryIsRead() = runTest {
        val a = planEntry("att-1", pdf, "application/pdf", "pdf")
        val out = ByteArrayOutputStream()
        ArtifactsCodec.write(out, plan(a), sources(a.locator to pdf))
        val bumped = bumpArtifactFormat(out.toByteArray(), to = 2)

        var entries = 0
        val boom = assertFailsWith<ArtifactsNewerFormat> {
            ArtifactsCodec.read(
                ByteArrayInputStream(bumped),
                onManifest = { },
                onEntry = { _, _ -> entries += 1 },
            )
        }
        assertEquals(2, boom.found)
        assertEquals(1, boom.supported)
        assertEquals(0, entries)
    }

    @Test fun anArchiveThatDoesNotStartWithTheManifestIsCorrupt() = runTest {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("artifacts/att-1.pdf"))
            zos.write(pdf)
            zos.closeEntry()
        }
        assertFailsWith<BackupCorrupt> {
            ArtifactsCodec.read(ByteArrayInputStream(out.toByteArray()), onManifest = { }, onEntry = { _, _ -> })
        }
    }

    /** Rewrites the manifest's `artifactFormatVersion`, keeping every other entry as it was. */
    private fun bumpArtifactFormat(bytes: ByteArray, to: Int): ByteArray { /* unzip, edit the json, rezip */ }
}
```

> `bumpArtifactFormat` mirrors `BackupCodecTest`'s existing `unzip`/`rezip`/`bumpFormatVersion` trio: read every entry into a map, replace `"artifactFormatVersion": 1` with `"artifactFormatVersion": 2` in the manifest bytes, write the entries back in the same order. Copy those three helpers from `BackupCodecTest` rather than reinventing them.

- [ ] **Step 2: Write the failing use-case tests**

`core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/ArtifactsUseCasesTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.backup.ArtifactsCodec
import com.loosecannon.notenfc.core.backup.ArtifactsSetMismatch
import com.loosecannon.notenfc.core.model.Asset
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentMode
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.testing.FakeAttachmentStorage
import com.loosecannon.notenfc.core.testing.FakeUnitOfWork
import com.loosecannon.notenfc.core.testing.InMemoryAssetRepository
import com.loosecannon.notenfc.core.testing.InMemoryAttachmentRepository
import com.loosecannon.notenfc.core.testing.InMemoryAttachmentStore
import com.loosecannon.notenfc.core.testing.InMemoryDefinitionRepository
import com.loosecannon.notenfc.core.testing.InMemoryEventRepository
import com.loosecannon.notenfc.core.testing.InMemoryLinkRepository
import com.loosecannon.notenfc.core.testing.InMemoryProfileRepository
import com.loosecannon.notenfc.core.testing.InMemoryTagRepository
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtifactsUseCasesTest {

    private val assets = InMemoryAssetRepository()
    private val tags = InMemoryTagRepository()
    private val links = InMemoryLinkRepository()
    private val definitions = InMemoryDefinitionRepository()
    private val profiles = InMemoryProfileRepository()
    private val events = InMemoryEventRepository()
    private val attachments = InMemoryAttachmentRepository()
    private val uow = FakeUnitOfWork(assets, tags, links, definitions, profiles, events, attachments)
    private val storage = FakeAttachmentStorage()
    private val store: InMemoryAttachmentStore get() = storage.store

    private val export = ExportBackupSet(
        assets, tags, links, definitions, profiles, events, attachments, uow,
        IdGenerator { "set-1" }, Clock { 1_726_000_000_000L }, appVersion = "2.4", schemaVersion = 5,
    )
    private val restore = RestoreArtifacts(attachments, storage)

    private val payload = "bullfrog headrest clip".toByteArray()

    private suspend fun seed(id: String = "att-1", mode: AttachmentMode = AttachmentMode.MANAGED): Attachment {
        assets.upsert(Asset(id = AssetId("a1"), name = "Hot tub", createdAt = 1L, updatedAt = 1L))
        val locator = "assets/a1/$id.pdf"
        val row = Attachment(
            id = AttachmentId(id), owner = AttachmentOwner.OfAsset(AssetId("a1")),
            kind = AttachmentKind.MANUAL, mode = mode, displayName = "$id.pdf",
            mimeType = "application/pdf", sizeBytes = payload.size.toLong(),
            sha256 = InMemoryAttachmentStore.sha256Hex(payload), storageLocator = locator,
            capturedOn = null, createdAt = 1L, updatedAt = 1L,
        )
        attachments.upsert(row)
        store.put(locator, ByteSource { payload.inputStream() })
        return row
    }

    private suspend fun writeArchive(): ByteArray {
        val set = export.run()
        val out = ByteArrayOutputStream()
        ArtifactsCodec.write(out, set.plan) { locator -> store.open(locator) }
        return out.toByteArray()
    }

    @Test fun thePlanListsEveryManagedRowExactlyOnceAndNoReferenceRow() = runTest {
        seed("att-1")
        seed("att-2")
        seed("att-3", mode = AttachmentMode.REFERENCE)
        val set = export.run()

        assertEquals(listOf("att-1", "att-2"), set.plan.entries.map { it.attachmentId.value })
        assertEquals("set-1", set.plan.backupSetId)
        assertEquals(5, set.plan.dataFormatVersion)
        assertEquals(
            listOf("artifacts/att-1.pdf", "artifacts/att-2.pdf"),
            set.plan.entries.map { it.entryName },
        )
        // and the data archive carries the same set id and the same tallies
        val manifest = com.loosecannon.notenfc.core.backup.BackupCodec.decode(set.data).manifest
        assertEquals("set-1", manifest.backupSetId)
        assertEquals(2, manifest.artifactCount)
        assertEquals(1, uow.reads)   // one snapshot for the whole export
    }

    @Test fun restoreWritesTheBytesAtTheRowsOwnLocator() = runTest {
        val row = seed()
        val archive = writeArchive()
        store.files.clear()   // a fresh install: rows restored from the data archive, no bytes

        val report = restore.run(ByteArrayInputStream(archive), expectedSetId = "set-1")

        assertEquals(1, report.restored)
        assertEquals(0, report.skipped)
        assertEquals("set-1", report.backupSetId)
        assertTrue(report.missingRows.isEmpty())
        assertContentEquals(payload, store.open(row.storageLocator)!!.use { it.readBytes() })
    }

    @Test fun aSetMismatchIsRefusedAndWritesNothing() = runTest {
        seed()
        val archive = writeArchive()
        store.files.clear()

        val boom = assertFailsWith<ArtifactsSetMismatch> {
            restore.run(ByteArrayInputStream(archive), expectedSetId = "set-9")
        }
        assertEquals("set-9", boom.expected)
        assertEquals("set-1", boom.found)
        assertTrue(store.files.isEmpty())

        // a null expectation is the "I have not restored data in this session" case: allowed
        assertEquals(1, restore.run(ByteArrayInputStream(archive), expectedSetId = null).restored)
    }

    @Test fun anEntryWithNoRowIsCountedAndNamed() = runTest {
        seed()
        val archive = writeArchive()
        attachments.deleteAll()
        store.files.clear()

        val report = restore.run(ByteArrayInputStream(archive), expectedSetId = "set-1")
        assertEquals(0, report.restored)
        assertEquals(1, report.skipped)
        assertEquals(listOf("att-1"), report.missingRows)
        assertTrue(store.files.isEmpty())
    }

    @Test fun anEntryWhoseShaDisagreesWithTheRowIsSkippedAndLeavesNoBytes() = runTest {
        val row = seed()
        val archive = writeArchive()
        store.files.clear()
        // the data archive restored a row claiming a different digest for these bytes
        attachments.upsert(row.copy(sha256 = "b".repeat(64)))

        val report = restore.run(ByteArrayInputStream(archive), expectedSetId = "set-1")
        assertEquals(0, report.restored)
        assertEquals(1, report.skipped)
        assertFalse(store.exists(row.storageLocator))
    }

    @Test fun restoreWithNoStoreConfiguredIsAnIoFailureNotAPartialRestore() = runTest {
        seed()
        val archive = writeArchive()
        storage.state = com.loosecannon.notenfc.core.ports.StoreState.NotConfigured
        assertFailsWith<com.loosecannon.notenfc.core.ports.StoreIoException> {
            restore.run(ByteArrayInputStream(archive), expectedSetId = "set-1")
        }
    }

    @Test fun coversIsTrueOnlyWhenEveryPlannedRowLandedWithItsSize() {
        val plan = ArtifactsPlan(backupSetId = "set", createdAt = 1L, entries = listOf(
            ArtifactsPlanEntry("att-1", "assets/a1/att-1.pdf", "0".repeat(64), 10L, "application/pdf"),
            ArtifactsPlanEntry("att-2", "assets/a1/att-2.jpg", "1".repeat(64), 5L, "image/jpeg"),
        ))
        assertTrue(ArtifactsWritten(2, 15L, emptyList(), emptyList()).covers(plan))
        assertFalse(ArtifactsWritten(1, 10L, listOf(AttachmentId("att-2")), emptyList()).covers(plan))
        assertFalse(ArtifactsWritten(1, 10L, emptyList(), listOf(AttachmentId("att-2"))).covers(plan))
        assertFalse(ArtifactsWritten(2, 14L, emptyList(), emptyList()).covers(plan))
    }

    @Test fun anInstallWithNoAttachmentsStillExportsAPlanAndAnArchive() = runTest {
        assets.upsert(Asset(id = AssetId("a1"), name = "Hot tub", createdAt = 1L, updatedAt = 1L))
        val archive = writeArchive()
        val report = restore.run(ByteArrayInputStream(archive), expectedSetId = "set-1")
        assertEquals(0, report.restored)
        assertEquals(0, report.skipped)
    }
}
```

- [ ] **Step 3: Run both new suites and watch them fail**

Run: `./gradlew :core:test --tests '*ArtifactsCodecTest' --tests '*ArtifactsUseCasesTest'`
Expected: FAIL — `ArtifactsCodec`, `ExportBackupSet`, `RestoreArtifacts` unresolved.

- [ ] **Step 4: Implement the codec and the two use cases**

Create `backup/ArtifactsCodec.kt`, append the two errors to `backup/BackupErrors.kt`, create `usecase/RestoreArtifacts.kt`, and `git mv core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ExportBackup.kt core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ExportBackupSet.kt` before rewriting it as the **Produces** block spells it. Update `BackupUseCasesTest`'s `exportOf` helper to build `ExportBackupSet` and use `set.data`.

- [ ] **Step 5: Run the tests and watch them pass**

Run: `./gradlew :core:test`
Expected: PASS — every `:core` suite, including the format-5 tests from Task 4.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/com/loosecannon/notenfc/core core/src/test/kotlin/com/loosecannon/notenfc/core
git commit -m "artifacts archive, restore artifacts, export backup set"
```

---

### Task 6: Room v5 — the `attachment` table, its cascades, and migration 4→5 (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/entities/AttachmentEntity.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/dao/AttachmentDao.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/AppDatabase.kt` (entity + dao + `version = 5`)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/Mappers.kt` (+attachment mappers and the owner `require`)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/RoomRepositories.kt` (+`RoomAttachmentRepository`)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/Migrations.kt` (+`MIGRATION_4_5`)
- Create: `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/5.json` (generated by the Room compiler, committed)
- Modify: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/MigrationTestSupport.kt` (register the new migration)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/Migration4To5Test.kt`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/Migration1To5Test.kt`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/AttachmentDaoTest.kt`

**Interfaces:**
- Consumes: Task 1's model, Task 2's `AttachmentRepository`.
- Produces: `AttachmentEntity`, `AttachmentDao`, `RoomAttachmentRepository(dao)`, `MIGRATION_4_5`, `AppDatabase.attachmentDao()`, schema version 5.

```kotlin
// entities/AttachmentEntity.kt
package com.loosecannon.notenfc.data.room.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Schema v5 (spec §9.1). One row per attached file; the bytes are never here — they live in the
 * owner's SAF folder, and `storage_locator` is how to find them, relative to that folder's root.
 *
 * Two nullable foreign keys, exactly one of which is set: `asset_id` for a file on an asset,
 * `event_id` for one on an entry, both CASCADE so deleting the owner takes the row with it (the
 * *bytes* are `DeleteAsset`/`DeleteEvent`'s job, which read the locators before the cascade runs).
 * There is deliberately **no SQL `CHECK`** for the exactly-one rule (spec §11.5): Room does not
 * model one in its schema hash, so it would be invisible to migration validation. The rule is
 * enforced in [com.loosecannon.notenfc.data.room.requireExactlyOneOwner] on the way into the
 * table and in the backup reader on the way in from a file, the same shape as `nfc_tag`'s
 * at-most-one-target rule.
 *
 * `(storage_provider, storage_locator)` is unique: two rows must never claim the same bytes.
 */
@Entity(
    tableName = "attachment",
    foreignKeys = [
        ForeignKey(
            entity = AssetEntity::class,
            parentColumns = ["id"],
            childColumns = ["asset_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AssetEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("asset_id"),
        Index("event_id"),
        Index(value = ["storage_provider", "storage_locator"], unique = true),
    ],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "asset_id") val assetId: String?,
    @ColumnInfo(name = "event_id") val eventId: String?,
    val kind: String,
    val mode: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    val sha256: String,
    @ColumnInfo(name = "storage_provider") val storageProvider: String,
    @ColumnInfo(name = "storage_locator") val storageLocator: String,
    @ColumnInfo(name = "captured_on") val capturedOn: String?,
    val notes: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

```kotlin
// dao/AttachmentDao.kt
package com.loosecannon.notenfc.data.room.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import com.loosecannon.notenfc.data.room.entities.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    /** See [AssetDao.upsert] for why this is not `@Upsert`. */
    @Transaction
    suspend fun upsert(e: AttachmentEntity) {
        if (update(e) == 0) insert(e)
    }

    @Update suspend fun update(e: AttachmentEntity): Int

    @Insert suspend fun insert(e: AttachmentEntity)

    @Query("SELECT * FROM attachment WHERE id = :id")
    suspend fun byId(id: String): AttachmentEntity?

    @Query("SELECT * FROM attachment WHERE asset_id = :assetId ORDER BY display_name COLLATE NOCASE")
    suspend fun forAsset(assetId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachment WHERE event_id = :eventId ORDER BY display_name COLLATE NOCASE")
    suspend fun forEvent(eventId: String): List<AttachmentEntity>

    @Query("SELECT * FROM attachment ORDER BY created_at")
    suspend fun all(): List<AttachmentEntity>

    @Query("SELECT COUNT(*) FROM attachment")
    suspend fun count(): Int

    @Query("SELECT * FROM attachment WHERE asset_id = :assetId ORDER BY display_name COLLATE NOCASE")
    fun observeForAsset(assetId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachment WHERE event_id = :eventId ORDER BY display_name COLLATE NOCASE")
    fun observeForEvent(eventId: String): Flow<List<AttachmentEntity>>

    @Query("DELETE FROM attachment WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM attachment")
    suspend fun deleteAll()
}
```

```kotlin
// Mappers.kt — appended
/**
 * D4 §11's exactly-one-owner rule, enforced where the row enters the table. The domain's
 * `AttachmentOwner` already makes both-at-once unrepresentable; this guards rows built any other
 * way, and it is the reason the schema carries no `CHECK` (spec §11.5).
 */
fun AttachmentEntity.requireExactlyOneOwner(): AttachmentEntity = apply {
    require((assetId == null) != (eventId == null)) {
        "attachment '$id' must name exactly one owner, found asset_id=$assetId event_id=$eventId"
    }
}

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = AttachmentId(id),
    owner = when {
        assetId != null -> AttachmentOwner.OfAsset(AssetId(assetId))
        eventId != null -> AttachmentOwner.OfEvent(EventId(eventId))
        else -> error("attachment '$id' has no owner")
    },
    kind = AttachmentKind.valueOf(kind),
    mode = AttachmentMode.valueOf(mode),
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    storageProvider = StorageProvider.valueOf(storageProvider),
    storageLocator = storageLocator,
    capturedOn = capturedOn,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id.value,
    assetId = (owner as? AttachmentOwner.OfAsset)?.assetId?.value,
    eventId = (owner as? AttachmentOwner.OfEvent)?.eventId?.value,
    kind = kind.name,
    mode = mode.name,
    displayName = displayName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    sha256 = sha256,
    storageProvider = storageProvider.name,
    storageLocator = storageLocator,
    capturedOn = capturedOn,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
```

```kotlin
// RoomRepositories.kt — appended
class RoomAttachmentRepository(private val dao: AttachmentDao) : AttachmentRepository {
    override suspend fun upsert(a: Attachment) = dao.upsert(a.toEntity().requireExactlyOneOwner())
    override suspend fun get(id: AttachmentId): Attachment? = dao.byId(id.value)?.toDomain()

    override suspend fun forOwner(owner: AttachmentOwner): List<Attachment> = when (owner) {
        is AttachmentOwner.OfAsset -> dao.forAsset(owner.assetId.value)
        is AttachmentOwner.OfEvent -> dao.forEvent(owner.eventId.value)
    }.map { it.toDomain() }

    override suspend fun forAsset(assetId: AssetId): List<Attachment> =
        dao.forAsset(assetId.value).map { it.toDomain() }

    override suspend fun all(): List<Attachment> = dao.all().map { it.toDomain() }
    override suspend fun delete(id: AttachmentId) = dao.delete(id.value)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun count(): Int = dao.count()

    override fun observeForOwner(owner: AttachmentOwner): Flow<List<Attachment>> = when (owner) {
        is AttachmentOwner.OfAsset -> dao.observeForAsset(owner.assetId.value)
        is AttachmentOwner.OfEvent -> dao.observeForEvent(owner.eventId.value)
    }.map { list -> list.map { it.toDomain() } }
}
```

```kotlin
// Migrations.kt — appended
/**
 * Schema v4 -> v5: the `attachment` table (spec §9.1). Nothing existing changes, so this is a
 * plain `CREATE TABLE` plus its three indexes — no recreate, no copy, no rewrite. Every row that
 * was on disk before the migration is untouched by construction.
 *
 * As everywhere in this file the SQL is copied verbatim from the exported `5.json`, so the
 * migration and the compiled entity have one source and Room validates the result on open.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachment` (`id` TEXT NOT NULL, `asset_id` TEXT, " +
                "`event_id` TEXT, `kind` TEXT NOT NULL, `mode` TEXT NOT NULL, " +
                "`display_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, " +
                "`size_bytes` INTEGER NOT NULL, `sha256` TEXT NOT NULL, " +
                "`storage_provider` TEXT NOT NULL, `storage_locator` TEXT NOT NULL, " +
                "`captured_on` TEXT, `notes` TEXT NOT NULL, `created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`asset_id`) REFERENCES `asset`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`event_id`) REFERENCES `asset_event`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_attachment_asset_id` ON `attachment` (`asset_id`)",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_attachment_event_id` ON `attachment` (`event_id`)",
        )
        connection.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_attachment_storage_provider_storage_locator` ON `attachment` " +
                "(`storage_provider`, `storage_locator`)",
        )
    }
}
```

- [ ] **Step 1: Add the entity and generate `5.json`**

Add `AttachmentEntity`, `AttachmentDao`, the mappers and `RoomAttachmentRepository`; add the entity and `abstract fun attachmentDao(): AttachmentDao` to `AppDatabase` and set `version = 5`.

Run: `./gradlew :app:kspDebugKotlin`
Expected: `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/5.json` appears, with `"version": 5` and a fresh `identityHash`. Open it and confirm the `attachment` entity's `createSql` and its three `indices` match the strings in `MIGRATION_4_5` character for character (with `${TABLE_NAME}` substituted). **If they differ, the schema wins** — paste the generated strings into the migration, not the other way round.

- [ ] **Step 2: Write the failing tests**

`Migration4To5Test.kt`:

```kotlin
package com.loosecannon.notenfc.data.room

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.loosecannon.notenfc.core.model.AssetId
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.model.AttachmentKind
import com.loosecannon.notenfc.core.model.AttachmentMode
import com.loosecannon.notenfc.core.model.AttachmentOwner
import com.loosecannon.notenfc.core.model.EventId
import com.loosecannon.notenfc.core.model.StorageProvider
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MIGRATION_4_5]: the additive migration that gives the schema its `attachment` table (spec
 * §9.1). A v4 database is built from the exported `4.json`, seeded with what a 2B-2 install really
 * has, and opened through Room, which runs the migration and validates the result against the
 * compiled v5 schema. See [openMigrated] for why the proof is assembled this way.
 *
 * Nothing is recreated here, so what is at risk is not the old rows but the new table's shape:
 * the two nullable foreign keys, the three indexes, and the cascades that make an attachment row
 * die with its owner.
 */
class Migration4To5Test {

    @Test
    fun addsTheAttachmentTableAndLeavesEverythingElseAlone() = runTest {
        val file = File.createTempFile("notenfc-migrate-4-5", ".db").also { it.delete() }
        try {
            createSchemaVersion(4, file, ::seedPhase2b2)

            val db = openMigrated(file)
            try {
                // the v4 rows came through untouched
                val asset = db.assetDao().byId("a1")!!
                assertEquals("Hot tub", asset.name)
                assertEquals("Spa", asset.category)
                assertEquals(listOf("t1"), db.nfcTagDao().forAsset("a1").map { it.id })
                assertEquals(listOf("e1"), db.eventDao().forAsset("a1").map { it.event.id })

                // and the new table is there, empty, and takes a row on either owner
                val repo = RoomAttachmentRepository(db.attachmentDao())
                assertEquals(0, repo.count())
                repo.upsert(
                    attachment("att-1", AttachmentOwner.OfAsset(AssetId("a1")), "assets/a1/att-1.pdf"),
                )
                repo.upsert(
                    attachment("att-2", AttachmentOwner.OfEvent(EventId("e1")), "events/e1/att-2.jpg"),
                )
                assertEquals(2, repo.count())
                assertEquals(
                    listOf(AttachmentId("att-1")),
                    repo.forAsset(AssetId("a1")).map { it.id },
                )
                assertEquals(
                    listOf(AttachmentId("att-2")),
                    repo.forOwner(AttachmentOwner.OfEvent(EventId("e1"))).map { it.id },
                )
            } finally {
                db.close()
            }

            withConnection(file) { c ->
                assertTrue("attachment" in c.tableNames())
                assertTrue(c.tableNames().containsAll(JOURNAL_TABLES))
                assertEquals(V5_ATTACHMENT_COLUMNS, c.columnNamesOf("attachment"))
                assertTrue(
                    "v5's indexes must exist, found ${c.indexNamesOn("attachment")}",
                    c.indexNamesOn("attachment").containsAll(V5_ATTACHMENT_INDEXES),
                )
            }
        } finally {
            file.delete()
        }
    }

    /** What a 2B-2 install has on disk: an asset with a tag and an event carrying a reading. */
    private fun seedPhase2b2(c: SQLiteConnection) {
        /* the same four INSERTs as Migration3To4Test.seedPhase2b1, against 4.json's asset columns:
           asset (id..season_end_mmdd), nfc_tag, measurement_definition, asset_event, measurement */
    }

    private fun attachment(id: String, owner: AttachmentOwner, locator: String) =
        com.loosecannon.notenfc.core.model.Attachment(
            id = AttachmentId(id), owner = owner, kind = AttachmentKind.DOCUMENT,
            mode = AttachmentMode.MANAGED, displayName = "$id.pdf", mimeType = "application/pdf",
            sizeBytes = 12L, sha256 = "a".repeat(64), storageProvider = StorageProvider.SAF_TREE,
            storageLocator = locator, capturedOn = null, notes = "", createdAt = 1L, updatedAt = 1L,
        )

    private companion object {
        val V5_ATTACHMENT_COLUMNS = setOf(
            "id", "asset_id", "event_id", "kind", "mode", "display_name", "mime_type",
            "size_bytes", "sha256", "storage_provider", "storage_locator", "captured_on",
            "notes", "created_at", "updated_at",
        )
        val V5_ATTACHMENT_INDEXES = setOf(
            "index_attachment_asset_id",
            "index_attachment_event_id",
            "index_attachment_storage_provider_storage_locator",
        )
    }
}
```

`Migration1To5Test.kt` — the same shape as the existing `Migration1To4Test`: build a v1 database from `1.json`, seed one asset, one tag and one link, open through Room so all four migrations run in order, then assert the v1 rows survive, the journal tables exist and `attachment` exists and is empty.

`AttachmentDaoTest.kt`:

```kotlin
package com.loosecannon.notenfc.data.room

import /* ...core model, ports, inMemoryDb... */
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentDaoTest {

    private val db = inMemoryDb()
    private val assets = RoomAssetRepository(db.assetDao())
    private val events = RoomEventRepository(db.eventDao())
    private val attachments = RoomAttachmentRepository(db.attachmentDao())

    @After fun close() = db.close()

    @Test fun roundTripsARowOnAnAssetAndOnAnEvent() = runTest { /* upsert, get, forOwner, all, count */ }

    @Test fun deletingTheAssetCascadesItsAttachmentRows() = runTest {
        // seed asset a1 + attachment att-1 on it, plus asset a2 + att-2
        assets.delete(AssetId("a1"))
        assertNull(attachments.get(AttachmentId("att-1")))
        assertEquals(1, attachments.count())
    }

    @Test fun deletingTheEventCascadesItsAttachmentRows() = runTest {
        // seed asset a1 + event e1 + attachment att-1 on the event
        events.delete(EventId("e1"))
        assertNull(attachments.get(AttachmentId("att-1")))
    }

    @Test fun deletingTheAssetAlsoTakesItsEventsAttachments() = runTest {
        // asset -> event -> attachment: two cascades in one delete
        assets.delete(AssetId("a1"))
        assertEquals(0, attachments.count())
    }

    @Test fun twoRowsCannotClaimTheSameProviderAndLocator() = runTest {
        // att-1 at assets/a1/att-1.pdf, then att-2 at the same locator
        val boom = runCatching { attachments.upsert(second) }.exceptionOrNull()
        assertTrue("expected a unique-index failure, got $boom", boom != null)
    }

    @Test fun aRowWithBothOwnersOrNeitherIsRefusedByTheMapper() = runTest {
        val both = AttachmentEntity(id = "x", assetId = "a1", eventId = "e1", /* ... */)
        assertThrows(IllegalArgumentException::class.java) { both.requireExactlyOneOwner() }
        val neither = both.copy(assetId = null, eventId = null)
        assertThrows(IllegalArgumentException::class.java) { neither.requireExactlyOneOwner() }
    }

    @Test fun observeForOwnerEmitsOnEveryWriteAndIsOrderedByName() = runTest {
        // upsert "Zebra.pdf" then "Apple.pdf"; the flow's latest is [Apple, Zebra]
        assertEquals(
            listOf("Apple.pdf", "Zebra.pdf"),
            attachments.observeForOwner(AttachmentOwner.OfAsset(AssetId("a1"))).first().map { it.displayName },
        )
    }
}
```

- [ ] **Step 3: Run them and watch them fail, then pass**

Run: `./gradlew :app:testDebugUnitTest --tests '*Migration4To5Test' --tests '*AttachmentDaoTest'`
Expected: FAIL first (the migration is not registered in `MigrationTestSupport.openMigrated`), then PASS once `MIGRATION_4_5` is added to `openMigrated`'s `.addMigrations(...)` list and the table SQL matches `5.json`.

- [ ] **Step 4: Run the whole `:app` JVM suite**

Run: `./gradlew :core:test :app:testDebugUnitTest`
Expected: PASS. `DeleteAsset`/`DeleteEvent` grew an `AttachmentStorage` parameter in Task 3 and `:app` has no implementation of it until Task 7, so this task adds the minimum that keeps both graphs compiling and honest:

```kotlin
// app/src/main/kotlin/com/loosecannon/notenfc/attachments/NoAttachmentStorage.kt
package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState

/**
 * No folder, ever. A placeholder while Task 6 lands the table ahead of Task 7's real resolver:
 * every attachment path refuses, which is the same answer a fresh install gives, so nothing can
 * quietly half-work in between.
 */
object NoAttachmentStorage : AttachmentStorage {
    override fun state(): StoreState = StoreState.NotConfigured
    override fun store(): AttachmentStore? = null
}
```

Wire `AppGraph` and `FakeGraph` with `attachments = RoomAttachmentRepository(db.attachmentDao())`, `NoAttachmentStorage`, the two grown delete constructors, and `SCHEMA_VERSION = 5` (in both). `AppGraph`'s builder gains `MIGRATION_4_5`. The export/import use cases still name `ExportBackup`; rename those two call sites to `ExportBackupSet`/the grown `ImportBackupReplace` here too, so `:app` compiles against the `:core` that exists.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/loosecannon/notenfc/data/room app/schemas app/src/test/kotlin/com/loosecannon/notenfc/data/room
git commit -m "room v5: attachment table and migration"
```

---

### Task 7: The SAF tree store, the storage resolver, thumbnails, and the graph (`:app`)

**Files:**
- Modify: `gradle/libs.versions.toml` (+`documentfile`)
- Modify: `app/build.gradle.kts` (+`implementation(libs.androidx.documentfile)`)
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/attachments/AttachmentRoot.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/attachments/SafTreeAttachmentStore.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/attachments/SafAttachmentStorage.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/attachments/Thumbnails.kt`
- Delete: `app/src/main/kotlin/com/loosecannon/notenfc/attachments/NoAttachmentStorage.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/prefs/AppPrefs.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt`
- Modify: `app/src/main/AndroidManifest.xml`, create `app/src/main/res/xml/file_paths.xml`
- Modify: `app/src/test/kotlin/com/loosecannon/notenfc/testing/FakeGraph.kt`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/attachments/SafAttachmentStorageTest.kt`
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/attachments/ThumbnailsTest.kt`
- Test: modify `app/src/test/kotlin/com/loosecannon/notenfc/prefs/AppPrefsTest.kt`

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: `AttachmentRoot`, `DocumentTreeRoot`, `SafTreeAttachmentStore`, `SafAttachmentStorage`, `Thumbnails`, `AppPrefs.attachmentTreeUri`, `AppPrefs.lastRestoredBackupSetId`, and on `AppGraph`: `attachments`, `attachmentStorage`, `thumbnails`, `addAttachment`, `updateAttachment`, `deleteAttachment`, `restoreArtifacts`, `exportBackupSet`, `cameraCaptureUri()`.

```toml
# gradle/libs.versions.toml
[versions]
documentfile = "1.1.0"

[libraries]
androidx-documentfile = { group = "androidx.documentfile", name = "documentfile", version.ref = "documentfile" }
```

```kotlin
// attachments/AttachmentRoot.kt
package com.loosecannon.notenfc.attachments

import android.content.ContentResolver
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.loosecannon.notenfc.core.ports.AttachmentStore

/**
 * One chosen tree, as [SafAttachmentStorage] needs to see it.
 *
 * This interface exists so the storage resolver is testable at all: spec §5.3 asks for a
 * `rootResolver: (treeUri) -> DocumentFile?` seam, and a `DocumentFile` cannot be constructed in
 * a plain JVM unit test (it is abstract, and every implementation wants a `Context`). The seam is
 * therefore one level up — a resolver hands back an `AttachmentRoot`, production's is a
 * [DocumentTreeRoot] over `DocumentFile.fromTreeUri`, the device-proof suite's is one over
 * `DocumentFile.fromFile`, and a JVM test's is a hand-written fake.
 */
interface AttachmentRoot {
    val displayName: String
    val authority: String
    fun canWrite(): Boolean
    fun store(): AttachmentStore
    /** The `content://` URI of a stored file, for `ACTION_VIEW`; null when it is not there. */
    fun viewUri(locator: String): Uri?
}

class DocumentTreeRoot(
    private val tree: DocumentFile,
    private val resolver: ContentResolver,
) : AttachmentRoot {
    override val displayName: String get() = tree.name ?: tree.uri.lastPathSegment ?: "Folder"
    override val authority: String get() = tree.uri.authority ?: ""
    override fun canWrite(): Boolean = tree.canWrite()
    override fun store(): AttachmentStore = SafTreeAttachmentStore(tree, resolver)
    override fun viewUri(locator: String): Uri? =
        SafTreeAttachmentStore(tree, resolver).documentFor(locator)?.uri
}
```

```kotlin
// attachments/SafTreeAttachmentStore.kt
package com.loosecannon.notenfc.attachments

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.ByteSource
import com.loosecannon.notenfc.core.ports.StoreIoException
import com.loosecannon.notenfc.core.ports.StoredBytes
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The managed store: ordinary documents in a folder the owner picked (spec §5.2). Directories are
 * created on demand by walking the locator's segments with `findFile` then `createDirectory`.
 *
 * Two provider quirks shape the lookup. Some providers append their own extension when the mime
 * type and the requested name disagree, and some normalise the name; so the store records nothing
 * about the created document's name, keeps the locator it asked for, and resolves a locator by
 * `findFile` per segment, **falling back to the first child whose name starts with `<id>.`**. The
 * attachment id is unique, so that match is unambiguous.
 *
 * `put` hashes while it streams (64 KiB buffer) and is the only place an attachment's sha256 is
 * ever computed (spec §11.10); on any failure it deletes the partial document and rethrows as
 * [StoreIoException], so a half-written file never survives to back a row.
 */
class SafTreeAttachmentStore(
    private val tree: DocumentFile,
    private val resolver: ContentResolver,
) : AttachmentStore {

    override suspend fun put(locator: String, source: ByteSource): StoredBytes =
        withContext(Dispatchers.IO) {
            val segments = locator.split('/')
            val directory = directoryFor(segments.dropLast(1))
            val fileName = segments.last()
            // A stale document at the same locator is replaced, not appended to.
            documentIn(directory, fileName)?.delete()
            val document = directory.createFile(mimeFor(fileName), fileName)
                ?: throw StoreIoException("cannot create $locator in ${tree.uri.authority}")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                resolver.openOutputStream(document.uri, "wt")?.use { out ->
                    DigestInputStream(source.open(), digest).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            size += read
                        }
                    }
                } ?: throw StoreIoException("cannot open $locator for writing")
                StoredBytes(
                    sha256 = digest.digest().joinToString("") { b -> "%02x".format(b) },
                    sizeBytes = size,
                )
            } catch (t: Throwable) {
                runCatching { document.delete() }
                throw if (t is StoreIoException) t else StoreIoException("writing $locator failed", t)
            }
        }

    override suspend fun open(locator: String): InputStream? = withContext(Dispatchers.IO) {
        documentFor(locator)?.let { resolver.openInputStream(it.uri) }
    }

    override suspend fun exists(locator: String): Boolean =
        withContext(Dispatchers.IO) { documentFor(locator) != null }

    override suspend fun delete(locator: String) {
        withContext(Dispatchers.IO) { documentFor(locator)?.delete() }
    }

    /** The document at [locator], by exact name then by the `<id>.` prefix. App layer only. */
    fun documentFor(locator: String): DocumentFile? {
        val segments = locator.split('/')
        var directory: DocumentFile = tree
        segments.dropLast(1).forEach { segment ->
            directory = directory.findFile(segment)?.takeIf { it.isDirectory } ?: return null
        }
        return documentIn(directory, segments.last())
    }

    private fun documentIn(directory: DocumentFile, fileName: String): DocumentFile? =
        directory.findFile(fileName)
            ?: directory.listFiles().firstOrNull { child ->
                child.isFile && child.name?.startsWith(fileName.substringBefore('.') + ".") == true
            }

    private fun directoryFor(segments: List<String>): DocumentFile {
        var directory: DocumentFile = tree
        segments.forEach { segment ->
            directory = directory.findFile(segment)?.takeIf { it.isDirectory }
                ?: directory.createDirectory(segment)
                ?: throw StoreIoException("cannot create directory $segment")
        }
        return directory
    }

    /** The provider wants a mime type to create a file; the extension is all we have here. */
    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "")) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}
```

```kotlin
// attachments/SafAttachmentStorage.kt
package com.loosecannon.notenfc.attachments

import android.net.Uri
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.prefs.AppPrefs

/**
 * Owns the tree preference and answers the one question every attachment path asks first: is
 * there a folder, and can we write to it (spec §5.3).
 *
 * `AccessLost` is not a failure mode to be papered over — a persisted grant really does go away
 * when the owner clears the app's data in Settings or the provider is uninstalled — so it is a
 * state with its own wording and its own repair (re-choosing the *same* folder).
 */
class SafAttachmentStorage(
    private val prefs: AppPrefs,
    private val rootResolver: (treeUri: String) -> AttachmentRoot?,
    private val grantCheck: (treeUri: String) -> Boolean,
) : AttachmentStorage {

    override fun state(): StoreState {
        val uri = prefs.attachmentTreeUri ?: return StoreState.NotConfigured
        val root = rootResolver(uri) ?: return StoreState.AccessLost(nameOf(uri))
        if (!grantCheck(uri) || !root.canWrite()) return StoreState.AccessLost(root.displayName)
        return StoreState.Ready(root.displayName, root.authority)
    }

    override fun store(): AttachmentStore? =
        prefs.attachmentTreeUri
            ?.takeIf { state() is StoreState.Ready }
            ?.let { rootResolver(it)?.store() }

    /** For `ACTION_VIEW`: the document's own URI, or null when the bytes are not on this device. */
    fun viewUri(locator: String): Uri? =
        prefs.attachmentTreeUri
            ?.takeIf { state() is StoreState.Ready }
            ?.let { rootResolver(it)?.viewUri(locator) }

    /** A folder whose root we cannot even resolve still has a name worth showing. */
    private fun nameOf(uri: String): String =
        uri.substringAfterLast("%2F").substringAfterLast('/').ifEmpty { "Folder" }
}
```

```kotlin
// attachments/Thumbnails.kt
package com.loosecannon.notenfc.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.loosecannon.notenfc.core.model.Attachment
import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Image thumbnails without an image library (spec §8.2, §11.9): bounds-only decode for the
 * sample size, a second decode at that size, JPEG 80 into the OS cache directory.
 *
 * The cache name carries the row's id *and* the first eight characters of its sha256, so bytes
 * that changed can never be served from a stale thumbnail, and the file for a deleted row is
 * simply never asked for again. The cache is `cacheDir`, so it may vanish at any time; nothing
 * depends on it existing.
 */
class Thumbnails(
    private val cacheDir: File,
    private val storage: AttachmentStorage,
    private val maxEdgePx: Int = 256,
) {
    /** `<cacheDir>/thumbs/<attachment-id>-<sha256 prefix 8>.jpg`. Pure, and the test's subject. */
    fun cacheFileFor(id: AttachmentId, sha256: String): File =
        File(File(cacheDir, "thumbs"), "${id.value}-${sha256.take(8)}.jpg")

    /** The cached thumbnail, decoding it first if need be. Null when it cannot be produced. */
    suspend fun thumbnail(attachment: Attachment): File? = withContext(Dispatchers.IO) {
        val target = cacheFileFor(attachment.id, attachment.sha256)
        if (target.isFile && target.length() > 0L) return@withContext target
        val store = storage.store() ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        store.open(attachment.storageLocator)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        val bitmap = store.open(attachment.storageLocator)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return@withContext null

        target.parentFile?.mkdirs()
        val ok = runCatching {
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        }.getOrDefault(false)
        bitmap.recycle()
        if (ok) target else null.also { runCatching { target.delete() } }
    }

    /** The smallest power of two that brings the long edge to [maxEdgePx] or below. */
    internal fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var edge = maxOf(width, height)
        while (edge / 2 >= maxEdgePx) {
            edge /= 2
            sample *= 2
        }
        return sample
    }
}
```

```kotlin
// prefs/AppPrefs.kt — appended, in the shape of the prefs already there
/** The SAF tree the owner chose for attachments. Device-local, never in a backup. */
var attachmentTreeUri: String?
    get() = store.getString(KEY_ATTACHMENT_TREE)
    set(value) = store.putString(KEY_ATTACHMENT_TREE, value ?: "")

/** The set id of the last data archive restored, so a later artifacts restore can refuse (§7.3). */
var lastRestoredBackupSetId: String?
    get() = store.getString(KEY_LAST_RESTORED_SET)
    set(value) = store.putString(KEY_LAST_RESTORED_SET, value ?: "")
```

> `KeyValueStore.putString` cannot store null, and both getters must read "" as "never set". Add
> `private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }` in the file and
> apply it in both getters, so clearing a preference and never setting it are one state.

```kotlin
// di/AppGraph.kt — the additions
.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)

val attachments: AttachmentRepository = RoomAttachmentRepository(db.attachmentDao())

/**
 * Swapped only by the instrumented suite, which has no SAF picker to drive and no persisted
 * grant to check (spec §12): it points these at `DocumentFile.fromFile` on an app-external
 * directory. Production never reassigns them.
 */
@VisibleForTesting
var attachmentRootResolver: (String) -> AttachmentRoot? = { treeUri ->
    DocumentFile.fromTreeUri(context.applicationContext, treeUri.toUri())
        ?.let { DocumentTreeRoot(it, context.applicationContext.contentResolver) }
}

@VisibleForTesting
var attachmentGrantCheck: (String) -> Boolean = { treeUri ->
    context.applicationContext.contentResolver.persistedUriPermissions.any {
        it.uri.toString() == treeUri && it.isReadPermission && it.isWritePermission
    }
}

val attachmentStorage: SafAttachmentStorage = SafAttachmentStorage(
    prefs = prefs,
    rootResolver = { uri -> attachmentRootResolver(uri) },
    grantCheck = { uri -> attachmentGrantCheck(uri) },
)

val thumbnails: Thumbnails = Thumbnails(context.applicationContext.cacheDir, attachmentStorage)

// Phase 4A — attachments.
val addAttachment: AddAttachment =
    AddAttachment(attachments, assets, events, attachmentStorage, uow, ids, clock)
val updateAttachment: UpdateAttachment = UpdateAttachment(attachments, uow, clock)
val deleteAttachment: DeleteAttachment = DeleteAttachment(attachments, attachmentStorage, uow)
val restoreArtifacts: RestoreArtifacts = RestoreArtifacts(attachments, attachmentStorage)

val exportBackupSet: ExportBackupSet = ExportBackupSet(
    assets, tags, links, definitions, profiles, events, attachments, uow, ids, clock,
    BuildConfig.VERSION_NAME, SCHEMA_VERSION,
)
val importBackupReplace: ImportBackupReplace = ImportBackupReplace(
    assets, tags, links, definitions, profiles, events, attachments, attachmentStorage, uow,
)
val deleteAsset: DeleteAsset = DeleteAsset(assets, events, attachments, attachmentStorage, uow)
val deleteEvent: DeleteEvent = DeleteEvent(events, attachments, attachmentStorage, uow)

/** A cache file the camera can write into through the FileProvider (spec §9.3). */
fun cameraCaptureUri(): Uri {
    val file = File(File(context.applicationContext.cacheDir, "camera"), "${ids.newId()}.jpg")
    file.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        context.applicationContext,
        "${BuildConfig.APPLICATION_ID}.files",
        file,
    )
}

private companion object {
    const val DB_NAME = "notenfc.db"
    const val SCHEMA_VERSION = 5
}
```

> `AppGraph`'s constructor currently keeps no reference to its `Context`. Change the parameter to
> `private val context: Context` so the three members above can reach `applicationContext`; nothing
> else in the class changes.

```xml
<!-- AndroidManifest.xml — inside <application> -->
<!-- The camera writes into cacheDir/camera/ and hands the URI straight back (spec §9.3). -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- AndroidManifest.xml — inside the existing <queries>, so resolveActivity can answer "no viewer" -->
<intent>
    <action android:name="android.intent.action.VIEW" />
    <data android:scheme="content" android:mimeType="*/*" />
</intent>
```

```xml
<!-- app/src/main/res/xml/file_paths.xml -->
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="camera" path="camera/" />
</paths>
```

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/com/loosecannon/notenfc/attachments/SafAttachmentStorageTest.kt`:

```kotlin
package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.StoreState
import com.loosecannon.notenfc.prefs.AppPrefs
import com.loosecannon.notenfc.prefs.KeyValueStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `state()` is the gate every attachment path passes through, so it is worth a JVM test of its
 * own. The `DocumentFile` is behind [AttachmentRoot] precisely so this can run without an
 * emulator; the real tree is proved by `SafTreeAttachmentStoreContractTest` (Task 10).
 */
class SafAttachmentStorageTest {

    private class MapStore : KeyValueStore {
        private val longs = mutableMapOf<String, Long>()
        private val strings = mutableMapOf<String, String>()
        override fun getLong(key: String): Long? = longs[key]
        override fun putLong(key: String, value: Long) { longs[key] = value }
        override fun getString(key: String): String? = strings[key]
        override fun putString(key: String, value: String) { strings[key] = value }
    }

    private class FakeRoot(
        override val displayName: String = "Attachments",
        override val authority: String = "com.example.documents",
        private val writable: Boolean = true,
    ) : AttachmentRoot {
        override fun canWrite(): Boolean = writable
        override fun store(): AttachmentStore = error("not needed for state()")
        override fun viewUri(locator: String) = null
    }

    private val prefs = AppPrefs(MapStore())

    private fun storage(
        root: AttachmentRoot? = FakeRoot(),
        granted: Boolean = true,
    ) = SafAttachmentStorage(prefs, { root }, { granted })

    @Test fun noPreferenceIsNotConfigured() {
        assertEquals(StoreState.NotConfigured, storage().state())
        assertNull(storage().store())
    }

    @Test fun aResolvableWritableGrantedTreeIsReady() {
        prefs.attachmentTreeUri = "content://com.example.documents/tree/spa"
        assertEquals(
            StoreState.Ready("Attachments", "com.example.documents"),
            storage().state(),
        )
    }

    @Test fun aRevokedGrantAnUnresolvableTreeAndAReadOnlyTreeAreAllAccessLost() {
        prefs.attachmentTreeUri = "content://com.example.documents/tree/spa"
        assertEquals(StoreState.AccessLost("Attachments"), storage(granted = false).state())
        assertEquals(StoreState.AccessLost("Attachments"), storage(root = FakeRoot(writable = false)).state())
        // an unresolvable root still names the folder from the last path segment
        assertEquals(StoreState.AccessLost("spa"), storage(root = null).state())
        assertNull(storage(granted = false).store())
    }

    @Test fun clearingThePreferenceGoesBackToNotConfigured() {
        prefs.attachmentTreeUri = "content://com.example.documents/tree/spa"
        prefs.attachmentTreeUri = null
        assertEquals(StoreState.NotConfigured, storage().state())
    }
}
```

`app/src/test/kotlin/com/loosecannon/notenfc/attachments/ThumbnailsTest.kt`:

```kotlin
package com.loosecannon.notenfc.attachments

import com.loosecannon.notenfc.core.model.AttachmentId
import com.loosecannon.notenfc.core.ports.AttachmentStore
import com.loosecannon.notenfc.core.ports.AttachmentStorage
import com.loosecannon.notenfc.core.ports.StoreState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * The naming rule and the sample-size arithmetic, which are the two parts of [Thumbnails] that do
 * not need `BitmapFactory`. The decode itself is exercised on the emulator (Task 10).
 */
class ThumbnailsTest {

    private val noStore = object : AttachmentStorage {
        override fun state(): StoreState = StoreState.NotConfigured
        override fun store(): AttachmentStore? = null
    }
    private val thumbnails = Thumbnails(File("/tmp-not-touched"), noStore)

    @Test fun theCacheNameCarriesTheIdAndTheShaPrefix() {
        val file = thumbnails.cacheFileFor(AttachmentId("att-1"), "abcdef0123456789".repeat(4))
        assertEquals("att-1-abcdef01.jpg", file.name)
        assertEquals("thumbs", file.parentFile!!.name)
    }

    @Test fun bytesThatChangedCannotBeServedFromTheOldThumbnail() {
        val before = thumbnails.cacheFileFor(AttachmentId("att-1"), "a".repeat(64))
        val after = thumbnails.cacheFileFor(AttachmentId("att-1"), "b".repeat(64))
        assertNotEquals(before, after)
    }

    @Test fun sampleSizeBringsTheLongEdgeToTwoFiftySixOrBelow() {
        assertEquals(1, thumbnails.sampleSize(256, 128))
        assertEquals(1, thumbnails.sampleSize(400, 300))
        assertEquals(2, thumbnails.sampleSize(512, 384))
        assertEquals(8, thumbnails.sampleSize(4032, 3024))
        assertEquals(1, thumbnails.sampleSize(0, 0))
    }
}
```

Append to `AppPrefsTest`: `attachmentTreeUriRoundTripsAndClears`, `lastRestoredBackupSetIdRoundTripsAndClears` — set, read back, set to null, read back null.

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*SafAttachmentStorageTest' --tests '*ThumbnailsTest'`
Expected: FAIL — `SafAttachmentStorage`, `AttachmentRoot`, `Thumbnails`, `AppPrefs.attachmentTreeUri` unresolved.

- [ ] **Step 3: Implement the store, the resolver, the thumbnails and the wiring**

Add the catalog entry and the app dependency; create the four files in `attachments/`; delete `NoAttachmentStorage.kt`; extend `AppPrefs`; wire `AppGraph` (including `private val context: Context`) and `FakeGraph` (`attachments`, a local `FakeAttachmentStorage` in `app/src/test/kotlin/com/loosecannon/notenfc/testing/`, `addAttachment`, `updateAttachment`, `deleteAttachment`, `restoreArtifacts`, `exportBackupSet`, the grown `importBackupReplace`/`deleteAsset`/`deleteEvent`, `SCHEMA_VERSION = 5`); add the provider, the `<queries>` intent and `file_paths.xml`.

> `:app`'s test source set cannot see `:core`'s test fixtures, so `FakeGraph` gets its own
> `FakeAttachmentStorage` — an in-memory map store plus a `var state` — mirroring the `:core` one.
> Put it in `app/src/test/kotlin/com/loosecannon/notenfc/testing/FakeAttachmentStorage.kt` and say
> in its KDoc that it is the `:app` twin of the core fixture.

- [ ] **Step 4: Run the tests, then build the app**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`
Expected: PASS, and the debug APK builds with the new provider and the new dependency.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main app/src/test
git commit -m "saf tree attachment store, storage resolver, thumbnails"
```

---

### Task 8: The DOCUMENTS section, the edit sheet, the viewer, the pickers and the camera (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/attachments/AttachmentsSectionViewModel.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/attachments/DocumentsSection.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/attachments/AttachmentEditSheet.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/attachments/AttachmentPickers.kt`
- Create: `app/src/main/res/drawable/ic_photo.xml`, `app/src/main/res/drawable/ic_attach_file.xml`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/components/NoteNfcIcons.kt` (+`Photo`, +`AttachFile`)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/asset/AssetDetailScreen.kt` (DOCUMENTS after LINKS)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/journal/EventDetailScreen.kt` (DOCUMENTS after the readings)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/nav/NoteNfcApp.kt` (the new `onOpenSettings` on both screens)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/attachments/AttachmentsSectionViewModelTest.kt`

**Interfaces:**
- Consumes: Task 7's `AppGraph.attachmentStorage`/`thumbnails`/`addAttachment`/`updateAttachment`/`deleteAttachment`/`cameraCaptureUri()`, Task 3's commands and `AttachmentResult`, Task 1's model.
- Produces:

```kotlin
// ui/attachments/AttachmentsSectionViewModel.kt — the state and the API the two screens use
/** One picked or captured file, as the section hands it to the use case. */
data class PickedFile(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val fromCamera: Boolean = false,
    val open: () -> java.io.InputStream,
)

/** One DOCUMENTS row. `present` is false when the bytes are not on this device (spec §7.2). */
data class AttachmentRowState(
    val id: String,
    val displayName: String,
    val kind: AttachmentKind,
    val sizeBytes: Long,
    val capturedOn: String?,
    val notes: String,
    val mimeType: String,
    val locator: String,
    val isImage: Boolean,
    val present: Boolean,
    val thumbnail: java.io.File? = null,
)

data class AttachmentsSectionState(
    val store: StoreState = StoreState.NotConfigured,
    val rows: List<AttachmentRowState> = emptyList(),
    /** "Adding 3 of 8…" while a multi-select runs; null otherwise (spec §8.1). */
    val progress: String? = null,
)

class AttachmentsSectionViewModel(
    private val owner: AttachmentOwner,
    private val attachments: AttachmentRepository,
    private val storage: SafAttachmentStorage,
    private val addAttachment: AddAttachment,
    private val updateAttachment: UpdateAttachment,
    private val deleteAttachment: DeleteAttachment,
    private val thumbnails: Thumbnails,
) : ViewModel() {

    constructor(graph: AppGraph, owner: AttachmentOwner) : this(
        owner, graph.attachments, graph.attachmentStorage, graph.addAttachment,
        graph.updateAttachment, graph.deleteAttachment, graph.thumbnails,
    )

    val state: StateFlow<AttachmentsSectionState>
    /** One line per finished operation or refusal, shown once (the 1C snackbar pattern). */
    val messages: SharedFlow<String>

    /** Sequential, so a failure names its file and the rest still land (spec §8.1). */
    fun add(files: List<PickedFile>)
    fun save(id: String, cmd: UpdateAttachmentCommand)
    fun delete(id: String)
    /** Null when the bytes are not on this device; the caller shows the snackbar. */
    fun viewUri(locator: String): android.net.Uri? = storage.viewUri(locator)
}
```

Behaviour the state flow owns:

- `store` is re-read from `storage.state()` on every emission, so coming back from Settings with a folder chosen flips the section from the `StatusBlock` to the list with no manual refresh.
- `rows` comes from `attachments.observeForOwner(owner)` mapped to `AttachmentRowState`, with `present = store.exists(locator)` and `thumbnail` filled from a cache the ViewModel holds: images are decoded one at a time on `Dispatchers.IO` after the rows land, and a failure leaves `thumbnail` null so the row falls back to its kind glyph.
- Refusals map to one line each: `NoStore` to "Choose an attachment folder in Settings first", `StoreUnavailable` to "The attachment folder is not available", `TooLarge` to "That file is larger than 256 MB", `BlankName` to "Give the file a name", `OwnerMissing` to "That file is no longer here", `Unchanged` to no message at all (the sheet simply closes).

The composables:

```kotlin
// ui/attachments/DocumentsSection.kt
/**
 * DOCUMENTS, as the Apollo Service Binder draws a list section (D12 §8): a `SectionHeader` with
 * the count in its title, a 56 dp leading thumbnail or kind glyph, a one-line ellipsised name, a
 * quiet `kind · size · captured-on` line, and a trailing overflow. No cards, no FAB.
 */
@Composable
fun DocumentsSection(
    state: AttachmentsSectionState,
    onOpen: (AttachmentRowState) -> Unit,
    onEdit: (AttachmentRowState) -> Unit,
    onAddFiles: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenSettings: () -> Unit,
)

/** The wrapper both detail screens call: it owns the ViewModel, the pickers and the sheet. */
@Composable
fun AttachmentsSection(
    graph: AppGraph,
    owner: AttachmentOwner,
    onOpenSettings: () -> Unit,
)
```

- Header: `SectionHeader(title = "Documents · ${state.rows.size}")` when there are rows, plain `"Documents"` when there are none.
- Empty and `Ready`: `QuietLine("No documents yet")` plus the two actions.
- `NotConfigured` or `AccessLost`: a `StatusBlock` — headline "Attachment storage", title "Attachment storage not set up", detail "Choose a folder in Settings", `NoteNfcIcons.CloudOff`, `leftRule = false`, the `seasonInactive` family — and a `TextButton("Open settings")` calling `onOpenSettings`. Both add actions are hidden rather than merely disabled: there is nowhere for the bytes to go.
- A row with `present = false`: glyph at 38% alpha, quiet line "Not on this device", and a tap that reports the same wording instead of launching an intent.
- `state.progress?.let { QuietLine(it) }` under the actions.
- Size formatting: a private `fun Long.asFileSize(): String` — under 1024 is "N B", under a mebibyte is one-decimal KB, else one-decimal MB. It lives in this file because nothing else needs it.

```kotlin
// ui/attachments/AttachmentEditSheet.kt
/**
 * Rename, re-kind, captured-on, notes, and Delete, in a `ModalBottomSheet` (spec §8.1). Delete is
 * a plain confirmation, not a typed one (spec §11.7): it removes one file from the owner's own
 * folder, which is not the weight of deleting an asset.
 */
@Composable
fun AttachmentEditSheet(
    row: AttachmentRowState,
    onSave: (UpdateAttachmentCommand) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
)
```

- Name: `OutlinedTextField`. Kind: a `FlowRow` of `FilterChip`s over all seven `AttachmentKind` entries with sentence-case labels ("Label photo", "Warranty", and so on). Captured on: the same ISO date field plus `DatePickerDialog` the event entry form uses. Notes: a multi-line field.
- Delete: a `TextButton` in the destructive family opening an `AlertDialog` — title "Delete file?", body `"Delete ${row.displayName}? The file is removed from your attachment folder."`, confirm "Delete", dismiss "Cancel".

```kotlin
// ui/attachments/AttachmentPickers.kt
/**
 * The two launchers and the one intent the section needs, kept out of the screens: a multi-select
 * document picker, a camera capture into a `FileProvider` cache URI, and `ACTION_VIEW`.
 */
@Composable
fun rememberAttachmentPickers(
    graph: AppGraph,
    onPicked: (List<PickedFile>) -> Unit,
    onNoViewer: () -> Unit,
): AttachmentPickers

class AttachmentPickers internal constructor(
    val addFiles: () -> Unit,        // OpenMultipleDocuments, arrayOf("*/*")
    val takePhoto: () -> Unit,       // TakePicture into graph.cameraCaptureUri()
    val open: (AttachmentRowState) -> Unit,
)
```

- `addFiles` maps every returned `Uri` through the resolver: the display name and size from `OpenableColumns.DISPLAY_NAME` and `OpenableColumns.SIZE`, the mime from `resolver.getType(uri)`, `open = { resolver.openInputStream(uri)!! }`. `capturedOn` is defaulted to today by the caller.
- `takePhoto` remembers the capture URI across the launch and **deletes the temp file either way** — on success once `AddAttachment` has copied it, on cancel immediately.
- `open` resolves `viewUri(locator)`, builds `Intent(ACTION_VIEW)` with `setDataAndType(uri, row.mimeType)` and `FLAG_GRANT_READ_URI_PERMISSION`, and calls `onNoViewer()` when `resolveActivity` finds nothing ("No app can open this file").

Screen wiring:

- `AssetDetailScreen` — one new line in the scrolling column, **after `LinksSection`** and before `NotesSection`:
  ```kotlin
  AttachmentsSection(
      graph = graph,
      owner = AttachmentOwner.OfAsset(AssetId(assetId)),
      onOpenSettings = onOpenSettings,
  )
  ```
  It gains one parameter, `onOpenSettings: () -> Unit`, wired in `NoteNfcApp` to pushing `Route.Settings` (which already exists — 4A adds no `Route`).
- `EventDetailScreen` — the same line **after `ReadingsSection`** and before `MaterialsSection`, with `AttachmentOwner.OfEvent(EventId(eventId))` and the same new parameter.

- [ ] **Step 1: Write the failing ViewModel tests**

`app/src/test/kotlin/com/loosecannon/notenfc/ui/attachments/AttachmentsSectionViewModelTest.kt`, on `FakeGraph` (Room-backed, so the observe flow is the production one):

```kotlin
class AttachmentsSectionViewModelTest {

    private val graph = FakeGraph()
    private lateinit var assetId: AssetId

    @Before fun seed() = runBlocking {
        assetId = graph.createAsset.run(AssetCommand(name = "Hot tub")).id
    }

    @After fun close() = graph.close()

    private fun model(owner: AttachmentOwner = AttachmentOwner.OfAsset(assetId)) =
        AttachmentsSectionViewModel(
            owner, graph.attachments, graph.attachmentStorage, graph.addAttachment,
            graph.updateAttachment, graph.deleteAttachment, graph.thumbnails,
        )

    private fun picked(name: String, mime: String = "application/pdf", body: String = "x") =
        PickedFile(name, mime, body.length.toLong()) { body.toByteArray().inputStream() }

    @Test fun aFreshInstallSaysTheStoreIsNotConfiguredAndListsNothing() {
        // state.store == StoreState.NotConfigured, rows empty, and add() emits the Settings line
    }

    @Test fun withAFolderChosenAddedFilesAppearAsRowsOrderedByName() {
        // storage ready; add "Zebra.pdf" then "Apple.pdf"
        // rows == ["Apple.pdf", "Zebra.pdf"], both present, both kind DOCUMENT
    }

    @Test fun addingSeveralFilesReportsProgressAndKeepsGoingPastAFailure() {
        // three files with the middle one rigged to fail in the store
        // messages carries a line naming the failed file; the other two are rows
        // progress is null again once the batch finishes
    }

    @Test fun anAccessLostStoreRefusesAddAndSaysWhyOnce() {
        // messages == "The attachment folder is not available", rows unchanged
    }

    @Test fun aRowWhoseBytesAreGoneIsMarkedNotPresent() {
        // add a file, then delete the bytes behind the store's back: the row stays, present is false
    }

    @Test fun savingRenamesTheRowAndLeavesTheLocatorAlone() {
        // UpdateAttachmentCommand("Installation guide", MANUAL, "2026-09-14", ""), then assert both
    }

    @Test fun savingNothingIsSilent() {
        // the same values back: Unchanged emits no message
    }

    @Test fun deletingRemovesTheRowAndTheBytes() {
        // rows empty and the store no longer has the locator
    }

    @Test fun anEventOwnerSeesOnlyItsOwnFiles() {
        // one attachment on the asset, one on an event of that asset; each ViewModel sees one row
    }
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*AttachmentsSectionViewModelTest'`
Expected: FAIL — `AttachmentsSectionViewModel` unresolved.

- [ ] **Step 3: Implement the ViewModel, then the composables**

Write `AttachmentsSectionViewModel.kt` first and get the tests green; then the three composable files, the two drawables (24 dp Material outlined paths in the shape of the existing `res/drawable/ic_*.xml`), the two `NoteNfcIcons` entries, and the two screen call sites plus their new `onOpenSettings` parameter in `NoteNfcApp`.

- [ ] **Step 4: Run the tests and build**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main app/src/test
git commit -m "documents section on asset and event detail"
```

---

### Task 9: Settings → Attachment storage, and the backup set on the Backup screen (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/backup/SafBackupSetIO.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/backup/BackupViewModel.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/backup/BackupScreen.kt`
- Test: modify `app/src/test/kotlin/com/loosecannon/notenfc/ui/backup/BackupViewModelTest.kt`

**Interfaces:**
- Consumes: Task 5's `ExportBackupSet`/`BackupSet`/`ArtifactsCodec`/`RestoreArtifacts`/`ArtifactsReport`/`ArtifactsSetMismatch`, Task 7's `attachmentStorage` and the two new preferences.
- Produces:

```kotlin
// backup/SafBackupSetIO.kt
package com.loosecannon.notenfc.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A folder the owner picked for *this* export only — no persisted grant here (spec §11.2; 3R turns
 * it into a remembered destination). Two files go in, both stamped, and the data file is removed
 * again if the artifacts file cannot be written: half a set is worse than none, because a restore
 * would look possible and then not be.
 */
class SafBackupSetWriter(
    private val context: Context,
    private val resolver: ContentResolver,
    private val tree: DocumentFile,          // the screen passes DocumentFile.fromTreeUri(...)
) : BackupSetSink {
    /**
     * Creates the document, streams [body] into it, and — the invariant the owner asked for —
     * deletes that document again if [body] throws, then rethrows. A failed export must leave
     * no file from that attempted set, and the caller only knows the handles of writes that
     * returned.
     */
    override suspend fun write(name: String, body: suspend (OutputStream) -> Unit): String =
        withContext(Dispatchers.IO) {
            val document = tree.createFile("application/zip", name)
                ?: error("cannot create $name in the chosen folder")
            try {
                resolver.openOutputStream(document.uri, "wt")?.use { body(it) }
                    ?: error("cannot open $name for writing")
            } catch (t: Throwable) {
                runCatching { document.delete() }
                throw t
            }
            document.uri.toString()
        }

    override suspend fun delete(handle: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                DocumentFile.fromSingleUri(context.applicationContext, handle.toUri())?.delete()
            }
        }
    }
}

/** `noteNFC-data-<stamp>.zip` and `noteNFC-artifacts-<stamp>.zip`, stamp = local `yyyyMMdd-HHmmss`. */
object BackupSetNames {
    fun stamp(at: Long): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date(at))
    fun data(stamp: String): String = "noteNFC-data-$stamp.zip"
    fun artifacts(stamp: String): String = "noteNFC-artifacts-$stamp.zip"
}
```

```kotlin
// ui/backup/BackupViewModel.kt — the new surface
/** A destination for one export: two named files, and a way to take one back. */
interface BackupSetSink {
    /** Returns a handle the caller can pass to [delete] — in the app, the document's URI. */
    suspend fun write(name: String, body: suspend (java.io.OutputStream) -> Unit): String
    suspend fun delete(handle: String)
}

data class BackupState(
    val lastBackupAt: Long? = null,
    val busy: Boolean = false,
    /** The set id of the last data archive restored here, which the files archive must match. */
    val lastRestoredBackupSetId: String? = null,
)

class BackupViewModel(
    private val exportBackupSet: ExportBackupSet,
    private val importBackupReplace: ImportBackupReplace,
    private val restoreArtifacts: RestoreArtifacts,
    private val storage: AttachmentStorage,
    private val prefs: AppPrefs,
    private val clock: Clock,
) : ViewModel() {

    constructor(graph: AppGraph) : this(
        graph.exportBackupSet, graph.importBackupReplace, graph.restoreArtifacts,
        graph.attachmentStorage, graph.prefs, graph.clock,
    )

    /**
     * Writes both archives and marks the export **only when both landed** (spec §7.3): the nudge
     * is a promise that a restorable set exists, and a data file with no artifacts beside it is
     * not one. A failed artifacts write deletes the data file this export already wrote.
     */
    /**
     * Completeness rule (owner's ruling, spec §7.3): the set is a backup only if every managed
     * row's bytes landed in the artifacts archive with the planned size. A missing or drifted
     * file fails the whole export, both files are removed, and `lastBackupAt` does not move.
     */
    suspend fun exportSet(sink: BackupSetSink): Result<String> = runCatching {
        val set = exportBackupSet.run()
        val stamp = BackupSetNames.stamp(set.plan.createdAt)
        val dataHandle = sink.write(BackupSetNames.data(stamp)) { out -> out.write(set.data) }
        var artifactsHandle: String? = null
        try {
            lateinit var written: ArtifactsWritten
            artifactsHandle = sink.write(BackupSetNames.artifacts(stamp)) { out ->
                // With zero attachments this is a manifest-only archive: a set is always two files.
                written = ArtifactsCodec.write(out, set.plan) { locator -> storage.store()?.open(locator) }
            }
            if (!written.covers(set.plan)) throw BackupSetIncomplete(written.missing, written.mismatched)
        } catch (t: Throwable) {
            artifactsHandle?.let { sink.delete(it) }   // the SAF sink already removed a partial one; harmless
            sink.delete(dataHandle)
            throw t
        }
        prefs.markBackupExported(clock.nowMillis())
        _state.update { it.copy(lastBackupAt = prefs.lastBackupAt) }
        BackupSetNames.data(stamp) + " + " + BackupSetNames.artifacts(stamp)
    }.rethrowCancellation()

    /** Wipes and loads the data archive, and remembers the set id the files step must match. */
    suspend fun restoreData(io: BackupIO): Result<ImportReport> = runCatching {
        val report = importBackupReplace.run(io.read())
        prefs.lastRestoredBackupSetId = report.lastRestoredBackupSetId
        _state.update { it.copy(lastRestoredBackupSetId = prefs.lastRestoredBackupSetId) }
        report
    }.rethrowCancellation()

    suspend fun restoreFiles(io: BackupIO): Result<ArtifactsReport> = runCatching {
        io.openStream().use { restoreArtifacts.run(it, prefs.lastRestoredBackupSetId) }
    }.rethrowCancellation()

    fun exportSetTo(sink: BackupSetSink)
    fun restoreDataFrom(io: BackupIO)
    fun restoreFilesFrom(io: BackupIO)
}
```

> `BackupIO` reads whole byte arrays, which is right for a data archive and wrong for an artifacts
> archive that may be hundreds of megabytes. Add one method to the port rather than a second port:
> `suspend fun openStream(): java.io.InputStream` on `:core.ports.BackupIO`, implemented in
> `SafBackupIO` as `resolver.openInputStream(uri) ?: error(...)` and in the test double as
> `bytes.inputStream()`. `read()` stays for the data side.

Message wording, one line each, through the existing `messages` flow:

- export: `"Exported as <data name> + <artifacts name>"`; a partial export: `"Export failed: the files archive could not be written. Nothing was saved."`
- `restoreData`: the existing counts line, plus, when `report.attachments > 0`, `" · ${report.attachments} attachments listed; restore the files archive to get their contents"`.
- `restoreFiles`: `"Restored <restored> files, skipped <skipped>"`; on `ArtifactsSetMismatch`, `"Those files belong to backup set <found.take(8)>, not <expected.take(8)>"`; on `StoreIoException`, `"Choose an attachment folder in Settings first"`.

Screens:

- **Settings** gains a section between Theme and Utilities:

  ```
  SectionHeader("Attachment storage")
  LabelValue("Folder", <display name> | "Not set")
  LabelValue("Provider", <authority>)     // only in Ready or AccessLost
  QuietLine("Files are written as ordinary documents in this folder; a sync tool such as Syncthing owns any off-device copy.")
  Button("Choose folder")                 // ActivityResultContracts.OpenDocumentTree
  ```

  After the picker returns: first release the previously stored tree's grant when there is one and it differs (spec §5.3; S5 showed grants accumulate otherwise):

```kotlin
val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
prefs.attachmentTreeUri?.takeIf { it != uri.toString() }?.let { old ->
    runCatching { resolver.releasePersistableUriPermission(Uri.parse(old), flags) }
}
resolver.takePersistableUriPermission(uri, flags)
prefs.attachmentTreeUri = uri.toString()
```

  then re-read `graph.attachmentStorage.state()` into the screen's local state. The `runCatching` matters: releasing a grant the system no longer holds throws `SecurityException`, and that must not block taking the new one.

  With at least one attachment row (`graph.attachments.count() > 0`, read once in a `LaunchedEffect`) the button is **disabled** under `QuietLine("Moving attachments to another folder arrives in a later release")` (spec §11.12) — **except** in `AccessLost`, where re-choosing the *same* folder is the repair: the button is enabled, and a pick whose authority plus tree document id differ from the stored ones is refused with the snackbar `"That is a different folder. Choose the same one to restore access."`
- **Backup** replaces its single `CreateDocument` export — the 1C path, removed per spec §7.3 — with three actions: `Export backup set` (`OpenDocumentTree`), `Restore data` (`OpenDocument` on the existing `IMPORT_TYPES`, then the existing typed REPLACE dialog unchanged), and `Restore files` (`OpenDocument`, no typed dialog: it adds bytes and deletes nothing). The "Last backup" line and the busy guard are unchanged. A format-4 file still imports through Restore data.

- [ ] **Step 1: Write the failing tests**

Append to `BackupViewModelTest`, which already builds the ViewModel on `FakeGraph` with in-memory IO:

```kotlin
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

private val STAMPED = Regex("""^noteNFC-(data|artifacts)-\d{8}-\d{6}\.zip$""")

@Test fun exportWritesTwoStampedFilesAndMarksTheBackupOnce() {
    // both names match STAMPED and carry the same stamp; lastBackupAt is set; the message names both
}

@Test fun anInstallWithNoAttachmentsStillWritesBothFiles() {
    // the artifacts file exists and holds exactly one entry, manifest.json
}

@Test fun aFailedArtifactsWriteRemovesTheDataFileAndLeavesTheNudgeAlone() {
    // RecordingSink("noteNFC-artifacts"): files empty, deleted names the data file,
    // lastBackupAt still null, the message says nothing was saved
}

@Test fun missingManagedAttachmentMakesExportFailAndLeavesNoArchives() {
    // one attachment row exists but storage.store holds no bytes at its locator:
    // exportSet returns failure with BackupSetIncomplete(missing = [that id]); sink.files is
    // empty; sink.deleted names BOTH stamped files; prefs.lastBackupAt is still null; the
    // screen message says "Backup not saved: 1 attachment file is missing"
}

@Test fun mismatchedManagedAttachmentMakesExportFailAndLeavesNoArchives() {
    // the row's sha256 differs from the bytes in storage.store: same outcome, the message says
    // "1 attachment file has changed since it was added"
}

@Test fun midArtifactsWriteRemovesPartialArtifactsAndDataZip() {
    // a sink whose write records the created name BEFORE running body, and whose body throws
    // half-way for the artifacts name: after exportSet fails, sink.files is empty, sink.deleted
    // contains the data file, and the sink itself dropped the artifacts name (the SAF writer's
    // own delete-on-throw, modelled in the fake); lastBackupAt still null
}

@Test fun restoringDataRemembersTheSetIdAndMentionsTheAttachmentsItListed() {
    // prefs.lastRestoredBackupSetId == the archive's set id; the message carries "attachments listed"
}

@Test fun restoringTheFilesArchiveOfTheSameSetRestoresTheBytes() {
    // export, wipe the store's bytes, restoreData, restoreFiles: the bytes are back, skipped == 0
}

@Test fun restoringTheFilesArchiveOfAnotherSetIsRefusedByTheTwoShortIds() {
    // export twice; feed set 1's artifacts after restoring set 2's data
    // the message carries both eight-character ids and the store gained nothing
}

@Test fun restoringFilesWithNoFolderConfiguredSaysToChooseOne() {
    // storage NotConfigured: the message is the Settings wording, and nothing partial is written
}

@Test fun aFormatFourFileStillImportsThroughRestoreData() {
    // a format-4 archive: the report has 0 attachments and an empty set id, and does not throw
}
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew :app:testDebugUnitTest --tests '*BackupViewModelTest'`
Expected: FAIL — `exportSet`, `BackupSetSink`, `restoreFiles` unresolved.

- [ ] **Step 3: Implement the sink, the ViewModel and the two screens**

Add `openStream()` to `:core.ports.BackupIO` and to `SafBackupIO`; write `backup/SafBackupSetIO.kt`; rework `BackupViewModel`; then the Settings section and the Backup screen. `BackupScreen` builds a `BackupSetSink` that delegates to `SafBackupSetWriter`, and keeps `SafBackupIO` for the two `OpenDocument` reads.

- [ ] **Step 4: Run the tests and build**

Run: `./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/main app/src/main app/src/test
git commit -m "attachment storage settings, backup set export and restore"
```

---

### Task 10: Device proof on the emulator — the store contract and the attachments suite (`:app`)

**Files:**
- Create: `app/src/androidTest/kotlin/com/loosecannon/notenfc/attachments/SafTreeAttachmentStoreContractTest.kt`
- Create: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/AttachmentsDeviceProofTest.kt`
- Modify: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/AppSmokeTest.kt` (`clearInstall` also wipes attachments and the two new preferences)

**This task runs on the emulator only.** `ANDROID_SERIAL` is pinned to the running emulator (API 37.1, no NFC) before anything is launched, and `./gradlew :app:connectedDebugAndroidTest` is never pointed at a physical device: an instrumented run wipes app data, and the phone holds the owner's real logs. Nothing in this task writes a serial, a device name or a path into a tracked file.

**Interfaces:**
- Consumes: everything from Tasks 1–9, plus `AppGraph.attachmentRootResolver` / `attachmentGrantCheck` (Task 7's test seams).
- Produces: the two instrumented suites, and `clearInstall`'s extra two lines.

```kotlin
// androidTest/.../attachments/SafTreeAttachmentStoreContractTest.kt
package com.loosecannon.notenfc.attachments

import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import com.loosecannon.notenfc.core.ports.ByteSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The same six claims `AttachmentStoreContractTest` makes about the JVM fake, made about the real
 * `DocumentFile` store — over `DocumentFile.fromFile` on an app-external directory, because an
 * instrumented test cannot drive the SAF picker. The list is duplicated on purpose: JVM test
 * fixtures cannot be shared with an `androidTest` variant, so the two suites are written to read
 * the same way, and a drift between them is meant to be visible in review.
 */
class SafTreeAttachmentStoreContractTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var root: File
    private lateinit var store: SafTreeAttachmentStore

    private val payload = ByteArray(200_000) { (it % 251).toByte() }

    @Before fun freshTree() {
        root = File(context.getExternalFilesDir(null), "attachments-contract").also {
            it.deleteRecursively()
            it.mkdirs()
        }
        store = SafTreeAttachmentStore(DocumentFile.fromFile(root), context.contentResolver)
    }

    @Test fun putThenOpenRoundTripsTheBytes() = runBlocking {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertArrayEquals(payload, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    @Test fun putReturnsTheShaAndSizeTheStoreItselfSaw() = runBlocking {
        val stored = store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertEquals(payload.size.toLong(), stored.sizeBytes)
        assertEquals(sha256Hex(payload), stored.sha256)
    }

    @Test fun existsAnswersForBothCases() = runBlocking {
        assertFalse(store.exists("assets/a1/att-1.pdf"))
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertTrue(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun openOfAnAbsentLocatorIsNull() = runBlocking {
        assertNull(store.open("assets/a1/nothing.pdf"))
    }

    @Test fun deleteOfAnAbsentLocatorIsSilent() = runBlocking {
        store.delete("assets/a1/nothing.pdf")
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        store.delete("assets/a1/att-1.pdf")
        assertFalse(store.exists("assets/a1/att-1.pdf"))
    }

    @Test fun aLocatorWithTwoDirectoryLevelsIsCreated() = runBlocking {
        store.put("events/e1/att-2.jpg", ByteSource { payload.inputStream() })
        assertTrue(File(root, "events/e1").isDirectory)
        assertTrue(store.exists("events/e1/att-2.jpg"))
    }

    /** The provider-renamed case: a locator still resolves by its `<id>.` prefix (spec §5.2). */
    @Test fun aDocumentTheProviderRenamedIsStillFoundByItsId() = runBlocking {
        store.put("assets/a1/att-1.pdf", ByteSource { payload.inputStream() })
        assertTrue(File(root, "assets/a1/att-1.pdf").renameTo(File(root, "assets/a1/att-1.pdf.pdf")))
        assertTrue(store.exists("assets/a1/att-1.pdf"))
        assertArrayEquals(payload, store.open("assets/a1/att-1.pdf")!!.use { it.readBytes() })
    }

    /** A failed write leaves nothing behind for a row to point at. */
    @Test fun aSourceThatThrowsMidCopyLeavesNoDocument() = runBlocking {
        val boom = runCatching {
            store.put("assets/a1/att-1.pdf", ByteSource { ThrowingStream(payload, after = 1024) })
        }
        assertTrue(boom.isFailure)
        assertFalse(store.exists("assets/a1/att-1.pdf"))
    }

    private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { b -> "%02x".format(b) }

    /** Reads [after] bytes and then fails, the way a revoked provider grant does. */
    private class ThrowingStream(bytes: ByteArray, private val after: Int) : java.io.InputStream() {
        private val source = bytes.inputStream()
        private var read = 0
        override fun read(): Int {
            if (read++ >= after) throw java.io.IOException("rigged mid-copy failure")
            return source.read()
        }
    }
}
```

```kotlin
// androidTest/.../ui/AttachmentsDeviceProofTest.kt
package com.loosecannon.notenfc.ui

/**
 * Phase 4A's device proof (spec §12), driven through the real screens on the **emulator**. Same
 * shape as `AssetModelDeviceProofTest`: an empty Compose rule, a destructive `@Before`, one test
 * per scenario, each cold-starting the screen it needs through the `notenfc://asset/<id>` deep
 * link a tag tap takes.
 *
 * What the suite deliberately does *not* claim:
 *
 * - **The SAF picker is never driven.** There is no way to tap a system file chooser reliably, and
 *   spec §5.3 provides the seam instead: `@Before` points `AppGraph.attachmentRootResolver` at a
 *   `DocumentFile.fromFile` tree under `getExternalFilesDir`, `attachmentGrantCheck` at `{ true }`,
 *   and writes `AppPrefs.attachmentTreeUri`. Everything downstream of the picker — the store, the
 *   rows, the thumbnails, the viewer intent, the export, the restore — is the production path.
 * - **Files arrive from a test-provided source, not from a document picker.** A scenario seeds a
 *   file through `graph.addAttachment.run(...)` with a `ByteSource` over a test asset, which is
 *   exactly what `AttachmentPickers` hands the use case. The picker's own mapping is covered by
 *   the §10 procedure, on real files, through the shipped UI.
 * - **`ACTION_VIEW` is asserted as an intent, not as a viewer.** The emulator has no PDF viewer,
 *   so the row's tap is proved by the "No app can open this file" line — which is the branch that
 *   the `<queries>` entry exists for — and the launch path is proved by `viewUri` being non-null.
 */
class AttachmentsDeviceProofTest {

    @get:Rule val rule = createEmptyComposeRule()

    @Before fun freshInstallWithATree() {
        clearInstall()
        useFileBackedTree()
    }

    // ---------------------------------------------------------------- scenario (a)
    /** With no folder configured the section says so and offers the way to Settings. */
    @Test fun withNoFolderTheDocumentsSectionPointsAtSettings() { /* clear the pref, open the asset */ }

    // ---------------------------------------------------------------- scenario (b)
    /** A file added from a test-provided source becomes a row, and its bytes are in the tree. */
    @Test fun anAddedFileIsARowAndAFileInTheTree() { /* "Documents · 1", the name, the size line */ }

    // ---------------------------------------------------------------- scenario (c)
    /** The overflow sheet renames and re-kinds, and the row redraws from the flow. */
    @Test fun theSheetRenamesAndReKindsTheRow() { /* overflow, Rename, chip "Manual", Save */ }

    // ---------------------------------------------------------------- scenario (d)
    /** Delete asks once, then the row and the file are both gone. */
    @Test fun deletingARowRemovesTheRowAndTheFile() { /* the plain confirm dialog, then assert both */ }

    // ---------------------------------------------------------------- scenario (e)
    /** An image row shows a thumbnail; a document row shows its kind glyph. */
    @Test fun anImageRowGetsAThumbnailAndADocumentRowGetsAGlyph() { /* the cache file exists for one, not the other */ }

    // ---------------------------------------------------------------- scenario (f)
    /** Export writes two stamped files into a test tree, through the Backup screen. */
    @Test fun exportingWritesTwoStampedFilesIntoThePickedFolder() { /* names match the stamped pattern */ }

    /** The real writer's invariant: a body that throws leaves no document behind. */
    @Test fun safWriterRemovesTheDocumentItCreatedWhenTheBodyThrows() = runTest {
        val dir = File(context.getExternalFilesDir(null), "set-${System.nanoTime()}").apply { mkdirs() }
        val writer = SafBackupSetWriter(context, context.contentResolver, DocumentFile.fromFile(dir))
        val failure = runCatching {
            writer.write("noteNFC-artifacts-20260916-000000.zip") { out ->
                out.write(ByteArray(4096))
                error("rigged mid-write failure")
            }
        }
        assertTrue(failure.isFailure)
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    // ---------------------------------------------------------------- scenario (g)
    /** Data-only restore is a first-class outcome: rows come back reading "Not on this device". */
    @Test fun restoringDataAloneLeavesTheRowsSayingNotOnThisDevice() { /* wipe, restore data, assert the line */ }

    // ---------------------------------------------------------------- scenario (h)
    /** Then the files archive puts the bytes back and the row stops saying it. */
    @Test fun restoringTheFilesArchiveBringsTheBytesBack() { /* the line is gone; the thumbnail returns */ }

    // ---------------------------------------------------------------- scenario (i)
    /** The files archive of another set is refused, naming both short ids. */
    @Test fun aFilesArchiveFromAnotherSetIsRefused() { /* the snackbar, and the store gained nothing */ }

    // ---------------------------------------------------------------- scenario (j)
    /** An event carries its own files, and deleting the entry takes them with it. */
    @Test fun anEventsFilesAreItsOwnAndGoWhenTheEntryDoes() { /* event detail DOCUMENTS, then delete the entry */ }
}

// -------------------------------------------------------------------- fixtures

/**
 * Points the graph's attachment seams at an ordinary directory (spec §12). The picker is the one
 * thing an instrumented test cannot drive; everything it would have produced is produced here.
 */
private fun useFileBackedTree(): File {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = File(context.getExternalFilesDir(null), "attachments-proof").also {
        it.deleteRecursively()
        it.mkdirs()
    }
    val graph = app.graph
    graph.attachmentRootResolver = { _ ->
        DocumentTreeRoot(DocumentFile.fromFile(root), context.contentResolver)
    }
    graph.attachmentGrantCheck = { true }
    graph.prefs.attachmentTreeUri = "file://" + root.absolutePath
    return root
}

/** A folder for the backup set, picked the same way: a `SafBackupSetWriter` over a `fromFile` tree. */
private fun backupTree(): File = /* getExternalFilesDir(null)/backup-proof, wiped */

/** Adds a file the way `AttachmentPickers` would: display name, mime, size, a byte source. */
private fun addFile(
    owner: AttachmentOwner,
    name: String,
    mime: String,
    bytes: ByteArray,
): String = runBlocking {
    val result = app.graph.addAttachment.run(
        owner,
        AddAttachmentCommand(displayName = name, mimeType = mime, sizeBytes = bytes.size.toLong()),
        ByteSource { bytes.inputStream() },
    )
    (result as AttachmentResult.Ok).value.id.value
}

/** A tiny valid JPEG, encoded in-process so no binary test asset is committed. */
private fun jpegBytes(): ByteArray = ByteArrayOutputStream().also { out ->
    Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888)
        .compress(Bitmap.CompressFormat.JPEG, 90, out)
}.toByteArray()
```

`clearInstall` in `AppSmokeTest.kt` gains the attachment table inside its existing `uow.write` block, before `events.deleteAll()`:

```kotlin
graph.attachments.deleteAll()
```

and, after the preference wipe, a line that also clears the thumbnail cache so a scenario cannot pass on a stale image:

```kotlin
File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "thumbs").deleteRecursively()
```

- [ ] **Step 1: Start the emulator and pin it**

```bash
adb devices                      # confirm exactly one emulator-* target is listed
export ANDROID_SERIAL="$(adb devices | awk '/^emulator-/ {print $1; exit}')"
echo "$ANDROID_SERIAL"            # must be non-empty, and must be the emulator
```

Expected: `adb devices` lists an `emulator-*` line and no physical device. **If a phone is attached, stop and unplug it** — the suite wipes app data.

- [ ] **Step 2: Write the store contract suite and run it**

Run: `./gradlew :app:connectedDebugAndroidTest --tests 'com.loosecannon.notenfc.attachments.SafTreeAttachmentStoreContractTest'`
Expected: PASS — eight tests, the same six claims as the JVM contract plus the provider-rename and the failed-write rows.

- [ ] **Step 3: Write the device-proof suite, filling in every scenario body**

Each scenario follows `AssetModelDeviceProofTest`'s idiom exactly: `openAsset(id).use { rule.awaitText(...) ; rule.onNodeWithText(...).performScrollTo().assertIsDisplayed() }`, with `rule.awaitGone(...)` where something must disappear and `rule.onAllNodesWithText(...).assertCountEquals(0)` where something must never have been there. The overflow is reached with `rule.onAllNodesWithContentDescription("More").onFirst().performClick()` inside the section, and the Backup screen through Settings, as `NavigationSmokeTest` reaches it.

- [ ] **Step 4: Run the full instrumented suite on the emulator**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: PASS — the new suites plus every existing smoke and device-proof suite (`AppSmokeTest`, `NavigationSmokeTest`, `JournalSmokeTest`, `ComponentsSmokeTest`, `EditorsDeviceProofTest`, `JournalDeviceProofTest`, `AssetModelDeviceProofTest`), all still green with the DOCUMENTS section on two screens.

- [ ] **Step 5: Commit**

```bash
git add app/src/androidTest
git commit -m "attachments device proof on the emulator"
```

---

### Task 11: The SPA import procedure, the evidence file, and the release gate

**Files:**
- Modify: `app/build.gradle.kts` (`versionCode = 6`, `versionName = "2.4"`)
- Create: `docs/design/phase-4a-evidence.md`
- Modify: `README.md` (one feature line), `docs/design/README.md` (one row)
- Modify: `docs/design/04-data-model-and-storage.md` §11 and `docs/design/07-phasing.md`'s Phase 4 block per spec §11 (the deviations the spec records: `formatVersion` keeps its name, no SQL `CHECK`, no `LOCAL` provider, two-step restore, store change blocked once rows exist)

**Interfaces:**
- Consumes: the shipped 2.4 debug build.
- Produces: the evidence file, the version bump, and a phone that holds the eight SPA files on the Hot tub asset.

- [ ] **Step 1: Bump the version**

`app/build.gradle.kts`:

```kotlin
versionCode = 6
versionName = "2.4"
```

- [ ] **Step 2: Run the final gate**

Run: `./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin`
Expected: PASS, both APKs built.

Run: `ANDROID_SERIAL=<the emulator> ./gradlew :app:connectedDebugAndroidTest`
Expected: PASS, on the emulator only.

- [ ] **Step 3: Privacy and taxonomy greps**

```bash
git grep -nIE 'GonzRon|/home/|/Users/|emulator-[0-9]+|[0-9A-F]{8}:[0-9A-F]{2}' \
  -- . ':!docs/design/phase-4a-evidence.md' ':!docs/superpowers/plans' | cat
git grep -nI 'LOCAL' -- 'core/src/main/kotlin/**/Attachment.kt' | cat
```

Expected: the first prints nothing but the project URL's `GonzRon` in `SettingsScreen.kt` and `README.md`, which are deliberate; the second prints nothing at all — `StorageProvider` has no `LOCAL` member (spec §11.8). Record both outcomes in the evidence file's §9.

- [ ] **Step 4: Install 2.4 over the real 2B-2 install (the phone, no instrumented run)**

The phone gets the release-shaped debug build and nothing else; no instrumented suite is ever run against it.

```bash
# the phone is the only device attached for this step; the emulator is stopped first
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then, by hand on the phone: open the app, confirm it starts and the Assets list is intact — that is the v4 to v5 migration having run in place on real data. Confirm the journal and the tags are still there.

- [ ] **Step 5: Choose the attachment folder, once**

On the phone: Settings → Attachment storage → Choose folder → pick the Syncthing-replicated folder on primary storage. Confirm the Folder line names it and the Provider line names an authority.

- [ ] **Step 6: Push the eight SPA files**

They are already extracted and renamed to their link text from the Joplin export (spec §10.1). Referring to the extraction directory as `~/spa-import/`:

```bash
adb shell mkdir -p /sdcard/Download/spa
adb push "~/spa-import/1-2-3 Easy Installation Guide.pdf" /sdcard/Download/spa/
adb push "~/spa-import/SPA Water Chemistry.pdf" /sdcard/Download/spa/
adb push "~/spa-import/SPA Water Chemistry.docx" /sdcard/Download/spa/
adb push "~/spa-import/BP Troubleshooting Manual 60Hz.pdf" /sdcard/Download/spa/
adb push "~/spa-import/5169797_Replacement_Cartridge_for_Bullfrog_at-ease.zip" /sdcard/Download/spa/
adb push "~/spa-import/4649576_Bullfrog_Spa_Headrest_Clip-_2016_to_present_Version.zip" /sdcard/Download/spa/
adb push "~/spa-import/4226467_Bullfrog_Spa_Headrest_Clip.zip" /sdcard/Download/spa/
adb push "~/spa-import/2018-Bullfrog-Owners-Manual-rev3.1.8-web.pdf" /sdcard/Download/spa/
adb shell ls -l /sdcard/Download/spa
```

Expected: eight files listed, with the names above.

- [ ] **Step 7: Import them through the product's own picker**

On the phone, **one action by the owner**: open the Hot tub asset → DOCUMENTS → `Add file` → in the picker, navigate to `Download/spa` and multi-select all eight → confirm.

Expected: the progress line counts up to "Adding 8 of 8…", then DOCUMENTS reads "Documents · 8". The five PDFs land as `DOCUMENT`, the `.docx` and the three `.zip`s as `OTHER` (spec §10.3). Re-kinding the three manuals to `MANUAL` from the sheet is optional and the owner's call.

This is the picker path the product ships, so it is also 4A's real-data proof. No instrumented code runs on the phone.

- [ ] **Step 8: Export a set, confirm two files, then clean up**

On the phone: Settings → Backup → `Export backup set` → pick a folder. Confirm the folder then holds `noteNFC-data-<stamp>.zip` and `noteNFC-artifacts-<stamp>.zip` with the same stamp, and that the artifacts file is roughly the size of the eight documents. Confirm the attachment folder holds `assets/<the asset>/` with eight files in it, and that Syncthing has picked them up.

```bash
adb shell rm -rf /sdcard/Download/spa
```

- [ ] **Step 9: Write the evidence file**

`docs/design/phase-4a-evidence.md`, the nine standard sections in the shape of `docs/design/phase-2b2-evidence.md`:

1. **Exit criteria (spec §12) → evidence** — one row per bullet of spec §12, each naming the test or the manual step that proves it.
2. **What shipped (by commit)** — the eleven commits of this plan, one line each.
3. **Tests** — `:core` count before and after, `:app` JVM count before and after, the instrumented suites, and the two contract suites named as a pair.
4. **Device proof** — the emulator rows (scenarios a–j and the store contract) and the phone rows (v4→v5 in place on real data, the §10 import, the two-file export, Syncthing picking them up). The phone rows say "the owner's phone" and name no model and no serial.
5. **Status of the device proof** — what is proven, and what is only proven on the emulator (the SAF picker itself, and `ACTION_VIEW` reaching a real viewer).
6. **Rulings made during execution** — including the three this plan resolved: `AttachmentResult` in place of a two-parameter `Result`; `AddAttachmentCommand.sizeBytes`; `AttachmentRoot` as the JVM-testable seam under spec §5.3's `rootResolver`.
7. **Deferred** — 4B's list from spec §2, verbatim.
8. **What 4A changed for Phase 3** — the attachment store is available to a completion flow; `DeleteEvent` now has byte cleanup; backup is a set, which 3R's remembered destination must write two of.
9. **Final gate** — the exact commands from Steps 2 and 3 and their outcomes.

- [ ] **Step 10: Commit**

```bash
git add app/build.gradle.kts docs README.md
git commit -m "phase 4a evidence, spa import, versionCode 6"
```

---

## Self-review

**1. Spec coverage.**

| Spec | Task |
|---|---|
| §1 goal, §2 scope | the plan as a whole; §2's "not in 4A" list appears only in Task 11's evidence §7 |
| §3 global constraints | Global Constraints, and the per-task gates |
| §4 model, locator, kinds, `isImage`, `AttachmentProblem`, 256 MiB | Task 1 |
| §5.1 ports | Task 2 |
| §5.2 `SafTreeAttachmentStore`, `viewUri`, contract test | Task 7 (store), Task 10 (contract on the emulator), Task 2 (the JVM twin) |
| §5.3 `AttachmentStorage`, the tree preference, the Settings section | Task 7 (resolver + prefs), Task 9 (the section and the same-folder repair) |
| §6 `AddAttachment`, `UpdateAttachment`, `DeleteAttachment`, `DeleteAsset`, `DeleteEvent` | Task 3 |
| §6 `ExportBackupSet`, `RestoreArtifacts`, `ImportBackupReplace`, `AttachmentRepository` | Task 2 (repository), Task 4 (import), Task 5 (export + restore) |
| §7.1 data format 5 | Task 4 |
| §7.2 artifacts format 1 | Task 5 |
| §7.3 files and the Backup screen | Task 9 |
| §8.1 DOCUMENTS section, rows, sheet, actions, progress | Task 8 |
| §8.2 thumbnails | Task 7 (the class), Task 8 (the per-row cache), Task 10 (the decode, on the emulator) |
| §8.3 Settings and Backup | Task 9 |
| §8.4 nothing else changes, no new `Route` | Task 8 (it reuses `Route.Settings`) |
| §9.1 Room v5 and migration 4→5 | Task 6 |
| §9.2 preferences | Task 7 |
| §9.3 `FileProvider`, `file_paths.xml`, `<queries>` | Task 7 |
| §10 the SPA import procedure | Task 11, Steps 4–8 |
| §11 rulings and deviations | honoured throughout; recorded in docs in Task 11 |
| §12 proof | every task's own tests, plus Task 10 and Task 11 |

No gap found.

**2. Placeholder scan.** Every code step carries real Kotlin, every test is named with its inputs and its expected outcome, and every run command is exact. Three places carry a deliberate, bounded elision rather than content: `BackupCodecTest`'s zip-tampering helper (Task 5 — it says to copy the existing `unzip`/`rezip`/`bumpFormatVersion` trio from the same file), `Migration4To5Test.seedPhase2b2` (Task 6 — it says to reuse `Migration3To4Test.seedPhase2b1`'s five `INSERT`s against `4.json`'s columns), and the bodies of the ten device-proof scenarios (Task 10 — each has its assertion named, and Step 3 states the exact idiom to write them in). No "TBD", no "add error handling", no "similar to Task N".

**3. Type consistency.** `AttachmentId`/`Attachment`/`AttachmentOwner`/`AttachmentKind`/`AttachmentMode`/`StorageProvider`/`AttachmentProblem`/`AttachmentLocator`/`MimeTypes`/`AttachmentKinds` are defined once, in Task 1, and every later task names them unchanged. `AttachmentStore`/`ByteSource`/`StoredBytes`/`StoreState`/`AttachmentStorage`/`AttachmentRepository` are defined once, in Task 2; `SafAttachmentStorage` (Task 7) implements `AttachmentStorage` and adds only `viewUri`. `AttachmentResult`/`AddAttachmentCommand`/`UpdateAttachmentCommand` are defined once, in Task 3, and used by Tasks 8 and 10. `BackupCodec.FORMAT_VERSION = 5` (Task 4) and `AppGraph.SCHEMA_VERSION = 5` (Task 6) stay two separate constants, as they have since 1A, and `ArtifactsCodec.ARTIFACT_FORMAT_VERSION = 1` is a third. `ArtifactsPlan`/`ArtifactsPlanEntry`/`ArtifactEntry`/`ArtifactsManifest`/`ArtifactsWritten`/`BackupSet`/`ArtifactsReport` are defined once, in Task 5, and consumed by Task 9. `ImportReport` grows exactly two fields in Task 4 (`attachments`, `lastRestoredBackupSetId`) and both are read in Task 9. `BackupIO.openStream()` is added once, in Task 9. `DeleteAsset(assets, events, attachments, storage, uow)` and `DeleteEvent(events, attachments, storage, uow)` have one signature each from Task 3 on, and Tasks 6 and 7 construct them with it.

**Ambiguities resolved (also recorded for the evidence file's §6).**

1. **Spec §6 writes `Result<Attachment, AttachmentProblem>`**, which `kotlin.Result` cannot express (one type parameter). Resolved as a two-member sealed `AttachmentResult<out T>` (`Ok`, `Refused`) in `:core.usecase`, following `OpenLink.Outcome`'s existing shape. Every use-case signature the spec names is otherwise verbatim.
2. **Spec §6's `AddAttachmentCommand` field list omits a size**, but §8.1 says the picker passes one and §11.11 requires the guard to refuse "before any byte is copied". Resolved as `sizeBytes: Long?` — checked before `put` when the provider reported one, and checked again against the `StoredBytes` the store returns when it did not (a camera capture), deleting the bytes if it went over.
3. **Spec §5.3's `rootResolver: (treeUri) -> DocumentFile?`** cannot be faked in a plain JVM unit test, which §12 nevertheless asks for ("`AttachmentStorage.state()` over a fake resolver"). Resolved by putting the seam one level up: `rootResolver: (String) -> AttachmentRoot?`, with `DocumentTreeRoot` as production's `DocumentFile` implementation and the instrumented suite substituting one over `DocumentFile.fromFile`. `grantCheck` is unchanged.
4. **Spec §7.2 wants the artifacts manifest first *and* per-entry sha256 verification.** The manifest's digests come from the rows (the store hashed them at `put` time), so no streaming is needed to build it; `write` then makes a first pass to compute each entry's CRC — which STORED demands up front — and drops any row whose bytes are missing or no longer hash to what the row claims, reporting both. Nothing is buffered in memory, and a known-bad entry never reaches the archive.
5. **Spec §6's `DeleteAsset`/`DeleteEvent` column says "+ `attachments`, `store`".** They take `AttachmentStorage`, not `AttachmentStore`, because a store can be absent or `AccessLost` and neither delete may fail for that reason; the parameter is named `storage` and its KDoc says so.
6. **`DeleteAttachment` returns `Unit`, not a refusal.** Spec §4's problem list is closed and has no member for "no such attachment", and the store's own contract already says an absent target is not an error; deleting a row that is not there is therefore a silent no-op. `UpdateAttachment` reuses `OwnerMissing` for a row that has gone, and its KDoc says that is what the member means there.
