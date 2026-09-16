# Phase 4A — Attachments: managed SAF-tree store and the backup set split: design

Date 2026-09-15. Authority chain: owner's ruling of 2026-09-15 (modified 4A) → D7 "Phase 4"
block → D4 §11 → D3 §11 → spike S5 (`docs/design/spikes/S5-saf-tree-provider.md`). Where this
spec and an older design doc disagree, this spec wins and §11 records the deviation.

## 1. Goal

Attach photos and documents to assets and events, keep the bytes in a folder the owner chose
(a SAF tree, never app-private storage by default), open them with the system viewer, show image
thumbnails, and back everything up as a **set of two archives** so a database-only restore stays
possible. Then bring the owner's eight remaining Joplin files (the SPA procedures) onto the
Hot tub asset through the product's own picker.

## 2. Scope

In 4A:

1. `Attachment` model, `AttachmentStore` port, provider-relative locators.
2. One store implementation: a SAF tree chosen once in Settings → Attachment storage, with a
   persistable read+write grant.
3. Attach from the document picker (multi-select) and the camera, to an asset or to an event.
4. Open with the system viewer; image thumbnails in the list; DOCUMENTS section on asset detail
   and on event detail; edit name / kind / captured-on / notes; delete with confirmation.
5. Backup format 5 (attachment metadata in the data archive) and artifacts format 1 (bytes in a
   second archive), both under one `backupSetId`; export writes both, restore is two steps.
6. Room v5, migration 4→5, `AppGraph` wiring, version 2.4 (versionCode 6).
7. The SPA import procedure (§10), which uses the product path and no seed code.

Not in 4A (4B after 3R): REFERENCE-mode attachments (`SAF_DOCUMENT` pointers), changing the
store folder once attachments exist (migration UX), an orphan sweep / storage health job,
attaching from the event entry form, attachment search, EXIF capture dates, video.

## 3. Global constraints

- Toolchain unchanged: AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, Room 3.0.3,
  Navigation 3 1.1.7, kotlinx-serialization 1.9.0, minSdk 26, compileSdk 37. New dependency:
  `androidx.documentfile:documentfile:1.1.0` only. No Coil, no CameraX, no WorkManager.
- `:core` stays JVM-only: no `android.*` import. `java.util.zip`, `java.security` and
  `java.io` streams are allowed (they already are, in `BackupCodec`).
- Bytes are never in Room (D4 §11). Rows hold metadata; the store holds bytes.
- The managed store is the user's SAF tree. If none is configured, attaching is refused with a
  path to Settings; nothing is written to app-private storage as a fallback.
- Every new use case runs inside `UnitOfWork.write`; `FakeUnitOfWork` is non-re-entrant, so
  use cases never call another use case's `run` inside their own transaction.
- IDs are UUID strings from `IdGenerator`; timestamps from `Clock`; nothing calls
  `System.currentTimeMillis()` outside `AppGraph`.
- Privacy rule unchanged: no owner data in tracked files.
- Instrumented suites never run on the owner's phone (they wipe app data); device proof runs
  on the emulator, and the phone gets the release-shaped debug build for real use only.

## 4. Model (`:core.model.Attachment`)

```kotlin
@JvmInline value class AttachmentId(val value: String)

enum class AttachmentKind { PHOTO, LABEL_PHOTO, RECEIPT, MANUAL, WARRANTY, DOCUMENT, OTHER }
enum class AttachmentMode { MANAGED, REFERENCE }          // REFERENCE unused until 4B
enum class StorageProvider { SAF_TREE, SAF_DOCUMENT }      // LOCAL deliberately absent

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
```

Single owners (pure, tested):

- `AttachmentLocator.forOwner(owner, id, displayName, mimeType): String` builds
  `assets/<asset-id>/<attachment-id>.<ext>` or `events/<event-id>/<attachment-id>.<ext>`.
  `<ext>` is the lowercase extension of `displayName` if it is 1–8 `[a-z0-9]` chars, else the
  extension `MimeTypes.extensionFor(mimeType)` knows (`image/jpeg→jpg`, `image/png→png`,
  `application/pdf→pdf`, `application/zip→zip`, `text/plain→txt`, the Office `docx/xlsx`
  types), else `bin`. The locator never contains the display name (privacy, renames).
- `AttachmentKinds.inferFrom(mimeType, fromCamera): AttachmentKind` — camera → `PHOTO`;
  `image/*` → `PHOTO`; `application/pdf` → `DOCUMENT`; everything else → `OTHER`. A default,
  never a constraint; the user picks any kind.
