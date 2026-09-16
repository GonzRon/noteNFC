# D7 — Implementation sequence

Status: design-phase document, revised 2026-09-14 after review 1 (phase order per ruling R-6;
Phase 1 split into 1A/1B/1C; Room 3.0; Todoist representation policy). Vertical slices; the app
is installable and useful at the end of every phase. Exit criteria are observations someone could
disprove, not "tests pass". Issue numbers refer to the reconciled mapping in D2.

```
Phase 0  Foundation
   ▼
Phase 1  Tag survival (M1)  =  1A persistence + legacy migration + backup/restore
                               1B tag payload format v1 + legacy resolver + real-device NFC proof
                               1C Compose shell + asset/link UX + full restore proof
   ▼
Phase 2  Journal + profiles
   ▼
Phase 4A Attachments: SAF-tree managed store + data/artifacts backup split   (pulled ahead 2026-09-15)
   ▼
Phase 3  Scheduling + local reminders
   ▼
Phase 3R Resilience: automatic versioned backup   (scheduling, retention, health; the format split is already in 4A)
   ▼
Phase 4B Attachments: referenced documents, store migration UX          (R-6: first, because manuals/photos/provenance records pay off immediately)
   ▼
Phase 5  Todoist              (R-6: second)
   ▼
Phase 6  Supplies             (R-6: third)
   ▼
Phase 7  Extension points / polish
```

Phases 4, 5, 6 remain architecturally independent of one another; R-6 fixes the execution order
only.

---

## Phase 0 — Foundation and repository hygiene

| | |
|---|---|
| **Goal** | A clone-buildable, CI-tested repository on the modern toolchain with the legacy behaviour characterised by tests, and no behaviour change for users. |
| **Prerequisites** | Ruling R-1's non-destructive keystore investigation (identify the installed APK's certificate via `adb shell pm` / `apksigner`, search the author's machines for the `fillMateAndroid` keystore); spike S1 (toolchain: AGP 9.4 + built-in Kotlin + KSP 2 + Room 3.0.x + Compose BOM 2026.08 + Navigation 3 1.1.x). |
| **Source areas** | `.gitignore` (stop ignoring wrapper, settings, `gradle/`, test dirs; ignore `build/`), remove tracked `app/build/outputs/…/app-debug.apk` and the release APK from the tree (keep in a GitHub release if wanted); commit wrapper + `settings.gradle.kts` + `gradle.properties`; `gradle/libs.versions.toml`; AGP 9.4 + built-in Kotlin; JDK 17 targets; minSdk 26 / target 36 / compile 37; remove manifest `package`; add `android:exported`; new `:core` module; GitHub Actions; `LegacyKey` and `NdefCodec` (legacy decode only) extracted into `:core` and used by the three existing activities unchanged in behaviour. |
| **Schema** | none |
| **Tests** | `LegacyKeyTest` (known vectors), `NdefCodecTest` (legacy decode), a Robolectric test that `MainActivity` stores `MD5[0:8] → text` (characterisation), CI green. |
| **User-visible** | nothing; version 1.1 (versionCode 2). |
| **Exit criteria** | (1) `git clone && ./gradlew :core:test :app:testDebugUnitTest` passes on a machine without Android Studio; (2) the legacy decode tests prove the old on-tag bytes decode to the same key (the on-device old-APK check is an optional sanity check under D13, not a gate); (3) `git status` clean after `assembleDebug`; (4) the keystore investigation result is recorded in D8 R-1 (closed by D13). **Met 2026-09-14; see `phase-0-evidence.md`.** |
| **Rollback / compat** | No data change. If the AGP 9 toolchain fights back, fall back to AGP 8.13 + KGP 2.x for this phase only. |

## Phase 1 — Tag survival (Milestone M1, the smallest useful end-to-end slice)

Goal for the whole milestone (revised by D13): prove the new architecture end to end — Room
store with stable IDs, payload format v1, tag resolver (legacy `md5_short` recognised
best-effort), Compose shell, and backup/restore that preserves every *newly provisioned* tag
relationship. After this phase a phone can die and every new tag still works after a restore.
**Prelude (first commit of 1A):** normalise `applicationId`/`namespace`/Kotlin package root to
`com.loosecannon.notenfc`, reset `versionCode 1` / `versionName "2.0"`, create and document a
new release keystore outside the repo (D13 §4). Implemented as three internal slices with their
own falsification points; the milestone is done when 1C's criteria hold.

### 1A — Persistence, legacy migration, backup/restore

