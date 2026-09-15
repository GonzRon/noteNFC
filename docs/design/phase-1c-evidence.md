# Phase 1C evidence — Compose shell, asset/link UX, full restore proof (milestone M1)

Branch `phase-1c` from master `17d6297`. Date 2026-09-15.

Phase 1C replaces the interim View screens with the real app: the Apollo Service Binder theme
(D12), the three signature composables, a single-activity Navigation 3 shell, assets, links, scan,
write, the share card, backup and a settings shell. It is the last slice of milestone **M1**, so
this document also carries M1's exit criteria.

**Read the status first (§5).** Everything in §1 and §4 that needs a tag, a phone or a note app is
**pending the owner's phone session**: no phone was attached when this task ran, so no device row
below claims a result. The JVM and build evidence (§3, §9) is complete and was produced on this
machine.

## 1. Exit criteria (D7 §1C — milestone M1) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | A new tag written on phone X resolves on phone Y after restoring X's backup, **entirely through the UI** | Write path: `WriteTagScreen` + `TagWriteController` (read → confirm → write → verify read-back) over the 1B `TagWriter`; export/import path: the production `BackupScreen` (`ExportBackup`/`ImportBackupReplace` through SAF); resolve path: `ScanScreen` → `ResolveTag` → `TagResultSheet`. Identity preservation is JVM-proven by `:app` `RestoreProofTest` (2) and `:core` `BackupCodecTest` (23)/`BackupUseCasesTest` (8). "Phone Y" is stood in for by the debug **Wipe** button, which empties the store without touching the tag. Device: §4 rows 1–4 | **pending the owner's phone session** |
| 2 | The original share → write → scan → launch flow works with Joplin end to end and **returns to Joplin** after the write | `ShareActivity` runs in its own task (`excludeFromRecents`, no back-stack entry); `ShareFlow` hosts the ordinary `WriteTagScreen` inside that task and `onDone` calls `finish()`, so Done leaves the task and the previous app is what is underneath. `ShareActivitySmokeTest` proves the card renders from a real `EXTRA_TEXT` (instrumented, §3). Launch on scan without a sheet is R-7 (`ScanEvent.Launch` → `LinkLauncher`). Device: §4 rows 5–6 | **pending the owner's phone session** |
| 3 | Home shows the "no backup yet" nudge until the first export succeeds | `DashboardViewModel.needsBackup = lastBackupAt == null && (active assets or links exist)`; `BackupViewModel` calls `AppPrefs.markBackupExported` only on a successful export, and `DashboardScreen` re-reads the preference on every return via `LaunchedEffect { refresh() }`. JVM: `DashboardViewModelTest` (3), `AppPrefsTest` (4), `BackupViewModelTest` (2). Instrumented: `AppSmokeTest.dashboardShowsTheBackupNudgeOnAFreshInstall`. Device: §4 row 7 | **pending the owner's phone session** |
| 4 | All 1A/1B criteria still hold | 1A: `RestoreProofTest`, the DAO/repository suite and the unchanged backup format (§3). 1B: the payload codec, resolver, overwrite policy and writer are untouched by 1C — `TagWriteController` reaches them through the new `TagIo` seam, and `:core` still runs the whole 1B suite green. The interim writer and dispatch screens were deleted in `19042da` and replaced, so the 1B device rows must be re-run against the Compose screens. Device: §4 rows 8–10 | **pending the owner's phone session** |

## 2. What shipped (by commit)

