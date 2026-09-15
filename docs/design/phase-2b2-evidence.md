# Phase 2B-2 evidence — physical asset model, schema v4, format 4

Branch `phase-2b2` from `phase-2b1` at `5fdef2f`. Date 2026-09-15.

Phase 2B-2 is the second half of Phase 2B: the asset stops being a name with a category and
becomes the thing on the wall. It carries make, model and serial, what it cost and who from, where
it is, what it is part of, when its warranty runs out and which months of the year it is in use at
all. Four pure owners arrive in `:core` — `AssetTree` for composition, `Season` for windows,
`Money` for minor units, `CategorySuggestions` for the catalog — Room goes to v4 with fifteen new
asset columns and a self-referencing parent FK, the backup goes to format 4 with a parents-first
restore order, retirement becomes a date rather than a status, and the bottom bar drops to two
tabs with the tag reader kept as a Settings utility. What 2B-2 deliberately is not is a scheduler:
the season window has exactly one effect, and it is a badge (spec §2).

**Read the status first (§5).** The JVM suites are green — `:core` 272, `:app` 158 — the
instrumented suite is **37 tests, 0 failures, 0 skipped** on the owner's Android 17 phone, and the
v3 → v4 migration is device-proven over a real 2B-1 install with its rows intact (§4 row 1). Every
device row is automated: `AssetModelDeviceProofTest` is eight tests, one per scenario of spec §12,
and there are no manual rows in this document. The first full run found three defects and all
three were in *tests*: 2A's and 2B-1's em-dash counts still expected a three-cell identity plate,
which 2B-2's six-cell plate makes wrong, and 1C's two new-asset smoke tests tapped a **Save asset**
button that the grouped form of spec §9 has pushed below the fold (§3).

## 1. Exit criteria (spec §12) → evidence

| # | Criterion | Evidence | Status |
|---|---|---|---|
| 1 | The tree round-trips through backup with ids exact and a shuffled file imports | `BackupCodec.FORMAT_VERSION` is 4 and the asset payload carries all fifteen new fields plus `parentAssetId`; the reader validates the tree it is handed (`unknownParentIsCorrupt`, `cycleIsCorrupt`, `badSeasonIsCorrupt`) and `ImportBackupReplace` writes rows in `AssetTree.parentsFirst` order, so a file whose assets are in any order at all still satisfies the FK. JVM: `BackupCodecTest` (46, incl. `formatFourRoundTripsATree` and the format 1/2/3 decode tests), `BackupUseCasesTest` (14, incl. `shuffledTreeImportsParentsFirst`), `:app` `RestoreProofTest` (5, incl. `aThreeLevelTreeSurvivesTheRoundTrip`) | **PROVEN on the JVM.** A three-level tree survives export → wipe → import against a real Room store with every id exact and every parent pointer re-resolved, and a file whose asset list is shuffled imports parents-first rather than failing the FK |
| 2 | Season state is correct on every boundary case in tests and shows on device | `Season` is pure and total: both-null is year-round, a half-set window is refused, boundaries are inclusive, `start > end` wraps the year, and 02-29 resolves to 02-28 in a non-leap year both as a boundary and as today. JVM: `SeasonTest` (9 — `yearRoundWhenBothNull`, `bothOrNeither`, `badMonthDay`, `ordinaryWindowInclusive`, `wrappingWindow`, `oneDaySeason`, `feb29InLeapYear`, `feb29InNonLeapYearBehavesAsFeb28`, `todayFeb29AgainstAFeb28Boundary`), `AssetViewModelsTest.outOfSeasonComputedFromClock` | **PROVEN, both halves.** §4 row 6 is `AssetModelDeviceProofTest.anOutOfSeasonWindowBadgesTheAssetAndItsRowWhileAnInSeasonOneDoesNot` — a window two days wide starting thirty days out shows OUT OF SEASON on the asset screen *and* on its Assets row, while a window from yesterday to tomorrow shows no badge at all, both computed from the phone's own calendar day |
| 3 | Category hints never override an explicit template choice (test + device) | `CategorySuggestions.templateFor` is an exact case-insensitive label match and nothing reads the category string after creation; `AssetEditViewModel.onCategory` applies the hint only while `!editing && !templateTouched`, and `onTemplate` sets `templateTouched` forever. JVM: `CategorySuggestionsTest` (3), `AssetViewModelsTest.hintPreselectsTemplateOnNewAsset`, `.explicitTemplateSurvivesCategoryChange`, `.editingNeverShowsOrChangesTemplate` | **PROVEN, both halves.** §4 row 7 is `AssetModelDeviceProofTest.aCategoryHintPreSelectsATemplateButNeverOverridesAnExplicitChoice` — typing Category "RO system" on the phone selects the **RO water** chip on its own; tapping **Hot tub** by hand and then changing the category to "Generator" (which hints at `power_equipment`) leaves Hot tub selected and Power equipment unselected |
| 4 | Retirement is independent of the optional event (test + device) | `RetireAsset` writes `retiredOn` and touches nothing else — not `status`, not the journal — and `Asset.isRetired` is `retiredOn != null`. The follow-on event is a second dialog raised *after* the write, so all three of its answers are fine ones. JVM: `RetireDeleteAssetTest` (5 — `retireSetsDateAndKeepsStatus`, `unretireClears`, `archiveDoesNotCascade`, `deleteRefusedWithChildrenNamed`, `deleteWithoutChildrenRemoves`), `AssetViewModelsTest.retireKeepsAssetAndFollowOnIsOptional`, `.unretireClears` | **PROVEN, both halves.** §4 row 9 is `AssetModelDeviceProofTest.retirementCommitsBeforeTheEventIsOfferedAndDecliningItChangesNothing` — Retire → the date field on today → confirm → **"Log what happened?" → Not now**, and the RETIRED badge is still on the plate; Unretire then clears it |
| 5 | The Scan tab is gone and ambient dispatch plus the utility route still reach the scan screen | `TopLevelRoutes` is `Dashboard, Assets` and `BottomBar` iterates it, so the third tab cannot come back by accident; `Route.Scan` stays a pushed destination reached from Settings' **Read / inspect tag** and from the empty dashboard's own action; `NfcDispatchActivity` is untouched by 2B-2 and still hands `MainActivity` a (format, key) pair. JVM: `RouteTest.topLevelRoutesIsDashboardThenAssetsOnly`, `:core` `TagRouteTest` (3), `DeepLinkRouteTest` (5), `ResolveTagTest` (8). Instrumented: `NavigationSmokeTest.bottomBarHasTwoItems`, `.settingsOpensReadInspectTag`, `AppSmokeTest.emptyDashboardScanActionOpensReadInspectTag`, `DeepLinkSmokeTest.malformedDeepLinkLandsOnDashboard` | **PROVEN** on the phone: §4 row 10 is `AssetModelDeviceProofTest.theBottomBarHasTwoTabsAndTheTagReaderLivesUnderSettings` — exactly two clickable bar items, zero clickable "Scan", and Settings → **Read / inspect tag** → **READY TO SCAN** → Back landing on Settings |
| 6 | v1 → v4 chain green and a real 2B-1 install upgrades on device | `AppDatabase` v4 with `MIGRATION_3_4`, which recreates `asset` with the fifteen new columns and the `parent_asset_id` FK (`ON DELETE RESTRICT`), copies every row forward with `parent_asset_id` NULL and the new text columns `''`, maps any stored `RETIRED` status to `ARCHIVED`, and adds `index_asset_parent_asset_id`. JVM: `Migration3To4Test` (2), `Migration1To4Test` (the whole chain), `Migration1To3Test`, `Migration2To3Test`, `Migration1To2Test`, plus `AssetDaoTest` (7) for the FK's RESTRICT and the unknown-parent refusal | **PROVEN, both halves.** JVM chain green; **and on the phone** (§4 row 1): the 2B-1 debug build (versionCode 4) seeded by driving its own screens, then the 2B-2 build installed over it and opened from the launcher — `user_version` 3 → **4**, every row count identical, the asset row intact with all fifteen new columns present, `parent_asset_id` NULL, 20 → **21** indexes, no `FATAL EXCEPTION` |
| 7 | The taxonomy grep stays clean | The template-key grep (§9) finds the four keys in `SeedTemplates.kt` and in `CategorySuggestions.kt`, which is the catalog's *creation-time hint* and the one place spec §8 puts them, plus the word `"ups"` inside `AssetDetailScreen`'s icon-keyword list, which is a substring match on an asset's free-text category and picks a glyph. No screen, table, query or branch asks what kind of thing an asset is | **PROVEN** — §9 |