- `Attachment.isImage` = `mimeType.startsWith("image/")` — the only thing the thumbnail path
  asks.
- `AttachmentProblem` sealed: `BlankName`, `NoStore`, `StoreUnavailable`, `TooLarge(limit)`,
  `OwnerMissing`, `Unchanged`. Size limit 256 MiB per file (a guard against a mistaken pick,
  not a product limit).

## 5. The store boundary

### 5.1 Port (`:core.ports.AttachmentStore`)

```kotlin
interface AttachmentStore {
    /** Streams [source] into [locator] (creating parents), returns the bytes' sha256 and size. */
    suspend fun put(locator: String, source: ByteSource): StoredBytes
    suspend fun open(locator: String): InputStream?        // null when absent
    suspend fun exists(locator: String): Boolean
    suspend fun delete(locator: String)                    // absent is not an error
}
fun interface ByteSource { fun open(): InputStream }
data class StoredBytes(val sha256: String, val sizeBytes: Long)

sealed interface StoreState {
    data object NotConfigured : StoreState
    data class Ready(val displayName: String, val authority: String) : StoreState
    data class AccessLost(val displayName: String) : StoreState
}
interface AttachmentStorage {           // app-level resolver, owns the tree URI preference
    fun state(): StoreState
    fun store(): AttachmentStore?       // null unless Ready
}
```

Hashing is done once, inside `put`, while streaming; the caller gets back what the store saw,
and the row records that. `ImportBackupReplace`'s artifacts step verifies the archive's hash
against the row before writing.

### 5.2 `SafTreeAttachmentStore` (app, over `DocumentFile`)

- Constructed from a `DocumentFile` tree root and a `ContentResolver`. `assets/` and `events/`
  and the per-owner directories are created on demand by walking segments with `findFile` then
  `createDirectory`. Files are created with `createFile(mimeType, "<id>.<ext>")`. Some providers append
  their own extension when the mime type disagrees with the name; the store therefore records
  nothing about the created document's name, keeps the locator it asked for, and resolves a
  locator by `findFile` per segment, falling back to the first child whose name starts with
  `<id>.` — the id is unique, so the match is unambiguous.
- `put` streams through a `DigestInputStream`, 64 KiB buffer; on any failure it deletes the
  partial document and rethrows as `StoreIoException`.
- `viewUri(locator): Uri?` — the document's `content://` URI, for `ACTION_VIEW` with
  `FLAG_GRANT_READ_URI_PERMISSION`. App layer only; `:core` never sees a `Uri`.
- Contract test (JVM-free is impossible; instrumented on the emulator) runs the same suite over
  `DocumentFile.fromFile(context.getExternalFilesDir(null))`: put/open/exists/delete
  round-trip, sha256 equality with a locally computed digest, `open` of an absent locator is
  null, `delete` of an absent locator is silent, a locator with two directory levels is created.
  A JVM `InMemoryAttachmentStore` fake in `:core` test fixtures runs the same contract so the
  use-case tests are pure.

### 5.3 `AttachmentStorage` and the tree preference

