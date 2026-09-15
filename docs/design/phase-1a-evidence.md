# Phase 1A exit-criteria evidence — durable identity

Branch `phase-1a`, gathered 2026-09-14 at the tip of the Task 4 commit
(`saf backup io, debug backup screen, restore proof test, phase 1a evidence`), on top of
`63635be` → `970c739` → `13a7d54`. Base: `c84b881` (Phase 0 merged).

The criteria below are the owner's authorisation for Phase 1A: 1–6 are the things that must be
demonstrated, 7–9 are the things that must **not** have been built.

## Exit criteria

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | Package identity normalised | `aapt dump badging` on both APKs: `package: name='com.loosecannon.notenfc' versionCode='1' versionName='2.0'` (§1) | **Pass** |
| 2 | Room 3 schema v1 for Asset / NfcTag / ExternalLink, exported `1.json` committed | `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/1.json`, tracked by git; version 1, tables `asset`, `nfc_tag`, `external_link`, identityHash `ec9cee7c4071ec5303cf7975868fea93` (§2) | **Pass** |
| 3 | Durable IDs + repository layer with JVM tests | UUID-v4 string PKs generated in `:core` (`UuidGenerator`), never by SQLite; `RoomAssetRepository` / `RoomTagRepository` / `RoomLinkRepository` / `RoomUnitOfWork` over pure domain models; 28 JVM tests in `:app` (§3, §6) | **Pass** |
| 4 | Replace-mode backup export/import | `BackupCodec` (ZIP of `manifest.json` + `data.json`, SHA-256 over the data entry), `ExportBackup`, `ImportBackupReplace` in one write transaction; `SafBackupIO` binds it to a user-picked SAF document; wired into `AppGraph` as `exportBackup` / `importBackupReplace` (§4) | **Pass** |
| 5 | The proof: create → export → wipe/reinstall-equivalent → restore → same IDs and relationships | `RestoreProofTest.forgetEverythingAndRebuildTheSameGraph` (JVM, bundled SQLite driver). Assertions quoted in §5. | **Pass** |
| 6 | JVM DAO / schema / backup tests | `:core` 50 tests, `:app` 28 tests, **78 total**, 0 failures, 0 errors, 0 skipped (§6) | **Pass** |
| 7 | No NFC-writing UX added | The three legacy activities are byte-for-byte the pre-1A code, moved packages only. No new NFC write path, no tag-binding screen. The only new screen is debug-build-only (§7). | **Pass (nothing built)** |
| 8 | No maintenance model | No schedule, interval, due-date, event or reminder table, entity or use case exists. Schema v1 is exactly three tables. | **Pass (nothing built)** |
| 9 | No Apollo UI | No Compose dependency, no design system, no theme, no navigation. The debug screen is plain `android.app.Activity` + `android.widget` on an XML `LinearLayout` — deliberately ugly so it cannot be mistaken for product UI. | **Pass (nothing built)** |

---

## 1. Package identity

```
$ aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -E "^package:|^launchable-activity"
package: name='com.loosecannon.notenfc' versionCode='1' versionName='2.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
launchable-activity: name='com.loosecannon.notenfc.debug.DebugBackupActivity'  label='noteNFC Backup (debug)' icon=''
launchable-activity: name='com.loosecannon.notenfc.legacy.MainActivity'  label='' icon=''

$ aapt dump badging app/build/outputs/apk/release/app-release.apk | grep -E "^package:|^launchable-activity"
package: name='com.loosecannon.notenfc' versionCode='1' versionName='2.0' platformBuildVersionName='17' platformBuildVersionCode='37' compileSdkVersion='37' compileSdkVersionCodename='17'
launchable-activity: name='com.loosecannon.notenfc.legacy.MainActivity'  label='' icon=''
```

`namespace` and `applicationId` are both `com.loosecannon.notenfc`; the Kotlin package root
matches; `app/src/main/java` no longer exists. The second launcher entry in the debug APK is the
debug backup screen and is proved absent from release in §7.

### Release signing fingerprint

Release builds are signed by the key generated in Task 1, from
`~/.config/notenfc/keystore.properties` (never in the repo):

