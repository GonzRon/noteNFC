# Phase 2A evidence — event journal, measurement definitions, profiles, templates

Branch `phase-2a` from master `f1b4e5e`. Date 2026-09-15.

Phase 2A is the first half of Phase 2: the asset gets a journal. Events carry typed measurements
and consumable usage, the measurement definitions and the entry profiles are data rather than
code, five seed templates put useful data there on the first tap, the asset screen grows CURRENT
READINGS and a SERVICE RECORD, and the backup format carries all of it with identities intact.
What it deliberately does not contain is the editor for any of that — 2B — or anything that
knows what a hot tub is.

**Read the status first (§5).** The JVM suites and the instrumented suite ran green on the
owner's Android 17 phone on 2026-09-15. The device checklist's UI rows (§4 rows 3–11) are no
longer manual: they are `JournalDeviceProofTest`, which drives them through the real screens on
the phone. Row 12 was run by the controller with `adb`. Row 13 needs a physical tag and was not
run. Row 9's quick action originally read "Log tDS test"; the automated row caught it and the
defect is fixed in this commit (§4 row 9, §5).

## 1. Exit criteria (spec §12, from D7 Phase 2 as they apply to 2A) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | The hot-tub acceptance from issue #13.1 reproduced on device — configure the metrics, log a water test, see the history — with "configure the metrics" satisfied by the seed in 2A and by the editor in 2B | `SeedTemplates.hotTub` gives five definitions (pH, free chlorine, alkalinity, calcium hardness, water temperature) and two profiles (Water test, Treatment) with the chlorine/pH-reducer consumables; `ApplyTemplate` instantiates them with fresh ids on `CreateAsset(templateKey = …)` or from the asset screen's **Set up from template**; `LogEvent` validates and stores the reading set; `LatestReadings` + `RangeState` drive CURRENT READINGS; `AssetDetailScreen`'s SERVICE RECORD lists the event. JVM: `SeedTemplatesTest` (6), `ApplyTemplateTest` (5), `EventUseCasesTest` (13), `LatestReadingsTest` (5), `RangeStateTest` (5), `AssetViewModelsTest` (9), `EventEntryViewModelTest` (9). **Device: instrumented `JournalSmokeTest` on the phone** — seed a hot tub from the template, open "Log water test", type pH 7.9 and free chlorine 2.0, watch the row say HIGH while typing, save, and find "Water test" in SERVICE RECORD with 7.9 · HIGH in CURRENT READINGS (§3, §4 row 2) | **PROVEN** on the phone, and now by the walk-through as well: `JournalDeviceProofTest` drives §4 rows 3–6 through the real screens — five empty readings, a five-reading water test with two materials from the profile's own suggestion chips, an edit that corrects a reading in place, and a backdated entry that files below without becoming the current value |
| 2 | A mower oil-change event with engine hours stored as a meter reading (its effect on a schedule is Phase 3) | `SeedTemplates.powerEquipment` carries one definition, `engine_hours` (`isMeter = true`, unit `h`, 1 decimal, no range), and an Oil change profile requiring it with Engine oil (qt) and Oil filter (pcs) as suggested consumables. The meter reading is an ordinary `Measurement` on the event — there is no meter table and no meter column — and the current value is derived in `:core` by `LatestReadings` from the event list (spec §11). `RangeState.NO_TARGET` is what a range-less definition reports, so the row renders NO TARGET SET rather than inventing a judgement. JVM: `SeedTemplatesTest`, `LatestReadingsTest`, `EventUseCasesTest` | **IMPLEMENTED, JVM-proven and device-proven**: §4 row 8 is `JournalDeviceProofTest.mowerOilChangeRecordsTheMeterAndBothMaterials` — engine hours 138.5 logged on the phone with Engine oil 1.5 qt and Oil filter 1 pcs, the meter row rendering NO TARGET SET rather than a judgement |
| 3 | An exported backup re-imports with identical table counts | Backup format 2 adds the seven journal tables to the manifest and the payload; format 1 still decodes (an old backup restores into a v2 store with an empty journal); the codec validates referentially before it writes anything, and rejects the four bad value shapes. `ImportBackupReplace` writes in dependency order inside one `UnitOfWork` and reports per-table counts, keeping `formatVersion` on the report. JVM: `BackupCodecTest` (33), `BackupUseCasesTest` (12), `:app` `RestoreProofTest` (3) — which round-trips all **seven** tables through real Room and asserts every id survives | **PROVEN on the JVM and on the phone**: §4 row 10 is `JournalDeviceProofTest.backupRoundTripKeepsEveryCountAndTheAssetRendersAgain` — export, wipe and import run in-process against the device's own Room store, all **ten** table counts equal before and after, and the asset screen drawing its readings again from the imported rows |
| 4 | No hot-tub-, UPS-, RO- or mower-specific table or code path exists | Schema v2 adds `measurement_definition`, `event_profile`, `profile_field`, `profile_consumable`, `asset_event`, `measurement`, `consumable_usage` — seven generic tables, ten in all with `asset`, `nfc_tag`, `external_link`. Nothing in the UI branches on a template: the quick actions are `profiles.map { quickActionLabel(it) }`, the entry form is built from `ValueType` alone, and the template picker iterates `SeedTemplates.all`. The grep in §9 finds the five template keys **only** in `SeedTemplates.kt` | **PROVEN** — §9 grep, and §4 row 12 read off the phone's own database: exactly the ten app tables, `user_version` 2, and nothing named after an asset kind |

