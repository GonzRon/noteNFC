# Phase 2B-1 evidence — definition and profile editors, derived readings, schema v3, format 3

Branch `phase-2b1` from master `2b482c6`. Date 2026-09-15.

Phase 2B-1 is the first half of Phase 2B: the asset's readings and quick actions stop being
something only a seed template can write. A **Readings & actions** screen per asset lists both, an
editor behind each row creates, edits, reorders, archives and (when nothing points at it) deletes
it, and a reading can be **DERIVED** — computed from two of the asset's own readings on the same
entry, with `PERCENT_DROP` as the one formula that exists. Room goes to v3, the backup goes to
format 3, and the `ro_water` seed gains "Rejection". What 2B-1 deliberately is not is a form
builder: no conditional fields, no expressions, no chained derivations (spec §2).

**Read the status first (§5).** The JVM suites are green — `:core` 223, `:app` 127 — the
instrumented suite is **28 tests, 0 failures**, green on two consecutive runs on the owner's
Android 17 phone, and the v2 → v3 migration is device-proven over a real 2A install with its rows
intact (§4 row 1). Every device row is automated: `EditorsDeviceProofTest` is six tests, one per
scenario of spec §12, and there are no manual rows in this document. The first full run found one
defect and it was in a *test*: 2A's `anAssetWithNoTemplateCanBeSetUpLater` still expected the
`ro_water` template to seed three readings, which 2B-1's own "Rejection" row makes wrong (§3).

## 1. Exit criteria (spec §12) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | A custom definition and profile created in the editors drive a logged event and a current reading, on device, with no code change | `SaveDefinition` mints the key from the label with `slugify`, collects every `DefinitionProblem` at once, and freezes `key`/`kind`/`valueType` once measurements exist; `SaveProfile` validates each field against an ENTERED definition of the same asset and keeps child ids. `AssetSetupScreen` lists both with per-row Edit / Move / Archive / Delete; `DefinitionEditScreen` and `ProfileEditScreen` are the editors. Nothing in the entry form or the asset screen changes: the quick actions are `profiles.map { quickActionLabel(it) }` and the entry rows are built from `ValueType` alone. JVM: `DefinitionUseCasesTest` (17), `ProfileUseCasesTest` (8), `AssetSetupViewModelTest` (6), `DefinitionEditViewModelTest` (9), `ProfileEditViewModelTest` (11) | **PROVEN** on the phone: §4 row 3 is `EditorsDeviceProofTest.aCustomReadingAndActionDriveALoggedEntry` — a "Pressure" reading (psi, 0 decimals) and a "Pressure check" action requiring it, both typed into the editors on the phone, then logged as 42 through the quick action the new action produced, and read back off CURRENT READINGS and SERVICE RECORD |
| 2 | RO rejection appears from one TDS test and is never combined across events (test + device) | `Derived.compute` reads both sources off **one** `AssetEvent` and returns null if either is missing, if A is 0, if either source is archived, or if the result is not finite; `LatestReadings` walks the events newest-first and takes the first one that computes, so a newer partial entry is skipped rather than half-used. `SeedTemplates.roWater` carries "Rejection" (`%`, 1 decimal) over `tds_prefilter` and `tds_post_membrane`. JVM: `DerivedTest` (15), `LatestReadingsTest` (6), `SeedTemplatesTest` (7), `EventEntryViewModelTest` (12) | **PROVEN** on the phone: §4 rows 4–5 are `EditorsDeviceProofTest.rejectionComesFromOneTestAndIsNeverCombinedAcrossEvents` — 310 / 18 / 12 logged through the form puts **94.2** on the asset, and a second test carrying only pre-filter 300 makes 300 the current pre-filter reading while Rejection stays **94.2** |
| 3 | A v1 database upgrades through v2 to v3 with rows intact (JVM chain) and a real 2A install upgrades on device | `AppDatabase` v3 with `MIGRATION_2_3`, which recreates `measurement_definition` with `kind`, `formula`, `source_a_id`, `source_b_id`, copies every row forward as `kind = 'ENTERED'` with null sources, and adds the two source indexes. JVM: `Migration2To3Test`, `Migration1To3Test` (the whole chain), `Migration1To2Test`, plus `JournalDaoTest` (9) for the new columns and the self-referencing RESTRICT | **PROVEN, both halves.** JVM chain green; **and on the phone** (§4 row 1): the 2A debug build (versionCode 3) seeded by driving its own screens, then the 2B-1 build installed over it and opened from the launcher — `user_version` 2 → **3**, every row count identical, `kind = 'ENTERED'` and null sources on all five definitions, 18 → **20** indexes, no `FATAL EXCEPTION` |
| 4 | Format 2 backups import into format 3 | `BackupCodec.FORMAT_VERSION` is 3; the manifest names its version and the reader branches on it, so a format-2 file still decodes into a v3 store with `kind = ENTERED` everywhere. The codec validates the DERIVED invariants with the same `derivedProblems` the use cases use, and refuses a measurement written against a DERIVED definition. JVM: `BackupCodecTest` (39, incl. `formatTwoFileStillDecodes` and the format-3 round trip), `BackupUseCasesTest` (13), `:app` `RestoreProofTest` (4) | **PROVEN on the JVM, and the format-3 round trip is proven on the phone** (§4 row 7): export → wipe → import against the device's own Room store, all eight counts equal, "Rejection" back as a DERIVED row pointing at the same two sources, and the asset drawing 94.2 again from the imported rows |
| 5 | The §9 grep stays clean and no screen branches on a template key | The template-key grep (§9) finds the four keys **only** in `SeedTemplates.kt`, plus the word `"ups"` inside `AssetDetailScreen`'s icon-keyword list, which is a substring match on an asset's free-text category and picks a glyph. Both pickers iterate `SeedTemplates.all`. The editors know nothing about templates at all: they write the same seven tables the seeds write, through the same ports | **PROVEN** — §9 |

