# Phase 2B-1 — Editors and Derived Readings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A user can fully define what an asset measures and which service forms it offers, in the app, with one derived reading kind (PERCENT_DROP) computed from a single event's readings and never stored.

**Architecture:** `:core` gains the `DERIVED` definition kind, the pure `Derived.compute`, the definition/profile use cases and backup format 3; `:app` gains Room v3 (table recreate for the two RESTRICT source FKs), the setup screen, the definition and profile editors, derived rows in the three reading surfaces, and the two 2A UX fixes. Everything the editors write, a seed could have written; nothing branches on a template.

**Tech Stack:** as Phase 2A (Kotlin 2.4.20, Room 3.0.3 + bundled driver, Compose BOM 2026.08.00, Navigation 3 1.1.7 with entry-scoped ViewModels, kotlinx-serialization, JUnit 5 `:core` / JUnit 4 `:app`, androidx.test + Espresso 3.7.0).

**Spec:** `docs/superpowers/specs/2026-09-15-phase-2b1-editors-design.md` (read it first; it extends `2026-09-15-phase-2a-journal-design.md`).

## Global Constraints

- All of the 2A plan's Global Constraints (no Android in `:core`; entry-scoped ViewModels under the existing decorators; no raw colours outside `ui/theme`; state = wording + glyph + colour; no FAB/cards; `EventChronology` owns order; nothing caches a current value; units snapshotted; commits casual with no attribution line; no pushes from the worktree; privacy grep clean; gate per task `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin`).
- **Not a form builder:** no conditional fields, sections, expressions, chained derivations, custom widgets. `DerivedFormula` has exactly one member, `PERCENT_DROP`.
- **Same-event rule:** a derived value is computed only from measurements of one event; `LatestReadings` takes the newest event where it computes; never latest-A plus latest-B.
- **Derived is computed-only:** never a `Measurement` row, never an entry field; backup carries the definition, not values; a measurement referencing a DERIVED definition is invalid everywhere (`shapeMatches` callers and the codec).
- **One validation owner:** `MeasurementDefinition.derivedSpecValid(sources)` in `core.model` is used by `SaveDefinition`, `ApplyTemplate` and `BackupCodec`.
- **Editors write seed-shaped rows:** the use cases mint child ids, keep them on update, and reject anything a seed could not express.
- Schema: v3 via `MIGRATION_2_3` using the Room table-recreate pattern for `measurement_definition` (SQLite cannot ALTER in a foreign key), `3.json` committed, `1.json`/`2.json` unchanged, `Migration2To3Test` + chained `Migration1To3Test`.
- Backup: `FORMAT_VERSION = 3`, new DTO fields defaulted so format 2 decodes.
- `versionCode = 4`, `versionName = "2.2"` in Task 8.

## File structure

`:core`: `model/Journal.kt` (+`DefinitionKind`, `DerivedFormula`, `DerivedSpec`, fields, `derivedSpecValid`), `journal/Derived.kt` (compute), `journal/LatestReadings.kt` (derived rows, `Reading.derivedValue`), `journal/SeedTemplates.kt` (`TemplateDefinition.derived`, ro_water "Rejection"), `usecase/ApplyTemplate.kt` (resolve derived sources), `usecase/DefinitionCommands.kt` + `SaveDefinition.kt`, `ArchiveDefinition.kt`, `DeleteDefinition.kt`, `ReorderDefinitions.kt`, `usecase/ProfileCommands.kt` + `SaveProfile.kt`, `ArchiveProfile.kt`, `DeleteProfile.kt`, `ReorderProfiles.kt`, `backup/BackupFormat.kt`, `backup/BackupCodec.kt`; tests beside them; `testing/InMemoryRepositories.kt` (no interface change expected; `DefinitionRepository` gains `delete(id)`, `ProfileRepository` gains `delete(id)`, `EventRepository` gains `countMeasurementsFor(definitionId)`).
`:app`: `data/room/entities/JournalEntities.kt`, `dao/JournalDaos.kt`, `Migrations.kt` (+`MIGRATION_2_3`), `AppDatabase.kt` (v3), `JournalMappers.kt`, `JournalRepositories.kt`, `di/AppGraph.kt`, `app/schemas/.../3.json`; `ui/journal/{EventEntryScreen,EventEntryViewModel,EventDetailScreen,JournalFormat}.kt`; `ui/asset/{AssetDetailScreen,AssetViewModels}.kt`; new `ui/setup/{AssetSetupScreen,AssetSetupViewModel,DefinitionEditScreen,DefinitionEditViewModel,ProfileEditScreen,ProfileEditViewModel}.kt`; `ui/nav/{Route,NoteNfcApp}.kt`; tests; `androidTest/.../ui/EditorsDeviceProofTest.kt`; `docs/design/phase-2b1-evidence.md`.