- `AppPrefs.attachmentTreeUri: String?` (SharedPreferences, as today's prefs).
- `AttachmentStorage` takes a `rootResolver: (treeUri: String) -> DocumentFile?` (production:
  `DocumentFile.fromTreeUri`; the device-proof test substitutes `DocumentFile.fromFile` on an
  app-external directory) and a `grantCheck: (String) -> Boolean` (production: the resolver's
  persisted permissions; tests: always true).
- `state()`: no pref → `NotConfigured`; pref set but `contentResolver.persistedUriPermissions`
  lacks a read+write entry for it, or `DocumentFile.fromTreeUri(...)?.canWrite() != true` →
  `AccessLost(name)`; else `Ready(name, authority)`.
- Settings → Attachment storage: `Choose folder` (`OpenDocumentTree`, then
  `takePersistableUriPermission(READ or WRITE)`), a `LabelValue` for the folder's display name
  and the provider authority, and a `QuietLine` "Files are written as ordinary documents in
  this folder; a sync tool such as Syncthing owns any off-device copy." When at least one
  attachment row exists the button is disabled with `QuietLine("Moving attachments to another
  folder arrives in a later release")` (4B), except in `AccessLost`, where re-choosing the
  *same* folder is the repair: allowed, and refused with a snackbar if the picked tree's
  authority + tree document id differ from the stored ones.

## 6. Use cases (`:core.usecase`)

| Use case | Signature | Rules |
|---|---|---|
| `AddAttachment` | `run(owner, cmd: AddAttachmentCommand, source: ByteSource): Result<Attachment, AttachmentProblem>` | owner exists; store present; name non-blank (default = picked display name); `put` first, then the row inside `uow.write`; if the write fails the bytes are deleted. `cmd` = displayName, mimeType, kind, capturedOn, notes, fromCamera. |
| `UpdateAttachment` | `run(id, cmd: UpdateAttachmentCommand)` | name/kind/capturedOn/notes; `Unchanged` when nothing differs; touches `updatedAt`. |
| `DeleteAttachment` | `run(id)` | row inside `uow.write`, then `store.delete` best effort (a failed byte delete is logged, not surfaced: the row is gone, the orphan is 4B's sweep). |
| `DeleteAsset` (existing) | + `attachments`, `store` | collects the asset's *and its events'* attachment locators inside the transaction, deletes rows via cascade, then deletes bytes after commit, best effort. |
| `DeleteEvent` (existing) | + `attachments`, `store` | same for the event's attachments. |
| `ExportBackupSet` | `run(): BackupSet` (replaces `ExportBackup.run`) | one `uow.read` snapshot; data archive bytes, plus an `ArtifactsPlan` (list of attachment id → locator → sha256 → size) the app streams into the second archive. |
| `ImportBackupReplace` (existing) | `run(bytes): ImportReport` | format 5 reads `attachments`; rows are written after their owners; `ImportReport.attachments` count; old managed bytes are deleted after commit best effort; `lastRestoredBackupSetId` returned in the report. |
| `RestoreArtifacts` | `run(archive: InputStream, expectedSetId: String?): ArtifactsReport` | manifest `backupSetId` must equal the last restored data set (else `SetMismatch`); each entry must match a row by id and sha256 (else counted `skipped`); bytes go to the row's locator; report `restored / skipped / missingRows`. |

`AttachmentRepository` (port): `upsert`, `get`, `forOwner(owner)`, `forAsset(assetId)` (asset's
own rows only), `all`, `delete(id)`, `deleteAll()`, `count()`, `observeForOwner(owner): Flow`.

## 7. Backup set

### 7.1 Data archive (format 5, `BackupCodec.FORMAT_VERSION = 5`)

- `data.json` gains `attachments: List<AttachmentDto>` (all §4 fields, owner as
  `assetId?`/`eventId?`, exactly one non-null). Format ≤4 archives decode with an empty list.
- `manifest.json` gains `backupSetId: String` (UUID), `artifactFormatVersion: Int = 1`,
  `artifactCount: Int`, `artifactBytes: Long`. `formatVersion` keeps its name and becomes 5 —
  D7's `dataFormatVersion` is this field; renaming it would break the branch-on-version reader.
- Reader validation (before any write, like format 4): every attachment's owner exists in the
  same archive; `sha256` is 64 hex chars; `sizeBytes ≥ 0`; locator matches
  `AttachmentLocator` shape for its owner; ids unique.

### 7.2 Artifacts archive (artifact format 1, `ArtifactsCodec`)

- Entries: `manifest.json` first, then `artifacts/<attachment-id>.<ext>` per row, `STORED` for
  already-compressed types (`image/jpeg`, `image/png`, `application/pdf`, `application/zip`)
  and `DEFLATED` otherwise. Streamed from the store, never materialised.
- `manifest.json`: `{ artifactFormatVersion: 1, dataFormatVersion: 5, backupSetId, createdAt,
  entries: [{ attachmentId, entryName, sha256, sizeBytes, mimeType }] }`.
- Reader: refuses `artifactFormatVersion > 1`; verifies each entry's streamed sha256 against the
  manifest **and** against the row; a mismatch skips that entry and is reported, it never
  writes partial bytes (the store's `put` deletes on failure).
- Rows whose bytes are absent from the archive stay rows; the DOCUMENTS section shows them as
  "Not on this device".

### 7.3 Files and the Backup screen

- Export: `Export backup set` → `OpenDocumentTree` (one-off, not persisted) → writes
  `noteNFC-data-<stamp>.zip` and `noteNFC-artifacts-<stamp>.zip` into that folder,
  `<stamp>` = `yyyyMMdd-HHmmss` local. `lastBackupAt` is set only after both writes succeed;
  a failed artifacts write deletes the data file too and reports. With zero attachments the
  artifacts archive is still written (manifest only) so a set is always two files.
- Restore data: `OpenDocument` on the data zip, the existing typed confirmation, then the
  report adds "N attachments listed; restore the artifacts file to get their contents".
- Restore artifacts: `OpenDocument` on the artifacts zip (enabled always; refuses on set
  mismatch with the two ids shown short). Requires the store to be `Ready`.
- The 1C export (single `CreateDocument`) is removed; a format-4 file still imports.

## 8. Screens

### 8.1 DOCUMENTS section (asset detail, event detail)

- Placed after LINKS on the asset; after the readings on the event. Header count in the
  section title as elsewhere ("Documents · 8").
- Row: 56 dp leading thumbnail for images (else a kind glyph from `NoteNfcIcons`), display
  name (one line, ellipsised), a quiet line `kind · size · captured-on` (size in KB/MB, one
  decimal), trailing overflow. Tap opens the system viewer (`ACTION_VIEW`, `viewUri`,
  grant-read flag); no viewer → snackbar "No app can open this file". Row for missing bytes:
  glyph dimmed, quiet line "Not on this device", tap shows the same snackbar text.
- Overflow → bottom sheet: Rename (text), Kind (chips, all seven), Captured on (date field as on
  event entry), Notes, Delete (plain confirm dialog: "Delete <name>? The file is removed from
  your attachment folder."). Save via `UpdateAttachment`.
- Section actions: `Add file` (`OpenMultipleDocuments`, `*/*`; each picked URI → `AddAttachment`
  with resolver display name, mime, size; capturedOn defaults to today) and `Take photo`
  (`TakePicture` into a `FileProvider` cache URI `camera/<uuid>.jpg`; on success →
  `AddAttachment(fromCamera = true)`, then the temp file is deleted either way). With the store
  `NotConfigured`/`AccessLost` both actions instead show a `StatusBlock` "Attachment storage
  not set up — Choose a folder in Settings" with a button that pushes `Route.Settings`.
- Adding several files runs sequentially with a progress line "Adding 3 of 8…"; a failure
  reports which file and continues with the rest.

### 8.2 Thumbnails (`Thumbnails`, app)

- Decoded from `store.open(locator)` with `BitmapFactory` bounds + `inSampleSize` to ≤ 256 px on
  the long edge, encoded JPEG 80 into `cacheDir/thumbs/<attachment-id>-<sha256-prefix-8>.jpg`;
  cache hit is a file read. Work on `Dispatchers.IO`, one at a time per screen, results in a
  `mutableStateMap` in the ViewModel; failures fall back to the glyph. The cache is the OS
  cache dir and may vanish; nothing depends on it.

### 8.3 Settings → Attachment storage (see §5.3) and the Backup screen (see §7.3).

### 8.4 Nothing changes on Dashboard, Assets list, entry form, or navigation. No new `Route`.

## 9. Data

### 9.1 Room v5

`attachment` per D4 §11: `id` PK, `asset_id` FK → asset(id) CASCADE nullable, `event_id` FK →
asset_event(id) CASCADE nullable, `kind`, `mode`, `display_name`, `mime_type`, `size_bytes`,
`sha256`, `storage_provider`, `storage_locator`, `captured_on`, `notes`, `created_at`,
`updated_at`. Indexes: `asset_id`, `event_id`, unique `(storage_provider, storage_locator)`.
No SQL `CHECK` (Room's schema hash does not model it); the exactly-one-owner invariant lives in
the entity mapper (`require`) and in the DTO reader. Migration 4→5 is a plain `CREATE TABLE` +
indexes; exported schema `5.json`; `AppGraph.SCHEMA_VERSION = 5`; the JVM migration test opens a
v4 database built from `4.json` and migrates.

`AssetDao.deleteAllInOrder` is unaffected (CASCADE removes rows); `DeleteAsset` reads the
locators before the cascade runs.

### 9.2 Preferences

`attachmentTreeUri: String?`, `lastRestoredBackupSetId: String?`. Both non-observable, as
today's prefs.

### 9.3 Manifest / resources

`FileProvider` (`${applicationId}.files`, `cache-path name="camera" path="camera/"`),
`file_paths.xml`; no new permissions (`TakePicture` and SAF need none); `<queries>` gains
`ACTION_VIEW` for `content://*` with `*/*` so `resolveActivity` can answer "no viewer".

## 10. Importing the SPA files (procedure, not code)

The eight files live in the Joplin export beside the note. The note body is only a title and
the eight links, so there is no prose to carry over. Steps, in order:

1. Extract the JEX (already done in this session's scratchpad) and rename each resource to its
   link text: `1-2-3 Easy Installation Guide.pdf`, `SPA Water Chemistry.pdf`,
   `SPA Water Chemistry.docx`, `BP Troubleshooting Manual 60Hz.pdf`,
   `5169797_Replacement_Cartridge_for_Bullfrog_at-ease.zip`,
   `4649576_Bullfrog_Spa_Headrest_Clip-_2016_to_present_Version.zip`,
   `4226467_Bullfrog_Spa_Headrest_Clip.zip`, `2018-Bullfrog-Owners-Manual-rev3.1.8-web.pdf`.
2. `adb push` them to the phone's `Download/spa/`.
3. The owner chooses the attachment folder in Settings once, opens Hot tub → Documents →
   `Add file`, multi-selects the eight files. Kinds land as `DOCUMENT` (PDF) / `OTHER` (zip,
   docx); the owner may re-kind the three manuals as `MANUAL` from the sheet, or not.
4. Export a backup set afterwards; delete `Download/spa/`.

This is the picker path the product ships, so it is also 4A's real-data proof. No instrumented
code runs on the phone.

## 11. Rulings and deviations recorded

1. **Google Drive** was never on the phone (S5); the owner's folder is a Syncthing-replicated
   folder on primary storage. Proton Drive is a later, optional pick with no design impact.
2. **Backup export picks a folder per export** (`OpenDocumentTree`, no persisted grant). 3R
   turns this into a remembered destination; 4A does not pre-build it.
3. **Restore is two steps** (data, then artifacts) because `OpenDocument` cannot see a sibling
   file. A set id ties them; data-only restore is a first-class outcome, per the owner's ruling.
4. **`formatVersion` keeps its name** in the data manifest (D7 said `dataFormatVersion`); the
   artifacts manifest uses `dataFormatVersion` for the cross-reference.
5. **No SQL `CHECK`** for exactly-one owner; enforced in the mapper and the backup reader.
6. **Event attachments are added from event detail**, not from the entry form (4B may add a
   camera button to the form once Phase 3 completion flows exist).
7. **Attachment delete is a plain confirmation**, not typed: it removes one file, and the
   attachment folder is the owner's ordinary folder (recoverable via Syncthing versioning if
   they enabled it).
8. **No `LOCAL` store**, by the owner's ruling; the enum value is absent, not reserved.
9. **Thumbnails without Coil**: eight-to-fifty images per asset do not justify a dependency.
10. **`AttachmentStore.put` owns hashing** so the row can never disagree with the bytes.
11. **Size guard 256 MiB** per attachment, refused before any byte is copied.
12. **Store change is blocked once rows exist** (except the same-folder repair); the migration
    UX is 4B by the owner's ruling.
13. **The SPA import is a procedure** through the shipped picker, not seed code (instrumented
    runs would wipe the phone).

## 12. Proof

- `:core` JVM: locator and extension rules; kind inference; `AddAttachment` (store first, row
  second, bytes deleted when the row write fails; refusals for blank name, missing owner, size
  guard, no store); `UpdateAttachment` incl. `Unchanged`; `DeleteAttachment`; `DeleteAsset` and
  `DeleteEvent` remove bytes of the asset's own and its events' attachments; format 5 codec
  round trip with attachments, format 4 file decodes with none, reader refuses unknown owner /
  bad sha / duplicate locator; `ArtifactsCodec` round trip, `STORED` for compressed types,
  refuses newer format, sha mismatch skipped and reported; `RestoreArtifacts` set mismatch
  refused, missing rows counted, bytes land at the row's locator; `ExportBackupSet` plan lists
  every managed row exactly once.
- `:app` JVM: migration 4→5 from `4.json`; `RoomAttachmentRepository` DAO round trip incl.
  cascade on asset and event delete and `observeForOwner`; `AttachmentStorage.state()` over a
  fake resolver; `Thumbnails` cache naming; ViewModel tests for the DOCUMENTS section states
  (ready / not configured / access lost / missing bytes).
- Emulator (instrumented): `SafTreeAttachmentStore` contract over a `fromFile` tree;
  `AttachmentsDeviceProofTest`: configure a tree (test injects the tree via `AppPrefs` on a
  `fromFile` root), add a file from a test-provided content URI, see the row and the bytes,
  open the sheet and rename, delete, export a set into a test tree and find two stamped files,
  wipe, restore data (row present, "Not on this device"), restore artifacts (thumbnail/glyph
  restored), set-mismatch refused. Existing smoke suites still green.
- Phone (real data, no instrumented runs): the §10 procedure; a real 2B-2 install migrates v4→v5
  in place; export a set; the folder shows two files; Syncthing picks them up.
- CI green; evidence file `docs/design/phase-4a-evidence.md` with the nine standard sections.