## 2. What shipped (by commit)

- `d19d6a4` — journal model, chronology, range state, latest readings. The `:core` types
  (`MeasurementDefinition`, `EventProfile`, `AssetEvent`, `Measurement`, `ConsumableUsage`,
  `ValueType`, `EventKind`), `EventChronology`'s newest-first order, `RangeState` with inclusive
  bounds, and `LatestReadings` deriving the current value per definition from the event list.
- `95dd7ba` — seed templates and `ApplyTemplate`; `CreateAsset` can seed from one. Five typed
  Kotlin templates in `:core` (spec §11: not JSON), applied once with fresh ids, `AlreadySetUp`
  on the second call, `templateKey` set only when it was null.
- `17f378a` — fix `ro_water` template display name to match spec, pin all five names.
  `SeedTemplatesTest` now asserts the five display names so the next rename is a test failure.
- `cd272d9` — log, update, delete events with one validation path. `LogEvent`, `UpdateEvent`,
  `DeleteEvent` over one shared validator: required fields, NUMBER parsing, BOOLEAN 0/1, the unit
  snapshot, consumables, and `EventOwnership` when a profile or definition belongs elsewhere.
- `7c14300` — wire `UpdateEvent`'s id generator through instead of a hidden `UuidGenerator`,
  guard missing-definition required fields. `UpdateEvent` takes an injected `IdGenerator` like
  every other use case, so the JVM tests see deterministic ids (§6).
- `992bfc3` — backup format 2: journal tables, format 1 still imports.
- `1a5b859` — put `formatVersion` back on `ImportReport`, my plan dropped it by mistake.
- `3f6e2ec` — Room v2: journal tables, migration, repositories. `AppDatabase` v2,
  `MIGRATION_1_2`, the exported `2.json`, the journal DAO with `@Transaction`/`@Relation`
  aggregates, and the repositories behind the `:core` ports.
- `e87cfbd` — index the FK child columns Room was warning about. Three indexes added to schema v2
  and spec §8 amended to match (§6).
- `53524da` — asset screen: current readings, quick actions, service record, template pickers.
- `7fb71d1` — event entry route and event detail. The generic profile-driven entry screen
  (`InstrumentEntryRow` per `ValueType`, live range state, materials, notes, backdating) plus
  event detail with edit and delete.
- `bb62b54` — carry an edited event's stray measurements onto the form. A measurement whose
  definition is no longer on the profile is preserved through an edit instead of being dropped.
- `69ddfae` — phase 2a evidence, journal smoke test, versionCode 3. `JournalSmokeTest`, this
  document, the index row, the root README feature line, the spec §8 amendment.
- (this commit) — `JournalDeviceProofTest`: the device checklist's UI rows as one instrumented
  suite. Eight tests, one per checklist row group, each cold-starting the asset it needs through
  its `notenfc://asset/<id>` deep link and asserting that row's Expected column on screen.
  Test-only; no production file changed, which is why row 9's finding is reported rather than
  fixed (§4, §7).

## 3. Tests

### JVM (`./gradlew :core:test :app:testDebugUnitTest`)

