# Phase 2B-1 — Definition and profile editors, derived readings: design

Approved in conversation on 2026-09-15 after the Phase 2A merge (`7fe7079`). Authorities above it:
the 2A spec (`2026-09-15-phase-2a-journal-design.md`, whose model this extends), D4 §6–§7, D12
§8–§9, G1 §1.3, the 2A evidence §7–§8. Deviations from D4 are in §11.

## 1. Goal

"Can I completely define what measurements and service forms belong to this particular asset
without touching code?" After 2B-1 the seeds are one way to get definitions and profiles, and the
editors are the other; both write the same rows through the same ports. One derived reading kind,
PERCENT_DROP, exists because the RO system needs its rejection percentage from the readings it
already records.

## 2. Scope

**In 2B-1.** Room v3 (derived-definition columns); the `DERIVED` definition kind with the closed
formula enum; editors for definitions (add, edit, archive/unarchive, delete when unreferenced,
reorder) and profiles (add, edit, archive, delete, reorder, fields with required flags, consumable
suggestions); derived rows in the entry form (live), event detail and current readings; backup
format 3; the `ro_water` seed gains "Rejection"; the two priority 2A fixes — the entry form
survives focus on long profiles, and archived definitions are handled correctly everywhere.

**In 2B-2 (recorded here so nothing is lost; not built now).** Full asset fields (manufacturer,
model, serial, purchase/in-service dates, price, vendor, location, warranty, retired_on); the
category suggestion catalog with optional template hints used only at creation (D4 §4, no
equipment-type key); season windows; retirement of the Scan tab (D12 §16); and **parent/child
composition with these pinned semantics**: `parentAssetId` nullable, one parent, arbitrary depth,
no cycles, reparenting allowed, a child is a full independent asset (own readings, events,
profiles, tags, later schedules) and stays independently scannable; the parent screen gets a
COMPONENTS section (name, category, "N readings out of range" derived from the child's own
current-reading state) and the child plate says "Part of <parent>"; no merged ledger or readings
roll-up — a system view, if ever, is an explicit derived view; delete refused while children
exist; archiving a parent does not archive descendants; backup is the ordinary full backup with
relationships preserved and ids exact.

**Never in 2B-1 (the "not a form builder" boundary).** Conditional fields, nested sections,
formulas beyond the closed enum, chained derivations, scripting, custom widgets, per-profile
layouts, reset-from-template, templates as editable objects.

## 3. Global constraints

All of the 2A spec §3, plus: no asset-specific workflow code (the §9 grep stays in the gate);
identities stay at adapter boundaries (D7 "Product separation"); the editors write only rows the
seeds could have written; derived values are computed, never persisted, never entered.

## 4. Model changes (`:core`)

```kotlin
enum class DefinitionKind { ENTERED, DERIVED }
enum class DerivedFormula { PERCENT_DROP }          // (a − b) / a × 100; closed; add members only with a consumer
data class DerivedSpec(val formula: DerivedFormula, val sourceA: DefinitionId, val sourceB: DefinitionId)

data class MeasurementDefinition(
    …existing fields…,
    val kind: DefinitionKind = DefinitionKind.ENTERED,
    val derived: DerivedSpec? = null,               // non-null iff kind == DERIVED
)
```

Invariants (enforced by the use cases and by backup validation, one predicate
`MeasurementDefinition.derivedSpecValid(sources: Map<DefinitionId, MeasurementDefinition>)`):
a DERIVED definition has `valueType == NUMBER`, `isMeter == false`, a `derived` spec whose two
sources are distinct, exist, belong to the same asset, are `ENTERED` and `NUMBER`; an ENTERED
definition has `derived == null`. Unit, decimals and range on a derived definition mean what they
mean on any NUMBER definition.

## 5. Computation (pure, `core.journal.Derived`)

- `fun compute(def: MeasurementDefinition, event: AssetEvent, sources: Map<DefinitionId, MeasurementDefinition>): Double?`
  — null unless `def.kind == DERIVED`; takes A and B from **this event's** measurements only; null
  if either source definition is missing from `sources` or archived, if either value is absent, if
  A is 0 for PERCENT_DROP, or if the result is not finite. Same-event semantics are a
  hard rule: latest A and latest B from different events are never combined.
- `LatestReadings.of` gains derived rows: for a DERIVED definition the reading is the value from
  the newest event (by `EventChronology`) for which `compute` is non-null; `Reading.measurement` is
  null and a new `Reading.derivedValue: Double?` carries it; `state` classifies it against the
  definition's range. An archived source makes `compute` return null (the derived row then reads
  "—"); history is untouched because nothing was stored.
- Entry form: derived rows render live beneath the inputs from the values typed so far (same
  rule, same function); they are never inputs. Event detail renders them from the stored event.

