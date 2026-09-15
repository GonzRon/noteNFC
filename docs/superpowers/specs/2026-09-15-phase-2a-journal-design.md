# Phase 2A — Maintenance journal: design

Approved in conversation on 2026-09-15 after the Phase 1C merge (`a8d0094`). This is the spec the
Phase 2A plan argues from. Authorities above it: D4 §4–§7 (data model), D7 Phase 2 (sequence and
exit criteria), D12 §8–§9 and G1 §1.3 (presentation), `docs/design/g1/00-source-data-inventory.md`
(the owner's real records). Where this spec deviates from D4/D7 it says so in §11.

## 1. Goal

After 2A, scanning an asset and recording what happened to it is real: a structured event with
typed measurements and the materials used, shown back as current readings and a service record,
and carried by backup. "Scan the hot tub → log a water test in a few taps; see the history."

## 2. Scope

**In 2A (this spec).** Room v2 with the seven journal tables; `:core` model, ports and use cases
for definitions, profiles and events; five seed templates applied on asset creation (and on demand
for an asset that has none); the generic profile-driven entry route; event detail, edit and delete;
current readings and the Service Record on the asset screen; backup format 2 with all seven
tables and format-1 import; JVM proof and a real-device vertical proof on a hot tub, a UPS and a
mower.

**In 2B (not here).** The definition/profile editor; custom definitions and profiles; the full
asset fields (manufacturer, model, serial, purchase/in-service dates, price, vendor, location,
warranty); child assets; season windows; template management, including any "reset from
template"; derived display values such as RO rejection percentage.

**Never in 2A.** Stock or inventory of consumables (Phase 6); schedules and completion links
(Phase 3); attachments (Phase 4); asset-type-specific tables, screens or workflows.

## 3. Global constraints (carried from 1C, binding on every task)

- Toolchain as on master: AGP 9.4.0, Kotlin 2.4.20, Compose BOM 2026.08.00, Navigation 3 1.1.7,
  lifecycle 2.10.0 with `lifecycle-viewmodel-navigation3`, Room 3.0.3 with the bundled SQLite driver
  in JVM tests, kotlinx-serialization, JUnit 5 in `:core`, JUnit 4 in `:app`.
- `:core` has no Android imports. Business rules live there; `:app` maps and renders.
- Every screen ViewModel is created inside a `NavDisplay` entry and depends on that scope
  (1C evidence §8). State computed in `init` is allowed only because of it.
- No raw colours outside `ui/theme`; every state is position + wording + glyph + colour; range
  states are the D12 §5 measurement family: LOW, IN RANGE, HIGH, NO TARGET SET. OK is blue.
- D12 restraint list stands: no cards for everything, no dials, no stripes, no FAB.
- Commits casual/terse/human, never any AI attribution line; identity GonzRon; no pushes from
  the worktree; nothing personal (device ids, tag UIDs, note ids, home paths) in tracked files.
- Schema export on; every version bump ships a hand-written `Migration` and a migration test.
- Backup: ids verbatim, one read transaction for export, one write transaction for import,
  SHA-256 over the data entry, never a second "cloud" format.

## 4. Domain model (`:core`, package `core.model` unless stated)

Ids: `DefinitionId`, `ProfileId`, `EventId` value classes beside `AssetId`.

```kotlin
enum class ValueType { NUMBER, TEXT, BOOLEAN }

data class MeasurementDefinition(
    val id: DefinitionId, val assetId: AssetId,
    val key: String,            // slug, unique per asset, e.g. "ph", "engine_hours"
    val label: String, val unit: String,   // unit may be "" (pH has none)
    val valueType: ValueType, val decimals: Int,
    val rangeLow: Double?, val rangeHigh: Double?,   // NUMBER only; both null = no target
    val isMeter: Boolean,       // monotonic counter (hours); informational until Phase 3
    val sortOrder: Int, val archivedAt: Long?, val createdAt: Long, val updatedAt: Long,
)

enum class EventKind { MAINTENANCE, INSPECTION, MEASUREMENT, TREATMENT, INCIDENT, REPLACEMENT,
                       SEASON_START, SEASON_END, NOTE, CUSTOM }

data class ProfileField(val definitionId: DefinitionId, val required: Boolean, val sortOrder: Int)
data class ProfileConsumable(val name: String, val defaultQuantity: Double?, val unit: String, val sortOrder: Int)

data class EventProfile(
    val id: ProfileId, val assetId: AssetId,
    val name: String,           // "Water test" — the quick-action label
    val eventKind: EventKind, val defaultTitle: String,
    val templateKey: String?,   // provenance only: which seed produced it
    val sortOrder: Int, val archivedAt: Long?, val createdAt: Long, val updatedAt: Long,
    val fields: List<ProfileField>, val consumables: List<ProfileConsumable>,
)

enum class EventSource { MANUAL, IMPORT }

data class Measurement(
    val id: String, val definitionId: DefinitionId,
    val valueNum: Double?, val valueText: String?,   // exactly one is set, by the definition's type
    val unit: String,           // SNAPSHOT of the definition's unit at entry
    val sortOrder: Int,
)
data class ConsumableUsage(val id: String, val name: String, val quantity: Double, val unit: String, val sortOrder: Int)

data class AssetEvent(              // aggregate root; saved and loaded with its children
    val id: EventId, val assetId: AssetId,
    val kind: EventKind, val title: String,
    val profileId: ProfileId?,
    val occurredOn: String,         // ISO-8601 calendar date "YYYY-MM-DD", required, backdating allowed
    val occurredTime: String?,      // "HH:MM" local, optional
    val tzId: String,               // zone id at entry (audit)
    val notes: String,
    val source: EventSource, val sourceRef: String?,
    val createdAt: Long, val updatedAt: Long,
    val measurements: List<Measurement>, val consumables: List<ConsumableUsage>,
)
```

Not modelled in 2A although D4 lists them: `schedule_id`, `details_pending` (Phase 3),
`cost_minor`/`currency` (2B with the money fields on the asset), `supply_id`, `affects_stock`
(Phase 6). They arrive with their own migrations.

### 4.1 Chronology (the one rule everything derived depends on)

`EventChronology` is a `Comparator<AssetEvent>` in `core.journal`, ascending:

1. `occurredOn` (ISO string order is date order),
2. `occurredTime` with `null` treated as `"00:00"` (an untimed entry sorts before any timed
   entry on the same day),
3. `createdAt`,
4. `id` (lexicographic), so the order is total and stable across devices.

"Newest" always means greatest under this comparator. Insertion order and `updatedAt` never
participate. Pinned by tests: a backdated event created later does not become newest; two
events on the same date without time are ordered by `createdAt`; a timed and an untimed event on
the same date put the timed one later.

### 4.2 Current readings (derived, never stored)

`core.journal.LatestReadings.of(definitions, events): List<Reading>` — for each unarchived
definition in `sortOrder`, the measurement from the newest event (by §4.1) that carries a
measurement for that definition; definitions with no measurement yet produce a `Reading` with
`value = null`. A `Reading` carries the definition, the measurement (or null), the event's
`occurredOn`/`occurredTime`, and the `RangeState`. Computed in `:core` from the observed event
list, so editing or deleting an event changes the answer on the next emission with no cache to
invalidate. Pinned by tests: delete the newest → previous becomes current; edit the newest
event's date backwards past an older one → the older one becomes current; edit a value → the
new value shows; backdated insert → unchanged current.

### 4.3 Range state (pure)

```kotlin
enum class RangeState { LOW, IN_RANGE, HIGH, NO_TARGET }
fun classify(value: Double, low: Double?, high: Double?): RangeState =
    when {
        low == null && high == null -> NO_TARGET
        low != null && value < low  -> LOW
        high != null && value > high -> HIGH
        else -> IN_RANGE
    }
```

Bounds are inclusive. TEXT and BOOLEAN definitions have no range and no badge. Display of a
NUMBER uses the definition's `decimals`; the stored value is never rounded.

## 5. Ports (`core.ports.Repositories.kt`, additions)

```kotlin
interface DefinitionRepository {
    suspend fun upsert(d: MeasurementDefinition); suspend fun get(id: DefinitionId): MeasurementDefinition?
    suspend fun forAsset(assetId: AssetId): List<MeasurementDefinition>; suspend fun all(): List<MeasurementDefinition>
    suspend fun deleteAll(); fun observeForAsset(assetId: AssetId): Flow<List<MeasurementDefinition>>
}
interface ProfileRepository {   // aggregate: upsert replaces fields and consumables
    suspend fun upsert(p: EventProfile); suspend fun get(id: ProfileId): EventProfile?
    suspend fun forAsset(assetId: AssetId): List<EventProfile>; suspend fun all(): List<EventProfile>
    suspend fun deleteAll(); fun observeForAsset(assetId: AssetId): Flow<List<EventProfile>>
}
interface EventRepository {     // aggregate: upsert replaces measurements and consumables
    suspend fun upsert(e: AssetEvent); suspend fun get(id: EventId): AssetEvent?
    suspend fun forAsset(assetId: AssetId): List<AssetEvent>; suspend fun all(): List<AssetEvent>
    suspend fun delete(id: EventId); suspend fun deleteAll()
    fun observeForAsset(assetId: AssetId): Flow<List<AssetEvent>>   // newest first by §4.1
    fun observe(id: EventId): Flow<AssetEvent?>
}
```

`AssetRepository` is unchanged except that `Asset` gains `templateKey: String?`.

## 6. Use cases (`core.usecase`)

- **`ApplyTemplate(assetId, template)`** — inside one `uow.write`: if the asset already has any
  definition or profile, return `AlreadySetUp` and change nothing (this is the idempotency rule);
  otherwise create the template's definitions and profiles with fresh ids and timestamps, set
  `asset.templateKey` if null, return `Applied(definitions, profiles)`. `template_key` on the
  asset and on profiles means "originally seeded from"; nothing later consults the template.
- **`CreateAsset.run(name, category, description, notes, templateKey: String?)`** — as today,
  then applies the named seed in the same transaction when `templateKey` is non-null.
- **`LogEvent(command)`** and **`UpdateEvent(command)`** — one validation path
  (`EventCommand` → `AssetEvent`): title non-blank (defaulted from the profile); `occurredOn`
  parses as a calendar date; `occurredTime` null or `HH:MM`; every `required` profile field has a
  value; NUMBER values parse (locale-independent, `.` decimal), BOOLEAN is 0/1, TEXT is trimmed
  non-empty or absent; unit snapshotted from the definition at save; consumables need a name and
  a quantity ≥ 0. Errors are typed (`EventValidation` with a list of field problems) so the form
  can mark rows. The aggregate is written in one `uow.write`; `updatedAt` set on update, `createdAt`
  preserved.
- **`DeleteEvent(id)`** — one `uow.write`; children go with it (CASCADE).
- **`ExportBackup` / `ImportBackupReplace`** — extended per §9.

## 7. Seed templates (`core.journal.SeedTemplates`, typed Kotlin, no JSON)

Five `Template(key, name, definitions, profiles)` values. Ranges and units below are the seeds'
defaults; from the moment they are applied they are the asset's own rows and 2B's editor may
change them. Keys are stable identifiers; labels are what the user sees.

| key | definitions (key · label · unit · type · decimals · range · meter) | profiles (name → kind, fields, suggested consumables) |
|---|---|---|
| `hot_tub` | `ph` pH · "" · NUMBER · 1 · 7.2–7.8; `free_chlorine` Free chlorine · ppm · 1 · 1.0–3.0; `alkalinity` Alkalinity · ppm · 0 · 80–120; `calcium_hardness` Calcium hardness · ppm · 0 · 150–250; `water_temp` Water temperature · °F · 0 · no range | **Water test** → MEASUREMENT, all five, none required except `ph` and `free_chlorine`; consumables: Chlorine (oz), pH reducer (oz), pH increaser (oz), Alkalinity increaser (oz). **Treatment** → TREATMENT, fields `ph`, `free_chlorine` optional; same consumables |
| `power_equipment` | `engine_hours` Engine hours · h · NUMBER · 1 · no range · **meter** | **Oil change** → MAINTENANCE, `engine_hours` required; consumables: Engine oil (qt), Oil filter (pcs). **Service** → MAINTENANCE, `engine_hours` optional. **Season start** → SEASON_START, `engine_hours` optional. **Season end** → SEASON_END, `engine_hours` optional |
| `ups` | `battery_voltage` Battery voltage · V · 1 · no range; `load_percent` Load · % · 0 · no range; `runtime_minutes` Runtime · min · 0 · no range; `test_passed` Passed · "" · BOOLEAN | **Load test** → INSPECTION, all four, `test_passed` required. **Battery replacement** → REPLACEMENT, `battery_voltage` optional; consumables: Battery (pcs) |
| `ro_water` | `tds_prefilter` Pre-filter TDS · ppm · 0 · no range; `tds_post_membrane` Post-membrane TDS · ppm · 0 · no range; `tds_output` Output TDS · ppm · 0 · no range | **TDS test** → MEASUREMENT, all three required |
| `generic` | none | **Note** → NOTE, no fields |

The three RO points are the sampling locations in the owner's own log (pre-filter, post-membrane,
output). Rejection percentage is not stored and not shown in 2A (§2). `ro_water`'s definitions
ship without ranges on purpose: they exercise the NO TARGET SET state end to end.