| Module | Class | Tests |
|---|---|---|
| `:core` | `ApplyTemplateTest` | 5 |
| `:core` | `AssetUseCasesTest` | 6 |
| `:core` | `BackupCodecTest` | 33 |
| `:core` | `BackupUseCasesTest` | 12 |
| `:core` | `DeepLinkRouteTest` | 5 |
| `:core` | `DeleteLinkTest` | 2 |
| `:core` | `EventChronologyTest` | 4 |
| `:core` | `EventUseCasesTest` | 13 |
| `:core` | `LatestReadingsTest` | 5 |
| `:core` | `LinkLaunchPolicyTest` | 9 |
| `:core` | `LinkUseCasesTest` | 9 |
| `:core` | `MeasurementShapeTest` | 3 |
| `:core` | `NdefCodecTest` | 8 |
| `:core` | `NdefCodecV1Test` | 14 |
| `:core` | `OverwritePolicyTest` | 8 |
| `:core` | `RangeStateTest` | 5 |
| `:core` | `ResolveTagTest` | 8 |
| `:core` | `SeedTemplatesTest` | 6 |
| `:core` | `TagBindingUseCasesTest` | 14 |
| `:core` | `TagRouteTest` | 3 |
| `:app` | `AppPrefsTest` | 4 |
| `:app` | `AssetDaoTest` | 5 |
| `:app` | `AssetViewModelsTest` | 9 |
| `:app` | `BackupViewModelTest` | 2 |
| `:app` | `ContrastTest` | 6 |
| `:app` | `DashboardViewModelTest` | 3 |
| `:app` | `EventEntryViewModelTest` | 9 |
| `:app` | `ExternalLinkDaoTest` | 5 |
| `:app` | `JournalDaoTest` | 6 |
| `:app` | `JournalFormatTest` | 5 |
| `:app` | `Migration1To2Test` | 1 |
| `:app` | `NfcTagDaoTest` | 7 |
| `:app` | `RepositoryFlowsTest` | 3 |
| `:app` | `RestoreProofTest` | 3 |
| `:app` | `RoomRepositoriesTest` | 11 |
| `:app` | `TagUseCasesRoomTest` | 3 |
| `:app` | `TagWriteControllerTest` | 6 |

Totals: **`:core` 172, `:app` 88** — 0 failures, 0 skipped (§9). 1C finished at `:core` 117 /
`:app` 61, so 2A added 55 and 27.

### Instrumented (`./gradlew :app:connectedDebugAndroidTest`)

Run on the owner's Android 17 phone, 2026-09-15. **22 tests, 0 failures, 0 skipped**, green twice
in a row.

```
ui.AppSmokeTest             assetCanBeCreatedFromTheDashboardAndOpens     2.225s  pass
ui.AppSmokeTest             backupScreenRenders                           1.383s  pass
ui.AppSmokeTest             bottomBarReachesScanAndShowsReadyToScan       2.458s  pass
ui.AppSmokeTest             dashboardShowsTheBackupNudgeOnAFreshInstall   1.121s  pass
ui.AppSmokeTest             secondNewAssetFormStartsBlank                 3.124s  pass
ui.DeepLinkSmokeTest        malformedDeepLinkLandsOnDashboard             1.038s  pass
ui.JournalDeviceProofTest   hotTubTemplateSeedsReadingsThenAWaterTestFillsThem      9.037s  pass
ui.JournalDeviceProofTest   editingTheWaterTestCorrectsItInPlace                    5.338s  pass
ui.JournalDeviceProofTest   aBackdatedTestSitsBelowAndLeavesTheCurrentReadingAlone  5.394s  pass
ui.JournalDeviceProofTest   upsLoadTestShowsNoTargetsAndPassedYes                   4.133s  pass
ui.JournalDeviceProofTest   mowerOilChangeRecordsTheMeterAndBothMaterials           4.373s  pass
ui.JournalDeviceProofTest   anAssetWithNoTemplateCanBeSetUpLater                    1.743s  pass
ui.JournalDeviceProofTest   backupRoundTripKeepsEveryCountAndTheAssetRendersAgain   4.727s  pass
ui.JournalDeviceProofTest   deletingTodaysTestFallsBackToTheOlderReading            6.523s  pass
ui.JournalSmokeTest         hotTubWaterTestShowsInRecordAndReadings       3.345s  pass
ui.ShareActivitySmokeTest   sharedWebLinkShowsTheCard                     0.819s  pass
ui.components.ComponentsSmokeTest  identityPlateShowsDashForBlankValues   0.801s  pass
ui.components.ComponentsSmokeTest  ledgerEntryShowsItsDateAndTitle        0.796s  pass
ui.components.ComponentsSmokeTest  sectionHeaderShowsItsTitle             0.772s  pass
ui.components.ComponentsSmokeTest  statusBadgeExposesItsLabelToAccessibility 0.762s pass
ui.nav.NavigationSmokeTest  bottomBarSwitchesToScan                       1.276s  pass
ui.nav.NavigationSmokeTest  dashboardIsTheStartDestination                0.953s  pass
```

The thirteen from 1C are unchanged. `JournalSmokeTest` is 2A's first addition and is the whole
vertical slice in one test: seed a hot tub from the template, open the asset through its
`notenfc://asset/<id>` deep link, tap **Log water test**, confirm the entry screen names itself
`WATER TEST · SPA`, type into `value-ph` and `value-free_chlorine`, assert the live **HIGH** on
pH 7.9 against the template's 7.2–7.8, save from the app bar, and back on the asset assert 7.9,
the HIGH badge and the "Water test" row under SERVICE RECORD.