```
$ apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
Signer #1 certificate DN: CN=noteNFC, O=GonzRon

SHA-256: 09:02:D3:B0:F8:26:38:19:05:C6:D8:25:4F:DA:F3:67:56:92:4D:80:DA:7C:19:B9:0A:08:93:0B:33:27:7A:9F
```

Same fingerprint as the README records. A clone without that file still builds — the release APK
is simply unsigned.

## 2. Schema v1

```
$ git ls-files app/schemas
app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/1.json

version 1
tables ['asset', 'nfc_tag', 'external_link']
identityHash ec9cee7c4071ec5303cf7975868fea93
```

Foreign keys as D4 §3–§4 requires: `nfc_tag.asset_id → asset ON DELETE SET NULL`,
`nfc_tag.link_id → external_link ON DELETE SET NULL`, `external_link.asset_id → asset ON DELETE
CASCADE`; `UNIQUE(payload_format, payload_key)` on `nfc_tag`; `_at` epoch-millis `Long` columns;
enums stored as their Kotlin names. The D4 `CHECK(NOT (asset_id IS NOT NULL AND link_id IS NOT
NULL))` has no Room annotation and is enforced in `RoomTagRepository` via
`NfcTagEntity.requireAtMostOneTarget()` — see Deviations.

## 3. Durable IDs and the repository layer

Identity is generated in `:core` (`UuidGenerator`, UUID v4 strings) and written verbatim to the
primary-key columns; SQLite never invents an id, and the backup format carries ids as-is. That is
the whole point of criterion 5: an id survives an uninstall because nothing in the storage layer
owns it.

`:app` DAO/repository tests, all on a fresh in-memory database per test with `db.close()` in a
`finally`, no Robolectric:

| Class | tests |
|---|---|
| `AssetDaoTest` | 5 |
| `NfcTagDaoTest` | 7 |
| `ExternalLinkDaoTest` | 5 |
| `RoomRepositoriesTest` | 9 |
| `RestoreProofTest` | 2 |
| **total** | **28** |

## 4. Replace-mode backup

- Format v1 = ZIP with exactly `manifest.json` and `data.json`. The manifest carries
  `formatVersion`, `appVersion`, `schemaVersion`, `createdAt`, per-table `counts`, and the
  SHA-256 of the `data.json` bytes. Lists are sorted by id and entry times are fixed, so the same
  input always encodes to the same bytes.
- A newer `formatVersion` raises `BackupNewerFormat`; a hash mismatch, a missing entry,
  unparsable JSON, or an enum value this build cannot name raises `BackupCorrupt`. So does a
  broken graph: `decode()` also checks that ids are unique within each of the three lists and that
  every non-null `externalLinks[].assetId`, `nfcTags[].assetId` and `nfcTags[].linkId` resolves
  inside the file (§8). All of it happens **before** the transaction opens, so a refused import
  cannot have touched anything.
- `ImportBackupReplace` runs `deleteAll` ×3 then the three insert loops inside one
  `withWriteTransaction`; inserts go assets → links → tags so foreign keys hold at every step.
- `SafBackupIO(resolver, uri)` implements the `BackupIO` port over a user-picked document,
  `Dispatchers.IO`, whole-array read/write, `"wt"` on open so a re-export truncates first.
- `AppGraph` now exposes `exportBackup` (with `appVersion = BuildConfig.VERSION_NAME`,
  `schemaVersion = 1`) and `importBackupReplace`; `buildFeatures { buildConfig = true }` was added
  to `app/build.gradle.kts` for `VERSION_NAME`.

### Debug backup screen (debug builds only)

`app/src/debug/` — manifest, activity, layout. Four buttons and a counts line:
**Seed sample** (2 assets, 2 links — one asset-bound, one standalone — and 3 tags: asset-bound
LEGACY_MD5, link-bound V1, unbound spare, all inside one `uow.write`), **Export**
(`ACTION_CREATE_DOCUMENT`, `application/zip`, suggested `notenfc-backup-<yyyyMMdd-HHmm>.zip` →
`ExportBackup.run()` → `SafBackupIO.write`), **Import (replace)** (`ACTION_OPEN_DOCUMENT` →
`SafBackupIO.read` → `ImportBackupReplace.run` → the `ImportReport` on screen), **Wipe**
(`deleteAll` ×3 in one transaction). Counts (`assets / tags / links`) refresh after every action;
**every** outcome — success, failure and a cancelled picker alike — goes through the same
`report()`, which raises a `Toast` and writes the same line under the counts. Coroutines come
from a `MainScope()` cancelled in `onDestroy`.

