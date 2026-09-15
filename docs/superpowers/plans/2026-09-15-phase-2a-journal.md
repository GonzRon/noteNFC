# Phase 2A — Maintenance Journal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scan an asset and record what happened to it: profile-driven events with typed measurements and materials used, shown back as current readings and a service record, carried by backup, proven on a hot tub, a UPS and a mower.

**Architecture:** `:core` gains the journal model (definitions, profiles, events as an aggregate), the chronology comparator that every derived value depends on, seed templates as typed data, and the use cases; `:app` gains Room v2 with seven tables and a hand-written migration, repositories, backup format 2, and three UI surfaces (asset-detail readings/record/quick actions, the generic entry route, event detail). Nothing branches on an asset type.

**Tech Stack:** Kotlin 2.4.20, Room 3.0.3 (bundled SQLite driver in JVM tests, `SQLiteDriverMigrationTestHelper`), Compose BOM 2026.08.00 / Material3, Navigation 3 1.1.7 with `lifecycle-viewmodel-navigation3` 2.10.0, kotlinx-serialization, JUnit 5 (`:core`) / JUnit 4 (`:app`), androidx.test 1.7 + Espresso 3.7.0 for the device suite.

**Spec:** `docs/superpowers/specs/2026-09-15-phase-2a-journal-design.md` (read it first; D4 §4–§7, D7 Phase 2, D12 §8–§9, G1 §1.3 sit behind it).

## Global Constraints

- `:core` has no Android imports; business rules live there. `:app` maps and renders.
- Every screen ViewModel is created inside a `NavDisplay` entry (`NoteNfcApp.kt` already passes `rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`); never add a second `NavDisplay` and never remove those decorators.
- No raw `Color(0x…)` outside `ui/theme`. Range states use exactly `NoteNfcTheme.semanticColors.measurementLow / measurementInRange / measurementHigh / measurementNoTarget` with the wording **LOW / IN RANGE / HIGH / NO TARGET SET**. State = position + wording + glyph + colour; never colour alone.
- D12 restraint: no cards for everything, no dials, no stripes, no FAB, uppercase eyebrows, mono numbers and ids, no exclamation marks.
- Chronology has one owner: `EventChronology` (spec §4.1). Nothing else decides "newest". Insertion order and `updatedAt` never participate.
- Current readings are derived from the event list (spec §4.2); nothing caches a "current" value.
- Units are snapshotted onto each `Measurement` at save; stored values are never rounded.
- Child rows (`profile_field`, `profile_consumable`, `measurement`, `consumable_usage`) have durable text ids of their own, carried domain → Room → backup verbatim; import never mints replacement ids.
- Ownership: an event, its profile and every definition it measures belong to one asset; `UpdateEvent` never re-parents (spec §6).
- New assets default to **no template** (`templateKey = null`); Generic is an explicit choice (spec §6, §10).
- Seed templates are starter data, not taxonomy: after `ApplyTemplate` runs, no code may branch on a template key. The only places a template key appears are `SeedTemplates`, `ApplyTemplate`, the two pickers, and provenance columns.
- Schema: `exportSchema = true`; version 2 ships `MIGRATION_1_2` and `Migration1To2Test`; `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/2.json` is committed.
- Backup: `BackupCodec.FORMAT_VERSION = 2`; every new list defaults to empty so format-1 files decode; export in one `uow.read`, import in one `uow.write`; ids verbatim; lists sorted by id.
- Columns for later phases stay out of v2: no `schedule_id`, `details_pending`, `cost_minor`, `currency`, `supply_id`, `affects_stock`.
- Commit messages casual/terse/human; **never** any `Co-Authored-By` or AI attribution line; repo-local identity GonzRon. Do not push. Do not touch the sibling checkout of `master`.
- Privacy: no device serial, tag UID, note id/link, phone model, or home path in any tracked file (evidence included).
- Gate for every task: `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` green; `:app:assembleRelease` on the last task.

## File structure

`:core` (`core/src/main/kotlin/com/loosecannon/notenfc/core/`)
- `model/Journal.kt` — ids, enums, `MeasurementDefinition`, `EventProfile` (+`ProfileField`, `ProfileConsumable`), `AssetEvent` (+`Measurement`, `ConsumableUsage`). Data only.
- `model/Asset.kt` — gains `templateKey: String? = null`.
- `journal/EventChronology.kt` — the comparator. `journal/RangeState.kt` — `classify`. `journal/LatestReadings.kt` — `Reading` + `of(...)`.
- `journal/SeedTemplates.kt` — `Template`, `TemplateDefinition`, `TemplateProfile`, the five seeds.
- `ports/Repositories.kt` — `DefinitionRepository`, `ProfileRepository`, `EventRepository`.
- `usecase/ApplyTemplate.kt`, `usecase/CreateAsset.kt` (templateKey), `usecase/EventCommands.kt` (`EventCommand`, `EventValidation`, `validate`), `usecase/LogEvent.kt`, `usecase/UpdateEvent.kt`, `usecase/DeleteEvent.kt`.
- `backup/BackupFormat.kt` (DTOs + mapping), `backup/BackupCodec.kt` (version 2, validation), `usecase/ExportBackup.kt`, `usecase/ImportBackupReplace.kt`.

`:app` (`app/src/main/kotlin/com/loosecannon/notenfc/`)
- `data/room/entities/JournalEntities.kt` — seven entities. `data/room/dao/JournalDaos.kt` — `DefinitionDao`, `ProfileDao`, `EventDao` (+ relation classes). `data/room/Migrations.kt` — `MIGRATION_1_2`. `data/room/AppDatabase.kt` — version 2. `data/room/JournalMappers.kt`, `data/room/JournalRepositories.kt`.
- `di/AppGraph.kt` — new repositories and use cases; `SCHEMA_VERSION = 2`.
- `ui/components/InstrumentRow.kt` — the D12 §9 measurement row (display and entry variants).
- `ui/asset/AssetDetailScreen.kt`, `ui/asset/AssetViewModels.kt` — readings, quick actions, service record, template picker; `AssetEditScreen.kt` template row.
- `ui/journal/EventEntryScreen.kt`, `ui/journal/EventEntryViewModel.kt`, `ui/journal/EventDetailScreen.kt`, `ui/journal/EventDetailViewModel.kt`, `ui/journal/JournalFormat.kt` (value/date formatting helpers).
- `ui/nav/Route.kt` (+`EventEntry`, `EventDetail`), `ui/nav/NoteNfcApp.kt` (two entries).
- Tests: `core/src/test/.../journal/*Test.kt`, `core/src/test/.../usecase/{ApplyTemplate,EventUseCases}Test.kt`, `core/src/test/.../testing/InMemoryRepositories.kt` (three fakes), `app/src/test/.../data/room/{Migration1To2Test,JournalDaoTest}.kt`, `app/src/test/.../backup/RestoreProofTest.kt`, `app/src/test/.../ui/journal/EventEntryViewModelTest.kt`, `app/src/test/.../ui/asset/AssetViewModelsTest.kt`, `app/src/test/.../testing/FakeGraph.kt`, `app/src/androidTest/.../ui/JournalSmokeTest.kt`.
- Docs: `docs/design/phase-2a-evidence.md`, `docs/design/README.md` row, root `README.md` feature line, `app/build.gradle.kts` `versionCode = 3`.

---

### Task 1: Journal model, chronology, range state, latest readings (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/model/Journal.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/journal/EventChronology.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/journal/RangeState.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/journal/LatestReadings.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/model/Asset.kt` (add `templateKey`)
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/journal/EventChronologyTest.kt`, `RangeStateTest.kt`, `LatestReadingsTest.kt`

**Interfaces:**
- Consumes: `AssetId` (`core/model/Ids.kt`).
- Produces: everything in spec §4 verbatim (`ProfileField` and `ProfileConsumable` carry `id: String`), plus `object EventChronology : Comparator<AssetEvent>`, `fun classify(value: Double, low: Double?, high: Double?): RangeState`, `data class Reading(val definition: MeasurementDefinition, val measurement: Measurement?, val occurredOn: String?, val occurredTime: String?, val state: RangeState?)`, `object LatestReadings { fun of(definitions: List<MeasurementDefinition>, events: List<AssetEvent>): List<Reading> }`.

- [ ] **Step 1: Write the failing tests**

`EventChronologyTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EventChronologyTest {
    private fun event(id: String, on: String, time: String? = null, created: Long = 0L) = AssetEvent(
        id = EventId(id), assetId = AssetId("a1"), kind = EventKind.NOTE, title = id,
        profileId = null, occurredOn = on, occurredTime = time, tzId = "UTC", notes = "",
        source = EventSource.MANUAL, sourceRef = null, createdAt = created, updatedAt = created,
        measurements = emptyList(), consumables = emptyList(),
    )

    @Test fun laterDateIsNewerRegardlessOfCreation() {
        val backdated = event("old", "2026-09-10", created = 2_000L)   // entered later
        val newer = event("new", "2026-09-15", created = 1_000L)
        assertEquals(listOf(backdated, newer), listOf(newer, backdated).sortedWith(EventChronology))
    }

    @Test fun timedEntrySortsAfterUntimedOnSameDay() {
        val untimed = event("u", "2026-09-15", null, created = 9_000L)
        val timed = event("t", "2026-09-15", "00:00", created = 1L)
        // "00:00" ties with the null default, so created_at decides: t (1L) before u (9000L)
        assertEquals(listOf(timed, untimed), listOf(untimed, timed).sortedWith(EventChronology))
        val afternoon = event("p", "2026-09-15", "14:30", created = 1L)
        assertEquals(listOf(untimed, afternoon), listOf(afternoon, untimed).sortedWith(EventChronology))
    }

    @Test fun sameDayNoTimeOrdersByCreatedAtThenId() {
        val a = event("b", "2026-09-15", created = 5L)
        val b = event("a", "2026-09-15", created = 5L)
        val c = event("c", "2026-09-15", created = 4L)
        assertEquals(listOf(c, b, a), listOf(a, b, c).sortedWith(EventChronology))
    }

    @Test fun newestIsMaxWith() {
        val events = listOf(event("x", "2026-01-01"), event("y", "2026-03-01"), event("z", "2026-02-01"))
        assertEquals("y", events.maxWith(EventChronology).id.value)
    }
}
```

`RangeStateTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.journal