Seed templates are starter data, not product taxonomy: no code may branch on a template key
after application.

## 8. Schema v2 (Room, `:app`)

`asset` gains `template_key TEXT NULL`. New tables, all ids TEXT primary keys:

| table | columns | keys / indexes |
|---|---|---|
| `measurement_definition` | id, asset_id, key, label, unit, value_type, decimals INTEGER, range_low REAL NULL, range_high REAL NULL, is_meter INTEGER, sort_order INTEGER, archived_at INTEGER NULL, created_at, updated_at | FK asset CASCADE; `UNIQUE(asset_id, key)`; index asset_id |
| `event_profile` | id, asset_id, name, event_kind, default_title, template_key NULL, sort_order, archived_at NULL, created_at, updated_at | FK asset CASCADE; index asset_id |
| `profile_field` | id, profile_id, definition_id, required INTEGER, sort_order | FK profile CASCADE, FK definition CASCADE; `UNIQUE(profile_id, definition_id)` |
| `profile_consumable` | id, profile_id, name, default_quantity REAL NULL, unit, sort_order | FK profile CASCADE |
| `asset_event` | id, asset_id, kind, title, profile_id NULL, occurred_on TEXT, occurred_time TEXT NULL, tz_id, notes, source, source_ref NULL, created_at, updated_at | FK asset CASCADE; FK profile SET NULL; index `(asset_id, occurred_on DESC, created_at DESC)`; `UNIQUE(source, source_ref)` |
| `measurement` | id, event_id, definition_id, value_num REAL NULL, value_text TEXT NULL, unit, sort_order | FK event CASCADE; FK definition **RESTRICT**; index `(definition_id, event_id)`, index event_id |
| `consumable_usage` | id, event_id, name, quantity REAL, unit, sort_order | FK event CASCADE; index event_id |