- `5117530` — phase 1c plan: compose shell, theme, nav3, assets/links/scan/backup. The task plan for the whole slice.
- `8a6500f` — compose toolchain + apollo service binder theme, contrast-tested. Compose BOM, Navigation 3, `NoteNfcTheme`, `NoteNfcSemanticColors`, typography and shapes from D12, with `ContrastTest` asserting the ratios.
- `a8204d4` — flows on the read ports, `notenfc://` route parser, app prefs. `observeAll`/`observeForAsset`/`observeForLink` on the repositories, `DeepLinkRoute`, `AppPrefs`/`SharedPrefsStore`.
- `f5ab9d2` — identity plate, status badge, ledger entry and the small layout primitives. The three D12 signature composables plus `SectionHeader`, `QuietLine`, `LabelValue`, `ActionGrid`, `StatusBlock`.
- `8c5a85c` — plate eyebrow semibold, name 15sp, ledger title yields to its badge. G1 §1.1 corrections from review.
- `19042da` — single-activity nav3 shell, share host, dispatch trampoline; interim screens gone. `MainActivity` (`singleTask`), `NoteNfcApp` composable, `Route`, `BottomBar`, `ShareActivity`, `NfcDispatchActivity`; `ui.interim` deleted.
- `838d6a3` — assets: list, identity plate detail, edit, archive. `AssetsScreen`, `AssetDetailScreen`, `AssetEditScreen`, `UpdateAsset`/`ArchiveAsset`.
- `3c0f953` — backup icon, save runs in the viewmodel, honest empty state. `ic_backup` drawable, save moved to `viewModelScope` with an in-flight guard, "no active assets · N archived".
- `c808b49` — scan, tag result sheets, write flow with the 1b rules, share card, links. `ScanScreen`, `TagResultSheet`, `TagWriteController` + the `TagIo` seam, `ShareCardScreen`, `LinksScreen`/`LinkDetailScreen`, `DeleteLink`.
- `ee740c9` — dashboard with the backup nudge, backup screen, settings shell. `DashboardScreen`, the production `BackupScreen` (export + typed-`REPLACE` import), `SettingsScreen`.
- `3e67932` — nudge only when there's something to back up; cancellation isn't a failure. `needsBackup` gated on a non-empty store; `CancellationException` rethrown in the two `runCatching` blocks.
- (this commit) — phase 1c evidence, device smoke suite, readme. `AppSmokeTest`/`ShareActivitySmokeTest`, this document, the index row, the root README, the D12 §5 colour sync.

## 3. Tests

### JVM (`./gradlew :core:test :app:testDebugUnitTest`)

| Module | Class | Tests |
|---|---|---|
| `:core` | `BackupCodecTest` | 23 |
| `:core` | `DeepLinkRouteTest` | 5 |
| `:core` | `LinkLaunchPolicyTest` | 9 |
| `:core` | `NdefCodecTest` | 8 |
| `:core` | `NdefCodecV1Test` | 14 |
| `:core` | `OverwritePolicyTest` | 8 |
| `:core` | `TagRouteTest` | 3 |
| `:core` | `AssetUseCasesTest` | 6 |
| `:core` | `BackupUseCasesTest` | 8 |
| `:core` | `DeleteLinkTest` | 2 |
| `:core` | `LinkUseCasesTest` | 9 |
| `:core` | `ResolveTagTest` | 8 |
| `:core` | `TagBindingUseCasesTest` | 14 |
| `:app` | `RestoreProofTest` | 2 |
| `:app` | `AssetDaoTest` | 5 |
| `:app` | `ExternalLinkDaoTest` | 5 |
| `:app` | `NfcTagDaoTest` | 7 |
| `:app` | `RepositoryFlowsTest` | 3 |
| `:app` | `RoomRepositoriesTest` | 11 |
| `:app` | `TagUseCasesRoomTest` | 3 |
| `:app` | `AppPrefsTest` | 4 |
| `:app` | `AssetViewModelsTest` | 4 |
| `:app` | `BackupViewModelTest` | 2 |
| `:app` | `DashboardViewModelTest` | 3 |
| `:app` | `TagWriteControllerTest` | 6 |
| `:app` | `ContrastTest` | 6 |

Totals: **`:core` 117, `:app` 61** — 0 failures, 0 skipped (§9).

### Instrumented (`./gradlew :app:connectedDebugAndroidTest`)