---

### Task 1: Derived definitions in the model, computation, latest readings, seeds (`:core`)

**Files:** Modify `core/.../model/Journal.kt`, `core/.../journal/LatestReadings.kt`, `core/.../journal/SeedTemplates.kt`, `core/.../usecase/ApplyTemplate.kt`; Create `core/.../journal/Derived.kt`; Tests `core/src/test/.../journal/DerivedTest.kt`, extend `LatestReadingsTest.kt`, `SeedTemplatesTest.kt`, `ApplyTemplateTest.kt`.

**Interfaces — Produces:**

```kotlin
enum class DefinitionKind { ENTERED, DERIVED }
enum class DerivedFormula { PERCENT_DROP }
data class DerivedSpec(val formula: DerivedFormula, val sourceA: DefinitionId, val sourceB: DefinitionId)
// MeasurementDefinition gains: val kind: DefinitionKind = DefinitionKind.ENTERED, val derived: DerivedSpec? = null
sealed interface DerivedProblem { data object NotNumber; data object IsMeter; data object MissingSpec; data object SpecOnEntered;
    data object SameSource; data class UnknownSource(val id: DefinitionId); data class SourceOtherAsset(val id: DefinitionId);
    data class SourceNotEntered(val id: DefinitionId); data class SourceNotNumber(val id: DefinitionId) }
fun MeasurementDefinition.derivedProblems(sources: Map<DefinitionId, MeasurementDefinition>): List<DerivedProblem>
fun MeasurementDefinition.derivedSpecValid(sources: Map<DefinitionId, MeasurementDefinition>): Boolean = derivedProblems(sources).isEmpty()

object Derived {
    /** Same-event only. null unless kind == DERIVED, both sources present in [event], A != 0, result finite. */
    fun compute(def: MeasurementDefinition, event: AssetEvent, sources: Map<DefinitionId, MeasurementDefinition>): Double?
}
// Reading gains: val derivedValue: Double? = null   (measurement == null for derived rows)
// LatestReadings.of(definitions, events): for DERIVED defs, the newest event where compute != null; archived sources → null → "—"
// TemplateDefinition gains: val derived: Pair<String, String>? = null  (source keys); TemplateDefinition for a derived row has valueType NUMBER, isMeter false
// SeedTemplates ro_water gains TemplateDefinition("rejection_percent", "Rejection", "%", NUMBER, 1, null, null, isMeter = false, derived = "tds_prefilter" to "tds_post_membrane")
// ApplyTemplate resolves derived source keys to the freshly minted ids; inserts ENTERED definitions before DERIVED ones
```

`compute` for PERCENT_DROP: `a = valueNum of sourceA in event`, `b = valueNum of sourceB in event`; `if (a == null || b == null || a == 0.0) null else ((a - b) / a * 100).takeIf { it.isFinite() }`. `compute` returns null when either source definition is archived (`sources[id]?.archivedAt != null`).