## 2. What shipped (by commit)

```
596cf19  asset fields, tree, season, category suggestions
80e7397  asset use cases: full fields, hierarchy, retire, delete
4f1044c  money: refuse a trailing dot and a negative minor; feb 29 season test
a577f42  backup format 4: asset fields and tree, parents-first restore
f0d02de  room v4: asset columns, parent fk, migration chain
bb648f7  asset editor: identity, placement, purchase, warranty, season, part of
ab3f74b  asset detail: plate, details, components, retire, list badges
feccb46  components always shows, plate swaps the dup category for purchased
53f9cac  nav: scan tab retired, read/inspect tag lives in settings
```

Nine commits, 5 425 insertions across 63 files.

- **`596cf19` — the model and its four owners.** `Asset` grows fifteen fields; `AssetTree`
  (`children`, `descendants`, `wouldCycle`, `parentsFirst`), `Season` (`validate`, `inSeason`),
  `Money` (`fractionDigits`, `parse`, `format`) and `CategorySuggestions` (twelve labels, six of
  them carrying a creation-time template hint) are pure, total and the single owners of their
  rules.
- **`80e7397` + `4f1044c` — the use cases.** One `AssetCommand` for create *and* update, so the
  two paths cannot drift; `validateAsset` collects every `AssetProblem` at once and then refuses a
  cycle; `RetireAsset` writes a date and nothing else; `DeleteAsset` refuses a parent and names its
  children. `Money.parse` refuses a trailing `.` the same way it refuses a leading one, and
  `Money.format` refuses a negative minor outright.
- **`a577f42` — backup format 4.** The manifest names its version and the reader branches on it, so
  formats 1, 2 and 3 still decode; the writer emits the new fields and the parent pointer, the
  reader validates the tree it is given, and the importer writes `parentsFirst`.
- **`f0d02de` — Room v4.** `MIGRATION_3_4` recreates `asset`, copies every row forward, maps a
  stored `RETIRED` to `ARCHIVED`, and adds the parent index. `assets.deleteAll()` became
  children-first through a `@Transaction` DAO method fed by `AssetTree`, because the FK that stops
  a cascade also stops a wipe.
- **`bb648f7` — the editor.** Identity / Placement / Purchase / Warranty / Notes, the "Part of"
  picker that excludes the asset and everything under it, the year-round switch over two month-day
  fields, and a price that is text until its currency resolves it.
- **`ab3f74b` + `feccb46` — the asset screen.** The six-cell identity plate, a DETAILS section that
  shows only what is set, a COMPONENTS section that always renders (so "+ Add component" is always
  reachable), "Part of <parent>" tapping upward, the retire / unretire / delete flow with its
  children-first refusal, and the three independent badges.