## 5. The proof

**Test:** `app/src/test/kotlin/com/loosecannon/notenfc/backup/RestoreProofTest.kt` →
`forgetEverythingAndRebuildTheSameGraph` — **PASSED**.

Shape: build a graph over in-memory database **1** (2 assets; 1 link owned by the first asset and
1 standalone link; 3 tags — one bound to the asset with a `LEGACY_MD5` payload, one bound to the
standalone link, one `UNBOUND` with `TagTarget.None`), export it to bytes, take a sorted domain
snapshot, then **`db1.close()`** — that database and everything in it is gone. Open in-memory
database **2**, which has never seen the data (asserted empty), import the bytes into it, snapshot
again.

Assertion summary:

```
assertTrue("the new database must start empty", snapshot(g2).assets.isEmpty())
assertEquals(before, after)                                   // whole sorted snapshot: assets, tags, links
assertEquals(ImportReport(assets = 2, tags = 3, links = 2, formatVersion = 1), report)

// relationships through the FK columns, not just the id strings:
assertEquals(1, g2.tags.forAsset(before.assets[0].id).size)
assertEquals(TagId("tag-on-furnace"), g2.tags.forAsset(before.assets[0].id).single().id)
assertEquals(1, g2.tags.forLink(standalone.id).size)          // standalone = the link with assetId == null
assertEquals(TagId("tag-on-manual"), g2.tags.forLink(standalone.id).single().id)
assertEquals(1, g2.links.forAsset(AssetId("asset-furnace")).size)
assertEquals(listOf(LinkId("link-standalone")), g2.links.standalone().map { it.id })
assertEquals(TagTarget.None, g2.tags.get(TagId("tag-spare"))!!.target)
assertEquals(PayloadFormat.LEGACY_MD5,
    g2.tags.findByPayload(PayloadFormat.LEGACY_MD5, "0123456789abcdef")!!.payloadFormat)
```

`assertEquals(before, after)` compares the sorted `Asset` / `TagBinding` / `ExternalLink` lists
whole, so every id, name, status, timestamp and target must match — not just the counts.