| Class | Tests | What it proves |
|---|---|---|
| `ui.components.ComponentsSmokeTest` | 4 | `IdentityPlate` renders an em dash for a blank cell and mono for a tag id; `StatusBadge` exposes its label to accessibility; `LedgerEntry` shows date and title; `SectionHeader` renders |
| `ui.nav.NavigationSmokeTest` | 2 | Dashboard is the start destination; the bottom bar switches to Scan |
| `ui.AppSmokeTest` | 5 | Backup nudge on a fresh install; bottom bar → Scan shows **READY TO SCAN**; an asset created from the dashboard opens on its own plate; the production Backup screen renders; a malformed `notenfc://asset/nope` leaves the dashboard standing and says so |
| `ui.ShareActivitySmokeTest` | 1 | `ShareActivity` launched with an `EXTRA_TEXT` of a title line followed by `https://example.invalid/x` shows **WEB PAGE**, the title line and the URI in mono |

Compilation is gated on every build: `./gradlew :app:compileDebugAndroidTestKotlin` is part of the
final gate (§9) and passes. **Execution result: pending the owner's phone session** — no device was
attached when Task 8 ran (`adb devices` listed none), so the runner was never started. Nothing
below this line was observed:

```
(per-test results from ./gradlew :app:connectedDebugAndroidTest — to be pasted here)
```

Two notes for whoever runs it:

- `AppSmokeTest` clears `SharedPreferences("notenfc")` and empties the three tables in `@Before`,
  so the suite is destructive to whatever is on the phone. **Export a backup first**, or run it on
  a build that holds nothing worth keeping.
- `malformedDeepLinkLandsOnDashboard` hands the intent to the already-running `MainActivity` with
  `startActivity` rather than to a second `ActivityScenario`. `MainActivity` is `singleTask`: a
  second `ActivityScenario.launch` is routed by the platform to the same instance, so the scenario
  would wait for an activity that is never created. `startActivity` is the delivery path a deep
  link actually takes when the app is already open, and it reaches the same `safeRouteFrom` branch
  a cold start does. The cold-start case is covered by device row 11.

## 4. Device checklist (the owner's Android 17 phone; old `com.looseCannon.noteNFC` app uninstalled first — D13 §4)

Run in order: Android 17 does not deliver NFC intents to a package in the *stopped* state, so a
fresh `adb install` leaves noteNFC stopped until it is launched once. Rows 1–4 are the M1 restore
proof and must be run as one unbroken sequence.