- **`53f9cac` — navigation.** `TopLevelRoutes` drops to two; `Route.Scan` becomes a Settings
  utility; the README and the smoke tests follow.

## 3. Tests

### JVM (`./gradlew :core:test :app:testDebugUnitTest`)

| Module | Class | Tests |
|---|---|---|
| `:core` | `ApplyTemplateTest` | 6 |
| `:core` | `AssetTreeTest` | 7 |
| `:core` | `AssetUseCasesTest` | 13 |
| `:core` | `BackupCodecTest` | 46 |
| `:core` | `BackupUseCasesTest` | 14 |
| `:core` | `CategorySuggestionsTest` | 3 |
| `:core` | `DeepLinkRouteTest` | 5 |
| `:core` | `DefinitionUseCasesTest` | 17 |
| `:core` | `DeleteLinkTest` | 2 |
| `:core` | `DerivedTest` | 15 |
| `:core` | `EventChronologyTest` | 4 |
| `:core` | `EventUseCasesTest` | 16 |
| `:core` | `LatestReadingsTest` | 6 |
| `:core` | `LinkLaunchPolicyTest` | 9 |
| `:core` | `LinkUseCasesTest` | 9 |
| `:core` | `MeasurementShapeTest` | 3 |
| `:core` | `MoneyTest` | 7 |
| `:core` | `NdefCodecTest` | 8 |
| `:core` | `NdefCodecV1Test` | 14 |
| `:core` | `OverwritePolicyTest` | 8 |
| `:core` | `ProfileUseCasesTest` | 8 |
| `:core` | `RangeStateTest` | 5 |
| `:core` | `ResolveTagTest` | 8 |
| `:core` | `RetireDeleteAssetTest` | 5 |
| `:core` | `SeasonTest` | 9 |
| `:core` | `SeedTemplatesTest` | 7 |
| `:core` | `TagBindingUseCasesTest` | 15 |
| `:core` | `TagRouteTest` | 3 |
| `:app` | `AppPrefsTest` | 4 |
| `:app` | `AssetDaoTest` | 7 |
| `:app` | `AssetSetupViewModelTest` | 6 |
| `:app` | `AssetViewModelsTest` | 31 |
| `:app` | `BackupViewModelTest` | 2 |
| `:app` | `ContrastTest` | 6 |
| `:app` | `DashboardViewModelTest` | 4 |
| `:app` | `DefinitionEditViewModelTest` | 9 |
| `:app` | `EventEntryViewModelTest` | 13 |
| `:app` | `ExternalLinkDaoTest` | 5 |
| `:app` | `JournalDaoTest` | 9 |
| `:app` | `JournalFormatTest` | 7 |
| `:app` | `Migration1To2Test` | 1 |
| `:app` | `Migration1To3Test` | 1 |
| `:app` | `Migration1To4Test` | 1 |
| `:app` | `Migration2To3Test` | 1 |
| `:app` | `Migration3To4Test` | 2 |
| `:app` | `NfcTagDaoTest` | 7 |
| `:app` | `ProfileEditViewModelTest` | 11 |
| `:app` | `RepositoryFlowsTest` | 3 |
| `:app` | `RestoreProofTest` | 5 |
| `:app` | `RoomRepositoriesTest` | 13 |
| `:app` | `RouteTest` | 1 |
| `:app` | `TagUseCasesRoomTest` | 3 |
| `:app` | `TagWriteControllerTest` | 6 |

Totals from the JUnit XML: **`:core` 272 tests, 0 failures, 0 skipped**; **`:app` 158 tests, 0
failures, 0 skipped**. 2B-1 finished at `:core` 225 / `:app` 128, so 2B-2 added **47 and 30**.

### Instrumented (`./gradlew :app:connectedDebugAndroidTest`)

Run on the owner's Android 17 phone, 2026-09-15. **37 tests, 0 failures, 0 skipped** — nine more
than 2B-1's 28: the eight of `AssetModelDeviceProofTest` plus `NavigationSmokeTest`'s third test.