import kotlin.test.Test
import kotlin.test.assertEquals

class RangeStateTest {
    @Test fun noBoundsIsNoTarget() = assertEquals(RangeState.NO_TARGET, classify(7.8, null, null))
    @Test fun boundsAreInclusive() {
        assertEquals(RangeState.IN_RANGE, classify(7.2, 7.2, 7.8))
        assertEquals(RangeState.IN_RANGE, classify(7.8, 7.2, 7.8))
    }
    @Test fun belowLowIsLow() = assertEquals(RangeState.LOW, classify(7.1, 7.2, 7.8))
    @Test fun aboveHighIsHigh() = assertEquals(RangeState.HIGH, classify(7.9, 7.2, 7.8))
    @Test fun oneSidedRanges() {
        assertEquals(RangeState.IN_RANGE, classify(500.0, 80.0, null))
        assertEquals(RangeState.LOW, classify(10.0, 80.0, null))
        assertEquals(RangeState.HIGH, classify(500.0, null, 120.0))
        assertEquals(RangeState.IN_RANGE, classify(0.0, null, 120.0))
    }
}
```

`LatestReadingsTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LatestReadingsTest {
    private val ph = MeasurementDefinition(
        id = DefinitionId("d-ph"), assetId = AssetId("a1"), key = "ph", label = "pH", unit = "",
        valueType = ValueType.NUMBER, decimals = 1, rangeLow = 7.2, rangeHigh = 7.8, isMeter = false,
        sortOrder = 0, archivedAt = null, createdAt = 0L, updatedAt = 0L,
    )
    private val temp = ph.copy(id = DefinitionId("d-t"), key = "water_temp", label = "Water temperature", unit = "°F", rangeLow = null, rangeHigh = null, sortOrder = 1)
    private val archived = ph.copy(id = DefinitionId("d-x"), key = "old", archivedAt = 1L, sortOrder = 2)

    private fun event(id: String, on: String, created: Long, vararg values: Pair<DefinitionId, Double>) = AssetEvent(
        id = EventId(id), assetId = AssetId("a1"), kind = EventKind.MEASUREMENT, title = "Water test",
        profileId = null, occurredOn = on, occurredTime = null, tzId = "UTC", notes = "",
        source = EventSource.MANUAL, sourceRef = null, createdAt = created, updatedAt = created,
        measurements = values.mapIndexed { i, (d, v) -> Measurement("m-$id-$i", d, v, null, "", i) },
        consumables = emptyList(),
    )

    @Test fun newestByOccurrenceWinsNotByInsertion() {
        val events = listOf(
            event("e1", "2026-09-12", 1L, ph.id to 7.4),
            event("e2", "2026-09-15", 2L, ph.id to 7.8),
            event("e3", "2026-09-10", 3L, ph.id to 7.0),   // backdated, entered last
        )
        val r = LatestReadings.of(listOf(ph, temp, archived), events)
        assertEquals(listOf("ph", "water_temp"), r.map { it.definition.key })   // archived omitted, sortOrder kept
        assertEquals(7.8, r[0].measurement?.valueNum)
        assertEquals("2026-09-15", r[0].occurredOn)
        assertEquals(RangeState.HIGH, r[0].state)
        assertNull(r[1].measurement); assertNull(r[1].state)                   // no reading yet
    }

    @Test fun deletingNewestFallsBackToPrevious() {
        val e1 = event("e1", "2026-09-12", 1L, ph.id to 7.4)
        val e2 = event("e2", "2026-09-15", 2L, ph.id to 7.8)
        assertEquals(7.8, LatestReadings.of(listOf(ph), listOf(e1, e2))[0].measurement?.valueNum)
        assertEquals(7.4, LatestReadings.of(listOf(ph), listOf(e1))[0].measurement?.valueNum)
    }

    @Test fun editingNewestDateBackwardsChangesWhichIsCurrent() {
        val e1 = event("e1", "2026-09-12", 1L, ph.id to 7.4)
        val e2 = event("e2", "2026-09-15", 2L, ph.id to 7.8)
        val moved = e2.copy(occurredOn = "2026-09-01")
        assertEquals(7.4, LatestReadings.of(listOf(ph), listOf(e1, moved))[0].measurement?.valueNum)
    }

    @Test fun editingValueShowsNewValueAndNoTargetState() {
        val e = event("e", "2026-09-15", 1L, temp.id to 100.0)
        val r = LatestReadings.of(listOf(temp), listOf(e.copy(measurements = listOf(e.measurements[0].copy(valueNum = 102.0)))))
        assertEquals(102.0, r[0].measurement?.valueNum)
        assertEquals(RangeState.NO_TARGET, r[0].state)
    }

    @Test fun eventWithoutThatDefinitionIsSkipped() {
        val e1 = event("e1", "2026-09-12", 1L, ph.id to 7.4)
        val e2 = event("e2", "2026-09-15", 2L, temp.id to 100.0)   // newer but no pH
        assertEquals(7.4, LatestReadings.of(listOf(ph), listOf(e1, e2))[0].measurement?.valueNum)
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:test --tests 'com.loosecannon.notenfc.core.journal.*'`
Expected: compilation failure (types missing).

- [ ] **Step 3: Implement**

`model/Asset.kt`: add `val templateKey: String? = null,` after `status`.

`model/Journal.kt` — exactly the spec §4 types, with `@JvmInline value class DefinitionId(val value: String)`, `ProfileId`, `EventId` following `Ids.kt`'s style (put them in `Ids.kt` if that file holds all ids).

`journal/EventChronology.kt`:

```kotlin
package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.AssetEvent

/**
 * The one ordering of events (spec §4.1): calendar date, then time with an untimed entry
 * sorting as "00:00", then creation instant, then id. Total and stable across devices; insertion
 * order and `updatedAt` never participate. "Newest" everywhere means `maxWith(EventChronology)`.
 */
object EventChronology : Comparator<AssetEvent> {
    private const val UNTIMED = "00:00"
    override fun compare(a: AssetEvent, b: AssetEvent): Int =
        compareValuesBy(a, b,
            { it.occurredOn },
            { it.occurredTime ?: UNTIMED },
            { it.createdAt },
            { it.id.value },
        )
}
```

`journal/RangeState.kt`: the enum and `classify` from spec §4.3 verbatim.

`journal/LatestReadings.kt`:

```kotlin
package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.*

data class Reading(
    val definition: MeasurementDefinition,
    val measurement: Measurement?,
    val occurredOn: String?,
    val occurredTime: String?,
    /** null when there is no reading or the definition is not NUMBER. */
    val state: RangeState?,
)

/** Derived on every call from the event list; never cached (spec §4.2). */
object LatestReadings {
    fun of(definitions: List<MeasurementDefinition>, events: List<AssetEvent>): List<Reading> {
        val newestFirst = events.sortedWith(EventChronology.reversed())
        return definitions
            .filter { it.archivedAt == null }
            .sortedBy { it.sortOrder }
            .map { def ->
                var found: Pair<AssetEvent, Measurement>? = null
                for (e in newestFirst) {
                    val m = e.measurements.firstOrNull { it.definitionId == def.id }
                    if (m != null) { found = e to m; break }
                }
                val (event, m) = found ?: return@map Reading(def, null, null, null, null)
                val state = if (def.valueType == ValueType.NUMBER && m.valueNum != null)
                    classify(m.valueNum, def.rangeLow, def.rangeHigh) else null
                Reading(def, m, event.occurredOn, event.occurredTime, state)
            }
    }
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:test`
Expected: PASS; total `:core` = 117 + 14 new.

- [ ] **Step 5: Commit**

```bash
git add core/src && git commit -m "journal model, chronology, range state, latest readings"
```

---

### Task 2: Ports, in-memory fakes, seed templates, ApplyTemplate, CreateAsset(templateKey) (`:core`)

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/ports/Repositories.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/journal/SeedTemplates.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ApplyTemplate.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/CreateAsset.kt`
- Modify: `core/src/test/kotlin/com/loosecannon/notenfc/core/testing/InMemoryRepositories.kt` (add `InMemoryDefinitionRepository`, `InMemoryProfileRepository`, `InMemoryEventRepository`, same `Rollbackable`/`Witnessed`/version-flow pattern as the asset one)
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/journal/SeedTemplatesTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/ApplyTemplateTest.kt`; extend `AssetUseCasesTest` (or `CreateAssetTest` if one exists) with a templateKey case.

**Interfaces:**
- Consumes: Task 1 types.
- Produces: the three repository interfaces exactly as spec §5; `data class TemplateDefinition(key, label, unit, valueType, decimals, rangeLow, rangeHigh, isMeter)`, `data class TemplateConsumable(name: String, defaultQuantity: Double?, unit: String)`, `data class TemplateProfile(name, eventKind, defaultTitle, fields: List<Pair<String /*definition key*/, Boolean /*required*/>>, consumables: List<TemplateConsumable>)`, `data class Template(key, name, definitions: List<TemplateDefinition>, profiles: List<TemplateProfile>)`, `object SeedTemplates { val all: List<Template>; fun byKey(key: String): Template? }`; `class ApplyTemplate(defs, profiles, assets, uow, ids, clock) { suspend fun run(assetId: AssetId, template: Template): ApplyResult }` with `sealed interface ApplyResult { data object AlreadySetUp; data class Applied(val definitions: List<MeasurementDefinition>, val profiles: List<EventProfile>) }`; `CreateAsset.run(name, category, description, notes, templateKey: String? = null)` — `CreateAsset` now also takes `applyTemplate: ApplyTemplate` as its last constructor parameter.

- [ ] **Step 1: Write the failing tests**

`SeedTemplatesTest.kt`:

```kotlin
package com.loosecannon.notenfc.core.journal

import com.loosecannon.notenfc.core.model.ValueType
import kotlin.test.*

class SeedTemplatesTest {
    @Test fun fiveTemplatesWithStableKeys() =
        assertEquals(listOf("hot_tub", "power_equipment", "ups", "ro_water", "generic"), SeedTemplates.all.map { it.key })

    @Test fun definitionKeysUniquePerTemplate() = SeedTemplates.all.forEach { t ->
        assertEquals(t.definitions.size, t.definitions.map { it.key }.toSet().size, t.key)
    }

    @Test fun everyProfileFieldReferencesADefinitionInTheSameTemplate() = SeedTemplates.all.forEach { t ->
        val keys = t.definitions.map { it.key }.toSet()
        t.profiles.flatMap { it.fields }.forEach { (key, _) -> assertTrue(key in keys, "${t.key}: $key") }
    }

    @Test fun genericHasNoDefinitionsAndOneNoteProfile() {
        val g = SeedTemplates.byKey("generic")!!
        assertTrue(g.definitions.isEmpty()); assertEquals(listOf("Note"), g.profiles.map { it.name })
    }

    @Test fun roWaterShipsWithoutRangesAndUpsHasABoolean() {
        SeedTemplates.byKey("ro_water")!!.definitions.forEach { assertNull(it.rangeLow); assertNull(it.rangeHigh) }
        assertEquals(ValueType.BOOLEAN, SeedTemplates.byKey("ups")!!.definitions.first { it.key == "test_passed" }.valueType)
        assertTrue(SeedTemplates.byKey("power_equipment")!!.definitions.single().isMeter)
    }
}
```

`ApplyTemplateTest.kt` (fakes from `core.testing`):

```kotlin
package com.loosecannon.notenfc.core.usecase

import com.loosecannon.notenfc.core.journal.SeedTemplates
import com.loosecannon.notenfc.core.model.*
import com.loosecannon.notenfc.core.ports.Clock
import com.loosecannon.notenfc.core.ports.IdGenerator
import com.loosecannon.notenfc.core.testing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ApplyTemplateTest {
    private val assets = InMemoryAssetRepository()
    private val defs = InMemoryDefinitionRepository()
    private val profiles = InMemoryProfileRepository()
    private val uow = FakeUnitOfWork(assets, defs, profiles)
    private var seq = 0
    private val ids = IdGenerator { "id-${++seq}" }
    private val apply = ApplyTemplate(defs, profiles, assets, uow, ids, Clock { 1_000L })
    private val hotTub = SeedTemplates.byKey("hot_tub")!!

    private suspend fun asset(templateKey: String? = null) = Asset(
        id = AssetId("a1"), name = "Spa", createdAt = 1L, updatedAt = 1L, templateKey = templateKey,
    ).also { assets.upsert(it) }

    @Test fun appliesDefinitionsProfilesAndProvenanceOnce() = runTest {
        asset()
        val r = apply.run(AssetId("a1"), hotTub) as ApplyResult.Applied
        assertEquals(5, r.definitions.size); assertEquals(2, r.profiles.size)
        assertEquals("hot_tub", assets.get(AssetId("a1"))!!.templateKey)
        val waterTest = profiles.forAsset(AssetId("a1")).first { it.name == "Water test" }
        assertEquals(5, waterTest.fields.size)
        assertEquals(setOf("ph", "free_chlorine"),
            waterTest.fields.filter { it.required }.map { f -> defs.get(f.definitionId)!!.key }.toSet())
        assertEquals("hot_tub", waterTest.templateKey)
        // child rows have durable ids of their own
        assertEquals(5, waterTest.fields.map { it.id }.toSet().size)
        assertEquals(4, waterTest.consumables.map { it.id }.toSet().size)
    }

    @Test fun secondApplicationIsANoOp() = runTest {
        asset()
        apply.run(AssetId("a1"), hotTub)
        val before = defs.forAsset(AssetId("a1")) to profiles.forAsset(AssetId("a1"))
        assertEquals(ApplyResult.AlreadySetUp, apply.run(AssetId("a1"), SeedTemplates.byKey("ups")!!))
        assertEquals(before, defs.forAsset(AssetId("a1")) to profiles.forAsset(AssetId("a1")))
        assertEquals("hot_tub", assets.get(AssetId("a1"))!!.templateKey)
    }

    @Test fun keepsAnExistingProvenanceKey() = runTest {
        asset(templateKey = "generic")
        apply.run(AssetId("a1"), hotTub)
        assertEquals("generic", assets.get(AssetId("a1"))!!.templateKey)
    }

    @Test fun unknownAssetFails() = runTest {
        assertFailsWith<NoSuchAsset> { apply.run(AssetId("nope"), hotTub) }
    }

    @Test fun createAssetWithTemplateSeedsInTheSameTransaction() = runTest {
        val create = CreateAsset(assets, uow, ids, Clock { 1_000L }, apply)
        val a = create.run("UPS", templateKey = "ups")
        assertEquals(4, defs.forAsset(a.id).size)
        assertEquals("ups", assets.get(a.id)!!.templateKey)
        val plain = create.run("Plain")                       // templateKey = null: no template
        assertEquals(0, defs.forAsset(plain.id).size); assertEquals(0, profiles.forAsset(plain.id).size)
        assertNull(assets.get(plain.id)!!.templateKey)
        assertTrue(apply.run(plain.id, hotTub) is ApplyResult.Applied)   // can be set up later
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :core:test --tests '*SeedTemplatesTest' --tests '*ApplyTemplateTest'`
Expected: compilation failure.

- [ ] **Step 3: Implement**

`ports/Repositories.kt`: append the three interfaces from spec §5 verbatim.

`journal/SeedTemplates.kt`: the types from **Interfaces** above and the five templates from spec §7. Transcribe every row: keys, labels, units, `ValueType`, decimals, ranges, `isMeter`, profile names, `EventKind`, default titles (= profile name), fields with `required`, and consumable suggestions (`TemplateConsumable(name, defaultQuantity = null, unit)`).

`usecase/ApplyTemplate.kt`:

```kotlin
class ApplyTemplate(
    private val definitions: DefinitionRepository,
    private val profiles: ProfileRepository,
    private val assets: AssetRepository,
    private val uow: UnitOfWork,
    private val ids: IdGenerator,
    private val clock: Clock,
) {
    suspend fun run(assetId: AssetId, template: Template): ApplyResult = uow.write {
        val asset = assets.get(assetId) ?: throw NoSuchAsset(assetId)
        if (definitions.forAsset(assetId).isNotEmpty() || profiles.forAsset(assetId).isNotEmpty()) {
            return@write ApplyResult.AlreadySetUp
        }
        val now = clock.nowMillis()
        val defs = template.definitions.mapIndexed { i, d ->
            MeasurementDefinition(DefinitionId(ids.newId()), assetId, d.key, d.label, d.unit, d.valueType,
                d.decimals, d.rangeLow, d.rangeHigh, d.isMeter, i, null, now, now)
        }
        val byKey = defs.associateBy { it.key }
        val profs = template.profiles.mapIndexed { i, p ->
            EventProfile(ProfileId(ids.newId()), assetId, p.name, p.eventKind, p.defaultTitle, template.key,
                i, null, now, now,
                fields = p.fields.mapIndexed { j, (key, required) -> ProfileField(ids.newId(), byKey.getValue(key).id, required, j) },
                consumables = p.consumables.mapIndexed { j, c -> ProfileConsumable(ids.newId(), c.name, c.defaultQuantity, c.unit, j) })
        }
        defs.forEach { definitions.upsert(it) }
        profs.forEach { profiles.upsert(it) }
        if (asset.templateKey == null) assets.upsert(asset.copy(templateKey = template.key, updatedAt = now))
        ApplyResult.Applied(defs, profs)
    }
}
```

`CreateAsset`: add `private val applyTemplate: ApplyTemplate` as the last constructor parameter and `templateKey: String? = null` as the last `run` parameter; inside the existing `uow.write { assets.upsert(asset) }` block, after the upsert: `templateKey?.let { key -> applyTemplate.run(asset.id, SeedTemplates.byKey(key) ?: throw UnknownTemplate(key)) }` (define `class UnknownTemplate(key: String) : IllegalArgumentException("no template $key")` in `ApplyTemplate.kt`). Nested `uow.write` must be re-entrant: check `FakeUnitOfWork` and `RoomUnitOfWork` (`withWriteTransaction` in Room 3 is re-entrant on the same coroutine context; `FakeUnitOfWork` — read its implementation and, if it is not re-entrant, make `ApplyTemplate` expose an internal `applyInTransaction(...)` that `run` wraps and `CreateAsset` calls directly). Update every `CreateAsset(...)` construction in tests (`FakeGraph` is Task 5; core tests here).

In-memory fakes: mirror `InMemoryAssetRepository` (rows map, `version` `MutableStateFlow<Int>`, `snapshot()`, `Witnessed`). `InMemoryEventRepository.observeForAsset` must return rows sorted `sortedWith(EventChronology.reversed())`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src && git commit -m "seed templates and ApplyTemplate; CreateAsset can seed from one"
```

---

### Task 3: Event commands — LogEvent, UpdateEvent, DeleteEvent (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/EventCommands.kt`
- Create: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/LogEvent.kt`, `UpdateEvent.kt`, `DeleteEvent.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/EventUseCasesTest.kt`

**Interfaces:**
- Consumes: Task 1 model, Task 2 ports and fakes.
- Produces:

```kotlin
data class EventCommand(
    val assetId: AssetId,
    val profileId: ProfileId?,
    val kind: EventKind,
    val title: String,
    val occurredOn: String,            // "YYYY-MM-DD"
    val occurredTime: String?,         // "HH:MM" or null
    val tzId: String,
    val notes: String,
    val values: Map<DefinitionId, String>,   // raw text as typed; absent or blank = no value
    val consumables: List<ConsumableInput>,  // data class ConsumableInput(name: String, quantity: String, unit: String)
)
sealed interface FieldProblem { val definitionId: DefinitionId?
    data class Required(override val definitionId: DefinitionId) : FieldProblem
    data class NotANumber(override val definitionId: DefinitionId) : FieldProblem
    data class BadDate(override val definitionId: DefinitionId? = null) : FieldProblem
    data class BadTime(override val definitionId: DefinitionId? = null) : FieldProblem
    data object TitleRequired : FieldProblem { override val definitionId = null }
    data class BadConsumable(val index: Int) : FieldProblem { override val definitionId = null }
}
class EventValidation(val problems: List<FieldProblem>) : IllegalArgumentException("invalid event: $problems")
class EventOwnership(detail: String) : IllegalArgumentException(detail)   // wrong asset for event, profile or definition
class NoSuchEvent(id: EventId) : IllegalArgumentException("no event ${id.value}")

class LogEvent(events, definitions, profiles, assets, uow, ids, clock) { suspend fun run(cmd: EventCommand): AssetEvent }
class UpdateEvent(events, definitions, profiles, uow, clock) { suspend fun run(id: EventId, cmd: EventCommand): AssetEvent }
class DeleteEvent(events, uow) { suspend fun run(id: EventId) }
```

Validation lives in one internal function `buildEvent(cmd, definitions, profile, existing: AssetEvent?, ids, now): AssetEvent` in `EventCommands.kt` used by both Log and Update: title trimmed non-blank else `TitleRequired` (Log defaults a blank title to the profile's `defaultTitle` before checking); `occurredOn` must match `^\d{4}-\d{2}-\d{2}$` and parse as `java.time.LocalDate` (java.time is allowed in `:core`; it is JVM, not Android); `occurredTime` null or `^\d{2}:\d{2}$` with hour < 24, minute < 60; ownership is checked before any field: `UpdateEvent` throws `EventOwnership` when `existing.assetId != cmd.assetId`; both use cases throw `EventOwnership` when `cmd.profileId` names a profile whose `assetId != cmd.assetId` (or no profile), or when any definition in `values` has `assetId != cmd.assetId`; nothing is stored in those cases; NUMBER: `trim().toDoubleOrNull()` else `NotANumber`; BOOLEAN: "1"/"true" → 1.0, "0"/"false" → 0.0, else `NotANumber`; TEXT: trimmed, blank = absent; each `required` field of the profile (when `profileId` is set) with no value → `Required`; consumables: blank name or quantity not a non-negative double → `BadConsumable(i)`. Measurements are produced in profile field order first, then any extra definitions in definition `sortOrder`, `unit` snapshotted from the definition, ids new (Log) or preserved by definition id (Update, so an edit keeps measurement ids where the definition is unchanged). Collect all problems, throw once.

- [ ] **Step 1: Write the failing tests** — `EventUseCasesTest` using the fakes: seed one asset with the `hot_tub` template via `ApplyTemplate`, then:
  - `logsAWaterTestWithSnapshotUnitsAndConsumables` (pH "7.8", free chlorine "0.8", one consumable "Chlorine" "1" "oz" → event has 2 measurements with `unit` from the definitions, `valueNum` parsed, consumable quantity 1.0, `source == MANUAL`, `createdAt == updatedAt == clock`).
  - `missingRequiredFieldIsReported` (no `ph` → `EventValidation` containing `Required(phId)`; nothing stored).
  - `notANumberAndBadDateAreCollectedTogether` (pH "abc", date "15/09/2026" → both problems in one exception).
  - `booleanParsesToZeroOrOne` (use the `ups` template on a second asset: "true" → 1.0, "0" → 0.0, "maybe" → `NotANumber`).
  - `blankTitleDefaultsToProfileTitle`.
  - `updateKeepsIdsAndCreatedAtAndSetsUpdatedAt` (advance clock; measurement id for pH unchanged; value changed; `createdAt` unchanged).
  - `updateUnknownEventFails` (`NoSuchEvent`).
  - `deleteRemovesTheAggregate` (get returns null afterwards).
  - `logAgainstUnknownAssetFails` (`NoSuchAsset`).
  - `updateCannotMoveAnEventToAnotherAsset` (second asset; `UpdateEvent` with the other `assetId` → `EventOwnership`; the stored event is byte-for-byte unchanged).
  - `profileMustBelongToTheCommandAsset` (a UPS profile id on the hot-tub asset → `EventOwnership`; nothing stored).
  - `definitionMustBelongToTheCommandAsset` (a UPS definition in `values` on the hot-tub asset → `EventOwnership`).

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:test --tests '*EventUseCasesTest'` → compilation failure.

- [ ] **Step 3: Implement** the types and the three use cases per **Interfaces**; each use case is one `uow.write`.

- [ ] **Step 4: Run** `./gradlew :core:test` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "log, update, delete events with one validation path"`

---

### Task 4: Backup format 2 (`:core`)

**Files:**
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/backup/BackupFormat.kt`, `BackupCodec.kt`
- Modify: `core/src/main/kotlin/com/loosecannon/notenfc/core/usecase/ExportBackup.kt`, `ImportBackupReplace.kt`
- Test: `core/src/test/kotlin/com/loosecannon/notenfc/core/backup/BackupCodecTest.kt`, `core/src/test/kotlin/com/loosecannon/notenfc/core/usecase/BackupUseCasesTest.kt` (extend both)

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces (all `@Serializable`, every field explicit, defaults only where format 1 lacks the field):

```kotlin
data class AssetDto(..., val templateKey: String? = null)          // appended, default null
data class MeasurementDefinitionDto(val id: String, val assetId: String, val key: String, val label: String,
    val unit: String, val valueType: String, val decimals: Int, val rangeLow: Double?, val rangeHigh: Double?,
    val isMeter: Boolean, val sortOrder: Int, val archivedAt: Long?, val createdAt: Long, val updatedAt: Long)
data class ProfileFieldDto(val id: String, val definitionId: String, val required: Boolean, val sortOrder: Int)
data class ProfileConsumableDto(val id: String, val name: String, val defaultQuantity: Double?, val unit: String, val sortOrder: Int)
data class EventProfileDto(val id: String, val assetId: String, val name: String, val eventKind: String,
    val defaultTitle: String, val templateKey: String?, val sortOrder: Int, val archivedAt: Long?,
    val createdAt: Long, val updatedAt: Long, val fields: List<ProfileFieldDto>, val consumables: List<ProfileConsumableDto>)
data class MeasurementDto(val id: String, val definitionId: String, val valueNum: Double?, val valueText: String?, val unit: String, val sortOrder: Int)
data class ConsumableUsageDto(val id: String, val name: String, val quantity: Double, val unit: String, val sortOrder: Int)
data class AssetEventDto(val id: String, val assetId: String, val kind: String, val title: String, val profileId: String?,
    val occurredOn: String, val occurredTime: String?, val tzId: String, val notes: String, val source: String,
    val sourceRef: String?, val createdAt: Long, val updatedAt: Long,
    val measurements: List<MeasurementDto>, val consumables: List<ConsumableUsageDto>)
data class BackupData(
    val assets: List<AssetDto>, val nfcTags: List<NfcTagDto>, val externalLinks: List<ExternalLinkDto>,
    val measurementDefinitions: List<MeasurementDefinitionDto> = emptyList(),
    val eventProfiles: List<EventProfileDto> = emptyList(),
    val assetEvents: List<AssetEventDto> = emptyList(),
)
data class ImportReport(val assets: Int, val tags: Int, val links: Int, val definitions: Int, val profiles: Int, val events: Int)
```

`BackupCodec.FORMAT_VERSION = 2`; manifest `counts` gains `measurementDefinitions`, `eventProfiles`, `assetEvents` (the seven tables are represented: fields and consumables ride inside their parents, `counts` also carries `profileFields`, `profileConsumables`, `measurements`, `consumableUsages` totals so an operator can compare seven numbers). `ExportBackup` and `ImportBackupReplace` take the three new repositories. Import deletes in the order events, profiles, definitions, tags, links, assets and inserts assets, definitions, profiles, links, tags, events.

- [ ] **Step 1: Write the failing tests**

`BackupCodecTest` additions:
- `formatOneFileStillDecodes`: build a `BackupData` with only the three original lists, encode with a manifest whose `formatVersion = 1` (construct the zip by hand through the existing private helpers or a test-only `encodeWithVersion`), decode → new lists empty, `templateKey` null.
- `formatTwoRoundTripsAllSevenTables`: one asset with one definition, one profile (one field, one consumable), one event (one measurement, one consumable) → encode → decode equals input; `manifest.counts` has the seven keys with value 1 each plus the three originals.
- `measurementMustReferenceADefinitionOnTheSameAsset`: measurement whose definition belongs to another asset → `BackupCorrupt` naming the measurement id.
- `profileFieldMustReferenceAKnownDefinition`, `eventMustReferenceAKnownAsset`, `unknownValueTypeIsCorrupt`, `duplicateDefinitionIdIsCorrupt`, `duplicateProfileFieldIdIsCorrupt`.
- `measurementValueShapeMustMatchItsDefinition`: four files, each `BackupCorrupt` naming the measurement id — NUMBER with `valueNum = null, valueText = "7.8"`; TEXT with `valueNum = 7.8, valueText = null`; BOOLEAN with `valueNum = 2.0`; NUMBER with both `valueNum = 7.8` and `valueText = "seven"`. Plus a fifth: neither value set.
- `encodeIsReproducibleWithTheNewLists` (two encodes byte-equal).

`BackupUseCasesTest` additions:
- `exportCarriesJournalRows` (seed with fakes; the decoded data has them).
- `importReplacesJournalRowsAndReportsCounts` (pre-existing journal rows for another asset vanish; report counts match the file).
- `importFailureLeavesEverythingIntact` (rig the event fake to fail on upsert; assert assets/definitions unchanged — the fakes already support `RiggedFailure` and rollback via `FakeUnitOfWork`).

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:test --tests '*Backup*'` → compilation failure.

- [ ] **Step 3: Implement** DTOs + `toDto()`/`toDomain()` mappings (enum names via the existing `enumOrCorrupt`), sorting of the new lists by id (and children by `sortOrder`) in `encode`, `validateGraph` extended with: unique ids for definitions, profiles, events, and (across the file) measurements and consumable usages; `definition.assetId ∈ assets`; `profile.assetId ∈ assets`; each `profile.fields[].definitionId ∈ definitions` and its definition's `assetId == profile.assetId`; `event.assetId ∈ assets`; `event.profileId` null or `∈ profiles` on the same asset; each `measurement.definitionId ∈ definitions` on the same asset as the event; unique ids across the file for profile fields, profile consumables, measurements and consumable usages; `valueType`, `eventKind`, `kind`, `source` known; **value shape** per the definition's `valueType` (NUMBER: `valueNum != null && valueText == null`; BOOLEAN: `valueNum in {0.0, 1.0} && valueText == null`; TEXT: `!valueText.isNullOrBlank() && valueNum == null`), else `BackupCorrupt("assetEvents: measurement <id> …")`. Import never mints ids: every id in the file is the id stored. Export/Import per **Interfaces**.

- [ ] **Step 4: Run** `./gradlew :core:test` → PASS.

- [ ] **Step 5: Commit** — `git commit -m "backup format 2: journal tables, format 1 still imports"`

---

### Task 5: Room v2 — entities, DAOs, migration, repositories, graph wiring (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/entities/JournalEntities.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/dao/JournalDaos.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/Migrations.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/JournalMappers.kt`, `JournalRepositories.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/data/room/AppDatabase.kt` (version 2, entities, DAOs), `entities/AssetEntity.kt` (`template_key`), `Mappers.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/di/AppGraph.kt` (`.addMigrations(MIGRATION_1_2)`, three repositories, `applyTemplate`, `logEvent`, `updateEvent`, `deleteEvent`, `CreateAsset(..., applyTemplate)`, `ExportBackup`/`ImportBackupReplace` with the new repositories, `SCHEMA_VERSION = 2`)
- Modify: `app/src/test/kotlin/com/loosecannon/notenfc/testing/FakeGraph.kt` (same wiring, `SCHEMA_VERSION = 2`)
- Create: `app/schemas/com.loosecannon.notenfc.data.room.AppDatabase/2.json` (generated by the build; commit it)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/data/room/Migration1To2Test.kt`, `JournalDaoTest.kt`; extend `backup/RestoreProofTest.kt`

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: `RoomDefinitionRepository`, `RoomProfileRepository`, `RoomEventRepository` implementing the spec §5 ports; `val MIGRATION_1_2: Migration`; `AppGraph.{definitions, profiles, events, applyTemplate, logEvent, updateEvent, deleteEvent}`.

Entities (column names exactly as spec §8; enums stored as their Kotlin names; booleans as INTEGER 0/1 via `Boolean` fields, which Room maps):

```kotlin
@Entity(tableName = "measurement_definition",
    foreignKeys = [ForeignKey(AssetEntity::class, ["id"], ["asset_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("asset_id"), Index(value = ["asset_id", "key"], unique = true)])
data class MeasurementDefinitionEntity(@PrimaryKey val id: String, @ColumnInfo(name = "asset_id") val assetId: String,
    val key: String, val label: String, val unit: String, @ColumnInfo(name = "value_type") val valueType: String,
    val decimals: Int, @ColumnInfo(name = "range_low") val rangeLow: Double?, @ColumnInfo(name = "range_high") val rangeHigh: Double?,
    @ColumnInfo(name = "is_meter") val isMeter: Boolean, @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?, @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long)
// event_profile, profile_field (UNIQUE(profile_id, definition_id), FK profile CASCADE, FK definition CASCADE),
// profile_consumable (FK profile CASCADE), asset_event (FK asset CASCADE, FK profile SET NULL,
//   Index(["asset_id","occurred_on","created_at"]), Index(["source","source_ref"], unique = true)),
// measurement (FK event CASCADE, FK definition RESTRICT, Index(["definition_id","event_id"]), Index("event_id")),
// consumable_usage (FK event CASCADE, Index("event_id")) — same style.
```

`MIGRATION_1_2` is the exact SQL Room generates for those entities (copy from `2.json`'s `createSql` after the first build, replacing `${TABLE_NAME}`), preceded by `ALTER TABLE asset ADD COLUMN template_key TEXT`. Room 3's `Migration` takes `(startVersion, endVersion)` and overrides `migrate(connection: SQLiteConnection)`; use `connection.execSQL(...)`.

DAOs: `DefinitionDao` (upsert, byId, forAsset ordered by `sort_order`, all, deleteAll, observeForAsset), `ProfileDao` (profile + `@Relation` lists of `ProfileFieldEntity` and `ProfileConsumableEntity` through a `ProfileWithParts` class; `upsert(profile, fields, consumables)` is a `@Transaction` method that upserts the row, deletes the old children by `profile_id`, inserts the new; forAsset/observeForAsset ordered by `sort_order`), `EventDao` (same shape with `EventWithParts`; `forAsset`/`observeForAsset` use `ORDER BY occurred_on DESC, COALESCE(occurred_time,'00:00') DESC, created_at DESC, id DESC`; `observe(id)`; `delete(id)`; `deleteAll`). Repositories map through `JournalMappers.kt` (child ids pass through unchanged in both directions); `RoomEventRepository.observeForAsset` additionally applies `sortedWith(EventChronology.reversed())` so the rule has one owner.

- [ ] **Step 1: Write the failing tests**

`Migration1To2Test.kt`:

```kotlin
package com.loosecannon.notenfc.data.room

import androidx.room3.testing.SQLiteDriverMigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class Migration1To2Test {
    @Test fun migratesAndKeepsPhase1Rows() {
        val file = File.createTempFile("notenfc-migrate", ".db").also { it.delete() }
        val helper = SQLiteDriverMigrationTestHelper(
            schemaDirectoryPath = File("schemas/com.loosecannon.notenfc.data.room.AppDatabase").absolutePath,
            databasePath = file.absolutePath,
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
            migrations = listOf(MIGRATION_1_2),
        )
        helper.createDatabase(version = 1).use { c ->
            c.execSQL("INSERT INTO asset (id,name,description,category,notes,status,created_at,updated_at) VALUES ('a1','Spa','','','','ACTIVE',1,1)")
            c.execSQL("INSERT INTO nfc_tag (id,payload_format,payload_key,asset_id,link_id,status,label,physical_uid,written_at,last_scanned_at,created_at,updated_at) VALUES ('t1','V1','t1','a1',NULL,'ACTIVE',NULL,NULL,NULL,NULL,1,1)")
        }
        helper.runMigrationsAndValidate(version = 2).use { c ->
            c.prepare("SELECT template_key FROM asset WHERE id='a1'").use { s -> s.step(); assertEquals(true, s.isNull(0)) }
            c.prepare("SELECT COUNT(*) FROM nfc_tag").use { s -> s.step(); assertEquals(1L, s.getLong(0)) }
            c.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('measurement_definition','event_profile','profile_field','profile_consumable','asset_event','measurement','consumable_usage')")
                .use { s -> var n = 0; while (s.step()) n++; assertEquals(7, n) }
        }
    }
}
```

(Check the exact constructor names of `SQLiteDriverMigrationTestHelper` in the 3.0.3 sources jar under `~/.gradle`; adjust parameter names, keep the assertions. The `schemas` path is relative to the `app` module directory, which is the JVM test working directory.)

`JournalDaoTest.kt` (with `inMemoryDb()`): `profileUpsertReplacesChildren`, `eventUpsertReplacesChildrenAndObserveEmits` (collect `observeForAsset` in a `turbine`-free way: `first()` after each write), `eventsComeNewestFirstByOccurrenceThenTimeThenCreated` (three events as in `EventChronologyTest`), `deletingAnAssetCascadesToJournalRows`, `deletingADefinitionWithDataIsRefused` (expect an `SQLiteException`/`SQLException` from the RESTRICT FK), `uniqueDefinitionKeyPerAsset`.

`RestoreProofTest` additions: seed a hot-tub asset via `FakeGraph.createAsset(name, templateKey = "hot_tub")`, log an event via `FakeGraph.logEvent`, export, wipe with the existing wipe path, import, and assert counts and rows equal for all seven tables (compare `all()` results from the three journal repositories before and after).

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests '*Migration1To2Test' --tests '*JournalDaoTest' --tests '*RestoreProofTest'` → compilation failure.

- [ ] **Step 3: Implement** entities, DAOs, `AppDatabase` v2, build once (`./gradlew :app:kspDebugKotlin`) to generate `2.json`, copy the `createSql` into `MIGRATION_1_2`, mappers, repositories, `AppGraph` + `FakeGraph` wiring (`Room.databaseBuilder(...).addMigrations(MIGRATION_1_2)`), `SCHEMA_VERSION = 2` in both.

- [ ] **Step 4: Run** `./gradlew :core:test :app:testDebugUnitTest` → PASS. Confirm `git status` shows `app/schemas/.../2.json` and that `1.json` is unchanged.

- [ ] **Step 5: Commit** — `git commit -m "room v2: journal tables, migration, repositories"`

---

### Task 6: Asset screen — current readings, quick actions, service record, template pickers (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/components/InstrumentRow.kt`
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/journal/JournalFormat.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/asset/AssetViewModels.kt` (`AssetDetailState` + `AssetDetailViewModel`, `AssetEditState` + `AssetEditViewModel`)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/asset/AssetDetailScreen.kt`, `AssetEditScreen.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/nav/NoteNfcApp.kt` (two new callbacks on `AssetDetailScreen`, wired to routes that Task 7 adds — add the `Route` cases in this task so it compiles: `Route.EventEntry(assetId: String, profileId: String?, eventId: String?)`, `Route.EventDetail(id: String)`; their `entry<>` blocks are Task 7)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/components/Previews.kt` (one `InstrumentRow` preview per state)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/asset/AssetViewModelsTest.kt` (extend)

**Interfaces:**
- Consumes: `LatestReadings`, `Reading`, `RangeState`, `EventProfile`, `AssetEvent`, `ApplyTemplate`, `SeedTemplates`, `CreateAsset(templateKey)`, `NoteNfcTheme.semanticColors.measurement*`, `StatusBadge`, `LedgerList`/`LedgerEntry`, `ActionGrid`/`ActionSpec`, `SectionHeader`, `QuietLine`.
- Produces:

```kotlin
// ui/components/InstrumentRow.kt — the D12 §9 row. Display variant.
@Composable fun InstrumentRow(label: String, target: String /* "7.2–7.8" or "No target" */,
    value: String? /* formatted, null = "—" */, unit: String, state: RangeState?, modifier: Modifier = Modifier)
// ui/journal/JournalFormat.kt
fun formatValue(m: Measurement?, def: MeasurementDefinition): String?   // NUMBER: decimals; BOOLEAN: "Pass"/"Fail"... no: "Yes"/"No" — see below; TEXT: text
fun formatTarget(def: MeasurementDefinition): String                    // "7.2–7.8", "≥ 80", "≤ 120", "No target"
fun stateLabel(s: RangeState): String                                   // "LOW", "IN RANGE", "HIGH", "NO TARGET SET"
fun stateColors(s: RangeState, colors: NoteNfcSemanticColors): StatusColor
fun stateIcon(s: RangeState): ImageVector                               // arrow down / check / arrow up / help glyph from NoteNfcIcons
fun quickActionLabel(profile: EventProfile): String                     // "Log " + name with first letter lowercased: "Log water test"
fun eventDetailLine(e: AssetEvent, defs: Map<DefinitionId, MeasurementDefinition>): String
    // up to three "label value unit" joined by " · ", else the first consumable "name qty unit", else the notes' first line, else ""
```

BOOLEAN display: the definition's label names the question ("Passed"); the value renders as **Yes** / **No** in the display row, and the entry control in Task 7 offers the same two words (spec §7 and §10).

`AssetDetailState` gains `definitions: List<MeasurementDefinition>`, `profiles: List<EventProfile>`, `events: List<AssetEvent>`, and a derived `readings: List<Reading>` (computed in the ViewModel's `combine` with `LatestReadings.of(definitions, events)`; unarchived profiles only). `AssetDetailViewModel` combines `definitions.observeForAsset`, `profiles.observeForAsset`, `events.observeForAsset` with the existing three flows (Kotlin `combine` takes up to five; nest two combines). It gains `fun setUpFromTemplate(key: String)` (viewModelScope, `applyTemplate.run`, snackbar text on `AlreadySetUp`/error via a `messages` SharedFlow the screen shows).

`AssetEditState` gains `templateKey: String? = null` (new only; `null` = "None · set up later"); `AssetEditViewModel.onTemplate(key: String?)`; `save()` passes `templateKey` to `createAsset.run` when `id == null`.

`AssetDetailScreen` gains `onLogEvent: (assetId: String, profileId: String) -> Unit` and `onOpenEvent: (eventId: String) -> Unit`, and renders per spec §10: plate → `SectionHeader("Current readings")` + one `InstrumentRow` per reading (absent when `definitions` is empty) → "No schedule yet" → `ActionGrid` (profiles first, filled, `quickActionLabel`, icon `NoteNfcIcons.Ledger` or the closest existing glyph; then the existing four; when both `definitions` and `profiles` are empty, an extra outlined "Set up from template" action that opens an `AlertDialog` listing `SeedTemplates.all` by name) → `SectionHeader("Service record")` + `LedgerList` of `events` (date from `occurredOn` — parse `LocalDate`, day/month/year strings as the existing `asLedgerDate` does for millis; title; detail `eventDetailLine`; badge only when any NUMBER measurement in the event classifies LOW or HIGH: `StatusBadge("LOW"/"HIGH")` of the first such; click → `onOpenEvent`) with empty state `QuietLine("No service recorded yet")` → Tags → Links → Notes.

`AssetEditScreen` (new asset only): below Category, a "Template" row: `ExposedDropdownMenuBox` or `FilterChip`s with **None · set up later** (selected by default), Hot tub, Power equipment, UPS, RO water, Generic; helper text "Starts the asset with its readings and quick actions. Choose None to decide on the asset later."

- [ ] **Step 1: Write the failing tests** — `AssetViewModelsTest` additions with `FakeGraph`:
  - `detailStateDerivesReadingsFromEvents`: create hot-tub asset, log two water tests (7.4 on 09-12, 7.8 on 09-15), `state.first { it?.events?.size == 2 }`, assert `readings[0].measurement?.valueNum == 7.8`, state HIGH; delete the newer event; assert 7.4.
  - `detailStateListsUnarchivedProfilesInOrder`.
  - `setUpFromTemplateOnAPlainAsset`: create asset with no template, call `setUpFromTemplate("ups")`, assert 4 definitions appear in state.
  - `newAssetFormPassesTemplateKey`: `AssetEditViewModel(graph, null)` → `onName("Spa")`, `onTemplate("hot_tub")`, `save()`, collect `saved`, assert `graph.definitions.forAsset(id).size == 5`.
  - `newAssetDefaultsToNoTemplateAndCanBeSetUpLater`: `onName("Thing")`, `save()` without `onTemplate` → 0 definitions, 0 profiles, `templateKey == null`; then `AssetDetailViewModel(graph, id).setUpFromTemplate("ups")` → 4 definitions. And `onTemplate("generic")` explicitly → 1 profile "Note".
  - `JournalFormatTest` (plain JUnit, `ui/journal/`): `formatTarget` for both-bounds/one-sided/none; `formatValue` decimals (7.8 with decimals 1 → "7.8"; 110.0 with 0 → "110"; BOOLEAN 1.0 → "Yes"); `quickActionLabel(EventProfile(name = "Water test"…)) == "Log water test"`; `eventDetailLine` with three readings, with none but a consumable, with nothing.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests '*AssetViewModelsTest' --tests '*JournalFormatTest'` → compilation failure.

- [ ] **Step 3: Implement** per **Interfaces** and spec §10. `InstrumentRow`: 3-column `Row` (label + target stacked left with `weight(1f)`; value column 92dp: value in `NoteNfcTheme.typography` mono at the entry size (22sp — reuse the existing measurement text style from `Type.kt` if one exists, else add `measurementEntry` there) with the unit in `bodySmall` beside it; state column 72dp: `StatusBadge(stateLabel, stateColors, stateIcon)` or nothing). No card. Hairline between rows via `LedgerList`-style `HorizontalDivider(color = outlineVariant)`.

- [ ] **Step 4: Run** `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` → PASS (the smoke tests must still compile; `AssetDetailScreen`'s new parameters are wired in `NoteNfcApp` with `backStack.add(Route.EventEntry(...))` / `Route.EventDetail(...)` even though their entries land in Task 7 — a route with no entry is a compile-time non-issue and a runtime one Task 7 removes).

- [ ] **Step 5: Commit** — `git commit -m "asset screen: current readings, quick actions, service record, template pickers"`

---

### Task 7: Event entry route and event detail (`:app`)

**Files:**
- Create: `app/src/main/kotlin/com/loosecannon/notenfc/ui/journal/EventEntryViewModel.kt`, `EventEntryScreen.kt`, `EventDetailViewModel.kt`, `EventDetailScreen.kt`
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/components/InstrumentRow.kt` (add the entry variant `InstrumentEntryRow`)
- Modify: `app/src/main/kotlin/com/loosecannon/notenfc/ui/nav/NoteNfcApp.kt` (two `entry<>` blocks)
- Test: `app/src/test/kotlin/com/loosecannon/notenfc/ui/journal/EventEntryViewModelTest.kt`

**Interfaces:**
- Consumes: Task 3 use cases and `EventCommand`/`FieldProblem`; Task 6 formatting helpers and routes.
- Produces:

```kotlin
data class FieldRow(val definition: MeasurementDefinition, val required: Boolean, val text: String, val problem: FieldProblem?) {
    val liveState: RangeState? // NUMBER with a parseable value → classify; else null
}
data class ConsumableRow(val name: String, val quantity: String, val unit: String, val problem: Boolean)
data class EventEntryState(
    val assetName: String = "", val profileName: String = "", val title: String = "",
    val occurredOn: String, val occurredTime: String?,             // defaults: today / now "HH:MM" from graph.clock in the device zone
    val fields: List<FieldRow> = emptyList(), val suggestions: List<ProfileConsumable> = emptyList(),
    val consumables: List<ConsumableRow> = emptyList(), val notes: String = "",
    val editing: Boolean = false, val saving: Boolean = false, val firstProblem: String? = null, val loaded: Boolean = false,
)
class EventEntryViewModel(graph: AppGraph, assetId: String, profileId: String?, eventId: String?) : ViewModel() {
    val state: StateFlow<EventEntryState>; val saved: SharedFlow<EventId>
    fun onTitle(String); fun onDate(String); fun onTime(String?); fun onValue(DefinitionId, String); fun onNotes(String)
    fun addSuggested(ProfileConsumable); fun addBlankConsumable(); fun onConsumable(index: Int, name: String?, quantity: String?, unit: String?); fun removeConsumable(index: Int)
    fun save()   // re-entry guard; LogEvent or UpdateEvent; EventValidation → mark rows + firstProblem; success → saved.tryEmit
}
class EventDetailViewModel(graph: AppGraph, eventId: String) : ViewModel() {
    val state: StateFlow<EventDetailState?>   // event + definitions map + assetName; null while loading
    val missing: StateFlow<Boolean>; val deleted: SharedFlow<Unit>; fun delete()
}
@Composable fun EventEntryScreen(graph: AppGraph, assetId: String, profileId: String?, eventId: String?, onDone: () -> Unit, onBack: () -> Unit)
@Composable fun EventDetailScreen(graph: AppGraph, eventId: String, onEdit: (assetId: String, eventId: String) -> Unit, onBack: () -> Unit)
@Composable fun InstrumentEntryRow(row: FieldRow, onValue: (String) -> Unit, imeAction: ImeAction, onNext: () -> Unit, modifier: Modifier = Modifier)
```

`EventEntryViewModel.init`: load asset name, definitions, the profile (fields in order → `FieldRow`s with empty text; `title = profile.defaultTitle`; `suggestions = profile.consumables`) or, in edit mode, the event (rows prefilled from its measurements using `formatValue`-free raw text: NUMBER → plain `valueNum.toString()` trimmed of a trailing ".0" when decimals == 0, BOOLEAN → "1"/"0", TEXT → text; consumables prefilled; date/time/notes/title from the event; profile from `event.profileId` when present, else all unarchived definitions as optional rows). `save()` builds the `EventCommand` from state (`values` = non-blank texts), calls `logEvent`/`updateEvent`, maps `EventValidation.problems` back onto rows (`Required`/`NotANumber` by definition id; `BadDate`/`BadTime`/`TitleRequired`/`BadConsumable` to `firstProblem` text: "Enter a date as YYYY-MM-DD", "Enter a time as HH:MM", "Give the entry a title", "Check material N"), and sets `firstProblem` to the first row problem's label otherwise ("pH is required", "pH is not a number").

`EventEntryScreen` per spec §10: `Scaffold` with a top bar (close icon → `onBack`; eyebrow text `"${profileName.uppercase()} · ${assetName.uppercase()}"` in the label style; `TextButton("Save")`), a `LazyColumn`: LOGGED label with a date `OutlinedTextField` (keyboard type Number, placeholder YYYY-MM-DD) and a time field (optional, HH:MM); column header READING / VALUE / TARGET in the label style; `InstrumentEntryRow` per field (`OutlinedTextField` value column 92dp, `KeyboardType.Decimal`, `ImeAction.Next` except last = `Done`; unit as `suffix`; BOOLEAN → `SingleChoiceSegmentedButtonRow` Yes/No; TEXT → single-line text field; below the label: `StatusBadge(liveState)` when non-null, else "Required" in `onSurfaceVariant` when required and blank, else nothing; problem text under the row in the `due` foreground colour); `SectionHeader("Materials used")` with suggestion `AssistChip`s, rows (name field · quantity field mono · unit field · remove icon), "+ Add material" outlined full-width; Notes `OutlinedTextField` (min 3 lines); bottom `Button("Save")`; `firstProblem` shown in a `Snackbar` on failed save. Pops via `onDone` on `saved`.

`EventDetailScreen`: top bar with back and an overflow (Edit, Delete → confirm `AlertDialog` "Delete this entry? Its readings go with it." with the confirm button in `destructiveAction` colours); body: eyebrow `"${title.uppercase()}"`, logged line "15 Sep 2026 · 14:42", `InstrumentRow` per measurement (display variant, state by `classify`), Materials list, Notes. Missing → `onBack` (1C pattern via `LaunchedEffect(missing)`).

`NoteNfcApp`: `entry<Route.EventEntry> { key -> EventEntryScreen(graph, key.assetId, key.profileId, key.eventId, onDone = { backStack.removeLastOrNull() }, onBack = { backStack.removeLastOrNull() }) }`, `entry<Route.EventDetail> { key -> EventDetailScreen(graph, key.id, onEdit = { a, e -> backStack.add(Route.EventEntry(a, null, e)) }, onBack = { backStack.removeLastOrNull() }) }`. Since Task 6 already wires `AssetDetailScreen`'s callbacks, nothing else changes.

- [ ] **Step 1: Write the failing tests** — `EventEntryViewModelTest` with `FakeGraph` and `UnconfinedTestDispatcher`/`Dispatchers.setMain` as the existing ViewModel tests do:
  - `newEntryLoadsProfileRowsInOrderWithDefaults` (hot tub Water test → 5 rows, `ph` and `free_chlorine` required, title "Water test", suggestions 4, `occurredOn` = today from `graph.clock`).
  - `saveWithMissingRequiredMarksRowAndDoesNotEmit` (`firstProblem == "pH is required"`, `fields[0].problem is Required`, no `saved` emission, `graph.events.forAsset` empty).
  - `liveStateClassifiesWhileTyping` (`onValue(ph, "7.9")` → `fields[0].liveState == HIGH`; "abc" → null).
  - `saveLogsEventAndEmits` (fill required, one suggested consumable with quantity "1", save → `saved` emits; stored event has 2 measurements with snapshot units and 1 consumable).
  - `saveIsGuardedWhileSaving` (call `save()` twice synchronously; exactly one event stored).
  - `editModePrefillsAndPreservesIds` (log an event, open `EventEntryViewModel(graph, assetId, null, eventId)`, rows prefilled with "7.8", change to "7.5", save → same event id, same measurement id for pH, value 7.5, `createdAt` unchanged).
  - `EventDetailViewModelTest` (same file or its own): `deleteRemovesAndEmits`, `missingEventFlagsMissing`.

- [ ] **Step 2: Run to verify they fail** — `./gradlew :app:testDebugUnitTest --tests '*ui.journal*'` → compilation failure.

- [ ] **Step 3: Implement** per **Interfaces**.

- [ ] **Step 4: Run** `./gradlew :core:test :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` → PASS. Also run the app on the phone or emulator once (`:app:installDebug`) and open a hot-tub asset's "Log water test" to confirm the route renders; this is a smoke check, the proof is Task 8.

- [ ] **Step 5: Commit** — `git commit -m "event entry route and event detail"`

---

### Task 8: Device smoke test, evidence, release gate (`:app`, docs)

**Files:**
- Create: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/JournalSmokeTest.kt`
- Modify: `app/src/androidTest/kotlin/com/loosecannon/notenfc/ui/AppSmokeTest.kt` (`clearInstall()` also empties the journal tables: events, profiles, definitions — in that order, before the three existing `deleteAll`s)
- Create: `docs/design/phase-2a-evidence.md`
- Modify: `docs/design/README.md` (row), root `README.md` (feature line), `app/build.gradle.kts` (`versionCode = 3`, `versionName = "2.1"`)

**Interfaces:**
- Consumes: everything above; the 1C `AppSmokeTest` helpers (`awaitText`, `clearInstall`, `createAndroidComposeRule<MainActivity>()`).
- Produces: the evidence document with the same sections as `phase-1c-evidence.md` (§1 exit criteria → evidence, §2 what shipped, §3 tests, §4 device checklist, §5 status, §6 rulings, §7 deferred, §8 what changed for later phases, §9 final gate).

`JournalSmokeTest` (destructive, same `@Before` as `AppSmokeTest`):

```kotlin
@Test fun hotTubWaterTestShowsInRecordAndReadings() {
    val id = runBlocking { app.graph.createAsset.run("Spa", templateKey = "hot_tub").id.value }
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    // open the asset through the deep link so the test does not depend on list copy
    rule.activityRule.scenario.onActivity { it.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("notenfc://asset/$id")).setClass(ctx, MainActivity::class.java)) }
    // NOTE: per 1C's lesson, do this through a cold-start rule instead if the scenario teardown times out:
    // make this class use createEmptyComposeRule() + ActivityScenario.launch(intent).use { ... }
    rule.awaitText("Log water test"); rule.onNodeWithText("Log water test").performClick()
    rule.awaitText("WATER TEST · SPA")
    rule.onNodeWithTag("value-ph").performTextInput("7.9")
    rule.onNodeWithTag("value-free_chlorine").performTextInput("2.0")
    rule.onNodeWithText("HIGH").assertIsDisplayed()          // live state
    rule.onAllNodesWithText("Save").onFirst().performClick()
    rule.awaitText("Service record")
    rule.onNodeWithText("Water test").assertIsDisplayed()
    rule.onAllNodesWithText("HIGH").onFirst().assertIsDisplayed()   // current readings badge
    rule.onNodeWithText("7.9").assertIsDisplayed()
}
```

(Give every `InstrumentEntryRow` value field `Modifier.testTag("value-${definition.key}")` in Task 7 — add that now if missing. Use the cold-start empty-rule pattern from `DeepLinkSmokeTest` rather than `startActivity` into the running activity; the snippet above shows the assertions, the class shape follows `DeepLinkSmokeTest`.)

Evidence §4 device checklist rows (fill Result on the phone; the destructive suite is row 2):

| # | Step | Expected |
|---|---|---|
| 1 | `adb install -r` the 2A debug build over the 1C install; launch once | Dashboard; existing assets still there (migration ran); no crash |
| 2 | `adb shell svc power stayon usb` → `./gradlew :app:connectedDebugAndroidTest` → `svc power stayon false`; then **reinstall and launch** (the task uninstalls the app) | All instrumented tests pass (13 from 1C + JournalSmokeTest) |
| 3 | New asset "Hot tub", template Hot tub → asset screen | CURRENT READINGS shows five rows with "—"; actions "Log water test", "Log treatment" first |
| 4 | Log water test: pH 7.9, FC 0.8, alkalinity 110, calcium 200, temp 102; materials: Chlorine 1 oz, pH reducer 0.5 oz; Save | Live badges HIGH / LOW / IN RANGE / IN RANGE / NO TARGET SET while typing; back on the asset, readings show the same states; Service record shows "Water test" with "pH 7.9 · Free chlorine 0.8 ppm · Alkalinity 110 ppm" and a HIGH badge |
| 5 | Open the event → Edit → pH 7.5 → Save | Current readings pH 7.5 IN RANGE; the record line updated; same event, not a second one |
| 6 | Log water test dated a week earlier with pH 7.0; Save | Service record lists it **below** today's; current readings still 7.5 |
| 7 | New asset "UPS", template UPS → Log load test: voltage 12.7, load 38, runtime 42, Passed = Yes; Save | Readings show the three numbers with NO TARGET SET and "Passed · Yes"; record line "Battery voltage 12.7 V · Load 38 % · Runtime 42 min" |
| 8 | New asset "Mower", template Power equipment → Log oil change: engine hours 138.5, Engine oil 1.5 qt, Oil filter 1 pcs; Save | Readings show Engine hours 138.5 h NO TARGET SET; record line shows the reading; opening the event lists both materials |
| 9 | Existing 1C asset (no template) and a new asset saved with Template = None → "Set up from template" → RO water | Three TDS rows appear with "—"; "Log TDS test" action appears; doing it again is not offered |
| 10 | Backup → Export; Debug → Wipe; Backup → Import (REPLACE) | Dashboard, assets, readings and records identical; the manifest counts in the file match `sqlite3` counts of the seven tables (`adb shell run-as … sqlite3` or the debug screen's counts) |
| 11 | Open the hot tub → delete today's water test | Current readings fall back to the week-old pH 7.0 (LOW) |
| 12 | `adb shell run-as com.loosecannon.notenfc sqlite3 databases/notenfc.db ".tables"` (or `.schema` via a pulled copy) | Exactly the ten tables: asset, nfc_tag, external_link + the seven journal tables; nothing named after a hot tub, UPS, RO or mower |
| 13 | Scan the bound tag (from 1C) → asset opens → Log … | The scan path is unchanged and the quick action is one tap away |

- [ ] **Step 1: Write the instrumented test** and make it compile: `./gradlew :app:compileDebugAndroidTestKotlin`.
- [ ] **Step 2: Run it on the phone if attached** (`adb devices`), else record "pending device" in the evidence exactly as 1C did, and say so in the report.
- [ ] **Step 3: Write the evidence document** with §1–§9 (results pending where not run), update `docs/design/README.md`, root `README.md` (one line under features: "Log maintenance events with typed readings and materials used; current readings and a service record per asset"), bump `versionCode = 3` / `versionName = "2.1"`.
- [ ] **Step 4: Final gate** — `./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL; paste totals and APK sizes into evidence §9; privacy grep `git grep -nIiE '/home/[a-z]+|<the owner's username>' -- . ':!.superpowers'` empty; `grep -rn "hot_tub\|power_equipment\|ro_water\|\"ups\"" app/src/main core/src/main --include=*.kt` hits only `SeedTemplates.kt`, the two pickers and provenance mapping.
- [ ] **Step 5: Commit** — `git commit -m "phase 2a evidence, journal smoke test, versionCode 3"`

---

## Self-review

**Spec coverage.** §4 model → T1; §4.1–§4.3 → T1; §5 ports → T2; §6 use cases → T2 (ApplyTemplate, CreateAsset), T3 (Log/Update/Delete), T4 (Export/Import); §7 templates → T2; §8 schema → T5; §9 backup → T4 + T5 (RestoreProof); §10 screens → T6 (asset detail, new-asset template row, routes) + T7 (entry, detail, entries); §12 proof → T1–T7 JVM tests, T8 instrumented + device checklist; §11 deviations recorded in evidence §6 by T8.

**Placeholder scan.** Task 3 and Task 6 list tests by name and behaviour rather than full code; each names the exact inputs and assertions, which an engineer can transcribe. No "TBD"/"add validation"/"handle edge cases".

**Type consistency.** `Reading` (T1) used by T6; `ApplyResult.AlreadySetUp` (T2) used by T6; `EventCommand.values: Map<DefinitionId, String>` (T3) used by T7; `Route.EventEntry(assetId, profileId, eventId)` declared in T6, entered in T7, used by T8 via the asset screen; `FakeGraph.logEvent` (T5) used by T6/T7 tests; `SCHEMA_VERSION = 2` in `AppGraph` and `FakeGraph` (T5) matches `BackupCodec`'s manifest.