| # | Step | Expected | Criterion | Result |
|---|---|---|---|---|
| 1 | Dashboard → **Add your first asset** → name it → Save → on the asset, **Write a tag** → hold a blank tag | Write screen reports the tag written and read back byte-identical; the asset's plate shows the tag id | 1 | pending |
| 2 | Dashboard → **Export now** (or Backup → **Export backup**) → save the zip somewhere off the phone | A `notenfc-backup-<stamp>.zip` is written; the nudge is gone when you come back to the dashboard | 1, 3 | pending |
| 3 | Debug launcher → **noteNFC Backup (debug)** → **Wipe** → return to the app → Scan → tap the tag from row 1 | Counts read `0 / 0 / 0`; the scan result sheet says the tag is an unregistered v1 tag and offers Bind / New asset — it does **not** resolve to the asset | 1 | pending |
| 4 | Backup → **Import (replace everything)** → pick the zip from row 2 → type `REPLACE` → Replace → Scan → tap the same tag | Import succeeds; the tag now resolves to the row-1 asset with its original id, and the asset's plate is as it was | 1 | pending |
| 5 | Joplin → a note → *Copy external link* → share to noteNFC | The share card shows **JOPLIN NOTE**, the note's title and the URI in mono | 2 | pending |
| 6 | On that card → **Write to a new tag** → hold a blank tag → Done | The tag is written and Done returns to Joplin, not into noteNFC. Then close noteNFC and tap the tag: Joplin opens the note directly, with no noteNFC screen in between | 2 | pending |
| 7 | Reinstall (or Wipe + clear app data), create one asset, look at the dashboard, then export | The nudge "No backup yet · Tags survive a phone change only if you have one." is present before the export and absent after it; it is also absent on a genuinely empty install (Task 7 ruling, §6) | 3 | pending |
| 8 | On the write screen, hold the tag from row 1 (which already holds a different noteNFC payload) | The confirmation names what is on the tag; **Keep it** leaves it unwritten and a later scan still resolves the original id | 4 (1B ex. 2) | pending |
| 9 | Swipe noteNFC from recents → tap the tag from row 1 | The app opens on the tag result / asset, through `NfcDispatchActivity` | 4 (1B ex. 3) | pending |
| 10 | Hold an old `md5_short` tag on the scan screen | Recognised as a legacy tag and offered Bind / Rewrite, never an error | 4 (D6) | pending |
| 11 | `adb shell am force-stop com.loosecannon.notenfc`, then `adb shell am start -a android.intent.action.VIEW -d "notenfc://asset/nope"` | The app cold-starts on the dashboard and shows "That link doesn't point at anything here." — no crash, no half-drawn screen | — | pending |
| 12 | `adb shell am start -a android.intent.action.VIEW -d "notenfc://asset/<id of the row-1 asset>"` | The asset's own screen opens on top of the dashboard; one back press returns to it | — | pending |
| 13 | Settings → Appearance → Dark, then Light, then System | The theme changes; the status colours stay the semantic ones in both (dynamic colour never recolours the semantic layer, D12) | — | pending |
| 14 | `adb shell am force-stop com.loosecannon.notenfc` → tap a written tag | Expected on Android 17: **no** dispatch until the app is launched once (platform rule, D3 §9). Record what happens; it is a result, not a defect. 1B could not capture this | — | pending |
| 15 | `./gradlew :app:connectedDebugAndroidTest` with the phone unlocked (`adb shell svc power stayon usb` first, `svc power stayon false` after) | 12 instrumented tests pass; paste the per-test lines into §3 | — | pending |

Result column: filled in by whoever runs the phone session (see §5). Row 15 wipes the phone's
noteNFC data — run it **after** rows 1–14 or before seeding anything worth keeping.

## 5. Status of the device proof

**No phone was attached when Task 8 ran.** `adb devices` listed nothing, so neither the
instrumented suite (§3) nor any checklist row (§4) was executed, and none of the four M1 exit
criteria is device-proven yet. Every criterion is implemented and JVM-proven at the seams that can
be tested without hardware; what is missing is the tag, the note app and the phone.

What is complete on this machine:

- The final gate passes, including `:app:compileDebugAndroidTestKotlin`, so the smoke suite is
  known to build against the shipped shell (§9).
- `:core` 117 and `:app` 61 JVM tests pass, covering the payload codec, the resolver, the backup
  format and its identity guarantees, the link policy, the asset use cases, the four new
  ViewModels, the write controller over a fake `TagIo`, and the D12 contrast ratios.
- The release APK contains exactly three exported activities of ours and no debug harness (§9).

What the phone session must produce: §4 rows 1–15, and the per-test block in §3. Until then the
honest statement of M1 is **implemented and JVM-proven, not device-proven**.

Privacy: this document records no device serial, phone model, tag UID, note link or any other
personal data, and paths are written relative to the repository. The phone is referred to
throughout as "the owner's Android 17 phone".

## 6. Rulings made during execution

Toolchain and process (from the pre-flight scan):

- Compose BOM 2026.08.00, activity-compose 1.13.0 and Navigation 3 1.1.7 were S1-verified;
  lifecycle 2.10.0 and androidx.test 1.7.0/1.7.0/1.3.0 were best-known guesses. Task 1 resolved
  every guessed version exactly as written — no substitutions were needed.