## 2. What shipped (by commit)

`git log --oneline 2b482c6..HEAD`:

- `2bab93b` — derived definitions: model, compute, latest readings, ro_water rejection.
  `DefinitionKind`, `DerivedFormula`, `DerivedSpec` on `MeasurementDefinition`, the pure
  `Derived.compute` with its same-event rule, `derivedProblems` as the one owner of the DERIVED
  shape invariants, `Reading.derivedValue`, and "Rejection" on the `ro_water` seed.
- `7759cbb` — definition and profile use cases. `SaveDefinition`, `ArchiveDefinition`,
  `DeleteDefinition`, `ReorderDefinitions`, `SaveProfile`, `ArchiveProfile`, `DeleteProfile`,
  `ReorderProfiles`, with collected validation and the prospective derived-graph check.
- `07b95f4` — refuse turning a profile field's definition derived. `DefinitionWouldBreakProfiles`,
  so an ENTERED → DERIVED change cannot leave a profile offering a field nobody can type into.
- `f44cbc4` — derived sources can't be meters. `DerivedProblem.SourceIsMeter`; spec §4 amended.
- `d2b90f0` — backup format 3: derived definitions, format 2 still imports.
- `d4c49ab` — Room v3: derived definition columns, migration chain. `MIGRATION_2_3`, the exported
  `3.json`, the two source indexes, and the DAO's ordered delete for the self-referencing RESTRICT.
- `4e4b76f` — derived readings on screen, entry form scrolls, archived handled. The derived block
  on the entry form (live), on event detail and in current readings, and the 2A focus bug fixed by
  making the entry form a `Column` + `verticalScroll` instead of a `LazyColumn`.
- `c585a29` — readings & actions screen, definition editor.
- `e41d308` — profile editor.
- (this commit) — phase 2b-1 evidence, editors device proof, versionCode 4. `EditorsDeviceProofTest`
  (six tests, one per spec §12 scenario), this document, the index row, the root README feature
  line, the two D4 amendments of spec §11, `versionCode` 4 / `versionName` 2.2, and one amended
  assertion in 2A's `JournalDeviceProofTest` — the `ro_water` template seeds four readings now, not
  three (§3). Test and document only: no production file changed.

## 3. Tests

### JVM (`./gradlew :core:test :app:testDebugUnitTest`)

| Module | Class | Tests |
|---|---|---|
| `:core` | `ApplyTemplateTest` | 6 |
| `:core` | `AssetUseCasesTest` | 6 |
| `:core` | `BackupCodecTest` | 39 |
| `:core` | `BackupUseCasesTest` | 13 |
| `:core` | `DeepLinkRouteTest` | 5 |
| `:core` | `DefinitionUseCasesTest` | 17 |
| `:core` | `DeleteLinkTest` | 2 |
| `:core` | `DerivedTest` | 15 |
| `:core` | `EventChronologyTest` | 4 |
| `:core` | `EventUseCasesTest` | 14 |
| `:core` | `LatestReadingsTest` | 6 |
| `:core` | `LinkLaunchPolicyTest` | 9 |
| `:core` | `LinkUseCasesTest` | 9 |
| `:core` | `MeasurementShapeTest` | 3 |
| `:core` | `NdefCodecTest` | 8 |
| `:core` | `NdefCodecV1Test` | 14 |
| `:core` | `OverwritePolicyTest` | 8 |
| `:core` | `ProfileUseCasesTest` | 8 |
| `:core` | `RangeStateTest` | 5 |
| `:core` | `ResolveTagTest` | 8 |
| `:core` | `SeedTemplatesTest` | 7 |
| `:core` | `TagBindingUseCasesTest` | 14 |
| `:core` | `TagRouteTest` | 3 |
| `:app` | `AppPrefsTest` | 4 |
| `:app` | `AssetDaoTest` | 5 |
| `:app` | `AssetSetupViewModelTest` | 6 |
| `:app` | `AssetViewModelsTest` | 11 |
| `:app` | `BackupViewModelTest` | 2 |
| `:app` | `ContrastTest` | 6 |
| `:app` | `DashboardViewModelTest` | 3 |
| `:app` | `DefinitionEditViewModelTest` | 9 |
| `:app` | `EventEntryViewModelTest` | 12 |
| `:app` | `ExternalLinkDaoTest` | 5 |
| `:app` | `JournalDaoTest` | 9 |
| `:app` | `JournalFormatTest` | 7 |
| `:app` | `Migration1To2Test` | 1 |
| `:app` | `Migration1To3Test` | 1 |
| `:app` | `Migration2To3Test` | 1 |
| `:app` | `NfcTagDaoTest` | 7 |
| `:app` | `ProfileEditViewModelTest` | 11 |
| `:app` | `RepositoryFlowsTest` | 3 |
| `:app` | `RestoreProofTest` | 4 |
| `:app` | `RoomRepositoriesTest` | 11 |
| `:app` | `TagUseCasesRoomTest` | 3 |
| `:app` | `TagWriteControllerTest` | 6 |