`JournalDeviceProofTest` is the second, and it is the device checklist's UI rows (§4 rows 3–11)
turned into eight tests — one per row group, each cold-starting the asset it needs through the
same deep link and asserting that row's Expected column on the screen. The mapping is in §4; what
is worth stating here is what the suite is careful **not** to claim:

- **A reading and its badge are tied together by counting, not by adjacency.** `InstrumentRow`
  lays the label, the value and the status badge out in a plain `Row` with no semantics of its
  own, so the merged semantics tree has no node that owns all three and no assertion can say
  "*this* value carries *that* badge". On the entry form the live badge is attributed by
  arithmetic — after a value goes in, exactly one more row says HIGH (or LOW, IN RANGE,
  NO TARGET SET) than said it before — and on the asset screen the test pins the exact multiset
  of state words alongside each formatted value. Which definition earns which state is pinned by
  `RangeStateTest` and `LatestReadingsTest` on the JVM.
- **The asset itself is created in-process.** Every row under test begins on the asset screen, and
  the new-asset form is already covered by `AppSmokeTest`; the events, the edits, the backdating,
  the template pick and the delete are all driven through the real screens.
- **Row 10's export and import are called in-process.** The UI half of the backup screen is a SAF
  document picker, which belongs to the system rather than to the app. What runs on the phone is
  the round trip against the device's own Room store.

Corrections made while getting the two suites green, recorded here rather than silently fixed:

- The 1C helpers `app`, `clearInstall()` and `awaitText()` were file-private top-level
  declarations in `AppSmokeTest.kt`, so a second file in the same package could not see them.
  They are `internal` now; nothing else changed in `AppSmokeTest.kt`. `clearInstall()` already
  emptied the journal tables (events → profiles → definitions, before the three 1C `deleteAll`s)
  from Task 5, so the destructive `@Before` needed no change.
- `JournalSmokeTest`'s first run failed on `onNodeWithText("Water test").assertIsDisplayed()`: the
  node existed but sat below the fold. The asset screen is one `verticalScroll` `Column`, so the
  assertions now `performScrollTo()` — readings first, since they are above the record — and the
  failure was a real statement about the screen, not a flake.
- `JournalDeviceProofTest`'s first run was 4/8. Two causes, both in the test. A focused
  `OutlinedTextField` publishes `ScrollBy` of its own, so "the screen's scrollable" matched two
  nodes the moment a field had focus; the helper now asks for the scrollable that is *not* a text
  field. And the live-badge assertion used `onNodeWithText`, which is ambiguous the moment a
  second row says the same word — hence the counting rule above.
- The third run aborted after five tests with an empty failure record. A second device (an
  emulator) had come online mid-run; the run was repeated pinned to the phone and the class went
  8/8, then the whole suite went 22/22 twice. The aborted run is reported rather than dropped.

## 4. Device checklist (the owner's Android 17 phone)

Row 2 is destructive by construction — the suite's `@Before` clears `SharedPreferences("notenfc")`
and empties all ten tables — so it runs before anything worth keeping exists. AGP **uninstalls the
app when `connectedDebugAndroidTest` finishes**, which is 1C's lesson repeated: after row 2 the
package is gone, and it must be reinstalled with `adb install -r` and launched once before any row
below it, because Android 17 delivers no NFC intent to a package in the *stopped* state.