## 6. Use cases (`core.usecase`, each one transaction)

- `SaveDefinition(cmd)` create or update: `key` slug `^[a-z][a-z0-9_]{0,39}$` unique per asset
  (generated from the label when blank: lowercase, non-alphanumerics → `_`, de-duplicated with a
  numeric suffix); label non-blank; `decimals` 0–4; `rangeLow <= rangeHigh` when both set; ranges
  and meter flag only for NUMBER; derived invariants from §4; changing `valueType` or `kind` of a
  definition that has measurements is refused (`DefinitionInUse`); typed `DefinitionValidation`.
  **Prospective graph check:** before committing an update, the use case loads the asset's
  definitions, substitutes the edited one in memory, and runs `derivedProblems` for every DERIVED
  definition of the asset; if any existing derived definition would become invalid — a source
  turning TEXT/BOOLEAN, turning DERIVED, or gaining the meter flag — the update is refused with
  `DefinitionWouldBreakDerived(id, dependentDerivedIds)`. Label, unit, range, decimals and key
  edits pass; archiving a source is allowed by design (§5). This is what keeps the editor from
  writing a state the backup codec would later reject.
- `ArchiveDefinition(id)` / `UnarchiveDefinition(id)`.
- `DeleteDefinition(id)` refused with `DefinitionReferenced(measurements: Int, derivedBy: List<DefinitionId>, profiles: List<ProfileId>)`
  when any measurement, derived definition or profile field references it; otherwise deletes.
  (Profile fields cascade at the schema level; the use case still reports them so the UI can say
  "used by Water test".)
- `ReorderDefinitions(assetId, ids)` rewrites `sortOrder`.
- `SaveProfile(cmd)`: name non-blank and unique per asset (case-insensitive); `eventKind`;
  `defaultTitle` defaulted from name; ordered fields referencing ENTERED, unarchived definitions of
  the same asset, each with `required`; consumable suggestions (name non-blank, unit, optional
  default quantity ≥ 0); child ids preserved on update, minted on create.
- `ArchiveProfile(id)` / `UnarchiveProfile(id)`; `DeleteProfile(id)` allowed always (events keep
  their history; `profile_id` SET NULL by the schema); `ReorderProfiles(assetId, ids)`.
- `ApplyTemplate` unchanged; the seeds may carry derived definitions (`TemplateDefinition` gains
  `derived: Pair<sourceKey, sourceKey>?` resolved to ids at application).

## 7. Schema v3 (Room)

`measurement_definition` gains `kind TEXT NOT NULL DEFAULT 'ENTERED'`, `formula TEXT NULL`,
`source_a_id TEXT NULL`, `source_b_id TEXT NULL`, both FK → `measurement_definition(id)`
**RESTRICT**, indexed. `MIGRATION_2_3` hand-written: because SQLite cannot add foreign-key columns with
ALTER TABLE, the migration recreates the table the Room way — create `_new_measurement_definition`
from `3.json`'s createSql, copy the v2 rows with `kind = 'ENTERED'`, drop the old table, rename,
then create the indexes (the existing three plus the two new source indexes), `3.json` committed,
`Migration2To3Test` on the JVM in the 2A style (build v2 from `2.json`, migrate, validate, rows
survive) plus a chained `Migration1To3Test` (v1 → v3 through both migrations: the continuous
upgrade proof).

## 8. Backup format 3

`MeasurementDefinitionDto` gains `kind: String = "ENTERED"`, `formula: String? = null`,
`sourceAId: String? = null`, `sourceBId: String? = null`; `FORMAT_VERSION = 3`; format 2 files
decode with the defaults. Validation: kind/formula names known; the §4 invariants via
`derivedSpecValid` over the file's own definitions; a measurement must not reference a DERIVED
definition (`BackupCorrupt` naming it). Import order unchanged (definitions before profiles; a
derived definition's sources are inserted in the same batch — insert ENTERED rows first, then
DERIVED). Restore proof extended.

## 9. Screens

**Asset detail** gains one outlined action **Readings & actions** → `Route.AssetSetup(assetId)`.
"Set up from template" still appears only when the asset has no definitions and no profiles, where
"no definitions" now ignores nothing — archived definitions count as existing.