Totals from the JUnit XML: **`:core` 223 tests, 0 failures, 0 skipped**; **`:app` 127 tests, 0
failures, 0 skipped**. 2A finished at `:core` 172 / `:app` 88, so 2B-1 added **51 and 39**.

### Instrumented (`./gradlew :app:connectedDebugAndroidTest`)

Run on the owner's Android 17 phone, 2026-09-15. **28 tests, 0 failures, 0 skipped**, green twice
in a row.

```
ui.AppSmokeTest             bottomBarReachesScanAndShowsReadyToScan                    2.569s  pass
ui.AppSmokeTest             assetCanBeCreatedFromTheDashboardAndOpens                  2.282s  pass
ui.AppSmokeTest             secondNewAssetFormStartsBlank                              3.168s  pass
ui.AppSmokeTest             dashboardShowsTheBackupNudgeOnAFreshInstall                1.131s  pass
ui.AppSmokeTest             backupScreenRenders                                        1.343s  pass
ui.DeepLinkSmokeTest        malformedDeepLinkLandsOnDashboard                          1.015s  pass
ui.EditorsDeviceProofTest   aCustomReadingAndActionDriveALoggedEntry                   8.011s  pass
ui.EditorsDeviceProofTest   rejectionComesFromOneTestAndIsNeverCombinedAcrossEvents    6.631s  pass
ui.EditorsDeviceProofTest   archivingASourceEmptiesTheDerivedRowAndLeavesHistoryAlone  5.943s  pass
ui.EditorsDeviceProofTest   deletingAReadingWithDataAndADependentIsRefusedByName       4.500s  pass
ui.EditorsDeviceProofTest   formatThreeRoundTripBringsTheDerivedReadingBack            1.576s  pass
ui.EditorsDeviceProofTest   imeNextWalksFocusDownALongProfile                          4.340s  pass
ui.JournalDeviceProofTest   hotTubTemplateSeedsReadingsThenAWaterTestFillsThem         9.116s  pass
ui.JournalDeviceProofTest   editingTheWaterTestCorrectsItInPlace                       5.392s  pass
ui.JournalDeviceProofTest   aBackdatedTestSitsBelowAndLeavesTheCurrentReadingAlone     5.248s  pass
ui.JournalDeviceProofTest   upsLoadTestShowsNoTargetsAndPassedYes                      4.038s  pass
ui.JournalDeviceProofTest   mowerOilChangeRecordsTheMeterAndBothMaterials              4.108s  pass
ui.JournalDeviceProofTest   anAssetWithNoTemplateCanBeSetUpLater                       1.720s  pass
ui.JournalDeviceProofTest   backupRoundTripKeepsEveryCountAndTheAssetRendersAgain      4.851s  pass
ui.JournalDeviceProofTest   deletingTodaysTestFallsBackToTheOlderReading               6.578s  pass
ui.JournalSmokeTest         hotTubWaterTestShowsInRecordAndReadings                    3.390s  pass
ui.ShareActivitySmokeTest   sharedWebLinkShowsTheCard                                  0.888s  pass
ui.components.ComponentsSmokeTest  sectionHeaderShowsItsTitle                          0.775s  pass
ui.components.ComponentsSmokeTest  identityPlateShowsDashForBlankValues                0.787s  pass
ui.components.ComponentsSmokeTest  statusBadgeExposesItsLabelToAccessibility           0.775s  pass
ui.components.ComponentsSmokeTest  ledgerEntryShowsItsDateAndTitle                     0.829s  pass
ui.nav.NavigationSmokeTest  bottomBarSwitchesToScan                                    1.299s  pass
ui.nav.NavigationSmokeTest  dashboardIsTheStartDestination                             0.893s  pass
```