```
ui.AppSmokeTest                 dashboardShowsTheBackupNudgeOnAFreshInstall                          1.068s  pass
ui.AppSmokeTest                 emptyDashboardScanActionOpensReadInspectTag                          1.542s  pass
ui.AppSmokeTest                 assetCanBeCreatedFromTheDashboardAndOpens                            4.297s  pass
ui.AppSmokeTest                 secondNewAssetFormStartsBlank                                        4.527s  pass
ui.AppSmokeTest                 backupScreenRenders                                                  1.399s  pass
ui.AssetModelDeviceProofTest    aParentListsItsComponentsAndEachComponentNamesTheParent              3.031s  pass
ui.AssetModelDeviceProofTest    aComponentIsReparentedUnderItsSiblingAndNoDescendantIsOffered        5.133s  pass
ui.AssetModelDeviceProofTest    deletingAParentIsRefusedByNameAndArchivingItLeavesTheChildrenActive  3.858s  pass
ui.AssetModelDeviceProofTest    anOutOfSeasonWindowBadgesTheAssetAndItsRowWhileAnInSeasonOneDoesNot  3.856s  pass
ui.AssetModelDeviceProofTest    aCategoryHintPreSelectsATemplateButNeverOverridesAnExplicitChoice    4.642s  pass
ui.AssetModelDeviceProofTest    retirementCommitsBeforeTheEventIsOfferedAndDecliningItChangesNothing 2.307s  pass
ui.AssetModelDeviceProofTest    theBottomBarHasTwoTabsAndTheTagReaderLivesUnderSettings              1.729s  pass
ui.AssetModelDeviceProofTest    aGroupedPriceIsStoredAsMinorUnitsAndShownAtTheCurrencysPrecision     6.597s  pass
ui.ComponentsSmokeTest          sectionHeaderShowsItsTitle                                           0.761s  pass
ui.ComponentsSmokeTest          identityPlateShowsDashForBlankValues                                 0.781s  pass
ui.ComponentsSmokeTest          statusBadgeExposesItsLabelToAccessibility                            0.775s  pass
ui.ComponentsSmokeTest          ledgerEntryShowsItsDateAndTitle                                      0.807s  pass
ui.DeepLinkSmokeTest            malformedDeepLinkLandsOnDashboard                                    1.085s  pass
ui.EditorsDeviceProofTest       aCustomReadingAndActionDriveALoggedEntry                             8.127s  pass
ui.EditorsDeviceProofTest       rejectionComesFromOneTestAndIsNeverCombinedAcrossEvents              6.829s  pass
ui.EditorsDeviceProofTest       archivingASourceEmptiesTheDerivedRowAndLeavesHistoryAlone            7.938s  pass
ui.EditorsDeviceProofTest       formatThreeRoundTripBringsTheDerivedReadingBack                      1.691s  pass
ui.EditorsDeviceProofTest       deletingAReadingWithDataAndADependentIsRefusedByName                 4.720s  pass
ui.EditorsDeviceProofTest       imeNextWalksFocusDownALongProfile                                    4.291s  pass
ui.JournalDeviceProofTest       hotTubTemplateSeedsReadingsThenAWaterTestFillsThem                   8.955s  pass
ui.JournalDeviceProofTest       editingTheWaterTestCorrectsItInPlace                                 5.721s  pass
ui.JournalDeviceProofTest       aBackdatedTestSitsBelowAndLeavesTheCurrentReadingAlone               5.453s  pass
ui.JournalDeviceProofTest       deletingTodaysTestFallsBackToTheOlderReading                         6.594s  pass
ui.JournalDeviceProofTest       mowerOilChangeRecordsTheMeterAndBothMaterials                        4.423s  pass
ui.JournalDeviceProofTest       upsLoadTestShowsNoTargetsAndPassedYes                                4.089s  pass
ui.JournalDeviceProofTest       anAssetWithNoTemplateCanBeSetUpLater                                 1.812s  pass
ui.JournalDeviceProofTest       backupRoundTripKeepsEveryCountAndTheAssetRendersAgain                4.807s  pass
ui.JournalSmokeTest             hotTubWaterTestShowsInRecordAndReadings                              3.340s  pass
ui.NavigationSmokeTest          dashboardIsTheStartDestination                                       0.933s  pass
ui.NavigationSmokeTest          bottomBarHasTwoItems                                                 0.940s  pass
ui.NavigationSmokeTest          settingsOpensReadInspectTag                                          1.482s  pass
ui.ShareActivitySmokeTest       sharedWebLinkShowsTheCard                                            0.824s  pass
```

**The three defects the first full run found were all in tests, not in the app.**

- `JournalDeviceProofTest.hotTubTemplateSeedsReadingsThenAWaterTestFillsThem` expected **8** em
  dashes and found 11; `.anAssetWithNoTemplateCanBeSetUpLater` expected 7 and found 10;
  `EditorsDeviceProofTest.archivingASourceEmptiesTheDerivedRowAndLeavesHistoryAlone` expected 4 and
  found 7. All three are the same fact: the identity plate had **three** blank cells in 2A and 2B-1
  and has **six** in 2B-2 (Model, Serial, Location, Purchased, In service, NFC tag — spec §9), so
  every count of "—" on an asset screen is three higher. The expectations were corrected and their
  comments now name the six cells.
- `AppSmokeTest.assetCanBeCreatedFromTheDashboardAndOpens` and `.secondNewAssetFormStartsBlank` both
  timed out waiting for the saved asset's name. Both tapped the form's foot button **Save asset**
  without scrolling to it, which was fine when the new-asset form was four fields and is not now
  that it is twenty: the button is far below the fold. A `performScrollTo()` before the click fixes
  both. The app bar's **Save** was on screen the whole time and works — `AssetModelDeviceProofTest`
  uses it — so nothing about the app was wrong.

One defect in the new suite was found and fixed the same way: `openOverflow()` followed by
`onNodeWithText("Edit")` is ambiguous, because the asset screen offers **Edit** twice — as a quick
action in the grid and as the first item of the overflow. The test now taps the grid's button,
which is the one that is always on screen.

## 4. Device proof (the owner's Android 17 phone)

No manual rows: every row below is either an instrumented test or an `adb`-driven check run by the
controller. Row 2 is destructive by construction — each test's `@Before` clears
`SharedPreferences("notenfc")` and empties the store — and AGP **uninstalls the app when
`connectedDebugAndroidTest` finishes**, which is 1C's, 2A's and 2B-1's lesson repeated: after row 2
the package is gone and must be reinstalled with `adb install -r` and launched once, because
Android 17 delivers no NFC intent to a package in the *stopped* state. Row 1 therefore runs before
row 2. `ANDROID_SERIAL` was pinned for every `adb` and Gradle command; no serial, model or codename
appears anywhere in this repository (§9).