- No ViewModel-per-`NavEntry` scoping library in 1C: activity-scoped `viewModel(key = …)` is
  enough for these screens.
- The dynamic-colour parameter exists but has no settings UI (Phase 7). Changing the appearance
  mode recreates the activity.
- Compose instrumented tests compile on every task and run on the phone in Task 8 only.
- Screens in Tasks 5–7 were specified as contracts and layout specs rather than verbatim Compose
  listings; reviewers judged them against D12 and the G1 report.
- `androidx.compose.ui.test.junit4.v2.createComposeRule` / `createAndroidComposeRule` accepted as
  the test-rule flavour for the whole phase.

Design and implementation:

- The plate's category eyebrow is SemiBold (G1) via a local copy of `Eyebrow`; ordinary metadata
  labels stay Medium. The friendly name is `bodyLarge.copy(fontSize = 15.sp)` in
  `onSurfaceVariant` — G1 wins over the task brief. `LedgerEntry`'s title takes
  `weight(1f, fill = false)` so a long title cannot squeeze out its badge.
- Extra drawables for the D12 §5 glyphs are accepted rather than forcing the Material icon set.
- The light **DUE** container was nudged from `#F3C89A` to `#F3CA9D` (4.49:1 → 4.56:1) to clear
  4.5:1. **D12 §5 is synced to the shipped value in this commit**, with a note in the table.
- `androidx.profileinstaller.ProfileInstallReceiver` — merged in transitively, exported but
  guarded by `android.permission.DUMP` — is a receiver from a library, not one of our activities,
  and does not count against the three-exported-activities rule (§9).
- `replay = 1` on `MainActivity`'s deep-link and snackbar flows: `onCreate` emits before the first
  composition subscribes, so a cold-start deep link would otherwise be dropped. The flows are
  per-activity-instance, so a replayed value cannot outlive its intent.
- A `NONE`-format hand-off carries its reason in the `key` field, which `TagResultSheet` renders
  as prose.
- `MainActivity.routeFrom` is wrapped in `try`/`catch`: the launcher activity is exported, and on
  pre-33 devices reading a hostile bundle throws rather than returning null. A bad intent from
  someone else is treated as "no route", exactly as the NFC trampoline does.
- `CreateAsset` gained `description`/`notes` defaults. `AssetsScreen` is a root destination and has
  no back affordance.
- The Backup action uses a dedicated `ic_backup` drawable, not `Icons.Outlined.Share`.
- The asset form's save runs in `viewModelScope` with an in-flight guard, so a double tap or a
  rotation mid-save cannot create two assets or lock the screen.
- The assets empty state distinguishes "no assets" from "all of them are archived", and offers the
  chip in the second case.
- The Scan destination has no Cancel: it is a tab, and one back press leaves it.
- `TagResultSheet` is a bottom-anchored panel when it was reached through the NFC trampoline.
- Haptics on a successful scan or write are deferred (§7).
- "New asset…" from the bind picker leaves the flow; accepted for 1C, parked for Phase 2.
- The backup nudge appears only when there is at least one active asset or link — an empty install
  has nothing to lose, and shows the two empty-state calls to action instead. A store holding only
  archived assets shows no nudge; archived rows are covered by the same export whenever any active
  item exists, and this is revisited in Phase 2.
- `CancellationException` is rethrown from the two `runCatching` blocks that wrap suspending work,
  so a cancelled job is not reported to the user as a failure.
- Settings shows a snackbar when `LinkLauncher.open` returns false (no browser on the phone).
- `DashboardScreen` keeps its unused `onOpenLinks` parameter: links reach the dashboard with the
  ledger in Phase 3 and the route already knows how to answer it.
- `NavigationSmokeTest` was allowed to change the title it asserts when the dashboard's app bar
  became the app's name.
- Import demands the word `REPLACE` typed into the dialog before it runs (R-9); there is no Wipe
  on the production Backup screen — that stays the debug harness's job.

