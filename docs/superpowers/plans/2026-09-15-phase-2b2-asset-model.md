# Phase 2B-2 — Physical Asset Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An asset carries its full physical metadata, can be part of another asset, can be retired as data, can declare a season with one presentational effect, and the app's navigation drops the Scan tab in favour of ambient reads plus a Settings utility.

**Architecture:** `:core` gains the asset fields, `AssetTree` (single owner of cycle detection and the parents-first order), `Season` (single owner of in-window logic incl. Feb 29), `CategorySuggestions`, and the asset use cases; `:app` gains Room v4 (asset table recreate for the parent FK), backup format 4 with parents-first restore, the grouped asset editor, the enriched asset detail and list, and the two-tab navigation.

**Tech Stack:** as 2B-1.

**Spec:** `docs/superpowers/specs/2026-09-15-phase-2b2-asset-model-design.md` (read it first).

## Global Constraints

- All of the 2A/2B-1 plan constraints (no Android in `:core`; entry-scoped ViewModels; no raw colours outside `ui/theme`; states wording+glyph+colour; no FAB/cards; commits casual with no attribution line; no pushes from the worktree; privacy grep clean with no username, device name or path quoted; gate per task `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`).
- **Single owners:** `AssetTree.wouldCycle`/`parentsFirst` for hierarchy (use case, picker, codec); `Season.inSeason` for the window (UI now, Phase 3 later); `CategorySuggestions` for the catalog.
- **Category is free text; template hints are creation-time only and never override an explicit choice** (`templateTouched`).
- **Retirement is data:** `retiredOn != null`; `AssetStatus` = ACTIVE | ARCHIVED; the follow-on event is optional and independent.
- **Season:** inclusive month-days, `start > end` wraps, both-or-neither, Feb 29 behaves as Feb 28 in non-leap years; exactly one effect (OUT OF SEASON presentation).
- **Restore order:** assets inserted parents-first regardless of file order; shuffled-file test.
- Room v4 via `MIGRATION_3_4` (asset recreate), `4.json`, `Migration3To4Test` + chained `Migration1To4Test`; `1.json`–`3.json` unchanged. Backup `FORMAT_VERSION = 4`, formats 1–3 decode.
- `versionCode = 5`, `versionName = "2.3"` in Task 8.

## File structure

`:core`: `model/Asset.kt` (fields, `isRetired`, `AssetStatus` without RETIRED), `model/AssetTree.kt`, `model/Season.kt`, `journal/CategorySuggestions.kt`, `usecase/AssetCommands.kt` (`AssetCommand`, `AssetProblem`, `AssetValidation`, `AssetCycle`, `AssetHasChildren`), `usecase/UpdateAsset.kt` (rewritten to take `AssetCommand`), `usecase/CreateAsset.kt` (takes `AssetCommand` + templateKey), `usecase/RetireAsset.kt` (retire/unretire), `usecase/DeleteAsset.kt`, `backup/BackupFormat.kt`, `backup/BackupCodec.kt`, `usecase/ImportBackupReplace.kt`; tests beside them; `testing/InMemoryRepositories.kt` (`AssetRepository.delete` exists; add `children(id)` if needed).
`:app`: `data/room/entities/AssetEntity.kt`, `dao/AssetDao.kt`, `Migrations.kt` (+`MIGRATION_3_4`), `AppDatabase.kt` (v4), `Mappers.kt`, `RoomRepositories.kt`, `di/AppGraph.kt`, `app/schemas/.../4.json`; `ui/asset/{AssetEditScreen,AssetDetailScreen,AssetsScreen,AssetViewModels}.kt`, `ui/components/IdentityPlate.kt` (six cells), `ui/dashboard/DashboardViewModel.kt` (exclude retired), `ui/nav/{Route,BottomBar,NoteNfcApp}.kt`, `ui/settings/SettingsScreen.kt`; tests; `androidTest/.../ui/AssetModelDeviceProofTest.kt`, updated `NavigationSmokeTest`/`AppSmokeTest`; `docs/design/phase-2b2-evidence.md`.

---

### Task 1: Asset fields, `AssetTree`, `Season`, `CategorySuggestions` (`:core`)

**Files:** Modify `core/.../model/Asset.kt`; Create `core/.../model/AssetTree.kt`, `core/.../model/Season.kt`, `core/.../journal/CategorySuggestions.kt`; fix every `AssetStatus.RETIRED` reference (`app/.../ui/asset/AssetsScreen.kt:154` becomes the RETIRED badge from `isRetired` in Task 6 — for now delete the enum branch); Tests `AssetTreeTest.kt`, `SeasonTest.kt`, `CategorySuggestionsTest.kt`.