The twenty-two from 1C and 2A are unchanged but for one assertion (below). `EditorsDeviceProofTest`
is 2B-1's addition and is spec §12's scenario list turned into six tests — one per §4 row group,
each cold-starting the asset it needs through its `notenfc://asset/<id>` deep link and asserting
that row's Expected column on the screen. The mapping is in §4.

What the suite deliberately does **not** claim, stated as 2A stated its own limits:

- **A reading and its number are tied together by presence and count, not by adjacency.**
  `InstrumentRow` lays label, value and badge out in a plain `Row` with no semantics of its own, so
  no node owns all three. Which definition earns which number is pinned on the JVM by `DerivedTest`
  and `LatestReadingsTest`; the device test pins the formatted values and the exact count of em
  dashes on screen — which is what makes "the Rejection row went blank when its source was
  archived" a provable statement rather than an impression.
- **The asset, and the six-field profile of the focus walk, are created in-process.** Every
  scenario begins on a screen; the new-asset form is already covered by `AppSmokeTest`, and typing
  six definitions through the editor would be six copies of the row that already proves the editor.
- **The backup round trip is called in-process**, as in 2A: the file half of the backup screen is a
  SAF document picker, which belongs to the system and not to the app. What runs on the phone is
  the round trip against the device's own Room store.
- **Decoding a format-2 file is JVM-proven, not device-proven.** `BackupCodec.encode(…,
  formatVersion)` — the overload that seals a manifest claiming an older version — is `internal` to
  `:core` and cannot be reached from `androidTest`, so the device row is the format-3 round trip and
  `BackupCodecTest.formatTwoFileStillDecodes` is what pins the older file.

Corrections made while getting the suite green, recorded here rather than silently fixed:

- **A stale 2A assertion, and the automated row caught it.** The first full run was 27/28:
  `JournalDeviceProofTest.anAssetWithNoTemplateCanBeSetUpLater` expected six em dashes on an asset
  just set up from the `ro_water` template — three empty readings plus the plate's three blank
  cells — and found seven. The seventh is correct: 2B-1 gave that template a fourth row,
  "Rejection", which cannot compute on an asset with no events and so reads "—" like any other
  empty reading. The 2A test is amended in this commit to name all four rows and expect seven; no
  production file changed, because nothing was wrong with the app.
- **The suite cannot run behind a keyguard, and that is worth writing down.** An earlier attempt in
  this task ran 6 and failed 6, every one with `IllegalStateException: No compose hierarchies found
  in the app`. The phone was locked: `dumpsys trust` reported `deviceLocked=1`, `dumpsys window`
  reported `mCurrentFocus=… NotificationShade`, and `MainActivity` sat at `state=STOPPED` even
  after `am start` named it `mFocusedApp`. A stopped activity has no composition, so there is no
  semantics tree to assert on — and the 2A suite fails identically in the same state. The phone was
  unlocked and the suite then went 28/28 twice. The aborted attempt is reported rather than dropped.

## 4. Device proof (the owner's Android 17 phone)

No manual rows: every row below is either an instrumented test or an `adb`-driven check run by the
controller. Row 2 is destructive by construction — each test's `@Before` clears
`SharedPreferences("notenfc")` and empties the store — and AGP **uninstalls the app when
`connectedDebugAndroidTest` finishes**, which is 1C's and 2A's lesson repeated: after row 2 the
package is gone and must be reinstalled with `adb install -r` and launched once, because Android 17
delivers no NFC intent to a package in the *stopped* state. Row 1 therefore runs before row 2.