Process note recorded during Task 7: rulings must be passed to the implementer in the dispatch,
not only to the reviewer.

## 7. Deferred to Phase 2

From D12/D7, deliberately out of 1C scope:

- The **measurement sheet** (D12 §9 instrument rows: value, unit, range, explicit LOW / IN RANGE /
  HIGH) — Phase 2, with the event journal.
- **Ledger events** on the asset screen: `LedgerEntry` exists and is tested, but nothing writes
  events yet, so the asset screen has no Service Record section.
- The **serial field** on the identity plate — the plate renders the cells it is given; the asset
  model gains serial/model fields with the full asset schema in Phase 2.
- **Hero reading** on the asset screen (the single most important current measurement) — needs
  measurements.
- **Haptics** on a successful scan and a successful write.
- Retiring a bound `LEGACY_MD5` row when its tag is rewritten in v1 (carried from 1B).
- `lastOpenedAt` display on a link.
- targetSdk 37 and `DISPATCH_NFC_MESSAGE` (Phase 7).

Parked minors from the task ledger — small, real, none of them blocking:

- `@Preview` still lives in `src/main`'s `Theme.kt` alongside `Previews.kt`; function-scope
  `@Suppress` on `DashboardScreen`; the preview `uiMode` flag pattern.
- `LocalNoteNfcSemanticColors` has a light default rather than an `error()` default.
- Raw brick hexes in `Color.kt` instead of named constants; `okIsNotGreen` is asserted for light
  only.
- Check on the phone that `StatusBadge` is not double-announced by TalkBack (content description
  plus text).
- `ActionButton`'s `modifier` parameter position.
- A `Toast` in the NFC trampoline, and the toast + snackbar double message on a browserless phone.
- The `RETIRED` status renders as a raw enum name in a couple of places; the archived chip label
  vs `RETIRED` wording.
- `observeAll` is subscribed twice in the asset detail ViewModel; the edit form can clobber a
  prefill if the flow emits late.
- `FirstRun`'s button `Row` does not wrap at the largest font scale.
- Preferences are read on the main thread (one `SharedPreferences` lookup per emission).
- Once the first export has happened the nudge is gone and the Backup screen is reachable only
  from an asset's backup action — there is no route to it from an empty dashboard or from
  Settings. Settings is the obvious home for it in Phase 2.
- Write flow: add tests for the verify-mismatch path and for clearing consent when a different tag
  is presented; show a "Writing…" state while a confirmed write is in flight; `ShareCard` hardcodes
  `confirmedOther = true` (the flag should ride on the `Card` state); a help glyph on the
  unregistered-tag sheet; the `busy` flag is a non-atomic test-and-set; `confirmedOverwrite` is not
  nulled in `finishWrite`.
- Carried from 1B and still open: `LinkLaunchPolicy.extractUri` does not exclude curly quotes and
  can trim a legitimate trailing `)`; `BindTag` does not trim `label` while `ProvisionTag` does;
  `locked = false` is silent when `canMakeReadOnly()` is false.

## 8. What 1C changed for later phases

- **Navigation is a structure now, not a screen list.** `Route` is a `NavKey` sealed hierarchy and
  `NoteNfcApp` is a single `NavDisplay` over one back stack. A new destination is one `Route` and
  one `entry<…>` block; a new top-level tab is one entry in `TopLevelRoutes`. Phase 3's dashboard,
  schedule editor and health screen plug in here without touching an activity. The top-level switch
  clears the stack before pushing, so a tab is always a root.
- **Intents never reach a screen.** `MainActivity` translates intents into routes and emits them on
  a `replay = 1` flow; nothing below the activity knows what an `Intent` is. `notenfc://schedule`
  in Phase 3 is a `DeepLinkRoute` case and a `Route`, nothing more.
- **The `NONE` hand-off contract.** When the trampoline or the resolver cannot name a payload it
  hands over the format string `"NONE"` with the human-readable reason in the `key` field, and
  `TagResultSheet` renders that reason as prose. Anything that produces tag results later must
  honour the same contract rather than inventing a second failure channel.