**Produces (exact):**

```kotlin
enum class AssetStatus { ACTIVE, ARCHIVED }
data class Asset(…existing…, manufacturer: String = "", model: String = "", serialNumber: String = "", purchaseOn: String? = null,
    inServiceOn: String? = null, purchasePriceMinor: Long? = null, currency: String? = null, vendor: String = "", location: String = "",
    warrantyExpiresOn: String? = null, warrantyNotes: String = "", retiredOn: String? = null, parentAssetId: AssetId? = null,
    seasonStartMmdd: String? = null, seasonEndMmdd: String? = null)
val Asset.isRetired: Boolean get() = retiredOn != null

object AssetTree {
    fun wouldCycle(assets: Collection<Asset>, assetId: AssetId, newParentId: AssetId?): Boolean
    fun descendants(assets: Collection<Asset>, assetId: AssetId): Set<AssetId>
    fun children(assets: Collection<Asset>, assetId: AssetId): List<Asset>          // by name, case-insensitive
    fun parentsFirst(assets: Collection<Asset>): List<Asset>                        // Kahn; roots first; ties by id; throws IllegalStateException on a cycle
}
object Season {
    sealed interface Problem { data object BothOrNeither : Problem; data class BadDate(val which: String) : Problem }
    fun validate(start: String?, end: String?): List<Problem>
    fun inSeason(start: String?, end: String?, today: java.time.LocalDate): Boolean  // inclusive; start > end wraps; 02-29 → 02-28 in non-leap years
}
object Money {
    fun fractionDigits(code: String): Int?                       // java.util.Currency; null when unresolvable or negative
    fun parse(text: String, code: String): Long?                 // grouping stripped, "." decimal; null when malformed or too many fraction digits
    fun format(minor: Long, code: String): String                // "123.45 USD", "5000 JPY"
}
data class CategorySuggestion(val label: String, val suggestedTemplateKey: String?)
object CategorySuggestions { val all: List<CategorySuggestion>; fun templateFor(category: String): String? }  // exact label match, case-insensitive
```

- [ ] Tests: `AssetTreeTest` — `selfIsACycle`, `directCycle`, `transitiveCycle`, `preExistingCycleIsDetected`, `parentsFirstPutsRootsFirstFromShuffledInput` (five assets three levels deep, shuffled, assert every parent precedes its children and the order is deterministic), `descendantsAreTransitive`. `SeasonTest` — `yearRoundWhenBothNull`, `bothOrNeither`, `badMonthDay` ("13-01", "02-30", "2-1"), `ordinaryWindowInclusive` (05-01..09-30: Apr 30 false, May 1 true, Sep 30 true, Oct 1 false), `wrappingWindow` (10-15..04-15: Dec 1 true, Jul 1 false, Apr 15 true, Apr 16 false), `oneDaySeason`, `feb29InLeapYear` (start 02-29 in 2028: Feb 29 true, Feb 28 false), `feb29InNonLeapYearBehavesAsFeb28` (start 02-29 in 2027: Feb 28 true, Feb 27 false; end 02-29 in 2027: Feb 28 true, Mar 1 false). `MoneyTest` — `parsesAndFormatsTwoDigitCurrency` (USD "1,234.5" → 123450 → "1234.50 USD"), `zeroDigitCurrency` (JPY "5000" → 5000, "50.5" → null), `tooManyFractionDigitsIsNull`, `unresolvableCodeIsNull`. `CategorySuggestionsTest` — `catalogHasTheTwelveLabels`, `hintsMatchTheSeeds` (RO system → ro_water, Hot tub → hot_tub, Generator/Lawn mower/Snowblower → power_equipment, UPS → ups, Other → generic, Battery → null), `templateForIsCaseInsensitiveAndNullForFreeText`.
- [ ] Run → fail → implement → `./gradlew :core:test` PASS (225 + new) and `./gradlew :app:compileDebugKotlin` (only the RETIRED branch removal needed) → commit `asset fields, tree, season, category suggestions`.

---

### Task 2: Asset use cases (`:core`)

**Files:** Create `usecase/AssetCommands.kt`, `usecase/RetireAsset.kt`, `usecase/DeleteAsset.kt`; Modify `usecase/CreateAsset.kt`, `usecase/UpdateAsset.kt`, `ports/Repositories.kt` (if `AssetRepository` needs `children(id)` — prefer `all()` + `AssetTree`), test fakes; Tests `AssetUseCasesTest.kt` (extend), `RetireDeleteAssetTest.kt`.