- [ ] **Step 1: Failing tests.** `DerivedTest`: `computesFromOneEvent` (310, 18 → 94.19…, assert `abs(v - 94.1935) < 0.001`), `missingSourceIsNull`, `zeroFeedIsNull`, `archivedSourceIsNull`, `enteredDefinitionIsNull`, `sourcesFromDifferentEventsAreNeverCombined` (event 1 has A only, event 2 has B only → `LatestReadings` derived row has null value even though both "latest" exist). `LatestReadingsTest.derivedRowComesFromNewestComputableEvent` (event 1 full → 94.2; newer event 2 with A only → still 94.2 from event 1, `occurredOn` = event 1's). `SeedTemplatesTest.roWaterHasADerivedRejection` (kind DERIVED, sources resolve to keys). `ApplyTemplateTest.derivedSourcesResolveToMintedIds`. `derivedProblems` cases: spec on ENTERED, missing spec, same source, other asset, source DERIVED, source TEXT, meter.
- [ ] **Step 2: Run** `./gradlew :core:test --tests '*Derived*' --tests '*LatestReadings*' --tests '*SeedTemplates*' --tests '*ApplyTemplate*'` → compile failure.
- [ ] **Step 3: Implement** per Interfaces. Defaults on the new fields keep every existing constructor call compiling.
- [ ] **Step 4: Run** `./gradlew :core:test` → PASS (173 + new).
- [ ] **Step 5: Commit** `derived definitions: model, compute, latest readings, ro_water rejection`

---

### Task 2: Definition and profile use cases (`:core`)

**Files:** Modify `core/.../ports/Repositories.kt` (`DefinitionRepository.delete(id)`, `ProfileRepository.delete(id)`, `EventRepository.countMeasurementsFor(definitionId: DefinitionId): Int`), `core/src/test/.../testing/InMemoryRepositories.kt`; Create `usecase/DefinitionCommands.kt`, `SaveDefinition.kt`, `ArchiveDefinition.kt`, `DeleteDefinition.kt`, `ReorderDefinitions.kt`, `usecase/ProfileCommands.kt`, `SaveProfile.kt`, `ArchiveProfile.kt`, `DeleteProfile.kt`, `ReorderProfiles.kt`; Tests `DefinitionUseCasesTest.kt`, `ProfileUseCasesTest.kt`.

**Interfaces — Produces:**

```kotlin
data class DefinitionCommand(val assetId: AssetId, val key: String /* "" = generate */, val label: String, val unit: String,
    val kind: DefinitionKind, val valueType: ValueType, val decimals: Int, val rangeLow: Double?, val rangeHigh: Double?,
    val isMeter: Boolean, val formula: DerivedFormula?, val sourceA: DefinitionId?, val sourceB: DefinitionId?)
sealed interface DefinitionProblem { data object LabelRequired; data object BadKey; data object KeyTaken; data object BadDecimals;
    data object RangeOrder; data object RangeOnNonNumber; data object MeterOnNonNumber; data class Derived(val p: DerivedProblem) }
class DefinitionValidation(val problems: List<DefinitionProblem>) : IllegalArgumentException(...)
class DefinitionInUse(val id: DefinitionId, val measurements: Int) : IllegalStateException(...)   // type/kind change refused
class DefinitionReferenced(val id: DefinitionId, val measurements: Int, val derivedBy: List<DefinitionId>, val profiles: List<ProfileId>) : IllegalStateException(...)
class DefinitionWouldBreakDerived(val id: DefinitionId, val dependentDerivedIds: List<DefinitionId>) : IllegalStateException(...)   // prospective graph check on update

class SaveDefinition(definitions, events, profiles, assets, uow, ids, clock) { suspend fun run(id: DefinitionId?, cmd: DefinitionCommand): MeasurementDefinition }
class ArchiveDefinition(definitions, uow, clock) { suspend fun run(id: DefinitionId, archived: Boolean) }
class DeleteDefinition(definitions, events, profiles, uow) { suspend fun run(id: DefinitionId) }
class ReorderDefinitions(definitions, uow, clock) { suspend fun run(assetId: AssetId, orderedIds: List<DefinitionId>) }

data class ProfileFieldInput(val definitionId: DefinitionId, val required: Boolean)
data class ProfileConsumableInput(val id: String? /* keep on update */, val name: String, val defaultQuantity: Double?, val unit: String)
data class ProfileCommand(val assetId: AssetId, val name: String, val eventKind: EventKind, val defaultTitle: String,
    val fields: List<ProfileFieldInput>, val consumables: List<ProfileConsumableInput>)
sealed interface ProfileProblem { data object NameRequired; data object NameTaken; data class BadField(val id: DefinitionId, val reason: String); data class BadConsumable(val index: Int) }
class ProfileValidation(val problems: List<ProfileProblem>) : IllegalArgumentException(...)
class SaveProfile(profiles, definitions, assets, uow, ids, clock) { suspend fun run(id: ProfileId?, cmd: ProfileCommand): EventProfile }
class ArchiveProfile(profiles, uow, clock) { suspend fun run(id: ProfileId, archived: Boolean) }
class DeleteProfile(profiles, uow) { suspend fun run(id: ProfileId) }
class ReorderProfiles(profiles, uow, clock) { suspend fun run(assetId: AssetId, orderedIds: List<ProfileId>) }
```

Rules (spec §6): key slug regex `^[a-z][a-z0-9_]{0,39}$`, generation from label (`lowercase`, runs of non-`[a-z0-9]` → `_`, trimmed, `_2`, `_3` suffix on collision), unique per asset among all definitions (archived included); on update the key may change only while `countMeasurementsFor(id) == 0`; `valueType`/`kind` change with measurements → `DefinitionInUse`; derived problems via Task 1's `derivedProblems` over the asset's definitions (excluding the definition being edited); **prospective graph check on update:** substitute the edited definition into the asset's definition map and run `derivedProblems` for every other DERIVED definition — any that becomes invalid → `DefinitionWouldBreakDerived(id, dependents)` and nothing is written (relabel/unit/range/key/archive of a source still pass); `ProfileFieldInput` definitions must be the asset's, ENTERED, unarchived, distinct; `SaveProfile` on update keeps `ProfileField` ids for definitions already present and `ProfileConsumable` ids passed in; `DeleteDefinition` counts measurements via `countMeasurementsFor`, derived references via `definitions.forAsset`, profile references via `profiles.forAsset`. Each use case = one `uow.write`, no nested use-case `run`.

- [ ] **Step 1: Failing tests** (fakes + hot_tub seed): `keyGeneratedFromLabelAndDeduplicated`, `keyTakenIsRefused`, `rangeOrderAndNonNumberRulesCollected`, `typeChangeRefusedWhenDataExists`, `derivedDefinitionValidatedAgainstAssetSources` (self-reference, TEXT source, other asset), `sourceUsedByDerivedCannotBecomeText`, `sourceUsedByDerivedCannotBecomeDerived` (both → `DefinitionWouldBreakDerived` naming the rejection definition; nothing stored), `sourceUsedByDerivedCanBeRelabelledOrArchived`, `deleteRefusedListsReferences` (a measurement, a derived def, a profile field → all three named), `deleteSucceedsWhenUnreferenced`, `archiveAndUnarchive`, `reorderRewritesSortOrder`; profiles: `nameUniquePerAssetCaseInsensitive`, `fieldMustBeEnteredUnarchivedOnAsset`, `updateKeepsChildIds`, `deleteProfileLeavesEventsWithProfileCleared` (fake must SET NULL like the schema — implement in `InMemoryEventRepository` via a hook the profile fake calls, or assert through a helper the test seeds), `reorderProfiles`.
- [ ] **Step 2: Run** → compile failure. **Step 3: Implement.** **Step 4:** `./gradlew :core:test` → PASS. **Step 5: Commit** `definition and profile use cases`

---

### Task 3: Backup format 3 (`:core`)

**Files:** Modify `backup/BackupFormat.kt`, `backup/BackupCodec.kt`, `usecase/ImportBackupReplace.kt`; Tests extend `BackupCodecTest.kt`, `BackupUseCasesTest.kt`.

**Produces:** `MeasurementDefinitionDto` + `kind: String = "ENTERED"`, `formula: String? = null`, `sourceAId: String? = null`, `sourceBId: String? = null`; `FORMAT_VERSION = 3`; validation: enum names known; `derivedProblems` over the file's definitions must be empty for every DERIVED row (`BackupCorrupt` names the definition and the first problem); any measurement whose definition is DERIVED → `BackupCorrupt` naming the measurement; import inserts ENTERED definitions before DERIVED ones (sources exist when the FK is checked).

- [ ] Tests: `formatTwoFileStillDecodes` (kind defaults ENTERED), `formatThreeRoundTripsDerivedDefinition`, `derivedDefinitionWithBadSourceIsCorrupt`, `measurementOnDerivedDefinitionIsCorrupt`, `importInsertsEnteredBeforeDerived` (fake with FK-like check or order assertion via a recording fake). Run → fail → implement → `./gradlew :core:test` PASS → commit `backup format 3: derived definitions, format 2 still imports`.

---

### Task 4: Room v3, repositories, graph wiring, migration chain (`:app`)

**Files:** Modify `entities/JournalEntities.kt` (`MeasurementDefinitionEntity` + `kind`, `formula`, `source_a_id`, `source_b_id` with `ForeignKey(MeasurementDefinitionEntity::class, ["id"], ["source_a_id"], onDelete = RESTRICT)` ×2 and `Index("source_a_id")`, `Index("source_b_id")`), `dao/JournalDaos.kt` (`DefinitionDao.delete(id)`, `ProfileDao.delete(id)`, `EventDao.countMeasurementsFor(definitionId)`), `Migrations.kt` (+`MIGRATION_2_3`), `AppDatabase.kt` (v3), `JournalMappers.kt`, `JournalRepositories.kt`, `di/AppGraph.kt` + test `FakeGraph.kt` (wire `saveDefinition`, `archiveDefinition`, `deleteDefinition`, `reorderDefinitions`, `saveProfile`, `archiveProfile`, `deleteProfile`, `reorderProfiles`; `SCHEMA_VERSION = 3`; `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)`); Create `app/schemas/.../3.json`; Tests `Migration2To3Test.kt`, `Migration1To3Test.kt`, extend `JournalDaoTest.kt` (`deletingASourceOfADerivedDefinitionIsRefused`, `deleteDefinitionCascadesProfileFields`, `countMeasurementsFor`), `RestoreProofTest.kt` (derived definition survives with its sources).

`MIGRATION_2_3`: `CREATE TABLE _new_measurement_definition (…3.json createSql…)`, `INSERT INTO _new_measurement_definition (…v2 columns…, kind) SELECT …, 'ENTERED' FROM measurement_definition`, `DROP TABLE measurement_definition`, `ALTER TABLE _new_measurement_definition RENAME TO measurement_definition`, then the indexes from `3.json` (the existing three plus the two new). Run with `PRAGMA foreign_keys` as Room's migration does (Room disables FK checks during `migrate`; note it in a comment). `Migration1To3Test` builds v1 from `1.json` and opens with both migrations.

- [ ] Tests → fail → implement (build once for `3.json`) → `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` PASS → commit `room v3: derived definition columns, migration chain`.

---

### Task 5: Derived rows in the three reading surfaces, entry-form fixes, asset-detail hook (`:app`)

**Files:** Modify `ui/journal/EventEntryViewModel.kt` (rows exclude archived unless carried; `derivedRows: List<Reading>` recomputed from the typed values on every change via `Derived.compute` over a synthetic `AssetEvent` built from parsed finite values), `ui/journal/EventEntryScreen.kt` (`Column` + `verticalScroll(rememberScrollState())` replacing `LazyColumn`; derived `InstrumentRow`s under the inputs with eyebrow DERIVED), `ui/journal/EventDetailScreen.kt` + its ViewModel (derived rows from the stored event), `ui/journal/JournalFormat.kt` (`formatValue` for `Reading.derivedValue`; merge `plain()`/`quantity()` into one `formatNumber`), `ui/asset/AssetViewModels.kt` (`bare` = no definitions at all, archived included; readings include derived via Task 1), `ui/asset/AssetDetailScreen.kt` (outlined action **Readings & actions** → `onSetup(assetId)`), `ui/nav/Route.kt` (+`AssetSetup(assetId)`, `DefinitionEdit(assetId, definitionId?)`, `ProfileEdit(assetId, profileId?)`), `ui/nav/NoteNfcApp.kt` (wire `onSetup`; entries land in Tasks 6–7); Tests extend `EventEntryViewModelTest` (`derivedRowUpdatesLiveFromTypedValues`, `archivedDefinitionGetsNoRowUnlessCarried`), `AssetViewModelsTest` (`bareIgnoresNothingArchivedCountsAsExisting`, `readingsIncludeDerived`), `JournalFormatTest`.

- [ ] Tests → fail → implement → gate PASS → commit `derived readings on screen, entry form scrolls, archived handled`.

---

### Task 6: Readings & actions screen and the definition editor (`:app`)

**Files:** Create `ui/setup/AssetSetupViewModel.kt` (`AssetSetupState(assetName, definitions: List<MeasurementDefinition>, profiles: List<EventProfile>, sourcesById)`, `reorderDefinitions(ids)`, `reorderProfiles(ids)`, `archiveDefinition(id, Boolean)`, `archiveProfile(id, Boolean)`, `deleteDefinition(id)` → `DefinitionReferenced` surfaces as a `refusal: DefinitionReferenced?` state the screen renders in a dialog, `deleteProfile(id)`), `ui/setup/AssetSetupScreen.kt` (spec §9: READINGS rows with kind glyph, derived formula line in mono "= (A − B) / A × 100" with the source labels, target text, ARCHIVED badge, up/down in an overflow per row — ruling: overflow up/down rather than drag, simplest correct; "+ Add reading"; ACTIONS rows; "+ Add action"), `ui/setup/DefinitionEditViewModel.kt` (`DefinitionEditState` mirroring `DefinitionCommand` as text fields + `problems: Map<String, String>` + `inUse: Int`, `save()` with guard → `saved`), `ui/setup/DefinitionEditScreen.kt` (spec §9 fields; Kind and Type segmented; Source A/B `ExposedDropdownMenuBox` over ENTERED NUMBER definitions; overflow Archive/Unarchive/Delete with the refusal dialog naming counts and names); Modify `NoteNfcApp.kt` (three entries; `DefinitionEdit` navigation from setup); Tests `AssetSetupViewModelTest` (lists, reorder, delete refusal surfaces), `DefinitionEditViewModelTest` (new ENTERED saves; new DERIVED with sources saves; key auto-generation shown; type change disabled when in use; problems map).

- [ ] Tests → fail → implement → gate PASS (androidTest compiles) → commit `readings & actions screen, definition editor`.

---

### Task 7: Profile editor (`:app`)

**Files:** Create `ui/setup/ProfileEditViewModel.kt` (`ProfileEditState(name, eventKind, defaultTitle, fields: List<FieldPick(definition, required)>, available: List<MeasurementDefinition>, consumables: List<ConsumableEdit(id?, name, qty, unit)>, problems, saving, loaded)`, `addField(definitionId)`, `removeField`, `moveField(index, delta)`, `setRequired`, `addConsumable`, `editConsumable`, `removeConsumable`, `save()` → `saved`, `archive`, `delete`), `ui/setup/ProfileEditScreen.kt` (spec §9); Modify `NoteNfcApp.kt` (entry). Tests `ProfileEditViewModelTest` (new profile with two fields and a material saves in order; update keeps child ids; name taken → problem; picker offers only unarchived ENTERED definitions not chosen).

- [ ] Tests → fail → implement → gate PASS → commit `profile editor`.

---

### Task 8: Device proof, evidence, release gate

**Files:** Create `app/src/androidTest/.../ui/EditorsDeviceProofTest.kt` (spec §12's five scenarios, cold-start pattern, `value-<key>` tags; assert "Rejection" row reads "94.2" after one TDS test of 310/18/12 and still "94.2" after a second test with pre-filter only; the delete refusal dialog names "1 reading"), `docs/design/phase-2b1-evidence.md` (nine sections; §4 rows: install over the 2A build + launch (v2→v3 device migration: reinstall the 2A APK first — build it from `7fe7079` in a scratch worktree — create an asset, then install 2B-1 over it and check `user_version` 3 and rows intact), the instrumented suite, then the automated rows; no manual rows), `app/build.gradle.kts` (`versionCode = 4`, `versionName = "2.2"`), root `README.md` feature line ("Define your own readings and service forms per asset; derived readings such as RO rejection"), `docs/design/README.md` row; amend D4 §7's computed-fields sentence and D4 §13's delete-definition row per spec §11 (docs).
- [ ] Gate `./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin`; privacy grep with the username written as `<the owner's username>`; template-key grep; commit `phase 2b-1 evidence, editors device proof, versionCode 4`.

---

## Self-review

**Spec coverage.** §4 → T1; §5 → T1 (+T5 surfaces); §6 → T2 (+T1 ApplyTemplate); §7 → T4; §8 → T3 (+T4 restore proof); §9 → T5 (asset detail hook, entry/detail/current readings), T6 (setup + definition editor), T7 (profile editor); §10 → T5; §11 → T8 docs; §12 → tests per task + T8.
**Placeholder scan.** Tasks 3–8 describe tests by name and assertion rather than full code; each names inputs and expected outcomes. No TBD/"handle edge cases".
**Type consistency.** `DerivedSpec`/`derivedProblems` (T1) used by T2, T3; `DefinitionReferenced` (T2) rendered by T6; `Reading.derivedValue` (T1) consumed by T5; routes declared in T5, entered in T6/T7; `countMeasurementsFor` on the port (T2) implemented by T4's DAO; `FORMAT_VERSION = 3` (T3) and `SCHEMA_VERSION = 3` (T4) are different numbers that happen to coincide — keep them separate constants.