**Readings & actions** (`AssetSetupScreen`): two sections under one app bar (title = asset name,
eyebrow READINGS & ACTIONS). READINGS: `LedgerList`-style rows — label, unit and kind glyph
(derived rows show "= A − B / A" in mono under the label), target text, ARCHIVED badge when
archived; tap → edit; long-press or trailing handle → reorder (or up/down in the overflow —
implementation's choice, recorded); "+ Add reading" outlined full-width. ACTIONS: profile rows —
name, kind, field count, ARCHIVED badge; tap → edit; "+ Add action". No cards, no FAB.

**Definition editor** (`Route.DefinitionEdit(assetId, definitionId?)`): Label; Key (auto from
label, editable until the definition has data, mono); Kind segmented ENTERED / DERIVED; for
ENTERED: Type segmented NUMBER / TEXT / BOOLEAN, Unit, Decimals, Target low / high, "Meter
(counts up)" switch; for DERIVED: Formula (single option today, shown as "Percent drop:
(A − B) / A × 100"), Source A and Source B pickers listing the asset's ENTERED NUMBER definitions,
Unit defaulted "%", Decimals, Target. Save in the app bar; problems named per field (2A pattern);
overflow: Archive / Unarchive, Delete (refusal dialog lists what references it). `DefinitionInUse`
disables Kind and Type with the reason under them.

**Profile editor** (`Route.ProfileEdit(assetId, profileId?)`): Name; Kind (event kind dropdown);
Default title; FIELDS: ordered list of the chosen definitions with a Required switch and up/down
ordering, "+ Add field" opens a picker of the asset's unarchived ENTERED definitions not yet
chosen; MATERIALS: suggestion rows (name · default quantity · unit) with remove and "+ Add
material"; Save; overflow Archive / Delete (confirm: "Past entries keep their readings").

**Entry form** changes: the field list moves from `LazyColumn` to `Column` + `verticalScroll`
(focus and IME survive; `ImeAction.Next` reaches every row); archived definitions never produce
input rows unless the edited event already carries a value for them (2A carry rule); derived rows
appear live under the inputs as read-only `InstrumentRow`s.

**Current readings / event detail**: derived rows render as readings with their badge; "—" when
not computable.

## 10. 2A minors resolved here

Entry-form focus (Column + verticalScroll); archived definitions excluded from entry rows and from
the emptiness check, shown with an ARCHIVED badge in the setup screen and still rendered in
history; `quantity()`/`plain()` merged into `JournalFormat`. Others stay deferred (positional
consumable ids: the profile editor edits *suggestions*, not usages, so still not needed; the
AssetDetailViewModel facade; `isError` tint; BOOLEAN case).

## 11. Deviations recorded

- D4 §7 "not supported by design: computed fields" → "no expressions; a closed formula enum with
  same-event semantics" — justified by the RO rejection case; PERCENT_DROP is the only member.
- D4 §6 `measurement_definition` gains four columns not in the original table.
- D4 §13 "delete definition only if no measurement references it" is extended to derived and
  profile references, reported by name.

## 12. Proof

**JVM `:core`.** `DerivedTest` (same-event only; missing source → null; A = 0 → null; archived
source → null; result finite; classification against the derived range), `LatestReadingsTest`
additions (newest event where computable, skipping newer partial events), `DefinitionUseCasesTest`
(key generation and uniqueness, range order, type/kind change refused when in use, derived
invariants incl. self-reference and DERIVED-as-source, **`sourceUsedByDerivedCannotBecomeText`
and `sourceUsedByDerivedCannotBecomeDerived` → `DefinitionWouldBreakDerived` naming the
dependent, while relabelling or archiving the same source passes**, delete refusal listing
references, reorder), `ProfileUseCasesTest` (name uniqueness, field references validated, child ids
preserved, delete keeps events with profile cleared), `SeedTemplatesTest` (ro_water derived
"Rejection" resolves to the two TDS sources), `BackupCodecTest` (format 2 decodes, format 3 round
trip, derived invariants, measurement on a DERIVED definition rejected).

**JVM `:app`.** `Migration2To3Test`, `Migration1To3Test`, DAO tests for the new columns and the
RESTRICT on sources, `RestoreProofTest` extension, `AssetSetupViewModelTest`,
`DefinitionEditViewModelTest`, `ProfileEditViewModelTest`, entry-form derived row test.

**Instrumented (automated, on the emulator or phone).** `EditorsDeviceProofTest`: on a plain
asset add a NUMBER definition and a profile using it, log an event through the new quick action,
see the reading; on an RO-template asset log one TDS test and see "Rejection 94.2 %" as a reading,
then log a second test with only pre-filter TDS and confirm the current rejection is still from
the first; archive a definition and see it leave the entry form but remain in history; attempt to
delete a definition with data and see the refusal name the count; format-2 export → wipe → import
round trip in-process. Manual rows: none required.

**Exit criteria.** (1) a custom definition and profile created in the editors drive a logged
event and a current reading, on device, with no code change; (2) RO rejection appears from one TDS
test and is never combined across events (test + device); (3) a v1 database upgrades through v2
to v3 with rows intact (JVM chain) and a real 2A install upgrades on device; (4) format 2 backups
import into format 3; (5) the §9 grep stays clean and no screen branches on a template key.