- **The `TagIo` seam.** `TagWriteController` depends on `TagIo`, and `RealTagIo` wraps the 1B
  `TagWriter`. The whole write state machine is therefore testable on the JVM
  (`TagWriteControllerTest`), and Phase 2+ can add write paths without another NFC-shaped
  integration test.
- **Android 17 stopped state.** A freshly installed package receives no NFC intents until it has
  been launched once; swiping from recents is *not* that state. Every device checklist from here on
  must launch the app before its close-the-app tap rows, and CI-style "install then tap" scripts
  will silently do nothing. 1B could not capture the behaviour (row 13 there); §4 row 14 tries
  again.
- **Semantic colours are a separate layer.** `NoteNfcSemanticColors` sits beside the M3 scheme and
  is never derived from it, so dynamic colour (Phase 7) can recolour the app without touching what
  OVERDUE looks like. `ContrastTest` is the guard; add a row to it with every new state.
- **Preferences are device-local by design.** `AppPrefs` is not in the backup: a restored phone has
  no `lastBackupAt`, so it nudges for a backup of its own. That is intended, not an omission.

## 9. Final gate

```
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
    :app:compileDebugAndroidTestKotlin
```

→ **BUILD SUCCESSFUL in 12s** (113 actionable tasks: 61 executed, 51 from cache, 1 up-to-date).

Test totals from the JUnit XML: **`:core` 117 tests, 0 failures, 0 skipped**; **`:app` 61 tests, 0
failures, 0 skipped** (per-class breakdown in §3).

APK sizes:

```
-rw-r--r--. 12870183  app/build/outputs/apk/debug/app-debug.apk
-rw-r--r--.  9343057  app/build/outputs/apk/release/app-release.apk
```

(debug ≈ 12568 KiB, release ≈ 9124 KiB — up from 3246/2416 KiB in 1B; the difference is Compose,
Material 3 and Navigation 3, and the debug build additionally carries the Compose tooling and test
manifest.) `~/.config/notenfc/keystore.properties` exists on this machine and the release APK is
signed with the real `noteNFC` release certificate, not a debug key:

```
Signer #1 certificate DN: CN=noteNFC, O=GonzRon
Signer #1 certificate SHA-256 digest: 0902d3b0f826381905c6d8254fdaf36756924d80da7c19b90a08930b33277a9f
```

No raw colours or dead interim code outside the theme:

```
grep -rn "ui.interim\|TECH_DISCOVERED\|Color.Red\|Color.Green\|Color(0xFF" \
    app/src/main/kotlin --include=*.kt | grep -v "ui/theme/"
```

→ no output. Every `Color(0xFF…)` literal in the app lives in `ui/theme/Color.kt` and
`ui/theme/SemanticColors.kt`; nothing references the deleted `ui.interim` package; there is no
`TECH_DISCOVERED` catch-all filter.

Exported components in the **release** merged manifest
(`app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml`) —
exactly three activities, all ours:

| Component | Kind | Why it is exported |
|---|---|---|
| `com.loosecannon.notenfc.MainActivity` | activity | Launcher + the `notenfc://asset\|link\|tag` deep links |
| `com.loosecannon.notenfc.ShareActivity` | activity | `ACTION_SEND` `text/plain` from a notes app |
| `com.loosecannon.notenfc.nfc.NfcDispatchActivity` | activity | The two `NDEF_DISCOVERED` filters (`:tag`, `:md5_short`) |
| `androidx.profileinstaller.ProfileInstallReceiver` | receiver | Merged in transitively from a library; permission-guarded with `android.permission.DUMP`. Not an activity and not ours — see the §6 ruling |

`DebugBackupActivity` and its manifest entry appear **zero** times in the release manifest: the
debug harness ships only in debug builds, as in 1A.