**Produces:**

```kotlin
data class AssetCommand(val name: String, val category: String = "", val description: String = "", val notes: String = "",
    val manufacturer: String = "", val model: String = "", val serialNumber: String = "", val purchaseOn: String? = null, val inServiceOn: String? = null,
    val purchasePriceMinor: Long? = null, val currency: String? = null, val vendor: String = "", val location: String = "",
    val warrantyExpiresOn: String? = null, val warrantyNotes: String = "", val parentAssetId: AssetId? = null,
    val seasonStartMmdd: String? = null, val seasonEndMmdd: String? = null)
sealed interface AssetProblem { data object NameRequired; data object BadCurrency; data object CurrencyRequired; data object NegativePrice; data class BadDate(val field: String);
    data class Season(val p: com.loosecannon.notenfc.core.model.Season.Problem); data object UnknownParent }
class AssetValidation(val problems: List<AssetProblem>) : IllegalArgumentException(...)
class AssetCycle(val assetId: AssetId, val parentId: AssetId) : IllegalStateException(...)
class AssetHasChildren(val assetId: AssetId, val children: List<AssetId>) : IllegalStateException(...)
class CreateAsset(assets, uow, ids, clock, applyTemplate) { suspend fun run(cmd: AssetCommand, templateKey: String? = null): Asset }
class UpdateAsset(assets, uow, clock) { suspend fun run(id: AssetId, cmd: AssetCommand): Asset }   // keeps status, retiredOn, templateKey, createdAt
class RetireAsset(assets, uow, clock) { suspend fun retire(id: AssetId, on: String); suspend fun unretire(id: AssetId) }
class DeleteAsset(assets, uow) { suspend fun run(id: AssetId) }   // AssetHasChildren via AssetTree.children over assets.all(); else assets.delete(id) — cascades are the schema's
```

Validation (one function shared by create and update): trimmed name non-blank; currency null or `^[A-Z]{3}$`, and when a price is present it must resolve via `Money.fractionDigits` (`BadCurrency`) — a price without a currency is `CurrencyRequired`; price null or ≥ 0; each date null or ISO `LocalDate`; season via `Season.validate`; parent: null, or exists, and on update `!AssetTree.wouldCycle(all, id, parent)` else `AssetCycle` (on create a parent cannot cycle). Collect problems, throw once. `ArchiveAsset` unchanged and does not touch children.

- [ ] Tests: `updateWithAllFieldsRoundTrips`, `currencyMustBeThreeUpperLetters`, `datesMustParse`, `seasonProblemsSurface`, `unknownParentRefused`, `reparentUnderOwnDescendantIsACycle` (nothing written), `reparentUnderSiblingPasses`, `retireSetsDateAndKeepsStatus`, `unretireClears`, `deleteRefusedWithChildrenNamed`, `deleteWithoutChildrenRemoves`, `archiveDoesNotCascade`; update every existing `CreateAsset.run(name, …)` call site in `:core` tests to the command form (keep a convenience overload `run(name: String, templateKey: String? = null)` to limit churn — allowed).
- [ ] Run → fail → implement → `./gradlew :core:test` PASS → commit `asset use cases: full fields, hierarchy, retire, delete`.

---

### Task 3: Backup format 4 with parents-first restore (`:core`)

**Files:** Modify `backup/BackupFormat.kt` (`AssetDto` + the fields with defaults; `status` mapping without RETIRED), `backup/BackupCodec.kt` (`FORMAT_VERSION = 4`; validate parent resolves, `AssetTree.parentsFirst` succeeds, currency/season/date shapes), `usecase/ImportBackupReplace.kt` (insert assets in `parentsFirst` order; the wipe keeps calling `assets.deleteAll()`, whose contract becomes "children-first" — the in-memory fake may delete in any order, the Room adapter walks `AssetTree.parentsFirst(all).asReversed()` in Task 4); Tests extend `BackupCodecTest`, `BackupUseCasesTest`.
- [ ] Tests: `formatThreeFileStillDecodes` (new fields default), `formatFourRoundTripsATree`, `unknownParentIsCorrupt`, `cycleIsCorrupt`, `retiredStatusNameIsCorrupt`, `badCurrencyIsCorrupt`, `shuffledTreeImportsParentsFirst` (children listed first; a recording/FK-checking asset fake proves order; ids identical after). Run → fail → implement → PASS → commit `backup format 4: asset fields and tree, parents-first restore`.