| | |
|---|---|
| **Prerequisites** | Phase 0; S1 outcome (Room 3.0.x confirmed or 2.8.5 fallback chosen); S7 (Auto Backup rules). |
| **Source areas** | package-identity prelude (above); `data/room` (AppDatabase v1: `asset` minimal fields, `nfc_tag`, `external_link`), `backup/*` (ZIP codec, SAF export/import Replace only, auto snapshots), a minimal debug-only screen or CLI-style instrumentation to trigger export/import. No SharedPreferences migration (D13). |
| **Schema** | Room v1 with exported schema. |
| **Tests** | `BackupCodecTest` (round-trip with ID preservation, refuse newer format), DAO tests as plain JVM tests via the bundled driver, `BackupRestoreTest` (export → wipe → import → identical rows). |
| **Exit criteria** | (1) importing a backup produced by this build into an empty install yields identical `nfc_tag`/`asset`/`external_link` rows (byte-equal `data.json` after canonical ordering); (2) `./gradlew :app:assembleDebug` produces an APK whose package is `com.loosecannon.notenfc` and it installs beside the old app; (3) the schema JSON is committed and a no-op migration test passes. |

### 1B — Tag payload format v1, legacy resolver, real-device NFC proof

| | |
|---|---|
| **Prerequisites** | 1A; spike S2 (reader-mode write/read-back on the user's phone). |
| **Source areas** | `:core/nfc` (payload format v1 codec, `TagPayload`), `nfc/*` (reader mode session, writer with confirm + read-back + AAR, `NfcDispatchActivity` with both `NDEF_DISCOVERED` filters, `TECH_DISCOVERED` removed), `ResolveTag`/`BindTag` use cases, `links/LinkLaunchPolicy` + `<queries>`, unknown-tag and legacy-tag flows (D13 §3: recognise, offer rewrite or bind), `notenfc://tag` deep link. Delete the three legacy activities; remove `LegacyKey` and `LegacyLinkPolicy` from `:core` (superseded). |
| **Tests** | payload format v1 round-trip, exact byte layout, unknown version, malformed; `ResolveTagTest` (every resolution incl. unknown legacy → "Legacy tag" screen); `LinkLaunchPolicyTest`. |
| **Exit criteria** | (1) an NTAG213 holds the v1 message and reads back byte-identical; (2) a tag holding foreign NDEF content (including an old `md5_short` tag) triggers the confirmation and is not written without it, and a legacy tag is recognised as such; (3) scanning with the app closed opens it through `NfcDispatchActivity`; (4) optional sanity check: an old-APK tag is recognised as legacy on the device. |

### 1C — Compose shell, asset/link UX, full restore proof

| | |
|---|---|
| **Prerequisites** | 1A, 1B; S1's Navigation 3 confirmation; **gate G1** (D8 §2): representative-screen review of asset detail, dashboard, water-test entry, and NFC scan/write against D12 before the theme is written. |
| **Source areas** | `ui/theme` (D12: M3 light/dark schemes, `NoteNfcSemanticColors`, typography, shapes, appearance setting with optional dynamic colour that never recolours the semantic layer); `ui`: Home (assets + links), Asset create/edit (name, category, notes) with the Asset Identity Plate skeleton, Scan/Write tag with the D12 §11 NFC states, Links, Unknown/legacy-tag, Backup, Settings shell; `notenfc://asset|link` deep links; share-sheet card (D6 §8); "no backup yet" nudge; migration report. |
| **Tests** | Compose smoke tests for Home, Scan, Backup; deep-link routing tests; share-sheet intent test. |
| **Exit criteria (milestone M1)** | (1) a new tag written on phone X resolves on phone Y after restoring X's backup, entirely through the UI; (2) the original share → write → scan → launch flow works with Joplin end to end and returns to Joplin after the write; (3) Home shows the "no backup yet" nudge until the first export succeeds; (4) all 1A/1B criteria still hold. |
| **Rollback / compat** | The old app is a separate package and keeps working until uninstalled; Room v1 → nothing to roll back. |

## Phase 2 — Asset core, journal, profiles

| | |
|---|---|
| **Goal** | "Scan the hot tub → log a water test in a few taps; see the history." |
| **Prerequisites** | Phase 1. |
| **Source areas** | Room v2 (`measurement_definition`, `event_profile`, `profile_field`, `profile_consumable`, `asset_event`, `measurement`, `consumable_usage`; full `asset` fields incl. hierarchy and season window); seed templates JSON (`hot_tub`, `power_equipment`, `ups`, `generic`); generic entry form rendered as D12 §9 Instrument Measurement rows (value, unit, range, explicit LOW / IN RANGE / HIGH); journal as the D12 §8 Service Ledger; full Asset Identity Plate; journal list/detail/edit; range classification; profile editor; backup importer tolerates the new tables. |
| **Schema** | Room v2 migration + test. |
| **Tests** | `:core`: template application (idempotent), range classification, measurement typing. `:app`: DAO tests for the time-series query, migration v1→v2, entry-form Compose test (required-field gating), backup round-trip incl. events. |
| **User-visible** | Full asset fields; child assets; profiles and quick actions on the asset screen; structured history; "log a reading". |
| **2A / 2B split (2026-09-15)** | 2A = the journal vertical slice (spec `docs/superpowers/specs/2026-09-15-phase-2a-journal-design.md`). 2B = the definition/profile editor, full asset fields, child assets, season windows, template management, derived display values (e.g. RO rejection %), the retirement of the Scan tab (D12 §16), and a **category suggestion catalog** with optional template hints used only at creation. **Open 2B question, default answer no:** a canonical `equipment_type_key` is not introduced unless a concrete 2B-or-later feature needs one that free-text category plus suggestions cannot satisfy (D4 §4). |
| **Exit criteria** | (1) hot-tub acceptance from issue #13.1 reproduced on device; (2) mower oil-change event with engine hours stored as a meter reading; (3) exported backup re-imports with identical table counts; (4) no hot-tub- or mower-specific table exists (schema inspection). |
| **Rollback / compat** | Room down-migration not supported; a Phase-1 backup imports into Phase 2. |

## Phase 3 — Scheduling engine and local reminders

| | |
|---|---|
| **Goal** | "Scan the thing and see what needs to happen next", with reliable local reminders and a health screen. |
| **Prerequisites** | Phase 2; spike S4 (daily alarm + WorkManager behaviour on the user's device/OEM). |
| **Source areas** | `:core/scheduling` (rules, `rebuild`, status, season, subjects with `RuleFacts`); Room v3 (`maintenance_schedule`, `schedule_provider`, `schedule_state`); schedule editor (time/meter/both, basis, anchor, `anchor_meter`, lead, season behaviour, single-choice provider, completion mode); complete/snooze/postpone/edit flows; `reminders/local` (digest alarm, backstop worker, receivers, channels, POST_NOTIFICATIONS, quick actions with nonce); `reminders/health` (local findings + auto-repair) rendered with the D12 §5 states; Home dashboard with the D12 §10 attention hierarchy (grayscale-obvious is the acceptance test); `notenfc://schedule` links; default schedules from templates. |
| **Schema** | Room v3 migration + test. |
| **Tests** | `:core`: the full deterministic suite (testing doc §2). `:app`: alarm re-arm after `BOOT_COMPLETED` (`ShadowAlarmManager`), notification content and actions, nonce rejection, health findings with positive and negative controls, migration v2→v3. |
| **User-visible** | Schedules on assets; dashboard; notifications with Done/Snooze/Open; Reminder health with Repair; **scan-time maintenance context** (D5 §7A): an asset reached by NFC dispatch surfaces its OVERDUE/DUE work with Record completed (backdatable) / Log via the profile form / Review / Snooze / Postpone; NO_DATA meters surface "Log engine hours". Bottom navigation re-evaluated here (D12 §16): a maintenance destination may replace the retired Scan tab. |
| **Exit criteria** | (1) all D5 §10 worked examples pass as tests; (2) on the device, a schedule due tomorrow produces exactly one notification at the configured hour without opening the app, and again after a reboot; (3) revoking notification permission turns the health screen red and Repair opens settings; (5) D5 §7A scan acceptance: the overdue-mower scan shows the contextual sheet and **Log oil change** completes the schedule through the journal form, and a backdated **Record completed** yields the next due date from the backdated event; (4) winter-only hot tub shows INACTIVE_SEASON in July and DUE on Oct 15 (injected `Today`); (5) deleting the completion event moves the due date back, observed in the UI. |
| **Rollback / compat** | Schedules are additive; disabling reminders globally disarms everything. |

## Phase 3R — Resilience: automatic versioned backup

Recorded 2026-09-15 at the M1 boundary and deliberately kept **off the critical path**: manual
export/import, the proven restore into a fresh database and durable ids already give real
protection, and automatic backup unlocks no product capability, whereas Phase 2 does. It lands
after Phase 3 and before Phase 4 because attachments change the storage story (photos and PDFs
move backups into another size class), so the database-only version should exist first.

No hook is added to the code in the meantime: "dirty since the last backup" is already answerable
from the `updated_at` columns against the `lastBackupAt` preference, so no `backupDirty` flag or
abstraction is needed ahead of the phase.

| | |
|---|---|
| **Prerequisites** | Phase 3 (WorkManager is already in the app for the reminder backstop). No new format: the 1A backup ZIP (manifest + SHA-256, `BackupCodec`) is what gets written. |
| **Scope** | (1) A **backup destination** chosen once through `ACTION_OPEN_DOCUMENT_TREE` with a persistable URI grant — Google Drive, a local folder, Nextcloud or any other `DocumentsProvider`; noteNFC never becomes a Drive client (no OAuth, no Drive API, no hidden `appDataFolder`). (2) A **WorkManager** job (constraints: storage/network as the provider needs; retry with backoff) that runs at most daily and only when the store is dirty since the last successful backup, plus an explicit *Back up now*. (3) **Immutable versioned files** `notenfc-<UTC stamp>-v1.zip`; never a single overwritten `latest.zip`. (4) **Retention** applied after each successful write: 14 daily, 8 weekly, 12 monthly (≈ a year of rollback for a database this small). (5) **Backup health** state surfaced in Settings (destination, last backup, current / stale / failed / not configured) and on the dashboard once assets exist ("BACKUP NOT CONFIGURED — Choose backup location"), replacing the 1C "no backup yet" nudge; the 1C rule that an empty install is never nagged stands. (6) Restore from any versioned file through the existing Replace import. |
| **Replication is someone else's job** | The destination is an ordinary folder of ordinary ZIPs so that a sync tool (Syncthing to a NAS, another phone or a workstation; a cloud provider's own client) owns off-device copies and redundancy while noteNFC owns creation, format, integrity and retention. **Retention ownership caveat:** noteNFC's deletions propagate through a plain mirror, so a truly independent archive needs versioning on the receiving side (Syncthing file versioning, NAS snapshots) — the Settings copy for the destination says so, and noteNFC never assumes it is the only copy. |
| **Out of scope** | Attachments (Phase 4 separates database backup from attachment storage because photos/PDFs change the size class); Merge import; a Google-specific path — only if the SAF/Drive provider proves unreliable in the device spike does the direct Drive API (`drive.appdata`) get a concrete reason to exist. |
| **Tests** | JVM: dirty-flag semantics, retention policy over synthetic file lists, filename stamping/parsing, health-state derivation. Device: choose a Drive-backed folder, dirty the store, run the worker on demand, see the file appear in Drive's own UI; reboot and confirm the grant persists; restore an older version. |
| **Exit criteria** | (1) after one change and one worker run, a new versioned ZIP exists in the chosen tree and imports cleanly; (2) a second run with no change writes nothing; (3) retention deletes exactly the files the policy says over a synthetic 60-day history; (4) the grant survives a reboot; (5) health reads "current" only after a verified write. |
| **Rollback / compat** | Feature is additive and off until a destination is chosen; manual export/import unchanged. |

## Phase 4 — Attachments and the storage-provider boundary

**Reorder and split (decided 2026-09-15).** The owner wants the rest of the real records (manuals,
PDFs, photos from the Joplin export) in the app before schedules, so attachments come before
Phase 3, as **4A** (below) with the rest as **4B** after 3R. Two decisions taken with it:

- **The managed store is a user-selected SAF tree.** Google Drive was the intended first choice
  if its DocumentsProvider exposed a writable tree; spike S5 (2026-09-15,
  `spikes/S5-saf-tree-provider.md`) found Drive is not installed on the owner's phone at all, and
  proved the primary-storage provider end to end (persistable grant, create/write/read/delete,
  survival of a process kill). The owner picked a Syncthing-replicated folder, so off-device
  copies come from the sync tool — the owner then moved to a Proton Drive-synced folder, which
  passed the same probe; both are plain folders on the primary-storage provider.
  App-private storage is never silently made primary. The domain sees only `AttachmentStore`
  and provider-relative locators.
- **Backup becomes a set of two archives under one `backupSetId`**: `noteNFC-data-<stamp>.zip`
  (manifest + data JSON incl. attachment metadata, no bytes) and
  `noteNFC-artifacts-<stamp>.zip` (manifest + bytes keyed by attachment id and checksum). Both
  manifests carry `backupSetId`, `createdAt`, `dataFormatVersion`, `artifactFormatVersion`. A
  data-only restore succeeds and marks attachments unavailable; restoring the matching artifacts
  archive completes the set. Phase 3R later automates both without changing the format.

| | 4A | 4B |
|---|---|---|
| Scope | attachment model + `AttachmentStore` port; SAF-tree managed store (chosen once in Settings → Attachment storage, persistable grant); attach from the document picker and camera to assets and events; open with the system viewer; image thumbnails; DOCUMENTS sections; backup set split (data + artifacts); import of the owner's eight SPA files onto the hot tub | REFERENCED documents (single-document grants, "not available on this device" after restore, grant health finding); store-location change with the copy-loop migration; app-private LOCAL as an explicit alternative provider |


| | |
|---|---|
| **Goal** | Photos, labels, receipts, manuals on assets and events; SAF-first storage choice; backup bundles managed bytes. |
| **Prerequisites** | Phase 2 (and 3 for event attachments from completion forms); Phase 3R (database backups are automatic before attachment storage exists); spike S5 (which installed cloud providers expose a tree). |
| **Source areas** | Room v4 (`attachment`); `attachments/*` (LOCAL + SAF tree stores, references, thumbnails, health); camera/document pickers; Documents tab; backup ZIP with `attachments/`; storage settings ("current location / change"). |
| **Tests** | store contract tests run against both stores (put/open/delete/exists round-trip, locator relativity), backup with attachments round-trip, permission-lost health finding, migration v3→v4. |
| **Exit criteria** | (1) same `attachment` rows after switching the store from LOCAL to a SAF tree and migrating (locators unchanged); (2) a referenced cloud PDF opens after reboot; (3) restore on a second phone restores managed photos and lists references as "not available on this device". |

## Phase 5 — Todoist provider and sync

| | |
|---|---|
| **Goal** | Optional Todoist projection with capability-based representation, pull-based reconciliation, canonical verification, conflicts surfaced, health checks extended. |
| **Prerequisites** | Phase 3; rulings R-3 (personal token) and R-4 (revisit domain/App Links before starting); spikes S3 (custom-scheme link tappability), S6 (Sync commands, activity log, reopen/re-date of closed tasks), S8 (FIXED-rule native recurrence round trip). |
| **Source areas** | Room v5 (`reminder_projection` with `representation`, `provider_op`, `integration_account`); `security/KeystoreSecretStore`; `integrations/todoist` (API client over Sync + REST + activities, representation selector, provider, outbox drainer, sync worker, interpretation and verification rules); Integrations settings; health findings incl. `PROJECTION_DUE_DRIFT`; "Open in Todoist". |
| **Tests** | `:core`: `TodoistRepresentationTest`, `TodoistInterpretationTest` (both representations; native completion consumed once; due verification emits `SYNC_DUE`), projection diffing and hashing, replay safety. `:app`: `MockWebServer` scenarios (command uuid reuse, 401/477, 429 backoff, sync token continuity, activity-log pagination), outbox crash-between-write-and-ack recovery, migration v4→v5. |
| **Exit criteria** | (1) issue #9's acceptance sentence reproduced end to end with a real Todoist account for a `NATIVE_RECURRING` schedule (UPS load test `every! 90 days`): completing in Todoist shows the next occurrence immediately and noteNFC records exactly one event after two syncs; (2) a `MANAGED_OCCURRENCE` schedule (mower oil, time-or-meter) is visible in Todoist with meter state and is re-dated after completion in noteNFC; (3) manually rescheduling the native task in Todoist and completing it produces a Todoist date that noteNFC corrects to the canonical value on the next sync; (4) deleting the task in Todoist yields a MISSING finding and Repair recreates it once; (5) airplane-mode completion in noteNFC lands in Todoist after reconnecting without duplicates. |
| **Rollback / compat** | Disconnect withdraws or orphans tasks per user choice; local reminders unaffected. |

## Phase 6 — Supplies and parts

| | |
|---|---|
| **Goal** | "What part belongs here, and do I have enough of it?" with one low-stock reminder. |
| **Prerequisites** | Phase 3 (reminder subjects), Phase 2 (consumable usage). |
| **Source areas** | Room v6 (`supply_item`, `asset_supply`, `stock_ledger`; `supply_id` columns added to `consumable_usage` and `profile_consumable`); supplies screens; ledger + cache; low-stock subject into the configured provider; "Supplies needed" view. |
| **Tests** | ledger arithmetic (COUNT/DELTA ordering, reversal on event delete via cascade), low-stock dedup (one subject per supply), migration v5→v6. |
| **Exit criteria** | (1) logging 1 oz chlorine decrements the supply once; deleting the event restores it; (2) two consecutive digest runs produce one low-stock notification, not two; (3) a manual COUNT overrides history without touching events. |

## Phase 7 — Extension points and polish

| | |
|---|---|
| **Goal** | Keep the future open without building it. |
| **Source areas** | `MeasurementSource` port stub (telemetry → measurements with `source = TELEMETRY`), CSV/JSON per-asset export, backup encryption option, fatigue controls (#11 NEXT), multi-provider UI if ever wanted (model already allows it), Obsidian/Logseq link polish, targetSdk 37 + `DISPATCH_NFC_MESSAGE`, optional OAuth/App Links if R-4 grants a domain, widened native-recurrence eligibility if S8 passes. |
| **Exit criteria** | Each item ships behind its own falsifiable acceptance; no open-ended "polish" bucket remains. |

## Product separation — deferred convergence operation (decided 2026-09-15)

The application built here has become a different product from the original noteNFC. Three
artifacts are intended, but **the split is deferred until ServiceTag is functionally mature and
before its first real deployment or permanent tag rollout**, not tied to any phase. This
supersedes an earlier instruction to split immediately after Phase 2A.

Why deferring is right: the project is entirely greenfield — no users, no production installs, no
deployed tags, no data to migrate, no external compatibility obligations. Package names, NDEF
record types, deep-link schemes, databases and test tags are development artifacts, so there is
no phase-based migration tipping point. Waiting also means the shared NFC layer is extracted from
two concrete, finished consumers rather than designed prospectively.

Target shape at that time:

```text
current modern lineage ──► ServiceTag   (this repository, renamed; keeps history, docs, issues)
                            "the service record attached to the machine": assets, journal,
                            measurements, profiles, schedules, reminders, scan-time context,
                            attachments, supplies, integrations, backup, Apollo Service Binder UI,
                            its own applicationId (expected com.loosecannon.servicetag), record
                            type (com.loosecannon.servicetag:tag) and deep links (servicetag://)
historical narrow lineage ► noteNFC     (reconstructed from the last coherent Joplin/NFC commit,
                            found by reading source behaviour, not dates; keeps its ancestry and
                            com.loosecannon.notenfc; narrow scope with modern NFC safety)
intersection of the two ──► nfc-tag-core (product-neutral: NDEF framing, external-record and
                            AAR helpers taking the package as a parameter, reader-mode lifecycle,
                            capacity checks, safe write + read-back, error mapping; knows nothing
                            of Joplin, assets, Room, Compose, backups or navigation; versioned
                            and pinned by both apps; created only once both consume it)
```

Until then: one repository, one app, no parallel products, **no speculative shared library**.
Preserve separability through ordinary architecture only:

- domain code (`Asset`, `AssetEvent`, `TagBinding`, schedules, reminders) never references the
  applicationId, the NDEF record-type string, the deep-link scheme or the brand name; those live
  at adapter/configuration boundaries (`NdefCodec`, `TagRoute`/`DeepLinkRoute`, the manifest,
  `strings.xml`);
- NFC mechanism (reader mode, write/verify, capability inspection) stays distinct from what a
  record means to the app;
- no abstraction is introduced for the future split alone.

When the split runs it is its own investigation → design → plan → execution project with:
archaeology before mutation; durable checkpoint and rollback path; remote rename only after the
local split and builds are proven; both apps installable together with on-device dispatch proof
(note tag → noteNFC, asset tag → ServiceTag, link tag → ServiceTag, legacy tag → noteNFC, foreign
tag → nothing unsafe, no ambient scan enters write mode); data migration through the canonical
backup format (identity-preserving); explicit user-intent tag rewrite for any tag written before
the split; a distinct ServiceTag signing key, the original noteNFC key preserved, no private
material tracked; independent green CI for all three; docs under `docs/architecture/` for
archaeology, target and migration, with the existing design documents updated so no future
session mistakes the maintenance product for noteNFC.

## Cross-phase rules

- Every phase bumps `versionCode`, ships a migration test, and updates the backup importer to
  tolerate older exports.
- No phase removes legacy tag support.
- The `:core` test suite must stay pure JVM; any test needing Android goes to `:app`.
- A lane that changes `src/` without changing `tests/` is rejected (testing doc §5).