**Companion test:** `replaceImportIsAtomic` — **PASSED**. A populated database is handed bytes
with one flipped byte: `BackupCorrupt` is thrown and the snapshot is unchanged
(`assertEquals(untouched, snapshot(g))`). Then a valid backup taken from a *different* database is
imported over it, and none of the local rows survive (`asset-local`, `link-local`, `tag-local` all
absent; counts are exactly the backup's 2 / 3 / 2).

**Mutation check.** Because both proof tests passed the moment they were written — they assert
behaviour Tasks 2 and 3 had already built — the proof was checked against a deliberate mutation
rather than a missing implementation. `ImportBackupReplace` was temporarily changed to import tags
with `target = TagTarget.None`, which keeps every row count and every id correct and breaks only
the relationships:

```
> Task :app:testDebugUnitTest FAILED
RestoreProofTest > forgetEverythingAndRebuildTheSameGraph FAILED
2 tests completed, 1 failed
  java.lang.AssertionError: expected:<Snapshot(assets=[Asset(id=AssetId(value=asset-furnace), ...
```

The mutation was reverted; the proof bites on exactly the property it claims to prove.

### On-device run — **pending**

No device was attached when this evidence was gathered (`adb devices` listed none), so the manual
Seed → Export → Wipe → Import → counts-return run on hardware has **not** been performed. The
debug APK is built and contains the screen (§7). This is the one Phase 1A item still owed, and it
is a confirmation of the JVM proof rather than a substitute for it.

## 6. Test totals

`./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain -q`
→ **BUILD SUCCESSFUL**, exit 0, no output.

Counts read from the JUnit XML (`core/build/test-results/test/`,
`app/build/test-results/testDebugUnitTest/`):

| Module | Class | tests | failures | errors | skipped |
|---|---|---|---|---|---|
| `:core` | `BackupCodecTest` | 23 | 0 | 0 | 0 |
| `:core` | `BackupUseCasesTest` | 7 | 0 | 0 | 0 |
| `:core` | `NdefCodecTest` | 9 | 0 | 0 | 0 |
| `:core` | `LegacyKeyTest` | 6 | 0 | 0 | 0 |
| `:core` | `LegacyLinkPolicyTest` | 5 | 0 | 0 | 0 |
| `:core` | **total** | **50** | **0** | **0** | **0** |
| `:app` | `AssetDaoTest` | 5 | 0 | 0 | 0 |
| `:app` | `NfcTagDaoTest` | 7 | 0 | 0 | 0 |
| `:app` | `ExternalLinkDaoTest` | 5 | 0 | 0 | 0 |
| `:app` | `RoomRepositoriesTest` | 9 | 0 | 0 | 0 |
| `:app` | `RestoreProofTest` | 2 | 0 | 0 | 0 |
| `:app` | **total** | **28** | **0** | **0** | **0** |
| | **Phase 1A total** | **78** | **0** | **0** | **0** |

## 7. The debug screen is not in the release build

```
$ aapt dump xmltree app/build/outputs/apk/debug/app-debug.apk   AndroidManifest.xml | grep -c DebugBackup
1
$ aapt dump xmltree app/build/outputs/apk/release/app-release.apk AndroidManifest.xml | grep -c DebugBackup
0
```

And in the bytecode, not only the manifest — searching every `classes*.dex` in each APK for the
class name: debug 12 occurrences, release **0**. The screen lives entirely under `app/src/debug/`,
so the release variant never compiles it.

## 8. Final review fixes

Three Important findings from the Phase 1A final review, plus the owner's export-snapshot ruling (8.4) — all inside the authorised scope.

### 8.1 The bundled SQLite natives are gone from the shipped APK

`implementation(libs.sqlite.bundled)` was in `app/build.gradle.kts` — a plan defect, not a coding
slip. `AndroidSQLiteDriver` (the only driver the app itself uses, in `AppGraph`) comes from
`androidx.sqlite:sqlite-framework`, which `room3-runtime-android` already depends on, so the
bundled native build was pure payload: four `lib/*/libsqlite3*.so` slices the app never calls.
The line is deleted; `testImplementation(libs.sqlite.bundled.jvm)` stays, because the JVM tests
genuinely need it (`TestDb.inMemoryDb()` builds Room over `BundledSQLiteDriver`). Nothing had to
be added to replace it — `sqlite-framework` arrives transitively and the build resolves clean.

```
$ unzip -l app/build/outputs/apk/release/app-release.apk | grep -c 'lib/'
0
$ unzip -l app/build/outputs/apk/debug/app-debug.apk   | grep -c 'lib/'
0
```

| APK | before | after | delta |
|---|---|---|---|
| release | 7,323,927 B (6.98 MiB), 4 `lib/` entries | 2,424,909 B (2.31 MiB), 0 | **−4.90 MB, −66.9%** |
| debug | 10,787,122 B (10.29 MiB), 4 `lib/` entries | 7,870,130 B (7.51 MiB), 0 | **−2.92 MB, −27.0%** |

### 8.2 `BackupCodec.decode()` validates the graph, not just the rows

The DTO→domain pass proved every row was *nameable*; it did not prove the rows *fit together*. A
file whose `nfcTags[].linkId` pointed at a link that is not in the archive would have passed
`decode()` and then failed inside `ImportBackupReplace`'s single write transaction — after
`deleteAll` ×3, with the user's real data already gone. `decode()` now runs `validateGraph(data)`
after the DTO pass and before returning:

- ids unique within `assets`, within `nfcTags`, within `externalLinks`;
- every non-null `externalLinks[].assetId` present in `assets`;
- every non-null `nfcTags[].assetId` present in `assets`;
- every non-null `nfcTags[].linkId` present in `externalLinks`.

Each violation throws `BackupCorrupt` naming the table, the offending id, and the dangling or
duplicated reference, e.g. `nfcTags: tag t5 points at link l404, which is not in externalLinks`
and `assets: duplicate id a1`.

Seven tests added to `BackupCodecTest` (16 → 23): dangling link→asset, dangling tag→asset,
dangling tag→link, duplicate asset id, duplicate tag id, duplicate link id, and one asserting the
valid fixture still decodes whole. `encode()` deliberately does not validate, so a broken fixture
seals into a well-formed archive — matching hash, nameable enums — and only `decode()` is on
trial.

**Mutation check.** With `validateGraph(data)` commented out:

```
> Task :core:test FAILED
BackupCodecTest > a duplicate asset id is corrupt() FAILED
BackupCodecTest > a tag pointing at a missing asset is corrupt() FAILED
BackupCodecTest > a duplicate tag id is corrupt() FAILED
BackupCodecTest > a link pointing at a missing asset is corrupt() FAILED
BackupCodecTest > a tag pointing at a missing link is corrupt() FAILED
BackupCodecTest > a duplicate link id is corrupt() FAILED
50 tests completed, 6 failed
```

The seventh (`the fixture graph is referentially whole`) stays green either way, as it should.

### 8.3 The upsert ruling now has tests

Deviation 1 says `@Upsert` and `INSERT OR REPLACE` were both rejected because a REPLACE deletes
the old row first, firing `external_link` CASCADE and `nfc_tag` SET NULL and silently unbinding
every child. That was an argument with nothing holding it in place. Two tests now do:

- `AssetDaoTest.upsertOfAnExistingAssetKeepsItsChildrenBound` — seed asset `a1` with one bound tag
  and one bound link, upsert `a1` with a changed name, assert `nfc_tag.asset_id` and
  `external_link.asset_id` are both still `a1` (and still reachable via `forAsset`) while the name
  did change.
- `ExternalLinkDaoTest.upsertOfAnExistingLinkKeepsItsTagsBound` — seed link `l1` with one bound
  tag, upsert `l1` with a changed label, assert `nfc_tag.link_id` is still `l1`.

**Mutation check.** With both `upsert` bodies changed from `if (update(e) == 0) insert(e)` to
`delete(e.id); insert(e)` — i.e. REPLACE semantics:

```
> Task :app:testDebugUnitTest FAILED
AssetDaoTest > upsertOfAnExistingAssetKeepsItsChildrenBound FAILED
ExternalLinkDaoTest > upsertOfAnExistingLinkKeepsItsTagsBound FAILED
28 tests completed, 2 failed
```

Only the two new tests notice — which is exactly why they were missing.

### Verification after 8.1–8.3

```
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain -q
(no output, exit 0)
```

`:core` 43 → **50**, `:app` 26 → **28**, **78 total**, 0 failures, 0 errors, 0 skipped.

### 8.4 Export snapshot

`ExportBackup` read the three tables with three separate, unsynchronised calls. A write landing
between them would have been sealed into the archive as an inconsistent state — a tag whose asset
is not in the file, a link whose owner is not. The owner's ruling: a backup is one point in time.

- `UnitOfWork` gains `suspend fun <T> read(block: suspend () -> T): T` beside `write`. Contract:
  every repository read inside `block` observes one snapshot; writing inside it is illegal.
- `RoomUnitOfWork.read` = `db.withReadTransaction { block() }` (Room 3; §D3 §5 wording fixed to
  name both `withWriteTransaction {}` and `withReadTransaction {}`, there is no bare
  `withTransaction {}`).
- `ExportBackup` takes a `UnitOfWork` and builds its `BackupData` inside one `uow.read { ... }`.
  `AppGraph` and the two test call sites pass it.

Tests:

- `BackupUseCasesTest.exportReadsAllTablesInsideOneReadSnapshot` — the in-memory fakes now carry a
  `TransactionWitness`: `FakeUnitOfWork` counts `reads` and every `all()` served in no transaction
  bumps `readsOutsideSnapshot`. The test exports from populated fakes and asserts exactly one read
  transaction and zero reads outside it.
- `RoomRepositoriesTest.readTransactionRejectsWrites` — an `upsert` inside `uow.read { }` throws
  and the row is absent afterwards.
- `RoomRepositoriesTest.readTransactionAllowsReads` — `read { assets.all() }` returns the seeded
  rows.

**RED first.** With the fakes and the test in place but `ExportBackup` still reading outside a
transaction:

```
> Task :core:test FAILED
BackupUseCasesTest > exportReadsAllTablesInsideOneReadSnapshot() FAILED
    org.opentest4j.AssertionFailedError: expected: <1> but was: <0>
8 tests completed, 1 failed
```

**One thing the ruling did not get for free.** `readTransactionRejectsWrites` runs against a
file-backed database (`TestDb.fileBackedDb`), not `inMemoryDb()`. An in-memory Room database is a
single connection, so it has no read-only reader pool and `withReadTransaction` there is a
`BEGIN DEFERRED` that happily accepts writes — probed directly: the `upsert` succeeded and the row
was present. On a file database, which is what the app actually ships (`AppGraph` builds Room over
`getDatabasePath(...)`), the same write is refused by the read-only reader connection and the row
is absent. So the *refusal* is a property of the shipped configuration and the test has to sit on
a file to observe it. The snapshot guarantee `ExportBackup` depends on holds in both.

### Verification after 8.4

```
$ ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain -q
(no output, exit 0)
```

`:core` 50 → **51**, `:app` 28 → **30**, **81 total**, 0 failures, 0 errors, 0 skipped.

## Cutover note

From v2.0 the app ships as `com.loosecannon.notenfc`. The pre-2.0 builds were
`com.looseCannon.noteNFC` — a different package as far as Android is concerned — so the two
install side by side and **both** answer the legacy `md5_short` NFC dispatch filter.
**Uninstall `com.looseCannon.noteNFC` before any device testing of the new package**, otherwise a
tag scan raises a disambiguation dialog and the wrong copy may win. There is no data migration
between them: the old app kept its links in SharedPreferences and the old install is expected to
be thrown away (D13 §4).

## Deviations

1. **`@Upsert` replaced by `@Transaction` update-then-insert** (Task 2, carried forward). Room's
   upsert adapter reads a null `SQLException` message under the mockable `android.jar` in JVM
   tests and throws; `INSERT OR REPLACE` was not an option either because a delete+insert fires
   the `SET NULL` / `CASCADE` foreign keys and would silently unbind tags. Cost: two statements
   per upsert.
2. **`CHECK(NOT (asset_id IS NOT NULL AND link_id IS NOT NULL))` is not in the schema.** Room has
   no CHECK annotation. The rule is enforced on the way into the table by
   `NfcTagEntity.requireAtMostOneTarget()`, and the domain's sealed `TagTarget` already makes
   both-at-once unrepresentable. A row inserted by anything other than the repository could still
   violate it; nothing else writes to the database today.
3. **Import picker accepts `*/*`, not only `application/zip`** (Task 4). Document providers hand
   backups back as `application/octet-stream` often enough that a strict filter hides the file the
   user just exported. `EXTRA_MIME_TYPES` still advertises zip and octet-stream, and a wrong file
   is rejected by the codec, not by the picker.
4. **The debug screen uses the deprecated `startActivityForResult` / `onActivityResult`.** A plain
   `android.app.Activity` has no `ActivityResultLauncher`, and pulling in `androidx.activity`
   solely for a debug harness would have added a dependency the phase does not otherwise need.
5. **The proof tests were green on first run** — they assert behaviour Tasks 2 and 3 had already
   implemented, so there was no natural RED. A mutation check was substituted (§5).
6. **On-device run not performed** — no device attached; recorded as pending (§5).
7. **Asset carries fewer columns than D4 §3 describes.** Phase 1A deliberately ships the minimal
   subset (D7 1A); the remaining columns arrive by migration in Phase 2. Pre-agreed in the plan's
   pre-flight scan, restated here so the schema-v1 delta is on the record.

## Ready for Phase 1B: **yes**

Identity, schema, repositories and the backup round trip are all proved on the JVM, and the debug
harness exists to confirm the same path on hardware. The one outstanding item — the on-device
Seed → Export → Wipe → Import run — needs a phone, not more code, and does not block writing
Phase 1B against these ports.