---

### Task 4: Room v4, repositories, wiring, migration chain (`:app`)

**Files:** Modify `entities/AssetEntity.kt` (new columns; `ForeignKey(AssetEntity::class, ["id"], ["parent_asset_id"], onDelete = RESTRICT)`, `Index("parent_asset_id")`), `dao/AssetDao.kt` + `RoomRepositories.kt` (`RoomAssetRepository.deleteAll` is the single children-first strategy: load `dao.all()`, map to domain, `AssetTree.parentsFirst(all).asReversed().forEach { dao.delete(it.id.value) }` inside the write transaction — no ad hoc SQL; the DAO's plain `DELETE FROM asset` is removed or made private), `Migrations.kt` (+`MIGRATION_3_4`: recreate `asset` from `4.json`, copy v3 columns with `status = CASE status WHEN 'RETIRED' THEN 'ARCHIVED' ELSE status END`, drop, rename, indexes), `AppDatabase.kt` (v4), `Mappers.kt`, `RoomRepositories.kt`, `di/AppGraph.kt` + `FakeGraph.kt` (`retireAsset`, `deleteAsset`; `SCHEMA_VERSION = 4`; `.addMigrations(…, MIGRATION_3_4)`), `4.json`; Tests `Migration3To4Test`, `Migration1To4Test`, `AssetDaoTest` (parent RESTRICT), `RoomRepositoriesTest` (`deleteAll` on root → child → grandchild succeeds), `RestoreProofTest` (a three-level tree survives export → wipe → import with ids exact). Fix `:app` call sites of `CreateAsset`/`UpdateAsset` (ViewModels) minimally so it compiles — the editor UI is Task 5.
- [ ] Gate → commit `room v4: asset columns, parent fk, migration chain`.

---

### Task 5: Asset editor (`:app`)

**Files:** Modify `ui/asset/AssetEditScreen.kt`, `ui/asset/AssetViewModels.kt` (`AssetEditState` grows to the command's fields as text, `templateTouched`, `parentChoices`, `problems`, `seasonYearRound`), `ui/journal/JournalFormat.kt` (date/money formatting helpers if needed); Tests `AssetViewModelsTest` (extend).
Screen per spec §9: sections IDENTITY / PLACEMENT / PURCHASE / WARRANTY / NOTES / TEMPLATE (new only). Category: `ExposedDropdownMenuBox` over `CategorySuggestions.all` with free typing. Hint rule: `onCategory` sets `templateKey = CategorySuggestions.templateFor(category)` only when `!templateTouched && id == null`; `onTemplate` sets `templateTouched = true`. Part of: dropdown over `graph.assets.all()` minus self minus `AssetTree.descendants`, archived marked, "None" first. Season: "Year-round" switch; two month-day pickers (simple `MM-DD` text with a date picker limited to month/day is acceptable). Currency default: `java.util.Currency.getInstance(Locale.getDefault())` in a try/catch, applied once when the form is created new. Price text ↔ minor units only through `Money.parse`/`Money.format`; the field hints the currency's digit count. Dates via `DatePickerDialog` writing ISO. Problems under fields; `AssetCycle` → snackbar naming the parent.
- [ ] Tests: `hintPreselectsTemplateOnNewAsset`, `explicitTemplateSurvivesCategoryChange`, `editingNeverShowsOrChangesTemplate`, `parentChoicesExcludeSelfAndDescendants`, `seasonYearRoundClearsBoth`, `currencyDefaultsFromLocaleOnce`, `saveSendsTheFullCommand`, `cycleRefusalIsAMessage`. Gate → commit `asset editor: identity, placement, purchase, warranty, season, part of`.

---

### Task 6: Asset detail, components, retirement, list (`:app`)

**Files:** Modify `ui/components/IdentityPlate.kt` (six cells 2×3 or a `cells` list that lays out three rows), `ui/asset/AssetDetailScreen.kt` (plate cells CATEGORY / MODEL / SERIAL / LOCATION / IN SERVICE / NFC TAG; badges RETIRED (paused family), ARCHIVED, OUT OF SEASON (season family, `calendar_month`); "Part of <parent>" tappable line; DETAILS section of set fields with "expired" wording; COMPONENTS section with children rows "N readings out of range" from each child's `LatestReadings` (fetched via the repositories in the ViewModel, no roll-up of values), "+ Add component" → `Route.AssetEdit(null)` with a `parentId` argument (extend the route: `AssetEdit(id: String?, parentId: String? = null)`); overflow Retire / Unretire / Delete), `ui/asset/AssetViewModels.kt` (`AssetDetailState` + parent, children with counts, `outOfSeason` from `Season.inSeason(…, today from graph.clock)`; `retire(on)`, `unretire()`, `delete()` → `deleted`; the follow-on dialog state), `ui/asset/AssetsScreen.kt` (subtitle "Part of <parent>", badges, sort active → retired → archived by name), `ui/dashboard/DashboardViewModel.kt` (exclude retired from CURRENT like archived; nudge rule unchanged); Tests `AssetViewModelsTest` (`componentsListChildrenWithOutOfRangeCounts`, `outOfSeasonComputedFromClock`, `retireKeepsAssetAndFollowOnIsOptional`, `deleteRefusedNamesChildren`, list sort), `DashboardViewModelTest` (retired excluded).
- [ ] Gate → commit `asset detail: plate, details, components, retire, list badges`.

---

### Task 7: Navigation — two tabs, Read / inspect tag in Settings (`:app`, docs)

**Files:** Modify `ui/nav/Route.kt` (`TopLevelRoutes = listOf(Dashboard, Assets)`), `ui/nav/BottomBar.kt` (two items), `ui/nav/NoteNfcApp.kt` (Scan stays an entry; dashboard `onScan` pushes `Route.Scan` instead of switching tabs), `ui/settings/SettingsScreen.kt` (row **Read / inspect tag** → `Route.Scan`), `ui/dashboard/DashboardScreen.kt` (empty-state "Scan a tag" pushes the route); update `androidTest` `NavigationSmokeTest`/`AppSmokeTest`/`JournalDeviceProofTest` wherever they tap the Scan tab (reach the scan screen via Settings or the dashboard action); `docs/design/12-visual-design-apollo-service-binder.md` §16 (state that the tab is gone as of 2B-2), root `README.md` (a sentence: tap a tag anywhere; Read / inspect tag under Settings); Tests `NavigationSmokeTest` (two tabs; Settings → Read / inspect tag shows READY TO SCAN) and a JVM `RouteTest` if trivial.
- [ ] Gate → commit `nav: scan tab retired, read/inspect tag lives in settings`.

---

### Task 8: Device proof, evidence, release gate

**Files:** Create `androidTest/.../ui/AssetModelDeviceProofTest.kt` (spec §12 scenarios; inject `today` for the season row through a test-visible clock on `AppGraph` if one exists, else set a window that is out of season on the real date and one that is in season), `docs/design/phase-2b2-evidence.md` (nine sections; §4 row 1 = v3→v4 device migration over a real 2B-1 install: build the 2B-1 APK from `8b8b721` in a scratch worktree, seed through 2B-1's own instrumented suite as Task 8 of 2B-1 did, install 2B-2 over it, pull the DB: `user_version` 4, asset row intact with the new columns, tags/journal intact, `parent_asset_id` NULL, no crash), `app/build.gradle.kts` (`versionCode = 5`, `versionName = "2.3"`), root `README.md` feature line ("Describe equipment fully: make, model, serial, purchase and warranty, location, parts of a larger system, seasons"), `docs/design/README.md` row, D4 §4/§13 sentences per spec §11.
- [ ] Final gate `./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin`; instrumented suite on the attached device (pin `ANDROID_SERIAL`; never write a serial/model/username into a file); privacy grep with placeholders; taxonomy grep explained. Commit `phase 2b-2 evidence, asset model device proof, versionCode 5`.

---

## Self-review

**Spec coverage.** §4 → T1; §5 → T1 (tree), T2 (use cases), T5 (picker), T6 (components/delete); §6 → T1 (Season), T5 (editor), T6 (badge); §7 → T2, T6; §8 → T1, T5; §9 → T5, T6, T7; §10 → T3, T4; §11 → T8 docs; §12 → per-task tests + T8.
**Placeholder scan.** Tests are named with inputs and expected outcomes; no TBD.
**Type consistency.** `AssetCommand` (T2) is what T5's form builds; `AssetTree` (T1) is used by T2, T3, T5, T6; `Season.inSeason` (T1) by T6; `Route.AssetEdit(id, parentId)` extended in T6 and consumed by T6's "+ Add component"; `retireAsset`/`deleteAsset` on the graphs (T4) used by T6; `FORMAT_VERSION = 4` (T3) and `SCHEMA_VERSION = 4` (T4) are separate constants.