`AppDatabase` version 2, `MIGRATION_1_2` hand-written (`ALTER TABLE asset ADD COLUMN`, seven
`CREATE TABLE`, the indexes), `app/schemas/.../2.json` committed. Migration test on the JVM with
`androidx.room3.testing.SQLiteDriverMigrationTestHelper` over `BundledSQLiteDriver`: create v1 from
the exported schema, insert an asset/tag/link, migrate, validate, and assert the rows survived.
The event DAO loads an aggregate with `@Transaction` + `@Relation` (or two queries inside the
UoW) and returns events newest-first by the §4.1 order expressed in SQL
(`ORDER BY occurred_on DESC, COALESCE(occurred_time,'00:00') DESC, created_at DESC, id DESC`);
`:core` re-sorts with `EventChronology` anyway so the rule has one owner.

## 9. Backup format 2

- `FORMAT_VERSION = 2`. `BackupData` gains `measurementDefinitions`, `eventProfiles`
  (each carrying its `fields` and `consumables`), `assetEvents` (each carrying `measurements` and
  `consumables`) — seven tables, three lists, every row represented. All new lists default to
  empty so a format-1 file decodes; `AssetDto.templateKey` defaults to null.
- Validation on decode: unique ids per table; every `assetId`, `definitionId`, `profileId`
  references a row in the same file; a measurement's definition belongs to the same asset as its
  event; `value_type` and `kind` names known. Failures are `BackupCorrupt` with the offending id.