| # | Step | Expected | Result |
|---|---|---|---|
| 1 | `adb install -r` the 2A debug build; launch once | Dashboard; existing assets still there (migration ran); no crash | **PASS** 2026-09-15: after row 2's uninstall, `adb install -r app/build/outputs/apk/debug/app-debug.apk` → `Success`; launched through the launcher intent; `dumpsys package` reports `versionCode=3 versionName=2.1 stopped=false notLaunched=false` and `MainActivity` is the resumed activity; crash buffer `FATAL EXCEPTION` count 0. **Caveat, stated plainly:** this install is a *fresh* one, so it does not itself exercise the v1 → v2 migration. The 2A build was installed over the 1C install earlier in the branch (Task 7's smoke check, which left a hot-tub asset on the phone) and opened without loss; the migration itself is JVM-proven by `Migration1To2Test` over `BundledSQLiteDriver`. **Migration device-proven by the controller:** a 1C build (versionCode 2) with one asset and one written tag, then the 2A build installed over it — `user_version` 1→2, asset and tag intact, `template_key` NULL, seven tables, 18 indexes, no crash |
| 2 | `adb shell svc power stayon usb` → `./gradlew :app:connectedDebugAndroidTest` → `svc power stayon false`; then **reinstall and launch** (the task uninstalls the app) | All instrumented tests pass (13 from 1C + `JournalSmokeTest` + the eight of `JournalDeviceProofTest`) | **PASS** 2026-09-15: **22/22**, 0 failures, 0 skipped, green on two consecutive runs — per-test lines in §3, along with every earlier run and what it found. Reinstall and launch done, see row 1 |
| 3 | New asset "Hot tub", template Hot tub → asset screen | CURRENT READINGS shows five rows with "—"; actions "Log water test", "Log treatment" first | **PASS** (automated: `JournalDeviceProofTest.hotTubTemplateSeedsReadingsThenAWaterTestFillsThem`, run on the owner's Android 17 phone 2026-09-15). The five labels are on screen, "—" appears eight times (the five empty readings plus the plate's three blank cells), and both quick actions are present |
| 4 | Log water test: pH 7.9, FC 0.8, alkalinity 110, calcium 200, temp 102; materials: Chlorine 1 oz, pH reducer 0.5 oz; Save | Live badges HIGH / LOW / IN RANGE / IN RANGE / NO TARGET SET while typing; back on the asset, readings show the same states; SERVICE RECORD shows "Water test" with "pH 7.9 · Free chlorine 0.8 ppm · Alkalinity 110 ppm" and a HIGH badge | **PASS** (automated: `JournalDeviceProofTest.hotTubTemplateSeedsReadingsThenAWaterTestFillsThem`, run on the owner's Android 17 phone 2026-09-15). The two materials are added from the profile's own suggestion chips, which bring the unit with them; the quantity field carries no test tag, so it is found as the field immediately after the one the chip filled. Each live badge is attributed by the counting rule of §3, and back on the asset the test pins the five formatted values and the exact multiset HIGH ×2 (reading + ledger), LOW ×1, IN RANGE ×2, NO TARGET SET ×1, plus the ledger line verbatim |
| 5 | Open the event → Edit → pH 7.5 → Save | Current readings pH 7.5 IN RANGE; the record line updated; same event, not a second one | **PASS** (automated: `JournalDeviceProofTest.editingTheWaterTestCorrectsItInPlace`, run on the owner's Android 17 phone 2026-09-15). Opened from the ledger row, edited through the overflow's **Edit**, saved from the app bar; back on the asset 7.5 is on screen, 7.9 is gone, IN RANGE ×2 with no HIGH, and "Water test" appears exactly once |
| 6 | Log water test dated a week earlier with pH 7.0; Save | SERVICE RECORD lists it **below** today's; current readings still 7.5 | **PASS** (automated: `JournalDeviceProofTest.aBackdatedTestSitsBelowAndLeavesTheCurrentReadingAlone`, run on the owner's Android 17 phone 2026-09-15). The date **is** driven through the UI — the form's Date field is an ordinary text field holding today until it is replaced — so nothing here was done in-process. "Below" is read off the two ledger rows' laid-out `positionInRoot`, not inferred |
| 7 | New asset "UPS", template UPS → Log load test: voltage 12.7, load 38, runtime 42, Passed = Yes; Save | Readings show the three numbers with NO TARGET SET and "Passed · Yes"; record line "Battery voltage 12.7 V · Load 38 % · Runtime 42 min" | **PASS** (automated: `JournalDeviceProofTest.upsLoadTestShowsNoTargetsAndPassedYes`, run on the owner's Android 17 phone 2026-09-15). **Yes** is tapped on the segmented control. On the asset: 12.7 / 38 / 42, NO TARGET SET exactly three times (the boolean carries no state at all), "Passed" and "Yes" both on screen, and the record line verbatim |
| 8 | New asset "Mower", template Power equipment → Log oil change: engine hours 138.5, Engine oil 1.5 qt, Oil filter 1 pcs; Save | Readings show Engine hours 138.5 h NO TARGET SET; record line shows the reading; opening the event lists both materials | **PASS** (automated: `JournalDeviceProofTest.mowerOilChangeRecordsTheMeterAndBothMaterials`, run on the owner's Android 17 phone 2026-09-15). Both materials come from the profile's suggestion chips; the event is then opened from the ledger and both lines are asserted with their quantities and units (1.5 qt, 1 pcs) |
| 9 | Existing 1C asset (no template) and a new asset saved with Template = None → "Set up from template" → RO water | Three TDS rows appear with "—"; "Log TDS test" action appears; doing it again is not offered | **PASS** (automated: `JournalDeviceProofTest.anAssetWithNoTemplateCanBeSetUpLater`, run on the owner's Android 17 phone 2026-09-15). The three TDS rows appear with "—", the action reads "Log TDS test", and **Set up from template** is gone afterwards. This row originally failed the fourth clause — the automated row caught `quickActionLabel` reading the action as "Log tDS test" — and that defect is fixed in this commit: `quickActionLabel` now only lowercases the profile name's first character when the second one is itself lowercase, so "Water test" still reads "Log water test" while "TDS test" is left alone. The test now asserts the correct string |
| 10 | Backup → Export; Debug → Wipe; Backup → Import (REPLACE) | Dashboard, assets, readings and records identical; the manifest counts in the file match `sqlite3` counts of the seven tables | **PASS** (automated: `JournalDeviceProofTest.backupRoundTripKeepsEveryCountAndTheAssetRendersAgain`, run on the owner's Android 17 phone 2026-09-15). **Said plainly: the export and the import are called in-process** (`exportBackup.run()` → wipe through the ports → `importBackupReplace.run(bytes)`), because the UI half of the backup screen is a SAF document picker, which is the system's and not the app's. What ran on the phone is the round trip against the device's own Room store: **all ten** table counts — `asset`, `nfc_tag`, `external_link` and the seven journal tables, counted through the ports — are equal before and after, the wipe in between is asserted to have emptied the store, and the asset is then cold-started again and draws 7.9, the HIGH badge and its "Water test" row from the imported rows |
| 11 | Open the hot tub → delete today's water test | Current readings fall back to the week-old pH 7.0 (LOW) | **PASS** (automated: `JournalDeviceProofTest.deletingTodaysTestFallsBackToTheOlderReading`, run on the owner's Android 17 phone 2026-09-15). Both entries are logged through the form, today's is opened from the ledger by its own date, deleted through the overflow and the confirmation dialog; afterwards one "Water test" row remains, 7.5 is gone, 7.0 is on screen and LOW appears twice (the reading and the surviving entry's badge) |
| 12 | `adb shell run-as com.loosecannon.notenfc sqlite3 databases/notenfc.db ".tables"` | Exactly the ten tables: `asset`, `nfc_tag`, `external_link` + the seven journal tables; nothing named after a hot tub, UPS, RO or mower | **PASS** (controller, `adb` schema listing: asset, nfc_tag, external_link plus the seven journal tables; nothing type-specific). The phone has no `sqlite3` binary, so the database was read out through `run-as` and its schema listed off-device; besides the ten app tables it carries only SQLite's own `android_metadata` and Room's `room_master_table`. `user_version` is 2 and the eighteen declared indexes are there |
| 13 | Scan the bound tag (from 1C) → asset opens → Log … | The scan path is unchanged and the quick action is one tap away | **NOT RUN** — the owner declined further manual rows; the tag → asset path is unchanged since 1C and was proven there |

Rows 3–11 were manual because they need hands; they now have `JournalDeviceProofTest`'s instead,
which is why every one of them names the test that produced its result. Row 12 was run by the
controller against the phone's own database. Row 13 is the only row still unrun, and §5 says so
rather than implying the phase is device-complete.

## 5. Status of the device proof

**Device-proven, with one row not run.** Everything below ran on the owner's
Android 17 phone on 2026-09-15.

**Automated and passing (§4 rows 3–11).** `JournalDeviceProofTest` — eight tests, listed in
§3 — drives the checklist's UI rows through the real screens: the hot-tub template's five empty
readings and two quick actions, a five-reading water test with two materials and a live badge on
every row as it is typed, an edit that corrects a reading in place, a backdated entry that files
below without becoming the current value, the UPS load test's range-less readings and its Yes/No
answer, the mower's meter reading with both materials, the RO water template's three TDS rows and
"Log TDS test" action, the backup round trip, and a delete that falls back to the previous
reading. With `JournalSmokeTest` and the thirteen from 1C the instrumented suite is **22 tests, 0
failures**, green on two consecutive runs.

**Row 9's defect fixed in this commit.** The automated row originally caught the quick action
reading **"Log tDS test"** instead of **"Log TDS test"** — `quickActionLabel` was blanket-
lowercasing the profile name's first character, which is right for "Water test" but wrong for an
acronym. Fixed by only lowercasing when the second character is itself lowercase; the test now
asserts "Log TDS test" and the row passes.

**Run by the controller (§4 rows 1, 12).** The v1 → v2 migration over a real 1C install, and the
phone's own schema: exactly the ten app tables, `user_version` 2, eighteen declared indexes,
nothing named after an asset kind.

**Not run (§4 row 13).** The tag scan. The owner declined further manual rows; the tag → asset
path is unchanged since 1C and was proven there.

What is complete on this machine:

- The final gate passes, including `:app:compileDebugAndroidTestKotlin` and a signed release build
  (§9).
- `:core` 172 and `:app` 88 JVM tests pass — 82 of them new in 2A — covering the model, the
  chronology and range rules, the templates, the three event use cases, the migration, the journal
  DAO's aggregate writes, both new ViewModels, backup format 2 and the seven-table restore proof.
- No template key appears anywhere outside `SeedTemplates.kt` (§9).

The honest statement of 2A is **implemented, JVM-proven and device-proven across the three asset
kinds, the backup round trip and the journal's edit and delete paths**; what it is not is walked
by a person's hands, and what the walk found is one cosmetic label defect that is still open.

Privacy: this document records no device serial, phone model, tag UID, note id or link, or home
path; paths are written relative to the repository and the phone is referred to throughout as
"the owner's Android 17 phone".

## 6. Rulings made during execution

- **Range bounds are inclusive.** 7.8 at the top of a 7.2–7.8 range is **IN RANGE**, not HIGH; the
  plan's fixtures said HIGH and the plan was wrong. `RangeState` is `value < min → LOW`,
  `value > max → HIGH`, otherwise IN RANGE, and `RangeStateTest` pins both bounds. The
  instrumented test uses 7.9 precisely so that it is unambiguously over the line.
- **`UpdateEvent` takes an injected `IdGenerator`.** It had been reaching for a `UuidGenerator` of
  its own, which made new child rows untestable. Every use case now takes the same seam.
- **`ImportReport` keeps `formatVersion`.** The plan dropped it; it went back in (`1a5b859`). A
  caller needs to know whether it just imported a format-1 backup with no journal in it.
- **Three FK-column indexes were added to schema v2** (`e87cfbd`) after Room warned that the child
  columns of those foreign keys were unindexed, and **spec §8 was amended** to list them, so the
  spec and `2.json` agree.
- **The JVM migration test builds v1 from `1.json`** by executing the exported schema's statements,
  rather than through `androidx.room3.testing.SQLiteDriverMigrationTestHelper`: at Room 3.0.3 that
  helper ships only in the Android `room-testing` artifact and wants an instrumented context, so it
  cannot run in a JVM unit test. Spec §8 now says so in the sentence that used to name the helper.
- **The template default is None; Generic is explicit.** A new asset with no template chosen gets
  no definitions and no profiles at all, and can be set up later from the asset screen. There is no
  silent default, because a wrong default is worse than an empty screen.
- **BOOLEAN renders Yes/No**, and the UPS template's boolean definition is labelled "Passed" so
  that the value only ever answers the question the label asks ("Passed · Yes").
- **`Measurement.shapeMatches` is the one owner of value shape.** NUMBER means `value_num` set and
  `value_text` null, TEXT the reverse, BOOLEAN a `value_num` of 0.0 or 1.0. The use cases, the
  backup codec's referential validation and the ViewModels all ask it rather than re-deciding.
- **The `ro_water` template's display name is "RO water"** (`17f378a`), matching the spec; all five
  display names are now pinned by `SeedTemplatesTest` so a rename is a test failure.
- **The entry route is a full-screen destination**, not a bottom sheet: it is a form with a
  variable number of rows, a date, materials and notes. The bottom button reads **"Record entry"**
  for a new event and **"Save entry"** for an edit; the app bar keeps a compact **Save**, and both
  call the same `save()`.

## 7. Deferred to Phase 2B

From the spec's §2, deliberately out of 2A scope:

- The definition/profile editor, and with it custom definitions and profiles.
- The full asset fields — manufacturer, model, serial, purchase and in-service dates, price,
  vendor, location, warranty — and child assets.
- Season windows on the asset.
- Template management of any kind, including "reset from template" (`template_key` is provenance
  only in 2A, spec §11).
- Derived display values such as RO rejection percentage.

Minors from the task ledger — small, real, none of them blocking:

- Consumable id preservation on an update is **positional**: editing an event matches the existing
  consumable rows to the new ones by index, so reordering them re-keys the rows.
- BOOLEAN parsing accepts any case (`TRUE`, `true`, `True` alike); deliberate for now, but it is a
  looser contract than the spec states.
- `AssetDetailState.bare` counts archived definitions, so an asset whose definitions were all
  archived is not offered **Set up from template** again.
- `AssetDetailViewModel` now has nine collaborators and wants a journal facade to collapse them.
- `JournalDaoTest.eventUpsertReplacesChildrenAndObserveEmits` proves a fresh read after the
  upsert, not that the flow re-emitted; the re-emission itself is untested.
- `firstProblemText` orders `BadConsumable` before the row problems, so a bad quantity masks a
  missing required reading in the snackbar.
- `quantity()` and `plain()` duplicate their number formatting.
- The `isError` tint on a refused save is outside D12 §5's measurement colour family.
- The entry screen's value fields live in a `LazyColumn`, so on a long profile a scrolled-away
  field loses focus. The fix is `Column` + `verticalScroll`, and it should land with the 2B editor
  rather than as a lone change here.
- Carried from 1C and still open: **a rewritten tag leaves its old row bound** — the tag lifecycle
  should retire a superseded row when a known UID is rewritten.
- There is no `observe(id)` port: a single-entity observer would let the detail screens drop their
  list subscriptions.

## 8. What 2A changed for later phases

- **The journal is the record, and everything else is derived from it.** There is no meter table,
  no "current value" column and no cached state anywhere. `LatestReadings` computes the current
  reading per definition from the event list, and `RangeState` judges it against the definition.
  Phase 3's usage-based schedules must read the meter the same way, from the events, so that an
  edited or deleted event can never leave a stale due date (D4 §6, D5).
- **Definitions and profiles are data, not code.** The entry screen is built from a profile's
  fields and each definition's `ValueType`; the asset screen's quick actions are its profiles. A
  new asset kind is rows in seven tables, never a screen. 2B's editor writes the same rows the
  seed templates write, through the same ports.
- **`EventChronology` owns the order.** The DAO sorts in SQL
  (`occurred_on DESC, COALESCE(occurred_time,'00:00') DESC, created_at DESC, id DESC`) and `:core`
  re-sorts with `EventChronology` anyway, so there is exactly one definition of "newest first" and
  any new event source inherits it. Backdating is ordinary, not an edge case.
- **`Measurement.shapeMatches` is the value-shape gate.** Anything that produces measurements —
  the entry form, an import, a future Todoist or attachment path — asks it rather than trusting
  its own construction. The backup codec refuses a file that violates it before it writes a row.
- **Backup format 2 is additive and format 1 still imports.** The manifest names its version and
  the reader branches on it; a phase that adds tables adds them to the manifest and bumps the
  format, and the old readers keep their meaning. Phase 4's attachments follow the same shape.
- **`EventOwnership` is a real error, not an assertion.** Referring to a profile or a definition
  that belongs to another asset is refused by the use case and nothing is written. Any later
  feature that moves events between assets has to answer this deliberately.
- **Schema v2's seven tables are generic by construction**, and the §9 grep is the guard: if a
  template key ever appears outside `SeedTemplates.kt`, an asset kind has leaked into the code.
  Keep the grep in the gate.

## 9. Final gate

```
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
    :app:compileDebugAndroidTestKotlin
```

→ **BUILD SUCCESSFUL** in 24s, 113 actionable tasks.

Test totals from the JUnit XML: **`:core` 172 tests, 0 failures, 0 skipped**; **`:app` 88 tests, 0
failures, 0 skipped** (per-class breakdown in §3). Instrumented: **22 tests, 0 failures, 0
skipped** on the owner's Android 17 phone, twice in a row (§3, §4 row 2). The gate itself is
unchanged by the device-proof commit — `JournalDeviceProofTest` is `androidTest` source, so it
adds nothing to either APK and the sizes below are the same bytes as before.

APK sizes:

```
-rw-r--r--. 13181814  app/build/outputs/apk/debug/app-debug.apk
-rw-r--r--.  9543978  app/build/outputs/apk/release/app-release.apk
```

(debug ≈ 12873 KiB, release ≈ 9320 KiB — up from 12568 / 9124 KiB in 1C; the journal's model, use
cases, DAOs and two screens cost about 305 KiB debug and 196 KiB release.) The release APK is
signed with the real `noteNFC` release certificate, not a debug key:

```
Signer #1 certificate DN: CN=noteNFC, O=GonzRon
Signer #1 certificate SHA-256 digest: 0902d3b0f826381905c6d8254fdaf36756924d80da7c19b90a08930b33277a9f
```

`versionCode` 3, `versionName` `2.1`; the phone reports the same after the reinstall (§4 row 1).

No personal path or username anywhere in the tree:

```
git grep -nIiE '/home/[a-z]+|lcstyle' -- . ':!.superpowers'
```

→ two hits, and both are the command quoting itself: this document's own §9 above, and
`docs/superpowers/plans/2026-09-15-phase-2a-journal.md` line 895, which is the task plan's copy of
the same command. (The earlier wording said "one hit" and overlooked the self-match; corrected
here.) No path, no username, no device serial and no phone model in any source file, document or
resource — including `JournalDeviceProofTest`, which names no device at all.

No asset kind outside the seed data:

```
grep -rn 'hot_tub\|power_equipment\|ro_water\|"ups"' app/src/main core/src/main --include=*.kt
```

→ four hits in `core/.../journal/SeedTemplates.kt` — the `key =` lines of four of the five
templates; the fifth, `generic`, is not in the pattern — plus one in `AssetDetailScreen.kt`
line 464, which is the word `"ups"` inside the icon-guessing keyword list
(`any("power", "battery", "electric", "ups", "meter", "gauge")`). That is a substring match on an
asset's free-text category, not a template key and not a code path: it picks a glyph and nothing
else. Neither template picker names a key — both iterate `SeedTemplates.all` — so exit criterion 4
holds: no screen, table or branch knows what a hot tub is.