| # | Step | Expected | Result |
|---|---|---|---|
| 1 | v2 → v3 migration over a real 2A install: `adb install -r -d` the 2A debug build (versionCode 3), `pm clear`, drive its own screens to create an asset and log an entry, then `adb install -r` the 2B-1 debug build over it, open it from the launcher, and read the database back out through `run-as` | `user_version` 3; the asset, definitions, profiles, event, measurements and materials all still there; `kind = 'ENTERED'` and null sources on every definition; the two new source indexes present; no `FATAL EXCEPTION` | **PASS** (controller, `adb`, 2026-09-15). The 2A build was installed, its data cleared, and then **seeded through its own UI** — 2A's own `JournalDeviceProofTest.hotTubTemplateSeedsReadingsThenAWaterTestFillsThem` run against the 2A APK, which taps **Log water test** on a hot-tub asset and types five readings and two materials into the real form (10.5s, OK) — so the database the migration ran against was written by the 2A app through the 2A app's screens. Read back through `run-as` (the phone has no `sqlite3`, so the file is inspected off-device): `user_version` **2**, ten app tables, **18** indexes, `measurement_definition` with its fourteen 2A columns and no `kind`. The 2B-1 build was then installed over it and **opened from the launcher** (`am start` → `MainActivity` is `topResumedActivity`; `dumpsys package` reports `versionCode=4 versionName=2.2 stopped=false notLaunched=false`). After the upgrade: `user_version` **3**; counts **identical** — asset 1, measurement_definition 5, event_profile 2, profile_field 7, profile_consumable 8, asset_event 1, measurement 5, consumable_usage 2, nfc_tag 0, external_link 0; `measurement_definition` now carries `kind`, `formula`, `source_a_id` and `source_b_id`; all five rows read `kind = 'ENTERED'` with all three of those NULL; **20** indexes, the two new ones being `index_measurement_definition_source_a_id` and `index_measurement_definition_source_b_id`; every stored value survives exactly (pH 7.9 with the definition's empty unit, free chlorine 0.8 ppm, alkalinity 110 ppm, calcium hardness 200 ppm, water temperature 102 °F, Chlorine 1 oz, pH reducer 0.5 oz) and the asset still reads "Hot tub" with `template_key = 'hot_tub'`; `logcat` `FATAL EXCEPTION` count **0** |
| 2 | `adb shell svc power stayon usb` → `./gradlew :app:connectedDebugAndroidTest` → `svc power stayon false`; then **reinstall and launch** (the task uninstalls the app) | All instrumented tests pass (22 from 1C and 2A + the six of `EditorsDeviceProofTest`) | **PASS** 2026-09-15: **28/28**, 0 failures, 0 skipped, green on two consecutive runs — per-test lines in §3, along with the earlier keyguard-blocked attempt and the one stale 2A assertion this run found. Reinstall and launch done: `versionCode=4 versionName=2.2 stopped=false notLaunched=false`, `MainActivity` resumed, `FATAL EXCEPTION` count 0 |
| 3 | Plain asset (Template = None) → **Readings & actions** → **Add reading** (Label "Pressure", Unit "psi", Decimals 0) → Save → **Add action** (Name "Pressure check", field Pressure, required) → Save → back on the asset, **Log pressure check** → 42 → Save | The reading and the action appear on the setup screen; the asset grows a "Log pressure check" quick action; afterwards CURRENT READINGS shows Pressure 42 and SERVICE RECORD shows "Pressure check" | **PASS** (automated: `EditorsDeviceProofTest.aCustomReadingAndActionDriveALoggedEntry`, run on the owner's Android 17 phone 2026-09-15). Nothing here is seeded: the key `pressure` is minted from the label by `slugify`, the entry field is found by the `value-pressure` tag the definition's own key produces, and the quick action's wording comes from `quickActionLabel`. The field is switched from Optional to Required on the way, and the row says so |
| 4 | `ro_water` asset → **Log TDS test** 310 / 18 / 12 → Save | The asset shows a DERIVED "Rejection" row reading **94.2** | **PASS** (automated: `EditorsDeviceProofTest.rejectionComesFromOneTestAndIsNeverCombinedAcrossEvents`, same run). (310 − 18) / 310 × 100 = 94.1935…, drawn at the derived definition's one decimal |
| 5 | Same asset → edit the **TDS test** action so post-membrane and output are optional → log a second test with pre-filter 300 only | Pre-filter reads 300, and "Rejection" is **still 94.2** — never a number made of two events | **PASS** (automated: same test as row 4). The two fields are made optional **through the profile editor** on the phone, which is what makes the second entry possible at all; afterwards 300 is on screen, 310 is gone, Rejection is still 94.2, and the record lists two "TDS test" entries |
| 6 | Archive Pre-filter TDS from the setup screen's row overflow | The entry form has no `value-tds_prefilter` field; the Rejection row on the asset reads "—"; the earlier entry's detail still lists Pre-filter TDS 310 | **PASS** (automated: `EditorsDeviceProofTest.archivingASourceEmptiesTheDerivedRowAndLeavesHistoryAlone`, same run). The row's badge reads ARCHIVED, the asset then shows exactly four em dashes — the plate's three blank cells plus the blanked derived row — with "Pre-filter TDS" and "94.2" gone; the entry form has `value-tds_output` and no `value-tds_prefilter`; and the entry logged before the archive still lists Pre-filter TDS 310 under READINGS |
| 7 | Export a format-3 backup, wipe through the ports, import it back, then open the asset | Every table count equal; the derived definition and both its sources back; "Rejection" renders 94.2 again | **PASS** (automated: `EditorsDeviceProofTest.formatThreeRoundTripBringsTheDerivedReadingBack`, same run). **Said plainly: the export and the import are called in-process**, because the file half of the backup screen is a SAF document picker, which is the system's and not the app's. All **eight** counts are equal before and after, the wipe in between is asserted to have emptied the store, "Rejection" comes back as the single DERIVED row with `sourceA` = Pre-filter TDS and `sourceB` = Post-membrane TDS, no ENTERED row carries a spec, and the asset is then cold-started and draws 94.2 from the imported rows |
| 8 | Delete Post-membrane TDS from the setup screen | The delete is refused and the dialog names the entries, the derived reading and the action that point at it; nothing is written | **PASS** (automated: `EditorsDeviceProofTest.deletingAReadingWithDataAndADependentIsRefusedByName`, same run). Past the neutral confirmation, the refusal dialog reads **"Cannot delete this reading"** with **"1 reading logged"**, **"Used by Rejection"** and **"Offered by TDS test"**, and points at Archive instead. After OK the row is still there and still unarchived |
| 9 | Long profile (six NUMBER fields): type into the first field, press IME **Next** five times | The sixth field holds focus — the 2A scroll/focus bug | **PASS** (automated: `EditorsDeviceProofTest.imeNextWalksFocusDownALongProfile`, same run). All six `value-f*` fields exist before anything is scrolled, which a `LazyColumn` would not have managed, and the focus lands on each next field in turn until `value-f6` is focused. This is the 2A minor closed by making the entry form a `Column` + `verticalScroll` |

Rows 3–9 are the scenarios of spec §12, each one a test rather than a pair of hands, which is why
every one of them names the test that produced its result. Rows 1 and 2 were run by the controller
with `adb` and Gradle. Nothing in this table was run by the owner.

## 5. Status of the device proof

**Device-proven, with nothing left unrun.** Everything below ran on the owner's Android 17 phone on
2026-09-15.

**Automated and passing (§4 rows 3–9).** `EditorsDeviceProofTest` — six tests, listed in §3 —
drives spec §12's scenarios through the real screens: a reading and an action created in the
editors and then logged through the quick action they produce, RO rejection appearing from one TDS
test and refusing to combine with a second, an archived source leaving the entry form and blanking
what depends on it while its history stands, a refused delete naming every referrer, the format-3
round trip, and the IME focus walk down a six-field profile. With the twenty-two from 1C and 2A the
instrumented suite is **28 tests, 0 failures**, green on two consecutive runs.

**Run by the controller (§4 rows 1, 2).** The v2 → v3 migration over a real 2A install whose rows
were written by the 2A app through the 2A app's own screens, and the suite itself.

**One defect found, and it was in a test.** The first full run was 27/28:
`JournalDeviceProofTest.anAssetWithNoTemplateCanBeSetUpLater` still expected the `ro_water`
template to seed three readings, and 2B-1 gave it four. The seventh em dash on screen was the new
"Rejection" row reading "—" on an asset with no events, which is correct; the 2A test is amended in
this commit and no production file changed.

**An earlier attempt in this task was blocked by the phone's keyguard**, and §3 records it rather
than dropping it: a locked phone leaves every activity `state=STOPPED`, so no Compose test in this
repository can run — 2A's suite included. The phone was unlocked and the suite then went 28/28
twice.

What is complete on this machine:

- The final gate passes, including `:app:compileDebugAndroidTestKotlin` and a signed release build
  (§9).
- `:core` 223 and `:app` 127 JVM tests pass — 90 of them new in 2B-1 — covering the derived
  computation, the nine definition/profile use cases and their prospective graph checks, the
  migration chain, the new DAO columns and the self-referencing RESTRICT, format 3 and the
  four-table restore proof, the three new ViewModels and the entry form's derived rows.
- No template key appears anywhere outside `SeedTemplates.kt` (§9).

The honest statement of 2B-1 is **implemented, JVM-proven and device-proven**: the editors, the
same-event derivation, the archive rule, the refused delete, the format-3 round trip, the focus
walk and the v2 → v3 migration over a real 2A install have all run on real hardware. What it is
not is walked by a person's hands; the owner ran no checklist, by design.

Privacy: this document records no device serial, phone model, tag UID, note id or link, or home
path; paths are written relative to the repository and the phone is referred to throughout as
"the owner's Android 17 phone".

## 6. Rulings made during execution

From the task ledger, in plain words:

- **The in-memory profile fake gets a delete hook.** `InMemoryProfileRepository.delete` takes an
  optional `onDeleted: (ProfileId) -> Unit` that a test wires up to null out `profileId` on the
  event fake, so `deleteProfileLeavesEventsWithProfileCleared` can be written at all. Production
  relies on the schema's `SET NULL`; the hook is a test helper and nothing else.
- **Turning a definition DERIVED is refused while a profile still offers it.**
  `DefinitionWouldBreakProfiles(id, profileIds)` names the unarchived profiles that would be left
  holding a field nobody can type into. Checked *after* the derived-graph check, so a definition
  that is both a source and a field reports the derived break first.
- **`SaveProfile` keeps a field whose definition has since been archived.** "Unarchived" is a rule
  about *adding* a field, not about keeping one: the entry form already skips archived rows, so
  dropping it on save would silently rewrite someone's quick action the next time they renamed it.
- **A label with nothing slug-able in it is `BadKey`, not a guess.** `slugify` returns "" and the
  editor asks for a key rather than inventing one.
- **A derived source must not be a meter.** `DerivedProblem.SourceIsMeter`; spec §4 was amended to
  match. A meter counts up forever, so a percent drop between two of them is not a measurement of
  anything.
- **Consumable ids are trusted only when the profile already owns them.** An id from another
  profile is minted fresh instead of being adopted.
- **`kind` carries no SQL DEFAULT.** Spec §7's "DEFAULT 'ENTERED'" was descriptive: the migration's
  `SELECT` supplies the value and Room writes every column on every insert.
- **`Migration1To2Test` now runs the whole 1 → 3 chain**, because Room targets the compiled version;
  it still asserts the v2 facts it was written for.
- **`DefinitionDao.deleteAll` is a two-step transaction**, DERIVED rows first, because the table's
  source columns reference the table itself under RESTRICT. Any future bulk delete has to keep that
  order.
- **`measurement_definition` had two indexes in v2, not three**, and the migration matches `3.json`
  rather than the count the plan guessed.
- **A note-only entry shows no derived rows at all.** Event detail derives only for entries that
  recorded readings: a row that could never have a value on such an entry is noise, not
  information.
- **The definition editor's source list is loaded in `init`, not observed.** An editor cannot run
  behind an open form, so there is nothing to re-observe.
- **`slugify` is public in `:core`** so the editor's key preview and `SaveDefinition`'s minting are
  the same function and cannot drift.
- **Decimals, target and meter are shown for NUMBER only.** Reading spec §6 literally would have
  shown Decimals over a TEXT reading; the editor hides what a non-number has nothing to round.
- **Reorder is Move up / Move down in the row's own overflow.** A drag handle inside a scrolling
  column is a gesture fight; two menu items are unambiguous with one thumb.
- **The delete confirmation says only "This cannot be undone."** It used to promise "It has no
  data." before the use case had looked; whether the delete can happen at all is the use case's
  call, and the refusal dialog is where that is said.

## 7. Deferred

Minors from the task ledger — small, real, none of them blocking:

- The valid-before clause of the prospective derived check lets an already-broken derived row be
  broken further. Deliberate, and still untested; the test wants a restore fixture.
- A doubly-listed invalid profile field reports one reason rather than two.
- `DeleteDefinition` scans only the owning asset for derived referrers.
- The format-2 decode test serialises defaults rather than omitting the keys (house style since
  1A), and `BackupCodecTest.formatTwoRoundTripsAllSevenTables` keeps a name that is now stale — it
  covers ten.
- `Migration1To3Test`'s last comment overclaims what its refusal proves; the `profile_field` FK is
  not exercised after the chain; the DAO's ordered delete pieces are public; `deleteAll` keys on
  `kind` rather than on the source columns.
- The entry form's header still says TARGET over the derived block's badge column (cosmetic).
- A fast double **Move** drops the second tap.
- `ProfileProblem.BadField`'s reason string is shown verbatim, which is unreachable from the form
  but would read like a log line if it ever were.
- Carried from 2A and still open: positional consumable id preservation on an event update;
  case-insensitive BOOLEAN parsing; `AssetDetailState.bare` counting archived definitions;
  `AssetDetailViewModel`'s nine collaborators wanting a journal facade;
  `JournalDaoTest.eventUpsertReplacesChildrenAndObserveEmits` not proving the re-emission;
  `firstProblemText` ordering `BadConsumable` ahead of the row problems; the duplicated number
  formatting in `quantity()` and `plain()`; the `isError` tint outside D12 §5's measurement colour
  family; a rewritten tag leaving its old row bound; and the absent `observe(id)` port.

Deferred to 2B-2, from spec §2 (recorded so nothing is lost):

- Full asset fields — manufacturer, model, serial, purchase and in-service dates, price, vendor,
  location, warranty, `retired_on`.
- The category suggestion catalog with optional template hints used only at creation (D4 §4, no
  equipment-type key).
- Season windows on the asset.
- Retirement of the Scan tab (D12 §16).
- Parent/child composition with the semantics pinned in spec §2: one nullable parent, arbitrary
  depth, no cycles, reparenting allowed, a child is a full independent asset and stays
  independently scannable, a COMPONENTS section on the parent and "Part of &lt;parent&gt;" on the
  child's plate, no merged ledger or readings roll-up, delete refused while children exist,
  archiving a parent does not archive descendants, and an ordinary full backup with ids exact.

Never in 2B-1, and the boundary is the point (spec §2): conditional fields, nested sections,
formulas beyond the closed enum, chained derivations, scripting, custom widgets, per-profile
layouts, reset-from-template, and templates as editable objects.

## 8. What 2B-1 changed for later phases

- **Definitions and profiles are now user data, and the editor writes the same rows the seeds
  write.** There is no second path: `SaveDefinition` and `SaveProfile` go through the same ports
  `ApplyTemplate` does, so a template is just one caller. A later phase that wants to import
  definitions from anywhere has a use case to call, not a table to write.
- **A derived value is computed, never stored.** `Derived.compute` is pure and same-event; there is
  no `derived_value` column, no cache and no recomputation trigger, so an edited or deleted event
  can never leave a stale derived reading behind. Phase 3's schedules must read the meter the same
  way, from the events.
- **The formula set is closed, and that is a design position, not a stopgap.** D4 §7 now says
  "computed fields only as a closed formula enum with same-event semantics"; adding a formula is an
  enum member plus a branch in `Derived.compute` plus a spec line, and anything that wants an
  expression language has to argue against the "not a form builder" boundary first (spec §2).
- **`derivedProblems` is the one owner of the DERIVED shape.** The editor, `SaveDefinition` and the
  backup codec all ask it rather than each re-deciding what a valid derived row is; a file that
  violates it is refused before a row is written.
- **A definition's edit is checked *prospectively*.** `SaveDefinition` substitutes the candidate
  into the asset's definitions in memory and re-checks every other DERIVED row, refusing with
  `DefinitionWouldBreakDerived`; `DefinitionWouldBreakProfiles` does the same one table over. Any
  future field that other rows depend on should be guarded the same way, rather than by a
  constraint that fires after the write.
- **A delete is refused by name.** `DefinitionReferenced` carries the measurement count, the
  dependent derived ids and the profile ids, and the dialog reads them back as
  "1 reading logged / Used by Rejection / Offered by TDS test". Archive is the answer the refusal
  points at, which is why archive has to keep working everywhere.
- **Archived means "off the forms, still in history".** The entry form skips it, `LatestReadings`
  skips it, `Derived.compute` returns null when a source is archived, and event detail still shows
  the value it recorded. Anything that adds a new reader of definitions inherits that rule.
- **Backup format 3 is additive and format 2 still imports.** Same shape as 2A: the manifest names
  its version, the reader branches, the old meaning is preserved. Phase 4's attachments follow it.
- **Schema v3's derived columns are self-referential and RESTRICT-guarded**, so any bulk delete of
  definitions has to order DERIVED rows first. `DefinitionDao.deleteAll` does; so must the next one.

## 9. Final gate

```
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
    :app:compileDebugAndroidTestKotlin
```

→ **BUILD SUCCESSFUL**, 113 actionable tasks — 15s on a cold build cache, under a second on a
warm one.

Test totals from the JUnit XML: **`:core` 223 tests, 0 failures, 0 skipped**; **`:app` 127 tests, 0
failures, 0 skipped** (per-class breakdown in §3). Instrumented: **28 tests, 0 failures, 0
skipped** on the owner's Android 17 phone, twice in a row (§3, §4 row 2). The gate itself is
unchanged by the device-proof commit — `EditorsDeviceProofTest` is `androidTest` source, so it adds
nothing to either APK and the sizes below are the same bytes as before.

APK sizes:

```
-rw-r--r--. 13428133  app/build/outputs/apk/debug/app-debug.apk
-rw-r--r--.  9691946  app/build/outputs/apk/release/app-release.apk
```

(debug ≈ 13113 KiB, release ≈ 9465 KiB — up from 12873 / 9320 KiB in 2A; the derived model, the
nine use cases, the migration and the three new screens cost about 240 KiB debug and 145 KiB
release.) The release APK is signed with the real `noteNFC` release certificate, not a debug key:

```
Signer #1 certificate DN: CN=noteNFC, O=GonzRon
Signer #1 certificate SHA-256 digest: 0902d3b0f826381905c6d8254fdaf36756924d80da7c19b90a08930b33277a9f
```

`versionCode` 4, `versionName` `2.2`; the phone reports the same after the reinstall (§4 rows 1, 2).

No personal path or username anywhere in the tree:

```
git grep -nIiE '/home/[a-z]+|<the owner's username>|<the phone's model and codename>' -- . ':!.superpowers'
```

→ four hits, and none of them is a leak: `NfcDispatchActivity.kt` line 31 says "every pixel the app
draws", which the case-insensitive model-name pattern matches, and the other three are this document's
own §9 — the command quoting itself, plus the two lines of prose that name the pattern in order to
explain the first hit. No path, no username, no device serial and no phone model in any
source file, document or resource — including `EditorsDeviceProofTest`, which names no device at
all.

No asset kind outside the seed data:

```
grep -rn 'hot_tub\|power_equipment\|ro_water\|"ups"' app/src/main core/src/main --include=*.kt
```

→ four hits in `core/.../journal/SeedTemplates.kt` — the `key =` lines of four of the five
templates; the fifth, `generic`, is not in the pattern — plus one in `AssetDetailScreen.kt`
line 476, which is the word `"ups"` inside the icon-guessing keyword list
(`any("power", "battery", "electric", "ups", "meter", "gauge")`). That is a substring match on an
asset's free-text category, not a template key and not a code path: it picks a glyph and nothing
else. Neither template picker names a key — both iterate `SeedTemplates.all` — and the editors
know nothing about templates at all, so exit criterion 5 holds: no screen, table or branch knows
what a hot tub is. (`app/src/androidTest` names `hot_tub` and `ro_water` when it seeds the asset a
test needs, which is the test choosing a fixture, not the app branching on one.)