- Import order inside the one write transaction: delete events, profiles, definitions, tags,
  links, assets; then insert assets, definitions, profiles, links, tags, events. `ImportReport`
  gains `definitions`, `profiles`, `events` counts. Export reads everything in one read
  transaction, lists sorted by id.
- `RestoreProofTest` extends to the new tables: export → wipe → import yields identical table
  counts and identical rows.

## 10. Screens (`:app`)

**Routes.** `Route.EventEntry(assetId: String, profileId: String?, eventId: String?)` (new when
`eventId == null`), `Route.EventDetail(id: String)`. Both are `NavDisplay` entries with
entry-scoped ViewModels.

**Asset detail** (`AssetDetailScreen`), top to bottom: plate → **CURRENT READINGS** (instrument
rows: label and target on the left — "7.2–7.8" or "No target" — value in mono at the entry size
with the unit small, state badge under the value; a definition with no reading yet shows "—" and
no badge; the whole section is absent when the asset has no definitions) → "No schedule yet"
line (unchanged) → action grid: the asset's unarchived profiles first as filled actions labelled
"Log <name>" (e.g. "Log water test"), then Write tag, Edit, Links, Backup; an asset with no
definitions and no profiles gets one extra outlined action **Set up from template** that opens a
picker of the five seeds and calls `ApplyTemplate` → **SERVICE RECORD** (`LedgerList` of events
newest first: date column, title, detail line = up to three readings "pH 7.8 · FC 0.8 ppm · …" or
the first consumable, badge only when a reading is LOW/HIGH; tap → `EventDetail`; empty → "No
service recorded yet") → Tags → Links → Notes.

**New asset form** gains a **Template** row (the five seeds by name, default Generic) that maps
to `CreateAsset(templateKey)`. Edit of an existing asset shows no template row.

**Entry route** (`EventEntryScreen`, G1 §1.3 layout). A full-screen destination is the
recommended container; the implementation may choose otherwise only with a reason in the ledger.
App bar: ✕, eyebrow "<PROFILE NAME> · <ASSET NAME>", **Save** text button. Body: LOGGED label with
an editable date and optional time (defaults now, backdating allowed); column header
READING / VALUE / TARGET; one row per profile field in `sortOrder` — NUMBER: outlined value field
with the numeric keypad, unit inside the field, target text, live badge LOW / IN RANGE / HIGH /
NO TARGET SET; BOOLEAN: a two-state segmented control reading Yes / No (the definition label names the
question, e.g. "Passed", so the row reads "Passed · Yes"); TEXT: a text row; required rows show "Required" until filled and
Save is blocked with the first problem named. **MATERIALS USED** section: profile suggestions as
tappable chips that add a row (name · quantity mono · unit); "+ Add material" for a free row;
remove per row. Notes. A second Save at the bottom mirrors the app-bar one. Focus moves down the
value column. Save runs in `viewModelScope` with a re-entry guard and emits `saved` once; the
screen pops on it. Edit mode loads the event, keeps its ids, and shows the same form.

**Event detail** (`EventDetailScreen`): the same rows read-only (value, unit, badge), materials,
notes, logged date/time; overflow with Edit (→ entry route in edit mode) and Delete (confirm
dialog in the destructive family; pops on success). Missing event → back (1C pattern).

**Dashboard** is unchanged in 2A.

Copy rules: uppercase eyebrows, mono numbers and ids, no exclamation marks, state words exactly
LOW / IN RANGE / HIGH / NO TARGET SET.

## 11. Deviations from D4/D7 (recorded, not silent)

- Seed templates are typed Kotlin in `:core`, not JSON in app assets (D7 Phase 2 "seed templates
  JSON"): same content, JVM-testable, no parser; a JSON loader can wrap the type later.
- `template_key` is provenance only; D4 §7's "for reset to default" is not promised in 2A.
- `cost_minor`/`currency`, `schedule_id`, `details_pending`, `supply_id`, `affects_stock` are
  omitted from v2 and arrive with their phases.
- The current meter value is derived in `:core` from the event list rather than by a SQL query
  (D4 §6 describes it as derived; where it is computed is an implementation choice recorded here).
- D7's "profile editor" and "full asset fields incl. hierarchy and season window" move to 2B.

## 12. Proof

**JVM (`:core`).** `EventChronologyTest` (the four pins in §4.1), `LatestReadingsTest` (the four
pins in §4.2 plus "no reading yet"), `RangeStateTest` (bounds inclusive, NO_TARGET, one-sided
ranges), `SeedTemplatesTest` (five keys, unique definition keys per template, every profile field
references a definition in the same template), `ApplyTemplateTest` (applies once, second call
`AlreadySetUp` with no change, sets `templateKey` only when null), `LogEventTest` (required field
missing → typed error; NUMBER parse; BOOLEAN 0/1; unit snapshot; consumables), `UpdateEventTest`,
`DeleteEventTest`, `BackupCodecTest` additions (format-1 decode, format-2 round trip, referential
validation failures), `BackupUseCasesTest` additions (import order, report counts).

**JVM (`:app`).** `Migration1To2Test`, `JournalDaoTest` (aggregate upsert replaces children;
newest-first order; observe emits on child change; RESTRICT on a definition with data),
`RestoreProofTest` (seven tables), `EventEntryViewModelTest` (required gating, save guard, edit
mode preserves ids), `AssetDetailViewModelTest` (readings derive from the observed events).

**Instrumented.** `JournalSmokeTest`: create a hot-tub asset from the template, open "Log water
test", enter pH 7.8, save, see the event in the Service Record and 7.8 with **HIGH** in current
readings.

**Device checklist (evidence `docs/design/phase-2a-evidence.md`).** In order: install + launch;
the destructive instrumented suite; then the three slices — **hot tub**: template, water test
with five readings (two out of range) and two materials, current readings show the states,
ledger shows the event, edit the pH value and watch the reading change, log a backdated test and
confirm current readings do not change; **UPS**: template, load test with three numbers and
Passed = Yes, ledger detail line shows the readings; **mower**: power_equipment template, oil
change with engine hours, oil and filter, current readings show the meter; then export → wipe →
import: identical counts, the same events and readings; delete the newest hot-tub event and see
the previous reading become current; `adb shell` schema inspection listing no asset-type table.

**Exit criteria (D7 Phase 2, as they apply to 2A).** (1) hot-tub acceptance from issue #13.1
reproduced on device, with "configure the metrics" satisfied by the seed in 2A and by the editor
in 2B; (2) mower oil-change event with engine hours stored as a meter reading (its effect on a
schedule is Phase 3); (3) exported backup re-imports with identical table counts; (4) no hot-tub-,
UPS-, RO- or mower-specific table or code path exists (schema inspection and a grep for template
keys outside `SeedTemplates` and the picker).