| # | Step | Expected | Result |
|---|---|---|---|
| 1 | v3 → v4 migration over a real 2B-1 install: `adb install -r -d` the 2B-1 debug build (versionCode 4) built from `8b8b721`, `pm clear`, drive its own screens to create an asset and log an entry, then `adb install -r` the 2B-2 debug build over it, open it from the launcher, and read the database back out through `run-as` | `user_version` 4; the asset row intact with the fifteen new columns present, `parent_asset_id` NULL and the new non-null strings `''`; tag and journal counts identical; `index_asset_parent_asset_id` present; `FATAL EXCEPTION` count 0 | **PASS** (controller, `adb`, 2026-09-15). The 2B-1 build was installed, its data cleared, and then **seeded through its own UI** — 2B-1's own `JournalDeviceProofTest.hotTubTemplateSeedsReadingsThenAWaterTestFillsThem` run against the 2B-1 APK with `am instrument`, which taps **Log water test** on a hot-tub asset and types five readings and two materials into the real form (10.36s, OK) — so the database the migration ran against was written by the 2B-1 app through the 2B-1 app's screens. Its test package was then uninstalled and only the app left in place. Read back through `run-as` (the phone has no `sqlite3`, so the file and its `-wal` are inspected off-device with Python's `sqlite3`). The 2B-2 build was installed over it and **opened from the launcher** (`am start` → `MainActivity` is the resumed activity; `dumpsys package` reports `versionCode=5 versionName=2.3 stopped=false notLaunched=false`). Numbers in the before/after table below; `logcat` `FATAL EXCEPTION` count **0** |
| 2 | `adb shell svc power stayon usb` → `./gradlew :app:connectedDebugAndroidTest` → `svc power stayon false`; then **reinstall and launch** (the task uninstalls the app) | All instrumented tests pass (28 from 1C, 2A and 2B-1 plus the eight of `AssetModelDeviceProofTest` and `NavigationSmokeTest`'s third) | **PASS** 2026-09-15: **37/37**, 0 failures, 0 skipped — per-test lines in §3, together with the five stale expectations the first run found and the one ambiguity in the new suite. Reinstall and launch done: `versionCode=5 versionName=2.3 stopped=false notLaunched=false`, `MainActivity` resumed, `FATAL EXCEPTION` count 0 |
| 3 | Parent "Solar system" with components "Inverter" and "Battery bank" → the parent's COMPONENTS names both; the component's screen reads "Part of Solar system"; tapping that line opens the parent | Both names under COMPONENTS on the parent; "Part of Solar system" on the child; the tap lands on the parent | **PASS** (automated: `AssetModelDeviceProofTest.aParentListsItsComponentsAndEachComponentNamesTheParent`, run on the owner's Android 17 phone 2026-09-15). The parent is asserted to have no "Part of" line of its own, and after the tap the child's line is gone while the parent's COMPONENTS is on screen — so the navigation really moved rather than the same screen redrawing |
| 4 | Edit "Battery bank" → **Part of** = Inverter → **Save**; then open Solar system's own "Part of" picker | Inverter's COMPONENTS lists Battery bank; the picker offered to Solar system contains no descendant of it — no "Inverter" | **PASS** (automated: `AssetModelDeviceProofTest.aComponentIsReparentedUnderItsSiblingAndNoDescendantIsOffered`, same run). The reparent is done **through the picker on the phone**: the "Part of" dropdown is opened, "Inverter" chosen, the app bar's Save tapped, and the component then reads "Part of Inverter" with "Part of Solar system" gone. Tapping up lands on Inverter, whose COMPONENTS lists Battery bank; tapping up again lands on Solar system, and its own picker — opened, and proven open by a second clickable "None" appearing beside the anchor's — offers **neither** "Inverter" (its child) **nor** "Battery bank" (its grandchild). The one move that could create a cycle is not on the menu |
| 5 | Solar system → overflow **Delete** → confirm; then overflow **Archive**; then the Assets list | The delete is refused and the dialog names both components; nothing is written; after the archive the two children are still active, with no ARCHIVED badge on either | **PASS** (automated: `AssetModelDeviceProofTest.deletingAParentIsRefusedByNameAndArchivingItLeavesTheChildrenActive`, same run). Past the neutral confirmation ("Delete Solar system?"), the refusal reads **"Components first"** and one node carries both **"Inverter"** and **"Battery bank"**; after OK the parent and both components are still on screen. The archive then takes the parent alone: the default Assets list shows Inverter and Battery bank with **zero** "ARCHIVED" nodes and no "Solar system" at all, and with **Show archived** on, the badge count is exactly **1** and it belongs to the row that also says "Solar system" |
| 6 | An asset whose season window is two days wide starting thirty days from today, and another whose window runs from yesterday to tomorrow | OUT OF SEASON on the first asset's screen and on its Assets row; no badge on the second | **PASS** (automated: `AssetModelDeviceProofTest.anOutOfSeasonWindowBadgesTheAssetAndItsRowWhileAnInSeasonOneDoesNot`, same run). Both windows are computed in the test from the phone's own `LocalDate.now()`, so this row is date-independent rather than pinned to 2026-09-15. Said plainly: **`today` is not injected** — `AppGraph.clock` is a `val` over `System.currentTimeMillis()` and 2B-2 added no seam for a fake one, so the proof is arithmetic on the real calendar day instead. On the list the badge is attributed to its row by the row's own merged node (`AssetListRow` is a `clickable` Row), and the total badge count on the list is **1** |
| 7 | New asset → Category "RO system" → look at the template row; then tap **Hot tub** by hand → Category "Generator" → look again | RO water selected by the hint; Hot tub still selected after the category change, and Power equipment not selected | **PASS** (automated: `AssetModelDeviceProofTest.aCategoryHintPreSelectsATemplateButNeverOverridesAnExplicitChoice`, same run). Both categories are chosen **out of the suggestion menu on the phone**, which is also what closes it; the chips are asserted with `assertIsSelected` / `assertIsNotSelected`, not by colour |
| 8 | New asset with **Price** "1,234.5" and **Currency** USD → Save | The asset's DETAILS section reads "1234.50 USD" | **PASS** (automated: `AssetModelDeviceProofTest.aGroupedPriceIsStoredAsMinorUnitsAndShownAtTheCurrencysPrecision`, same run). Typed with a grouping comma and one decimal place, stored as 123450 minor units, and rendered back at the currency's own two digits by `Money.format` — the number on screen is not the text that was typed |
| 9 | Generator → overflow **Retire** → the default date → confirm → **"Log what happened?" → Not now**; then overflow **Unretire** | RETIRED on the plate after the confirm and still there after Not now; gone after Unretire | **PASS** (automated: `AssetModelDeviceProofTest.retirementCommitsBeforeTheEventIsOfferedAndDecliningItChangesNothing`, same run). The dialog is asserted to open on **today's ISO date** before it is confirmed, which is the backdatable default of spec §7; the follow-on offer is then declined and the badge is still on the plate. That last half is the whole point of the row: proving the badge appears proves nothing if declining the event would have rolled the retirement back |
| 10 | The bottom bar, and Settings → **Read / inspect tag** → Back | Exactly two clickable bar items (Dashboard, Assets) and no clickable "Scan"; the reader opens and says READY TO SCAN; Back lands on Settings | **PASS** (automated: `AssetModelDeviceProofTest.theBottomBarHasTwoTabsAndTheTagReaderLivesUnderSettings`, same run). The count is asserted as an exact **2** rather than as "Scan is absent", so a fourth tab would fail it too; and the Back assertion is that READY TO SCAN is **gone** and "Settings" is on screen, not merely that something happened |

### Row 1 in numbers — before and after

The 2B-1 install, seeded through 2B-1's own screens, then the 2B-2 build laid over it:

| | before (2B-1, versionCode 4 / 2.2) | after (2B-2, versionCode 5 / 2.3) |
|---|---|---|
| `user_version` | **3** | **4** |
| app tables | 10 | 10 |
| `asset` | 1 | 1 |
| `measurement_definition` | 5 | 5 |
| `event_profile` | 2 | 2 |
| `profile_field` | 7 | 7 |
| `profile_consumable` | 8 | 8 |
| `asset_event` | 1 | 1 |
| `measurement` | 5 | 5 |
| `consumable_usage` | 2 | 2 |
| `nfc_tag` | 0 | 0 |
| `external_link` | 0 | 0 |
| indexes | 20 | **21** |
| `index_asset_parent_asset_id` | absent | **present** |
| `asset` columns | 9 | **24** |

Every count is identical; only the schema moved. The `asset` row survives exactly — id
unchanged, `name` "Hot tub", `template_key` `hot_tub`, `status` `ACTIVE`, `created_at` and
`updated_at` byte-for-byte the same — and the fifteen columns `MIGRATION_3_4` adds arrive with the
defaults spec §10 asks for:

- `parent_asset_id` **NULL**, which is what makes the upgrade a no-op for composition;
- the six new non-null text columns all **`''`** — `manufacturer`, `model`, `serial_number`,
  `vendor`, `location`, `warranty_notes`;
- the nine new nullable columns all **NULL** — `purchase_on`, `in_service_on`,
  `purchase_price_minor`, `currency`, `warranty_expires_on`, `retired_on`, `parent_asset_id`,
  `season_start_mmdd`, `season_end_mmdd`.

The journal is untouched by name as well as by count: all five definitions still read
`kind = 'ENTERED'`, and every stored value survives exactly — pH 7.9, free chlorine 0.8,
alkalinity 110, calcium hardness 200, water temperature 102, Chlorine 1 oz, pH reducer 0.5 oz.
`logcat` `FATAL EXCEPTION` count **0**, before and after.

## 5. Status of the device proof

**Device-proven, with nothing left unrun.** Everything below ran on the owner's Android 17 phone on
2026-09-15.

**Automated and passing (§4 rows 3–10).** `AssetModelDeviceProofTest` — eight tests, listed in §3 —
drives spec §12's scenarios through the real screens: a parent and its two components naming each
other and tapping through, a reparent done in the picker with no descendant on offer, a delete
refused by name and an archive that takes only the parent, a season window badging one asset and
not another, a category hint that yields to an explicit choice, a retirement that survives
declining its own follow-on event, a two-tab bar with the reader under Settings, and a grouped
price stored as minor units. With the twenty-eight from 1C, 2A and 2B-1 and `NavigationSmokeTest`'s
third test, the instrumented suite is **37 tests, 0 failures**.

**Migration proven over a real install (§4 row 1).** Not a synthetic v3 file: a 2B-1 APK installed
on the phone, its data cleared, then seeded by running 2B-1's own instrumented water-test scenario
against it, so the rows the migration ran over were written by the older app through the older
app's UI. The upgrade moved `user_version` 3 → 4 and changed nothing else that can be counted.

**What is asserted in-process rather than through the UI, and why.** Every asset a row *starts*
from is created with `createAsset.run(AssetCommand(...))` — the same command the editor builds — so
a row about COMPONENTS does not fail because a dropdown moved. Two rows are about the form itself
(7 and 8) and drive it through the UI end to end. A child is given its parent as `parentAssetId`
rather than through the picker in row 3, because the picker is exactly what row 4 exercises.
Nothing was skipped and no assertion was dropped: the "if a UI step is too brittle, do it
in-process" escape hatch of the task brief was not needed once for this phase.

**One thing this suite cannot claim.** The season badge is proven against the phone's real calendar
day, not against an injected one, because there is no seam to inject through (§4 row 6). Every
boundary case — inclusive ends, a wrapping window, a one-day window, Feb 29 in both directions —
is proven on the JVM by `SeasonTest`, which *can* choose its today.

## 6. Rulings made during execution

Every ruling recorded in the SDD ledger, in plain words.

1. **The editor accepts a preset parent from Task 5, not Task 6.** `Route.AssetEdit` only grew its
   `parentId` in Task 6, but the editor had to be able to open with "Part of" already filled in
   before there was a route carrying it — otherwise "+ Add component" would have had nothing to
   talk to. Task 5 added the parameter; Task 6 wired the route to it. Cost if wrong: nil.
2. **`AssetNameRequired` stayed a subtype of `AssetValidation` until Task 5 deleted it.** The old
   single-problem exception had callers in screens that Task 5 was going to rewrite anyway, so it
   was kept open and inherited from the new one rather than removed mid-flight.
3. **`UpdateAsset` kept a four-string overload that re-sends every field.** The pre-2B-2 screens
   called it with three text fields, and a partial update would have blanked the rest; the overload
   re-sent everything until Task 5 replaced the call sites, then it went.
4. **No cross-field date ordering.** Nothing checks that `retired_on` is after `purchase_on`, or
   that a warranty expires after the purchase. Not in 2B-2 — recorded in §7.
5. **`Money.parse` refuses a trailing `.` the same way it refuses a leading one.** `".5"` and
   `"123."` are both half-typed numbers, and quietly reading them as `0.50` and `123.00` is the
   kind of helpfulness that puts the wrong price in the database. `Money.format` likewise refuses a
   negative minor outright rather than rendering `"-1.-50"`. Verified by the controller against the
   diff: two lines of production code in `Money.kt` plus tests.
6. **The corrupt-cycle message cannot name the ids involved.** A backup file containing a cycle is
   refused, but the message says only that there is one. Accepted: cycle files never come from this
   app, because `validateAsset` refuses to write one.
7. **`assets.deleteAll()` throws loudly on a stored cycle.** The children-first wipe walks the tree,
   and a cycle in the stored rows would make that walk impossible. Unreachable through the app;
   failing loudly beats deleting half a store.
8. **The older migration tests run the whole chain.** `Migration1To2Test` and `Migration1To3Test`
   now end at v4, because Room opens the database at the current version whatever the test is
   named after. Accepted; the names understate what they cover (§7).
9. **A stored `RETIRED` status maps to `ARCHIVED` in the copy.** No release ever wrote that value —
   `RETIRED` was in D4's table and never in the code — but the migration maps it defensively rather
   than carrying an unreadable status forward. Kept, and tested
   (`Migration3To4Test.aStoredRetiredStatusBecomesArchived`).
10. **Ordered deletes live behind a `@Transaction` DAO method.** `deleteAllInOrder(ids)` keeps the
    repositories constructed from a DAO alone, which is how every other repository in the app is
    built.
11. **Dialog state is not saved across process death.** Retiring with the dialog open and then
    being killed loses the dialog, not the asset. Accepted.
12. **COMPONENTS always renders.** Spec §9 said the section was absent when empty; an asset with no
    children would then have had no way to get its first one, because "+ Add component" lives in
    that section. It now always renders, reading "No components" when there are none. Spec §9 was
    amended rather than worked around.
13. **The plate's sixth cell is PURCHASED, not CATEGORY.** Spec §9's cell list repeated the
    category, which is already the eyebrow above the cells. The duplicate was swapped for the
    purchase date and spec §9 amended.
14. **The purchase date appears on the plate and in DETAILS.** A consequence of ruling 13.
    Accepted — DETAILS lists everything else about the purchase — and recorded in §7.
15. **The G1 report keeps "Dashboard · Assets · Scan" as history.** G1 is a record of what was
    reported at the time; the correction lives in D12 §16 and in this phase, not by editing an old
    report.
16. **The README bullet was rewritten wider than one sentence.** The old Assets bullet described
    the bottom bar as it was; a one-word edit would have left it wrong. Accepted.

## 7. Deferred

Nothing here blocks the phase; all of it is written down so the next phase does not rediscover it.

**Minors the reviews deferred.**

- **`deleteAll` atomicity rests on the caller's `uow.write`.** The children-first ordering is inside
  a `@Transaction` DAO method, but the *decision* about what order to delete in is taken outside it.
  Documented in the port's KDoc as a caller convention rather than enforced by a type.
- **Spec §10 says `DEFAULT ''` and the SQL has no `DEFAULT`.** The new non-null text columns are
  filled with `''` by the migration's own copy and by Room's entity defaults, not by a column
  default. The same ruling as 2B-1's `kind`; nothing can insert a row without going through Room.
- **The migration test names understate the chain.** `Migration1To2Test` and `Migration1To3Test`
  both end at v4 now (ruling 8).
- **Editing a since-deleted asset leaves a blank form.** Open the editor, delete the asset from
  another surface, and the form stays with nothing in it instead of popping.
- **`NegativePrice` is unreachable from the screen.** `Money.parse` refuses a leading `-` before the
  problem can be raised. The rule stays in the use case, which is where a future import or a future
  API would hit it.
- **The purchase date shows in two places** — the plate's PURCHASED cell and the DETAILS row
  (ruling 14).
- **The month-day picker shows a discarded year.** The season boundary fields open the ordinary
  calendar dialog and throw away the year it hands back, so the user picks a year that means
  nothing. A dedicated month-day picker is a Phase 3 nicety.
- **No cross-field date ordering** (ruling 4): nothing refuses a retirement before a purchase, or a
  warranty that expired before the asset arrived.

**Out of scope by the spec (spec §2, "Not in 2B-2").** Schedules, reminders and every other
consumer of the season window beyond presentation (Phase 3); attachments; supplies; a canonical
equipment-type key (D4 §4's standing decision); rolling a child's ledger or readings up into its
parent; nested tree browsing in the Assets list; reset from template; and the 2B-1 minors that no
2B-2 task happened to touch.

## 8. What 2B-2 changed for Phase 3

1. **The season window has an owner and exactly one consumer.** `Season.inSeason(start, end, today)`
   is pure, total and tested on every boundary, and the only thing that reads it today is a badge.
   Phase 3's scheduler can ask the same function the same question without inheriting any
   presentation decision — and without re-deciding what Feb 29 means.
2. **Retirement is data, not a status.** `retired_on` is a date the user picked and `isRetired` is
   `retiredOn != null`. A schedule that must stop when the machine leaves service has a date to
   compare against rather than a lifecycle enum to interpret, and retirement composes freely with
   archiving instead of competing with it.
3. **`AssetTree` is the single authority on composition.** `children`, `descendants`, `wouldCycle`
   and `parentsFirst` are pure functions over a collection of assets, used by the use cases, the
   picker, the COMPONENTS section, the backup importer and the Room wipe. Anything Phase 3 wants to
   roll up — a parent's due list, a component's schedule inherited from its system — walks the same
   tree the same way.
4. **The children-first wipe is solved once.** The parent FK is `ON DELETE RESTRICT`, which protects
   the tree and also blocks a naive `DELETE FROM asset`. `assets.deleteAll()` now orders its deletes
   by `AssetTree.parentsFirst` reversed, so import-replace and the test harness both work. Any
   future table that points at `asset` inherits a store that already knows how to empty itself.
5. **Navigation is two tabs, and the third destination pattern is established.** `TopLevelRoutes` is
   the one list `BottomBar` iterates, so Phase 3's new screens push rather than compete for a tab,
   and `Route.Scan` under Settings is the worked example of a utility that used to be a tab.
6. **Backup format 4 validates the shape it is given.** The reader refuses an unknown parent, a
   cycle and a bad season before anything is written, and the importer writes parents first. A
   Phase 3 format 5 inherits both the branch-on-version reader and the topological write order.

## 9. Final gate

```
./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease \
    :app:compileDebugAndroidTestKotlin
```

→ **BUILD SUCCESSFUL**, 113 actionable tasks — 15s on a cold build cache, about a second on a warm
one. Run twice: once before the documentation was written and once after, with the same totals and
byte-identical APKs.

Test totals from the JUnit XML: **`:core` 272 tests, 0 failures, 0 skipped**; **`:app` 158 tests, 0
failures, 0 skipped** (per-class breakdown in §3). Instrumented: **37 tests, 0 failures, 0
skipped** on the owner's Android 17 phone (§3, §4 row 2). The five test corrections of §3 touched
test code only — no production file changed after the first full run — and the whole gate was
re-run from `clean` afterwards regardless.

APK sizes:

```
-rw-r--r--. 13559205  app/build/outputs/apk/debug/app-debug.apk
-rw-r--r--.  9790254  app/build/outputs/apk/release/app-release.apk
```

(debug ≈ 13241 KiB, release ≈ 9561 KiB — up from 13113 / 9465 KiB in 2B-1: exactly 128 KiB debug
and 96 KiB release for four new `:core` owners, five use cases, the v4 migration, the grouped
editor and the rewritten asset screen.) The release APK is signed with the real `noteNFC` release
certificate, not a debug key:

```
Signer #1 certificate DN: CN=noteNFC, O=GonzRon
Signer #1 certificate SHA-256 digest: 0902d3b0f826381905c6d8254fdaf36756924d80da7c19b90a08930b33277a9f
```

`versionCode` 5, `versionName` `2.3`; the phone reports the same after the reinstall (§4 rows 1, 2).

No personal path or username anywhere in the tree:

```
git grep -nIiE '/home/[a-z]+|<the owner's username>|<the phone's model and codename>' -- . ':!.superpowers'
```

→ three hits, and none is a leak. `NfcDispatchActivity.kt` line 31 says "every pixel the app
draws", which the case-insensitive model-name pattern matches; the other two are the sentences in
`phase-2b1-evidence.md` §9 and in this section that quote that line in order to explain it. The
command above no longer matches anything itself, because the two private words in it are
placeholders. No path, no username, no device serial and no phone model in any source file,
document or resource — including `AssetModelDeviceProofTest`, which names no device at all.

No asset kind outside the seed data and the suggestion catalog:

```
grep -rn 'hot_tub\|power_equipment\|ro_water\|"ups"' app/src/main core/src/main --include=*.kt
```

→ eleven hits, in exactly two files plus one glyph. Four are the `key =` lines of four of the five
templates in `core/.../journal/SeedTemplates.kt` (the fifth, `generic`, is not in the pattern).
Six are in `core/.../journal/CategorySuggestions.kt`, and they are the whole point of spec §8: a
catalog row may carry a `suggestedTemplateKey`, which the new-asset form reads **once, while
creating the asset**, to pre-select a starter template the user can override — proven on device in
§4 row 7 and in `AssetViewModelsTest.explicitTemplateSurvivesCategoryChange`. Nothing reads the
category string after creation. The eleventh is `AssetDetailScreen.kt` line 763, the word `"ups"`
inside the icon-guessing keyword list
(`any("power", "battery", "electric", "ups", "meter", "gauge")`), a substring match on an asset's
free-text category that picks a glyph and nothing else.

Neither template picker names a key — both iterate `SeedTemplates.all` — no table has an
equipment-type column, and no screen, query or branch asks what kind of thing an asset is. Exit
criterion 7 holds. (`app/src/androidTest` names `hot_tub` and `ro_water` when it seeds the asset a
test needs, which is a test choosing a fixture, not the app branching on one.)
